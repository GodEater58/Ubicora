package com.officinastock.app

import android.content.Context
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
class ManagerOperationsTest {
    private lateinit var db:StockDb
    private var personal=0L
    private var business=0L
    private var item=0L
    @Before fun prepare() {
        val context=RuntimeEnvironment.getApplication()
        context.deleteDatabase("officina_stock_native4.db")
        db=StockDb(context)
        val p=db.createProfile("Privato","1234")
        val b=db.createProfile("Lavoro","5678")
        personal=db.createWarehouse(p,"Casa","")
        business=db.createWarehouse(b,"Azienda","")
        item=db.addItem(business,"Cuscinetto","Ricambi",5.0,2.0,"pz","")
    }
    @After fun cleanup(){db.close()}
    @Test fun managerActionsAreScopedAndDontChangeQuantities() {
        val id=db.createManagerAction(business,"PURCHASE","Acquistare cuscinetti",10.0,"Ufficio acquisti","Urgente",item)
        assertEquals(1,db.managerActions(business).size)
        assertTrue(db.managerActions(personal).isEmpty())
        assertEquals(5.0,db.item(item)!!.quantity,0.0)
        assertEquals("OPEN",db.managerActions(business).single().status)
        db.closeManagerAction(business,id)
        assertEquals("DONE",db.managerActions(business).single().status)
        assertEquals(5.0,db.item(item)!!.quantity,0.0)
        assertTrue(runCatching{db.closeManagerAction(business,id)}.isFailure)
        assertTrue(runCatching{db.closeManagerAction(personal,id)}.isFailure)
    }
    @Test fun cannotAssignOtherWarehouseItemsOrInvalidQuantities() {
        assertTrue(runCatching{db.createManagerAction(personal,"COUNT","Contare cuscinetti",null,"","",item)}.isFailure)
        assertTrue(runCatching{db.createManagerAction(business,"PURCHASE","Ordine",Double.NaN,"","",item)}.isFailure)
        val id=db.createManagerAction(business,"COUNT","Controlla scaffale",null,"Magazziniere","Verificare i codici")
        assertEquals("COUNT",db.managerActions(business).single().kind)
        db.closeManagerAction(business,id,true)
        assertEquals("CANCELLED",db.managerActions(business).single().status)
    }
}
