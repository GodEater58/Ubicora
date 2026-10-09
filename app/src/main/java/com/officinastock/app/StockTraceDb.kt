package com.officinastock.app

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase

data class StockLot(val id:Long,val itemId:Long,val warehouseId:Long,val code:String,
    val quantity:Double,val expiresAt:Long?,val createdAt:Long)
data class StockSerial(val id:Long,val itemId:Long,val warehouseId:Long,val code:String,
    val state:String,val createdAt:Long,val issuedAt:Long?)

/** Optional traceability for existing physical stock: registrations never create inventory. */
class StockTraceDb(private val stock:StockDb) {
    companion object {
        fun createTables(db:SQLiteDatabase) {
            db.execSQL("""CREATE TABLE IF NOT EXISTS stock_lots(
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                warehouse_id INTEGER NOT NULL REFERENCES warehouses(id) ON DELETE CASCADE,
                item_id INTEGER NOT NULL REFERENCES items(id) ON DELETE CASCADE,
                lot_code TEXT NOT NULL, quantity REAL NOT NULL CHECK(quantity>=0),
                expires_at INTEGER DEFAULT NULL,
                created_at INTEGER NOT NULL,
                UNIQUE(item_id,lot_code)
            )""")
            db.execSQL("""CREATE TABLE IF NOT EXISTS stock_serials(
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                warehouse_id INTEGER NOT NULL REFERENCES warehouses(id) ON DELETE CASCADE,
                item_id INTEGER NOT NULL REFERENCES items(id) ON DELETE CASCADE,
                serial_code TEXT NOT NULL,
                state TEXT NOT NULL DEFAULT 'IN_STOCK' CHECK(state IN ('IN_STOCK','ISSUED')),
                created_at INTEGER NOT NULL, issued_at INTEGER DEFAULT NULL,
                UNIQUE(item_id,serial_code)
            )""")
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_stock_lots_item ON stock_lots(item_id)")
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_stock_serials_item_state ON stock_serials(item_id,state)")
        }
        private fun storedLotQty(db:SQLiteDatabase,itemId:Long):Double=db.rawQuery(
            "SELECT COALESCE(SUM(quantity),0) FROM stock_lots WHERE item_id=?",
            arrayOf(itemId.toString())).use {c->c.moveToFirst();c.getDouble(0)}
        private fun storedSerialQty(db:SQLiteDatabase,itemId:Long):Double=db.rawQuery(
            "SELECT COUNT(*) FROM stock_serials WHERE item_id=? AND state='IN_STOCK'",
            arrayOf(itemId.toString())).use{c->c.moveToFirst();c.getDouble(0)}
        fun trackedStock(db:SQLiteDatabase,itemId:Long):Double {
            val lot=storedLotQty(db,itemId);val serial=storedSerialQty(db,itemId)
            require(!(lot>0 && serial>0)){"Tracciabilità mista non consentita sullo stesso articolo"}
            return lot+serial
        }
        fun assertPhysicalFloor(db:SQLiteDatabase,itemId:Long,nextPhysical:Double) {
            require(nextPhysical.isFinite() && nextPhysical+0.0000001>=trackedStock(db,itemId)){
                "Materiale tracciato: registra lo scarico del lotto o del seriale prima del prelievo."
            }
        }
    }
    private inline fun <T> transaction(block:(SQLiteDatabase)->T):T {
        val db=stock.writableDatabase
        db.beginTransaction()
        try{val result=block(db);db.setTransactionSuccessful();return result}
        finally{db.endTransaction()}
    }
    private fun stockItem(db:SQLiteDatabase,itemId:Long,warehouseId:Long):Pair<Double,String> =
        db.rawQuery("SELECT quantity,unit FROM items WHERE id=? AND warehouse_id=?",
            arrayOf(itemId.toString(),warehouseId.toString())).use{c->
            require(c.moveToFirst()){"Articolo non trovato nel magazzino"}
            c.getDouble(0) to c.getString(1)
        }
    fun lots(warehouseId:Long,limit:Int=250):List<StockLot> = stock.readableDatabase.rawQuery(
        """SELECT id,item_id,warehouse_id,lot_code,quantity,expires_at,created_at
           FROM stock_lots WHERE warehouse_id=? ORDER BY
           CASE WHEN expires_at IS NULL THEN 1 ELSE 0 END,expires_at,id LIMIT ?""",
        arrayOf(warehouseId.toString(),limit.coerceIn(1,1000000).toString())).use{c->buildList {
            while(c.moveToNext())add(StockLot(c.getLong(0),c.getLong(1),c.getLong(2),c.getString(3),
                c.getDouble(4),if(c.isNull(5))null else c.getLong(5),c.getLong(6)))
        }}
    fun serials(warehouseId:Long,limit:Int=250):List<StockSerial> = stock.readableDatabase.rawQuery(
        """SELECT id,item_id,warehouse_id,serial_code,state,created_at,issued_at
           FROM stock_serials WHERE warehouse_id=? ORDER BY CASE state WHEN 'IN_STOCK' THEN 0 ELSE 1 END,id DESC LIMIT ?""",
        arrayOf(warehouseId.toString(),limit.coerceIn(1,1000000).toString())).use{c->buildList {
            while(c.moveToNext())add(StockSerial(c.getLong(0),c.getLong(1),c.getLong(2),c.getString(3),
                c.getString(4),c.getLong(5),if(c.isNull(6))null else c.getLong(6)))
        }}
    fun registerLot(wid:Long,itemId:Long,code:String,amount:Double,expiry:Long?):Long {
        require(code.trim().length in 2..100 && !code.contains('\n')){"Codice lotto non valido"}
        require(amount.isFinite() && amount>0 && amount<=100000000){"Quantità lotto non valida"}
        require(expiry==null || expiry>0L){"Scadenza non valida"}
        return transaction{db->
            val physical=stockItem(db,itemId,wid).first
            require(db.rawQuery("SELECT 1 FROM stock_serials WHERE item_id=? LIMIT 1",
                arrayOf(itemId.toString())).use{!it.moveToFirst()}) {
                "Articolo gestito per seriali: non è possibile aggiungere lotti."
            }
            require(storedLotQty(db,itemId)+amount<=physical+0.0000001){
                "La quantità tracciata supererebbe la giacenza fisica."
            }
            db.insertOrThrow("stock_lots",null,ContentValues().apply{
                put("warehouse_id",wid);put("item_id",itemId);put("lot_code",code.trim())
                put("quantity",amount);if(expiry!=null)put("expires_at",expiry)
                put("created_at",System.currentTimeMillis())
            })
        }
    }
    fun registerSerial(wid:Long,itemId:Long,serial:String):Long {
        require(serial.trim().length in 3..100 && !serial.contains('\n')){"Seriale non valido"}
        return transaction{db->
            val (physical,unit)=stockItem(db,itemId,wid)
            require(unit.equals("pz",true)){"Seriali disponibili solo per unità 'pz'"}
            require(db.rawQuery("SELECT 1 FROM stock_lots WHERE item_id=? LIMIT 1",
                arrayOf(itemId.toString())).use{!it.moveToFirst()}) {
                "Articolo gestito per lotti: non è possibile aggiungere seriali."
            }
            require(storedSerialQty(db,itemId)+1<=physical+0.0000001){
                "Seriali superiori alle unità fisiche disponibili."
            }
            db.insertOrThrow("stock_serials",null,ContentValues().apply{
                put("warehouse_id",wid);put("item_id",itemId);put("serial_code",serial.trim())
                put("state","IN_STOCK");put("created_at",System.currentTimeMillis())
            })
        }
    }
    private fun adjustStock(db:SQLiteDatabase,itemId:Long,delta:Double,note:String){
        val physical=db.rawQuery("SELECT quantity FROM items WHERE id=?",
            arrayOf(itemId.toString())).use{c->require(c.moveToFirst());c.getDouble(0)}
        val next=StockRules.applyDelta(physical,delta)
        StockPlanningDb.assertUnreservedStock(db,itemId,next)
        assertPhysicalFloor(db,itemId,next)
        require(db.update("items",ContentValues().apply{put("quantity",next)},
            "id=?",arrayOf(itemId.toString()))==1){"Articolo non aggiornabile"}
        db.insertOrThrow("movements",null,ContentValues().apply{
            put("item_id",itemId);put("delta",delta);put("note",note)
            put("created_at",System.currentTimeMillis())
        })
    }
    fun consumeLot(wid:Long,lotId:Long,amount:Double,actor:String,discard:Boolean=false){
        require(amount.isFinite() && amount>0 && actor.trim().length in 2..100){"Quantità o operatore non validi"}
        transaction{db->
            val (row,expiresAt)=db.rawQuery("""SELECT item_id,quantity,lot_code,expires_at FROM stock_lots
                WHERE id=? AND warehouse_id=?""",arrayOf(lotId.toString(),wid.toString())).use{c->
                require(c.moveToFirst()){"Lotto non trovato"}
                Triple(c.getLong(0),c.getDouble(1),c.getString(2)) to
                    (if(c.isNull(3))null else c.getLong(3))
            }
            val startOfToday=java.time.LocalDate.now(java.time.ZoneOffset.UTC)
                .atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli()
            require(discard || expiresAt==null || expiresAt>=startOfToday){
                "Lotto scaduto: registra uno scarto, non un consumo."
            }
            require(amount<=row.second){"Quantità lotto insufficiente"}
            require(db.update("stock_lots",ContentValues().apply{
                put("quantity",row.second-amount)
            },"id=? AND warehouse_id=?",arrayOf(lotId.toString(),wid.toString()))==1){"Lotto non aggiornato"}
            adjustStock(db,row.first,-amount,
                (if(discard)"Scarto lotto " else "Consumo lotto ")+row.third+" · "+actor.trim())
        }
    }
    fun issueSerial(wid:Long,serialId:Long,actor:String) {
        require(actor.trim().length in 2..100){"Operatore non valido"}
        transaction{db->
            val row=db.rawQuery("""SELECT item_id,serial_code FROM stock_serials
                WHERE id=? AND warehouse_id=? AND state='IN_STOCK'""",
                arrayOf(serialId.toString(),wid.toString())).use{c->
                require(c.moveToFirst()){"Seriale già assegnato o non trovato"}
                c.getLong(0) to c.getString(1)
            }
            require(db.update("stock_serials",ContentValues().apply{
                put("state","ISSUED");put("issued_at",System.currentTimeMillis())
            },"id=? AND warehouse_id=? AND state='IN_STOCK'",
                arrayOf(serialId.toString(),wid.toString()))==1){"Seriale già assegnato"}
            adjustStock(db,row.first,-1.0,"Scarico seriale ${row.second} · ${actor.trim()}")
        }
    }
}

