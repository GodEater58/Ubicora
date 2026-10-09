package com.officinastock.app

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase

/** Workflows locali di magazzino. La conferma non e' un'autorizzazione multiutente remota. */
data class PurchaseRequest(
    val id:Long, val warehouseId:Long, val itemId:Long, val itemName:String,
    val quantity:Double, val received:Double, val unitPrice:Double?,
    val supplier:String, val owner:String, val status:String, val createdAt:Long,
    val centerId:Long?=null, val supplierId:Long?=null
)
data class StockCount(
    val id:Long, val warehouseId:Long, val itemId:Long, val itemName:String,
    val expected:Double, val counted:Double, val reason:String,
    val status:String, val createdAt:Long
)
data class StockTransfer(
    val id:Long, val fromId:Long, val toId:Long, val itemId:Long,
    val itemName:String, val quantity:Double, val status:String, val createdAt:Long
)
data class MaintenanceWork(
    val id:Long, val warehouseId:Long, val machine:String, val description:String,
    val itemId:Long?, val quantity:Double, val status:String, val createdAt:Long,
    val assetId:Long?=null,val laborHours:Double=0.0,val hourlyRate:Double?=null,
    val partsCost:Double?=null,val completedAt:Long?=null
)
data class OperationsEvent(
    val id:Long, val action:String, val actor:String, val detail:String, val at:Long
)

/** Uses the existing SQLite database: no remote account, no fake cloud sync. */
class OperationsDb(private val stock:StockDb) {
    companion object {
        fun createTables(db:SQLiteDatabase) {
            db.execSQL("""CREATE TABLE IF NOT EXISTS ops_orders (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                warehouse_id INTEGER NOT NULL REFERENCES warehouses(id) ON DELETE CASCADE,
                item_id INTEGER NOT NULL REFERENCES items(id) ON DELETE CASCADE,
                quantity REAL NOT NULL CHECK(quantity > 0),
                received REAL NOT NULL DEFAULT 0 CHECK(received >= 0),
                unit_price REAL DEFAULT NULL CHECK(unit_price >= 0),
                supplier TEXT NOT NULL DEFAULT '',
                owner TEXT NOT NULL DEFAULT '',
                status TEXT NOT NULL DEFAULT 'DRAFT'
                    CHECK(status IN ('DRAFT','APPROVED','ORDERED','RECEIVED','CANCELLED')),
                created_at INTEGER NOT NULL
            )""")
            db.execSQL("""CREATE TABLE IF NOT EXISTS ops_counts (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                warehouse_id INTEGER NOT NULL REFERENCES warehouses(id) ON DELETE CASCADE,
                item_id INTEGER NOT NULL REFERENCES items(id) ON DELETE CASCADE,
                expected REAL NOT NULL CHECK(expected >= 0),
                counted REAL NOT NULL CHECK(counted >= 0),
                reason TEXT NOT NULL,
                status TEXT NOT NULL DEFAULT 'PENDING' CHECK(status IN ('PENDING','APPLIED','CANCELLED')),
                created_at INTEGER NOT NULL
            )""")
            db.execSQL("""CREATE TABLE IF NOT EXISTS ops_transfers (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                from_id INTEGER NOT NULL REFERENCES warehouses(id) ON DELETE CASCADE,
                to_id INTEGER NOT NULL REFERENCES warehouses(id) ON DELETE CASCADE,
                item_id INTEGER NOT NULL REFERENCES items(id) ON DELETE CASCADE,
                quantity REAL NOT NULL CHECK(quantity > 0),
                status TEXT NOT NULL DEFAULT 'REQUESTED'
                    CHECK(status IN ('REQUESTED','IN_TRANSIT','RECEIVED','CANCELLED')),
                created_at INTEGER NOT NULL,
                CHECK(from_id <> to_id)
            )""")
            db.execSQL("""CREATE TABLE IF NOT EXISTS ops_maintenance (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                warehouse_id INTEGER NOT NULL REFERENCES warehouses(id) ON DELETE CASCADE,
                machine TEXT NOT NULL,
                description TEXT NOT NULL,
                item_id INTEGER REFERENCES items(id) ON DELETE SET NULL,
                quantity REAL NOT NULL DEFAULT 0 CHECK(quantity >= 0),
                status TEXT NOT NULL DEFAULT 'OPEN' CHECK(status IN ('OPEN','DONE','CANCELLED')),
                created_at INTEGER NOT NULL
            )""")
            db.execSQL("""CREATE TABLE IF NOT EXISTS ops_events (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                warehouse_id INTEGER NOT NULL REFERENCES warehouses(id) ON DELETE CASCADE,
                action TEXT NOT NULL, actor TEXT NOT NULL, detail TEXT NOT NULL,
                created_at INTEGER NOT NULL
            )""")
            db.execSQL("""CREATE TABLE IF NOT EXISTS ops_settings (
                profile_id INTEGER PRIMARY KEY NOT NULL REFERENCES profiles(id) ON DELETE CASCADE,
                business_mode INTEGER NOT NULL DEFAULT 0 CHECK(business_mode IN (0,1)),
                monthly_budget REAL DEFAULT NULL CHECK(monthly_budget >= 0)
            )""")
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_ops_orders_wh ON ops_orders(warehouse_id,status)")
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_ops_counts_wh ON ops_counts(warehouse_id,status)")
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_ops_transfers_wh ON ops_transfers(from_id,to_id,status)")
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_ops_maintenance_wh ON ops_maintenance(warehouse_id,status)")
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_ops_events_wh ON ops_events(warehouse_id,created_at DESC)")
        }
    }

