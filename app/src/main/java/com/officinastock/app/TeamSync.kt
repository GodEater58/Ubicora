package com.officinastock.app

import android.content.ContentValues
import android.util.Base64
import com.officinastock.app.AppDatabase as SQLiteDatabase
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/**
 * 4.8 phone-to-phone sync without a server ("tappa 1": articles and stock).
 *
 * One central phone owns the warehouse; satellite phones (letters B…Z) work offline and exchange
 * encrypted files with it:
 *  - satellite → central: the changes not yet confirmed (new articles, stock movements, locations),
 *    taken from an outbox filled by SQLite triggers, so every code path that moves stock is covered;
 *  - central → everyone: the complete current state (map, articles, quantities) plus, per phone,
 *    the last change it has processed. A satellite keeps any change the central has not seen yet
 *    on top of that state, so nothing is lost whatever the order and frequency of the exchanges.
 * Stock is merged by adding movements, never by copying quantities, and every change carries a
 * per-phone sequence number: importing the same file twice changes nothing.
 */
class TeamSync(private val stock: StockDb) {
    companion object {
        const val SNAPSHOT = "UBICORA_SYNC_STATE"
        const val CHANGES = "UBICORA_SYNC_CHANGES"
        const val JOIN = "UBICORA_SYNC_JOIN"
        const val KEY = "UBICORA_SYNC_KEY"
        /** One-time invite codes shown in the central's QR: valid for 10 minutes. */
        const val INVITE_MINUTES = 10
        /** A code with less time left than this is replaced, so the QR never shows one about to expire. */
        private const val INVITE_REUSE_MS = 3 * 60_000L
        private const val INVITE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        const val NEW_PASSWORD_MIN = 12
        /** 4.21.2: the password proposed to a new central: 16 random characters (80 bits), easy to copy by hand. */
        fun newPassword(): String {
            val random = java.security.SecureRandom()
            return (1..4).joinToString("-") { (1..4).map { INVITE_ALPHABET[random.nextInt(INVITE_ALPHABET.length)] }.joinToString("") }
        }
        private const val MAX_JSON = 64L * 1024 * 1024
        private const val MAX_EVENTS = 200_000
        private const val MAX_SITES = 10
        /** 4.19: the "department" of a satellite that can only withdraw. */
        const val PICK_ONLY = "Solo prelievo"
        /**
         * 4.21: a maintainer with a department travels as "Manutentore" plus his categories. Like
         * [PICK_ONLY] it matches no category, so an older app sees an ordinary department.
         * A satellite with no line at all is a maintainer of every category, as before.
         */
        const val MAINTAINER = "Manutentore"
        /** 4.21: what a maintainer can change on an article card, in the order shown. */
        val EDIT_FIELDS = linkedMapOf("sku" to "Codice", "name" to "Nome", "category" to "Categoria", "minimum" to "Scorta minima",
            "unit" to "Unità", "note" to "Descrizione", "cost" to "Costo unitario", "supplier" to "Fornitore", "lead" to "Giorni di consegna")
        fun isMaintainer(scope: Collection<String>) = scope.isEmpty() || MAINTAINER in scope
        /** The categories of a department, without the role marks (empty = every category). */
        fun department(scope: Collection<String>) = scope.filter { it != MAINTAINER && it != PICK_ONLY }
        /** An article card as it travels in a change request. */
        fun card(sku: String, name: String, category: String, minimum: Double, unit: String, note: String,
                 cost: Double?, supplier: String, lead: Int?): JSONObject =
            JSONObject().put("sku", sku.trim()).put("name", name.trim()).put("category", category).put("minimum", minimum)
                .put("unit", unit.trim()).put("note", note.trim()).put("cost", cost ?: JSONObject.NULL)
                .put("supplier", supplier.trim()).put("lead", lead ?: JSONObject.NULL)
        private const val MAX_EDITS = 2_000
        private const val MAX_EDIT_JSON = 20_000
        /** 4.15: photos asked for and carried by one file; what does not fit follows at the next exchanges. */
        private const val MAX_PHOTO_WANTS = 60
        private const val MAX_PHOTO_BYTES = 8 * 1024 * 1024
    }
    private val db get() = stock.writableDatabase
    /** Photos of articles removed by the last central update: deleted once the update is committed. */
    private val removedPhotos = mutableListOf<String>()
    private fun dropRemovedPhotos() { stock.deletePhotoFiles(removedPhotos); removedPhotos.clear() }
    private fun setting(key: String) = TeamSyncSchema.setting(db, key)
    private fun deviceUid(): String = setting("device_uid") ?: UUID.randomUUID().toString().also { TeamSyncSchema.put(db, "device_uid", it) }

    fun status(): TeamSyncStatus {
        val role = when (setting("role")) { "central" -> TeamRole.CENTRAL; "satellite" -> TeamRole.SATELLITE; else -> TeamRole.NONE }
        if (role == TeamRole.NONE) return TeamSyncStatus()
        val pending = db.rawQuery("SELECT COUNT(*) FROM team_sync_outbox", null).use { it.moveToFirst(); it.getInt(0) }
        val peers = db.rawQuery("SELECT letter,processed_seq,last_import FROM team_sync_peers ORDER BY letter", null).use { c ->
            buildList { while (c.moveToNext()) add(TeamPeer(c.getString(0), c.getLong(1), c.getLong(2))) } }
        val conflicts = db.rawQuery("SELECT id,letter,item,detail,created_at FROM team_sync_conflicts ORDER BY id DESC LIMIT 200", null).use { c ->
            buildList { while (c.moveToNext()) add(TeamConflict(c.getLong(0), c.getString(1), c.getString(2), c.getString(3), c.getLong(4))) } }
        return TeamSyncStatus(role, setting("warehouse_id")?.toLongOrNull(), setting("letter"),
            if (role == TeamRole.SATELLITE) pending else 0, setting("last_sync")?.toLongOrNull() ?: 0, peers, conflicts,
            lines(setting("scope")), if (role == TeamRole.CENTRAL) scopes() else emptyMap(), teamPw() == null,
            sites = extraSites().keys.toList(), labelIds = labelIds(), loans = teamLoans(), edits = edits(role))
    }

    // ---------------------------------------------------------------- 4.15 loans of the team

    // ---------------------------------------------------------------- 4.18 sites of the company

    /** The other shared sites: local warehouse id -> id shared by every phone. */
    private fun extraSites(): LinkedHashMap<Long, String> {
        val out = LinkedHashMap<Long, String>()
        for (line in lines(setting("sites"))) { val parts = line.split('|'); val id = parts.getOrNull(0)?.toLongOrNull(); if (id != null && parts.size == 2) out[id] = parts[1] }
        return out
    }
    private fun saveSites(sql: SQLiteDatabase, sites: Map<Long, String>) {
        if (sites.isEmpty()) sql.delete("team_sync_settings", "key IN ('sites','warehouse_ids')", null)
        else {
            TeamSyncSchema.put(sql, "sites", sites.entries.joinToString("\n") { "${it.key}|${it.value}" })
            TeamSyncSchema.put(sql, "warehouse_ids", "," + (listOf(setting("warehouse_id")!!.toLong()) + sites.keys).joinToString(",") + ",")
        }
    }
    /** Every site of the team on this phone, the first one included. */
    private fun teamIds(): List<Long> = listOfNotNull(setting("warehouse_id")?.toLongOrNull()) + extraSites().keys
    private fun labelIds(): Map<Long, Long> = buildMap {
        for (line in lines(setting("label_ids"))) { val parts = line.split('|'); val a = parts.getOrNull(0)?.toLongOrNull(); val b = parts.getOrNull(1)?.toLongOrNull(); if (a != null && b != null) put(a, b) }
    }
    private fun companyOf(warehouseId: Long): Long? =
        db.rawQuery("SELECT profile_id FROM warehouses WHERE id=?", arrayOf(warehouseId.toString())).use { if (it.moveToFirst()) it.getLong(0) else null }
    /** An article of any shared site, by its global id: (article, site). */
    private fun teamItem(sql: SQLiteDatabase, ids: Collection<Long>, uid: String): Pair<Long, Long>? =
        if (uid.isEmpty()) null else sql.rawQuery("SELECT id,warehouse_id FROM items WHERE uid=?", arrayOf(uid)).use {
            if (it.moveToFirst() && it.getLong(1) in ids) it.getLong(0) to it.getLong(1) else null }

    /** Central: another site of the same company joins the team. The satellites receive it with the next update. */
    fun addSite(warehouseId: Long) {
        require(setting("role") == "central") { "Le sedi della squadra le sceglie il telefono centrale" }
        val first = setting("warehouse_id")!!.toLong()
        val sites = extraSites()
        require(warehouseId != first && warehouseId !in sites) { "Questa sede è già condivisa con la squadra" }
        val company = companyOf(warehouseId)
        require(company != null && company == companyOf(first)) { "Si possono condividere solo le sedi della stessa azienda" }
        require(sites.size < MAX_SITES - 1) { "Troppe sedi condivise" }
        val sql = db
        sql.beginTransaction()
        try { saveSites(sql, sites + (warehouseId to UUID.randomUUID().toString())); sql.setTransactionSuccessful() } finally { sql.endTransaction() }
    }

    // ---------------------------------------------------------------- 4.15 photos of the team

