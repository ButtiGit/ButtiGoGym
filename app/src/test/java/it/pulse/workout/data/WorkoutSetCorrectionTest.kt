package it.pulse.workout.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkoutSetCorrectionTest {
    private val first = Exercise(
        id = "chest_press", name = "Chest press", muscle = "PETTO",
        sets = 3, target = "8-12", restSeconds = 90
    )
    private val second = Exercise(
        id = "pulldown", name = "Lat machine", muscle = "SCHIENA",
        sets = 2, target = "10-12"
    )
    private val day = WorkoutDay(
        id = "fri", weekday = "VEN", name = "Petto e schiena",
        description = "", duration = "60 min", exercises = listOf(first, second)
    )
    private fun starting() = WorkoutDraft(dayId = day.id, startedAt = 100L,
        daySnapshot = day)

    @Test fun correctedEarlierSetKeepsCompletionTimestampAndOtherSetsAndRest() {
        val base = starting()
            .recordSet(day, "40", "10", false, 1_000L)
            .recordSet(day, "42", "8", false, 2_000L)
            .recordSet(day, "44", "7", false, 3_000L)
        val original = base.entry(first, 1)
        val corrected = base.correctRecordedSet(
            day, first.id, 1, false, "47,5", "9"
        )
        assertEquals("47.5", corrected.entry(first, 1).weight)
        assertEquals("9", corrected.entry(first, 1).reps)
        assertTrue(corrected.entry(first, 1).done)
        assertEquals(original.completedAt, corrected.entry(first, 1).completedAt)
        assertEquals(base.entry(first, 2), corrected.entry(first, 2))
        assertEquals(base.guidedExerciseIndex, corrected.guidedExerciseIndex)
        assertEquals(base.guidedWorkSet, corrected.guidedWorkSet)
        assertEquals(base.restUntilMillis, corrected.restUntilMillis)
        assertEquals(base.startedAt, corrected.startedAt)
    }

    @Test fun undoEarlierWorkingSetDoesNotEraseLaterOnesAndSkipsThemOnReplay() {
        val base = starting()
            .recordSet(day, "40", "10", false, 1_000L)
            .recordSet(day, "42", "8", false, 2_000L)
            .recordSet(day, "44", "7", false, 3_000L)
            .recordSet(day, "50", "8", false, 4_000L)
        assertEquals(1, base.guidedExerciseIndex)
        val undone = base.cancelRecordedSet(day, first.id, 1, false)
        assertEquals(0, undone.guidedExerciseIndex)
        assertEquals(1, undone.guidedWorkSet)
        assertFalse(undone.entry(first, 1).done)
        assertEquals("40", undone.entry(first, 1).weight) // preload for retry
        assertTrue(undone.entry(first, 2).done)
        assertTrue(undone.entry(first, 3).done)
        assertTrue(undone.entry(second, 1).done)
        assertEquals(0L, undone.restUntilMillis)
        assertEquals(100L, undone.startedAt)

        val repeated = undone.recordSet(day, "41", "11", false, 5_000L)
        assertEquals(1, repeated.guidedExerciseIndex)
        assertEquals(2, repeated.guidedWorkSet) // skips already completed later sets
        assertEquals("42", repeated.entry(first, 2).weight)
        assertEquals("50", repeated.entry(second, 1).weight)
    }

    @Test fun undoLastTargetsMostRecentWorkingSetThenRewinds() {
        val base = starting()
            .recordSet(day, "40", "10", false, 1_000L)
            .recordSet(day, "42", "9", false, 2_000L)
        val undone = base.undoLastRecordedSet(day)
        assertTrue(undone.entry(first, 1).done)
        assertFalse(undone.entry(first, 2).done)
        assertEquals(0, undone.guidedExerciseIndex)
        assertEquals(2, undone.guidedWorkSet)
        assertEquals(0L, undone.restUntilMillis)
    }

    @Test fun warmupsMayBeEditedAndRemovedWithoutAffectingWorkSets() {
        val saved = starting()
            .recordSet(day, "15", "12", true, 1_000L)
            .recordSet(day, "20", "10", true, 2_000L)
            .recordSet(day, "40", "8", false, 3_000L)
        val corrected = saved.correctRecordedSet(
            day, first.id, 1, true, "17.5", "15"
        )
        assertEquals("17.5", corrected.warmups(first)[0].weight)
        assertEquals(1_000L, corrected.warmups(first)[0].completedAt)
        val undone = corrected.cancelRecordedSet(day, first.id, 1, true)
        assertEquals(1, undone.warmups(first).size)
        assertEquals("20", undone.warmups(first)[0].weight)
        assertTrue(undone.entry(first, 1).done)

        val next = undone.recordSet(day, "22", "8", true, 4_000L)
        val warmupKeys = next.entries.keys.filter { it.startsWith("warmup:") }
        assertEquals(2, warmupKeys.size)
        assertTrue("warmup:chest_press:2" in warmupKeys)
        assertTrue("warmup:chest_press:3" in warmupKeys)
        assertEquals("20", next.entries["warmup:chest_press:2"]?.weight)
    }

    @Test fun undoLatestWarmupDoesNotDiscardWorkingSet() {
        val base = starting()
            .recordSet(day, "40", "10", false, 1_000L)
            .recordSet(day, "20", "12", true, 2_000L)
        val undone = base.undoLastRecordedSet(day)
        assertTrue(undone.warmups(first).isEmpty())
        assertTrue(undone.entry(first, 1).done)
        assertEquals(0L, undone.restUntilMillis)
    }

    @Test fun completedWorkoutCanReturnToCancelledSetBeforeSaving() {
        val base = starting()
            .recordSet(day, "40", "10", false, 1_000L)
            .recordSet(day, "42", "9", false, 2_000L)
            .recordSet(day, "45", "7", false, 3_000L)
            .recordSet(day, "50", "10", false, 4_000L)
            .recordSet(day, "50", "9", false, 5_000L)
        assertEquals(day.exercises.size, base.guidedExerciseIndex)
        val canceled = base.cancelRecordedSet(day, first.id, 2, false)
        assertEquals(0, canceled.guidedExerciseIndex)
        assertEquals(2, canceled.guidedWorkSet)
        val restored = canceled.recordSet(day, "43", "8", false, 6_000L)
        assertEquals(day.exercises.size, restored.guidedExerciseIndex)
        assertEquals(5, restored.recordedSets(day).size)
    }

    @Test fun rejectsInvalidCorrectionsAndNonexistentRecords() {
        val completed = starting().recordSet(day, "40", "10", false, 1_000L)
        assertThrows(IllegalArgumentException::class.java) {
            completed.correctRecordedSet(day, first.id, 1, false, "-3", "10")
        }
        assertThrows(IllegalArgumentException::class.java) {
            completed.correctRecordedSet(day, first.id, 1, false, "Infinity", "10")
        }
        assertThrows(IllegalArgumentException::class.java) {
            completed.correctRecordedSet(day, first.id, 1, false, "40", "0")
        }
        assertThrows(IllegalArgumentException::class.java) {
            completed.correctRecordedSet(day, first.id, 2, false, "40", "10")
        }
        assertThrows(IllegalArgumentException::class.java) {
            completed.cancelRecordedSet(day, first.id, 2, false)
        }
        assertThrows(IllegalArgumentException::class.java) {
            completed.cancelRecordedSet(day, "unknown", 1, false)
        }
    }
}