    fun businessMode(profileId:Long):Boolean=stock.readableDatabase.rawQuery(
        "SELECT business_mode FROM ops_settings WHERE profile_id=?",arrayOf(profileId.toString())
    ).use{c->c.moveToFirst() && c.getInt(0)==1}
    fun setBusinessMode(profileId:Long,enabled:Boolean) {
        require(stock.profiles().any{it.id==profileId}){"Profilo non trovato"}
        stock.writableDatabase.insertWithOnConflict("ops_settings",null,ContentValues().apply {
            put("profile_id",profileId);put("business_mode",if(enabled)1 else 0)
            putNull("monthly_budget")
        },SQLiteDatabase.CONFLICT_IGNORE)
        stock.writableDatabase.update("ops_settings",ContentValues().apply {
            put("business_mode",if(enabled)1 else 0)
        },"profile_id=?",arrayOf(profileId.toString()))
    }
    fun monthlyBudget(profileId:Long):Double?=stock.readableDatabase.rawQuery(
        "SELECT monthly_budget FROM ops_settings WHERE profile_id=?",arrayOf(profileId.toString())
    ).use{c->if(!c.moveToFirst()||c.isNull(0))null else c.getDouble(0)}
    fun setMonthlyBudget(profileId:Long,budget:Double?) {
        require(budget==null || (budget.isFinite() && budget>=0.0 && budget<=1e12)){"Budget non valido"}
        require(stock.profiles().any{it.id==profileId}){"Profilo non trovato"}
        stock.writableDatabase.insertWithOnConflict("ops_settings",null,ContentValues().apply {
            put("profile_id",profileId);put("business_mode",if(businessMode(profileId))1 else 0)
        },SQLiteDatabase.CONFLICT_IGNORE)
        stock.writableDatabase.update("ops_settings",ContentValues().apply {
            if(budget==null)putNull("monthly_budget") else put("monthly_budget",budget)
        },"profile_id=?",arrayOf(profileId.toString()))
    }
    private fun requireItem(warehouseId:Long,itemId:Long):StockItem {
        val item=requireNotNull(stock.item(itemId)){"Articolo non trovato"}
        require(item.warehouseId==warehouseId){"Articolo di un altro magazzino"}
        return item
    }
    private fun audit(db:SQLiteDatabase,wid:Long,action:String,actor:String,detail:String) {
        db.insertOrThrow("ops_events",null,ContentValues().apply {
            put("warehouse_id",wid);put("action",action);put("actor",actor.trim().take(100).ifEmpty{"Operatore locale"})
            put("detail",detail.take(300));put("created_at",System.currentTimeMillis())
        })
    }
    private inline fun <T> transaction(block:(SQLiteDatabase)->T):T {
        val db=stock.writableDatabase
        db.beginTransaction()
        try {
            val result=block(db)
            db.setTransactionSuccessful()
            return result
        } finally {db.endTransaction()}
    }
    private fun changeStock(db:SQLiteDatabase,itemId:Long,delta:Double,note:String) {
        val old=db.rawQuery("SELECT quantity FROM items WHERE id=?",arrayOf(itemId.toString())).use {
            require(it.moveToFirst()){"Articolo mancante"};it.getDouble(0)
        }
        val next=StockRules.applyDelta(old,delta)
        StockPlanningDb.assertUnreservedStock(db,itemId,next)
        require(delta.isFinite() && delta!=0.0){"Movimento nullo"}
        require(db.update("items",ContentValues().apply{put("quantity",next)},"id=?",arrayOf(itemId.toString()))==1){"Movimento non applicato"}
        db.insertOrThrow("movements",null,ContentValues().apply {
            put("item_id",itemId);put("delta",delta);put("note",note);put("created_at",System.currentTimeMillis())
        })
    }