    /** Photos as base64 by name, skipping what is missing and stopping at the size limit of one file. */
    private fun photosJson(names: Collection<String>): JSONObject {
        val out = JSONObject()
        var total = 0
        for (name in names) {
            val bytes = stock.photoBytes(name) ?: continue
            if (total + bytes.size > MAX_PHOTO_BYTES) break
            total += bytes.size
            out.put(name, Base64.encodeToString(bytes, Base64.NO_WRAP))
        }
        return out
    }
    /** Central: stores the photo that came with a new article of a satellite. A bad photo never blocks the article. */
    private fun receivePhoto(sql: SQLiteDatabase, warehouseId: Long, uid: String, name: String, photos: JSONObject?) {
        if (name.isEmpty()) return
        runCatching {
            if (!stock.hasPhoto(name)) stock.storePhoto(name, Base64.decode(photos?.optString(name).orEmpty(), Base64.DEFAULT))
            sql.update("items", ContentValues().apply { put("photo_file", name) }, "uid=? AND warehouse_id=?", arrayOf(uid, warehouseId.toString()))
        }
    }
    /**
     * Satellite: every article takes the photo the central names. A photo already on this phone, or
     * delivered with this file, is used at once; the others are noted and asked for with the next file
     * of changes. Until a photo arrives the article keeps the one it had.
     */
    private fun applyPhotos(sql: SQLiteDatabase, warehouseId: Long, items: JSONArray, delivered: JSONObject?, pendingItems: Set<Long>,
                            wants: MutableSet<String>, nowFromCentral: MutableSet<String>, offers: MutableSet<String>): Boolean {
        // Photos this phone took from the central: only those follow a removal made on the central.
        // [offers]: photos taken on this phone for articles the central has no photo of: offered to it, never deleted.
        val fromCentral = lines(setting("photos_central")).toSet()
        for (i in 0 until items.length()) {
            val it = items.getJSONObject(i)
            if (!it.has("photo")) return false   // central older than 4.16: photos stay as they are
            val local = sql.rawQuery("SELECT id,photo_file FROM items WHERE uid=? AND warehouse_id=?", arrayOf(it.getString("uid"), warehouseId.toString()))
                .use { c -> if (c.moveToFirst()) c.getLong(0) to (if (c.isNull(1)) null else c.getString(1)) else null } ?: continue
            if (local.first in pendingItems) continue
            val name = if (it.isNull("photo")) null else it.getString("photo")
            if (name == local.second) { name?.let { same -> nowFromCentral += same }; continue }
            if (name == null && local.second !in fromCentral) { offers += it.getString("uid"); continue }
            if (name != null && !stock.hasPhoto(name)) {
                val data = delivered?.optString(name).orEmpty()
                if (data.isEmpty() || runCatching { stock.storePhoto(name, Base64.decode(data, Base64.DEFAULT)) }.isFailure) { wants += name; continue }
            }
            sql.update("items", ContentValues().apply { if (name == null) putNull("photo_file") else put("photo_file", name) }, "id=?", arrayOf(local.first.toString()))
            local.second?.let { removedPhotos += it }
            name?.let { adopted -> nowFromCentral += adopted }
        }
        return true
    }

    private fun teamLoans(): List<TeamLoan> {
        setting("warehouse_id") ?: return emptyList()
        return db.rawQuery("""SELECT t.letter,i.id,COALESCE(i.name,'Articolo non più presente'),t.borrower,t.quantity,t.created_at
            FROM team_loans t LEFT JOIN items i ON i.uid=t.item_uid ORDER BY t.created_at""", null).use { c ->
            buildList { while (c.moveToNext()) add(TeamLoan(c.getString(0), if (c.isNull(1)) null else c.getLong(1), c.getString(2),
                c.getString(3), c.getDouble(4), c.getLong(5))) } }
    }
    /** Open loans made on this phone, for the other phones to see. */
    private fun ownLoans(read: SQLiteDatabase, warehouseId: Long, letter: String, into: JSONArray = JSONArray()): JSONArray {
        read.rawQuery("""SELECT i.uid,l.borrower,l.quantity,l.created_at FROM loans l JOIN items i ON i.id=l.item_id
            WHERE i.warehouse_id=? AND l.returned_at IS NULL AND i.uid IS NOT NULL ORDER BY l.id LIMIT 2000""", arrayOf(warehouseId.toString())).use { c ->
            while (c.moveToNext()) into.put(JSONObject().put("letter", letter).put("item", c.getString(0)).put("borrower", c.getString(1))
                .put("quantity", c.getDouble(2)).put("at", c.getLong(3)))
        }
        return into
    }
    /** Stores the loans of a file; [forced] = they all belong to that letter (a satellite's file), otherwise each row says whose it is. */
    private fun storeLoans(sql: SQLiteDatabase, loans: JSONArray, forced: String?, skip: String?) {
        require(loans.length() <= 5000) { "File troppo grande" }
        for (i in 0 until loans.length()) {
            val o = loans.getJSONObject(i)
            val letter = forced ?: o.optString("letter")
            val quantity = o.optDouble("quantity")
            if (letter.length != 1 || letter == skip || !(quantity > 0.0) || !quantity.isFinite()) continue
            sql.insertOrThrow("team_loans", null, ContentValues().apply {
                put("letter", letter); put("item_uid", o.optString("item").take(64)); put("borrower", o.optString("borrower").take(120))
                put("quantity", quantity); put("created_at", o.optLong("at"))
            })
        }
    }

    /**
     * 4.15: this satellite takes the place of a central phone that is lost or broken. It keeps its
     * stock (its own unsent changes are already in it) and starts from what the last update said
     * about the other satellites, so the changes they had already delivered are not applied twice.
     * What only the old central knew after its last update to this phone is lost with it.
     */
    fun promoteToCentral() {
        require(setting("role") == "satellite") { "Solo un satellite può prendere il posto del centrale" }
        requireTeamPw()
        val me = deviceUid()
        val myLetter = setting("letter")
        val known = setting("known_peers")?.let { runCatching { JSONObject(it) }.getOrNull() }
        val sql = db
        sql.beginTransaction()
        try {
            for (t in listOf("team_sync_outbox", "team_sync_peers", "team_sync_conflicts")) sql.delete(t, null, null)
            val letters = known?.optJSONObject("letters"); val processed = known?.optJSONObject("processed"); val scopes = known?.optJSONObject("scopes")
            letters?.keys()?.forEach { device ->
                val letter = letters.optString(device)
                if (device != me && letter != myLetter && ArticleCodes.isSatelliteLetter(letter))
                    sql.insertWithOnConflict("team_sync_peers", null, ContentValues().apply {
                        put("device_uid", device); put("letter", letter); put("processed_seq", processed?.optLong(device, 0L) ?: 0L)
                    }, SQLiteDatabase.CONFLICT_REPLACE)
            }
            scopes?.keys()?.forEach { letter ->
                val categories = scopes.optJSONArray(letter)
                if (letter != myLetter && categories != null && categories.length() > 0)
                    TeamSyncSchema.put(sql, "scope:$letter", (0 until categories.length()).joinToString("\n") { categories.getString(it) })
            }
            sql.delete("team_sync_settings", "key IN ('letter','scope','known_peers','applying','returning','actor','photo_wants','photo_offers','photos_central')", null)
            // The loans made on the lost central can no longer be returned there: they leave the list (the stock already counts them as out).
            sql.delete("team_loans", "letter='A'", null)
            TeamSyncSchema.put(sql, "role", "central")
            sql.setTransactionSuccessful()
        } finally { sql.endTransaction() }
    }

    // ---------------------------------------------------------------- set-up

    /** This phone becomes the central one for [warehouseId]. */
    fun becomeCentral(warehouseId: Long, password: String) {
        // 4.21.2: a new team starts with a long password (the files travel by chat and email); old teams keep theirs.
        require(password.length >= NEW_PASSWORD_MIN) { "La password di una nuova squadra deve avere almeno $NEW_PASSWORD_MIN caratteri" }
        require(setting("role") == null) { "Questo telefono è già collegato a una sincronizzazione" }
        requireNotNull(stock.readableDatabase.rawQuery("SELECT id FROM warehouses WHERE id=?", arrayOf(warehouseId.toString()))
            .use { if (it.moveToFirst()) it.getLong(0) else null }) { "Magazzino non trovato" }
        val sql = db
        sql.beginTransaction()
        try {
            deviceUid()
            TeamSyncSchema.put(sql, "role", "central")
            TeamSyncSchema.put(sql, "warehouse_id", warehouseId.toString())
            TeamSyncSchema.put(sql, "warehouse_uid", UUID.randomUUID().toString())
            TeamSyncSchema.put(sql, "password", DeviceSecrets.seal(password))
            sql.setTransactionSuccessful()
        } finally { sql.endTransaction() }
    }

    /** First import on a satellite: creates the warehouse from the central's state under [profileId]. */
    fun joinAsSatellite(profileId: Long, input: InputStream, password: String, letter: String): TeamImportResult {
        requirePassword(password)
        require(ArticleCodes.isSatelliteLetter(letter)) { "Scegli una lettera da B a Z" }
        require(setting("role") == null) { "Questo telefono è già collegato a una sincronizzazione" }
        val state = read(input, password, SNAPSHOT)
        val room = state.getJSONObject("warehouse")
        val me = setting("device_uid")
        require(state.getJSONObject("letters").let { taken -> taken.keys().asSequence().none { it != me && taken.getString(it) == letter } }) {
            "La lettera $letter è già usata da un altro telefono: scegline un'altra"
        }
        val sql = db
        sql.beginTransaction()
        try {
            val warehouseId = sql.insertOrThrow("warehouses", null, ContentValues().apply {
                put("profile_id", profileId); put("name", room.getString("name").take(160))
                put("location", room.optString("location").take(300))
                put("map_width", dimension(room.getDouble("width"))); put("map_height", dimension(room.getDouble("height")))
            })
            deviceUid()
            TeamSyncSchema.put(sql, "role", "satellite")
            TeamSyncSchema.put(sql, "letter", letter)
            TeamSyncSchema.put(sql, "warehouse_id", warehouseId.toString())
            TeamSyncSchema.put(sql, "warehouse_uid", state.getString("warehouse_uid"))
            TeamSyncSchema.put(sql, "password", DeviceSecrets.seal(password))
            val result = applyState(sql, warehouseId, state)
            sql.setTransactionSuccessful()
            return result
        } finally { sql.endTransaction() }
    }

    /**
     * 4.9.1, nearby join: a small file encrypted with the team password that books [letter] on the
     * central before any article is created, so two phones can never end up with the same letter.
     */
    fun joinTicket(out: OutputStream, password: String, letter: String) {
        requirePassword(password)   // the team password, or the one-time code read from the central's QR
        require(ArticleCodes.isSatelliteLetter(letter)) { "Scegli una lettera da B a Z" }
        require(setting("role") == null) { "Questo telefono è già collegato a una sincronizzazione" }
        write(out, JSONObject().put("format", JOIN).put("version", 1).put("device_uid", deviceUid())
            .put("letter", letter).put("created_at", System.currentTimeMillis()), password)
    }

    data class JoinAccepted(val letter: String, val inviteCode: String?)

