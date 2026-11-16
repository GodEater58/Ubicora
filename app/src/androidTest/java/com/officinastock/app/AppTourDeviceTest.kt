package com.officinastock.app

import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The whole app on a real Android (emulator, encrypted archive): first start, demo warehouse,
 * every main screen, team central with the invite QR, then the app closed and opened again
 * (the archive is closed with the screen and must reopen intact). Clean install only.
 */
@RunWith(AndroidJUnit4::class)
class AppTourDeviceTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private var scenario: ActivityScenario<MainActivity>? = null

    @Before fun cleanInstall() { context.deleteDatabase("officina_stock_native4.db") }
    @After fun leaveClean() { scenario?.close(); context.deleteDatabase("officina_stock_native4.db") }

    private fun <T> onModel(block: (StockViewModel) -> T): T {
        var result: Result<T>? = null
        scenario!!.onActivity { result = runCatching { block(ViewModelProvider(it)[StockViewModel::class.java]) } }
        return result!!.getOrThrow()
    }
    /** The map and team screens animate forever: the clock is advanced by hand, never left to idle. */
    private fun waitFor(what: String, timeoutMs: Long = 30_000, condition: () -> Boolean) {
        val end = System.currentTimeMillis() + timeoutMs
        while (!condition()) {
            assertTrue("Tempo scaduto: $what", System.currentTimeMillis() < end)
            compose.mainClock.advanceTimeBy(100); Thread.sleep(50)
        }
    }
    private fun settle(what: String) = waitFor(what) { onModel { it.ui.initialized && !it.ui.busy } }
    private fun shown(text: String) = compose.onAllNodesWithText(text, substring = true, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
    private fun tab(label: String) {
        compose.onAllNodesWithContentDescription(label, useUnmergedTree = true).onFirst().performClick()
        settle("scheda $label")
        assertNull("Errore nella scheda $label", onModel { it.ui.message }?.takeIf { it.contains("errore", true) })
    }

    @Test fun firstStartDemoScreensQrAndReopen() {
        scenario = ActivityScenario.launch(MainActivity::class.java)
        compose.mainClock.autoAdvance = false
        waitFor("schermata iniziale") { shown("Crea azienda") }
        compose.onNodeWithText("Nome dell'azienda (es. Officina Rossi)").performTextInput("Azienda prova")
        compose.onNodeWithText("PIN locale · 4–8 cifre").performTextInput("1234")
        compose.onNodeWithText("Crea azienda").performClick()
        waitFor("creazione azienda") { shown("Dove lavoriamo?") }

        onModel { it.createWarehouse("Magazzino prova", "Sede") }; settle("magazzino")
        onModel { it.seedDemo() }; settle("dati demo")
        assertTrue("Archivio cifrato sul telefono", onModel { it.archiveEncrypted })
        val items = onModel { it.ui.items.size }
        assertTrue("Dati demo caricati", items > 0 && onModel { it.ui.placements.isNotEmpty() })
        onModel { it.dismissMessage() }

        for (label in listOf("Articoli", "Mappa", "Squadra", "Oggi")) tab(label)

        onModel { it.becomeTeamCentral("squadra-prova-2026") }; settle("telefono centrale")
        onModel { it.dismissMessage() }
        tab("Squadra")
        // The button is below the fold of a lazy list: not composed until the list scrolls to it.
        compose.onAllNodes(hasScrollToNodeAction() and hasAnyDescendant(hasText("Sincronizzazione tra i telefoni", substring = true)))
            .onFirst().performScrollToNode(hasText("QR per collegare un nuovo telefono"))
        compose.onNodeWithText("QR per collegare un nuovo telefono").performClick()
        waitFor("QR di invito") { compose.onAllNodesWithContentDescription("QR di collegamento").fetchSemanticsNodes().isNotEmpty() }
        assertFalse(shown("QR non disponibile"))

        // Closing the app closes the archive; opening it again must find the company and no archive problem.
        scenario!!.close()
        scenario = ActivityScenario.launch(MainActivity::class.java)
        compose.mainClock.autoAdvance = false
        waitFor("riapertura") { shown("Azienda prova") }
        assertNull("Archivio dopo la riapertura", DbCipher.lastProblem)
        assertTrue(onModel { it.archiveEncrypted })
    }
}
