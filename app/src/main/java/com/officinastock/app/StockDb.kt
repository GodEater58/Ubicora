package com.officinastock.app

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteConstraintException
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import org.json.JSONObject
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import java.util.Base64

/** Database transazionale locale. Nessun HTML, server, permesso Internet o sincronizzazione remota. */
class StockDb(context: Context, name: String = "officina_stock_native4.db"): SQLiteOpenHelper(context, name, null, 12) {
    private val appContext = context.applicationContext
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("PRAGMA foreign_keys = ON")
        db.execSQL("""CREATE TABLE profiles (
            id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL,
            salt TEXT NOT NULL, verifier TEXT NOT NULL,
            created_at INTEGER NOT NULL)""")
        db.execSQL("""CREATE TABLE warehouses (
            id INTEGER PRIMARY KEY AUTOINCREMENT, profile_id INTEGER NOT NULL REFERENCES profiles(id) ON DELETE CASCADE,
            name TEXT NOT NULL, location TEXT NOT NULL DEFAULT '',
            map_width REAL NOT NULL DEFAULT 12, map_height REAL NOT NULL DEFAULT 8)""")
        db.execSQL("""CREATE TABLE placements (
            id INTEGER PRIMARY KEY AUTOINCREMENT, warehouse_id INTEGER NOT NULL REFERENCES warehouses(id) ON DELETE CASCADE,
            kind TEXT NOT NULL, label TEXT NOT NULL, x REAL NOT NULL, y REAL NOT NULL,
            locked INTEGER NOT NULL DEFAULT 0,
            width_m REAL NOT NULL DEFAULT 1.6, depth_m REAL NOT NULL DEFAULT 0.75, rotation_deg REAL NOT NULL DEFAULT 0,
            levels INTEGER NOT NULL DEFAULT 1 CHECK(levels BETWEEN 1 AND 20),
            slots INTEGER NOT NULL DEFAULT 1 CHECK(slots BETWEEN 1 AND 50))""")
        db.execSQL("""CREATE TABLE items (
            id INTEGER PRIMARY KEY AUTOINCREMENT, warehouse_id INTEGER NOT NULL REFERENCES warehouses(id) ON DELETE CASCADE,
            sku TEXT NOT NULL, name TEXT NOT NULL, category TEXT NOT NULL,
            quantity REAL NOT NULL DEFAULT 0 CHECK(quantity >= 0),
            minimum REAL NOT NULL DEFAULT 0 CHECK(minimum >= 0),
            unit TEXT NOT NULL DEFAULT 'pz', placement_id INTEGER REFERENCES placements(id) ON DELETE SET NULL,
            note TEXT NOT NULL DEFAULT '', photo_file TEXT DEFAULT NULL,
            unit_cost REAL DEFAULT NULL CHECK(unit_cost >= 0), supplier TEXT NOT NULL DEFAULT '',
            lead_days INTEGER DEFAULT NULL CHECK(lead_days >= 0 AND lead_days <= 3650),
            shelf_level INTEGER DEFAULT NULL CHECK(shelf_level BETWEEN 1 AND 20),
            shelf_slot INTEGER DEFAULT NULL CHECK(shelf_slot BETWEEN 1 AND 50),
            UNIQUE(warehouse_id,sku))""")
        db.execSQL("""CREATE TABLE movements (
            id INTEGER PRIMARY KEY AUTOINCREMENT, item_id INTEGER NOT NULL REFERENCES items(id) ON DELETE CASCADE,
            delta REAL NOT NULL, note TEXT NOT NULL, created_at INTEGER NOT NULL)""")
        db.execSQL("""CREATE TABLE loans (
            id INTEGER PRIMARY KEY AUTOINCREMENT, item_id INTEGER NOT NULL REFERENCES items(id) ON DELETE CASCADE,
            borrower TEXT NOT NULL, quantity REAL NOT NULL CHECK(quantity > 0),
            created_at INTEGER NOT NULL, returned_at INTEGER)""")
        db.execSQL("""CREATE TABLE walls (
            id INTEGER PRIMARY KEY AUTOINCREMENT, warehouse_id INTEGER NOT NULL REFERENCES warehouses(id) ON DELETE CASCADE,
            x1 REAL NOT NULL, y1 REAL NOT NULL, x2 REAL NOT NULL, y2 REAL NOT NULL,
            source TEXT NOT NULL DEFAULT 'manual')""")
        createSkuSequences(db)
        createDemoAssets(db)
        OperationsDb.createTables(db)
        EnterpriseDb.createTables(db)
        EnterpriseDb.addOperationLinks(db)
        StockPlanningDb.createTables(db)
        StockTraceDb.createTables(db)
        db.execSQL("CREATE INDEX idx_items_warehouse ON items(warehouse_id,name)")
        db.execSQL("CREATE INDEX idx_placements_warehouse ON placements(warehouse_id)")
        db.execSQL("CREATE INDEX idx_movements_item ON movements(item_id,created_at DESC)")
        db.execSQL("CREATE INDEX idx_movements_date ON movements(created_at)")
        db.execSQL("CREATE INDEX idx_loans_item ON loans(item_id,returned_at)")
        TeamSyncSchema.create(db)
    }
    override fun onConfigure(db: SQLiteDatabase) { super.onConfigure(db); db.setForeignKeyConstraintsEnabled(true) }
    /**
     * 4.10.2: indexes for lookups that grew with the map and the sync (articles of a structure,
     * walls of a warehouse). IF NOT EXISTS: created once, on any database version, no migration needed.
     */
    override fun onOpen(db: SQLiteDatabase) {
        super.onOpen(db)
        if (!db.isReadOnly) {
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_items_placement ON items(placement_id)")
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_walls_warehouse ON walls(warehouse_id)")
        }
    }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Aggiornamento additivo: conserva articoli, movimenti, magazzini e profili della 4.0.0.
        if (oldVersion < 2) {
            createSkuSequences(db)
            db.rawQuery("SELECT sku FROM items", null).use { cursor ->
                while (cursor.moveToNext()) {
                    val sku = cursor.getString(0)
                    val prefix = sku.substringBefore('-', "")
                    val serial = sku.substringAfter('-', "").toLongOrNull()
                    if (prefix in ArticleCodes.categoryPrefixes.values && serial != null && serial in 1..999999999L) {
                        db.execSQL("INSERT OR IGNORE INTO sku_sequences(prefix,next_value) VALUES(?,1)", arrayOf(prefix))
                        db.execSQL("UPDATE sku_sequences SET next_value=max(next_value,?) WHERE prefix=?", arrayOf(serial+1,prefix))
                    }
                }
            }
        }
        if (oldVersion < 3 && newVersion >= 3) {
            db.execSQL("ALTER TABLE items ADD COLUMN photo_file TEXT DEFAULT NULL")
        }
        if (oldVersion < 4 && newVersion >= 4) {
            db.execSQL("ALTER TABLE items ADD COLUMN unit_cost REAL DEFAULT NULL CHECK(unit_cost >= 0)")
            db.execSQL("ALTER TABLE items ADD COLUMN supplier TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE items ADD COLUMN lead_days INTEGER DEFAULT NULL CHECK(lead_days >= 0 AND lead_days <= 3650)")
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_movements_date ON movements(created_at)")
        }
        if (oldVersion < 5 && newVersion >= 5) {
            db.execSQL("ALTER TABLE warehouses ADD COLUMN map_width REAL NOT NULL DEFAULT 12")
            db.execSQL("ALTER TABLE warehouses ADD COLUMN map_height REAL NOT NULL DEFAULT 8")
            db.execSQL("ALTER TABLE placements ADD COLUMN width_m REAL NOT NULL DEFAULT 1.6")
            db.execSQL("ALTER TABLE placements ADD COLUMN depth_m REAL NOT NULL DEFAULT 0.75")
            db.execSQL("ALTER TABLE placements ADD COLUMN rotation_deg REAL NOT NULL DEFAULT 0")
            // Geometry v4 was relative percent, so convert once to physical metres.
            db.execSQL("UPDATE placements SET x = x * 0.12, y = y * 0.08")
            db.execSQL("UPDATE walls SET x1=x1*0.12,x2=x2*0.12,y1=y1*0.08,y2=y2*0.08")
        }
        if (oldVersion < 6 && newVersion >= 6) {
            createDemoAssets(db)
            markLegacyDemo(db)
        }
        if (oldVersion < 7 && newVersion >= 7) {
            OperationsDb.createTables(db)
        }
        if (oldVersion < 8 && newVersion >= 8) {
            EnterpriseDb.createTables(db)
            EnterpriseDb.addOperationLinks(db)
        }
        if (oldVersion < 9 && newVersion >= 9) {
            StockPlanningDb.createTables(db)
            // StockPlanningDb.createTables also adds missing maintenance fields idempotently.
        }
        if (oldVersion < 10 && newVersion >= 10) StockTraceDb.createTables(db)
        if (oldVersion < 11 && newVersion >= 11) {
            // 4.7: shelf levels and positions. Additive and idempotent: existing structures become
            // 1 level x 1 position; columns already present (e.g. a re-run upgrade) are left alone.
            fun addColumn(table: String, column: String, definition: String) {
                val exists=db.rawQuery("PRAGMA table_info($table)",null).use { c ->
                    var found=false
                    while(c.moveToNext()) if(c.getString(1)==column) found=true
                    found
                }
                if(!exists) db.execSQL("ALTER TABLE $table ADD COLUMN $column $definition")
            }
            addColumn("placements","levels","INTEGER NOT NULL DEFAULT 1 CHECK(levels BETWEEN 1 AND 20)")
            addColumn("placements","slots","INTEGER NOT NULL DEFAULT 1 CHECK(slots BETWEEN 1 AND 50)")
            addColumn("items","shelf_level","INTEGER DEFAULT NULL CHECK(shelf_level BETWEEN 1 AND 20)")
            addColumn("items","shelf_slot","INTEGER DEFAULT NULL CHECK(shelf_slot BETWEEN 1 AND 50)")
        }
        // 4.8: phone-to-phone sync. Additive: global ids for items and structures, and the outbox.
        if (oldVersion < 12 && newVersion >= 12) TeamSyncSchema.create(db)
    }
    private fun createDemoAssets(db: SQLiteDatabase) {
        db.execSQL("""CREATE TABLE IF NOT EXISTS demo_assets (
            entity_type TEXT NOT NULL, entity_id INTEGER NOT NULL,
            warehouse_id INTEGER NOT NULL REFERENCES warehouses(id) ON DELETE CASCADE,
            PRIMARY KEY(entity_type,entity_id)
        )""")
    }
    private fun demoTag(db: SQLiteDatabase, warehouseId: Long, entity: String, id: Long) {
        db.insertWithOnConflict("demo_assets", null, ContentValues().apply {
            put("warehouse_id",warehouseId);put("entity_type",entity);put("entity_id",id)
        }, SQLiteDatabase.CONFLICT_IGNORE)
    }
    /** Recupero mirato per demo create con le build 4.1.1 o precedenti.
     * Il riconoscimento richiede la descrizione demo e nomi originali,
     * non marca altri articoli nemmeno se hanno SKU simili. */
    private fun markLegacyDemo(db: SQLiteDatabase) {
        val itemNames=listOf("Vite TCEI M8 x 35","Cuscinetto 6204 2RS","Sensore induttivo M18",
            "Chiave combinata 13 mm","Raccordo pneumatico 1/4","Fusibile rapido 10 A",
            "Guarnizione NBR 30 mm","Punta HSS Ø6")
        val warehouses=mutableSetOf<Long>()
        db.rawQuery("SELECT id,warehouse_id,name FROM items WHERE note='Dati dimostrativi'",null).use {c ->
            while(c.moveToNext()) if(c.getString(2) in itemNames) {
                demoTag(db,c.getLong(1),"items",c.getLong(0));warehouses.add(c.getLong(1))
            }
        }
        // Vecchia demo: identifichiamo le strutture solo se esistono articoli demo.
        warehouses.forEach {warehouseId ->
            db.rawQuery("SELECT id,kind,label,x,y FROM placements WHERE warehouse_id=?",arrayOf(warehouseId.toString())).use {c ->
                while(c.moveToNext()) {
                    val signature=Triple(c.getString(1),c.getString(2),c.getFloat(3) to c.getFloat(4))
                    val known= listOf(Triple("Scaffale","A · Ricambi",3.36f to 2.48f),
                        Triple("Armadio","B · Utensili",8.64f to 2.4f),
                        Triple("Cassetto","C · Elettrico",6.12f to 5.84f))
                    if(known.any {it.first==signature.first && it.second==signature.second &&
                        kotlin.math.abs(it.third.first-signature.third.first)<.02f &&
                        kotlin.math.abs(it.third.second-signature.third.second)<.02f}) demoTag(db,warehouseId,"placements",c.getLong(0))
                }
            }
            val original=listOf(floatArrayOf(1.08f,.72f,10.92f,.72f),floatArrayOf(10.92f,.72f,10.92f,7.28f),
                floatArrayOf(10.92f,7.28f,1.08f,7.28f),floatArrayOf(1.08f,7.28f,1.08f,.72f))
            db.rawQuery("SELECT id,x1,y1,x2,y2,source FROM walls WHERE warehouse_id=?",arrayOf(warehouseId.toString())).use {c ->
                while(c.moveToNext()) if(c.getString(5)=="manual") {
                    val p=FloatArray(4) {idx -> c.getFloat(idx+1)}
                    if(original.any {o -> (0..3).all {idx -> kotlin.math.abs(o[idx]-p[idx])<.02f} })
                        demoTag(db,warehouseId,"walls",c.getLong(0))
                }
            }
        }
    }
    private fun createSkuSequences(db: SQLiteDatabase) {
        db.execSQL("""CREATE TABLE IF NOT EXISTS sku_sequences (
            prefix TEXT PRIMARY KEY NOT NULL,
            next_value INTEGER NOT NULL CHECK(next_value > 0)
        )""")
    }
    /** Riserva il progressivo nella stessa transazione che inserisce l'articolo. */
    private fun nextSku(db: SQLiteDatabase, category: String): String {
        val prefix = ArticleCodes.prefix(category)
        // A satellite numbers its own codes (RIC-B000001…): no collision with the other phones.
        val letter = TeamSyncSchema.satelliteLetter(db)
        val key = if (letter == null) prefix else "$prefix:$letter"
        db.execSQL("INSERT OR IGNORE INTO sku_sequences(prefix,next_value) VALUES(?,1)", arrayOf(key))
        val number = db.rawQuery("SELECT next_value FROM sku_sequences WHERE prefix=?", arrayOf(key)).use {
            require(it.moveToFirst()) { "Numerazione non disponibile" }
            it.getLong(0)
        }
        val code = ArticleCodes.format(prefix,number,letter)
        db.execSQL("UPDATE sku_sequences SET next_value=? WHERE prefix=?", arrayOf(number+1,key))
        return code
    }
    /** Keeps the numbering of [sku]'s series above [sku], whatever phone created it. */
    internal fun bumpSequence(db: SQLiteDatabase, sku: String) {
        val code = ArticleCodes.parseCode(sku) ?: return
        db.execSQL("INSERT OR IGNORE INTO sku_sequences(prefix,next_value) VALUES(?,1)", arrayOf(code.sequenceKey))
        db.execSQL("UPDATE sku_sequences SET next_value=max(next_value,?) WHERE prefix=?", arrayOf(code.serial+1,code.sequenceKey))
    }
    private fun salt() = ByteArray(16).also { SecureRandom().nextBytes(it) }
    private fun proof(pin: String, salt: ByteArray): String {
        val specification = PBEKeySpec(pin.toCharArray(), salt, 150_000, 256)
        return try {
            val hash = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(specification).encoded
            Base64.getEncoder().encodeToString(hash)
        } finally { specification.clearPassword() }
    }
    fun createProfile(name: String, pin: String): Long {
        require(name.trim().isNotEmpty()) { "Inserisci il nome del profilo" }
        require(pin.matches(Regex("[0-9]{4,8}"))) { "Il PIN deve contenere da 4 a 8 cifre" }
        val salt = salt()
        return writableDatabase.insertOrThrow("profiles", null, ContentValues().apply {
            put("name", name.trim()); put("salt", Base64.getEncoder().encodeToString(salt))
            put("verifier", proof(pin,salt)); put("created_at", System.currentTimeMillis())
        })
    }
    fun profiles(): List<Profile> = readableDatabase.rawQuery("SELECT id,name FROM profiles ORDER BY created_at,id", null).use { c ->
        buildList { while(c.moveToNext()) add(Profile(c.getLong(0),c.getString(1))) }
    }
    fun authenticate(profileId: Long, pin: String): Boolean {
        val attempts=appContext.getSharedPreferences("login_attempts",Context.MODE_PRIVATE)
        val key="profile_$profileId"
        val now=System.currentTimeMillis()
        val retry=attempts.getLong("${key}_retry",0L)
        require(!PinThrottle.isLocked(retry,now)) { PinThrottle.message(retry,now) }
        readableDatabase.rawQuery("SELECT salt,verifier FROM profiles WHERE id=?", arrayOf(profileId.toString())).use { c ->
            if(!c.moveToFirst()) return false
            val current = proof(pin,Base64.getDecoder().decode(c.getString(0)))
            val valid=java.security.MessageDigest.isEqual(current.toByteArray(),c.getString(1).toByteArray())
            val failures=if(valid)0 else attempts.getInt(key,0)+1
            val locked=failures>=PinThrottle.MAX_FAILURES
            // Consecutive lockouts escalate the wait; a correct PIN resets the escalation.
            val lockouts=if(valid)0 else attempts.getInt("${key}_locks",0)+(if(locked)1 else 0)
            attempts.edit().putInt(key,if(locked)0 else failures)
                .putInt("${key}_locks",lockouts)
                .putLong("${key}_retry",if(locked)now+PinThrottle.lockMillis(lockouts) else 0L).commit()
            return valid
        }
    }
    fun createWarehouse(profileId: Long, name: String, location: String): Long {
        require(name.trim().isNotEmpty()) { "Inserisci un nome per il magazzino" }
        return writableDatabase.insertOrThrow("warehouses",null,ContentValues().apply {
            put("profile_id",profileId);put("name",name.trim());put("location",location.trim())
        })
    }
    fun warehouses(profileId: Long): List<Warehouse> = readableDatabase.rawQuery(
        "SELECT id,profile_id,name,location,map_width,map_height FROM warehouses WHERE profile_id=? ORDER BY id",arrayOf(profileId.toString())
    ).use { c -> buildList { while(c.moveToNext()) add(Warehouse(c.getLong(0),c.getLong(1),c.getString(2),c.getString(3),c.getFloat(4),c.getFloat(5))) } }
    fun updateMapSize(warehouseId: Long, width: Float, height: Float) {
        require(width.isFinite() && width in 2f..150f && height.isFinite() && height in 2f..150f) { "Dimensioni ammesse: 2–150 metri" }
        require(placements(warehouseId).all{PlanGeometry.placementFitsRoom(it,width,height)} &&
            walls(warehouseId).all{it.x1 in 0f..width && it.x2 in 0f..width && it.y1 in 0f..height && it.y2 in 0f..height}) {
            "Sposta le strutture e le pareti prima di ridurre la pianta: elementi fuori dai nuovi confini."
        }
        writableDatabase.update("warehouses",ContentValues().apply {put("map_width",width);put("map_height",height)},"id=?",arrayOf(warehouseId.toString()))
    }
    private fun mapBounds(warehouseId: Long): Pair<Float,Float> = readableDatabase.rawQuery(
        "SELECT map_width,map_height FROM warehouses WHERE id=?",arrayOf(warehouseId.toString())
    ).use { c -> require(c.moveToFirst()) { "Magazzino non trovato" }; c.getFloat(0) to c.getFloat(1) }

    fun items(warehouseId: Long, filter: String = ""): List<StockItem> {
        val query = """SELECT id,warehouse_id,sku,name,category,quantity,minimum,unit,placement_id,note,photo_file,unit_cost,supplier,lead_days,shelf_level,shelf_slot
            FROM items WHERE warehouse_id=? AND (?='' OR sku LIKE ? OR name LIKE ? OR category LIKE ?)
            ORDER BY name COLLATE NOCASE,id"""
        val safeFilter = "%${filter.trim().replace("\\","\\\\").replace("%","\\%").replace("_","\\_")}%"
        // Backslash escaping is explicit for SQL LIKE so '%' in search is interpreted literally.
        val escapedQuery = query.replace(" LIKE ?", " LIKE ? ESCAPE '\\'")
        return readableDatabase.rawQuery(escapedQuery,arrayOf(warehouseId.toString(),filter.trim(),safeFilter,safeFilter,safeFilter)).use { c ->
            buildList { while(c.moveToNext()) add(c.toItem()) }
        }
    }
    private fun Cursor.toItem() = StockItem(getLong(0),getLong(1),getString(2),getString(3),getString(4),
        getDouble(5),getDouble(6),getString(7),if(isNull(8)) null else getLong(8),getString(9),
        if(isNull(10)) null else getString(10),if(isNull(11)) null else getDouble(11),getString(12),if(isNull(13)) null else getInt(13),
        if(isNull(14)) null else getInt(14),if(isNull(15)) null else getInt(15))
    fun item(id: Long): StockItem? = readableDatabase.rawQuery(
        "SELECT id,warehouse_id,sku,name,category,quantity,minimum,unit,placement_id,note,photo_file,unit_cost,supplier,lead_days,shelf_level,shelf_slot FROM items WHERE id=?",arrayOf(id.toString())
    ).use { if(it.moveToFirst()) it.toItem() else null }
    fun addItem(warehouseId: Long, name: String, category: String,
                quantity: Double, minimum: Double, unit: String, note: String, photoFile: String? = null,
                unitCost: Double? = null, supplier: String = "", leadDays: Int? = null): Long {
        require(name.trim().length in 1..180) { "Nome articolo: massimo 180 caratteri" }
        require(unit.trim().length<=24) { "Unità di misura: massimo 24 caratteri" }
        require(note.length<=3000 && supplier.length<=160) { "Descrizione o fornitore troppo lunghi" }
        ArticleCodes.prefix(category) // Verifica prima di aprire la transazione.
        require(StockRules.validQuantity(quantity) && StockRules.validQuantity(minimum)) { "Quantità non valide" }
        validatePurchase(unitCost, leadDays)
        val db = writableDatabase
        db.beginTransaction()
        try {
            val sku = nextSku(db,category)
            val id = db.insertOrThrow("items",null,ContentValues().apply {
                put("warehouse_id",warehouseId);put("sku",sku);put("name",name.trim());put("category",category)
                put("quantity",quantity);put("minimum",minimum);put("unit",unit.trim().ifEmpty { "pz" });put("note",note.trim())
                if (photoFile != null) put("photo_file",photoFile)
                if (unitCost != null) put("unit_cost",unitCost)
                put("supplier",supplier.trim().take(160))
                if (leadDays != null) put("lead_days",leadDays)
            })
            if(quantity > 0) recordMovement(db,id,quantity,"Carico iniziale")
            db.setTransactionSuccessful()
            return id
        } catch(e: SQLiteConstraintException) {
            val department=e.message?.takeIf { it.contains("reparto") }?.substringBefore(" (code ")
            throw IllegalArgumentException(department ?: "Codice già utilizzato. Riprova.",e)
        }
        finally { db.endTransaction() }
    }
    private fun validatePurchase(unitCost: Double?, leadDays: Int?) {
        require(unitCost == null || (unitCost.isFinite() && unitCost >= 0.0 && unitCost <= 100000000.0)) { "Costo unitario non valido" }
        require(leadDays == null || leadDays in 0..3650) { "Tempi di consegna non validi" }
    }
    /** Permette di aggiornare costi e parametri di approvvigionamento senza modificare SKU o movimenti. */
    fun updatePurchase(itemId: Long, cost: Double?, supplier: String, days: Int?) {
        validatePurchase(cost,days)
        val values=ContentValues().apply {
            if(cost == null) putNull("unit_cost") else put("unit_cost",cost)
            put("supplier",supplier.trim().take(160))
            if(days == null) putNull("lead_days") else put("lead_days",days)
        }
        require(writableDatabase.update("items",values,"id=?",arrayOf(itemId.toString())) == 1) { "Articolo non trovato" }
    }
    /**
     * Modifica i metadati senza riscrivere SKU, giacenze o storico.
     * La giacenza va cambiata soltanto tramite un movimento tracciabile.
     */
    fun updateItemDetails(
        warehouseId: Long, itemId: Long, name: String, category: String,
        minimum: Double, unit: String, note: String, cost: Double?,
        supplier: String, leadDays: Int?
    ) {
        require(name.trim().length in 1..180) { "Nome articolo: massimo 180 caratteri" }
        require(unit.trim().length in 1..24) { "Unità di misura: da 1 a 24 caratteri" }
        require(note.length <= 3000) { "Descrizione troppo lunga" }
        require(supplier.length <= 160) { "Fornitore troppo lungo" }
        ArticleCodes.prefix(category)
        require(StockRules.validQuantity(minimum)) { "Soglia minima non valida" }
        validatePurchase(cost,leadDays)
        val original=requireNotNull(item(itemId)) { "Articolo inesistente" }
        require(original.warehouseId==warehouseId) { "Articolo appartenente a un altro magazzino" }
        // An open operation recorded its numeric quantity in the previous unit.
        // Changing pz -> kg in place would silently corrupt receipts, stock counts and parts usage.
        if(!original.unit.trim().equals(unit.trim(),ignoreCase=true)) {
            require(readableDatabase.rawQuery(
                """SELECT 1 FROM stock_lots WHERE item_id=? LIMIT 1""",
                arrayOf(itemId.toString())).use{!it.moveToFirst()} &&
                readableDatabase.rawQuery(
                    """SELECT 1 FROM stock_serials WHERE item_id=? LIMIT 1""",
                    arrayOf(itemId.toString())).use{!it.moveToFirst()}) {
                "L'articolo ha una tracciabilità storica: crea un nuovo codice per cambiare unità."
            }
            require(original.quantity==0.0) {
                "Prima esaurisci o trasferisci la giacenza: cambiare l'unità a una quantità già presente la falserebbe."
            }
            val hasOpenWork=readableDatabase.rawQuery(
                """SELECT (
                    EXISTS(SELECT 1 FROM ops_orders WHERE item_id=? AND status IN ('DRAFT','APPROVED','ORDERED'))
                    OR EXISTS(SELECT 1 FROM ops_counts WHERE item_id=? AND status='PENDING')
                    OR EXISTS(SELECT 1 FROM ops_transfers WHERE item_id=? AND status IN ('REQUESTED','IN_TRANSIT'))
                    OR EXISTS(SELECT 1 FROM ops_maintenance WHERE item_id=? AND status='OPEN')
                    OR EXISTS(SELECT 1 FROM loans WHERE item_id=? AND returned_at IS NULL)
                )""",Array(5){itemId.toString()}
            ).use { cursor -> cursor.moveToFirst() && cursor.getInt(0)!=0 }
            require(!hasOpenWork) {
                "Impossibile cambiare l'unità di misura: completa o annulla prima acquisti, conteggi, trasferimenti, manutenzioni e prestiti aperti."
            }
        }
        val values=ContentValues().apply {
            put("name",name.trim());put("category",category);put("minimum",minimum)
            put("unit",unit.trim());put("note",note.trim())
            if(cost==null)putNull("unit_cost") else put("unit_cost",cost)
            put("supplier",supplier.trim())
            if(leadDays==null)putNull("lead_days") else put("lead_days",leadDays)
        }
        require(writableDatabase.update("items",values,"id=? AND warehouse_id=?",
            arrayOf(itemId.toString(),warehouseId.toString()))==1) { "Articolo non aggiornato" }
    }
    /** Aggregazione server-side SQLite: non carichiamo migliaia di singoli movimenti nel Compose tree. */
    fun dailyActivity(warehouseId: Long, since: Long): List<DailyActivity> = readableDatabase.rawQuery(
        """SELECT date(m.created_at/1000,'unixepoch','localtime') AS day,
           SUM(CASE WHEN m.delta>0 THEN 1 ELSE 0 END),
           SUM(CASE WHEN m.delta<0 THEN 1 ELSE 0 END)
           FROM movements m JOIN items i ON i.id=m.item_id
           WHERE i.warehouse_id=? AND m.created_at>=?
           GROUP BY day ORDER BY day""", arrayOf(warehouseId.toString(),since.toString())
    ).use{c -> buildList {while(c.moveToNext()) add(DailyActivity(c.getString(0),c.getInt(1),c.getInt(2)))}}
    fun usageSummary(warehouseId: Long, now: Long): List<ItemUsage> {
        val d=86_400_000L
        val since90=now-90*d;val since30=now-30*d;val since7=now-7*d
        return readableDatabase.rawQuery(
            """SELECT m.item_id,
             SUM(CASE WHEN m.created_at>=? THEN -m.delta ELSE 0 END),
             SUM(CASE WHEN m.created_at>=? THEN -m.delta ELSE 0 END),
             SUM(-m.delta)
             FROM movements m JOIN items i ON i.id=m.item_id
             WHERE i.warehouse_id=? AND m.created_at>=? AND m.delta<0
             AND m.note NOT LIKE 'Assegnato a %'
             AND m.note NOT LIKE 'Spedizione trasferimento %'
             AND m.note NOT LIKE 'Inventario ciclico %'
             AND m.note NOT LIKE 'Rettifica inventario:%'
             GROUP BY m.item_id""",
            arrayOf(since7.toString(),since30.toString(),warehouseId.toString(),since90.toString())
        ).use{c -> buildList {while(c.moveToNext()) add(ItemUsage(c.getLong(0),c.getDouble(1),c.getDouble(2),c.getDouble(3)))}}
    }
    fun updatePhoto(itemId: Long, photoFile: String?) {
        val values=ContentValues().apply { if(photoFile==null) putNull("photo_file") else put("photo_file",photoFile) }
        val count=writableDatabase.update("items",values,"id=?",arrayOf(itemId.toString()))
        require(count==1) { "Articolo non trovato, foto non modificata" }
    }
    private fun recordMovement(db: SQLiteDatabase, itemId: Long, delta: Double, note: String) {
        db.insertOrThrow("movements",null,ContentValues().apply {
            put("item_id",itemId);put("delta",delta);put("note",note);put("created_at",System.currentTimeMillis())
        })
    }
    private fun adjust(db: SQLiteDatabase, itemId: Long, delta: Double, note: String, counted: Double? = null) {
        db.rawQuery("SELECT quantity FROM items WHERE id=?",arrayOf(itemId.toString())).use { cursor ->
            require(cursor.moveToFirst()) { "Articolo non trovato" }
            val next = counted ?: StockRules.applyDelta(cursor.getDouble(0),delta)
            StockPlanningDb.assertUnreservedStock(db,itemId,next)
            StockTraceDb.assertPhysicalFloor(db,itemId,next)
            db.update("items",ContentValues().apply { put("quantity",next) },"id=?",arrayOf(itemId.toString()))
            recordMovement(db,itemId,delta,note)
        }
    }
    /** Stock change received from another phone: same checks as a local movement, original date kept. */
    internal fun applyTeamMovement(db: SQLiteDatabase, itemId: Long, delta: Double, note: String, at: Long) {
        require(delta.isFinite() && delta != 0.0) { "quantità nulla" }
        db.rawQuery("SELECT quantity FROM items WHERE id=?",arrayOf(itemId.toString())).use { cursor ->
            require(cursor.moveToFirst()) { "articolo non trovato" }
            val next=StockRules.applyDelta(cursor.getDouble(0),delta)
            StockPlanningDb.assertUnreservedStock(db,itemId,next)
            StockTraceDb.assertPhysicalFloor(db,itemId,next)
            db.update("items",ContentValues().apply { put("quantity",next) },"id=?",arrayOf(itemId.toString()))
            recordTeamMovement(db,itemId,delta,note,at)
        }
    }
    internal fun recordTeamMovement(db: SQLiteDatabase, itemId: Long, delta: Double, note: String, at: Long) {
        db.insertOrThrow("movements",null,ContentValues().apply {
            put("item_id",itemId);put("delta",delta);put("note",note.ifBlank{"Sincronizzazione"});put("created_at",at.coerceIn(0L,4102444800000L))
        })
    }
    fun movement(itemId: Long, delta: Double, note: String) {
        require(delta.isFinite() && delta != 0.0) { "Inserisci una quantità diversa da zero" }
        writableDatabase.apply { beginTransaction();try { adjust(this,itemId,delta,note.ifBlank { if(delta>0) "Carico" else "Prelievo" });setTransactionSuccessful() } finally { endTransaction() } }
    }
    /** Rettifica inventario transazionale, con un movimento di audit obbligatorio. */
    fun reconcileQuantity(itemId: Long, counted: Double, reason: String) {
        require(StockRules.validQuantity(counted)) { "Conteggio non valido" }
        require(reason.trim().length in 3..250) { "Indica un motivo di almeno 3 caratteri" }
        val sql=writableDatabase
        sql.beginTransaction()
        try {
            val old=sql.rawQuery("SELECT quantity FROM items WHERE id=?",arrayOf(itemId.toString())).use { c ->
                require(c.moveToFirst()) { "Articolo non trovato" }
                c.getDouble(0)
            }
            val delta=StockRules.difference(counted,old)
            require(delta.isFinite() && delta!=0.0) { "Nessuna differenza da rettificare" }
            adjust(sql,itemId,delta,"Rettifica inventario: " + reason.trim(),counted)
            sql.setTransactionSuccessful()
        } finally { sql.endTransaction() }
    }
    fun movements(warehouseId: Long): List<Movement> = readableDatabase.rawQuery(
        """SELECT m.id,m.item_id,i.sku,i.name,m.delta,m.note,m.created_at FROM movements m
        JOIN items i ON i.id=m.item_id WHERE i.warehouse_id=? ORDER BY m.created_at DESC,m.id DESC LIMIT 250""",
        arrayOf(warehouseId.toString())
    ).use { c -> buildList { while(c.moveToNext()) add(Movement(c.getLong(0),c.getLong(1),c.getString(2),c.getString(3),c.getDouble(4),c.getString(5),c.getLong(6))) } }
    /** Cronologia paginata direttamente in SQLite: filtri applicati prima di LIMIT/OFFSET. */
    fun movementsPage(warehouseId: Long, since: Long, direction: Int, offset: Int, limit: Int = 101): List<Movement> {
        require(since >= 0L && direction in -1..1 && offset >= 0 && limit in 1..101) { "Parametri cronologia non validi" }
        val directionSql = when(direction) {
            1 -> " AND m.delta>0"
            -1 -> " AND m.delta<0"
            else -> ""
        }
        return readableDatabase.rawQuery(
            """SELECT m.id,m.item_id,i.sku,i.name,m.delta,m.note,m.created_at FROM movements m
               JOIN items i ON i.id=m.item_id
               WHERE i.warehouse_id=? AND m.created_at>=?$directionSql
               ORDER BY m.created_at DESC,m.id DESC LIMIT ? OFFSET ?""",
            arrayOf(warehouseId.toString(),since.toString(),limit.toString(),offset.toString())
        ).use { c -> buildList {
            while(c.moveToNext()) add(Movement(c.getLong(0),c.getLong(1),c.getString(2),c.getString(3),c.getDouble(4),c.getString(5),c.getLong(6)))
        } }
    }
    fun lend(itemId: Long, borrower: String, amount: Double) {
        require(borrower.trim().isNotEmpty() && amount > 0 && amount.isFinite()) { "Destinatario e quantità validi obbligatori" }
        writableDatabase.apply { beginTransaction();try {
            adjust(this,itemId,-amount,"Assegnato a ${borrower.trim()}")
            insertOrThrow("loans",null,ContentValues().apply {
                put("item_id",itemId);put("borrower",borrower.trim());put("quantity",amount);put("created_at",System.currentTimeMillis())
            })
            setTransactionSuccessful()
        } finally { endTransaction() } }
    }
    fun activeLoans(warehouseId: Long): List<Loan> = readableDatabase.rawQuery(
        """SELECT l.id,l.item_id,l.borrower,l.quantity,l.created_at,l.returned_at
        FROM loans l JOIN items i ON i.id=l.item_id WHERE i.warehouse_id=? AND l.returned_at IS NULL
        ORDER BY l.created_at DESC""",arrayOf(warehouseId.toString())
    ).use { c -> buildList { while(c.moveToNext()) add(Loan(c.getLong(0),c.getLong(1),c.getString(2),c.getDouble(3),c.getLong(4),null)) } }
    fun returnLoan(loanId: Long) {
        writableDatabase.apply { beginTransaction();try {
            rawQuery("SELECT item_id,quantity,borrower FROM loans WHERE id=? AND returned_at IS NULL",arrayOf(loanId.toString())).use { c ->
                require(c.moveToFirst()) { "Prestito già restituito o inesistente" }
                val itemId=c.getLong(0);val amount=c.getDouble(1);val borrower=c.getString(2)
                adjust(this,itemId,amount,"Restituito da $borrower")
                update("loans",ContentValues().apply { put("returned_at",System.currentTimeMillis()) },"id=? AND returned_at IS NULL",arrayOf(loanId.toString()))
            };setTransactionSuccessful()
        } finally { endTransaction() } }
    }
    fun placements(warehouseId: Long): List<Placement> = readableDatabase.rawQuery(
        "SELECT id,warehouse_id,kind,label,x,y,locked,width_m,depth_m,rotation_deg,levels,slots FROM placements WHERE warehouse_id=? ORDER BY id",arrayOf(warehouseId.toString())
    ).use { c -> buildList { while(c.moveToNext()) add(c.toPlacement()) } }
    private fun Cursor.toPlacement() = Placement(getLong(0),getLong(1),getString(2),getString(3),getFloat(4),getFloat(5),
        getInt(6)!=0,getFloat(7),getFloat(8),getFloat(9),getInt(10),getInt(11))
    fun placement(id: Long): Placement? = readableDatabase.rawQuery(
        "SELECT id,warehouse_id,kind,label,x,y,locked,width_m,depth_m,rotation_deg,levels,slots FROM placements WHERE id=?",
        arrayOf(id.toString())).use { if(it.moveToFirst()) it.toPlacement() else null }
    fun addPlacement(warehouseId: Long, kind: String, label: String, x: Float, y: Float): Long {
        require(label.trim().isNotEmpty() && label.trim().length<=80) { "Inserisci un nome (massimo 80 caratteri)" }
        require(kind in PlacementKinds.all) { "Tipo di struttura non riconosciuto" }
        val (mapWidth,mapHeight)=mapBounds(warehouseId)
        val (furnitureWidth,furnitureDepth)=PlanGeometry.defaultSize(kind)
        require(furnitureWidth<=mapWidth && furnitureDepth<=mapHeight) {
            "Struttura troppo grande per la pianta: aumenta le dimensioni del locale."
        }
        val pos=PlanGeometry.boundedPlacementCenter(x,y,furnitureWidth,furnitureDepth,0f,mapWidth,mapHeight)
        return writableDatabase.insertOrThrow("placements",null,ContentValues().apply {
            put("warehouse_id",warehouseId);put("kind",kind);put("label",label.trim())
            put("x",pos.x);put("y",pos.y);put("locked",0)
            put("width_m",furnitureWidth);put("depth_m",furnitureDepth);put("rotation_deg",0)
        })
    }
    fun movePlacement(id: Long, x: Float, y: Float): Boolean {
        require(x.isFinite() && y.isFinite()) { "Coordinate non valide" }
        val fixture=readableDatabase.rawQuery(
            "SELECT warehouse_id,width_m,depth_m,rotation_deg FROM placements WHERE id=?",
            arrayOf(id.toString())).use { c ->
            if(!c.moveToFirst()) return false
            Triple(c.getLong(0),Pair(c.getFloat(1),c.getFloat(2)),c.getFloat(3))
        }
        val (mapWidth,mapHeight)=mapBounds(fixture.first)
        val pos=PlanGeometry.boundedPlacementCenter(x,y,fixture.second.first,
            fixture.second.second,fixture.third,mapWidth,mapHeight)
        return writableDatabase.update("placements",ContentValues().apply {
            put("x",pos.x);put("y",pos.y)
        },"id=? AND locked=0",arrayOf(id.toString())) == 1
    }
    fun updatePlacementGeometry(id: Long, width: Float, depth: Float, rotation: Float) {
        require(width.isFinite() && depth.isFinite() && rotation.isFinite() && width in 0.2f..15f && depth in 0.2f..15f) { "Dimensioni della struttura non valide" }
        val entry=readableDatabase.rawQuery("SELECT warehouse_id,x,y FROM placements WHERE id=?",
            arrayOf(id.toString())).use { c ->
            require(c.moveToFirst()) { "Struttura non trovata" }
            Triple(c.getLong(0),c.getFloat(1),c.getFloat(2))
        }
        val (mapWidth,mapHeight)=mapBounds(entry.first)
        val normalized=((rotation%360f)+360f)%360f
        val pos=PlanGeometry.boundedPlacementCenter(entry.second,entry.third,
            width,depth,normalized,mapWidth,mapHeight)
        val testPlacement=Placement(id,entry.first,"","",pos.x,pos.y,false,width,depth,normalized)
        require(PlanGeometry.placementFitsRoom(testPlacement,mapWidth,mapHeight)) {
            "Ingombro troppo grande per la pianta: riduci la struttura o ingrandisci il locale."
        }
        writableDatabase.update("placements",ContentValues().apply {
            put("width_m",width);put("depth_m",depth);put("rotation_deg",normalized)
            put("x",pos.x);put("y",pos.y)
        },"id=?",arrayOf(id.toString()))
    }
    fun lockPlacement(id: Long, locked: Boolean) { writableDatabase.update("placements",ContentValues().apply {
        put("locked",if(locked) 1 else 0)
    },"id=?",arrayOf(id.toString())) }
    fun assignItem(itemId: Long, placementId: Long?, level: Int? = null, slot: Int? = null) {
        assignItems(listOf(itemId),placementId,level,slot)
    }
    /** Assigns several items in one transaction: all or nothing. */
    fun assignItems(itemIds: List<Long>, placementId: Long?, level: Int? = null, slot: Int? = null): Int {
        require(itemIds.isNotEmpty() && itemIds.size<=5000) { "Seleziona da 1 a 5000 articoli" }
        val target=placementId?.let { requireNotNull(placement(it)) { "Ubicazione inesistente" } }
        if(target!=null) Locations.validate(target,level,slot)
        else require(level==null && slot==null) { "Ripiano senza struttura" }
        val db=writableDatabase
        db.beginTransaction()
        try {
            for(itemId in itemIds.distinct()) {
                val item=requireNotNull(item(itemId)) { "Articolo inesistente" }
                require(target==null || target.warehouseId==item.warehouseId) { "Ubicazione di un altro magazzino" }
                require(db.update("items",ContentValues().apply {
                    if(placementId==null) putNull("placement_id") else put("placement_id",placementId)
                    if(level==null) putNull("shelf_level") else put("shelf_level",level)
                    if(slot==null) putNull("shelf_slot") else put("shelf_slot",slot)
                },"id=?",arrayOf(itemId.toString()))==1) { "Articolo non aggiornato" }
            }
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
        return itemIds.distinct().size
    }
    fun renamePlacement(id: Long, label: String) {
        require(label.trim().length in 1..80) { "Nome: da 1 a 80 caratteri" }
        require(writableDatabase.update("placements",ContentValues().apply{put("label",label.trim())},
            "id=?",arrayOf(id.toString()))==1) { "Struttura non trovata" }
    }
    /** Levels/positions can shrink only if no item sits beyond the new limits. */
    fun updatePlacementLayout(id: Long, levels: Int, slots: Int) {
        require(levels in 1..PlacementKinds.MAX_LEVELS && slots in 1..PlacementKinds.MAX_SLOTS) {
            "Ripiani 1–${PlacementKinds.MAX_LEVELS}, posizioni 1–${PlacementKinds.MAX_SLOTS}"
        }
        val p=requireNotNull(placement(id)) { "Struttura non trovata" }
        require(p.holdsStock || (levels==1 && slots==1)) { "${p.kind}: non ha ripiani" }
        val outside=readableDatabase.rawQuery("""SELECT COUNT(*) FROM items WHERE placement_id=?
            AND ((shelf_level IS NOT NULL AND shelf_level>?) OR (shelf_slot IS NOT NULL AND shelf_slot>?))""",
            arrayOf(id.toString(),levels.toString(),slots.toString())).use { it.moveToFirst(); it.getInt(0) }
        require(outside==0) { "$outside articoli sono su ripiani/posizioni che verrebbero eliminati: spostali prima." }
        writableDatabase.update("placements",ContentValues().apply{put("levels",levels);put("slots",slots)},
            "id=?",arrayOf(id.toString()))
    }
    /** Copy of a structure (geometry, kind, levels) with the next label, placed at [x],[y]. */
    fun duplicatePlacement(sourceId: Long, x: Float, y: Float, label: String? = null): Long {
        val src=requireNotNull(placement(sourceId)) { "Struttura non trovata" }
        val (mapWidth,mapHeight)=mapBounds(src.warehouseId)
        val pos=PlanGeometry.boundedPlacementCenter(x,y,src.width,src.depth,src.rotation,mapWidth,mapHeight)
        val name=(label ?: PlanGeometry.nextLabel(src.label)).trim().take(80)
        return writableDatabase.insertOrThrow("placements",null,ContentValues().apply {
            put("warehouse_id",src.warehouseId);put("kind",src.kind);put("label",name)
            put("x",pos.x);put("y",pos.y);put("locked",0)
            put("width_m",src.width);put("depth_m",src.depth);put("rotation_deg",src.rotation)
            put("levels",src.levels);put("slots",src.slots)
        })
    }
    /** [count] copies in a row after the source. Fails as a whole if any copy would leave the room. */
    fun createPlacementRow(sourceId: Long, count: Int, gap: Float, vertical: Boolean): List<Long> {
        val src=requireNotNull(placement(sourceId)) { "Struttura non trovata" }
        val (mapWidth,mapHeight)=mapBounds(src.warehouseId)
        val centres=PlanGeometry.rowCentres(src,count,gap,vertical)
        var label=src.label
        val db=writableDatabase
        db.beginTransaction()
        try {
            val ids=centres.map { c ->
                require(PlanGeometry.placementFitsRoom(src.copy(x=c.x,y=c.y),mapWidth,mapHeight)) {
                    "La fila esce dalla pianta: riduci il numero di copie o la distanza."
                }
                label=PlanGeometry.nextLabel(label)
                duplicatePlacement(sourceId,c.x,c.y,label)
            }
            db.setTransactionSuccessful()
            return ids
        } finally { db.endTransaction() }
    }
    /** Item placement captured before a delete, so the delete can be undone. */
    data class ItemSpot(val itemId: Long, val level: Int?, val slot: Int?)
    fun itemSpots(placementId: Long): List<ItemSpot> = readableDatabase.rawQuery(
        "SELECT id,shelf_level,shelf_slot FROM items WHERE placement_id=?",arrayOf(placementId.toString())
    ).use { c -> buildList { while(c.moveToNext()) add(ItemSpot(c.getLong(0),
        if(c.isNull(1)) null else c.getInt(1),if(c.isNull(2)) null else c.getInt(2))) } }
    /** Undo of a delete: same id, same geometry, items put back where they were (if still unplaced). */
    fun restorePlacement(p: Placement, spots: List<ItemSpot>) {
        // A structure brought back must still fit the room: otherwise the backup could not be restored.
        val (mapWidth,mapHeight)=mapBounds(p.warehouseId)
        require(PlanGeometry.placementFitsRoom(p,mapWidth,mapHeight)) {
            "La struttura non entra più nella pianta: impossibile annullare."
        }
        val db=writableDatabase
        db.beginTransaction()
        try {
            db.insertOrThrow("placements",null,ContentValues().apply {
                put("id",p.id);put("warehouse_id",p.warehouseId);put("kind",p.kind);put("label",p.label)
                put("x",p.x);put("y",p.y);put("locked",if(p.locked)1 else 0)
                put("width_m",p.width);put("depth_m",p.depth);put("rotation_deg",p.rotation)
                put("levels",p.levels);put("slots",p.slots)
            })
            for(s in spots) db.update("items",ContentValues().apply {
                put("placement_id",p.id)
                if(s.level==null) putNull("shelf_level") else put("shelf_level",s.level)
                if(s.slot==null) putNull("shelf_slot") else put("shelf_slot",s.slot)
            },"id=? AND placement_id IS NULL AND warehouse_id=?",arrayOf(s.itemId.toString(),p.warehouseId.toString()))
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }
    /** Undo/redo position: ignores the lock (it restores a previous state) but never leaves the room. */
    fun setPlacementPosition(id: Long, x: Float, y: Float) {
        // Bounded below, so a stale undo can never move a structure outside the room.
        require(x.isFinite() && y.isFinite()) { "Coordinate non valide" }
        val p=requireNotNull(placement(id)) { "Struttura non trovata" }
        val (mapWidth,mapHeight)=mapBounds(p.warehouseId)
        val pos=PlanGeometry.boundedPlacementCenter(x,y,p.width,p.depth,p.rotation,mapWidth,mapHeight)
        writableDatabase.update("placements",ContentValues().apply{put("x",pos.x);put("y",pos.y)},"id=?",arrayOf(id.toString()))
    }
    /** Per-structure movement count since [since]: drives the "most used" heat map. */
    fun placementActivity(warehouseId: Long, since: Long): Map<Long,Int> = readableDatabase.rawQuery(
        """SELECT i.placement_id,COUNT(*) FROM movements m JOIN items i ON i.id=m.item_id
           WHERE i.warehouse_id=? AND i.placement_id IS NOT NULL AND m.created_at>=?
           GROUP BY i.placement_id""",arrayOf(warehouseId.toString(),since.toString())
    ).use { c -> buildMap { while(c.moveToNext()) put(c.getLong(0),c.getInt(1)) } }
    fun deletePlacement(id: Long) {
        val db=writableDatabase
        db.beginTransaction()
        try {
            // ON DELETE SET NULL clears placement_id only: clear the shelf position too.
            db.update("items",ContentValues().apply{putNull("shelf_level");putNull("shelf_slot")},
                "placement_id=?",arrayOf(id.toString()))
            db.delete("placements","id=?",arrayOf(id.toString()))
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }
    fun walls(warehouseId: Long): List<Wall> = readableDatabase.rawQuery(
        "SELECT id,warehouse_id,x1,y1,x2,y2,source FROM walls WHERE warehouse_id=? ORDER BY id",arrayOf(warehouseId.toString())
    ).use { c -> buildList { while(c.moveToNext()) add(Wall(c.getLong(0),c.getLong(1),c.getFloat(2),c.getFloat(3),c.getFloat(4),c.getFloat(5),c.getString(6))) } }
    fun addWall(warehouseId: Long, raw: FloatArray): Long {
        val p=StockRules.normalizeWall(raw)
        val (mapWidth,mapHeight)=mapBounds(warehouseId)
        return writableDatabase.insertOrThrow("walls",null,ContentValues().apply {
            put("warehouse_id",warehouseId);put("x1",p[0].coerceIn(0f,mapWidth));put("y1",p[1].coerceIn(0f,mapHeight))
            put("x2",p[2].coerceIn(0f,mapWidth));put("y2",p[3].coerceIn(0f,mapHeight));put("source","manual")
        })
    }
    fun deleteWall(id: Long) { writableDatabase.delete("walls","id=?",arrayOf(id.toString())) }
    fun wall(id: Long): Wall? = readableDatabase.rawQuery(
        "SELECT id,warehouse_id,x1,y1,x2,y2,source FROM walls WHERE id=?",arrayOf(id.toString())
    ).use { c -> if(c.moveToFirst()) Wall(c.getLong(0),c.getLong(1),c.getFloat(2),c.getFloat(3),c.getFloat(4),c.getFloat(5),c.getString(6)) else null }
    fun updateWall(id: Long, raw: FloatArray) {
        val w=requireNotNull(wall(id)) { "Parete non trovata" }
        val p=StockRules.normalizeWall(raw)
        val (mapWidth,mapHeight)=mapBounds(w.warehouseId)
        require(p[0] in 0f..mapWidth && p[2] in 0f..mapWidth && p[1] in 0f..mapHeight && p[3] in 0f..mapHeight) {
            "La parete uscirebbe dalla pianta"
        }
        writableDatabase.update("walls",ContentValues().apply {
            put("x1",p[0]);put("y1",p[1]);put("x2",p[2]);put("y2",p[3])
        },"id=?",arrayOf(id.toString()))
    }
    /** Undo of a wall delete: same id and source (manual or AR). */
    fun restoreWall(w: Wall) {
        val (mapWidth,mapHeight)=mapBounds(w.warehouseId)
        require(w.x1 in 0f..mapWidth && w.x2 in 0f..mapWidth && w.y1 in 0f..mapHeight && w.y2 in 0f..mapHeight) {
            "La parete non entra più nella pianta: impossibile annullare."
        }
        writableDatabase.insertOrThrow("walls",null,ContentValues().apply {
            put("id",w.id);put("warehouse_id",w.warehouseId);put("x1",w.x1);put("y1",w.y1)
            put("x2",w.x2);put("y2",w.y2);put("source",w.source)
        })
    }
    /** Result format v1 from ARCore scanner; normalize meter-world XZ to an editable 0..100 canvas. */
    fun importArScan(warehouseId: Long, json: String): Int {
        val o=JSONObject(json)
        require(o.optString("format")=="OFFICINA_STOCK_AR_WALLS" && o.optInt("version")==1) { "Formato scansione non supportato" }
        val raw=o.getJSONArray("walls")
        require(raw.length() in 1..200) { "Nessuna parete valida o troppe pareti" }
        val segments=buildList {
            for(i in 0 until raw.length()) {
                val p=raw.getJSONArray(i);require(p.length()==4) { "Segmento non valido" }
                val pts=FloatArray(4){ p.getDouble(it).toFloat() }
                add(StockRules.normalizeWall(pts))
            }
        }
        val xs=segments.flatMap{listOf(it[0],it[2])};val ys=segments.flatMap{listOf(it[1],it[3])}
        val minX=xs.minOrNull()!!;val maxX=xs.maxOrNull()!!;val minY=ys.minOrNull()!!;val maxY=ys.maxOrNull()!!
        val (oldWidth,oldHeight)=mapBounds(warehouseId)
        // AR coordinates are metres: enlarge the room if needed, never stretch measured walls.
        val mapWidth=maxOf(oldWidth,maxX-minX+2f);val mapHeight=maxOf(oldHeight,maxY-minY+2f)
        require(mapWidth.isFinite() && mapHeight.isFinite() && mapWidth<=150f && mapHeight<=150f) { "Rilievo oltre il limite di 150 metri" }
        val scale=1f
        val dx=(mapWidth-(maxX-minX))/2;val dy=(mapHeight-(maxY-minY))/2
        val db=writableDatabase
        db.beginTransaction()
        try {
            db.update("warehouses",ContentValues().apply{put("map_width",mapWidth);put("map_height",mapHeight)},"id=?",arrayOf(warehouseId.toString()))
            db.delete("walls","warehouse_id=? AND source='ar'",arrayOf(warehouseId.toString()))
            for(p in segments) db.insertOrThrow("walls",null,ContentValues().apply {
                put("warehouse_id",warehouseId);put("source","ar")
                put("x1",dx+(p[0]-minX)*scale);put("y1",dy+(p[1]-minY)*scale)
                put("x2",dx+(p[2]-minX)*scale);put("y2",dy+(p[3]-minY)*scale)
            })
            db.setTransactionSuccessful()
            return segments.size
        } finally { db.endTransaction() }
    }
    /** Scenario completo e reversibile. I dati utente non vengono modificati. */
    fun seedDemo(warehouseId: Long) {
        require(demoAssetCount(warehouseId) == 0) {
            "Esistono già dati demo: usa Rigenera demo completa per aggiornarli"
        }
        val db=writableDatabase
        val now=System.currentTimeMillis()
        val day=86_400_000L
        val illustrated=mutableListOf<Triple<Long,String,String>>()
        db.beginTransaction()
        try {
            // Distribuzione coerente con una pianta metrica standard 12×8 m.
            val (w,h)=mapBounds(warehouseId)
            val racks=listOf(
                DemoRack("Scaffale", "A · Ricambi meccanici",.22f,.24f,2.5f,.8f,0f),
                DemoRack("Armadio", "B · Utensili",.73f,.24f,1.5f,.85f,0f),
                DemoRack("Scaffale", "C · Automazione",.49f,.24f,2.15f,.8f,0f),
                DemoRack("Scaffale", "D · Pneumatica",.23f,.60f,2.1f,.8f,0f),
                DemoRack("Scaffale", "E · Oleodinamica",.48f,.60f,2.1f,.8f,0f),
                DemoRack("Cassetto", "F · Consumabili",.75f,.60f,1.25f,.85f,0f),
                DemoRack("Armadio", "G · Sicurezza DPI",.89f,.24f,.8f,.95f,0f),
                DemoRack("Banco", "H · Banco assemblaggio",.5f,.82f,2.1f,.9f,0f),
                DemoRack("Area", "I · Materiale accessorio",.88f,.82f,1.4f,1.2f,0f)
            )
            val placementIds=racks.map { r ->
                val id=db.insertOrThrow("placements",null,ContentValues().apply {
                    put("warehouse_id",warehouseId);put("kind",r.kind);put("label",r.label)
                    put("x",(w*r.x).coerceIn(0f,w));put("y",(h*r.y).coerceIn(0f,h));put("locked",1)
                    put("width_m",r.width.coerceAtMost(w*.3f));put("depth_m",r.depth.coerceAtMost(h*.3f))
                    put("rotation_deg",r.angle)
                })
                demoTag(db,warehouseId,"placements",id)
                id
            }
            // Contorno + pareti divisorie, lasciando libero l'ingresso sul lato inferiore.
            val walls=listOf(
                floatArrayOf(.06f,.10f,.94f,.10f),floatArrayOf(.94f,.10f,.94f,.91f),
                floatArrayOf(.06f,.91f,.37f,.91f),floatArrayOf(.63f,.91f,.94f,.91f),
                floatArrayOf(.06f,.10f,.06f,.91f),
                floatArrayOf(.38f,.42f,.38f,.50f),floatArrayOf(.38f,.50f,.66f,.50f),
                floatArrayOf(.66f,.50f,.66f,.43f)
            )
            for(p in walls) {
                val id=db.insertOrThrow("walls",null,ContentValues().apply {
                    put("warehouse_id",warehouseId)
                    put("x1",w*p[0]);put("y1",h*p[1]);put("x2",w*p[2]);put("y2",h*p[3]);put("source","manual")
                })
                demoTag(db,warehouseId,"walls",id)
            }
            val usedDays=listOf(73L,45L,21L,9L,3L)
            val serviceReasons=listOf("Revisione linea 16","Manutenzione preventiva", "Intervento compressori", "Ricambio su guasto", "Rifornimento reparto")
            DemoCatalog.items.forEachIndexed { i, demo ->
                val opening=demo.stock+demo.used90+demo.onLoan-demo.received30
                require(opening>=0) { "Saldo demo negativo: ${demo.name}" }
                val id=db.insertOrThrow("items",null,ContentValues().apply {
                    put("warehouse_id",warehouseId);put("sku",nextSku(db,demo.category));put("name",demo.name)
                    put("category",demo.category);put("quantity",demo.stock);put("minimum",demo.minimum)
                    put("unit",demo.unit);put("note","[DEMO] ${demo.note}")
                    if(demo.placement!=null) put("placement_id",placementIds[demo.placement])
                    if(demo.cost!=null) put("unit_cost",demo.cost)
                    put("supplier",demo.supplier)
                    if(demo.leadDays!=null) put("lead_days",demo.leadDays)
                })
                demoTag(db,warehouseId,"items",id)
                fun event(delta:Double, label:String,daysAgo:Long) {
                    if(delta==0.0) return
                    db.insertOrThrow("movements",null,ContentValues().apply {
                        put("item_id",id);put("delta",delta);put("note",label)
                        put("created_at",now-daysAgo*day-i*1000L)
                    })
                }
                event(opening,"Carico iniziale · demo",115L+(i%5))
                if(demo.used90>0) {
                    var allocated=0.0
                    usedDays.forEachIndexed { idx,ago ->
                        val value=if(idx==usedDays.lastIndex) demo.used90-allocated else
                            kotlin.math.floor(demo.used90*listOf(.17,.18,.25,.19,.21)[idx]*100.0)/100.0
                        allocated+=value
                        event(-value,"Prelievo ${serviceReasons[idx]} · demo",ago)
                    }
                }
                event(demo.received30,"Carico da ${demo.supplier} · demo",29)
                if(demo.onLoan>0.0) {
                    val borrower=if(i%2==0)"Tecnico Mario (demo)" else "Manutentore Luca (demo)"
                    event(-demo.onLoan,"Assegnato a $borrower",12)
                    db.insertOrThrow("loans",null,ContentValues().apply {
                        put("item_id",id);put("borrower",borrower);put("quantity",demo.onLoan)
                        put("created_at",now-12*day)
                    })
                }
                if(demo.illustration) illustrated.add(Triple(id,demo.name,demo.category))
            }
            // Prestiti già restituiti, utili per provare lo storico e le statistiche.
            for ((index,operator) in listOf(19 to "Tecnico Mario (demo)",20 to "Manutentore Luca (demo)")) {
                val target=DemoCatalog.items[index].name
                db.rawQuery("SELECT id FROM items WHERE warehouse_id=? AND name=? AND note LIKE '[DEMO] %'",
                    arrayOf(warehouseId.toString(),target)).use {c ->
                    if(c.moveToFirst()) {
                        val id=c.getLong(0)
                        db.insertOrThrow("loans",null,ContentValues().apply {
                            put("item_id",id);put("borrower",operator);put("quantity",1.0)
                            put("created_at",now-33*day);put("returned_at",now-26*day)
                        })
                        fun history(delta:Double,why:String,timestamp:Long) {
                            db.insertOrThrow("movements",null,ContentValues().apply {
                                put("item_id",id);put("delta",delta);put("note",why)
                                put("created_at",timestamp)
                            })
                        }
                        history(-1.0,"Assegnato a $operator",now-33*day)
                        history(1.0,"Restituito da $operator",now-26*day)
                    }
                }
            }
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
        // Non bloccare o annullare il dataset se il telefono non riesce a creare una miniatura.
        illustrated.forEach { (id,name,category) ->
            val generated=runCatching {DemoArtwork.create(appContext,name,category)}.getOrNull()
            if(generated!=null) runCatching {updatePhoto(id,generated)}.onFailure {
                PhotoFiles.delete(appContext,generated)
            }
        }
    }
    private data class DemoRack(val kind:String,val label:String,val x:Float,val y:Float,
        val width:Float,val depth:Float,val angle:Float)
    fun demoAssetCount(warehouseId: Long): Int = readableDatabase.rawQuery(
        "SELECT COUNT(*) FROM demo_assets WHERE warehouse_id=?",arrayOf(warehouseId.toString())
    ).use {it.moveToFirst();it.getInt(0)}

    /** Solo gli ID etichettati come demo sono cancellabili. Gli altri restano intatti. */
    fun removeDemo(warehouseId: Long): List<String> {
        val db=writableDatabase
        val photos=mutableListOf<String>()
        db.beginTransaction()
        try {
            for((entity,table) in listOf("items" to "items","walls" to "walls","placements" to "placements")) {
                val ids=mutableListOf<Long>()
                db.rawQuery("SELECT entity_id FROM demo_assets WHERE warehouse_id=? AND entity_type=?",
                    arrayOf(warehouseId.toString(),entity)).use {c ->while(c.moveToNext())ids.add(c.getLong(0))}
                for(id in ids) {
                    if(entity=="items") db.rawQuery("SELECT photo_file FROM items WHERE id=? AND warehouse_id=?",
                        arrayOf(id.toString(),warehouseId.toString())).use {c ->
                        if(c.moveToFirst() && !c.isNull(0)) photos.add(c.getString(0))
                    }
                    if(entity=="placements") db.update("items",ContentValues().apply{putNull("shelf_level");putNull("shelf_slot")},
                        "placement_id=?",arrayOf(id.toString()))
                    db.delete(table,"id=? AND warehouse_id=?",arrayOf(id.toString(),warehouseId.toString()))
                }
            }
            db.delete("demo_assets","warehouse_id=?",arrayOf(warehouseId.toString()))
            db.setTransactionSuccessful()
        } finally {db.endTransaction()}
        return photos
    }

    /** Verifica duplicati e ubicazioni senza modificare un singolo record. */
    fun validateCsvImport(warehouseId: Long, rows: List<CsvImportRow>) {
        require(rows.isNotEmpty() && rows.size<=CsvImport.MAX_ITEMS) { "Numero articoli CSV non valido" }
        val existing=items(warehouseId).mapTo(HashSet()) { it.sku }
        val locations=placements(warehouseId).groupBy { it.label }
        val seen=HashSet<String>()
        for(row in rows) {
            if(row.sku.isNotEmpty()) {
                require(seen.add(row.sku)) { "Codice duplicato nel CSV" }
                require(!existing.contains(row.sku)) { "Codice già presente nel magazzino: ${row.sku}" }
            }
            if(row.location.isNotBlank()) {
                require(locations[row.location]?.size==1 && locations[row.location]!!.single().holdsStock) {
                    "Ubicazione non trovata o ambigua: ${row.location}. Prima dell’importazione crea nella Planimetria una struttura con questo nome esatto, oppure rinomina le strutture duplicate."
                }
                val target=locations[row.location]!!.single()
                require((row.shelfLevel ?: 1)<=target.levels && (row.shelfSlot ?: 1)<=target.slots) {
                    "${row.sku.ifEmpty{row.name}}: ${target.label} ha ${target.levels} ripiani e ${target.slots} posizioni. Imposta ripiani e posizioni della struttura prima di importare."
                }
            } else require(row.shelfLevel==null && row.shelfSlot==null) { "${row.sku.ifEmpty{row.name}}: ripiano senza ubicazione" }
        }
    }

    /** Import only NEW items, atomically. Never overwrite existing items or photos. */
    fun importCsv(warehouseId: Long, rows: List<CsvImportRow>): Int {
        val db=writableDatabase
        db.beginTransaction()
        try {
            validateCsvImport(warehouseId,rows)
            val locations=placements(warehouseId).associateBy { it.label }
            // Codes written in the file reserve their numbers first, so an automatic code given to
            // another row can never collide with one that appears later in the same file.
            for(row in rows.filter { it.sku.isNotEmpty() }) bumpSequence(db,row.sku)
            for(row in rows) {
                // No code in the file: the next one of the category, as when the article is created in the app.
                val sku=row.sku.ifEmpty { nextSku(db,row.category) }
                val values=ContentValues().apply {
                    put("warehouse_id",warehouseId);put("sku",sku)
                    put("name",row.name);put("category",row.category)
                    put("quantity",row.quantity);put("minimum",row.minimum)
                    put("unit",row.unit);put("note",row.note)
                    if(row.location.isNotBlank())put("placement_id",locations[row.location]!!.id)
                    row.shelfLevel?.let { put("shelf_level",it) }
                    row.shelfSlot?.let { put("shelf_slot",it) }
                    if(row.cost!=null)put("unit_cost",row.cost)
                    put("supplier",row.supplier)
                    if(row.leadDays!=null)put("lead_days",row.leadDays)
                }
                val id=db.insertOrThrow("items",null,values)
                if(row.quantity>0)recordMovement(db,id,row.quantity,"Carico iniziale da CSV")
                bumpSequence(db,sku)
            }
            db.setTransactionSuccessful()
            return rows.size
        } finally {db.endTransaction()}
    }

    /** CSV safely escaped for Excel, including formula injection prevention. */
    fun csv(warehouseId: Long): String {
        fun field(s: String): String {
            val neutral=if(s.trimStart().firstOrNull() in listOf('=','+','-','@','\t','\r')) "'$s" else s
            return "\"${neutral.replace("\"","\"\"")}\""
        }
        val header=CsvImport.header.joinToString(";") {field(it)}
        val labels=placements(warehouseId).associateBy{it.id}
        return buildString {
            appendLine("\uFEFF$header")
            for(it in items(warehouseId)) appendLine(listOf(it.sku,it.name,it.category,it.quantity.toString(),it.minimum.toString(),it.unit,
                labels[it.placementId]?.label ?: "",it.note,it.unitCost?.toString() ?: "",it.supplier,it.leadDays?.toString() ?: "",
                it.shelfLevel?.toString() ?: "",it.shelfSlot?.toString() ?: "").joinToString(";"){v->field(v)})
        }
    }

    /** SQL foreign keys alone do not enforce warehouse ownership or valid floating point geometry. */
    internal fun validateRestoredData() {
        val db=readableDatabase
        db.rawQuery("""SELECT 1 FROM items i JOIN placements p ON p.id=i.placement_id
            WHERE COALESCE(i.shelf_level,0)>p.levels OR COALESCE(i.shelf_slot,0)>p.slots LIMIT 1""",null).use {
            require(!it.moveToFirst()) { "Articolo su un ripiano inesistente nel backup" }
        }
        db.rawQuery("SELECT 1 FROM items WHERE placement_id IS NULL AND (shelf_level IS NOT NULL OR shelf_slot IS NOT NULL) LIMIT 1",null).use {
            require(!it.moveToFirst()) { "Ripiano senza struttura nel backup" }
        }
        db.rawQuery("SELECT 1 FROM items i JOIN placements p ON p.id=i.placement_id WHERE i.warehouse_id<>p.warehouse_id LIMIT 1",null).use {
            require(!it.moveToFirst()) { "Ubicazione collegata a un altro magazzino nel backup" }
        }
        for(profile in profiles()) for(w in warehouses(profile.id)) {
            require(w.mapWidth.isFinite() && w.mapWidth in 2f..150f && w.mapHeight.isFinite() && w.mapHeight in 2f..150f) { "Dimensioni pianta non valide nel backup" }
            for(p in placements(w.id)) require(listOf(p.x,p.y,p.width,p.depth,p.rotation).all{it.isFinite()} && p.width in .2f..15f && p.depth in .2f..15f && PlanGeometry.placementFitsRoom(p,w.mapWidth,w.mapHeight)) { "Struttura non valida nel backup" }
            for(wall in walls(w.id)) {
                StockRules.normalizeWall(floatArrayOf(wall.x1,wall.y1,wall.x2,wall.y2))
                require(wall.x1 in 0f..w.mapWidth && wall.x2 in 0f..w.mapWidth && wall.y1 in 0f..w.mapHeight && wall.y2 in 0f..w.mapHeight) { "Parete fuori dalla pianta nel backup" }
            }
        }
        db.rawQuery("SELECT quantity,minimum,unit_cost FROM items",null).use { c -> while(c.moveToNext()) {
            require(StockRules.validQuantity(c.getDouble(0)) && StockRules.validQuantity(c.getDouble(1)) &&
                (c.isNull(2) || StockRules.validQuantity(c.getDouble(2)))) { "Quantità o costo non valido nel backup" }
        } }
        db.rawQuery("SELECT quantity,received,status FROM ops_orders",null).use { c -> while(c.moveToNext()) {
            val total=c.getDouble(0);val received=c.getDouble(1)
            require(total.isFinite() && received.isFinite() && received<=total &&
                (c.getString(2)!="RECEIVED" || received==total)) { "Ricezione ordine incoerente nel backup" }
        } }
        // Foreign keys cannot by themselves enforce cross-profile business ownership.
        for((query,message) in listOf(
            """SELECT 1 FROM org_assets a JOIN warehouses w ON w.id=a.warehouse_id
                JOIN org_centers c ON c.id=a.center_id
                WHERE c.profile_id<>w.profile_id LIMIT 1""" to "Macchina assegnata a centro di un altro profilo",
            """SELECT 1 FROM ops_orders o JOIN warehouses w ON w.id=o.warehouse_id
                JOIN org_centers c ON c.id=o.center_id
                WHERE c.profile_id<>w.profile_id LIMIT 1""" to "Ordine con centro di costo esterno",
            """SELECT 1 FROM ops_orders o JOIN warehouses w ON w.id=o.warehouse_id
                JOIN org_suppliers s ON s.id=o.supplier_id
                WHERE s.profile_id<>w.profile_id LIMIT 1""" to "Ordine con fornitore di un altro profilo",
            """SELECT 1 FROM ops_maintenance m JOIN org_assets a ON a.id=m.asset_id
                WHERE a.warehouse_id<>m.warehouse_id LIMIT 1""" to "Intervento collegato ad altra macchina"
        )) db.rawQuery(query,null).use {c->require(!c.moveToFirst()){message}}
        for((table,quantityFilter) in listOf(
            "stock_lots" to "quantity > 0","stock_serials" to "state = 'IN_STOCK'"
        )) db.rawQuery("""SELECT 1 FROM $table t JOIN items i ON i.id=t.item_id
            WHERE t.warehouse_id<>i.warehouse_id LIMIT 1""",null).use {
            require(!it.moveToFirst()){"Tracciabilità associata a un altro magazzino"}
        }
        db.rawQuery("""SELECT 1 FROM items i WHERE
            (SELECT COALESCE(SUM(l.quantity),0) FROM stock_lots l WHERE l.item_id=i.id)
            +(SELECT COUNT(*) FROM stock_serials s WHERE s.item_id=i.id AND s.state='IN_STOCK')
            >i.quantity+0.0000001 LIMIT 1""",null).use {
            require(!it.moveToFirst()){"Tracciabilità superiore alla giacenza"}
        }
        db.rawQuery("""SELECT 1 FROM items i WHERE
            EXISTS(SELECT 1 FROM stock_lots l WHERE l.item_id=i.id AND l.quantity>0)
            AND EXISTS(SELECT 1 FROM stock_serials s WHERE s.item_id=i.id AND s.state='IN_STOCK')
            LIMIT 1""",null).use {
            require(!it.moveToFirst()){"Registrazioni lotto e seriale incompatibili"}
        }
        db.rawQuery("""SELECT 1 FROM stock_reservations r JOIN items i ON i.id=r.item_id
            WHERE r.warehouse_id<>i.warehouse_id LIMIT 1""",null).use {
            require(!it.moveToFirst()){"Prenotazione collegata a un altro magazzino"}
        }
        db.rawQuery("""SELECT i.id,i.quantity,COALESCE(SUM(r.quantity),0)
            FROM items i JOIN stock_reservations r ON r.item_id=i.id AND r.status='ACTIVE'
            GROUP BY i.id HAVING SUM(r.quantity)>i.quantity+0.0000001 LIMIT 1""",null).use {
            require(!it.moveToFirst()){"Prenotazioni superiori alle scorte fisiche"}
        }
        // Prevent cross-warehouse workflow references even in imported encrypted archives.
        db.rawQuery("""SELECT 1 FROM ops_orders o JOIN items i ON i.id=o.item_id
            WHERE o.warehouse_id<>i.warehouse_id LIMIT 1""",null).use {
            require(!it.moveToFirst()){"Ordine collegato a un altro magazzino nel backup"}
        }
        db.rawQuery("""SELECT 1 FROM ops_counts c JOIN items i ON i.id=c.item_id
            WHERE c.warehouse_id<>i.warehouse_id LIMIT 1""",null).use {
            require(!it.moveToFirst()){"Conteggio collegato a un altro magazzino nel backup"}
        }
        db.rawQuery("""SELECT 1 FROM ops_maintenance m JOIN items i ON i.id=m.item_id
            WHERE m.warehouse_id<>i.warehouse_id LIMIT 1""",null).use {
            require(!it.moveToFirst()){"Ricambio manutenzione in altro magazzino nel backup"}
        }
        db.rawQuery("""SELECT 1 FROM ops_transfers t JOIN items i ON i.id=t.item_id
            JOIN warehouses a ON a.id=t.from_id JOIN warehouses b ON b.id=t.to_id
            WHERE t.from_id<>i.warehouse_id OR a.profile_id<>b.profile_id LIMIT 1""",null).use {
            require(!it.moveToFirst()){"Trasferimento non coerente nel backup"}
        }
        // Restoring stale counters must not make the next insert collide with an existing SKU.
        db.rawQuery("SELECT sku FROM items",null).use { c -> while(c.moveToNext()) bumpSequence(db,c.getString(0)) }
        // Archives from before 4.8 have no global ids: give them one now.
        TeamSyncSchema.backfillIds(db)
    }
}

