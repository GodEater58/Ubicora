package com.officinastock.app

import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** 4.17: one company, two sites. The search looks in the other site, and a label of the other site takes the app there. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28])
class CompanySitesTest {
    private lateinit var model: StockViewModel
    private suspend fun awaitIdle() { withTimeout(15000) { while (model.ui.busy) delay(10) } }

    @Before fun setup() = runBlocking {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val app = RuntimeEnvironment.getApplication()
        app.deleteDatabase("officina_stock_native4.db")
        app.getSharedPreferences("login_attempts", 0).edit().clear().commit()
        val db = StockDb(app)
        val profile = try { db.createProfile("Azienda", "1234") } finally { db.close() }
        model = StockViewModel(app); awaitIdle()
        model.unlock(Profile(profile, "Azienda"), "1234"); awaitIdle()
        model.createWarehouse("Principale", "Rovigo"); awaitIdle()
        model.addItem("Vite M8", "Ricambi", 5.0, 1.0, "pz", ""); awaitIdle()
        model.leaveWarehouse()
        model.createWarehouse("Secondaria", "Padova"); awaitIdle()
        model.addItem("Vite M10", "Ricambi", 7.0, 1.0, "pz", ""); awaitIdle()
    }
    @After fun cleanup() { Dispatchers.resetMain() }

    @Test fun searchFindsTheArticleInTheOtherSiteAndOpensItThere() = runBlocking {
        assertEquals("Secondaria", model.ui.warehouse!!.name)
        model.searchOtherSites("vite")
        withTimeout(15000) { while (model.ui.siteHits.isEmpty()) delay(10) }
        val hit = model.ui.siteHits.single()
        assertEquals("Principale", hit.site.name); assertEquals("Vite M8", hit.item.name); assertEquals(5.0, hit.item.quantity, 0.0)
        // One letter is not a search; a new search replaces the old one.
        model.searchOtherSites("v"); assertTrue(model.ui.siteHits.isEmpty())
        model.openInSite(hit); awaitIdle()
        assertEquals("Principale", model.ui.warehouse!!.name)
        assertEquals(hit.item.id, model.ui.scannedItemId)
        assertEquals(listOf("Vite M8"), model.ui.items.map { it.name })
    }

    @Test fun labelOfTheOtherSiteTakesTheAppThere() = runBlocking {
        val main = model.ui.warehouses.single { it.name == "Principale" }
        val second = model.ui.warehouse!!
        val vite = StockDb(RuntimeEnvironment.getApplication()).let { db -> try { db.items(main.id).single() } finally { db.close() } }
        model.handleScannedCode(ArticleCodes.payload(main.id, vite.sku)); awaitIdle()
        assertEquals(main.id, model.ui.warehouse!!.id)
        assertEquals(vite.id, model.ui.scannedItemId)
        assertTrue(model.ui.message!!.contains("Principale"))
        // A label of a site that is not of this company still says so and changes nothing.
        model.acknowledgeScannedItem()
        model.handleScannedCode(ArticleCodes.payload(987654L, "RIC-000001")); awaitIdle()
        assertEquals(main.id, model.ui.warehouse!!.id); assertNull(model.ui.scannedItemId)
        // During a burst the labels of the other site are still refused: no jumping in the middle of a job.
        model.startBurst()
        model.handleScannedCode(ArticleCodes.payload(second.id, "RIC-000001")); awaitIdle()
        assertEquals(main.id, model.ui.warehouse!!.id)
    }
}
