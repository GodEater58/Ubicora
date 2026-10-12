package com.officinastock.app

import android.content.ContentValues
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
object TeamSyncSchema {
    fun create(db: SQLiteDatabase) {
        fun addColumn(table: String, column: String) {
            val exists = db.rawQuery("PRAGMA table_info($table)", null).use { c ->
                var found = false; while (c.moveToNext()) if (c.getString(1) == column) found = true; found
            }
            if (!exists) db.execSQL("ALTER TABLE $table ADD COLUMN $column TEXT DEFAULT NULL")
        }
        addColumn("items", "uid")
        addColumn("placements", "uid")
        backfillIds(db)
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS idx_items_uid ON items(uid)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS idx_placements_uid ON placements(uid)")
        // Every new article or structure gets a global id, whatever code inserted it.
        for (table in listOf("items", "placements")) db.execSQL("""
            CREATE TRIGGER IF NOT EXISTS ${table}_uid AFTER INSERT ON $table WHEN NEW.uid IS NULL
            BEGIN UPDATE $table SET uid=lower(hex(randomblob(16))) WHERE id=NEW.id; END""")
        db.execSQL("CREATE TABLE IF NOT EXISTS team_sync_settings(key TEXT PRIMARY KEY NOT NULL, value TEXT NOT NULL)")
        db.execSQL("""CREATE TABLE IF NOT EXISTS team_sync_outbox(
            seq INTEGER PRIMARY KEY AUTOINCREMENT, kind TEXT NOT NULL CHECK(kind IN ('ITEM','MOVE','LOCATE')),
            ref_id INTEGER NOT NULL, created_at INTEGER NOT NULL)""")
        db.execSQL("""CREATE TABLE IF NOT EXISTS team_sync_peers(
            device_uid TEXT PRIMARY KEY NOT NULL, letter TEXT NOT NULL UNIQUE,
            processed_seq INTEGER NOT NULL DEFAULT 0, last_import INTEGER NOT NULL DEFAULT 0)""")
        db.execSQL("""CREATE TABLE IF NOT EXISTS team_sync_conflicts(
            id INTEGER PRIMARY KEY AUTOINCREMENT, letter TEXT NOT NULL, item TEXT NOT NULL,
            detail TEXT NOT NULL, created_at INTEGER NOT NULL)""")
        // Outbox triggers: active only on a satellite, only for the synced warehouse, and never
        // while a central update is being applied (that is not a local change to send back).
        val active = """(SELECT value FROM team_sync_settings WHERE key='role')='satellite'
            AND COALESCE((SELECT value FROM team_sync_settings WHERE key='applying'),'0')='0'"""
        val synced = "CAST((SELECT value FROM team_sync_settings WHERE key='warehouse_id') AS INTEGER)"
        val now = "CAST(strftime('%s','now') AS INTEGER)*1000"
        db.execSQL("""CREATE TRIGGER IF NOT EXISTS team_out_item AFTER INSERT ON items
            WHEN $active AND NEW.warehouse_id=$synced
            BEGIN INSERT INTO team_sync_outbox(kind,ref_id,created_at) VALUES('ITEM',NEW.id,$now); END""")
        db.execSQL("""CREATE TRIGGER IF NOT EXISTS team_out_move AFTER INSERT ON movements
            WHEN $active AND (SELECT warehouse_id FROM items WHERE id=NEW.item_id)=$synced
            BEGIN INSERT INTO team_sync_outbox(kind,ref_id,created_at) VALUES('MOVE',NEW.id,$now); END""")
        db.execSQL("""CREATE TRIGGER IF NOT EXISTS team_out_locate AFTER UPDATE OF placement_id,shelf_level,shelf_slot ON items
            WHEN $active AND NEW.warehouse_id=$synced AND (OLD.placement_id IS NOT NEW.placement_id
                OR OLD.shelf_level IS NOT NEW.shelf_level OR OLD.shelf_slot IS NOT NEW.shelf_slot)
            BEGIN INSERT INTO team_sync_outbox(kind,ref_id,created_at) VALUES('LOCATE',NEW.id,$now); END""")
        createScopeTriggers(db)
    }
    /**
     * 4.9 departments: on a satellite the central may limit new articles, loads and moves to some
     * categories (setting "scope", one category per line; empty = all). Withdrawals stay free.
     * Enforced here for every code path; the central checks again when importing.
     */
    fun createScopeTriggers(db: SQLiteDatabase) {
        fun s(key: String) = "(SELECT value FROM team_sync_settings WHERE key='$key')"
        val active = "${s("role")}='satellite' AND COALESCE(${s("applying")},'0')='0'"
        val synced = "CAST(${s("warehouse_id")} AS INTEGER)"
        fun outside(category: String) = "COALESCE(${s("scope")},'')<>'' AND instr(char(10)||${s("scope")}||char(10), char(10)||$category||char(10))=0"
        val message = "Fuori dal tuo reparto: qui puoi solo prelevare. Per il resto chiedi al telefono centrale"
        db.execSQL("""CREATE TRIGGER IF NOT EXISTS team_scope_item BEFORE INSERT ON items
            WHEN $active AND NEW.warehouse_id=$synced AND ${outside("NEW.category")}
            BEGIN SELECT RAISE(ABORT,'Fuori dal tuo reparto: non puoi creare articoli di questa categoria'); END""")
        db.execSQL("""CREATE TRIGGER IF NOT EXISTS team_scope_category BEFORE UPDATE OF category ON items
            WHEN $active AND NEW.warehouse_id=$synced AND ${outside("NEW.category")}
            BEGIN SELECT RAISE(ABORT,'Fuori dal tuo reparto: categoria non consentita'); END""")
        db.execSQL("""CREATE TRIGGER IF NOT EXISTS team_scope_load BEFORE INSERT ON movements
            WHEN $active AND NEW.delta>0 AND COALESCE(${s("returning")},'0')='0'
                AND EXISTS(SELECT 1 FROM items i WHERE i.id=NEW.item_id AND i.warehouse_id=$synced AND ${outside("i.category")})
            BEGIN SELECT RAISE(ABORT,'$message'); END""")
        db.execSQL("""CREATE TRIGGER IF NOT EXISTS team_scope_locate BEFORE UPDATE OF placement_id,shelf_level,shelf_slot ON items
            WHEN $active AND NEW.warehouse_id=$synced AND ${outside("OLD.category")} AND (OLD.placement_id IS NOT NEW.placement_id
                OR OLD.shelf_level IS NOT NEW.shelf_level OR OLD.shelf_slot IS NOT NEW.shelf_slot)
            BEGIN SELECT RAISE(ABORT,'$message'); END""")
    }
    fun backfillIds(db: SQLiteDatabase) {
        for (table in listOf("items", "placements"))
            db.execSQL("UPDATE $table SET uid=lower(hex(randomblob(16))) WHERE uid IS NULL")
    }
    fun setting(db: SQLiteDatabase, key: String): String? =
        db.rawQuery("SELECT value FROM team_sync_settings WHERE key=?", arrayOf(key)).use { if (it.moveToFirst()) it.getString(0) else null }
    fun put(db: SQLiteDatabase, key: String, value: String) {
        db.insertWithOnConflict("team_sync_settings", null, ContentValues().apply { put("key", key); put("value", value) },
            SQLiteDatabase.CONFLICT_REPLACE)
    }
    fun satelliteLetter(db: SQLiteDatabase): String? =
        if (setting(db, "role") == "satellite") setting(db, "letter") else null
}

