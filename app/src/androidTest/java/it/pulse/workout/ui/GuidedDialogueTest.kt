package it.pulse.workout.ui

import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import it.pulse.workout.data.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class GuidedDialogueTest {
    @get:Rule val rule = createComposeRule()
    private val exercise = Exercise("bench", "Panca", "PETTO", 2, "8–12", restSeconds = 90)
    private val day = WorkoutDay("test", "Lunedì", "Petto", "", "30 min", listOf(exercise))
    private var draft = mutableStateOf(WorkoutDraft(day.id, System.currentTimeMillis()))
    private var updates = 0

    @Composable private fun Screen(onDiscard: () -> Unit = {}) {
        PulseTheme {
            GuidedSessionScreen(day, draft.value, emptyList(), false, {},
                { updates++; draft.value = it }, {}, {}, true, null, "running", {}, {}, {}, {},
                onDiscard = onDiscard)
        }
    }

    @Test fun oneInputAtATimeAndOnlyReviewPersistsTheSet() {
        rule.setContent { Screen() }
        rule.onAllNodes(hasSetTextAction()).assertCountEquals(1)
        rule.onNodeWithText("Continua").assertIsNotEnabled()
        rule.onNode(hasSetTextAction()).performTextInput("32,5")
        capture("01-peso")
        rule.onNodeWithText("Continua").performClick()
        rule.onAllNodes(hasSetTextAction()).assertCountEquals(1)
        rule.onNode(hasSetTextAction()).performTextInput("10")
        capture("02-ripetizioni")
        rule.onNodeWithText("Continua").performClick()
        rule.onAllNodes(hasSetTextAction()).assertCountEquals(0)
        rule.onNodeWithText("Salta", useUnmergedTree = true).performClick()
        rule.runOnIdle { assertEquals(0, updates) }
        capture("03-conferma")
        rule.onNodeWithText("Salva serie").performClick()
        rule.runOnIdle {
            assertEquals(1, updates)
            assertEquals("32.5", draft.value.entry(exercise, 1).weight)
            assertEquals("10", draft.value.entry(exercise, 1).reps)
            assertNull(draft.value.entry(exercise, 1).rir)
        }
        rule.onNodeWithText("Sono pronto").assertIsDisplayed()
        capture("04-recupero")
        rule.onNodeWithText("Sono pronto").performClick()
        rule.onAllNodes(hasSetTextAction()).assertCountEquals(1)
    }

    @Test fun restorationPreservesAnswerAndCurrentQuestion() {
        val restorer = StateRestorationTester(rule)
        restorer.setContent { Screen() }
        rule.onNode(hasSetTextAction()).performTextInput("20")
        rule.onNodeWithText("Continua").performClick()
        rule.onNode(hasSetTextAction()).performTextInput("8")
        restorer.emulateSavedInstanceStateRestore()
        rule.onAllNodes(hasSetTextAction()).assertCountEquals(1)
        rule.onNode(hasSetTextAction()).assertTextContains("8")
        rule.onNodeWithContentDescription("Indietro").performClick()
        rule.onNode(hasSetTextAction()).assertTextContains("20")
        rule.runOnIdle { assertEquals(0, updates) }
    }

    @Test fun checkInAsksOneQuestionAtATime() {
        var saved: PreWorkoutCheckIn? = null
        rule.setContent { PulseTheme {
            PreWorkoutCheckInDialog(null, { saved = it; null }, {}, {})
        } }
        rule.onNodeWithText("Bassa").performClick()
        rule.onNodeWithText("Bassa").assertDoesNotExist()
        rule.onNodeWithText("<6 h").performClick()
        rule.onNodeWithText("Nessuno").performClick()
        rule.runOnIdle { assertNull(saved) }
        rule.onNodeWithText("Continua").performScrollTo().performClick()
        rule.runOnIdle {
            assertEquals(WorkoutEnergy.LOW, saved?.energy)
            assertEquals(LastNightSleep.SHORT, saved?.sleep)
            assertEquals(WorkoutSoreness.NONE, saved?.soreness)
        }
    }

    @Test fun warmupAndTechniqueRemainAvailableInModals() {
        rule.setContent { Screen() }
        rule.onNodeWithContentDescription("Opzioni allenamento").performClick()
        rule.onNodeWithText("Tecnica e dimostrazione").performClick()
        rule.onNodeWithText("Tecnica").assertIsDisplayed()
        rule.onNodeWithText("Chiudi").performClick()
        rule.onNodeWithContentDescription("Opzioni allenamento").performClick()
        rule.onNode(isToggleable()).performClick()
        rule.onNode(hasSetTextAction()).performTextInput("10")
        rule.onNodeWithText("Continua").performClick()
        rule.onNode(hasSetTextAction()).performTextInput("8")
        rule.onNodeWithText("Continua").performClick()
        rule.onNodeWithText("Salva riscaldamento").performClick()
        rule.runOnIdle {
            assertEquals(1, draft.value.warmups(exercise).size)
            assertEquals(1, draft.value.guidedWorkSet)
            assertFalse(draft.value.entry(exercise, 1).done)
        }
    }

    @Test fun discardIsConfirmedAndDoesNotCallFinishOrUpdate() {
        var discarded = false
        var finished = false
        rule.setContent {
            PulseTheme {
                GuidedSessionScreen(day, draft.value, emptyList(), false, {},
                    { updates++; draft.value = it },
                    { finished = true }, {}, true, null, "running", {}, {}, {}, {},
                    onDiscard = { discarded = true })
            }
        }
        rule.onNodeWithContentDescription("Opzioni allenamento").performClick()
        rule.onNodeWithText("Scarta allenamento · non salvare").performClick()
        rule.onNodeWithText("Scartare l'allenamento?").assertIsDisplayed()
        rule.onNodeWithText("Scarta definitivamente").performClick()
        rule.runOnIdle {
            assertTrue(discarded)
            assertFalse(finished)
            assertEquals(0, updates)
        }
    }

    private fun capture(name: String) {
        saveTestScreenshot(rule, name)
    }
}
