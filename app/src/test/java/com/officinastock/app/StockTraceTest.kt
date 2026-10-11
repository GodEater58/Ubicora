package com.officinastock.app

import android.net.Uri
import java.io.File
import java.util.UUID
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28])
class StockTraceTest {
    @Test fun fractionalLotCanBeConsumedToZero() {
        val lot=trace.registerLot(warehouse,item,"FRACTION",0.3,null)
        trace.consumeLot(warehouse,lot,0.1,"Operatore")
        trace.consumeLot(warehouse,lot,0.2,"Operatore")
        assertEquals(0.0,trace.lots(warehouse).single().quantity,0.0)
        assertEquals(11.7,db.item(item)!!.quantity,0.0)
    }
    private val context get()=RuntimeEnvironment.getApplication()
    private lateinit var db:StockDb
    private lateinit var trace:StockTraceDb
    private lateinit var planning:StockPlanningDb
    private var warehouse=0L
    private var other=0L
    private var item=0L
    @Before fun setup(){
        context.deleteDatabase("officina_stock_native4.db")
        db=StockDb(context)
        trace=StockTraceDb(db);planning=StockPlanningDb(db)
        val profile=db.createProfile("Impresa","1234")
        warehouse=db.createWarehouse(profile,"A","")
        other=db.createWarehouse(profile,"B","")
        item=db.addItem(warehouse,"Sensore","Elettrico",12.0,1.0,"pz","")
    }
    @After fun cleanup(){db.close()}

