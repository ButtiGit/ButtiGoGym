package it.pulse.workout.ui

import android.Manifest
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.rule.GrantPermissionRule
import it.pulse.workout.MainActivity
import org.junit.Rule
import org.junit.Test

class AppNavigationTest {
    @get:Rule(order = 0) val permissions = GrantPermissionRule.grant(
        Manifest.permission.POST_NOTIFICATIONS, Manifest.permission.ACTIVITY_RECOGNITION)
    @get:Rule(order = 1) val rule = createAndroidComposeRule<MainActivity>()

    @Test fun mainSectionsAndToolsRemainReachable() {
        rule.onNodeWithContentDescription("Sezione Oggi").performClick()
        capture("00-home")
        rule.onNodeWithContentDescription("Strumenti e aggiornamenti").performClick()
        rule.onNodeWithText("Backup e ripristino").assertExists()
        rule.onNodeWithText("Aggiornamenti").performClick()
        rule.onNodeWithText("Verifica aggiornamenti").assertExists()
        rule.onNodeWithText("Chiudi").performClick()
        rule.onNodeWithContentDescription("Sezione Scheda").performClick()
        capture("05-scheda")
        rule.onNodeWithContentDescription("Sezione Myself").performClick()
        rule.onNodeWithText("Mappa muscolare").assertIsDisplayed()
        rule.onNodeWithText("Fronte").assertIsDisplayed()
        rule.onNodeWithText("Retro").performClick()
        rule.onNodeWithText("Fronte").performClick()
        capture("06-myself")
        rule.onNode(hasScrollAction()).performScrollToNode(hasText("🏅 Rank di forza"))
        rule.onNodeWithText("🏅 Rank di forza").performClick()
        rule.onNodeWithText("Rank di forza").assertIsDisplayed()
        rule.onNodeWithContentDescription("Torna indietro").performClick()
        rule.onNodeWithContentDescription("Sezione Storico").performClick()
        capture("07-storico")
    }

    private fun capture(name: String) {
        saveTestScreenshot(rule, name)
    }
}
