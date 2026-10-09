package com.officinastock.app

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase

data class StockReservation(val id:Long,val warehouseId:Long,val itemId:Long,
    val quantity:Double,val reference:String,val requestedBy:String,val status:String,val createdAt:Long)
data class StockAvailability(val physical:Double,val reserved:Double) {
    val available:Double get()=StockRules.difference(physical,reserved).coerceAtLeast(0.0)
}

/** Stock allocation is separate from physical inventory until consumed. All writes are atomic. */
class StockPlanningDb(private val stock:StockDb) {
    companion object {
        fun createTables(db:SQLiteDatabase){
            db.execSQL("""CREATE TABLE IF NOT EXISTS stock_reservations(
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                warehouse_id INTEGER NOT NULL REFERENCES warehouses(id) ON DELETE CASCADE,
                item_id INTEGER NOT NULL REFERENCES items(id) ON DELETE CASCADE,
                quantity REAL NOT NULL CHECK(quantity>0 AND quantity<=100000000),
                reference TEXT NOT NULL,
                requested_by TEXT NOT NULL,
                status TEXT NOT NULL DEFAULT 'ACTIVE'
                    CHECK(status IN ('ACTIVE','CONSUMED','RELEASED')),
                created_at INTEGER NOT NULL,
                closed_at INTEGER DEFAULT NULL
            )""")
            // Maintenance cost columns exist in v9 fresh installs; upgrades add them via StockDb.onUpgrade.
            // ops_maintenance is already present when this function is called.
            val maintenanceColumns=db.rawQuery("PRAGMA table_info(ops_maintenance)",null).use{c->
                buildSet { while(c.moveToNext()) add(c.getString(1)) }
            }
            for((column,definition) in listOf(
                "labor_hours" to "REAL NOT NULL DEFAULT 0",
                "hourly_rate" to "REAL DEFAULT NULL",
                "parts_cost" to "REAL DEFAULT NULL",
                "completed_at" to "INTEGER DEFAULT NULL")) {
                if(column !in maintenanceColumns) db.execSQL(
                    "ALTER TABLE ops_maintenance ADD COLUMN $column $definition")
            }
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_reservations_item_state ON stock_reservations(item_id,status)")
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_reservations_warehouse_state ON stock_reservations(warehouse_id,status)")
        }
        /** Must be queried on the SAME SQLite connection as the transaction changing stock. */
        fun reserved(db:SQLiteDatabase,itemId:Long):Double=db.rawQuery(
            "SELECT COALESCE(SUM(quantity),0) FROM stock_reservations WHERE item_id=? AND status='ACTIVE'",
            arrayOf(itemId.toString())).use {c->require(c.moveToFirst());c.getDouble(0)}
        fun assertUnreservedStock(db:SQLiteDatabase,itemId:Long,newQuantity:Double) {
            require(newQuantity.isFinite() && newQuantity>=reserved(db,itemId)-0.0000001) {
                "Scorta riservata per attività aperte: libera o consuma prima le prenotazioni."
            }
        }
    }
    private inline fun <T> transaction(block:(SQLiteDatabase)->T):T {
        val db=stock.writableDatabase
        db.beginTransaction()
        try {val result=block(db);db.setTransactionSuccessful();return result}
        finally {db.endTransaction()}
    }
    fun availability(itemId:Long):StockAvailability {
        val db=stock.readableDatabase
        return db.rawQuery("SELECT quantity FROM items WHERE id=?",arrayOf(itemId.toString())).use{c->
            require(c.moveToFirst()){"Articolo inesistente"}
            StockAvailability(c.getDouble(0),reserved(db,itemId))
        }
    }
    fun reservations(warehouseId:Long,limit:Int=250):List<StockReservation> = stock.readableDatabase.rawQuery(
        """SELECT r.id,r.warehouse_id,r.item_id,r.quantity,r.reference,r.requested_by,r.status,r.created_at
           FROM stock_reservations r WHERE r.warehouse_id=?
           ORDER BY CASE r.status WHEN 'ACTIVE' THEN 0 ELSE 1 END,r.created_at DESC,r.id DESC LIMIT ?""",
        arrayOf(warehouseId.toString(),limit.coerceIn(1,1000000).toString())).use {c->buildList{
            while(c.moveToNext())add(StockReservation(c.getLong(0),c.getLong(1),c.getLong(2),
                c.getDouble(3),c.getString(4),c.getString(5),c.getString(6),c.getLong(7)))
        }}
    fun reservedByItem(warehouseId:Long):Map<Long,Double> = stock.readableDatabase.rawQuery(
        "SELECT item_id,SUM(quantity) FROM stock_reservations WHERE warehouse_id=? AND status='ACTIVE' GROUP BY item_id",
        arrayOf(warehouseId.toString())).use {c->buildMap{while(c.moveToNext())put(c.getLong(0),c.getDouble(1))}}
    fun reserve(warehouseId:Long,itemId:Long,qty:Double,reference:String,actor:String):Long {
        require(qty.isFinite() && qty>0 && qty<=100000000){"Quantità non valida"}
        require(reference.trim().length in 3..160 && actor.trim().length in 2..100){"Indica attività e responsabile"}
        return transaction {db->
            val current=db.rawQuery("SELECT quantity,warehouse_id FROM items WHERE id=?",
                arrayOf(itemId.toString())).use{c->
                require(c.moveToFirst()){"Articolo inesistente"}
                require(c.getLong(1)==warehouseId){"Articolo in un altro magazzino"}
                c.getDouble(0)
            }
            require(qty<=current-reserved(db,itemId)+0.0000001){"Quantità disponibile insufficiente"}
            db.insertOrThrow("stock_reservations",null,ContentValues().apply{
                put("warehouse_id",warehouseId);put("item_id",itemId);put("quantity",qty)
                put("reference",reference.trim());put("requested_by",actor.trim())
                put("status","ACTIVE");put("created_at",System.currentTimeMillis())
            })
        }
    }
    fun close(warehouseId:Long,reservationId:Long,consume:Boolean,actor:String) {
        require(actor.trim().length in 2..100){"Responsabile non valido"}
        transaction {db->
            val reservation=db.rawQuery(
                "SELECT item_id,quantity,reference FROM stock_reservations WHERE id=? AND warehouse_id=? AND status='ACTIVE'",
                arrayOf(reservationId.toString(),warehouseId.toString())).use{c->
                require(c.moveToFirst()){"Prenotazione non aperta nel magazzino selezionato"}
                Triple(c.getLong(0),c.getDouble(1),c.getString(2))
            }
            require(db.update("stock_reservations",ContentValues().apply {
                put("status",if(consume)"CONSUMED" else "RELEASED")
                put("closed_at",System.currentTimeMillis())
            },"id=? AND warehouse_id=? AND status='ACTIVE'",
                arrayOf(reservationId.toString(),warehouseId.toString()))==1){"Prenotazione già chiusa"}
            if(consume){
                val current=db.rawQuery("SELECT quantity FROM items WHERE id=?",
                    arrayOf(reservation.first.toString())).use{c->
                    require(c.moveToFirst()){"Articolo mancante"}
                    c.getDouble(0)
                }
                val next=StockRules.applyDelta(current,-reservation.second)
                assertUnreservedStock(db,reservation.first,next)
                StockTraceDb.assertPhysicalFloor(db,reservation.first,next)
                require(db.update("items",ContentValues().apply{put("quantity",next)},
                    "id=?",arrayOf(reservation.first.toString()))==1){"Scarico non applicato"}
                db.insertOrThrow("movements",null,ContentValues().apply{
                    put("item_id",reservation.first);put("delta",-reservation.second)
                    put("note","Consumo prenotazione #$reservationId · ${reservation.third} · ${actor.trim()}")
                    put("created_at",System.currentTimeMillis())
                })
            }
            db.insertOrThrow("ops_events",null,ContentValues().apply{
                put("warehouse_id",warehouseId)
                put("action",if(consume)"PRENOTAZIONE_CONSUMATA" else "PRENOTAZIONE_RILASCIATA")
                put("actor",actor.trim())
                put("detail","Prenotazione #$reservationId · ${reservation.third}")
                put("created_at",System.currentTimeMillis())
            })
        }
    }
}


