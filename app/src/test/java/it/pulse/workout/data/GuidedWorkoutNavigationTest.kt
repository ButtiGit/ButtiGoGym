package it.pulse.workout.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class GuidedWorkoutNavigationTest {
    private val first = Exercise("press", "Chest press", "PETTO", 2, "8–12")
    private val second = Exercise("row", "Row", "SCHIENA", 2, "8–12")
    private val optional = Exercise("curl", "Curl", "BICIPITI", 1, "12", optional = true)
    private val day = WorkoutDay("fri", "VEN", "Petto e schiena",
        "", "55 min", listOf(first, second, optional))
    private fun start() = WorkoutDraft(day.id, startedAt = 100L, daySnapshot = day)

    @Test fun jumpingToNextPendingDoesNotMarkWorkingSetsDone() {
        val current = start()
        assertEquals(1, current.adjacentPendingExercise(day, 1))
        assertNull(current.adjacentPendingExercise(day, -1))
        val jumped = current.navigateToExercise(day, 1)
        assertEquals(1, jumped.guidedExerciseIndex)
        assertEquals(1, jumped.guidedWorkSet)
        assertEquals(0L, jumped.restUntilMillis)
        assertEquals(current.entries, jumped.entries)
        assertEquals(current.startedAt, jumped.startedAt)
    }

    @Test fun completedExerciseCannotBeReopenedAsBlankWorkingSet() {
        val completedFirst = start()
            .recordSet(day, "40", "12", false, 1_000L)
            .recordSet(day, "40", "12", false, 2_000L)
        assertEquals(1, completedFirst.guidedExerciseIndex)
        assertEquals(completedFirst, completedFirst.navigateToExercise(day, 0))
        assertNull(completedFirst.adjacentPendingExercise(day, -1))
    }

    @Test fun navigatingBackSelectsFirstIncompleteSeriesAndClearsRest() {
        val firstOne = start().recordSet(day, "40", "10", false, 1_000L)
        val jumpToRow = firstOne.navigateToExercise(day, 1)
        assertEquals(0L, jumpToRow.restUntilMillis)
        val back = jumpToRow.navigateToExercise(day, 0)
        assertEquals(0, back.guidedExerciseIndex)
        assertEquals(2, back.guidedWorkSet)
        assertTrue(back.entry(first, 1).done)
        assertFalse(back.entry(first, 2).done)
    }

    @Test fun switchingAheadThenRecordingLastCannotMissMandatorySets() {
        val ahead = start().navigateToExercise(day, 1)
            .recordSet(day, "45", "10", false, 1_000L)
            .recordSet(day, "45", "9", false, 2_000L)
        val atOptional = ahead.navigateToExercise(day, 2)
        val completedExtra = atOptional.recordSet(day, "10", "12", false, 3_000L)
        assertEquals(0, completedExtra.guidedExerciseIndex)
        assertEquals(1, completedExtra.guidedWorkSet)
        assertTrue(completedExtra.entry(second, 1).done)
        assertTrue(completedExtra.entry(second, 2).done)
        assertTrue(completedExtra.entry(optional, 1).done)

        val firstLogged = completedExtra.recordSet(day, "40", "10", false, 4_000L)
        val lastLogged = firstLogged.recordSet(day, "40", "10", false, 5_000L)
        assertEquals(day.exercises.size, lastLogged.guidedExerciseIndex)
    }

    @Test fun skippingOptionalLastCannotHideUnfinishedRequiredExercises() {
        val draft = start().navigateToExercise(day, 2)
        val afterSkip = draft.skipOptionalExercise(day)
        assertEquals(0, afterSkip.guidedExerciseIndex)
        assertEquals(1, afterSkip.guidedWorkSet)
        assertEquals(0L, afterSkip.restUntilMillis)
    }

    @Test fun completedRequiredExercisesMayFinishWithOptionalUnfinished() {
        val filled = start().recordSet(day, "40", "10", false, 1_000L)
            .recordSet(day, "40", "10", false, 2_000L)
            .recordSet(day, "50", "10", false, 3_000L)
            .recordSet(day, "50", "10", false, 4_000L)
        assertEquals(2, filled.guidedExerciseIndex)
        assertEquals(day.exercises.size, filled.skipOptionalExercise(day).guidedExerciseIndex)
    }

    @Test fun invalidSelectionAndDirectionRejected() {
        assertThrows(IllegalArgumentException::class.java) { start().navigateToExercise(day, -1) }
        assertThrows(IllegalArgumentException::class.java) { start().navigateToExercise(day, 3) }
        assertThrows(IllegalArgumentException::class.java) {
            start().adjacentPendingExercise(day, 2)
        }
    }
}

class QuickSetValuesTest {
    @Test fun emptyInputMustNotCompleteASet() {
        assertFalse(QuickSetValues().valid)
        assertFalse(QuickSetValues("20", "0").valid)
        assertFalse(QuickSetValues("NaN", "10").valid)
        assertTrue(QuickSetValues("0", "10").valid)
        assertTrue(QuickSetValues("27,5", "8", actualRir = 2).valid)
        assertFalse(QuickSetValues("40", "12", actualRir = 7).valid)
    }

    @Test fun adjustWeightAndRepetitionsAreBoundedAndHumanReadable() {
        val old = QuickSetValues("0", "1")
        assertEquals("0", old.adjustKg(-2.5).weight)
        assertEquals("2.5", old.adjustKg(2.5).weight)
        assertEquals("0", old.adjustReps(-1).reps)
        assertEquals("999", QuickSetValues("20", "999").adjustReps(1).reps)
    }

    @Test fun previousWorkingSetCanFillInputsWithoutFakingRirOrCompletion() {
        val input = QuickSetValues("10", "8", actualRir = 3)
        val copied = input.withPreviousSet(
            SetEntry(weight = "30", reps = "11", done = true, rir = 1)
        )
        assertEquals("30", copied.weight)
        assertEquals("11", copied.reps)
        assertNull(copied.actualRir)
        assertEquals(input, input.withPreviousSet(SetEntry("30", "11", done = false)))
        assertEquals(input, input.withPreviousSet(SetEntry("30", "11", done = true, warmup = true)))
    }
}
