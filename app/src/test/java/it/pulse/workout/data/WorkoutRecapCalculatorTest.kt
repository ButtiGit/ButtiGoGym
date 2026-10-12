package it.pulse.workout.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkoutRecapCalculatorTest {
    private val day = WorkoutDay(
        id = "fri", weekday = "VEN", name = "Petto e schiena",
        description = "", duration = "80 min",
        exercises = listOf(
            Exercise(id = "bench", name = "Chest press", muscle = "PETTO",
                sets = 2, target = "8-12"),
            Exercise(id = "farmers_walk", name = "Farmer's walk", muscle = "AVAMBRACCI",
                sets = 1, target = "30 sec", timeBased = true)
        )
    )

    @Test fun volumeCountsEveryCompletedWorkingSetButNotWarmupOrTimedCarry() {
        val r = SessionRecord(
            id = 1L, dayId = "fri", completedAt = 1L,
            durationMinutes = 40, durationSeconds = 2400,
            completedSets = 3, totalSets = 3, daySnapshot = day,
            entries = mapOf(
                "bench:1" to SetEntry("40", "10", done = true),
                "bench:2" to SetEntry("42,5", "8", done = true),
                "warmup:bench:1" to SetEntry("20", "15", done = true, warmup = true),
                "farmers_walk:1" to SetEntry("30", "40", done = true),
                "bench:3" to SetEntry("55", "12", done = false)
            )
        )
        val actual = WorkoutRecapCalculator.calculate(r)
        assertEquals(740.0, actual.volumeKg, 0.0001)
        assertEquals(3, actual.workingSets)
        assertEquals(2400, actual.durationSeconds)
        assertEquals(203, actual.estimatedCalories)
    }

    @Test fun emptySessionDoesNotInventVolumeAndCaloriesUseSavedDuration() {
        val r = SessionRecord(
            id = 2L, dayId = "fri", completedAt = 2L,
            durationMinutes = 0, durationSeconds = 90, completedSets = 0,
            totalSets = 2
        )
        val actual = WorkoutRecapCalculator.calculate(r)
        assertEquals(0.0, actual.volumeKg, 0.0)
        assertEquals(0, actual.workingSets)
        assertEquals(90, actual.durationSeconds)
        assertTrue(actual.estimatedCalories > 0)
    }
}