    /**
     * Central: checks the ticket (team password, or the QR one-time code while valid) and books the
     * letter. [JoinAccepted.inviteCode] is set when the phone joined with the code.
     */
    fun acceptJoin(input: InputStream): JoinAccepted {
        require(setting("role") == "central") { "Questo telefono non è il centrale" }
        val bytes = input.readBytes()
        require(bytes.size <= 64 * 1024) { "Richiesta di collegamento non valida" }
        var usedCode: String? = null
        val ticket = runCatching { read(bytes.inputStream(), requireTeamPw(), JOIN) }.getOrNull()
            ?: activeInvite()?.let { code -> runCatching { read(bytes.inputStream(), code, JOIN) }.getOrNull()?.also { usedCode = code } }
            ?: throw IllegalArgumentException(if (setting("invite_until") != null && activeInvite() == null)
                "Password errata o QR scaduto: sul centrale mostra un QR nuovo" else "Password della squadra errata: controlla e riprova")
        val device = ticket.getString("device_uid"); val letter = ticket.getString("letter")
        require(ArticleCodes.isSatelliteLetter(letter) && device.isNotBlank()) { "Richiesta di collegamento non valida" }
        val sql = db
        sql.beginTransaction()
        try {
            val owner = sql.rawQuery("SELECT device_uid FROM team_sync_peers WHERE letter=?", arrayOf(letter)).use {
                if (it.moveToFirst()) it.getString(0) else null }
            require(owner == null || owner == device) { "La lettera $letter è già usata da un altro telefono: scegline un'altra" }
            val known = sql.rawQuery("SELECT 1 FROM team_sync_peers WHERE device_uid=?", arrayOf(device)).use { it.moveToFirst() }
            if (known) sql.update("team_sync_peers", ContentValues().apply { put("letter", letter) }, "device_uid=?", arrayOf(device))
            else sql.insertOrThrow("team_sync_peers", null, ContentValues().apply { put("device_uid", device); put("letter", letter) })
            sql.setTransactionSuccessful()
        } finally { sql.endTransaction() }
        return JoinAccepted(letter, usedCode)
    }

    /**
     * Leaves the sync. A satellite with unsent changes must send them first, unless [force]
     * (for example the central refused its letter): the local data stays, it is just not sent.
     * The phone keeps its identity, so the central recognises it if it joins again.
     */
    fun leave(force: Boolean = false) {
        require(force || setting("role") != "satellite" ||
            db.rawQuery("SELECT COUNT(*) FROM team_sync_outbox", null).use { it.moveToFirst(); it.getInt(0) } == 0) {
            "Ci sono modifiche non ancora inviate al centrale: inviale prima di scollegare il telefono"
        }
        val sql = db
        sql.beginTransaction()
        try {
            sql.delete("team_sync_settings", "key<>'device_uid'", null)
            for (t in listOf("team_sync_outbox", "team_sync_peers", "team_sync_conflicts", "team_loans", "team_edits")) sql.delete(t, null, null)
            sql.setTransactionSuccessful()
        } finally { sql.endTransaction() }
    }

    /**
     * On a satellite the article details (name, category, minimum, purchase data) come from the
     * central with every update, so a local edit would be silently overwritten: only the phone's
     * own articles not yet sent can still be corrected.
     */
    fun requireDetailsEditable(itemId: Long) {
        require(detailsEdit(itemId) == DetailsEdit.DIRECT) {
            "Su questo telefono (satellite ${setting("letter")}) i dati dell'articolo si modificano dal telefono centrale. Carichi, prelievi e ubicazione invece sì."
        }
    }

    /**
     * 4.21: how the card of [itemId] can be changed here. A maintainer changes the cards of his
     * department, but the change waits for the central's validation ([DetailsEdit.REVIEW]); the
     * other satellites cannot change them; the phone's own articles not yet sent change at once.
     */
    fun detailsEdit(itemId: Long): DetailsEdit {
        if (setting("role") != "satellite") return DetailsEdit.DIRECT
        val (site, category) = db.rawQuery("SELECT warehouse_id,category FROM items WHERE id=?", arrayOf(itemId.toString()))
            .use { if (it.moveToFirst()) it.getLong(0) to it.getString(1) else null } ?: return DetailsEdit.DIRECT
        if (site !in teamIds()) return DetailsEdit.DIRECT
        if (db.rawQuery("SELECT 1 FROM team_sync_outbox WHERE kind='ITEM' AND ref_id=?", arrayOf(itemId.toString())).use { it.moveToFirst() })
            return DetailsEdit.DIRECT
        val scope = lines(setting("scope"))
        require(isMaintainer(scope)) {
            "Su questo telefono (satellite ${setting("letter")}) i dati dell'articolo si modificano dal telefono centrale. Carichi, prelievi e ubicazione invece sì."
        }
        val dept = department(scope)
        require(dept.isEmpty() || category in dept) {
            "Articolo fuori dal tuo reparto (${dept.joinToString(", ")}): la scheda la modifica il telefono centrale"
        }
        return DetailsEdit.REVIEW
    }

    /**
     * Central: what satellite [letter] may do. [maintainer] adds the changes of article cards (validated
     * here). No category (or, for a maintainer, every one) = the whole warehouse. Reaches it at the next exchange.
     */
    fun setScope(letter: String, categories: List<String>, maintainer: Boolean = false) {
        require(setting("role") == "central") { "I reparti li assegna il telefono centrale" }
        require(ArticleCodes.isSatelliteLetter(letter)) { "Lettera non valida" }
        department(categories).forEach { ArticleCodes.prefix(it) }
        val all = ArticleCodes.categoryPrefixes.keys
        val chosen = all.filter { it in categories }
        when {
            chosen.isEmpty() || (maintainer && chosen.size == all.size) -> db.delete("team_sync_settings", "key=?", arrayOf("scope:$letter"))
            maintainer -> TeamSyncSchema.put(db, "scope:$letter", (listOf(MAINTAINER) + chosen).joinToString("\n"))
            else -> TeamSyncSchema.put(db, "scope:$letter", chosen.joinToString("\n"))
        }
    }
    /**
     * 4.19: satellite [letter] can only withdraw (and lend): no new articles, no loads, no moves, in
     * any category. It travels as a department that matches no category, so every version of the
     * app enforces it, on the satellite and again on the central.
     */
    fun setPickOnly(letter: String) {
        require(setting("role") == "central") { "I permessi li assegna il telefono centrale" }
        require(ArticleCodes.isSatelliteLetter(letter)) { "Lettera non valida" }
        TeamSyncSchema.put(db, "scope:$letter", PICK_ONLY)
    }
    private fun scopes(): Map<String, List<String>> =
        db.rawQuery("SELECT key,value FROM team_sync_settings WHERE key LIKE 'scope:%'", null).use { c ->
            buildMap { while (c.moveToNext()) put(c.getString(0).removePrefix("scope:"), lines(c.getString(1))) } }
    private fun lines(value: String?) = value.orEmpty().split('\n').filter { it.isNotBlank() }

    /**
     * Runs [block] as the return of something this phone took (undo of a withdrawal, a loan
     * coming back): allowed even outside the department.
     */
    fun <T> returning(block: () -> T): T {
        if (setting("role") != "satellite") return block()
        val sql = db
        sql.beginTransaction()   // the flag can never stay on: it is rolled back with the block
        try {
            TeamSyncSchema.put(sql, "returning", "1")
            val result = block()
            TeamSyncSchema.put(sql, "returning", "0")
            sql.setTransactionSuccessful()
            return result
        } finally { sql.endTransaction() }
    }

    /** Central only: the team password, to show it as a QR for new phones. */
    fun teamPassword(): String? = if (setting("role") == "central") teamPw() else null

    /** 4.11: the team password, kept sealed by the Android Keystore; legacy plain values get sealed here. */
    private fun teamPw(): String? {
        val raw = setting("password") ?: return null
        val plain = DeviceSecrets.open(raw) ?: return null
        if (!DeviceSecrets.isSealed(raw)) DeviceSecrets.seal(plain).takeIf { it != raw }?.let { TeamSyncSchema.put(db, "password", it) }
        return plain
    }
    private fun requireTeamPw(): String = teamPw()
        ?: throw IllegalArgumentException("Reinserisci la password della squadra: Impostazioni › Sincronizzazione")

    /** After a backup restored on another phone: the password must be typed again (it is never exported readable). */
    fun restorePassword(password: String) {
        requirePassword(password)
        require(setting("role") != null) { "Questo telefono non è collegato a una sincronizzazione" }
        TeamSyncSchema.put(db, "password", DeviceSecrets.seal(password))
    }

    /** Central: a new one-time code for the join QR (valid [INVITE_MINUTES] minutes, any number of phones). */
    fun createInvite(): String {
        require(setting("role") == "central") { "Il QR lo mostra il telefono centrale" }
        // Reuse a code with time left: a phone that already scanned the QR must not find it replaced.
        val until = setting("invite_until")?.toLongOrNull() ?: 0L
        if (until - System.currentTimeMillis() > INVITE_REUSE_MS) activeInvite()?.let { return it }
        val random = java.security.SecureRandom()
        val code = (1..10).map { INVITE_ALPHABET[random.nextInt(INVITE_ALPHABET.length)] }.joinToString("")
        TeamSyncSchema.put(db, "invite_code", DeviceSecrets.seal(code))
        TeamSyncSchema.put(db, "invite_until", (System.currentTimeMillis() + INVITE_MINUTES * 60_000L).toString())
        return code
    }
    private fun activeInvite(): String? {
        val until = setting("invite_until")?.toLongOrNull() ?: return null
        if (System.currentTimeMillis() > until) return null
        return setting("invite_code")?.let { DeviceSecrets.open(it) }
    }
    /** Central, answering a phone that joined with the QR code: the team password, encrypted with that code. */
    fun inviteKey(out: OutputStream, code: String) =
        write(out, JSONObject().put("format", KEY).put("version", 1).put("password", requireTeamPw()), code)
    /** Satellite: the team password received after joining with the QR code. */
    fun openInviteKey(input: InputStream, code: String): String = read(input, code, KEY).getString("password")

    /** Identifies the shared warehouse (the same on the central and on every satellite). */
    fun warehouseUid(): String? = if (setting("role") == null) null else setting("warehouse_uid")

    fun dismissConflicts() { db.delete("team_sync_conflicts", null, null) }

    // ---------------------------------------------------------------- 4.21 cards changed by maintainers