    fun createOrder(wid:Long,itemId:Long,quantity:Double,supplier:String,actor:String,
                    centerId:Long?=null,supplierId:Long?=null):Long {
        require(quantity.isFinite() && quantity>0.0 && quantity<=1e8){"Quantità ordine non valida"}
        require(actor.trim().length in 2..100){"Indica il responsabile"}
        val item=requireItem(wid,itemId)
        val directory=EnterpriseDb(stock)
        val profileId=stock.readableDatabase.rawQuery("SELECT profile_id FROM warehouses WHERE id=?",
            arrayOf(wid.toString())).use {c->require(c.moveToFirst());c.getLong(0)}
        require(centerId==null || directory.profileForCenter(centerId)==profileId){"Centro di costo di un altro profilo"}
        require(supplierId==null || directory.profileForSupplier(supplierId)==profileId){"Fornitore di un altro profilo"}
        val resolvedSupplier=supplierId?.let { sid ->
            directory.suppliers(profileId).firstOrNull{it.id==sid}?.name
                ?: error("Fornitore non trovato")
        } ?: supplier.trim().take(160).ifEmpty{item.supplier}
        return transaction { db ->
            val id=db.insertOrThrow("ops_orders",null,ContentValues().apply {
                put("warehouse_id",wid);put("item_id",itemId);put("quantity",quantity);put("received",0.0)
                if(item.unitCost!=null)put("unit_price",item.unitCost)
                put("supplier",resolvedSupplier)
                if(centerId!=null)put("center_id",centerId)
                if(supplierId!=null)put("supplier_id",supplierId)
                put("owner",actor.trim());put("status","DRAFT");put("created_at",System.currentTimeMillis())
            })
            audit(db,wid,"ORDINE_CREATO",actor,"Richiesta #$id · ${item.sku} · $quantity ${item.unit}")
            id
        }
    }
    fun advanceOrder(wid:Long,id:Long,action:String,actor:String) = transaction { db ->
        val order=db.rawQuery("SELECT item_id,quantity,received,status FROM ops_orders WHERE id=? AND warehouse_id=?",
            arrayOf(id.toString(),wid.toString())).use {c ->
            require(c.moveToFirst()){"Ordine inesistente"};listOf(c.getLong(0).toString(),c.getDouble(1).toString(),c.getDouble(2).toString(),c.getString(3))
        }
        val current=order[3]
        val next=when(action) {
            "APPROVE" -> {require(current=="DRAFT"){"Ordine non approvabile"};"APPROVED"}
            "PLACE" -> {require(current=="APPROVED"){"Prima approva la richiesta"};"ORDERED"}
            "RECEIVE" -> {require(current=="ORDERED"){"Ordine non ancora inviato"};"RECEIVED"}
            "CANCEL" -> {require(current in listOf("DRAFT","APPROVED","ORDERED")){"Ordine non annullabile"};"CANCELLED"}
            else -> throw IllegalArgumentException("Azione non riconosciuta")
        }
        if(action=="RECEIVE") {
            val qty=order[1].toDouble()-order[2].toDouble()
            require(qty>0 && qty.isFinite()){"Quantità già ricevuta"}
            changeStock(db,order[0].toLong(),qty,"Ricezione ordine #$id")
        }
        val values=ContentValues().apply {
            put("status",next)
            if(action=="RECEIVE")put("received",order[1].toDouble())
        }
        require(db.update("ops_orders",values,"id=? AND warehouse_id=? AND status=?",
            arrayOf(id.toString(),wid.toString(),current))==1){"Stato ordine cambiato"}
        audit(db,wid,"ORDINE_$next",actor,"Ordine #$id")
    }
    /** Partial receipt. Stock changes only by delivered quantity, atomically with order status. */
    fun receiveOrderQuantity(wid:Long,id:Long,delivered:Double,actor:String) = transaction { db ->
        require(delivered.isFinite() && delivered>0.0){"Quantità ricevuta non valida"}
        val order=db.rawQuery("SELECT item_id,quantity,received,status FROM ops_orders WHERE id=? AND warehouse_id=?",
            arrayOf(id.toString(),wid.toString())).use {c->
            require(c.moveToFirst()){"Ordine non trovato"}
            listOf(c.getLong(0).toString(),c.getDouble(1).toString(),c.getDouble(2).toString(),c.getString(3))
        }
        require(order[3]=="ORDERED"){"Ordine non in consegna"}
        val total=order[1].toDouble();val received=order[2].toDouble()
        require(delivered<=total-received){"Quantità superiore al residuo dell'ordine"}
        val nextReceived=received+delivered
        require(nextReceived.isFinite() && nextReceived<=total){"Quantità ricevuta non valida"}
        changeStock(db,order[0].toLong(),delivered,"Ricezione parziale ordine #$id")
        val next=if(nextReceived==total)"RECEIVED" else "ORDERED"
        require(db.update("ops_orders",ContentValues().apply{
            put("received",nextReceived);put("status",next)
        },"id=? AND warehouse_id=? AND status='ORDERED' AND received=?",
            arrayOf(id.toString(),wid.toString(),received.toString()))==1){"Ordine modificato: ricarica i dati"}
        audit(db,wid,"ORDINE_RICEZIONE",actor,
            "Ordine #$id · ricevuti $delivered · totale $nextReceived / $total")
    }
    fun orders(wid:Long):List<PurchaseRequest> = stock.readableDatabase.rawQuery(
        """SELECT o.id,o.warehouse_id,o.item_id,i.name,o.quantity,o.received,o.unit_price,o.supplier,o.owner,o.status,o.created_at,o.center_id,o.supplier_id
            FROM ops_orders o JOIN items i ON i.id=o.item_id
            WHERE o.warehouse_id=? ORDER BY o.created_at DESC,o.id DESC LIMIT 250""",arrayOf(wid.toString())
    ).use { c -> buildList {while(c.moveToNext()) add(PurchaseRequest(c.getLong(0),c.getLong(1),c.getLong(2),
        c.getString(3),c.getDouble(4),c.getDouble(5),if(c.isNull(6))null else c.getDouble(6),c.getString(7),
        c.getString(8),c.getString(9),c.getLong(10),
        if(c.isNull(11))null else c.getLong(11),if(c.isNull(12))null else c.getLong(12))) } }

