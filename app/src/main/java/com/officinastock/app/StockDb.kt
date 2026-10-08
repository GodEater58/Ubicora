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
class StockDb(context: Context): SQLiteOpenHelper(context, "officina_stock_native4.db", null, 1) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("PRAGMA foreign_keys = ON")
        db.execSQL("""CREATE TABLE profiles (
            id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL,
            salt TEXT NOT NULL, verifier TEXT NOT NULL,
            created_at INTEGER NOT NULL)""")
        db.execSQL("""CREATE TABLE warehouses (
            id INTEGER PRIMARY KEY AUTOINCREMENT, profile_id INTEGER NOT NULL REFERENCES profiles(id) ON DELETE CASCADE,
            name TEXT NOT NULL, location TEXT NOT NULL DEFAULT '')""")
        db.execSQL("""CREATE TABLE placements (
            id INTEGER PRIMARY KEY AUTOINCREMENT, warehouse_id INTEGER NOT NULL REFERENCES warehouses(id) ON DELETE CASCADE,
            kind TEXT NOT NULL, label TEXT NOT NULL, x REAL NOT NULL, y REAL NOT NULL,
            locked INTEGER NOT NULL DEFAULT 0)""")
        db.execSQL("""CREATE TABLE items (
            id INTEGER PRIMARY KEY AUTOINCREMENT, warehouse_id INTEGER NOT NULL REFERENCES warehouses(id) ON DELETE CASCADE,
            sku TEXT NOT NULL, name TEXT NOT NULL, category TEXT NOT NULL,
            quantity REAL NOT NULL DEFAULT 0 CHECK(quantity >= 0),
            minimum REAL NOT NULL DEFAULT 0 CHECK(minimum >= 0),
            unit TEXT NOT NULL DEFAULT 'pz', placement_id INTEGER REFERENCES placements(id) ON DELETE SET NULL,
            note TEXT NOT NULL DEFAULT '', UNIQUE(warehouse_id,sku))""")
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
        db.execSQL("CREATE INDEX idx_items_warehouse ON items(warehouse_id,name)")
        db.execSQL("CREATE INDEX idx_placements_warehouse ON placements(warehouse_id)")
        db.execSQL("CREATE INDEX idx_movements_item ON movements(item_id,created_at DESC)")
        db.execSQL("CREATE INDEX idx_loans_item ON loans(item_id,returned_at)")
    }
    override fun onConfigure(db: SQLiteDatabase) { super.onConfigure(db); db.setForeignKeyConstraintsEnabled(true) }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Le future migrazioni devono essere additive: nessun DROP TABLE.
        throw IllegalStateException("Migrazione database mancante: $oldVersion → $newVersion")
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
        readableDatabase.rawQuery("SELECT salt,verifier FROM profiles WHERE id=?", arrayOf(profileId.toString())).use { c ->
            if(!c.moveToFirst()) return false
            val current = proof(pin,Base64.getDecoder().decode(c.getString(0)))
            return java.security.MessageDigest.isEqual(current.toByteArray(),c.getString(1).toByteArray())
        }
    }
    fun createWarehouse(profileId: Long, name: String, location: String): Long {
        require(name.trim().isNotEmpty()) { "Inserisci un nome per il magazzino" }
        return writableDatabase.insertOrThrow("warehouses",null,ContentValues().apply {
            put("profile_id",profileId);put("name",name.trim());put("location",location.trim())
        })
    }
    fun warehouses(profileId: Long): List<Warehouse> = readableDatabase.rawQuery(
        "SELECT id,profile_id,name,location FROM warehouses WHERE profile_id=? ORDER BY id",arrayOf(profileId.toString())
    ).use { c -> buildList { while(c.moveToNext()) add(Warehouse(c.getLong(0),c.getLong(1),c.getString(2),c.getString(3))) } }
    fun items(warehouseId: Long, filter: String = ""): List<StockItem> {
        val query = """SELECT id,warehouse_id,sku,name,category,quantity,minimum,unit,placement_id,note
            FROM items WHERE warehouse_id=? AND (?='' OR sku LIKE ? OR name LIKE ? OR category LIKE ?)
            ORDER BY name COLLATE NOCASE,id LIMIT 20000"""
        val safeFilter = "%${filter.trim().replace("\\","\\\\").replace("%","\\%").replace("_","\\_")}%"
        // Backslash escaping is explicit for SQL LIKE so '%' in search is interpreted literally.
        val escapedQuery = query.replace(" LIKE ?", " LIKE ? ESCAPE '\\'")
        return readableDatabase.rawQuery(escapedQuery,arrayOf(warehouseId.toString(),filter.trim(),safeFilter,safeFilter,safeFilter)).use { c ->
            buildList { while(c.moveToNext()) add(c.toItem()) }
        }
    }
    private fun Cursor.toItem() = StockItem(getLong(0),getLong(1),getString(2),getString(3),getString(4),
        getDouble(5),getDouble(6),getString(7),if(isNull(8)) null else getLong(8),getString(9))
    fun item(id: Long): StockItem? = readableDatabase.rawQuery(
        "SELECT id,warehouse_id,sku,name,category,quantity,minimum,unit,placement_id,note FROM items WHERE id=?",arrayOf(id.toString())
    ).use { if(it.moveToFirst()) it.toItem() else null }
    fun addItem(warehouseId: Long, skuInput: String, name: String, category: String,
                quantity: Double, minimum: Double, unit: String, note: String): Long {
        val sku = StockRules.sku(skuInput)
        require(sku.isNotEmpty() && name.trim().isNotEmpty()) { "Codice e descrizione obbligatori" }
        require(StockRules.validQuantity(quantity) && StockRules.validQuantity(minimum)) { "Quantità non valide" }
        val db = writableDatabase
        db.beginTransaction()
        try {
            val id = db.insertOrThrow("items",null,ContentValues().apply {
                put("warehouse_id",warehouseId);put("sku",sku);put("name",name.trim());put("category",category.trim().ifEmpty { "Altro" })
                put("quantity",quantity);put("minimum",minimum);put("unit",unit.trim().ifEmpty { "pz" });put("note",note.trim())
            })
            if(quantity > 0) recordMovement(db,id,quantity,"Carico iniziale")
            db.setTransactionSuccessful()
            return id
        } catch(e: SQLiteConstraintException) { throw IllegalArgumentException("Codice già utilizzato in questo magazzino",e) }
        finally { db.endTransaction() }
    }
    private fun recordMovement(db: SQLiteDatabase, itemId: Long, delta: Double, note: String) {
        db.insertOrThrow("movements",null,ContentValues().apply {
            put("item_id",itemId);put("delta",delta);put("note",note);put("created_at",System.currentTimeMillis())
        })
    }
    private fun adjust(db: SQLiteDatabase, itemId: Long, delta: Double, note: String) {
        db.rawQuery("SELECT quantity FROM items WHERE id=?",arrayOf(itemId.toString())).use { cursor ->
            require(cursor.moveToFirst()) { "Articolo non trovato" }
            val next = StockRules.applyDelta(cursor.getDouble(0),delta)
            db.update("items",ContentValues().apply { put("quantity",next) },"id=?",arrayOf(itemId.toString()))
            recordMovement(db,itemId,delta,note)
        }
    }
    fun movement(itemId: Long, delta: Double, note: String) {
        require(delta.isFinite() && delta != 0.0) { "Inserisci una quantità diversa da zero" }
        writableDatabase.apply { beginTransaction();try { adjust(this,itemId,delta,note.ifBlank { if(delta>0) "Carico" else "Prelievo" });setTransactionSuccessful() } finally { endTransaction() } }
    }
    fun movements(warehouseId: Long): List<Movement> = readableDatabase.rawQuery(
        """SELECT m.id,m.item_id,i.sku,i.name,m.delta,m.note,m.created_at FROM movements m
        JOIN items i ON i.id=m.item_id WHERE i.warehouse_id=? ORDER BY m.created_at DESC,m.id DESC LIMIT 250""",
        arrayOf(warehouseId.toString())
    ).use { c -> buildList { while(c.moveToNext()) add(Movement(c.getLong(0),c.getLong(1),c.getString(2),c.getString(3),c.getDouble(4),c.getString(5),c.getLong(6))) } }
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
        "SELECT id,warehouse_id,kind,label,x,y,locked FROM placements WHERE warehouse_id=? ORDER BY id",arrayOf(warehouseId.toString())
    ).use { c -> buildList { while(c.moveToNext()) add(Placement(c.getLong(0),c.getLong(1),c.getString(2),c.getString(3),c.getFloat(4),c.getFloat(5),c.getInt(6)!=0)) } }
    fun addPlacement(warehouseId: Long, kind: String, label: String, x: Float, y: Float): Long {
        require(label.trim().isNotEmpty()) { "Inserisci un nome" }
        return writableDatabase.insertOrThrow("placements",null,ContentValues().apply {
            put("warehouse_id",warehouseId);put("kind",kind);put("label",label.trim())
            put("x",StockRules.mapPosition(x));put("y",StockRules.mapPosition(y));put("locked",0)
        })
    }
    fun movePlacement(id: Long, x: Float, y: Float): Boolean {
        return writableDatabase.update("placements",ContentValues().apply {
            put("x",StockRules.mapPosition(x));put("y",StockRules.mapPosition(y))
        },"id=? AND locked=0",arrayOf(id.toString())) == 1
    }
    fun lockPlacement(id: Long, locked: Boolean) { writableDatabase.update("placements",ContentValues().apply {
        put("locked",if(locked) 1 else 0)
    },"id=?",arrayOf(id.toString())) }
    fun assignItem(itemId: Long, placementId: Long?) {
        val item = requireNotNull(item(itemId)) { "Articolo inesistente" }
        if(placementId!=null) {
            val allowed = readableDatabase.rawQuery("SELECT 1 FROM placements WHERE id=? AND warehouse_id=?",arrayOf(placementId.toString(),item.warehouseId.toString())).use{it.moveToFirst()}
            require(allowed) { "Ubicazione di un altro magazzino" }
        }
        writableDatabase.update("items",ContentValues().apply {
            if(placementId==null) putNull("placement_id") else put("placement_id",placementId)
        },"id=?",arrayOf(itemId.toString()))
    }
    fun deletePlacement(id: Long) { writableDatabase.delete("placements","id=?",arrayOf(id.toString())) }
    fun walls(warehouseId: Long): List<Wall> = readableDatabase.rawQuery(
        "SELECT id,warehouse_id,x1,y1,x2,y2,source FROM walls WHERE warehouse_id=? ORDER BY id",arrayOf(warehouseId.toString())
    ).use { c -> buildList { while(c.moveToNext()) add(Wall(c.getLong(0),c.getLong(1),c.getFloat(2),c.getFloat(3),c.getFloat(4),c.getFloat(5),c.getString(6))) } }
    fun addWall(warehouseId: Long, raw: FloatArray): Long {
        val p=StockRules.normalizeWall(raw)
        return writableDatabase.insertOrThrow("walls",null,ContentValues().apply {
            put("warehouse_id",warehouseId);put("x1",StockRules.mapPosition(p[0]));put("y1",StockRules.mapPosition(p[1]))
            put("x2",StockRules.mapPosition(p[2]));put("y2",StockRules.mapPosition(p[3]));put("source","manual")
        })
    }
    fun deleteWall(id: Long) { writableDatabase.delete("walls","id=?",arrayOf(id.toString())) }
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
        val scale=86f/maxOf(1f,maxX-minX,maxY-minY)
        val dx=(100f-(maxX-minX)*scale)/2;val dy=(100f-(maxY-minY)*scale)/2
        val db=writableDatabase
        db.beginTransaction()
        try {
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
    /** Dati sintetici opzionali per provare la UX. Non parte mai automaticamente. */
    fun seedDemo(warehouseId: Long) {
        require(items(warehouseId).isEmpty() && placements(warehouseId).isEmpty() && walls(warehouseId).isEmpty()) {
            "Il magazzino deve essere vuoto per caricare la demo"
        }
        val db=writableDatabase
        db.beginTransaction()
        try {
            val shelves=listOf(Triple("Scaffale","A · Ricambi",28f to 31f),
                Triple("Armadio","B · Utensili",72f to 30f),
                Triple("Cassetto","C · Elettrico",51f to 73f))
            val ids=shelves.map { (kind,label,pos) ->
                db.insertOrThrow("placements",null,ContentValues().apply {
                    put("warehouse_id",warehouseId);put("kind",kind);put("label",label)
                    put("x",pos.first);put("y",pos.second);put("locked",1)
                })
            }
            val data=listOf(
                listOf("VIT-M8-035","Vite TCEI M8 x 35","Meccanica","240","40","pz","0"),
                listOf("CUS-6204","Cuscinetto 6204 2RS","Ricambi","12","8","pz","0"),
                listOf("SEN-IND-18","Sensore induttivo M18","Elettrico","3","6","pz","2"),
                listOf("CHI-DIN-13","Chiave combinata 13 mm","Utensili","6","2","pz","1"),
                listOf("RAC-14","Raccordo pneumatico 1/4","Pneumatica","5","10","pz","0"),
                listOf("FUS-10A","Fusibile rapido 10 A","Elettrico","44","15","pz","2"),
                listOf("GUA-NBR","Guarnizione NBR 30 mm","Ricambi","18","8","pz","0"),
                listOf("BRO-6","Punta HSS Ø6","Utensili","9","4","pz","1")
            )
            for(row in data) {
                val id=db.insertOrThrow("items",null,ContentValues().apply {
                    put("warehouse_id",warehouseId);put("sku",row[0]);put("name",row[1]);put("category",row[2]);
                    put("quantity",row[3].toDouble());put("minimum",row[4].toDouble());put("unit",row[5]);
                    put("placement_id",ids[row[6].toInt()]);put("note","Dati dimostrativi")
                })
                recordMovement(db,id,row[3].toDouble(),"Carico iniziale · demo")
            }
            listOf(floatArrayOf(9f,9f,91f,9f),floatArrayOf(91f,9f,91f,91f),
                floatArrayOf(91f,91f,9f,91f),floatArrayOf(9f,91f,9f,9f)).forEach { p ->
                db.insertOrThrow("walls",null,ContentValues().apply {
                    put("warehouse_id",warehouseId);put("x1",p[0]);put("y1",p[1]);
                    put("x2",p[2]);put("y2",p[3]);put("source","manual")
                })
            }
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }
    /** CSV safely escaped for Excel, including formula injection prevention. */
    fun csv(warehouseId: Long): String {
        fun field(s: String): String {
            val neutral=if(s.trimStart().firstOrNull() in listOf('=','+','-','@','\t','\r')) "'$s" else s
            return "\"${neutral.replace("\"","\"\"")}\""
        }
        val header=listOf("Codice","Descrizione","Categoria","Quantità","Minimo","Unità","Ubicazione","Note").joinToString(";") {field(it)}
        val labels=placements(warehouseId).associateBy{it.id}
        return buildString {
            appendLine("\uFEFF$header")
            for(it in items(warehouseId)) appendLine(listOf(it.sku,it.name,it.category,it.quantity.toString(),it.minimum.toString(),it.unit,
                labels[it.placementId]?.label ?: "",it.note).joinToString(";"){v->field(v)})
        }
    }
}