enum class TeamRole { NONE, CENTRAL, SATELLITE }

data class TeamPeer(val letter: String, val processedSeq: Long, val lastImport: Long)
data class TeamConflict(val id: Long, val letter: String, val item: String, val detail: String, val createdAt: Long)
data class TeamSyncStatus(
    val role: TeamRole = TeamRole.NONE, val warehouseId: Long? = null, val letter: String? = null,
    val pending: Int = 0, val lastSync: Long = 0, val peers: List<TeamPeer> = emptyList(),
    val conflicts: List<TeamConflict> = emptyList(),
    /** Satellite: its department (categories); empty = all. */
    val scope: List<String> = emptyList(),
    /** Central: departments per satellite letter. */
    val scopes: Map<String, List<String>> = emptyMap(),
    /** 4.11: the team password is not readable on this phone (e.g. backup restored on a new phone). */
    val needsPassword: Boolean = false
)
data class TeamImportResult(val articles: Int, val movements: Int, val locations: Int, val skipped: Int, val conflicts: Int,
                            val letter: String? = null)

class TeamSync(private val stock: StockDb) {
    companion object {
        const val SNAPSHOT = "UBICORA_SYNC_STATE"
        const val CHANGES = "UBICORA_SYNC_CHANGES"
        const val JOIN = "UBICORA_SYNC_JOIN"
        const val KEY = "UBICORA_SYNC_KEY"
        /** One-time invite codes shown in the central's QR: valid for 10 minutes. */
        const val INVITE_MINUTES = 10
        private const val INVITE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        private const val MAX_JSON = 64L * 1024 * 1024
        private const val MAX_EVENTS = 200_000
    }
    private val db get() = stock.writableDatabase
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
            lines(setting("scope")), if (role == TeamRole.CENTRAL) scopes() else emptyMap(), teamPw() == null)
    }

    // ---------------------------------------------------------------- set-up

    /** This phone becomes the central one for [warehouseId]. */
    fun becomeCentral(warehouseId: Long, password: String) {
        requirePassword(password)
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
            for (t in listOf("team_sync_outbox", "team_sync_peers", "team_sync_conflicts")) sql.delete(t, null, null)
            sql.setTransactionSuccessful()
        } finally { sql.endTransaction() }
    }

    /**
     * On a satellite the article details (name, category, minimum, purchase data) come from the
     * central with every update, so a local edit would be silently overwritten: only the phone's
     * own articles not yet sent can still be corrected.
     */
    fun requireDetailsEditable(itemId: Long) {
        if (setting("role") != "satellite") return
        val synced = db.rawQuery("SELECT 1 FROM items WHERE id=? AND warehouse_id=?",
            arrayOf(itemId.toString(), setting("warehouse_id").orEmpty())).use { it.moveToFirst() }
        if (!synced) return
        require(db.rawQuery("SELECT 1 FROM team_sync_outbox WHERE kind='ITEM' AND ref_id=?", arrayOf(itemId.toString())).use { it.moveToFirst() }) {
            "Su questo telefono (satellite ${setting("letter")}) i dati dell'articolo si modificano dal telefono centrale. Carichi, prelievi e ubicazione invece sì."
        }
    }

    /** Central: limits satellite [letter] to [categories] (empty = all). Reaches it at the next exchange. */
    fun setScope(letter: String, categories: List<String>) {
        require(setting("role") == "central") { "I reparti li assegna il telefono centrale" }
        require(ArticleCodes.isSatelliteLetter(letter)) { "Lettera non valida" }
        categories.forEach { ArticleCodes.prefix(it) }
        val all = ArticleCodes.categoryPrefixes.keys
        val chosen = all.filter { it in categories }
        if (chosen.isEmpty() || chosen.size == all.size) db.delete("team_sync_settings", "key=?", arrayOf("scope:$letter"))
        else TeamSyncSchema.put(db, "scope:$letter", chosen.joinToString("\n"))
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

    // ---------------------------------------------------------------- central

    /** Complete state for every phone. */
    fun exportState(out: OutputStream) {
        require(setting("role") == "central") { "Solo il telefono centrale invia l'aggiornamento" }
        val warehouseId = setting("warehouse_id")!!.toLong()
        val read = stock.readableDatabase
        val state = JSONObject().put("format", SNAPSHOT).put("version", 1)
            .put("warehouse_uid", setting("warehouse_uid")).put("central", deviceUid())
            .put("created_at", System.currentTimeMillis())
        read.rawQuery("SELECT name,location,map_width,map_height FROM warehouses WHERE id=?", arrayOf(warehouseId.toString())).use { c ->
            require(c.moveToFirst()) { "Il magazzino sincronizzato non esiste più" }
            state.put("warehouse", JSONObject().put("name", c.getString(0)).put("location", c.getString(1))
                .put("width", c.getDouble(2)).put("height", c.getDouble(3)))
        }
        val processed = JSONObject(); val letters = JSONObject()
        read.rawQuery("SELECT device_uid,letter,processed_seq FROM team_sync_peers", null).use { c ->
            while (c.moveToNext()) { processed.put(c.getString(0), c.getLong(2)); letters.put(c.getString(0), c.getString(1)) }
        }
        state.put("processed", processed).put("letters", letters)
            .put("scopes", JSONObject().apply { scopes().forEach { (l, c) -> put(l, JSONArray(c)) } })
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
            i.note,i.unit_cost,i.supplier,i.lead_days FROM items i LEFT JOIN placements p ON p.id=i.placement_id
            WHERE i.warehouse_id=? ORDER BY i.id""", arrayOf(warehouseId.toString())).use { c ->
            while (c.moveToNext()) items.put(itemJson(c.getString(0), c.getString(1), c.getString(2), c.getString(3), c.getDouble(4),
                c.getDouble(5), c.getString(6), if (c.isNull(7)) null else c.getString(7), if (c.isNull(8)) null else c.getInt(8),
                if (c.isNull(9)) null else c.getInt(9), c.getString(10), if (c.isNull(11)) null else c.getDouble(11),
                c.getString(12), if (c.isNull(13)) null else c.getInt(13)))
        }
        state.put("placements", placements).put("walls", walls).put("items", items)
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
            val scope = setting("scope:$letter")?.let { lines(it).toSet() }
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
                            val inserted = runCatching { insertArticle(sql, warehouseId, e) }
                            inserted.onSuccess { if (it) articles++ else skipped++ }.onFailure {
                                conflict(sql, letter, "${o.optString("sku")} · ${o.optString("name")}",
                                    "articolo non creato: ${it.message ?: "dati non validi"}"); conflicts++
                            }
                        }
                    }
                    "MOVE" -> {
                        val itemId = itemId(sql, warehouseId, e.getString("item"))
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
                        val itemId = itemId(sql, warehouseId, e.getString("item"))
                        val failure = if (itemId == null) "articolo sconosciuto"
                            else if (outside(itemId)) "spostamento fuori dal reparto $letter"
                            else runCatching {
                            locate(sql, warehouseId, itemId, e.optString("placement").ifEmpty { null },
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
                processed = seq
            }
            sql.update("team_sync_peers", ContentValues().apply { put("processed_seq", processed); put("last_import", System.currentTimeMillis()) },
                "device_uid=?", arrayOf(device))
            sql.setTransactionSuccessful()
            return TeamImportResult(articles, moves, locations, skipped, conflicts, letter)
        } finally { sql.endTransaction() }
    }

    // ---------------------------------------------------------------- satellite

    /** Everything not yet confirmed by the central phone. */
    fun exportChanges(out: OutputStream): Int {
        require(setting("role") == "satellite") { "Solo un satellite invia le modifiche al centrale" }
        val read = stock.readableDatabase
        val events = JSONArray()
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
        write(out, JSONObject().put("format", CHANGES).put("version", 1).put("warehouse_uid", setting("warehouse_uid"))
            .put("device_uid", deviceUid()).put("letter", setting("letter")).put("created_at", System.currentTimeMillis())
            .put("events", events))
        return events.length()
    }

    /** Central update on a satellite: map, articles and quantities, keeping unsent local changes. */
    fun importState(input: InputStream): TeamImportResult {
        require(setting("role") == "satellite") { "Collega prima il telefono come satellite" }
        val state = read(input, requireTeamPw(), SNAPSHOT)
        require(state.getString("warehouse_uid") == setting("warehouse_uid")) { "Il file appartiene a un altro magazzino" }
        val sql = db
        sql.beginTransaction()
        try {
            val result = applyState(sql, setting("warehouse_id")!!.toLong(), state)
            sql.setTransactionSuccessful()
            return result
        } finally { sql.endTransaction() }
    }

    private fun applyState(sql: SQLiteDatabase, warehouseId: Long, state: JSONObject): TeamImportResult {
        val me = deviceUid()
        val processed = state.getJSONObject("processed").optLong(me, 0L)
        val letterOwner = state.getJSONObject("letters").let { l -> l.keys().asSequence().firstOrNull { l.getString(it) == setting("letter") } }
        require(letterOwner == null || letterOwner == me) { "La lettera ${setting("letter")} risulta di un altro telefono" }
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
        for (i in 0 until items.length()) {
            val it = items.getJSONObject(i)
            val uid = it.getString("uid")
            val local = sql.rawQuery("SELECT id,quantity FROM items WHERE uid=? AND warehouse_id=?", arrayOf(uid, warehouseId.toString())).use { c -> if (c.moveToFirst()) c.getLong(0) to c.getDouble(1) else null }
            // Central quantity plus what this phone moved and has not sent yet (never below zero).
            val target = StockRules.difference(it.getDouble("quantity"), -(local?.let { l -> pendingDelta[l.first] } ?: 0.0))
                .coerceAtLeast(0.0)
            val values = articleValues(it).apply { put("quantity", target) }
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
        TeamSyncSchema.put(sql, "applying", "0")
        TeamSyncSchema.put(sql, "last_sync", System.currentTimeMillis().toString())
        val stillPending = sql.rawQuery("SELECT COUNT(*) FROM team_sync_outbox", null).use { c -> c.moveToFirst(); c.getInt(0) }
        return TeamImportResult(updated, 0, 0, stillPending, 0)
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
