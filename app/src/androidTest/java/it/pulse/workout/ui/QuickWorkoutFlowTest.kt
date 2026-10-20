package it.pulse.workout.ui

import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import it.pulse.workout.data.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.Before
import org.junit.After

class QuickWorkoutFlowTest {
    @get:Rule val rule = createComposeRule()
    private val prefs get() = InstrumentationRegistry.getInstrumentation().targetContext
        .getSharedPreferences("workout_entry_ui", android.content.Context.MODE_PRIVATE)
    @Before fun quickMode() { prefs.edit().putBoolean("guided", false).commit() }
    @After fun resetMode() { prefs.edit().clear().commit() }
    private val exercise = Exercise("bench", "Panca", "PETTO", 3, "8–12", restSeconds = 90)
    private val day = WorkoutDay("quick", "LUN", "Petto", "", "30 min", listOf(exercise))
    private val draft = mutableStateOf(WorkoutDraft(day.id, System.currentTimeMillis()))
    private var updates = 0

    @Composable private fun Screen(dark: Boolean = false, timed: Boolean = false) {
        val actualDay = if (timed) day.copy(exercises = listOf(exercise.copy(timeBased = true))) else day
        PulseTheme(darkTheme = dark) {
            GuidedSessionScreen(actualDay, draft.value, emptyList(), false, {},
                { updates++; draft.value = it }, {}, {}, true, null, "running", {}, {}, {}, {})
        }
    }

    @Test fun saveIsExplicitAndLastSetCanBeRecalledWithoutSaving() {
        rule.setContent { Screen() }
        rule.onAllNodes(hasSetTextAction()).assertCountEquals(2)
        rule.onNodeWithText("Salva serie").assertIsNotEnabled()
        rule.onNodeWithTag("quick-weight").performTextInput("32,5")
        rule.onNodeWithTag("quick-reps").performTextInput("10")
        rule.onNodeWithTag("quick-reps").performImeAction()
        rule.runOnIdle { assertEquals(0, updates) }
        saveTestScreenshot(rule, "11-quick-entry")
        rule.onNodeWithText("Salva serie").performClick()
        rule.runOnIdle {
            assertEquals(1, updates)
            assertEquals("32.5", draft.value.entry(exercise, 1).weight)
            assertEquals("10", draft.value.entry(exercise, 1).reps)
            assertNull(draft.value.entry(exercise, 1).rir)
        }
        saveTestScreenshot(rule, "12-quick-rest")
        rule.onNodeWithText("Sono pronto").performClick()
        rule.onNodeWithText("Ripeti ultima · 32.5 kg × 10 rep").performScrollTo().performClick()
        rule.onNodeWithTag("quick-weight").assertTextContains("32.5")
        rule.onNodeWithTag("quick-reps").assertTextContains("10")
        rule.runOnIdle { assertEquals(2, updates); assertFalse(draft.value.entry(exercise, 2).done) }
    }

    @Test fun unfinishedInputsSurviveRecreation() {
        val restore = StateRestorationTester(rule)
        restore.setContent { Screen() }
        rule.onNodeWithTag("quick-weight").performTextInput("27,5")
        rule.onNodeWithTag("quick-reps").performTextInput("8")
        restore.emulateSavedInstanceStateRestore()
        rule.onNodeWithTag("quick-weight").assertTextContains("27,5")
        rule.onNodeWithTag("quick-reps").assertTextContains("8")
        rule.runOnIdle { assertEquals(0, updates) }
    }

    @Test fun darkTimedSetAcceptsBodyweightAndQuickDuration() {
        rule.setContent { Screen(dark = true, timed = true) }
        rule.onNodeWithTag("quick-weight").performTextInput("0")
        rule.onNodeWithTag("quick-reps").performClick().performImeAction()
        rule.onNodeWithText("60 sec").performScrollTo().performClick()
        rule.onNodeWithTag("quick-reps").assertTextContains("60")
        rule.onNodeWithText("Salva serie").assertIsEnabled()
        saveTestScreenshot(rule, "13-quick-dark")
        rule.onNodeWithText("Salva serie").performClick()
        rule.runOnIdle {
            assertEquals("0", draft.value.entry(exercise, 1).weight)
            assertEquals("60", draft.value.entry(exercise, 1).reps)
        }
    }

    @Test fun switchingEntryModePreservesThePendingValues() {
        rule.setContent { Screen() }
        rule.onNodeWithTag("quick-weight").performTextInput("20")
        rule.onNodeWithTag("quick-reps").performTextInput("8")
        rule.onNodeWithTag("quick-reps").performImeAction()
        rule.onNodeWithContentDescription("Opzioni allenamento").performClick()
        rule.onNodeWithText("Passa all’inserimento passo passo").performClick()
        rule.onAllNodes(hasSetTextAction()).assertCountEquals(1)
        rule.onNode(hasSetTextAction()).assertTextContains("20")
        rule.onNodeWithText("Continua").performClick()
        rule.onNode(hasSetTextAction()).assertTextContains("8")
        rule.runOnIdle { assertEquals(0, updates); assertTrue(prefs.getBoolean("guided", false)) }
    }
}