    fun requestCount(wid:Long,itemId:Long,counted:Double,reason:String,actor:String):Long {
        val item=requireItem(wid,itemId)
        require(StockRules.validQuantity(counted) && counted<=1e9){"Conteggio non valido"}
        require(reason.trim().length in 3..250){"Indica il motivo del conteggio"}
        return transaction { db ->
            val id=db.insertOrThrow("ops_counts",null,ContentValues().apply {
                put("warehouse_id",wid);put("item_id",itemId);put("expected",item.quantity)
                put("counted",counted);put("reason",reason.trim());put("status","PENDING")
                put("created_at",System.currentTimeMillis())
            })
            audit(db,wid,"CONTEGGIO_PROPOSTO",actor,"Conteggio #$id · ${item.sku}")
            id
        }
    }
    fun finishCount(wid:Long,id:Long,approve:Boolean,actor:String) = transaction { db ->
        val count=db.rawQuery("SELECT item_id,expected,counted,reason,status FROM ops_counts WHERE id=? AND warehouse_id=?",
            arrayOf(id.toString(),wid.toString())).use { c ->
            require(c.moveToFirst()){"Conteggio non trovato"}
            listOf(c.getLong(0).toString(),c.getDouble(1).toString(),c.getDouble(2).toString(),c.getString(3),c.getString(4))
        }
        require(count[4]=="PENDING"){"Conteggio già gestito"}
        if(approve){
            val item=requireItem(wid,count[0].toLong())
            require(item.quantity==count[1].toDouble()){"Giacenza cambiata dopo il conteggio; ripeti l'inventario"}
            val delta=count[2].toDouble()-item.quantity
            if(delta!=0.0)changeStock(db,item.id,delta,"Inventario ciclico #$id · ${count[3]}")
        }
        val status=if(approve)"APPLIED" else "CANCELLED"
        require(db.update("ops_counts",ContentValues().apply{put("status",status)},"id=? AND status='PENDING'",
            arrayOf(id.toString()))==1){"Conteggio già gestito"}
        audit(db,wid,"CONTEGGIO_$status",actor,"Conteggio #$id")
    }
    fun counts(wid:Long):List<StockCount> = stock.readableDatabase.rawQuery(
        """SELECT c.id,c.warehouse_id,c.item_id,i.name,c.expected,c.counted,c.reason,c.status,c.created_at
            FROM ops_counts c JOIN items i ON i.id=c.item_id WHERE c.warehouse_id=?
            ORDER BY c.created_at DESC,c.id DESC LIMIT 250""",arrayOf(wid.toString())
    ).use {c -> buildList {while(c.moveToNext())add(StockCount(c.getLong(0),c.getLong(1),c.getLong(2),
        c.getString(3),c.getDouble(4),c.getDouble(5),c.getString(6),c.getString(7),c.getLong(8)))}}