    /** The card of [itemId] as it travels in a change request, with its global id; null when it is gone. */
    private fun cardOf(sql: SQLiteDatabase, itemId: Long): Pair<String, JSONObject>? =
        sql.rawQuery("SELECT uid,sku,name,category,minimum,unit,note,unit_cost,supplier,lead_days FROM items WHERE id=?", arrayOf(itemId.toString())).use { c ->
            if (!c.moveToFirst()) null else c.getString(0) to card(c.getString(1), c.getString(2), c.getString(3), c.getDouble(4), c.getString(5),
                c.getString(6), if (c.isNull(7)) null else c.getDouble(7), c.getString(8), if (c.isNull(9)) null else c.getInt(9))
        }
    private fun plain(v: Any?): String = when (v) { null, JSONObject.NULL -> ""; is Number -> v.toDouble().toString(); else -> v.toString().trim() }
    private fun shown(key: String, v: Any?): String {
        val text = plain(v)
        if (text.isEmpty()) return "—"
        // A number may arrive as text from another version: never let it break the screen.
        val n = (v as? Number)?.toDouble() ?: text.toDoubleOrNull()
        return when {
            key !in setOf("minimum", "cost", "lead") || n == null -> v.toString()
            key == "minimum" -> amount(n)
            key == "cost" -> "€ " + amount(n)
            else -> "${n.toInt()} giorni"
        }
    }

    /**
     * Satellite, maintainer: asks the central to change the card of [itemId] to [after] (see [card]).
     * Only the fields that differ travel. The article stays as it is here until the central validates:
     * the change then arrives with its update, like every other change of the central.
     */
    fun requestEdit(itemId: Long, after: JSONObject) {
        require(detailsEdit(itemId) == DetailsEdit.REVIEW) { "Questa scheda si modifica direttamente" }
        val sql = db
        val (itemUid, current) = requireNotNull(cardOf(sql, itemId)) { "Articolo inesistente" }
        val dept = department(lines(setting("scope")))
        val category = after.optString("category", current.getString("category"))
        require(dept.isEmpty() || category in dept) { "Categoria fuori dal tuo reparto (${dept.joinToString(", ")})" }
        sql.beginTransaction()
        try {
            // A change not yet sent for the same article is replaced (its "before" stays the original one).
            val earlier = sql.rawQuery("SELECT changes FROM team_edits WHERE item_uid=? AND status='sending'", arrayOf(itemUid))
                .use { if (it.moveToFirst()) JSONObject(it.getString(0)).optJSONObject("before") else null }
            val base = JSONObject(current.toString()).apply { earlier?.let { e -> e.keys().forEach { k -> put(k, e.get(k)) } } }
            val changed = JSONObject(); val before = JSONObject()
            for (key in EDIT_FIELDS.keys) if (after.has(key) && plain(after.get(key)) != plain(base.opt(key))) {
                changed.put(key, after.get(key)); before.put(key, base.opt(key) ?: JSONObject.NULL)
            }
            require(changed.length() > 0) { "Nessun campo cambiato rispetto alla scheda attuale" }
            // The same checks the central will make when it applies the change.
            articleValues(JSONObject(current.toString()).apply { changed.keys().forEach { put(it, changed.get(it)) } })
            sql.delete("team_edits", "item_uid=? AND status IN ('sending','rejected')", arrayOf(itemUid))
            sql.insertOrThrow("team_edits", null, ContentValues().apply {
                put("uid", UUID.randomUUID().toString()); put("letter", setting("letter").orEmpty()); put("item_uid", itemUid)
                put("item_label", "${current.getString("sku")} · ${current.getString("name")}".take(200))
                put("changes", JSONObject().put("after", changed).put("before", before).toString())
                put("created_at", System.currentTimeMillis()); put("status", "sending")
            })
            sql.setTransactionSuccessful()
        } finally { sql.endTransaction() }
    }

    /** Satellite: forgets an answered change (approved, or sent back and not to be done again). */
    fun dismissEdit(uid: String) { db.delete("team_edits", "uid=? AND status IN ('approved','rejected')", arrayOf(uid)) }

    /** Central: applies a maintainer's change to the card. Refused (with the reason) when it no longer fits. */
    fun approveEdit(uid: String) {
        require(setting("role") == "central") { "Le modifiche le valida il telefono centrale" }
        val sql = db
        sql.beginTransaction()
        try {
            val (itemUid, changes) = requireNotNull(sql.rawQuery("SELECT item_uid,changes FROM team_edits WHERE uid=? AND status='pending'", arrayOf(uid))
                .use { if (it.moveToFirst()) it.getString(0) to JSONObject(it.getString(1)) else null }) { "Modifica già gestita" }
            val (itemId, site) = requireNotNull(teamItem(sql, teamIds(), itemUid)) { "L'articolo non esiste più: rimanda indietro la modifica" }
            val card = requireNotNull(cardOf(sql, itemId)).second
            val after = changes.getJSONObject("after")
            for (key in EDIT_FIELDS.keys) if (after.has(key)) card.put(key, after.get(key))
            articleValues(card)
            stock.updateItemDetails(site, itemId, card.getString("name"), card.getString("category"), card.getDouble("minimum"),
                card.getString("unit"), card.getString("note"), if (card.isNull("cost")) null else card.getDouble("cost"),
                card.getString("supplier"), if (card.isNull("lead")) null else card.getInt("lead"), sku = card.getString("sku"))
            decide(sql, uid, "approved", "")
            sql.setTransactionSuccessful()
        } finally { sql.endTransaction() }
    }

    /** Central: sends a maintainer's change back, with what to do again. The card does not change. */
    fun rejectEdit(uid: String, reason: String) {
        require(setting("role") == "central") { "Le modifiche le valida il telefono centrale" }
        require(decide(db, uid, "rejected", reason.trim().take(300).ifEmpty { "Da rifare" }) == 1) { "Modifica già gestita" }
    }
    private fun decide(sql: SQLiteDatabase, uid: String, status: String, reason: String): Int =
        sql.update("team_edits", ContentValues().apply { put("status", status); put("reason", reason); put("decided_at", System.currentTimeMillis()) },
            "uid=? AND status='pending'", arrayOf(uid))

    /** Central: a change received from satellite [letter]; checked again here, whatever version made it. */
    private fun receiveEdit(sql: SQLiteDatabase, letter: String, scope: Set<String>?, ids: List<Long>, e: JSONObject): Boolean {
        val uid = e.getString("uid")
        require(uid.length in 8..64) { "Modifica non valida nel file" }
        if (sql.rawQuery("SELECT 1 FROM team_edits WHERE uid=?", arrayOf(uid)).use { it.moveToFirst() }) return false
        val after = JSONObject(); val before = JSONObject()
        val change = e.getJSONObject("change")
        for (key in EDIT_FIELDS.keys) {
            if (change.getJSONObject("after").has(key)) after.put(key, change.getJSONObject("after").get(key))
            change.optJSONObject("before")?.takeIf { it.has(key) }?.let { before.put(key, it.get(key)) }
        }
        val stored = JSONObject().put("after", after).put("before", before).toString()
        require(stored.length <= MAX_EDIT_JSON && after.length() > 0) { "Modifica non valida nel file" }
        val found = teamItem(sql, ids, e.getString("item"))
        val category = found?.let { f -> sql.rawQuery("SELECT category FROM items WHERE id=?", arrayOf(f.first.toString())).use { it.moveToFirst(); it.getString(0) } }
        val dept = department(scope.orEmpty())
        val refusal = when {
            found == null -> "L'articolo non esiste più"
            !isMaintainer(scope.orEmpty()) -> "Il telefono $letter non è un manutentore"
            dept.isNotEmpty() && (category.orEmpty() !in dept || after.optString("category", category.orEmpty()) !in dept) ->
                "Articolo fuori dal reparto del telefono $letter"
            // The same checks as when it is approved: a card that could never be saved is sent back at once.
            runCatching { articleValues(cardOf(sql, found.first)!!.second.apply { after.keys().forEach { put(it, after.get(it)) } }) }.isFailure ->
                "Valori non validi nella modifica"
            else -> null
        }
        sql.insertOrThrow("team_edits", null, ContentValues().apply {
            put("uid", uid); put("letter", letter); put("item_uid", e.getString("item")); put("item_label", e.optString("label").take(200))
            put("changes", stored); put("created_at", e.optLong("at", System.currentTimeMillis()))
            put("status", if (refusal == null) "pending" else "rejected"); put("reason", refusal.orEmpty())
            put("decided_at", if (refusal == null) 0L else System.currentTimeMillis())
        })
        return refusal == null
    }

    /** What the screens show: on the central the changes to validate, on a satellite its own ones. */
    private fun edits(role: TeamRole): List<TeamEdit> {
        val read = db
        val where = if (role == TeamRole.CENTRAL) "status='pending'"
            else "status IN ('sending','pending','rejected') OR (status='approved' AND decided_at>${System.currentTimeMillis() - 7 * 86_400_000L})"
        val ids = teamIds()
        return read.rawQuery("SELECT uid,letter,item_uid,item_label,changes,created_at,status,reason,decided_at FROM team_edits WHERE $where ORDER BY created_at",
            null).use { c -> buildList {
            while (c.moveToNext()) {
                val changes = runCatching { JSONObject(c.getString(4)) }.getOrNull() ?: continue
                val after = changes.optJSONObject("after") ?: continue
                val itemId = teamItem(read, ids, c.getString(2))?.first
                // On the central "before" is the card as it is now; on a satellite, as it was when the change was made.
                val before = if (role == TeamRole.CENTRAL) itemId?.let { cardOf(read, it)?.second } ?: JSONObject()
                    else changes.optJSONObject("before") ?: JSONObject()
                val fields = EDIT_FIELDS.filterKeys { after.has(it) }.map { (key, label) ->
                    TeamEditField(key, label, shown(key, before.opt(key)), shown(key, after.get(key))) }
                add(TeamEdit(c.getString(0), c.getString(1), itemId, c.getString(3), fields, c.getLong(5), c.getString(6), c.getString(7), c.getLong(8)))
            }
        } }
    }

    // ---------------------------------------------------------------- central

