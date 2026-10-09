package com.officinastock.app

import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
@org.robolectric.annotation.Config(sdk=[28])
class OperationalAnalyticsTest {
    private lateinit var stock:StockDb
    private var wid=0L
    private var other=0L
    private var foreign=0L
    private var item=0L
    private val now get()=System.currentTimeMillis()
    @Before fun setup() {
        val context=RuntimeEnvironment.getApplication()
        context.deleteDatabase("officina_stock_native4.db")
        stock=StockDb(context)
        val profile=stock.createProfile("Azienda","1234")
        wid=stock.createWarehouse(profile,"Ricambi","")
        other=stock.createWarehouse(profile,"Altra sede","")
        foreign=stock.createWarehouse(stock.createProfile("Privato","5678"),"Casa","")
        item=stock.addItem(wid,"Cuscinetto","Ricambi",10.0,8.0,"pz","",unitCost=5.0)
    }
    @After fun cleanup()=stock.close()
    private fun order(w:Long,price:Double?,status:String="APPROVED") {
        stock.writableDatabase.execSQL("INSERT INTO ops_orders(warehouse_id,item_id,quantity,unit_price,status,created_at) VALUES(?,?,2,?,?,?)",
            arrayOf(w,if(w==wid)item else stock.addItem(w,"Altro ricambio","Ricambi",0.0,0.0,"pz",""),price,status,now))
    }
    @Test fun fullTotalsIncludeAllProfileWarehousesButNeverOtherProfilesOrCancelledOrders() {
        repeat(300){order(wid,4.0)}
        order(other,20.0)
        order(wid,null)
        order(foreign,1000.0)
        order(wid,1000.0,"CANCELLED")
        val r=OperationalAnalytics(stock).report(wid,30)
        assertEquals(301,r.openOrders)
        assertEquals(2440.0,r.monthlyCommitments,0.001)
        assertEquals(1,r.unpricedOrders)
        assertEquals(1,r.orderStages["CANCELLED"])
        assertEquals(250,OperationsDb(stock).orders(wid).size)
        assertEquals(302,OperationsDb(stock).orders(wid,500).size)
    }
    @Test fun availabilityUsesEveryActiveReservationAndExpiryCountsOnlyRemainingMaterial() {
        val planning=StockPlanningDb(stock)
        repeat(300){planning.reserve(wid,item,0.01,"Intervento $it","Tecnico")}
        assertEquals(3.0,planning.reservedByItem(wid)[item]!!,0.000001)
        val trace=StockTraceDb(stock)
        trace.registerLot(wid,item,"Scaduto",1.0,1L)
        trace.registerLot(wid,item,"Senza scadenza",1.0,null)
        val r=OperationalAnalytics(stock).report(wid,7)
        assertEquals(1,r.criticalAvailable)
        assertEquals(1,r.reservedReferences)
        assertEquals(1,r.expiredLots)
        assertEquals(0,r.expiringLots)
        assertEquals(250,planning.reservations(wid).size)
        assertEquals(300,planning.reservations(wid,500).size)
    }
    @Test fun costsUseCompletionDateExcludeUnknownTotalsAndTreatZeroLaborAsKnownZero() {
        val db=stock.writableDatabase
        fun job(w:Long,parts:Double?,hours:Double,rate:Double?,completed:Long,status:String="DONE") {
            db.execSQL("""INSERT INTO ops_maintenance(warehouse_id,machine,description,status,created_at,
                parts_cost,labor_hours,hourly_rate,completed_at) VALUES(?,'Pressa','Revisione',?,?, ?,?,?,?)""",
                arrayOf(w,status,now-100L*86400000,parts,hours,rate,completed))
        }
        job(wid,10.0,2.0,20.0,now)
        job(wid,5.0,0.0,null,now)
        job(wid,null,2.0,20.0,now)
        job(wid,900.0,0.0,null,now-100L*86400000)
        job(foreign,900.0,0.0,null,now)
        job(wid,900.0,0.0,null,now,"CANCELLED")
        val r=OperationalAnalytics(stock).report(wid,30)
        assertEquals(3,r.completedWork)
        assertEquals(1,r.unpricedWork)
        assertEquals(55.0,r.maintenanceCost,0.001)
        assertNull(r.countAccuracy)
    }
    @Test fun inventoryIndicatorMeasuresOnlyConfirmedCountsInTheSelectedWindow() {
        fun count(expected:Double,counted:Double,status:String,created:Long) {
            stock.writableDatabase.execSQL("INSERT INTO ops_counts(warehouse_id,item_id,expected,counted,reason,status,created_at) VALUES(?,?,?,?,'Verifica',?,?)",
                arrayOf(wid,item,expected,counted,status,created))
        }
        count(10.0,10.0,"APPLIED",now)
        count(10.0,9.0,"APPLIED",now)
        count(10.0,10.0,"PENDING",now)
        count(10.0,10.0,"APPLIED",now-100L*86400000)
        val r=OperationalAnalytics(stock).report(wid,7)
        assertEquals(2,r.confirmedCounts)
        assertEquals(1,r.exactCounts)
        assertEquals(50.0,r.countAccuracy!!,0.001)
    }
}
