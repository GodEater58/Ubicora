package com.officinastock.app

import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.json.JSONObject
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28])
class EnterpriseDbTest {
    private val context get()=RuntimeEnvironment.getApplication()
    private lateinit var db:StockDb
    private lateinit var enterprise:EnterpriseDb
    private lateinit var ops:OperationsDb
    private var profile=0L
    private var warehouse=0L
    private var item=0L
    @Before fun setup(){
        context.deleteDatabase("officina_stock_native4.db")
        db=StockDb(context)
        enterprise=EnterpriseDb(db);ops=OperationsDb(db)
        profile=db.createProfile("Industria","1234")
        warehouse=db.createWarehouse(profile,"Magazzino A","Padova")
        item=db.addItem(warehouse,"Sensore","Elettrico",10.0,2.0,"pz","",unitCost=12.0)
    }
    @After fun finish(){db.close()}

    @Test fun directoriesAreTenantScopedAndOutboxIsDurable() {
        val other=db.createProfile("Casa","5678")
        val secondWarehouse=db.createWarehouse(other,"Casa","")
        val cost=enterprise.createCenter(profile,"MAN","Manutenzione",1200.0)
        val supplier=enterprise.createSupplier(profile,"Ricambi Veneto","IT123","acquisti@example.it",6)
        val asset=enterprise.createAsset(warehouse,"PRESSA01","Pressa meccanica",cost)
        val staff=enterprise.addStaffDirectoryEntry(profile,"Operatore","WAREHOUSE")
        assertEquals(1,enterprise.centers(profile).size)
        assertEquals(1,enterprise.suppliers(profile).size)
        assertEquals(1,enterprise.assets(warehouse).size)
        assertEquals(1,enterprise.staffDirectory(profile).size)
        assertTrue(enterprise.centers(other).isEmpty())
        assertTrue(enterprise.suppliers(other).isEmpty())
        assertTrue(enterprise.assets(secondWarehouse).isEmpty())
        assertTrue(enterprise.staffDirectory(other).isEmpty())
        assertTrue(runCatching {enterprise.createAsset(secondWarehouse,"PRESSA02","Pressa esterna",cost)}.isFailure)
        assertTrue(runCatching {ops.createOrder(warehouse,item,2.0,"","Operatore",
            enterprise.createCenter(other,"EST","Reparto esterno",null),supplier)}.isFailure)
        val order=ops.createOrder(warehouse,item,4.0,"","Operatore",cost,supplier)
        assertEquals(cost,ops.orders(warehouse).single().centerId)
        assertEquals(supplier,ops.orders(warehouse).single().supplierId)
        assertEquals("Ricambi Veneto",ops.orders(warehouse).single().supplier)
        assertEquals(12.0,db.item(item)!!.unitCost!!,0.0)
        assertEquals(10.0,db.item(item)!!.quantity,0.0)
        val pending=enterprise.pendingEvents(profile)
        assertEquals(4,pending.size)
        assertEquals(4,pending.map{it.entityUuid}.toSet().size)
        pending.forEach{
            UUID.fromString(it.id);UUID.fromString(it.entityUuid)
            assertEquals(it.entityUuid,JSONObject(it.payload).getString("uuid"))
        }
        enterprise.markAcknowledged(profile,listOf(pending.first().id))
        assertEquals(3,enterprise.pendingEvents(profile).size)
        assertTrue(runCatching{enterprise.markAcknowledged(other,listOf(pending[1].id))}.isFailure)
        assertEquals(order,ops.orders(warehouse).single().id)
        assertEquals(asset,enterprise.assets(warehouse).single().id)
        assertEquals(staff,enterprise.staffDirectory(profile).single().id)
    }
    @Test fun partialReceiptsDoNotOverReceiveOrDoubleCount() {
        val order=ops.createOrder(warehouse,item,7.0,"Fornitore","Manager")
        ops.advanceOrder(warehouse,order,"APPROVE","Manager")
        ops.advanceOrder(warehouse,order,"PLACE","Manager")
        ops.receiveOrderQuantity(warehouse,order,2.0,"Manager")
        assertEquals("ORDERED",ops.orders(warehouse).single().status)
        assertEquals(2.0,ops.orders(warehouse).single().received,0.0)
        assertEquals(12.0,db.item(item)!!.quantity,0.0)
        assertTrue(runCatching{ops.receiveOrderQuantity(warehouse,order,6.0,"Manager")}.isFailure)
        assertEquals(12.0,db.item(item)!!.quantity,0.0)
        ops.receiveOrderQuantity(warehouse,order,5.0,"Manager")
        assertEquals("RECEIVED",ops.orders(warehouse).single().status)
        assertEquals(7.0,ops.orders(warehouse).single().received,0.0)
        assertEquals(17.0,db.item(item)!!.quantity,0.0)
        assertTrue(runCatching{ops.receiveOrderQuantity(warehouse,order,1.0,"Manager")}.isFailure)
        assertEquals(17.0,db.item(item)!!.quantity,0.0)
        assertTrue(db.movements(warehouse).count{it.note.contains("Ricezione parziale ordine #$order")}==2)
    }
    @Test fun migrationFromSchema7PreservesStockAndRecreatesEnterpriseTables(){
        val center=enterprise.createCenter(profile,"RND","Ricerca",null)
        val before=db.item(item)!!
        val sql=db.writableDatabase
        // Simulate a genuine pre-4.4 installation with no enterprise tables or linked fields.
        // SQLite does not support DROP COLUMN on all Android versions; the migration must
        // cope with already present optional columns as well as absent ones.
        sql.execSQL("DELETE FROM sync_outbox")
        sql.execSQL("DELETE FROM org_assets")
        sql.execSQL("DELETE FROM org_staff_directory")
        sql.execSQL("DELETE FROM org_suppliers")
        sql.execSQL("DELETE FROM org_centers")
        listOf("sync_outbox","org_assets","org_staff_directory","org_suppliers","org_centers").forEach{
            sql.execSQL("DROP TABLE $it")
        }
        sql.version=7
        db.close()
        db=StockDb(context);enterprise=EnterpriseDb(db)
        assertEquals(9,db.readableDatabase.version)
        assertEquals(before,db.item(item))
        assertTrue(db.authenticate(profile,"1234"))
        assertTrue(enterprise.centers(profile).isEmpty())
        assertTrue(enterprise.pendingEvents(profile).isEmpty())
        assertTrue(center>0)
        enterprise.createCenter(profile,"RND","Ricerca",null)
    }
    @Test fun syncProtocolRejectsInsecureEndpointsAndMixedProfiles() {
        val a=EnterpriseSyncProtocol.Target("https://inventory.example.it",UUID.randomUUID().toString())
        val profile1=enterprise.createCenter(profile,"PRD","Produzione",null)
        val events=enterprise.pendingEvents(profile)
        assertEquals(1,events.size)
        val json=JSONObject(String(EnterpriseSyncProtocol.batch(a,events),Charsets.UTF_8))
        assertEquals(1,json.getInt("protocol_version"))
        assertEquals(events[0].id,json.getJSONArray("events").getJSONObject(0).getString("event_id"))
        assertTrue(runCatching{
            EnterpriseSyncProtocol.batch(a.copy(baseUrl="http://inventory.example.it"),events)
        }.isFailure)
        assertTrue(runCatching{
            EnterpriseSyncProtocol.batch(a.copy(baseUrl="https://user:pass@inventory.example.it"),events)
        }.isFailure)
        assertTrue(profile1>0)
    }
}
