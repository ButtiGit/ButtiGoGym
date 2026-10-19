package it.pulse.workout.ui

import android.Manifest
import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import it.pulse.workout.MainActivity
import org.junit.Rule
import org.junit.Test
import java.io.File

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
        rule.onNodeWithText("Dettagli muscolo").assertIsDisplayed()
        capture("06-myself")
        rule.onNodeWithText("Dettagli muscolo").performClick()
        rule.onNodeWithText("Chiudi").performClick()
        rule.onNodeWithContentDescription("Sezione Storico").performClick()
        capture("07-storico")
    }

    private fun capture(name: String) {
        rule.waitForIdle()
        val bitmap = rule.onRoot().captureToImage().asAndroidBitmap()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val dir = File(context.getExternalFilesDir(null), "screenshots").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
