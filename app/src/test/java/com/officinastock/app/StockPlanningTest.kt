package com.officinastock.app

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
class StockPlanningTest {
    private val context get()=RuntimeEnvironment.getApplication()
    private lateinit var db:StockDb
    private lateinit var planning:StockPlanningDb
    private lateinit var ops:OperationsDb
    private lateinit var enterprise:EnterpriseDb
    private var profile=0L
    private var warehouse=0L
    private var otherWarehouse=0L
    private var item=0L
    @Before fun setup() {
        context.deleteDatabase("officina_stock_native4.db")
        db=StockDb(context)
        planning=StockPlanningDb(db);ops=OperationsDb(db);enterprise=EnterpriseDb(db)
        profile=db.createProfile("Azienda","1234")
        warehouse=db.createWarehouse(profile,"Ricambi","")
        otherWarehouse=db.createWarehouse(profile,"Officina","")
        item=db.addItem(warehouse,"Cuscinetto","Ricambi",10.0,1.0,"pz","",unitCost=5.0)
    }
    @After fun cleanup()=db.close()

    @Test fun reservedQuantityIsProtectedFromNormalAndManagerStockReductions() {
        val r=planning.reserve(warehouse,item,7.0,"Ordine di lavoro 42","Manutentore")
        assertEquals(10.0,planning.availability(item).physical,0.0)
        assertEquals(7.0,planning.availability(item).reserved,0.0)
        assertEquals(3.0,planning.availability(item).available,0.0)
        assertTrue(runCatching{db.movement(item,-4.0,"Prelievo non autorizzato")}.isFailure)
        assertTrue(runCatching{db.lend(item,"Tecnico",4.0)}.isFailure)
        val transfer=ops.requestTransfer(warehouse,otherWarehouse,item,4.0,"Manager")
        assertTrue(runCatching{ops.progressTransfer(transfer,"Manager","SHIP")}.isFailure)
        assertEquals("REQUESTED",ops.transfers(warehouse).single().status)
        val work=ops.createMaintenance(warehouse,"Pressa","Controllo cuscinetto",item,4.0,"Manager")
        assertTrue(runCatching{ops.finishMaintenance(warehouse,work,true,"Manager")}.isFailure)
        assertEquals("OPEN",ops.maintenance(warehouse).first().status)
        assertEquals(10.0,db.item(item)!!.quantity,0.0)
        planning.close(warehouse,r,true,"Manager")
        assertEquals(3.0,db.item(item)!!.quantity,0.0)
        assertEquals(0.0,planning.availability(item).reserved,0.0)
        assertEquals("CONSUMED",planning.reservations(warehouse).single().status)
        assertTrue(runCatching{planning.close(warehouse,r,true,"Manager")}.isFailure)
        assertEquals(3.0,db.item(item)!!.quantity,0.0)
    }
    @Test fun reservationsAreWarehouseScopedAndReleaseDoesNotChangeStock() {
        val id=planning.reserve(warehouse,item,6.0,"Revisione pressa","Tecnico")
        assertTrue(runCatching{planning.close(otherWarehouse,id,false,"Manager")}.isFailure)
        assertTrue(runCatching{planning.reserve(otherWarehouse,item,1.0,"Altro magazzino","Manager")}.isFailure)
        assertTrue(runCatching{planning.reserve(warehouse,item,5.0,"Altra prenotazione","Manager")}.isFailure)
        planning.close(warehouse,id,false,"Manager")
        assertEquals(10.0,db.item(item)!!.quantity,0.0)
        assertEquals(10.0,planning.availability(item).available,0.0)
        assertEquals("RELEASED",planning.reservations(warehouse).single().status)
        db.movement(item,-9.0,"Prelievo consentito")
        assertEquals(1.0,db.item(item)!!.quantity,0.0)
    }
    @Test fun maintenanceAssetLinksAndCostSnapshotsSurvivePriceChanges(){
        val center=enterprise.createCenter(profile,"MNT","Manutenzione",1000.0)
        val asset=enterprise.createAsset(warehouse,"PR-01","Pressa n. 1",center)
        val id=ops.createMaintenance(warehouse,"Pressa n. 1","Sostituzione ricambio",item,2.0,
            "Tecnico",assetId=asset,laborHours=2.5,hourlyRate=35.0)
        ops.finishMaintenance(warehouse,id,true,"Manager")
        val work=ops.maintenance(warehouse).single()
        assertEquals("DONE",work.status)
        assertEquals(asset,work.assetId)
        assertEquals(2.5,work.laborHours,0.0)
        assertEquals(35.0,work.hourlyRate!!,0.0)
        assertEquals(10.0,work.partsCost!!,0.0)
        assertNotNull(work.completedAt)
        assertEquals(8.0,db.item(item)!!.quantity,0.0)
        db.updatePurchase(item,100.0,"Nuovo fornitore",null)
        assertEquals(10.0,ops.maintenance(warehouse).single().partsCost!!,0.0)
    }
    @Test fun versionEightToNineIsAdditiveAndPreservesHistory(){
        val old=db.item(item)!!
        val baseline=db.movements(warehouse).size
        db.writableDatabase.execSQL("DROP TABLE stock_reservations")
        db.writableDatabase.version=8
        db.close()
        db=StockDb(context);planning=StockPlanningDb(db);ops=OperationsDb(db)
        assertEquals(13,db.readableDatabase.version)
        assertEquals(old,db.item(item))
        assertEquals(baseline,db.movements(warehouse).size)
        assertTrue(db.authenticate(profile,"1234"))
        assertTrue(planning.reservations(warehouse).isEmpty())
        assertTrue(planning.reserve(warehouse,item,1.0,"Ripristino","Tecnico")>0)
    }
}
