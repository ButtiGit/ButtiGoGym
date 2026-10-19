package it.pulse.workout.data

import org.junit.Assert.*
import org.junit.Test

class GuidedSetStepTest {
    @Test fun weightCanAdvanceBeforeRepsButInvalidValuesCannot() {
        for (weight in listOf("", "-1", "NaN", "Infinity", "10001", "2..5")) {
            val input = QuickSetValues(weight = weight)
            assertFalse(weight, GuidedSetStep.WEIGHT.accepts(input))
            assertEquals(GuidedSetStep.WEIGHT, GuidedSetStep.WEIGHT.next(input))
        }
        assertEquals(GuidedSetStep.REPS, GuidedSetStep.WEIGHT.next(QuickSetValues(weight = "0")))
        assertTrue(GuidedSetStep.WEIGHT.accepts(QuickSetValues(weight = "32,5")))
    }

    @Test fun workingSetRequiresExplicitReviewAndAllowsUnknownEffort() {
        val input = QuickSetValues("32,5", "10")
        assertEquals(GuidedSetStep.EFFORT, GuidedSetStep.REPS.next(input))
        assertTrue(GuidedSetStep.EFFORT.accepts(input))
        assertEquals(GuidedSetStep.REVIEW, GuidedSetStep.EFFORT.next(input))
        assertTrue(GuidedSetStep.REVIEW.accepts(input))
        assertEquals(GuidedSetStep.EFFORT, GuidedSetStep.REVIEW.previous(input))
    }

    @Test fun warmupSkipsEffortInBothDirections() {
        val input = QuickSetValues("10", "8", warmup = true)
        assertEquals(GuidedSetStep.REVIEW, GuidedSetStep.REPS.next(input))
        assertEquals(GuidedSetStep.REPS, GuidedSetStep.REVIEW.previous(input))
    }

    @Test fun reviewRejectsInvalidRepsOrEffortEvenAfterNavigation() {
        for (input in listOf(QuickSetValues("0", "0"), QuickSetValues("0", "1000"),
            QuickSetValues("0", "5", actualRir = 6), QuickSetValues("", "5"))) {
            assertFalse(GuidedSetStep.REVIEW.accepts(input))
        }
        assertEquals(GuidedSetStep.REPS, GuidedSetStep.REPS.next(QuickSetValues("0", "")))
    }
}
