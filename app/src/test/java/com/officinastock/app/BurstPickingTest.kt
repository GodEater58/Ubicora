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

    /** 4.15: one screen. The same label adds one, another label waits, every reading has its signal. */
    @Test fun sameLabelAddsOneAndOtherLabelsWaitForConfirmation() = runBlocking {
        val vite = model.ui.items.single { it.name == "Vite M8" }
        val dado = model.ui.items.single { it.name == "Dado M8" }
        val viteQr = ArticleCodes.payload(vite.warehouseId, vite.sku)
        model.startBurst()

        model.handleScannedCode(viteQr)
        assertEquals(vite.id, model.ui.burstItemId); assertEquals(1.0, model.ui.burstQty, 0.0)
        assertEquals(true, model.ui.scanSignal?.ok)
        model.handleScannedCode(viteQr)
        assertEquals(2.0, model.ui.burstQty, 0.0)

        // Another article while the first waits: ignored, nothing replaced.
        model.handleScannedCode(ArticleCodes.payload(dado.warehouseId, dado.sku))
        assertEquals(vite.id, model.ui.burstItemId); assertEquals(2.0, model.ui.burstQty, 0.0)

        // Never more than the shelf holds (5): the extra reading is refused.
        repeat(5) { model.handleScannedCode(viteQr) }
        assertEquals(5.0, model.ui.burstQty, 0.0)
        assertEquals(false, model.ui.scanSignal?.ok)
        model.burstAdjust(false); assertEquals(4.0, model.ui.burstQty, 0.0)

        model.burstPick(vite.id, model.ui.burstQty); awaitIdle()
        assertEquals(1.0, model.ui.items.single { it.id == vite.id }.quantity, 0.0)
        assertEquals(1.0, model.ui.burstQty, 0.0)
        assertEquals(-4.0, model.ui.movementUndo!!.delta, 0.0)

        // Undo gives the pieces back and the session goes on.
        model.undoMovement(); awaitIdle()
        assertEquals(5.0, model.ui.items.single { it.id == vite.id }.quantity, 0.0)
        assertTrue(model.ui.burstMode)

        // A label that is not an article: refused with its own signal.
        model.handleScannedCode("non-una-etichetta")
        assertEquals(false, model.ui.scanSignal?.ok); assertNull(model.ui.burstItemId)
    }

    /** 4.15 guided picking: the list withdraws what it asks, never more than the shelf, and can be undone. */
    @Test fun guidedPickWithdrawsTheListAndStopsAtTheShelf() = runBlocking {
        val vite = model.ui.items.single { it.name == "Vite M8" }   // 5 on the shelf
        val dado = model.ui.items.single { it.name == "Dado M8" }   // 4 on the shelf
        model.addToPickList(vite.id); model.addToPickList(vite.id); model.addToPickList(dado.id)
        assertEquals(2.0, model.ui.pickList.single { it.itemId == vite.id }.quantity, 0.0)
        repeat(9) { model.addToPickList(dado.id) }   // asks 10, only 4 there

        model.confirmPick(vite.id); awaitIdle()
        assertEquals(3.0, model.ui.items.single { it.id == vite.id }.quantity, 0.0)
        assertTrue("taken lines stay as done until the list is over", model.ui.pickList.single { it.itemId == vite.id }.done)
        assertEquals(-2.0, model.ui.movementUndo!!.delta, 0.0)

        model.skipPick(dado.id); assertTrue(model.ui.pickList.single { it.itemId == dado.id }.skipped)
        model.confirmPick(dado.id); awaitIdle()
        assertEquals(0.0, model.ui.items.single { it.id == dado.id }.quantity, 0.0)
        assertTrue(model.ui.pickList.isEmpty())

        // Nothing left: the list keeps the line and says why.
        model.addToPickList(dado.id); model.confirmPick(dado.id); awaitIdle()
        assertEquals(1, model.ui.pickList.size); assertNotNull(model.ui.message)
        model.clearPickList(); assertTrue(model.ui.pickList.isEmpty())

        // Confirmation by label: the wrong one is refused, the right one withdraws.
        model.addToPickList(vite.id)
        model.scanToConfirmPick(vite.id); assertNotEquals(0L, model.ui.scanRequest); model.consumeScanRequest()
        model.handleScannedCode(ArticleCodes.payload(dado.warehouseId, dado.sku))
        assertNotNull(model.ui.message); assertEquals(3.0, model.ui.items.single { it.id == vite.id }.quantity, 0.0)
        model.scanToConfirmPick(vite.id); model.consumeScanRequest()
        model.handleScannedCode(ArticleCodes.payload(vite.warehouseId, vite.sku)); awaitIdle()
        assertEquals(2.0, model.ui.items.single { it.id == vite.id }.quantity, 0.0)
        assertTrue(model.ui.pickList.isEmpty())
    }
}