    /** Plan, structures and articles of one site, as they travel to the satellites. */
    private fun siteJson(read: SQLiteDatabase, warehouseId: Long): JSONObject {
        val state = JSONObject()
        read.rawQuery("SELECT name,location,map_width,map_height FROM warehouses WHERE id=?", arrayOf(warehouseId.toString())).use { c ->
            require(c.moveToFirst()) { "Il magazzino sincronizzato non esiste più" }
            state.put("warehouse", JSONObject().put("name", c.getString(0)).put("location", c.getString(1))
                .put("width", c.getDouble(2)).put("height", c.getDouble(3))
                // 4.18: the number printed on this phone's labels for the site.
                .put("label_id", warehouseId))
        }
        val placements = JSONArray()
        read.rawQuery("SELECT uid,kind,label,x,y,locked,width_m,depth_m,rotation_deg,levels,slots FROM placements WHERE warehouse_id=? ORDER BY id",
            arrayOf(warehouseId.toString())).use { c -> while (c.moveToNext()) placements.put(JSONObject()
                .put("uid", c.getString(0)).put("kind", c.getString(1)).put("label", c.getString(2))
                .put("x", c.getDouble(3)).put("y", c.getDouble(4)).put("locked", c.getInt(5) != 0)
                .put("width", c.getDouble(6)).put("depth", c.getDouble(7)).put("rotation", c.getDouble(8))
                .put("levels", c.getInt(9)).put("slots", c.getInt(10))) }
        val walls = JSONArray()
        read.rawQuery("SELECT x1,y1,x2,y2,source FROM walls WHERE warehouse_id=? ORDER BY id", arrayOf(warehouseId.toString())).use { c ->
            while (c.moveToNext()) walls.put(JSONObject().put("x1", c.getDouble(0)).put("y1", c.getDouble(1))
                .put("x2", c.getDouble(2)).put("y2", c.getDouble(3)).put("source", c.getString(4))) }
        val items = JSONArray()
        read.rawQuery("""SELECT i.uid,i.sku,i.name,i.category,i.quantity,i.minimum,i.unit,p.uid,i.shelf_level,i.shelf_slot,
            i.note,i.unit_cost,i.supplier,i.lead_days,i.photo_file FROM items i LEFT JOIN placements p ON p.id=i.placement_id
            WHERE i.warehouse_id=? ORDER BY i.id""", arrayOf(warehouseId.toString())).use { c ->
            while (c.moveToNext()) items.put(itemJson(c.getString(0), c.getString(1), c.getString(2), c.getString(3), c.getDouble(4),
                c.getDouble(5), c.getString(6), if (c.isNull(7)) null else c.getString(7), if (c.isNull(8)) null else c.getInt(8),
                if (c.isNull(9)) null else c.getInt(9), c.getString(10), if (c.isNull(11)) null else c.getDouble(11),
                c.getString(12), if (c.isNull(13)) null else c.getInt(13))
                // 4.15: name of the photo (a new photo has a new name, so the name alone tells when it changed).
                .put("photo", if (c.isNull(14)) JSONObject.NULL else c.getString(14)))
        }
        return state.put("placements", placements).put("walls", walls).put("items", items)
    }

    /** Complete state for every phone. */
    /** 4.24.1: read in one transaction, so a satellite's file applied meanwhile cannot make the update inconsistent. */
    fun exportState(out: OutputStream) = consistent { exportStateNow(out) }
    fun exportChanges(out: OutputStream): Int = consistent { exportChangesNow(out) }
    private fun <T> consistent(block: () -> T): T {
        val sql = db
        sql.beginTransaction()
        try { return block().also { sql.setTransactionSuccessful() } } finally { sql.endTransaction() }
    }

    private fun exportStateNow(out: OutputStream) {
        require(setting("role") == "central") { "Solo il telefono centrale invia l'aggiornamento" }
        val warehouseId = setting("warehouse_id")!!.toLong()
        val read = stock.readableDatabase
        val state = JSONObject().put("format", SNAPSHOT).put("version", 1)
            .put("warehouse_uid", setting("warehouse_uid")).put("central", deviceUid())
            .put("created_at", System.currentTimeMillis())
        val site = siteJson(read, warehouseId)
        for (key in listOf("warehouse", "placements", "walls", "items")) state.put(key, site.get(key))
        // 4.18: the other sites of the company (older versions ignore this field and keep working on the first site).
        val sites = extraSites()
        val shared = JSONArray()
        for ((id, uid) in sites) if (companyOf(id) != null) shared.put(siteJson(read, id).put("warehouse_uid", uid))
        if (shared.length() > 0) state.put("sites", shared)
        val processed = JSONObject(); val letters = JSONObject()
        read.rawQuery("SELECT device_uid,letter,processed_seq FROM team_sync_peers", null).use { c ->
            while (c.moveToNext()) { processed.put(c.getString(0), c.getLong(2)); letters.put(c.getString(0), c.getString(1)) }
        }
        state.put("processed", processed).put("letters", letters)
            .put("scopes", JSONObject().apply { scopes().forEach { (l, c) -> put(l, JSONArray(c)) } })
        // 4.15: the photos the satellites said they miss, within a size limit; the rest follows at the next exchanges.
        val wanted = LinkedHashSet<String>()
        read.rawQuery("SELECT value FROM team_sync_settings WHERE key LIKE 'photo_wants:%'", null).use { c -> while (c.moveToNext()) wanted += lines(c.getString(0)) }
        state.put("photos", photosJson(wanted))
        // 4.15: who has which tool, from every phone (older versions ignore this field).
        val loans = ownLoans(read, warehouseId, "A")
        for (id in sites.keys) ownLoans(read, id, "A", loans)
        read.rawQuery("SELECT letter,item_uid,borrower,quantity,created_at FROM team_loans ORDER BY created_at", null).use { c ->
            while (c.moveToNext()) loans.put(JSONObject().put("letter", c.getString(0)).put("item", c.getString(1)).put("borrower", c.getString(2))
                .put("quantity", c.getDouble(3)).put("at", c.getLong(4)))
        }
        state.put("loans", loans)
        // 4.21: what the central did with the maintainers' card changes (each satellite reads its own).
        val reviews = JSONArray()
        read.rawQuery("SELECT uid,letter,status,reason,decided_at FROM team_edits ORDER BY created_at DESC LIMIT $MAX_EDITS", null).use { c ->
            while (c.moveToNext()) reviews.put(JSONObject().put("uid", c.getString(0)).put("letter", c.getString(1)).put("status", c.getString(2))
                .put("reason", c.getString(3)).put("decided_at", c.getLong(4)))
        }
        state.put("edit_reviews", reviews)
        write(out, state)
        TeamSyncSchema.put(db, "last_sync", System.currentTimeMillis().toString())
    }

