package com.officinastock.app

import android.app.Application
import android.os.Looper
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.printToString
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performScrollToNode
import org.junit.Assert.*
import org.junit.Assume
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.runner.RunWith
import org.junit.runners.model.Statement
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The real screens on the demo warehouse, driven like a user would (tabs, buttons) on every CI run.
 * Covers what has no other automatic check: every main screen opens, the invite QR appears,
 * a map drag that cannot be saved does not leave a ghost position.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk=[34], qualifiers="w392dp-h850dp-xhdpi")
class AppFlowsUiTest {
    private val compose=createComposeRule()
    // The compose test activity exists only in the debug manifest: skip in testReleaseUnitTest.
    @get:Rule val rules: RuleChain = RuleChain
        .outerRule(TestRule { base,_ -> object:Statement() { override fun evaluate() { Assume.assumeTrue(BuildConfig.DEBUG); base.evaluate() } } })
        .around(compose)
    private lateinit var model: StockViewModel

    private fun idleOnce() = shadowOf(Looper.getMainLooper()).idle()

    /** Waits for the model with the clock advanced by hand (map and team screens animate forever). */
    private fun settle() {
        repeat(400) {
            idleOnce(); compose.mainClock.advanceTimeByFrame()
            if(model.ui.initialized && !model.ui.busy) { repeat(12) { compose.mainClock.advanceTimeBy(100); idleOnce() }; return }
            Thread.sleep(25)
        }
        fail("Operazione ancora in corso: busy=${model.ui.busy} message=${model.ui.message}")
    }
    /** The setter is private to the app: tests reach the Compose state behind `ui` directly. */
    @Suppress("UNCHECKED_CAST")
    private fun setUi(state:StockUiState) {
        val field=StockViewModel::class.java.getDeclaredField("ui\$delegate").apply { isAccessible=true }
        (field.get(model) as androidx.compose.runtime.MutableState<StockUiState>).value=state
    }
    /** Scrolls whichever lazy list holds [text] (off-screen items are not composed); on failure shows the screen. */
    private fun scrollTo(text: String) {
        val lists = compose.onAllNodes(hasScrollToNodeAction())
        val count = lists.fetchSemanticsNodes().size
        val found = (0 until count).any { i -> runCatching { lists[i].performScrollToNode(hasText(text)) }.isSuccess }
        assertTrue("Non trovo '$text' in $count liste. Schermo:\n" + compose.onAllNodes(isRoot(), useUnmergedTree = true).printToString(), found)
    }
    private fun waitFor(timeoutMs:Long=10_000,condition:()->Boolean) {
        val end=System.currentTimeMillis()+timeoutMs
        while(!condition()) {
            assertTrue("Tempo scaduto",System.currentTimeMillis()<end)
            idleOnce(); compose.mainClock.advanceTimeBy(100); Thread.sleep(25)
        }
    }
    private fun tab(label:String) { compose.onAllNodesWithContentDescription(label,useUnmergedTree=true).onFirst().performClick(); settle() }
    private fun shown(text:String) = compose.onAllNodesWithText(text,substring=true,useUnmergedTree=true).fetchSemanticsNodes().isNotEmpty()

    @Before fun demoWarehouse() {
        val app=RuntimeEnvironment.getApplication() as Application
        app.deleteDatabase("officina_stock_native4.db")
        model=StockViewModel(app)
        compose.mainClock.autoAdvance=false
        compose.setContent { StockTheme { StockApp(model,{},{},{},{},{},{_->},{_->}) } }
        settle()
        model.createProfile("Azienda prova","1234"); settle()
        model.createWarehouse("Magazzino prova","Sede"); settle()
        model.seedDemo(); settle()
        assertTrue("Dati demo non caricati: ${model.ui.message}",model.ui.items.isNotEmpty() && model.ui.placements.isNotEmpty())
        model.dismissMessage(); settle()
    }

    @Test fun everyMainScreenOpensOnTheDemoWarehouse() {
        for(label in listOf("Articoli","Mappa","Squadra","Oggi")) {
            tab(label)
            assertNull("Errore aprendo $label",model.ui.message?.takeIf { it.contains("errore",true) || it.contains("non disponibile",true) })
        }
        model.openItemDetails(model.ui.items.first().id); settle()
    }

    @Test fun inviteQrIsShownOnTheCentral() {
        model.becomeTeamCentral("squadra-prova-2026"); settle()
        model.dismissMessage(); settle()
        tab("Squadra")
        scrollTo("QR per collegare un nuovo telefono")
        compose.onNodeWithText("QR per collegare un nuovo telefono").performClick()
        // The QR is prepared in the background: first the waiting text, never the red error.
        waitFor { compose.onAllNodesWithContentDescription("QR di collegamento").fetchSemanticsNodes().isNotEmpty() }
        assertFalse(shown("QR non disponibile"))
        assertFalse(shown("Preparo il QR"))
    }

    @Test fun dragThatCannotBeSavedPutsTheStructureBack() {
        tab("Mappa")
        // The demo structures arrive locked: unlock one, as the user does before moving it.
        model.lockPlacement(model.ui.placements.first().id,false); settle()
        val shelf=model.ui.placements.first { !it.locked }
        model.previewMove(shelf.id,shelf.x+0.5f,shelf.y)
        assertNotEquals(shelf.x,model.ui.placements.first { it.id==shelf.id }.x)
        setUi(model.ui.copy(busy=true))               // a sync started in the middle of the gesture
        model.commitMove(shelf.id,shelf.x,shelf.y,shelf.x+0.5f,shelf.y)
        val shown=model.ui.placements.first { it.id==shelf.id }
        assertEquals(shelf.x,shown.x,0.001f); assertEquals(shelf.y,shown.y,0.001f)
        setUi(model.ui.copy(busy=false))
        // With nothing in progress the same drag is saved.
        model.previewMove(shelf.id,shelf.x+0.5f,shelf.y)
        model.commitMove(shelf.id,shelf.x,shelf.y,model.ui.placements.first { it.id==shelf.id }.x,shelf.y); settle()
        assertNotEquals(shelf.x,model.ui.placements.first { it.id==shelf.id }.x)
    }
}
