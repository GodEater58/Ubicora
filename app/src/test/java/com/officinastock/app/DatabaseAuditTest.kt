package com.officinastock.app

import android.content.Context
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28])
class DatabaseAuditTest {
    private lateinit var db:StockDb
    private var warehouse=0L
    private var profile=0L
    @Before fun setup() {
        val c=RuntimeEnvironment.getApplication()
        c.deleteDatabase("officina_stock_native4.db")
        c.getSharedPreferences("login_attempts",Context.MODE_PRIVATE).edit().clear().commit()
        db=StockDb(c);profile=db.createProfile("Test","1234");warehouse=db.createWarehouse(profile,"A","")
    }
    @After fun close(){db.close()}
    private fun item(q:Double=10.0)=db.addItem(warehouse,"Vite","Ricambi",q,1.0,"pz","")
    private fun rejects(block:()->Unit){try{block();fail("Invalid operation accepted")}catch(expected:IllegalArgumentException){}}
    @Test fun overdrawRollsBackAndLoanReturnsExactlyOnce(){
        val id=item();val before=db.movements(warehouse).size
        rejects{db.movement(id,-11.0,"")}
        assertEquals(10.0,db.item(id)!!.quantity,0.0);assertEquals(before,db.movements(warehouse).size)
        db.lend(id,"Ada",3.0);val loan=db.activeLoans(warehouse).single()
        assertEquals(7.0,db.item(id)!!.quantity,0.0)
        db.returnLoan(loan.id);rejects{db.returnLoan(loan.id)}
        assertEquals(10.0,db.item(id)!!.quantity,0.0)
    }
    @Test fun restoreRejectsCrossWarehousePlacementAndRepairsCounter(){
        val id=item();val other=db.createWarehouse(profile,"B","")
        val placement=db.addPlacement(other,"Scaffale","B1",1f,1f)
        rejects{db.assignItem(id,placement)}
        db.writableDatabase.execSQL("UPDATE items SET placement_id=? WHERE id=?",arrayOf(placement,id))
        rejects{db.validateRestoredData()}
        db.writableDatabase.execSQL("UPDATE items SET placement_id=NULL WHERE id=?",arrayOf(id))
        db.writableDatabase.execSQL("UPDATE sku_sequences SET next_value=1")
        db.validateRestoredData()
        assertNotEquals(db.item(id)!!.sku,db.item(item())!!.sku)
    }
    @Test fun shrinkingMapDoesNotHideExistingObjects(){
        val id=db.addPlacement(warehouse,"Scaffale","A1",10f,6f)
        rejects{db.updateMapSize(warehouse,3f,3f)}
        assertEquals(12f,db.warehouses(profile).single().mapWidth,0f)
        rejects{db.movePlacement(id,Float.NaN,2f)}
    }
    @Test fun profileIsolationAndPersistedLoginThrottle(){
        val second=db.createProfile("Other","5678");db.createWarehouse(second,"Private","")
        assertEquals(listOf("A"),db.warehouses(profile).map{it.name})
        repeat(5){assertFalse(db.authenticate(profile,"0000"))}
        db.close();db=StockDb(RuntimeEnvironment.getApplication())
        rejects{db.authenticate(profile,"1234")}
        assertTrue(db.authenticate(second,"5678"))
    }
    @Test fun tenThousandItemsRemainUniqueSearchableAndExportable(){
        val start=System.nanoTime()
        val sql=db.writableDatabase
        sql.beginTransaction()
        try{repeat(10000){db.addItem(warehouse,"Part $it","Ricambi",1.0,0.0,"pz","")};sql.setTransactionSuccessful()}
        finally{sql.endTransaction()}
        assertEquals(10000,db.items(warehouse).size)
        assertEquals(1,db.items(warehouse,"Part 9999").size)
        assertEquals(10001,db.csv(warehouse).lineSequence().count{it.isNotEmpty()})
        println("AUDIT 10000 database items + search + CSV milliseconds="+(System.nanoTime()-start)/1000000)
    }
    @Test fun fullMovementHistoryCanBePagedAndFilteredWithoutTruncation(){
        val id=item()
        val base=System.currentTimeMillis()+10_000L
        val sql=db.writableDatabase
        sql.beginTransaction()
        try {
            val insert=sql.compileStatement("INSERT INTO movements(item_id,delta,note,created_at) VALUES(?,?,?,?)")
            try {
                repeat(260){ n ->
                    insert.clearBindings()
                    insert.bindLong(1,id)
                    insert.bindDouble(2,if(n%2==0)1.0 else -1.0)
                    insert.bindString(3,"event-${n}")
                    insert.bindLong(4,base+n)
                    insert.executeInsert()
                }
            } finally { insert.close() }
            sql.setTransactionSuccessful()
        } finally { sql.endTransaction() }
        val first=db.movementsPage(warehouse,base,0,0,101)
        val second=db.movementsPage(warehouse,base,0,100,101)
        val third=db.movementsPage(warehouse,base,0,200,101)
        assertEquals(101,first.size)
        assertEquals(101,second.size)
        assertEquals(60,third.size)
        assertEquals("event-259",first.first().note)
        assertEquals("event-0",third.last().note)
        assertEquals(260,(first.take(100)+second.take(100)+third).map{it.id}.toSet().size)
        assertEquals(101,db.movementsPage(warehouse,base,1,0,101).size)
        assertEquals(30,db.movementsPage(warehouse,base,1,100,101).size)
        assertEquals(30,db.movementsPage(warehouse,base,-1,100,101).size)
        assertEquals(60,db.movementsPage(warehouse,base+200,0,0,101).size)
        val other=db.createWarehouse(profile,"Other","")
        assertTrue(db.movementsPage(other,base,0,0,101).isEmpty())
        rejects{db.movementsPage(warehouse,base,0,0,500)}
    }
    @Test fun encryptedArchiveRoundTripAndInvalidRestorePreserveOriginal(){
        val context=RuntimeEnvironment.getApplication()
        val id=item()
        val uri=android.net.Uri.fromFile(java.io.File(context.cacheDir,"audit.osbackup"))
        val archive=BackupArchive(context,db)
        archive.export(uri,"password-test")
        db.movement(id,2.0,"extra")
        archive.restore(uri,"password-test")
        assertEquals(10.0,db.item(id)!!.quantity,0.0)
        val other=db.createWarehouse(profile,"B","")
        val p=db.addPlacement(other,"Scaffale","B",1f,1f)
        db.writableDatabase.execSQL("UPDATE items SET placement_id=? WHERE id=?",arrayOf(p,id))
        archive.export(uri,"password-test")
        db.assignItem(id,null)
        rejects{archive.restore(uri,"password-test")}
        assertNull(db.item(id)!!.placementId)
        assertEquals(10.0,db.item(id)!!.quantity,0.0)
    }
    @Test fun versionFiveUpgradePreservesStockAndAuth(){
        val id=item()
        db.writableDatabase.execSQL("DROP TABLE demo_assets")
        db.writableDatabase.version=5
        db.close();db=StockDb(RuntimeEnvironment.getApplication())
        assertEquals(6,db.readableDatabase.version)
        assertEquals(10.0,db.item(id)!!.quantity,0.0)
        assertTrue(db.authenticate(profile,"1234"))
        assertEquals(0,db.demoAssetCount(warehouse))
    }
    @Test fun arImportPreservesMetresAndRollsBackInvalidScan(){
        db.updateMapSize(warehouse,2f,2f)
        db.importArScan(warehouse,"""{"format":"OFFICINA_STOCK_AR_WALLS","version":1,"walls":[[0,0,10,0]]}""")
        val wall=db.walls(warehouse).single()
        assertEquals(10f,wall.x2-wall.x1,.001f)
        assertEquals(12f,db.warehouses(profile).single().mapWidth,.001f)
        rejects{db.importArScan(warehouse,"""{"format":"OFFICINA_STOCK_AR_WALLS","version":1,"walls":[[0,0,200,0]]}""")}
        assertEquals(wall,db.walls(warehouse).single())
    }
    @Test fun inventoryAndCsvDoNotSilentlyTruncateAfterTwentyThousand(){
        val sql=db.writableDatabase
        sql.beginTransaction()
        try {
            val insert=sql.compileStatement("INSERT INTO items(warehouse_id,sku,name,category,quantity,minimum,unit) VALUES(?,?,?,'Ricambi',1,0,'pz')")
            try {repeat(20001){insert.bindLong(1,warehouse);insert.bindString(2,"CODE-$it");insert.bindString(3,"Part $it");insert.executeInsert()}}
            finally{insert.close()}
            sql.setTransactionSuccessful()
        } finally{sql.endTransaction()}
        assertEquals(20001,db.items(warehouse).size)
        assertEquals(20002,db.csv(warehouse).lineSequence().count{it.isNotEmpty()})
    }
    @Test fun oversizedPhotoCannotProduceAnUnrestorableSuccessfulBackup(){
        val context=RuntimeEnvironment.getApplication();val id=item()
        val name="item_${java.util.UUID.randomUUID()}.jpg"
        val directory=java.io.File(context.filesDir,"article_photos").also{it.mkdirs()}
        val photo=java.io.File(directory,name)
        java.io.RandomAccessFile(photo,"rw").use{it.setLength(32L*1024*1024+1)}
        db.updatePhoto(id,name)
        val uri=android.net.Uri.fromFile(java.io.File(context.cacheDir,"too-large.osbackup"))
        rejects{BackupArchive(context,db).export(uri,"password-test")}
    }
    @Test fun originalVersionOneMigratesWithoutLosingStockHistoryOrLocations(){
        // DDL fixture extracted from repository commit 79025ce, not reconstructed from today's implementation.
        val context=RuntimeEnvironment.getApplication()
        db.close();context.deleteDatabase("officina_stock_native4.db")
        android.database.sqlite.SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath("officina_stock_native4.db"),null).use { old ->
            javaClass.getResourceAsStream("/schema-v1.sql")!!.bufferedReader().use { it.readText() }
                .split(';').filter{it.isNotBlank()}.forEach{old.execSQL(it)}
            old.execSQL("INSERT INTO profiles VALUES(1,'Original','salt','verifier',1)")
            old.execSQL("INSERT INTO warehouses VALUES(1,1,'Garage','Home')")
            old.execSQL("INSERT INTO placements VALUES(1,1,'Scaffale','A',40,50,1)")
            old.execSQL("INSERT INTO items VALUES(1,1,'RIC-000042','Original item','Ricambi',7,1,'pz',1,'note')")
            old.execSQL("INSERT INTO movements VALUES(1,1,7,'initial',1)")
            old.execSQL("INSERT INTO walls VALUES(1,1,0,0,100,0,'manual')")
            old.version=1
        }
        db=StockDb(context)
        assertEquals(7.0,db.item(1)!!.quantity,0.0)
        assertEquals(1L,db.item(1)!!.placementId)
        assertEquals(4.8f,db.placements(1).single().x,.001f)
        assertEquals(4f,db.placements(1).single().y,.001f)
        assertEquals(12f,db.walls(1).single().x2,.001f)
        assertEquals(1,db.movements(1).size)
        val next=db.addItem(1,"Next","Ricambi",1.0,0.0,"pz","")
        assertEquals("RIC-000043",db.item(next)!!.sku)
    }
}
