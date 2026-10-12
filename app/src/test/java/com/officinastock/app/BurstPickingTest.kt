package com.officinastock.app

import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** 4.10.2: burst picking must survive the reload that follows every withdrawal. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28])
class BurstPickingTest {
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
        model.addItem("Dado M8", "Ricambi", 4.0, 1.0, "pz", ""); awaitIdle()
    }
    @After fun cleanup() { Dispatchers.resetMain() }

    @Test fun scannerReopensAfterEveryWithdrawalUntilTheEnd() = runBlocking {
        val vite = model.ui.items.single { it.name == "Vite M8" }
        val dado = model.ui.items.single { it.name == "Dado M8" }
        model.startBurst()
        assertTrue(model.ui.burstMode); assertNotEquals(0L, model.ui.scanRequest)
        model.consumeScanRequest()

        model.handleScannedCode(ArticleCodes.payload(vite.warehouseId, vite.sku))
        assertEquals(vite.id, model.ui.burstItemId)
        model.burstPick(vite.id, 2.0); awaitIdle()
        assertTrue("burst must still be on after the reload", model.ui.burstMode)
        assertEquals(1, model.ui.burstCount)
        assertNull(model.ui.burstItemId)
        assertNotEquals("the scanner must reopen", 0L, model.ui.scanRequest)
        assertEquals(3.0, model.ui.items.single { it.id == vite.id }.quantity, 0.0)
        model.consumeScanRequest()

        model.handleScannedCode(ArticleCodes.payload(dado.warehouseId, dado.sku))
        model.burstPick(dado.id, 1.0); awaitIdle()
        assertEquals(2, model.ui.burstCount)
        assertEquals(3.0, model.ui.items.single { it.id == dado.id }.quantity, 0.0)

        // Closing the scanner ends the session.
        model.consumeScanRequest(); model.scanCancelled()
        assertFalse(model.ui.burstMode)
    }

    @Test fun aQuickMovementRefreshesStockAndHistory() = runBlocking {
        val vite = model.ui.items.single { it.name == "Vite M8" }
        val before = model.ui.movements.size
        model.quickMovement(vite.id, -1.0); awaitIdle()
        assertEquals(4.0, model.ui.items.single { it.id == vite.id }.quantity, 0.0)
        assertEquals(before + 1, model.ui.movements.size)
        assertNotNull(model.ui.movementUndo)
        model.undoMovement(); awaitIdle()
        assertEquals(5.0, model.ui.items.single { it.id == vite.id }.quantity, 0.0)
    }
}
