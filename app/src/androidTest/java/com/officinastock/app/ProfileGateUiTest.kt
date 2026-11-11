package com.officinastock.app

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.Lifecycle
import org.junit.Rule
import org.junit.Test

/** Run on a clean emulator/test install, never against a user's production database. */
class ProfileGateUiTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    @Test fun onboardingBackgroundLockAndModalIsolation() {
        compose.waitUntil(10000) { compose.onAllNodesWithText("Crea azienda").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Nome dell'azienda (es. Officina Rossi)").performTextInput("Audit UI")
        compose.onNodeWithText("PIN locale · 4–8 cifre").performTextInput("1234")
        compose.onNodeWithText("Crea azienda").performClick()
        compose.waitUntil(10000) { compose.onAllNodesWithText("Dove lavoriamo?").fetchSemanticsNodes().isNotEmpty() }
        compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        compose.onNodeWithText("Scegli l'azienda").assertIsDisplayed()
        compose.onNodeWithText("Audit UI").performClick()
        compose.onNodeWithText("Accedi a Audit UI").assertIsDisplayed()
        compose.onNodeWithText("Annulla").performClick()
        compose.onNodeWithText("Accedi a Audit UI").assertDoesNotExist()
        compose.onNodeWithText("Scegli l'azienda").assertIsDisplayed()
    }
}