    @Test fun trackedLotPreventsUntaggedConsumptionAndDoesNotInventStock(){
        val initial=db.movements(warehouse).size
        val id=trace.registerLot(warehouse,item,"LOT-01",10.0,1_800_000_000_000L)
        assertEquals(12.0,db.item(item)!!.quantity,0.0)
        assertEquals(initial,db.movements(warehouse).size)
        assertEquals(10.0,trace.lots(warehouse).single().quantity,0.0)
        assertTrue(runCatching{trace.registerLot(other,item,"LOT-02",1.0,null)}.isFailure)
        assertTrue(runCatching{trace.registerLot(warehouse,item,"LOT-02",3.0,null)}.isFailure)
        assertTrue(runCatching{db.movement(item,-3.0,"Senza lotto")}.isFailure)
        trace.consumeLot(warehouse,id,4.0,"Magazziniere")
        assertEquals(8.0,db.item(item)!!.quantity,0.0)
        assertEquals(6.0,trace.lots(warehouse).single().quantity,0.0)
        assertTrue(runCatching{trace.consumeLot(other,id,1.0,"Magazziniere")}.isFailure)
        assertTrue(runCatching{trace.consumeLot(warehouse,id,7.0,"Magazziniere")}.isFailure)
        assertEquals(8.0,db.item(item)!!.quantity,0.0)
        assertTrue(db.movements(warehouse).any{it.note.contains("LOT-01")})
    }
    @Test fun serialsHaveUniqueIdentityAndAtomicIssue(){
        val serial=trace.registerSerial(warehouse,item,"SN0000001")
        assertEquals(12.0,db.item(item)!!.quantity,0.0)
        assertEquals("IN_STOCK",trace.serials(warehouse).single().state)
        assertTrue(runCatching{trace.registerSerial(warehouse,item,"SN0000001")}.isFailure)
        assertTrue(runCatching{trace.registerLot(warehouse,item,"LOT-01",1.0,null)}.isFailure)
        trace.issueSerial(warehouse,serial,"Operatore")
        assertEquals(11.0,db.item(item)!!.quantity,0.0)
        assertEquals("ISSUED",trace.serials(warehouse).single().state)
        assertTrue(runCatching{trace.issueSerial(warehouse,serial,"Operatore")}.isFailure)
        assertEquals(11.0,db.item(item)!!.quantity,0.0)
    }
    @Test fun activeReservationsPreventLotIssueBelowReservedStock(){
        val lot=trace.registerLot(warehouse,item,"LOT-X",12.0,null)
        val reserved=planning.reserve(warehouse,item,10.0,"Fermo macchina","Responsabile")
        assertTrue(runCatching{trace.consumeLot(warehouse,lot,3.0,"Operatore")}.isFailure)
        assertEquals(12.0,db.item(item)!!.quantity,0.0)
        assertEquals(12.0,trace.lots(warehouse).single().quantity,0.0)
        planning.close(warehouse,reserved,false,"Manager")
        trace.consumeLot(warehouse,lot,3.0,"Operatore")
        assertEquals(9.0,db.item(item)!!.quantity,0.0)
        assertEquals(9.0,trace.lots(warehouse).single().quantity,0.0)
    }
    @Test fun encryptedBackupRestoresLotSerialAndPhysicalQuantities(){
        val second=db.addItem(warehouse,"Multimetro","Utensili",2.0,0.0,"pz","")
        val lot=trace.registerLot(warehouse,item,"BATCH-2026",5.0,1_850_000_000_000L)
        val serial=trace.registerSerial(warehouse,second,"MM-000001")
        val file=File(context.cacheDir,"trace-backup-"+UUID.randomUUID()+".osbackup")
        try {
            BackupArchive(context,db).export(Uri.fromFile(file),"trace-protected-backup")
            assertTrue(file.length()>100)
            trace.consumeLot(warehouse,lot,2.0,"Tecnico")
            trace.issueSerial(warehouse,serial,"Tecnico")
            assertEquals(10.0,db.item(item)!!.quantity,0.0)
            assertEquals(1.0,db.item(second)!!.quantity,0.0)
            BackupArchive(context,db).restore(Uri.fromFile(file),"trace-protected-backup")
            assertEquals(12.0,db.item(item)!!.quantity,0.0)
            assertEquals(2.0,db.item(second)!!.quantity,0.0)
            assertEquals(5.0,trace.lots(warehouse).single().quantity,0.0)
            assertEquals("IN_STOCK",trace.serials(warehouse).single().state)
            db.validateRestoredData()
        } finally {file.delete()}
    }
    @Test fun expiredLotsRequireExplicitWasteMovement(){
        val id=trace.registerLot(warehouse,item,"OLD-BATCH",3.0,1L)
        assertTrue(runCatching{trace.consumeLot(warehouse,id,1.0,"Operatore")}.isFailure)
        assertEquals(12.0,db.item(item)!!.quantity,0.0)
        trace.consumeLot(warehouse,id,1.0,"Operatore",discard=true)
        assertEquals(11.0,db.item(item)!!.quantity,0.0)
        assertEquals(2.0,trace.lots(warehouse).single().quantity,0.0)
        assertTrue(db.movements(warehouse).any{it.note.contains("Scarto lotto OLD-BATCH")})
    }
    @Test fun historicalTraceBlocksUnitChangeAndV9MigrationIsAdditive(){
        val serial=trace.registerSerial(warehouse,item,"SN0000012")
        trace.issueSerial(warehouse,serial,"Operatore")
        val entry=db.item(item)!!
        assertTrue(runCatching{db.updateItemDetails(warehouse,item,entry.name,entry.category,
            entry.minimum,"kg",entry.note,entry.unitCost,entry.supplier,entry.leadDays)}.isFailure)
        db.writableDatabase.execSQL("DROP TABLE stock_lots")
        db.writableDatabase.execSQL("DROP TABLE stock_serials")
        db.writableDatabase.version=9
        db.close();db=StockDb(context);trace=StockTraceDb(db)
        assertEquals(11,db.readableDatabase.version)
        assertEquals(11.0,db.item(item)!!.quantity,0.0)
        assertTrue(db.movements(warehouse).any{it.note.contains("SN0000012")})
        assertTrue(trace.lots(warehouse).isEmpty() && trace.serials(warehouse).isEmpty())
    }
}

