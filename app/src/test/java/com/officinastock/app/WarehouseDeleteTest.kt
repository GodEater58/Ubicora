package com.officinastock.app

import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** 4.12.1: a warehouse can be deleted with everything in it, never by mistake and never while synced. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28])
class WarehouseDeleteTest {
    private lateinit var model: StockViewModel
    private suspend fun awaitIdle() { withTimeout(15000) { while (model.ui.busy) delay(10) } }

    @Before fun setup() = runBlocking {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val app = RuntimeEnvironment.getApplication()
        app.deleteDatabase("officina_stock_native4.db")
        app.getSharedPreferences("login_attempts", 0).edit().clear().commit()
        val db = StockDb(app)
        val profile = try { db.createProfile("Nicola", "1234") } finally { db.close() }
        model = StockViewModel(app); awaitIdle()
        model.unlock(Profile(profile, "Nicola"), "1234"); awaitIdle()
        model.createWarehouse("Officina", "Rovigo"); awaitIdle()
        model.addItem("Vite M8", "Ricambi", 5.0, 1.0, "pz", ""); awaitIdle()
    }
    @After fun cleanup() { Dispatchers.resetMain() }

    @Test fun wrongNameKeepsTheWarehouse() = runBlocking {
        model.deleteWarehouse("Magazzino"); awaitIdle()
        assertEquals("Officina", model.ui.warehouse?.name)
        assertEquals(1, model.ui.warehouses.size)
        assertEquals(1, model.ui.items.size)
    }

    @Test fun deletesWarehouseAndItsContents() = runBlocking {
        val id = model.ui.warehouse!!.id
        model.deleteWarehouse(" officina "); awaitIdle()
        assertNull(model.ui.warehouse)
        assertTrue(model.ui.warehouses.isEmpty())
        val db = StockDb(RuntimeEnvironment.getApplication())
        try { assertTrue(db.items(id).isEmpty()) } finally { db.close() }
    }

    @Test fun syncedWarehouseIsProtected() = runBlocking {
        model.becomeTeamCentral("password-del-team-1"); awaitIdle()
        assertEquals(TeamRole.CENTRAL, model.ui.team.role)
        model.deleteWarehouse("Officina"); awaitIdle()
        assertEquals("Officina", model.ui.warehouse?.name)
        assertNotNull(model.ui.message)
    }
}
