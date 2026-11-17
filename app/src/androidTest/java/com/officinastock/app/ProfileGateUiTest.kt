package com.officinastock.app

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Run on a clean emulator/test install, never against a user's production database. */
class ProfileGateUiTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()

    /** Spinners and the map animate forever, so the screen is never idle: the clock is advanced by hand. */
    private fun waitFor(text:String,timeoutMs:Long=30_000) {
        val end=System.currentTimeMillis()+timeoutMs
        while(compose.onAllNodesWithText(text).fetchSemanticsNodes().isEmpty()) {
            assertTrue("Tempo scaduto: $text. Stato: ${state()}. Sullo schermo: ${visibleWords().joinToString(" | ")}",System.currentTimeMillis()<end)
            compose.mainClock.advanceTimeBy(100); Thread.sleep(50)
        }
    }
    private fun visibleWords() = compose.onAllNodes(isRoot(),useUnmergedTree=true).fetchSemanticsNodes().flatMap { root ->
        generateSequence(listOf(root)) { level -> level.flatMap { it.children }.ifEmpty { null } }.flatten()
            .flatMap { it.config.getOrNull(SemanticsProperties.Text).orEmpty().map { t -> t.text } + listOfNotNull(it.config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString()) }
    }
    private fun state():String { var out=""; compose.runOnUiThread { val u=ViewModelProvider(compose.activity)[StockViewModel::class.java].ui
        out="busy=${u.busy} initialized=${u.initialized} message=${u.message} profiles=${u.profiles.size} profile=${u.profile?.name} locked=${u.sessionLocked} problem=${DbCipher.lastProblem}" }; return out }
    private fun frames() = compose.mainClock.advanceTimeBy(500)

    @Test fun onboardingBackgroundLockAndModalIsolation() {
        compose.mainClock.autoAdvance=false
        waitFor("Crea azienda")
        compose.onNodeWithText("Nome dell'azienda (es. Officina Rossi)").performTextInput("Audit UI")
        compose.onNodeWithText("PIN locale · 4–8 cifre").performTextInput("1234")
        frames()   // the typed text reaches the button only at the next frame: before it, the button is disabled
        compose.onNode(hasText("Crea azienda") and hasClickAction()).assertIsEnabled().performClick()
        waitFor("Dove lavoriamo?")
        compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        frames()
        compose.onNodeWithText("Scegli l'azienda").assertIsDisplayed()
        compose.onNodeWithText("Audit UI").performClick(); frames()
        compose.onNodeWithText("Accedi a Audit UI").assertIsDisplayed()
        compose.onNodeWithText("Annulla").performClick(); frames()
        compose.onNodeWithText("Accedi a Audit UI").assertDoesNotExist()
        compose.onNodeWithText("Scegli l'azienda").assertIsDisplayed()
    }
}