    /** Applies a satellite's changes. All or nothing; already processed changes are skipped. */
    fun importChanges(input: InputStream): TeamImportResult {
        require(setting("role") == "central") { "Le modifiche dei satelliti si importano sul telefono centrale" }
        val changes = read(input, requireTeamPw(), CHANGES)
        require(changes.getString("warehouse_uid") == setting("warehouse_uid")) { "Il file appartiene a un altro magazzino" }
        val device = changes.getString("device_uid"); val letter = changes.getString("letter")
        require(ArticleCodes.isSatelliteLetter(letter)) { "Lettera del telefono non valida" }
        val warehouseId = setting("warehouse_id")!!.toLong()
        val sql = db
        sql.beginTransaction()
        try {
            val known = sql.rawQuery("SELECT letter,processed_seq FROM team_sync_peers WHERE device_uid=?", arrayOf(device)).use {
                if (it.moveToFirst()) it.getString(0) to it.getLong(1) else null }
            val owner = sql.rawQuery("SELECT device_uid FROM team_sync_peers WHERE letter=?", arrayOf(letter)).use {
                if (it.moveToFirst()) it.getString(0) else null }
            require(owner == null || owner == device) { "La lettera $letter è già di un altro telefono: ricollega il satellite con un'altra lettera" }
            if (known == null) sql.insertOrThrow("team_sync_peers", null, ContentValues().apply {
                put("device_uid", device); put("letter", letter) })
            else if (known.first != letter) sql.update("team_sync_peers", ContentValues().apply { put("letter", letter) },
                "device_uid=?", arrayOf(device))
            var processed = known?.second ?: 0L
            // 4.15: movements applied from this file belong to that satellite (rolled back with the transaction on failure).
            TeamSyncSchema.put(sql, "actor", letter)
            val scope = setting("scope:$letter")?.let { lines(it).toSet() }
            val ids = teamIds()
            val siteOf = extraSites().entries.associate { it.value to it.key } + (setting("warehouse_uid").orEmpty() to warehouseId)
            fun outside(itemId: Long?): Boolean = scope != null && itemId != null &&
                sql.rawQuery("SELECT category FROM items WHERE id=?", arrayOf(itemId.toString())).use { it.moveToFirst() && it.getString(0) !in scope }
            val events = changes.getJSONArray("events")
            require(events.length() <= MAX_EVENTS) { "File troppo grande" }
            var articles = 0; var moves = 0; var locations = 0; var skipped = 0; var conflicts = 0
            for (i in 0 until events.length()) {
                val e = events.getJSONObject(i)
                val seq = e.getLong("seq")
                if (seq <= processed) { skipped++; continue }
                // Each file holds every change not yet confirmed, so a gap in the numbering only
                // follows a forced disconnection or a restored backup: nothing to wait for.
                when (e.getString("kind")) {
                    "ITEM" -> {
                        val o = e.getJSONObject("item")
                        if (scope != null && o.getString("category") !in scope) {
                            conflict(sql, letter, "${o.getString("sku")} · ${o.getString("name")}",
                                "articolo non creato: la categoria ${o.getString("category")} non è del reparto $letter"); conflicts++
                        } else {
                            // A bad article (e.g. a code already taken) must not block every later file of this phone.
                            val inserted = runCatching {
                                // 4.18: the site the article was created in (none = the first, as before).
                                val site = e.optString("site").let { uid -> if (uid.isEmpty()) warehouseId else siteOf[uid] ?: throw IllegalArgumentException("sede non condivisa con la squadra") }
                                if (insertArticle(sql, site, e)) site else null
                            }
                            inserted.onSuccess { if (it != null) { articles++; receivePhoto(sql, it, o.getString("uid"), e.optString("photo"), changes.optJSONObject("photos")) } else skipped++ }.onFailure {
                                conflict(sql, letter, "${o.optString("sku")} · ${o.optString("name")}",
                                    "articolo non creato: ${it.message ?: "dati non validi"}"); conflicts++
                            }
                        }
                    }
                    "MOVE" -> {
                        val itemId = teamItem(sql, ids, e.getString("item"))?.first
                        val note = "$letter · " + e.getString("note").take(240)
                        val failure = if (itemId == null) "articolo sconosciuto"
                            else if (e.getDouble("delta") > 0 && outside(itemId) && !e.getString("note").startsWith("Annullato") &&
                                !e.getString("note").startsWith("Restituito")) "carico fuori dal reparto $letter"
                            else runCatching {
                            stock.applyTeamMovement(sql, itemId, e.getDouble("delta"), note, e.getLong("at"))
                        }.exceptionOrNull()?.let { it.message ?: "movimento non applicabile" }
                        if (failure == null) moves++ else {
                            conflict(sql, letter, itemLabel(sql, itemId, e.getString("item")),
                                "${signed(e.getDouble("delta"))} «${e.getString("note").take(80)}» non applicato: $failure"); conflicts++
                        }
                    }
                    "LOCATE" -> {
                        val found = teamItem(sql, ids, e.getString("item"))
                        val itemId = found?.first
                        val failure = if (itemId == null) "articolo sconosciuto"
                            else if (outside(itemId)) "spostamento fuori dal reparto $letter"
                            else runCatching {
                            locate(sql, found!!.second, itemId, e.optString("placement").ifEmpty { null },
                                e.optInt("level").takeIf { e.has("level") && !e.isNull("level") },
                                e.optInt("slot").takeIf { e.has("slot") && !e.isNull("slot") })
                        }.exceptionOrNull()?.let { it.message ?: "ubicazione non applicabile" }
                        if (failure == null) locations++ else {
                            conflict(sql, letter, itemLabel(sql, itemId, e.getString("item")), "spostamento non applicato: $failure"); conflicts++
                        }
                    }
                    "SKIP" -> skipped++
                    else -> throw IllegalArgumentException("Modifica sconosciuta nel file")
                }
                processed = maxOf(processed, seq)   // a file out of order never moves the mark back
            }
            sql.update("team_sync_peers", ContentValues().apply { put("processed_seq", processed); put("last_import", System.currentTimeMillis()) },
                "device_uid=?", arrayOf(device))
            TeamSyncSchema.put(sql, "actor", "")
            // 4.21: card changes of a maintainer wait here for validation (older files have none).
            var edits = 0
            changes.optJSONArray("edits")?.let { list ->
                require(list.length() <= MAX_EDITS) { "File troppo grande" }
                for (i in 0 until list.length()) if (receiveEdit(sql, letter, scope, ids, list.getJSONObject(i))) edits++
            }
            sql.delete("team_edits", "status<>'pending' AND decided_at>0 AND decided_at<?",
                arrayOf((System.currentTimeMillis() - 180 * 86_400_000L).toString()))
            // 4.15: the satellite's open loans replace what was known of it (a file of an older version has none: nothing changes).
            changes.optJSONArray("photo_offers")?.let { offers ->
                for (i in 0 until minOf(offers.length(), MAX_PHOTO_WANTS)) {
                    val o = offers.optJSONObject(i) ?: continue
                    val target = teamItem(sql, ids, o.optString("item")) ?: continue
                    val free = sql.rawQuery("SELECT 1 FROM items WHERE id=? AND photo_file IS NULL", arrayOf(target.first.toString())).use { it.moveToFirst() }
                    if (free) receivePhoto(sql, target.second, o.optString("item"), o.optString("photo"), changes.optJSONObject("photos"))
                }
            }
            changes.optJSONArray("photo_wants")?.let { wants ->
                TeamSyncSchema.put(sql, "photo_wants:$letter", (0 until minOf(wants.length(), MAX_PHOTO_WANTS)).joinToString("\n") { wants.optString(it) })
            }
            changes.optJSONArray("loans")?.let { loans ->
                sql.delete("team_loans", "letter=?", arrayOf(letter))
                storeLoans(sql, loans, forced = letter, skip = null)
            }
            sql.setTransactionSuccessful()
            return TeamImportResult(articles, moves, locations, skipped, conflicts, letter, edits = edits)
        } finally { sql.endTransaction() }
    }

    // ---------------------------------------------------------------- satellite

    /** Everything not yet confirmed by the central phone. */
    private fun exportChangesNow(out: OutputStream): Int {
        require(setting("role") == "satellite") { "Solo un satellite invia le modifiche al centrale" }
        val read = stock.readableDatabase
        val events = JSONArray()
        val newPhotos = LinkedHashSet<String>()
        val siteUid = extraSites() + (setting("warehouse_id")!!.toLong() to setting("warehouse_uid").orEmpty())
        read.rawQuery("SELECT seq,kind,ref_id,created_at FROM team_sync_outbox ORDER BY seq", null).use { c ->
            while (c.moveToNext()) {
                val seq = c.getLong(0); val ref = c.getLong(2)
                val e = JSONObject().put("seq", seq).put("kind", c.getString(1))
                when (c.getString(1)) {
                    "ITEM" -> read.rawQuery("""SELECT i.uid,i.sku,i.name,i.category,i.minimum,i.unit,p.uid,i.shelf_level,i.shelf_slot,
                        i.note,i.unit_cost,i.supplier,i.lead_days FROM items i LEFT JOIN placements p ON p.id=i.placement_id WHERE i.id=?""",
                        arrayOf(ref.toString())).use { r ->
                        // The quantity travels as movements: the article itself is sent empty.
                        if (r.moveToFirst()) e.put("item", itemJson(r.getString(0), r.getString(1), r.getString(2), r.getString(3), 0.0,
                            r.getDouble(4), r.getString(5), if (r.isNull(6)) null else r.getString(6), if (r.isNull(7)) null else r.getInt(7),
                            if (r.isNull(8)) null else r.getInt(8), r.getString(9), if (r.isNull(10)) null else r.getDouble(10),
                            r.getString(11), if (r.isNull(12)) null else r.getInt(12)))
                        else e.put("kind", "NONE")
                        // 4.18: the site it was created in.
                        read.rawQuery("SELECT warehouse_id FROM items WHERE id=?", arrayOf(ref.toString())).use { w ->
                            if (w.moveToFirst()) siteUid[w.getLong(0)]?.let { e.put("site", it) }
                        }
                        // 4.15: the photo of a new article travels with it.
                        read.rawQuery("SELECT photo_file FROM items WHERE id=? AND photo_file IS NOT NULL", arrayOf(ref.toString())).use { p ->
                            if (p.moveToFirst()) { e.put("photo", p.getString(0)); newPhotos += p.getString(0) }
                        }
                    }
                    "MOVE" -> read.rawQuery("SELECT i.uid,m.delta,m.note,m.created_at FROM movements m JOIN items i ON i.id=m.item_id WHERE m.id=?",
                        arrayOf(ref.toString())).use { r ->
                        if (r.moveToFirst()) e.put("item", r.getString(0)).put("delta", r.getDouble(1)).put("note", r.getString(2)).put("at", r.getLong(3))
                        else e.put("kind", "NONE")
                    }
                    "LOCATE" -> read.rawQuery("SELECT i.uid,p.uid,i.shelf_level,i.shelf_slot FROM items i LEFT JOIN placements p ON p.id=i.placement_id WHERE i.id=?",
                        arrayOf(ref.toString())).use { r ->
                        if (r.moveToFirst()) e.put("item", r.getString(0)).put("placement", if (r.isNull(1)) "" else r.getString(1))
                            .put("level", if (r.isNull(2)) JSONObject.NULL else r.getInt(2)).put("slot", if (r.isNull(3)) JSONObject.NULL else r.getInt(3))
                        else e.put("kind", "NONE")
                    }
                }
                // A row deleted meanwhile still keeps its place in the sequence, as an empty change.
                if (e.getString("kind") == "NONE") e.put("kind", "SKIP")
                events.put(e)
            }
        }
        // 4.16: photos taken here for articles the central has no photo of.
        val offers = JSONArray()
        for (uid in lines(setting("photo_offers"))) read.rawQuery("SELECT photo_file FROM items WHERE uid=? AND photo_file IS NOT NULL", arrayOf(uid)).use { p ->
            if (p.moveToFirst()) { offers.put(JSONObject().put("item", uid).put("photo", p.getString(0))); newPhotos += p.getString(0) }
        }
        // 4.21: card changes not yet received by the central (sent again until it confirms them).
        val edits = JSONArray()
        read.rawQuery("SELECT uid,item_uid,item_label,changes,created_at FROM team_edits WHERE status='sending' ORDER BY created_at", null).use { c ->
            while (c.moveToNext()) edits.put(JSONObject().put("uid", c.getString(0)).put("item", c.getString(1)).put("label", c.getString(2))
                .put("change", JSONObject(c.getString(3))).put("at", c.getLong(4)))
        }
        write(out, JSONObject().put("format", CHANGES).put("version", 1).put("warehouse_uid", setting("warehouse_uid")).put("edits", edits)
            .put("device_uid", deviceUid()).put("letter", setting("letter")).put("created_at", System.currentTimeMillis())
            .put("events", events).put("photos", photosJson(newPhotos))
            .put("photo_wants", JSONArray(lines(setting("photo_wants")).take(MAX_PHOTO_WANTS))).put("photo_offers", offers).put("loans", teamIds().fold(JSONArray()) { all, id -> ownLoans(read, id, setting("letter").orEmpty(), all) }))
        return events.length() + edits.length()
    }

    /** Central update on a satellite: map, articles and quantities, keeping unsent local changes. */
    fun importState(input: InputStream): TeamImportResult {
        require(setting("role") == "satellite") { "Collega prima il telefono come satellite" }
        val state = read(input, requireTeamPw(), SNAPSHOT)
        require(state.getString("warehouse_uid") == setting("warehouse_uid")) { "Il file appartiene a un altro magazzino" }
        // An older state would delete articles created since and orphan movements still in the outbox.
        val stateAt = state.optLong("created_at", 0L)
        require(stateAt == 0L || stateAt >= (setting("state_at")?.toLongOrNull() ?: 0L)) {
            "Questo aggiornamento del centrale è più vecchio di quello già importato: chiedi al centrale un file nuovo"
        }
        val sql = db
        sql.beginTransaction()
        removedPhotos.clear()
        // 4.25: the transaction ends exactly once; the photos go only after a successful commit.
        val result = try {
            applyState(sql, setting("warehouse_id")!!.toLong(), state).also { sql.setTransactionSuccessful() }
        } catch (e: Throwable) { removedPhotos.clear(); throw e }
        finally { sql.endTransaction() }
        dropRemovedPhotos()
        return result
    }