    private fun ensureTransferWarehouses(from:Long,to:Long) {
        val source=stock.readableDatabase.rawQuery("SELECT profile_id FROM warehouses WHERE id=?",arrayOf(from.toString())).use {
            require(it.moveToFirst()){"Magazzino origine inesistente"};it.getLong(0)
        }
        val target=stock.readableDatabase.rawQuery("SELECT profile_id FROM warehouses WHERE id=?",arrayOf(to.toString())).use {
            require(it.moveToFirst()){"Magazzino destinazione inesistente"};it.getLong(0)
        }
        require(from!=to && source==target){"Il trasferimento richiede due magazzini dello stesso profilo"}
    }
    fun requestTransfer(from:Long,to:Long,itemId:Long,amount:Double,actor:String):Long {
        ensureTransferWarehouses(from,to)
        val item=requireItem(from,itemId)
        require(amount.isFinite() && amount>0.0 && amount<=item.quantity){"Giacenza insufficiente per il trasferimento"}
        return transaction {db->
            val id=db.insertOrThrow("ops_transfers",null,ContentValues().apply {
                put("from_id",from);put("to_id",to);put("item_id",itemId)
                put("quantity",amount);put("status","REQUESTED");put("created_at",System.currentTimeMillis())
            })
            audit(db,from,"TRASFERIMENTO_RICHIESTO",actor,"Trasferimento #$id · ${item.sku} → magazzino $to")
            id
        }
    }
    fun progressTransfer(id:Long,actor:String,action:String) = transaction {db->
        val fields=db.rawQuery("SELECT from_id,to_id,item_id,quantity,status FROM ops_transfers WHERE id=?",
            arrayOf(id.toString())).use{c->
            require(c.moveToFirst()){"Trasferimento non trovato"}
            listOf(c.getLong(0).toString(),c.getLong(1).toString(),c.getLong(2).toString(),c.getDouble(3).toString(),c.getString(4))
        }
        val from=fields[0].toLong();val to=fields[1].toLong();val itemId=fields[2].toLong()
        val qty=fields[3].toDouble();val current=fields[4]
        ensureTransferWarehouses(from,to)
        val original=requireItem(from,itemId)
        val next=when(action) {
            "SHIP" -> {require(current=="REQUESTED"){"Trasferimento già avviato"};changeStock(db,itemId,-qty,"Spedizione trasferimento #$id");"IN_TRANSIT"}
            "RECEIVE" -> {
                require(current=="IN_TRANSIT"){"Trasferimento non ancora spedito"}
                val existing=db.rawQuery("SELECT id,name,category,unit FROM items WHERE warehouse_id=? AND sku=?",
                    arrayOf(to.toString(),original.sku)).use { c ->
                    if(!c.moveToFirst()) null else {
                        // SKU alone is not sufficient if legacy CSVs reused codes in different warehouses.
                        // Never silently mix incompatible products or measurement units.
                        require(c.getString(1).trim().equals(original.name.trim(),ignoreCase=true) &&
                            c.getString(2).trim().equals(original.category.trim(),ignoreCase=true) &&
                            c.getString(3).trim().equals(original.unit.trim(),ignoreCase=true)) {
                            "SKU presente in destinazione ma riferito a un articolo diverso. Correggi i codici prima della ricezione."
                        }
                        c.getLong(0)
                    }
                }
                val destination=existing ?: db.insertOrThrow("items",null,ContentValues().apply {
                    put("warehouse_id",to);put("sku",original.sku);put("name",original.name)
                    put("category",original.category);put("quantity",0.0);put("minimum",original.minimum)
                    put("unit",original.unit);put("note",original.note);put("supplier",original.supplier)
                    if(original.unitCost!=null)put("unit_cost",original.unitCost)
                    if(original.leadDays!=null)put("lead_days",original.leadDays)
                })
                changeStock(db,destination,qty,"Ricezione trasferimento #$id")
                "RECEIVED"
            }
            "CANCEL" -> {
                require(current in listOf("REQUESTED","IN_TRANSIT")){"Trasferimento non annullabile"}
                if(current=="IN_TRANSIT")changeStock(db,itemId,qty,"Rientro trasferimento annullato #$id")
                "CANCELLED"
            }
            else -> throw IllegalArgumentException("Azione trasferimento non valida")
        }
        require(db.update("ops_transfers",ContentValues().apply{put("status",next)},"id=? AND status=?",
            arrayOf(id.toString(),current))==1){"Stato trasferimento modificato"}
        audit(db,from,"TRASFERIMENTO_$next",actor,"Trasferimento #$id")
        audit(db,to,"TRASFERIMENTO_$next",actor,"Trasferimento #$id")
    }
    fun transfers(wid:Long):List<StockTransfer> = stock.readableDatabase.rawQuery(
        """SELECT t.id,t.from_id,t.to_id,t.item_id,i.name,t.quantity,t.status,t.created_at
            FROM ops_transfers t JOIN items i ON i.id=t.item_id
            WHERE t.from_id=? OR t.to_id=? ORDER BY t.created_at DESC,t.id DESC LIMIT 250""",
        arrayOf(wid.toString(),wid.toString())
    ).use{c->buildList{while(c.moveToNext())add(StockTransfer(c.getLong(0),c.getLong(1),
        c.getLong(2),c.getLong(3),c.getString(4),c.getDouble(5),c.getString(6),c.getLong(7)))}}

