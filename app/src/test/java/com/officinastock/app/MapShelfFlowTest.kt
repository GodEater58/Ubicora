package com.officinastock.app

import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** 4.20: shelf labels and tags open the shelf from the front, place articles, and wait for the PIN. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28])
class MapShelfFlowTest {
    private lateinit var model: StockViewModel
    private lateinit var profile: Profile
    private suspend fun awaitIdle() { withTimeout(15000) { while (model.ui.busy) delay(10) } }

    @Before fun setup() = runBlocking {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val app = RuntimeEnvironment.getApplication()
        app.deleteDatabase("officina_stock_native4.db")
        app.getSharedPreferences("login_attempts", 0).edit().clear().commit()
        val db = StockDb(app)
        val id = try { db.createProfile("Nicola", "1234") } finally { db.close() }
        profile = Profile(id, "Nicola")
        model = StockViewModel(app); awaitIdle()
        model.unlock(profile, "1234"); awaitIdle()
        model.createWarehouse("Officina", "Rovigo"); awaitIdle()
        model.addItem("Vite M8", "Ricambi", 5.0, 1.0, "pz", ""); awaitIdle()
        model.addPlacement("Scaffale", "sc2", 3f, 3f); awaitIdle()
        model.addPlacement("Banco", "Banco", 6f, 3f); awaitIdle()
        val sc2 = model.ui.placements.single { it.label == "sc2" }
        model.updatePlacementLayout(sc2.id, 3, 2); awaitIdle()
    }
    @After fun cleanup() { Dispatchers.resetMain() }

    private fun shelf(label: String) = model.ui.placements.single { it.label == label }
    private fun vite() = model.ui.items.single { it.name == "Vite M8" }

    @Test fun structuresHaveATeamIdAndTheirLabelOpensTheFrontView() = runBlocking {
        val sc2 = shelf("sc2")
        assertNotNull("every structure gets a team-wide id", sc2.uid)
        val label = LabelSpec.placement(sc2).payload
        assertTrue(label, label.startsWith("UBICORA-LOC:2:"))
        model.handleScannedCode(label)
        assertEquals(sc2.id, model.ui.shelfOpenId)
        assertNull(model.ui.mapFocusPlacementId)
        // Old labels (by number) still open it.
        model.closeShelf()
        model.handleScannedCode(ArticleCodes.locationPayload(sc2.warehouseId, sc2.id))
        assertEquals(sc2.id, model.ui.shelfOpenId)
        // An article label closes it and opens the article.
        model.handleScannedCode(ArticleCodes.payload(vite().warehouseId, vite().sku))
        assertNull(model.ui.shelfOpenId); assertEquals(vite().id, model.ui.scannedItemId)
        // A label of a structure that no longer exists says so.
        model.acknowledgeScannedItem()
        model.handleScannedCode(ArticleCodes.locationPayload(sc2.warehouseId, sc2.id, "ab".repeat(16)))
        assertNull(model.ui.shelfOpenId); assertNotNull(model.ui.message)
    }

    @Test fun whileChoosingAPlaceTheShelfLabelAnswers() = runBlocking {
        // A bench has no shelves: reading its label puts the article there at once.
        model.startLocationPick(listOf(vite().id))
        model.handleScannedCode(LabelSpec.placement(shelf("Banco")).payload); awaitIdle()
        assertNull(model.ui.locationPickItemIds)
        assertEquals(shelf("Banco").id, vite().placementId)
        // A shelf with positions opens from the front to choose the shelf and position.
        model.startLocationPick(listOf(vite().id))
        val sc2 = shelf("sc2")
        model.handleScannedCode(LabelSpec.placement(sc2).payload)
        assertEquals(sc2.id, model.ui.shelfOpenId)
        assertNotNull("still waiting for the place", model.ui.locationPickItemIds)
        model.completeLocationPick(sc2.id, 3, 2); awaitIdle()
        assertEquals(sc2.id, vite().placementId); assertEquals(3, vite().shelfLevel); assertEquals(2, vite().shelfSlot)
        assertEquals("the shelf stays open to show where it went", sc2.id, model.ui.shelfOpenId)
        val view = ShelfLayout.of(shelf("sc2"), model.ui.items)
        assertEquals(listOf(vite().id), view.rows.first().cells[1].items.map { it.id })
    }

    @Test fun findingOnTheMapAndClosingTheShelfForTheMap() = runBlocking {
        val sc2 = shelf("sc2")
        model.openShelf(sc2.id); assertEquals(sc2.id, model.ui.shelfOpenId)
        model.openShelf(-5); assertEquals("unknown ids are ignored", sc2.id, model.ui.shelfOpenId)
        model.showItemOnMap(vite().id)
        assertEquals(vite().id, model.ui.mapFindItemId); assertNull(model.ui.shelfOpenId)
        model.consumeMapFind(); assertNull(model.ui.mapFindItemId)
        // Choosing a place on the map closes the front view, which would hide the map.
        model.openShelf(sc2.id); model.startLocationPick(listOf(vite().id))
        assertNull(model.ui.shelfOpenId)
        // A reload keeps the shelf open.
        model.cancelLocationPick(); model.openShelf(sc2.id)
        model.addItem("Dado M8", "Ricambi", 2.0, 1.0, "pz", ""); awaitIdle()
        assertEquals(sc2.id, model.ui.shelfOpenId)
        // Leaving the app closes it, like the other screens over the tabs.
        model.lockForBackground()
        assertNull(model.ui.shelfOpenId)
    }

    @Test fun aTagThatOpenedTheAppIsReadAfterThePin() = runBlocking {
        val sc2 = shelf("sc2")
        model.lockForBackground()
        model.queueTagCode(LabelSpec.placement(sc2).payload)
        model.consumePendingCode()
        assertNotNull("locked: the tag waits", model.ui.pendingCode)
        assertNull(model.ui.shelfOpenId)
        model.unlock(profile, "1234"); awaitIdle()
        if (model.ui.warehouse == null) { model.selectWarehouse(model.ui.warehouses.single()); awaitIdle() }
        model.consumePendingCode()
        assertNull(model.ui.pendingCode)
        assertEquals(sc2.id, model.ui.shelfOpenId)
        // A tag of another app: a message, nothing opens.
        model.closeShelf(); model.queueTagCode(null); model.consumePendingCode()
        assertNull(model.ui.shelfOpenId); assertNotNull(model.ui.message)
    }

    @Test fun shelfTagsCarryTheLabelAndTheRecordThatOpensTheApp() {
        val sc2 = shelf("sc2")
        model.requestNfcWritePlacement(sc2.id)
        assertEquals(LabelSpec.placement(sc2).payload, model.nfcWritePayload)
        assertEquals("sc2", model.ui.nfcWrite)
        val message = NfcLabels.message(model.nfcWritePayload!!, "com.officinastock.app")
        assertEquals(2, message.records.size)
        assertEquals(model.nfcWritePayload, NfcLabels.firstText(listOf(message)))
        assertEquals(1, NfcLabels.message("x").records.size)
        assertNull(NfcLabels.firstText(emptyList()))
        // Small enough for the cheapest tags (NTAG213: 137 bytes for the message).
        assertTrue(message.toByteArray().size <= 137)
        model.cancelNfcWrite(); assertNull(model.nfcWritePayload)
    }
}
