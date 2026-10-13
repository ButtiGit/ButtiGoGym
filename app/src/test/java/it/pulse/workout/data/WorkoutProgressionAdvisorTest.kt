package it.pulse.workout.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test

class WorkoutProgressionAdvisorTest {
    private val exercise = Exercise("bench", "Chest press", "PETTO", 3, "8–12",
        restSeconds = 90, rir = "1–3")
    private val day = WorkoutDay("fri", "VEN", "Petto", "", "50 min",
        exercises = listOf(exercise))
    private fun history(
        weights: List<String> = listOf("40", "40", "40"),
        reps: List<String> = listOf("12", "12", "12"),
        rirs: List<Int?> = listOf(2, 3, 2),
        sourceDay: WorkoutDay = day,
        completed: Long = 10_000L
    ): List<SessionRecord> {
        val entries = weights.indices.associate { i ->
            WorkoutDraft.key(exercise.id, i + 1) to
                SetEntry(weight = weights[i], reps = reps[i], done = true,
                    completedAt = completed - (weights.size - i) * 100L, rir = rirs[i])
        }
        return listOf(SessionRecord(completed, sourceDay.id, completed,
            30, weights.size, weights.size, entries = entries, daySnapshot = sourceDay))
    }

    @Test fun increaseOnlyWhenEverySeriesAtUpperLimitWithMeasuredRir() {
        val tip = WorkoutProgressionAdvisor.advise(exercise, "fri", history())
        assertEquals(ProgressionDirection.CONSIDER_INCREASE, tip.direction)
        assertEquals(2.5, tip.increaseStepKg!!, 0.001)
        assertEquals(42.5, tip.suggestedWeight(1)!!, 0.001)
        assertEquals(3, tip.previousSets.size)
        assertEquals(2, tip.previous(1)?.actualRir)
    }

    @Test fun noAutomaticIncreaseWithoutActualRirDespiteHighReps() {
        val tip = WorkoutProgressionAdvisor.advise(exercise, "fri",
            history(rirs = listOf(null, null, null)))
        assertEquals(ProgressionDirection.KEEP_WEIGHT, tip.direction)
        assertNull(tip.suggestedWeight(1))
        assertTrue(tip.explanation.contains("RIR"))
    }

    @Test fun noIncreaseIfRirWasNearFailure() {
        val tip = WorkoutProgressionAdvisor.advise(exercise, "fri",
            history(rirs = listOf(0, 1, 2)))
        assertEquals(ProgressionDirection.KEEP_WEIGHT, tip.direction)
        assertNull(tip.increaseStepKg)
    }

    @Test fun addRepetitionBeforeConsideringWeight() {
        val tip = WorkoutProgressionAdvisor.advise(exercise, "fri",
            history(reps = listOf("8", "10", "11")))
        assertEquals(ProgressionDirection.ADD_REPETITIONS, tip.direction)
        assertEquals(9, tip.nextRepetitionTarget)
        assertNull(tip.suggestedWeight(1))
    }

    @Test fun lowRepsAndNearFailureRequiresEffortReview() {
        val tip = WorkoutProgressionAdvisor.advise(exercise, "fri",
            history(reps = listOf("6", "9", "9"), rirs = listOf(0, 2, 2)))
        assertEquals(ProgressionDirection.REVIEW_EFFORT, tip.direction)
        assertNull(tip.increaseStepKg)
    }

    @Test fun avoidWeightProgressionOnBodyweightOrDurationExercises() {
        val bodyweight = WorkoutProgressionAdvisor.advise(exercise, "fri",
            history(weights = listOf("0", "0", "0")))
        assertEquals(ProgressionDirection.KEEP_WEIGHT, bodyweight.direction)

        val carry = exercise.copy(id = "carry", name = "Farmer carry",
            timeBased = true, target = "30–45")
        val carryDay = day.copy(exercises = listOf(carry))
        val carryHistory = listOf(SessionRecord(
            20_000L, "fri", 20_000L, 12, 3, 3,
            entries = (1..3).associate {
                WorkoutDraft.key("carry", it) to SetEntry(
                    "25", "45", done = true, rir = 3)
            }, daySnapshot = carryDay
        ))
        val tip = WorkoutProgressionAdvisor.advise(carry, "fri", carryHistory)
        assertEquals(ProgressionDirection.TIME_BASED, tip.direction)
        assertNull(tip.suggestedWeight(1))
    }

    @Test fun missingAndUnrelatedDataMustNotBeFabricated() {
        assertEquals(ProgressionDirection.FIRST_SESSION,
            WorkoutProgressionAdvisor.advise(exercise, "fri", emptyList()).direction)
        assertEquals(ProgressionDirection.FIRST_SESSION,
            WorkoutProgressionAdvisor.advise(exercise, "mon", history()).direction)
        val changedId = exercise.copy(id = "other")
        assertEquals(ProgressionDirection.FIRST_SESSION,
            WorkoutProgressionAdvisor.advise(changedId, "fri", history()).direction)
    }

    @Test fun ignoreIncompleteWarmupAndDifferentExerciseTypes() {
        val incomplete = WorkoutProgressionAdvisor.advise(exercise, "fri",
            history(weights = listOf("40"), reps = listOf("12"), rirs = listOf(3)))
        assertFalse(incomplete.direction == ProgressionDirection.CONSIDER_INCREASE)
        val asTimed = WorkoutProgressionAdvisor.advise(
            exercise.copy(timeBased = true), "fri", history())
        assertEquals(ProgressionDirection.FIRST_SESSION, asTimed.direction)
    }

    @Test fun supportsIntervalsWithMultipleDashVariantsAndRefusesFreeText() {
        assertEquals(8..12, WorkoutProgressionAdvisor.repetitionRange("8–12"))
        assertEquals(8..12, WorkoutProgressionAdvisor.repetitionRange("8-12"))
        assertEquals(6..10, WorkoutProgressionAdvisor.repetitionRange("6—10"))
        assertEquals(10..10, WorkoutProgressionAdvisor.repetitionRange("10"))
        assertNull(WorkoutProgressionAdvisor.repetitionRange("AMRAP"))
        assertNull(WorkoutProgressionAdvisor.repetitionRange("12–8"))
    }

    @Test fun actualRirCanBeRecordedAndCorrectedButNotPredeclaredOnWarmup() {
        val started = WorkoutDraft("fri", 100L, daySnapshot = day)
        val recorded = started.recordSet(day, "40", "10", warmup = false,
            at = 1000L, rir = 2)
        assertEquals(2, recorded.entry(exercise, 1).rir)
        val corrected = recorded.correctRecordedSet(day, "bench", 1,
            warmup = false, weight = "42", reps = "9", rir = 1)
        assertEquals(1, corrected.entry(exercise, 1).rir)
        assertEquals(1000L, corrected.entry(exercise, 1).completedAt)
        val undone = corrected.undoLastRecordedSet(day)
        assertFalse(undone.entry(exercise, 1).done)
        val warming = started.recordSet(day, "20", "10", warmup = true,
            at = 1000L, rir = 5)
        assertNull(warming.warmups(exercise).first().rir)
        assertThrows(IllegalArgumentException::class.java) {
            started.recordSet(day, "40", "10", false, 1000L, rir = 6)
        }
    }
}