    /** One site of a central update: plan, structures and articles, keeping the local changes not yet confirmed. */
    private fun applySite(sql: SQLiteDatabase, warehouseId: Long, state: JSONObject, pendingDelta: Map<Long, Double>,
                          pendingLocate: Set<Long>, pendingItems: Set<Long>): Triple<Int, Int, JSONArray> {
        val room = state.getJSONObject("warehouse")
        sql.update("warehouses", ContentValues().apply {
            put("name", room.getString("name").take(160)); put("location", room.optString("location").take(300))
            put("map_width", dimension(room.getDouble("width"))); put("map_height", dimension(room.getDouble("height")))
        }, "id=?", arrayOf(warehouseId.toString()))
        // Map: the central's map replaces the local one.
        val placements = state.getJSONArray("placements")
        val keep = mutableSetOf<String>()
        for (i in 0 until placements.length()) {
            val p = placements.getJSONObject(i)
            val uid = p.getString("uid"); keep += uid
            val values = ContentValues().apply {
                put("warehouse_id", warehouseId); put("kind", p.getString("kind")); put("label", p.getString("label").take(80))
                put("x", p.getDouble("x")); put("y", p.getDouble("y")); put("locked", if (p.optBoolean("locked")) 1 else 0)
                put("width_m", p.getDouble("width")); put("depth_m", p.getDouble("depth")); put("rotation_deg", p.getDouble("rotation"))
                put("levels", p.getInt("levels")); put("slots", p.getInt("slots")); put("uid", uid)
            }
            if (sql.update("placements", values, "uid=? AND warehouse_id=?", arrayOf(uid, warehouseId.toString())) == 0) {
                freeUid(sql, "placements", uid, warehouseId)
                sql.insertOrThrow("placements", null, values)
            }
        }
        sql.rawQuery("SELECT id,uid FROM placements WHERE warehouse_id=?", arrayOf(warehouseId.toString())).use { c ->
            val gone = buildList { while (c.moveToNext()) if (c.getString(1) !in keep) add(c.getLong(0)) }
            gone.forEach { stock.deletePlacement(it) }
        }
        sql.delete("walls", "warehouse_id=?", arrayOf(warehouseId.toString()))
        val walls = state.getJSONArray("walls")
        for (i in 0 until walls.length()) { val w = walls.getJSONObject(i)
            sql.insertOrThrow("walls", null, ContentValues().apply {
                put("warehouse_id", warehouseId); put("x1", w.getDouble("x1")); put("y1", w.getDouble("y1"))
                put("x2", w.getDouble("x2")); put("y2", w.getDouble("y2")); put("source", w.optString("source", "manual"))
            }) }
        // Articles: central fields and quantity, plus the local changes not yet confirmed.
        val items = state.getJSONArray("items")
        var updated = 0
        val centralItems = HashSet<String>()
        for (i in 0 until items.length()) {
            val it = items.getJSONObject(i)
            val uid = it.getString("uid")
            centralItems += uid
            val local = sql.rawQuery("SELECT id,quantity FROM items WHERE uid=? AND warehouse_id=?", arrayOf(uid, warehouseId.toString())).use { c -> if (c.moveToFirst()) c.getLong(0) to c.getDouble(1) else null }
            // Central quantity plus what this phone moved and has not sent yet (never below zero).
            val target = StockRules.difference(it.getDouble("quantity"), -(local?.let { l -> pendingDelta[l.first] } ?: 0.0))
                .coerceAtLeast(0.0)
            val values = articleValues(it).apply { put("quantity", target) }
            // 4.24.1: codes swapped on the central (A→B, B→A): the article still holding this code here takes a
            // provisional one, it gets its own code a few lines later in this same update.
            sql.execSQL("UPDATE items SET sku='~'||uid WHERE warehouse_id=? AND sku=? AND uid<>? AND uid IS NOT NULL",
                arrayOf(warehouseId, values.getAsString("sku"), uid))
            if (local == null) {
                values.put("warehouse_id", warehouseId); values.put("uid", uid)
                placeOn(values, placementOf(sql, warehouseId, it))
                freeUid(sql, "items", uid, warehouseId)
                val id = sql.insertOrThrow("items", null, values)
                if (target > 0) stock.recordTeamMovement(sql, id, target, "Allineamento dal centrale", System.currentTimeMillis())
                stock.bumpSequence(sql, it.getString("sku"))
            } else {
                // A location changed here and not yet sent wins over the central one.
                if (local.first !in pendingLocate) placeOn(values, placementOf(sql, warehouseId, it))
                else { values.remove("shelf_level"); values.remove("shelf_slot") }
                sql.update("items", values, "id=?", arrayOf(local.first.toString()))
                val difference = StockRules.difference(target, local.second)
                if (difference != 0.0) stock.recordTeamMovement(sql, local.first, difference, "Allineamento dal centrale", System.currentTimeMillis())
                stock.bumpSequence(sql, it.getString("sku"))
            }
            updated++
        }
        // 4.13: an article the central no longer has was deleted there (or refused): it goes here too,
        // unless this phone created it and has not sent it yet.
        var removed = 0
        sql.rawQuery("SELECT id,uid,photo_file FROM items WHERE warehouse_id=?", arrayOf(warehouseId.toString())).use { c ->
            buildList { while (c.moveToNext()) if (c.getString(1) !in centralItems && c.getLong(0) !in pendingItems)
                add(c.getLong(0) to (if (c.isNull(2)) null else c.getString(2))) }
        }.forEach { (id, photo) ->
            sql.delete("items", "id=?", arrayOf(id.toString())); removed++
            photo?.let { removedPhotos += it }
        }
        return Triple(updated, removed, items)
    }

    private fun applyState(sql: SQLiteDatabase, warehouseId: Long, state: JSONObject): TeamImportResult {
        val me = deviceUid()
        val processed = state.getJSONObject("processed").optLong(me, 0L)
        val letterOwner = state.getJSONObject("letters").let { l -> l.keys().asSequence().firstOrNull { l.getString(it) == setting("letter") } }
        require(letterOwner == null || letterOwner == me) { "La lettera ${setting("letter")} risulta di un altro telefono" }
        state.optLong("created_at", 0L).takeIf { it > 0L }?.let { TeamSyncSchema.put(sql, "state_at", it.toString()) }
        // Changes the central has processed are confirmed: they leave the outbox.
        sql.delete("team_sync_outbox", "seq<=?", arrayOf(processed.toString()))
        // What is still pending stays on top of the central state.
        val pendingDelta = mutableMapOf<Long, Double>()
        val pendingLocate = mutableSetOf<Long>()
        val pendingItems = mutableSetOf<Long>()
        sql.rawQuery("""SELECT o.kind,o.ref_id,m.item_id,m.delta FROM team_sync_outbox o
            LEFT JOIN movements m ON o.kind='MOVE' AND m.id=o.ref_id""", null).use { c ->
            while (c.moveToNext()) when (c.getString(0)) {
                "MOVE" -> if (!c.isNull(2)) pendingDelta[c.getLong(2)] = (pendingDelta[c.getLong(2)] ?: 0.0) + c.getDouble(3)
                "LOCATE" -> pendingLocate.add(c.getLong(1))
                "ITEM" -> pendingItems.add(c.getLong(1))
            }
        }
        TeamSyncSchema.createScopeTriggers(sql)
        val myScope = state.optJSONObject("scopes")?.optJSONArray(setting("letter").orEmpty())
        if (myScope == null || myScope.length() == 0) sql.delete("team_sync_settings", "key='scope'", null)
        else TeamSyncSchema.put(sql, "scope", (0 until myScope.length()).joinToString("\n") { myScope.getString(it) })
        TeamSyncSchema.put(sql, "applying", "1")
        var (updated, removed, items) = applySite(sql, warehouseId, state, pendingDelta, pendingLocate, pendingItems)
        // 4.18: the other sites of the company. A new one is created beside the first; one the central
        // no longer shares stays on this phone as it is, out of the team.
        val knownSites = extraSites().entries.associate { it.value to it.key }
        val nowSites = LinkedHashMap<Long, String>()
        val labels = LinkedHashMap<Long, Long>()
        state.getJSONObject("warehouse").optLong("label_id", 0L).takeIf { it > 0 }?.let { labels[it] = warehouseId }
        val moreItems = ArrayList<Pair<Long, JSONArray>>()
        val company = companyOf(warehouseId)
        state.optJSONArray("sites")?.let { sites ->
            require(sites.length() < MAX_SITES) { "File non valido: troppe sedi" }
            for (i in 0 until sites.length()) {
                val site = sites.getJSONObject(i)
                val uid = site.getString("warehouse_uid")
                require(uid.isNotBlank() && uid != setting("warehouse_uid") && uid !in nowSites.values) { "File non valido: sede ripetuta" }
                val room = site.getJSONObject("warehouse")
                val local = knownSites[uid]?.takeIf { companyOf(it) != null } ?: sql.insertOrThrow("warehouses", null, ContentValues().apply {
                    put("profile_id", requireNotNull(company) { "Sede principale non trovata" }); put("name", room.getString("name").take(160))
                    put("location", room.optString("location").take(300))
                    put("map_width", dimension(room.getDouble("width"))); put("map_height", dimension(room.getDouble("height")))
                })
                nowSites[local] = uid
                room.optLong("label_id", 0L).takeIf { it > 0 }?.let { labels[it] = local }
                val applied = applySite(sql, local, site, pendingDelta, pendingLocate, pendingItems)
                updated += applied.first; removed += applied.second; moreItems += local to applied.third
            }
        }
        saveSites(sql, nowSites)
        // 4.19.2: numbers of labels printed by an earlier central stay known (after a replacement of the central), unless reused.
        for ((printed, local) in labelIds()) if (printed !in labels && companyOf(local) != null) labels[printed] = local
        TeamSyncSchema.put(sql, "label_ids", labels.entries.joinToString("\n") { "${it.key}|${it.value}" })
        TeamSyncSchema.put(sql, "applying", "0")
        TeamSyncSchema.put(sql, "last_sync", System.currentTimeMillis().toString())
        val wants = LinkedHashSet<String>(); val nowFromCentral = LinkedHashSet<String>(); val offers = LinkedHashSet<String>()
        var photosKnown = applyPhotos(sql, warehouseId, items, state.optJSONObject("photos"), pendingItems, wants, nowFromCentral, offers)
        for ((site, list) in moreItems) photosKnown = applyPhotos(sql, site, list, state.optJSONObject("photos"), pendingItems, wants, nowFromCentral, offers) && photosKnown
        if (photosKnown) {
            TeamSyncSchema.put(sql, "photo_wants", wants.take(MAX_PHOTO_WANTS).joinToString("\n"))
            TeamSyncSchema.put(sql, "photos_central", nowFromCentral.joinToString("\n"))
            TeamSyncSchema.put(sql, "photo_offers", offers.take(MAX_PHOTO_WANTS).joinToString("\n"))
        }
        // 4.15: loans of the other phones, and what the central knows of the team (needed if this phone ever replaces it).
        state.optJSONArray("loans")?.let { loans ->
            sql.delete("team_loans", null, null)
            storeLoans(sql, loans, forced = null, skip = setting("letter"))
        }
        // 4.21: answers of the central to this phone's card changes.
        state.optJSONArray("edit_reviews")?.let { reviews ->
            val mine = setting("letter")
            for (i in 0 until minOf(reviews.length(), MAX_EDITS)) {
                val r = reviews.optJSONObject(i) ?: continue
                val status = r.optString("status")
                if (r.optString("letter") != mine || status !in setOf("pending", "approved", "rejected")) continue
                sql.update("team_edits", ContentValues().apply {
                    put("status", status); put("reason", r.optString("reason").take(300)); put("decided_at", r.optLong("decided_at"))
                }, "uid=? AND status IN ('sending','pending')", arrayOf(r.optString("uid")))
            }
            sql.delete("team_edits", "status='approved' AND decided_at<?", arrayOf((System.currentTimeMillis() - 30 * 86_400_000L).toString()))
        }
        TeamSyncSchema.put(sql, "known_peers", JSONObject().put("processed", state.getJSONObject("processed"))
            .put("letters", state.getJSONObject("letters")).put("scopes", state.optJSONObject("scopes") ?: JSONObject()).toString())
        val stillPending = sql.rawQuery("SELECT COUNT(*) FROM team_sync_outbox", null).use { c -> c.moveToFirst(); c.getInt(0) }
        return TeamImportResult(updated, 0, 0, stillPending, 0, removed = removed)
    }

