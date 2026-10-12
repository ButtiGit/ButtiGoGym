package it.pulse.workout.data

import org.junit.Assert.*
import org.junit.Test

class OptionalWorkoutsTest {
    private val main = Exercise(
        id = "main", name = "Press", muscle = "PETTO", sets = 2,
        target = "8–12", restSeconds = 120
    )
    private val recall = Exercise(
        id = "recall", name = "Richiamo opzionale", muscle = "SPALLE",
        sets = 2, target = "12–18", restSeconds = 75, optional = true
    )
    private val plan = WorkoutDay("mon", "LUNEDÌ", "Test", "", "75–90 min",
        listOf(main, recall), cooldown = "Tapis roulant 12–15 minuti")

    @Test fun optionalSetsAreNotRequiredToFinishTheWorkout() {
        assertEquals(2, plan.setCount)
        assertEquals(2, plan.optionalSetCount)
        assertEquals(4, plan.allSetCount)

        val start = WorkoutDraft(dayId = "mon", startedAt = 0, daySnapshot = plan)
        val first = start.recordSet(plan, "25", "9", false, 1000)
        val second = first.recordSet(plan, "25", "9", false, 122000)
        assertEquals(1, second.guidedExerciseIndex)
        assertEquals(1, second.guidedWorkSet)
        val skipped = second.skipOptionalExercise(plan)
        assertEquals(2, skipped.guidedExerciseIndex)
        assertEquals(0L, skipped.restUntilMillis)
        assertEquals(2, skipped.entries.count { it.value.done })
    }

    @Test fun optionalExerciseCanBePerformedAndContributesActualSets() {
        val start = WorkoutDraft(dayId = "mon", startedAt = 0, daySnapshot = plan)
        val main1 = start.recordSet(plan, "25", "9", false, 1_000)
        val main2 = main1.recordSet(plan, "25", "9", false, 130_000)
        val recall1 = main2.recordSet(plan, "5", "15", false, 250_000)
        assertEquals(1, recall1.guidedExerciseIndex)
        assertEquals(2, recall1.guidedWorkSet)
        val skippedRemaining = recall1.skipOptionalExercise(plan)
        assertEquals(2, skippedRemaining.guidedExerciseIndex)
        assertTrue(skippedRemaining.entry(recall, 1).done)
        assertFalse(skippedRemaining.entry(recall, 2).done)
    }

    @Test fun cannotSkipMandatoryExercise() {
        val start = WorkoutDraft(dayId = "mon", startedAt = 0, daySnapshot = plan)
        try {
            start.skipOptionalExercise(plan)
            fail("Mandatory exercise should not be skippable")
        } catch (_: IllegalArgumentException) { }
    }

    @Test fun timeBasedExerciseKeepsSecondsAsRecordedValue() {
        val walk = Exercise("farmers_walk", "Farmer's walk", "AVAMBRACCI",
            2, "30–45 sec", 90, timeBased = true,
            primaryMuscles = listOf("forearms"))
        val fri = WorkoutDay("fri", "VENERDÌ", "Test", "", "90 min", listOf(walk))
        val result = WorkoutDraft("fri", 0).recordSet(fri, "18", "40", false, 1000)
        assertEquals("40", result.entry(walk, 1).reps)
        assertTrue(walk.timeBased)
    }
}
