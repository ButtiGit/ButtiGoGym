package it.pulse.workout.data

import org.junit.Assert.*
import org.junit.Test

class GuidedWorkoutTest {
    private val day = WorkoutDay(
        id = "mon", weekday = "LUNEDÌ", name = "Test", description = "",
        duration = "40 min",
        exercises = listOf(
            Exercise(id = "chest", name = "Chest press", muscle = "PETTO",
                sets = 2, target = "8-12", restSeconds = 120),
            Exercise(id = "row", name = "Rematore", muscle = "SCHIENA",
                sets = 1, target = "10-12", restSeconds = 90)
        )
    )

    @Test fun warmupIsPreservedButDoesNotAdvanceWorkingSet() {
        val draft = WorkoutDraft(dayId = day.id, startedAt = 1000L, daySnapshot = day)
        val warm = draft.recordSet(day, "10", "15", warmup = true, at = 2000L)
        assertEquals(0, warm.guidedExerciseIndex)
        assertEquals(1, warm.guidedWorkSet)
        assertEquals(1, warm.warmups(day.exercises.first()).size)
        assertEquals(92_000L, warm.restUntilMillis)
        assertFalse(warm.entry(day.exercises.first(), 1).done)
        assertTrue(warm.entries["warmup:chest:1"]!!.warmup)
    }

    @Test fun guidedWorkflowAdvancesAndRestTimeUsesPlan() {
        val start = WorkoutDraft(dayId = day.id, startedAt = 1000L, daySnapshot = day)
        val first = start.recordSet(day, "35", "12", warmup = false, at = 2000L)
        assertEquals(0, first.guidedExerciseIndex)
        assertEquals(2, first.guidedWorkSet)
        assertEquals(122_000L, first.restUntilMillis)
        assertTrue(first.entry(day.exercises.first(), 1).done)
        assertEquals(2000L, first.entry(day.exercises.first(), 1).completedAt)

        val second = first.recordSet(day, "40", "10", warmup = false, at = 125_000L)
        assertEquals(1, second.guidedExerciseIndex)
        assertEquals(1, second.guidedWorkSet)
        assertEquals(245_000L, second.restUntilMillis)

        val third = second.recordSet(day, "40", "11", warmup = false, at = 250_000L)
        assertEquals(day.exercises.size, third.guidedExerciseIndex)
        assertEquals(0L, third.restUntilMillis)
        assertEquals(3, day.exercises.sumOf { ex ->
            (1..ex.sets).count { third.entry(ex, it).done }
        })
    }

    @Test fun multipleWarmupsAndDecimalWeightsRemainDistinct() {
        val draft = WorkoutDraft(dayId = day.id, startedAt = 1000L, daySnapshot = day)
        val warm1 = draft.recordSet(day, "12.5", "12", warmup = true, at = 2000L)
        val warm2 = warm1.recordSet(day, "17.5", "8", warmup = true, at = 100_000L)
        assertEquals(2, warm2.warmups(day.exercises.first()).size)
        assertEquals("12.5", warm2.entries["warmup:chest:1"]?.weight)
        assertEquals("17.5", warm2.entries["warmup:chest:2"]?.weight)
        assertEquals(1, warm2.guidedWorkSet)
    }
}