    // ---------------------------------------------------------------- helpers

    private fun itemJson(uid: String, sku: String, name: String, category: String, quantity: Double, minimum: Double, unit: String,
                         placement: String?, level: Int?, slot: Int?, note: String, cost: Double?, supplier: String, lead: Int?) =
        JSONObject().put("uid", uid).put("sku", sku).put("name", name).put("category", category).put("quantity", quantity)
            .put("minimum", minimum).put("unit", unit).put("placement", placement ?: JSONObject.NULL)
            .put("level", level ?: JSONObject.NULL).put("slot", slot ?: JSONObject.NULL).put("note", note)
            .put("cost", cost ?: JSONObject.NULL).put("supplier", supplier).put("lead", lead ?: JSONObject.NULL)

    private fun articleValues(o: JSONObject) = ContentValues().apply {
        val name = o.getString("name").trim(); require(name.length in 1..180) { "Nome articolo non valido nel file" }
        ArticleCodes.prefix(o.getString("category"))
        // Codes from older versions may not follow today's format: any non-empty code is kept.
        require(o.getString("sku").trim().length in 1..40) { "Codice articolo non valido nel file" }
        val minimum = o.getDouble("minimum"); require(StockRules.validQuantity(minimum)) { "Scorta minima non valida nel file" }
        put("sku", o.getString("sku").trim()); put("name", name); put("category", o.getString("category"))
        put("minimum", minimum); put("unit", o.getString("unit").trim().take(24).ifEmpty { "pz" })
        put("note", o.optString("note").take(3000)); put("supplier", o.optString("supplier").take(160))
        if (o.isNull("cost")) putNull("unit_cost") else put("unit_cost", o.getDouble("cost").also { require(it.isFinite() && it >= 0) })
        if (o.isNull("lead")) putNull("lead_days") else put("lead_days", o.getInt("lead").coerceIn(0, 3650))
        if (o.isNull("level")) putNull("shelf_level") else put("shelf_level", o.getInt("level").coerceIn(1, 20))
        if (o.isNull("slot")) putNull("shelf_slot") else put("shelf_slot", o.getInt("slot").coerceIn(1, 50))
    }

    /** No structure means no shelf either (the backup checks enforce it). */
    private fun placeOn(values: ContentValues, placementId: Long?) {
        if (placementId == null) { values.putNull("placement_id"); values.putNull("shelf_level"); values.putNull("shelf_slot") }
        else values.put("placement_id", placementId)
    }
    private fun placementOf(sql: SQLiteDatabase, warehouseId: Long, o: JSONObject): Long? =
        if (o.isNull("placement")) null else sql.rawQuery("SELECT id FROM placements WHERE uid=? AND warehouse_id=?",
            arrayOf(o.getString("placement"), warehouseId.toString())).use { if (it.moveToFirst()) it.getLong(0) else null }

    private fun insertArticle(sql: SQLiteDatabase, warehouseId: Long, e: JSONObject): Boolean {
        val o = e.getJSONObject("item")
        if (itemId(sql, warehouseId, o.getString("uid")) != null) return false
        val values = articleValues(o).apply {
            put("warehouse_id", warehouseId); put("uid", o.getString("uid")); put("quantity", 0.0)
            placeOn(this, placementOf(sql, warehouseId, o))
        }
        require(sql.rawQuery("SELECT 1 FROM items WHERE warehouse_id=? AND sku=?", arrayOf(warehouseId.toString(), o.getString("sku")))
            .use { !it.moveToFirst() }) { "Codice ${o.getString("sku")} già presente: due telefoni con la stessa lettera?" }
        freeUid(sql, "items", o.getString("uid"), warehouseId)
        sql.insertOrThrow("items", null, values)
        stock.bumpSequence(sql, o.getString("sku"))
        return true
    }

    private fun locate(sql: SQLiteDatabase, warehouseId: Long, itemId: Long, placementUid: String?, level: Int?, slot: Int?) {
        val values = ContentValues()
        if (placementUid == null) { values.putNull("placement_id"); values.putNull("shelf_level"); values.putNull("shelf_slot") }
        else {
            val p = sql.rawQuery("SELECT id,levels,slots FROM placements WHERE uid=? AND warehouse_id=?", arrayOf(placementUid, warehouseId.toString()))
                .use { if (it.moveToFirst()) Triple(it.getLong(0), it.getInt(1), it.getInt(2)) else null }
                ?: throw IllegalArgumentException("la struttura non esiste più")
            require((level ?: 1) <= p.second && (slot ?: 1) <= p.third) { "ripiano o posizione non più presenti" }
            values.put("placement_id", p.first)
            if (level == null) values.putNull("shelf_level") else values.put("shelf_level", level)
            if (slot == null) values.putNull("shelf_slot") else values.put("shelf_slot", slot)
        }
        sql.update("items", values, "id=?", arrayOf(itemId.toString()))
    }

    /**
     * Another warehouse on this phone may carry the same global id (for example a copy of the
     * central's backup restored here before joining): that copy gets a new one.
     */
    private fun freeUid(sql: SQLiteDatabase, table: String, uid: String, warehouseId: Long) =
        sql.execSQL("UPDATE $table SET uid=lower(hex(randomblob(16))) WHERE uid=? AND warehouse_id<>?", arrayOf(uid, warehouseId))

    private fun itemId(sql: SQLiteDatabase, warehouseId: Long, uid: String): Long? =
        if (uid.isEmpty()) null else sql.rawQuery("SELECT id FROM items WHERE uid=? AND warehouse_id=?", arrayOf(uid, warehouseId.toString()))
            .use { if (it.moveToFirst()) it.getLong(0) else null }
    private fun itemLabel(sql: SQLiteDatabase, itemId: Long?, uid: String): String =
        itemId?.let { id -> sql.rawQuery("SELECT sku,name FROM items WHERE id=?", arrayOf(id.toString())).use {
            if (it.moveToFirst()) "${it.getString(0)} · ${it.getString(1)}" else null } } ?: "Articolo ${uid.take(8)}"
    private fun conflict(sql: SQLiteDatabase, letter: String, item: String, detail: String) {
        sql.insertOrThrow("team_sync_conflicts", null, ContentValues().apply {
            put("letter", letter); put("item", item.take(200)); put("detail", detail.take(400)); put("created_at", System.currentTimeMillis()) })
    }
    private fun signed(v: Double) = (if (v > 0) "+" else "") + amount(v)
    private fun dimension(v: Double): Double { require(v.isFinite() && v in 2.0..150.0) { "Dimensioni pianta non valide nel file" }; return v }
    private fun requirePassword(password: String) = require(password.length >= 8) { "La password della squadra deve avere almeno 8 caratteri" }

    private fun write(out: OutputStream, json: JSONObject, explicitPassword: String? = null) {
        val password = explicitPassword ?: requireTeamPw()
        BackupCipher.encrypt(out, password).use { enc -> GZIPOutputStream(enc).use { it.write(json.toString().toByteArray(Charsets.UTF_8)) } }
    }
    private fun read(input: InputStream, password: String, format: String): JSONObject {
        val bytes = ByteArrayOutputStream()
        try {
            GZIPInputStream(BackupCipher.decrypt(input, password)).use { stream ->
                val buffer = ByteArray(65536)
                while (true) { val n = stream.read(buffer); if (n < 0) break
                    require(bytes.size() + n <= MAX_JSON) { "File troppo grande" }; bytes.write(buffer, 0, n) }
            }
        } catch (e: IllegalArgumentException) { throw e }
        catch (e: Exception) { throw IllegalArgumentException("File non valido o password della squadra errata") }
        val json = runCatching { JSONObject(bytes.toString(Charsets.UTF_8.name())) }
            .getOrElse { throw IllegalArgumentException("File di sincronizzazione danneggiato") }
        require(json.optString("format") == format && json.optInt("version") == 1) {
            if (format == SNAPSHOT) "Questo file non è un aggiornamento del centrale" else "Questo file non contiene modifiche di un satellite"
        }
        return json
    }
}
