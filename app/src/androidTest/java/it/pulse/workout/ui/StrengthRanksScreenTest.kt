package it.pulse.workout.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import it.pulse.workout.data.*
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

    @Test fun recordedRankExpandsAndReturnsTheSelectedMuscle() {
        val exercise = Exercise("bench", "Panca", "PETTO", 2, "8–12", primaryMuscles = listOf("chest"))
        val day = WorkoutDay("rank-test", "LUN", "Petto", "", "30 min", listOf(exercise))
        val record = SessionRecord(id = 100L, dayId = day.id, completedAt = 100L,
            durationMinutes = 30, completedSets = 1, totalSets = 2, daySnapshot = day,
            entries = mapOf("bench:1" to SetEntry("45", "10", done = true)))
        var selected = ""
        rule.setContent { PulseTheme {
            StrengthRanksScreen(listOf(record), 60.0, {}, { selected = it }, {})
        } }
        rule.onNode(hasScrollAction()).performScrollToNode(hasText("Petto"))
        rule.onNodeWithText("Petto").performClick()
        rule.onNodeWithText("Vedi il muscolo →").performScrollTo().assertIsDisplayed()
        saveTestScreenshot(rule, "10-rank-expanded")
        rule.onNodeWithText("Vedi il muscolo →").performClick()
        rule.runOnIdle { assertEquals("chest", selected) }
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
