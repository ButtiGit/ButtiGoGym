package it.pulse.workout.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class StrengthRanksScreenTest {
    @get:Rule val rule = createComposeRule()

    @Test fun emptyHistoryRendersAllBadgeColorsAndWeightAction() {
        var edits = 0
        rule.setContent { PulseTheme {
            StrengthRanksScreen(emptyList(), null, {}, {}, { edits++ })
        } }
        rule.onNodeWithText("Imposta peso").performScrollTo().performClick()
        rule.runOnIdle { assertEquals(1, edits) }
        rule.onNode(hasScrollAction()).performScrollToNode(hasText("Legenda · dal livello più basso al più alto"))
        rule.onNodeWithText("Leggenda").performScrollTo().assertIsDisplayed()
        saveTestScreenshot(rule, "08-rank-legenda")
    }

    @Test fun validWeightAndDarkThemeRenderWithoutCrashing() {
        rule.setContent { PulseTheme(darkTheme = true) {
            StrengthRanksScreen(emptyList(), 58.2, {}, {}, {})
        } }
        rule.onNodeWithText("Rank di forza").assertIsDisplayed()
        saveTestScreenshot(rule, "09-rank-dark")
        rule.onNode(hasScrollAction()).performScrollToNode(hasText("Legenda · dal livello più basso al più alto"))
        rule.onNodeWithText("Leggenda").performScrollTo().assertIsDisplayed()
    }
}
