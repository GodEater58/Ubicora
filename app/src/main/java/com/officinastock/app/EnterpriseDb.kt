package com.officinastock.app

import android.content.ContentValues
import com.officinastock.app.AppDatabase as SQLiteDatabase
import org.json.JSONObject
import java.util.UUID

/** Offline-first business directories. IDs are local; public UUIDs are stable across server migrations. */
data class CostCenter(val id:Long,val profileId:Long,val uuid:String,val code:String,
    val name:String,val monthlyBudget:Double?)
data class BusinessSupplier(val id:Long,val profileId:Long,val uuid:String,val name:String,
    val taxId:String,val email:String,val leadDays:Int?)
data class BusinessAsset(val id:Long,val warehouseId:Long,val uuid:String,val code:String,
    val name:String,val centerId:Long?)
data class StaffDirectoryEntry(val id:Long,val profileId:Long,val uuid:String,val displayName:String,
    val proposedRole:String,val providerSubject:String?)
data class PendingSyncEvent(val id:String,val profileId:Long,val resource:String,
    val entityUuid:String,val operation:String,val payload:String,val createdAt:Long)

/** A directory is NOT a permission system. No local role here can approve operations. */
class EnterpriseDb(private val stock:StockDb) {
    companion object {
        val roles=setOf("MANAGER","PURCHASING","WAREHOUSE","MAINTENANCE","VIEWER")
        fun createTables(db:SQLiteDatabase) {
            db.execSQL("""CREATE TABLE IF NOT EXISTS org_centers(
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                profile_id INTEGER NOT NULL REFERENCES profiles(id) ON DELETE CASCADE,
                uuid TEXT NOT NULL UNIQUE,
                code TEXT NOT NULL, name TEXT NOT NULL,
                monthly_budget REAL DEFAULT NULL CHECK(monthly_budget >= 0),
                created_at INTEGER NOT NULL,
                UNIQUE(profile_id,code)
            )""")
            db.execSQL("""CREATE TABLE IF NOT EXISTS org_suppliers(
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                profile_id INTEGER NOT NULL REFERENCES profiles(id) ON DELETE CASCADE,
                uuid TEXT NOT NULL UNIQUE,
                name TEXT NOT NULL, tax_id TEXT NOT NULL DEFAULT '',
                email TEXT NOT NULL DEFAULT '',
                lead_days INTEGER DEFAULT NULL CHECK(lead_days BETWEEN 0 AND 3650),
                created_at INTEGER NOT NULL,
                UNIQUE(profile_id,name)
            )""")
            db.execSQL("""CREATE TABLE IF NOT EXISTS org_assets(
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                warehouse_id INTEGER NOT NULL REFERENCES warehouses(id) ON DELETE CASCADE,
                uuid TEXT NOT NULL UNIQUE,
                code TEXT NOT NULL, name TEXT NOT NULL,
                center_id INTEGER REFERENCES org_centers(id) ON DELETE SET NULL,
                created_at INTEGER NOT NULL,
                UNIQUE(warehouse_id,code)
            )""")
            db.execSQL("""CREATE TABLE IF NOT EXISTS org_staff_directory(
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                profile_id INTEGER NOT NULL REFERENCES profiles(id) ON DELETE CASCADE,
                uuid TEXT NOT NULL UNIQUE,
                display_name TEXT NOT NULL,
                proposed_role TEXT NOT NULL CHECK(proposed_role IN ('MANAGER','PURCHASING','WAREHOUSE','MAINTENANCE','VIEWER')),
                provider_subject TEXT DEFAULT NULL,
                created_at INTEGER NOT NULL
            )""")
            db.execSQL("""CREATE TABLE IF NOT EXISTS sync_outbox(
                id TEXT PRIMARY KEY NOT NULL,
                profile_id INTEGER NOT NULL REFERENCES profiles(id) ON DELETE CASCADE,
                resource TEXT NOT NULL,
                entity_uuid TEXT NOT NULL,
                operation TEXT NOT NULL CHECK(operation IN ('CREATE','UPDATE','DELETE')),
                payload TEXT NOT NULL,
                created_at INTEGER NOT NULL,
                attempt_count INTEGER NOT NULL DEFAULT 0 CHECK(attempt_count >= 0),
                ack_at INTEGER DEFAULT NULL
            )""")
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_org_centers_profile ON org_centers(profile_id)")
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_org_suppliers_profile ON org_suppliers(profile_id)")
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_org_assets_warehouse ON org_assets(warehouse_id)")
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_sync_outbox_profile ON sync_outbox(profile_id,ack_at,created_at)")
        }
        fun addOperationLinks(db:SQLiteDatabase) {
            // Support upgrades and legacy test fixtures that already contain a subset of columns.
            fun addIfMissing(table:String,column:String,sql:String) {
                val exists=db.rawQuery("PRAGMA table_info($table)",null).use{c->
                    val col=c.getColumnIndexOrThrow("name")
                    var found=false
                    while(c.moveToNext()) if(c.getString(col)==column)found=true
                    found
                }
                if(!exists)db.execSQL(sql)
            }
            addIfMissing("ops_orders","center_id",
                "ALTER TABLE ops_orders ADD COLUMN center_id INTEGER REFERENCES org_centers(id) ON DELETE SET NULL")
            addIfMissing("ops_orders","supplier_id",
                "ALTER TABLE ops_orders ADD COLUMN supplier_id INTEGER REFERENCES org_suppliers(id) ON DELETE SET NULL")
            addIfMissing("ops_maintenance","asset_id",
                "ALTER TABLE ops_maintenance ADD COLUMN asset_id INTEGER REFERENCES org_assets(id) ON DELETE SET NULL")
        }
    }
    private fun profileExists(id:Long)=stock.readableDatabase.rawQuery("SELECT 1 FROM profiles WHERE id=?",
        arrayOf(id.toString())).use {it.moveToFirst()}
    private fun belongsToProfile(table:String,id:Long,profileId:Long):Boolean =
        stock.readableDatabase.rawQuery("SELECT 1 FROM $table WHERE id=? AND profile_id=?",
            arrayOf(id.toString(),profileId.toString())).use{it.moveToFirst()}
    private fun profileForWarehouse(id:Long):Long=stock.readableDatabase.rawQuery(
        "SELECT profile_id FROM warehouses WHERE id=?",arrayOf(id.toString())
    ).use{c->require(c.moveToFirst()){"Magazzino inesistente"};c.getLong(0)}
    private fun storeEvent(db:SQLiteDatabase,pid:Long,resource:String,entityUuid:String,payload:JSONObject){
        db.insertOrThrow("sync_outbox",null,ContentValues().apply{
            put("id",UUID.randomUUID().toString());put("profile_id",pid)
            put("resource",resource);put("entity_uuid",entityUuid)
            put("operation","CREATE");put("payload",payload.toString())
            put("created_at",System.currentTimeMillis())
        })
    }
    private inline fun <T> transaction(block:(SQLiteDatabase)->T):T {
        val db=stock.writableDatabase
        db.beginTransaction()
        try{val result=block(db);db.setTransactionSuccessful();return result}
        finally{db.endTransaction()}
    }
    fun createCenter(profileId:Long,code:String,name:String,budget:Double?):Long {
        require(profileExists(profileId)){"Profilo inesistente"}
        val key=code.trim().uppercase(java.util.Locale.ROOT)
        require(key.matches(Regex("[A-Z0-9_-]{2,24}")) && name.trim().length in 2..100){"Centro di costo non valido"}
        require(budget==null || (budget.isFinite()&&budget>=0.0&&budget<=1e12)){"Budget non valido"}
        return transaction{db->
            val uuid=UUID.randomUUID().toString()
            val id=db.insertOrThrow("org_centers",null,ContentValues().apply{
                put("profile_id",profileId);put("uuid",uuid);put("code",key)
                put("name",name.trim());if(budget!=null)put("monthly_budget",budget)
                put("created_at",System.currentTimeMillis())
            })
            storeEvent(db,profileId,"cost_center",uuid,JSONObject().put("uuid",uuid)
                .put("code",key).put("name",name.trim()).put("monthly_budget",budget ?: JSONObject.NULL))
            id
        }
    }
    fun centers(profileId:Long):List<CostCenter> = stock.readableDatabase.rawQuery(
        "SELECT id,profile_id,uuid,code,name,monthly_budget FROM org_centers WHERE profile_id=? ORDER BY code",
        arrayOf(profileId.toString())).use {c->buildList{
            while(c.moveToNext())add(CostCenter(c.getLong(0),c.getLong(1),c.getString(2),
                c.getString(3),c.getString(4),if(c.isNull(5))null else c.getDouble(5)))
        }}
    fun createSupplier(profileId:Long,name:String,taxId:String,email:String,leadDays:Int?):Long {
        require(profileExists(profileId)){"Profilo inesistente"}
        require(name.trim().length in 2..160 && taxId.length<=40 && email.length<=180){"Fornitore non valido"}
        require(email.isBlank() || (email.contains('@') && !email.contains('\n'))){"Email non valida"}
        require(leadDays==null || leadDays in 0..3650){"Tempo consegna non valido"}
        return transaction{db->
            val uuid=UUID.randomUUID().toString()
            val id=db.insertOrThrow("org_suppliers",null,ContentValues().apply{
                put("profile_id",profileId);put("uuid",uuid);put("name",name.trim())
                put("tax_id",taxId.trim());put("email",email.trim())
                if(leadDays!=null)put("lead_days",leadDays)
                put("created_at",System.currentTimeMillis())
            })
            storeEvent(db,profileId,"supplier",uuid,JSONObject().put("uuid",uuid)
                .put("name",name.trim()).put("tax_id",taxId.trim())
                .put("email",email.trim()).put("lead_days",leadDays ?: JSONObject.NULL))
            id
        }
    }
    fun suppliers(profileId:Long):List<BusinessSupplier> = stock.readableDatabase.rawQuery(
        "SELECT id,profile_id,uuid,name,tax_id,email,lead_days FROM org_suppliers WHERE profile_id=? ORDER BY name",
        arrayOf(profileId.toString())).use{c->buildList{
            while(c.moveToNext())add(BusinessSupplier(c.getLong(0),c.getLong(1),c.getString(2),
                c.getString(3),c.getString(4),c.getString(5),if(c.isNull(6))null else c.getInt(6)))
        }}
    fun createAsset(warehouseId:Long,code:String,name:String,centerId:Long?):Long {
        val pid=profileForWarehouse(warehouseId)
        val key=code.trim().uppercase(java.util.Locale.ROOT)
        require(key.matches(Regex("[A-Z0-9_-]{2,32}")) && name.trim().length in 2..160){"Macchina non valida"}
        require(centerId==null || belongsToProfile("org_centers",centerId,pid)){"Centro di costo di un altro profilo"}
        return transaction{db->
            val uuid=UUID.randomUUID().toString()
            val id=db.insertOrThrow("org_assets",null,ContentValues().apply{
                put("warehouse_id",warehouseId);put("uuid",uuid);put("code",key)
                put("name",name.trim());if(centerId!=null)put("center_id",centerId)
                put("created_at",System.currentTimeMillis())
            })
            storeEvent(db,pid,"asset",uuid,JSONObject().put("uuid",uuid).put("code",key)
                .put("name",name.trim()).put("center_uuid",centerId?.let{centerUuid(it)} ?: JSONObject.NULL))
            id
        }
    }
    private fun centerUuid(centerId:Long):String=stock.readableDatabase.rawQuery(
        "SELECT uuid FROM org_centers WHERE id=?",arrayOf(centerId.toString())
    ).use{c->require(c.moveToFirst());c.getString(0)}
    fun assets(warehouseId:Long):List<BusinessAsset> = stock.readableDatabase.rawQuery(
        "SELECT id,warehouse_id,uuid,code,name,center_id FROM org_assets WHERE warehouse_id=? ORDER BY code",
        arrayOf(warehouseId.toString())).use{c->buildList{
            while(c.moveToNext())add(BusinessAsset(c.getLong(0),c.getLong(1),c.getString(2),
                c.getString(3),c.getString(4),if(c.isNull(5))null else c.getLong(5)))
        }}
    /** Directory only. Never grants login, approves purchases or circumvents existing PIN. */
    fun addStaffDirectoryEntry(profileId:Long,name:String,role:String):Long {
        require(profileExists(profileId)&&name.trim().length in 2..100 && role in roles){"Persona o ruolo non validi"}
        return transaction{db->
            val uuid=UUID.randomUUID().toString()
            val id=db.insertOrThrow("org_staff_directory",null,ContentValues().apply {
                put("profile_id",profileId);put("uuid",uuid);put("display_name",name.trim())
                put("proposed_role",role);put("created_at",System.currentTimeMillis())
            })
            storeEvent(db,profileId,"staff_directory",uuid,JSONObject().put("uuid",uuid)
                .put("display_name",name.trim()).put("proposed_role",role))
            id
        }
    }
    fun staffDirectory(profileId:Long):List<StaffDirectoryEntry> =stock.readableDatabase.rawQuery(
        "SELECT id,profile_id,uuid,display_name,proposed_role,provider_subject FROM org_staff_directory WHERE profile_id=? ORDER BY display_name",
        arrayOf(profileId.toString())).use{c->buildList{
            while(c.moveToNext())add(StaffDirectoryEntry(c.getLong(0),c.getLong(1),c.getString(2),
                c.getString(3),c.getString(4),if(c.isNull(5))null else c.getString(5)))
        }}
    fun pendingEventCount(profileId:Long):Int = stock.readableDatabase.rawQuery(
        "SELECT COUNT(*) FROM sync_outbox WHERE profile_id=? AND ack_at IS NULL",
        arrayOf(profileId.toString())).use{c->require(c.moveToFirst());c.getInt(0)}
    fun pendingEvents(profileId:Long,limit:Int=100):List<PendingSyncEvent> {
        require(limit in 1..100)
        return stock.readableDatabase.rawQuery("""SELECT id,profile_id,resource,entity_uuid,operation,payload,created_at
            FROM sync_outbox WHERE profile_id=? AND ack_at IS NULL ORDER BY created_at,id LIMIT ?""",
            arrayOf(profileId.toString(),limit.toString())).use{c->buildList{
                while(c.moveToNext())add(PendingSyncEvent(c.getString(0),c.getLong(1),c.getString(2),
                    c.getString(3),c.getString(4),c.getString(5),c.getLong(6)))
            }}
    }
    fun markAcknowledged(profileId:Long,eventIds:Collection<String>) {
        require(eventIds.size<=100){"Batch eccessivo"}
        if(eventIds.isEmpty())return
        transaction{db->
            for(id in eventIds){
                require(db.update("sync_outbox",ContentValues().apply{put("ack_at",System.currentTimeMillis())},
                    "id=? AND profile_id=? AND ack_at IS NULL",arrayOf(id,profileId.toString()))==1) {
                    "Evento sconosciuto, già confermato o di un altro profilo"
                }
            }
        }
    }
    fun profileForCenter(centerId:Long):Long=stock.readableDatabase.rawQuery(
        "SELECT profile_id FROM org_centers WHERE id=?",arrayOf(centerId.toString())
    ).use{c->require(c.moveToFirst()){"Centro di costo non trovato"};c.getLong(0)}
    fun profileForSupplier(supplierId:Long):Long=stock.readableDatabase.rawQuery(
        "SELECT profile_id FROM org_suppliers WHERE id=?",arrayOf(supplierId.toString())
    ).use{c->require(c.moveToFirst()){"Fornitore non trovato"};c.getLong(0)}
}

