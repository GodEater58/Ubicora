package com.officinastock.app

import android.net.Uri
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28])
class OperationsDbTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private lateinit var db:StockDb
    private lateinit var ops:OperationsDb
    private var profile=0L;private var source=0L;private var destination=0L;private var item=0L
    @Before fun prepare(){
        context.deleteDatabase("officina_stock_native4.db")
        db=StockDb(context)
        ops=OperationsDb(db)
        profile=db.createProfile("Gestore","1234")
        source=db.createWarehouse(profile,"Origine","")
        destination=db.createWarehouse(profile,"Destinazione","")
        item=db.addItem(source,"Cuscinetto","Ricambi",20.0,5.0,"pz","",unitCost=4.0,supplier="Fornitore")
    }
    @After fun clean(){db.close()}
    private fun rejects(task:()->Unit){
        try {task();fail("Operazione non valida accettata")}catch(expected:IllegalArgumentException){}
    }
    @Test fun ordersApprovePlaceReceiveOnlyOnceAndPreserveAudit(){
        val id=ops.createOrder(source,item,6.0,"Fornitore","Manager")
        assertEquals("DRAFT",ops.orders(source).single().status)
        assertEquals(20.0,db.item(item)!!.quantity,0.0)
        rejects{ops.advanceOrder(source,id,"RECEIVE","Manager")}
        ops.advanceOrder(source,id,"APPROVE","Manager")
        ops.advanceOrder(source,id,"PLACE","Manager")
        ops.advanceOrder(source,id,"RECEIVE","Manager")
        assertEquals(26.0,db.item(item)!!.quantity,0.0)
        assertEquals("RECEIVED",ops.orders(source).single().status)
        rejects{ops.advanceOrder(source,id,"RECEIVE","Manager")}
        assertEquals(26.0,db.item(item)!!.quantity,0.0)
        assertTrue(db.movements(source).any{it.note.contains("Ricezione ordine #$id")})
        assertEquals(4,ops.events(source).size)
        rejects{ops.createOrder(destination,item,2.0,"","Manager")}
    }
    @Test fun countedStockRequiresApprovalAndRejectsStaleSnapshot(){
        val id=ops.requestCount(source,item,12.0,"Conteggio mensile","Manager")
        assertEquals(20.0,db.item(item)!!.quantity,0.0)
        db.movement(item,-1.0,"Consumo")
        rejects{ops.finishCount(source,id,true,"Manager")}
        assertEquals(19.0,db.item(item)!!.quantity,0.0)
        assertEquals("PENDING",ops.counts(source).single().status)
        ops.finishCount(source,id,false,"Manager")
        val next=ops.requestCount(source,item,12.0,"Nuovo conteggio","Manager")
        ops.finishCount(source,next,true,"Manager")
        assertEquals(12.0,db.item(item)!!.quantity,0.0)
        assertTrue(db.movements(source).any{it.note.contains("Inventario ciclico #$next")})
        rejects{ops.finishCount(source,next,true,"Manager")}
    }
    @Test fun transferTwoPhasePreservesQuantitiesAcrossWarehouses(){
        val id=ops.requestTransfer(source,destination,item,8.0,"Manager")
        val outside=db.createProfile("Isolato","5678")
        val external=db.createWarehouse(outside,"Terzo","")
        rejects{ops.requestTransfer(source,external,item,1.0,"Manager")}
        assertEquals(20.0,db.item(item)!!.quantity,0.0)
        ops.progressTransfer(id,"Manager","SHIP")
        assertEquals(12.0,db.item(item)!!.quantity,0.0)
        assertEquals(0,db.items(destination).size)
        ops.progressTransfer(id,"Manager","RECEIVE")
        val destItem=db.items(destination).single()
        assertEquals(db.item(item)!!.sku,destItem.sku)
        assertEquals(8.0,destItem.quantity,0.0)
        assertEquals("RECEIVED",ops.transfers(destination).single().status)
        rejects{ops.progressTransfer(id,"Manager","RECEIVE")}
        assertEquals(8.0,db.item(destItem.id)!!.quantity,0.0)
        assertTrue(db.movements(destination).any{it.note.contains("Ricezione trasferimento")})
    }
    @Test fun transferDoesNotMergeSameSkuWithIncompatibleItems() {
        val conflicting=db.addItem(destination,"Valvola incompatibile","Pneumatica",4.0,0.0,"kg","")
        db.writableDatabase.execSQL("UPDATE items SET sku=? WHERE id=?",
            arrayOf(db.item(item)!!.sku,conflicting))
        val transfer=ops.requestTransfer(source,destination,item,3.0,"Manager")
        ops.progressTransfer(transfer,"Manager","SHIP")
        assertEquals(17.0,db.item(item)!!.quantity,0.0)
        rejects{ops.progressTransfer(transfer,"Manager","RECEIVE")}
        assertEquals("IN_TRANSIT",ops.transfers(source).single().status)
        assertEquals(4.0,db.item(conflicting)!!.quantity,0.0)
        ops.progressTransfer(transfer,"Manager","CANCEL")
        assertEquals(20.0,db.item(item)!!.quantity,0.0)
    }
    @Test fun pendingOperationsPreventUnitChangesUntilResolved(){
        val row=db.item(item)!!
        fun setUnit(unit:String)=db.updateItemDetails(source,item,row.name,row.category,
            row.minimum,unit,row.note,row.unitCost,row.supplier,row.leadDays)
        val order=ops.createOrder(source,item,4.0,"ACME","Manager")
        val count=ops.requestCount(source,item,18.0,"Inventario","Manager")
        val transfer=ops.requestTransfer(source,destination,item,2.0,"Manager")
        val work=ops.createMaintenance(source,"Pressa","Sostituzione sensore",item,1.0,"Manager")
        rejects {setUnit("kg")}
        assertEquals("pz",db.item(item)!!.unit)
        assertEquals(20.0,db.item(item)!!.quantity,0.0)
        ops.advanceOrder(source,order,"CANCEL","Manager")
        rejects {setUnit("kg")}
        ops.finishCount(source,count,false,"Manager")
        rejects {setUnit("kg")}
        ops.progressTransfer(transfer,"Manager","CANCEL")
        rejects {setUnit("kg")}
        ops.finishMaintenance(source,work,false,"Manager")
        setUnit("kg")
        assertEquals("kg",db.item(item)!!.unit)
        assertEquals(20.0,db.item(item)!!.quantity,0.0)
    }
    @Test fun cancellationReturnsInventoryAndMaintenanceUsesMovement(){
        val transfer=ops.requestTransfer(source,destination,item,3.0,"Manager")
        ops.progressTransfer(transfer,"Manager","SHIP")
        ops.progressTransfer(transfer,"Manager","CANCEL")
        assertEquals(20.0,db.item(item)!!.quantity,0.0)
        rejects{ops.progressTransfer(transfer,"Manager","RECEIVE")}
        val id=ops.createMaintenance(source,"Linea A","Sostituzione cuscinetto",item,4.0,"Manager")
        assertEquals(20.0,db.item(item)!!.quantity,0.0)
        ops.finishMaintenance(source,id,true,"Manager")
        assertEquals(16.0,db.item(item)!!.quantity,0.0)
        assertTrue(db.movements(source).any{it.note.contains("manutenzione #$id")})
        rejects{ops.finishMaintenance(source,id,true,"Manager")}
    }
    @Test fun businessModeBudgetAndCsvAreIsolatedPerProfile(){
        val second=db.createProfile("Casa","5678")
        assertFalse(ops.businessMode(profile))
        ops.setBusinessMode(profile,true)
        ops.setMonthlyBudget(profile,750.0)
        assertTrue(ops.businessMode(profile))
        assertEquals(750.0,ops.monthlyBudget(profile)!!,0.0)
        assertFalse(ops.businessMode(second))
        assertNull(ops.monthlyBudget(second))
        val dangerous=PurchaseRequest(1,source,item,"Test",5.0,0.0,2.5,
            "=HYPERLINK(\"bad\")","Manager","DRAFT",1L)
        val report=OperationsCsv.export(StockUiState(
            profile=Profile(profile,"Gestore"),
            warehouse=db.warehouses(profile).first{it.id==source},
            orders=listOf(dangerous),
            items=db.items(source)
        ))
        assertTrue(report.contains("'=HYPERLINK"))
        assertTrue(report.contains("12.5"))
        assertTrue(runCatching{ops.setMonthlyBudget(profile,-1.0)}.isFailure)
    }
    @Test fun encryptedBackupRestoresEveryManagerWorkflow(){
        val order=ops.createOrder(source,item,4.0,"Acme","Manager")
        ops.advanceOrder(source,order,"APPROVE","Manager")
        val count=ops.requestCount(source,item,18.0,"Verifica scaffale","Manager")
        val transfer=ops.requestTransfer(source,destination,item,2.0,"Manager")
        ops.progressTransfer(transfer,"Manager","SHIP")
        val maintenance=ops.createMaintenance(source,"Pressa","Controllo sensore",null,0.0,"Manager")
        val file=File(context.cacheDir,"ops-archive.osbackup")
        val backup=BackupArchive(context,db)
        try {
            backup.export(Uri.fromFile(file),"password-di-test")
            ops.advanceOrder(source,order,"PLACE","Manager")
            ops.finishCount(source,count,false,"Manager")
            ops.finishMaintenance(source,maintenance,true,"Manager")
            backup.restore(Uri.fromFile(file),"password-di-test")
            assertEquals("APPROVED",ops.orders(source).single().status)
            assertEquals("PENDING",ops.counts(source).single().status)
            assertEquals("IN_TRANSIT",ops.transfers(source).single().status)
            assertEquals("OPEN",ops.maintenance(source).single().status)
            assertTrue(ops.events(source).isNotEmpty())
            assertEquals(18.0,db.item(item)!!.quantity,0.0)
        } finally {file.delete()}
    }
}