    fun createMaintenance(wid:Long,machine:String,description:String,itemId:Long?,qty:Double,actor:String,
                          assetId:Long?=null,laborHours:Double=0.0,hourlyRate:Double?=null):Long {
        require(machine.trim().length in 2..120){"Indica macchina o impianto"}
        require(description.trim().length in 3..400){"Descrivi l'intervento"}
        require(StockRules.validQuantity(qty) && qty<=1e8){"Quantità ricambio non valida"}
        require(itemId!=null || qty==0.0){"Seleziona il ricambio"}
        if(itemId!=null)requireItem(wid,itemId)
        require(laborHours.isFinite() && laborHours>=0 && laborHours<=10000){"Ore lavorate non valide"}
        require(hourlyRate==null || (hourlyRate.isFinite() && hourlyRate>=0 && hourlyRate<=1e7)){"Costo orario non valido"}
        if(assetId!=null) {
            val valid=stock.readableDatabase.rawQuery(
                "SELECT 1 FROM org_assets WHERE id=? AND warehouse_id=?",
                arrayOf(assetId.toString(),wid.toString())).use{it.moveToFirst()}
            require(valid){"Macchina non appartenente al magazzino selezionato"}
        }
        return transaction {db->
            val id=db.insertOrThrow("ops_maintenance",null,ContentValues().apply {
                put("warehouse_id",wid);put("machine",machine.trim());put("description",description.trim())
                if(itemId!=null)put("item_id",itemId);put("quantity",qty)
                if(assetId!=null)put("asset_id",assetId)
                put("labor_hours",laborHours);if(hourlyRate!=null)put("hourly_rate",hourlyRate)
                put("status","OPEN");put("created_at",System.currentTimeMillis())
            })
            audit(db,wid,"INTERVENTO_CREATO",actor,"Intervento #$id · ${machine.trim()}")
            id
        }
    }
    fun finishMaintenance(wid:Long,id:Long,complete:Boolean,actor:String) = transaction {db->
        val job=db.rawQuery("""SELECT item_id,quantity,status,labor_hours,hourly_rate
            FROM ops_maintenance WHERE id=? AND warehouse_id=?""",
            arrayOf(id.toString(),wid.toString())).use{c->
            require(c.moveToFirst()){"Intervento inesistente"}
            listOf(if(c.isNull(0))null else c.getLong(0),c.getDouble(1),c.getString(2),
                c.getDouble(3),if(c.isNull(4))null else c.getDouble(4))
        }
        require(job[2]=="OPEN"){"Intervento già gestito"}
        val partId=job[0] as Long?
        val quantity=job[1] as Double
        var partsCost:Double?=if(partId==null || quantity==0.0)0.0 else null
        if(complete && partId!=null && quantity>0) {
            val material=requireItem(wid,partId)
            partsCost=material.unitCost?.let{cost ->
                (cost*quantity).takeIf{it.isFinite()}
            }
            changeStock(db,partId,-quantity,"Ricambio per manutenzione #$id")
        }
        val status=if(complete)"DONE" else "CANCELLED"
        require(db.update("ops_maintenance",ContentValues().apply{
            put("status",status)
            if(complete && partsCost!=null)put("parts_cost",partsCost)
            if(complete)put("completed_at",System.currentTimeMillis())
        },"id=? AND warehouse_id=? AND status='OPEN'",arrayOf(id.toString(),wid.toString()))==1) {
            "Intervento già gestito"
        }
        audit(db,wid,"INTERVENTO_$status",actor,"Intervento #$id")
    }
    fun maintenance(wid:Long):List<MaintenanceWork> = stock.readableDatabase.rawQuery(
        """SELECT id,warehouse_id,machine,description,item_id,quantity,status,created_at,
                asset_id,labor_hours,hourly_rate,parts_cost,completed_at
            FROM ops_maintenance WHERE warehouse_id=? ORDER BY created_at DESC,id DESC LIMIT 250""",
        arrayOf(wid.toString())
    ).use{c->buildList{while(c.moveToNext())add(MaintenanceWork(c.getLong(0),c.getLong(1),
        c.getString(2),c.getString(3),if(c.isNull(4))null else c.getLong(4),c.getDouble(5),
        c.getString(6),c.getLong(7),if(c.isNull(8))null else c.getLong(8),
        c.getDouble(9),if(c.isNull(10))null else c.getDouble(10),
        if(c.isNull(11))null else c.getDouble(11),if(c.isNull(12))null else c.getLong(12)))}}
    fun events(wid:Long):List<OperationsEvent> = stock.readableDatabase.rawQuery(
        """SELECT id,action,actor,detail,created_at FROM ops_events WHERE warehouse_id=?
           ORDER BY created_at DESC,id DESC LIMIT 80""",arrayOf(wid.toString())
    ).use{c->buildList{while(c.moveToNext())add(OperationsEvent(c.getLong(0),c.getString(1),
        c.getString(2),c.getString(3),c.getLong(4)))}}
}
