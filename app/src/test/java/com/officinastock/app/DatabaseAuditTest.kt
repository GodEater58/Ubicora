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
    @Test fun creationCannotStoreTextThatEditingAndCsvCannotAccept() {
        rejects{db.addItem(warehouse,"A".repeat(181),"Ricambi",1.0,0.0,"pz","")}
        rejects{db.addItem(warehouse,"Valido","Ricambi",1.0,0.0,"u".repeat(25),"")}
        rejects{db.addItem(warehouse,"Valido","Ricambi",1.0,0.0,"pz","N".repeat(3001))}
        assertTrue(db.items(warehouse).isEmpty())
        val id=db.addItem(warehouse,"A".repeat(180),"Ricambi",1.0,0.0,"","N".repeat(3000))
        assertEquals("pz",db.item(id)!!.unit)
        assertEquals(3000,CsvImport.parse(db.csv(warehouse)).single().note.length)
    }
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
    @Test fun restoreRejectsGeometryOutsideRoomAndImpossibleReceipts() {
        val id=item();val placement=db.addPlacement(warehouse,"Scaffale","A1",2f,2f)
        db.writableDatabase.execSQL("UPDATE placements SET x=150 WHERE id=?",arrayOf(placement))
        rejects{db.validateRestoredData()}
        db.writableDatabase.execSQL("UPDATE placements SET x=2 WHERE id=?",arrayOf(placement))
        val order=OperationsDb(db).createOrder(warehouse,id,2.0,"Fornitore","Manager")
        db.writableDatabase.execSQL("UPDATE ops_orders SET received=3 WHERE id=?",arrayOf(order))
        rejects{db.validateRestoredData()}
        db.writableDatabase.execSQL("UPDATE ops_orders SET received=1,status='RECEIVED' WHERE id=?",arrayOf(order))
        rejects{db.validateRestoredData()}
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
    @Test fun editingItemPreservesIdentityQuantityMovementsAndWarehouseIsolation(){
        val id=item(12.0)
        val before=db.movements(warehouse)
        val sku=db.item(id)!!.sku
        db.updateItemDetails(warehouse,id,"Cuscinetto","Meccanica",4.0,"pz",
            "Ricambio verificato",12.5,"Fornitore Test",3)
        val updated=db.item(id)!!
        assertEquals(sku,updated.sku)
        assertEquals("Meccanica",updated.category)
        assertEquals("Cuscinetto",updated.name)
        assertEquals(12.0,updated.quantity,0.0)
        assertEquals(4.0,updated.minimum,0.0)
        assertEquals("Ricambio verificato",updated.note)
        assertEquals(12.5,updated.unitCost!!,0.0)
        assertEquals(before.map{listOf(it.id,it.itemId,it.delta,it.note,it.createdAt)},
            db.movements(warehouse).map{listOf(it.id,it.itemId,it.delta,it.note,it.createdAt)})
        val other=db.createWarehouse(profile,"Second","")
        rejects {db.updateItemDetails(other,id,"Wrong","Ricambi",1.0,"pz","",null,"",null)}
        rejects {db.updateItemDetails(warehouse,id,"Bad","Ricambi",Double.NaN,"pz","",null,"",null)}
        assertEquals("Cuscinetto",db.item(id)!!.name)
    }
    @Test fun mapUndoNeverPutsThingsOutsideAShrunkRoomSoBackupsStayRestorable(){
        // A structure and a wall near the far edge of a 12 x 8 m room.
        val far=db.addPlacement(warehouse,"Scaffale","Lontano",11f,7f)
        val wallId=db.addWall(warehouse,floatArrayOf(10f,7.5f,11.5f,7.5f))
        val deleted=MapEdit.Deleted(listOf(db.placement(far)!! to db.itemSpots(far)))
        db.deletePlacement(far)
        val removedWall=MapEdit.WallsDeleted(listOf(db.wall(wallId)!!))
        db.deleteWall(wallId)
        db.updateMapSize(warehouse,6f,5f)
        // Before 4.7.3 undo brought them back outside the room and the backup check then refused the archive.
        rejects { deleted.revert(db) }
        rejects { removedWall.revert(db) }
        assertNull(db.placement(far))
        assertNull(db.wall(wallId))
        db.validateRestoredData()
    }
    @Test fun undoOfSeveralStructuresIsAllOrNothing(){
        val inside=db.addPlacement(warehouse,"Scaffale","Dentro",2f,2f)
        val outside=db.addPlacement(warehouse,"Scaffale","Fuori",11f,7f)
        val snapshot=listOf(inside,outside).map { db.placement(it)!! to db.itemSpots(it) }
        listOf(inside,outside).forEach { db.deletePlacement(it) }
        db.updateMapSize(warehouse,6f,5f)
        rejects { MapEdit.Deleted(snapshot).revert(db) }
        // The one that would fit is not restored alone: the edit applies completely or not at all.
        assertNull(db.placement(inside))
    }
    @Test fun undoRecordsTheOppositeMovementAndKeepsHistory(){
        val id=item(10.0)
        db.movement(id,-1.0,"Prelievo rapido")
        assertEquals(9.0,db.item(id)!!.quantity,0.0)
        // What the "Annulla" bar does: the opposite movement, never a deletion.
        db.movement(id,1.0,"Annullato: Prelievo 1 pz · Vite")
        assertEquals(10.0,db.item(id)!!.quantity,0.0)
        val notes=db.movements(warehouse).filter{it.itemId==id}.map{it.note}
        assertTrue(notes.contains("Prelievo rapido"))
        assertTrue(notes.contains("Annullato: Prelievo 1 pz · Vite"))
        // Undoing a load after the stock was used up is refused, stock never goes negative.
        val other=item(0.0)
        db.movement(other,1.0,"Carico rapido")
        db.movement(other,-1.0,"Prelievo")
        rejects { db.movement(other,-1.0,"Annullato: Carico 1 pz · Vite") }
        assertEquals(0.0,db.item(other)!!.quantity,0.0)
    }
    @Test fun csvExportAndImportKeepShelfLevelAndPosition(){
        val shelf=db.addPlacement(warehouse,"Scaffale","Scaffale A",2f,2f)
        db.updatePlacementLayout(shelf,4,6)
        val id=item()
        db.assignItem(id,shelf,3,5)
        val exported=CsvImport.parse(db.csv(warehouse)).single()
        assertEquals(3,exported.shelfLevel)
        assertEquals(5,exported.shelfSlot)
        val other=db.createWarehouse(profile,"B","")
        val copy=db.addPlacement(other,"Scaffale","Scaffale A",2f,2f)
        // The target structure is too small: the whole import is refused, nothing is written.
        rejects { db.importCsv(other,listOf(exported)) }
        assertTrue(db.items(other).isEmpty())
        db.updatePlacementLayout(copy,4,6)
        assertEquals(1,db.importCsv(other,listOf(exported)))
        val imported=db.items(other).single()
        assertEquals(copy,imported.placementId)
        assertEquals(3,imported.shelfLevel)
        assertEquals(5,imported.shelfSlot)
    }
    @Test fun csvImportRollsBackDuplicateAndPreservesOriginal(){
        val original=item()
        val row=CsvImportRow("RIC-001201","Other","Ricambi",2.0,1.0,"pz","","",null,"",null)
        assertEquals(1,db.importCsv(warehouse,listOf(row)))
        assertEquals(10.0,db.item(original)!!.quantity,0.0)
        val before=db.items(warehouse).size
        rejects { db.importCsv(warehouse,listOf(row)) }
        assertEquals(before,db.items(warehouse).size)
        assertEquals("RIC-001202",db.item(db.addItem(warehouse,"Third","Ricambi",0.0,0.0,"pz",""))!!.sku)
    }
    @Test fun countedStockCorrectionCreatesTraceableMovement(){
        val id=item(10.0)
        db.reconcileQuantity(id,7.0,"Conta fisica")
        assertEquals(7.0,db.item(id)!!.quantity,0.0)
        assertEquals(-3.0,db.movements(warehouse).first().delta,0.0)
        rejects{db.reconcileQuantity(id,7.0,"Ripetuto")}
        rejects{db.reconcileQuantity(id,-2.0,"Conta fisica")}
        assertEquals(7.0,db.item(id)!!.quantity,0.0)
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
    @Test fun versionFiveToSevenUpgradePreservesStockAndAuth(){
        val id=item()
        db.writableDatabase.execSQL("DROP TABLE demo_assets")
        db.writableDatabase.version=5
        db.close();db=StockDb(RuntimeEnvironment.getApplication())
        assertEquals(11,db.readableDatabase.version)
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

