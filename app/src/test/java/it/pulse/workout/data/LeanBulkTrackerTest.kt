package it.pulse.workout.data

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class LeanBulkTrackerTest {
    private val today = LocalDate.of(2026, 10, 15)
    private fun sample(base: Double, rise: Double): List<BodyProgressEntry> =
        (0..2).flatMap { week ->
            listOf(0, 2, 4).map { offset ->
                BodyProgressEntry(today.minusDays(((2 - week) * 7 + offset).toLong()),
                    weightKg = base + week * rise)
            }
        }

    @Test fun usesThreeCompleteWeeksBeforeEstimatingTrend() {
        val r = LeanBulkTracker.analyze(sample(58.0, 0.2), today)
        assertEquals(58.4, r.sevenDayAverageKg!!, 0.0001)
        assertEquals(0.2, r.averageWeeklyChangeKg!!, 0.0001)
        assertEquals(BulkTrend.ON_TRACK, r.trend)
        assertNull(r.suggestedKcal)
    }

    @Test fun rejectsSparseReadingsAndOutdatedWeights() {
        val sparse = listOf(BodyProgressEntry(today, 58.1),
            BodyProgressEntry(today.minusDays(2), 58.2))
        assertEquals(BulkTrend.NEED_MORE_DATA, LeanBulkTracker.analyze(sparse, today).trend)
        assertNull(LeanBulkTracker.analyze(sparse, today).sevenDayAverageKg)
        assertEquals(BulkTrend.NEED_MORE_DATA,
            LeanBulkTracker.analyze(sample(58.0, .2).map {
                it.copy(date = it.date.minusMonths(3))
            }, today).trend)
    }

    @Test fun calorieSuggestionIsNeverMoreThanOneHundredKcal() {
        val slow = LeanBulkTracker.analyze(sample(58.0, .02), today)
        val fast = LeanBulkTracker.analyze(sample(58.0, .6), today)
        assertEquals(BulkTrend.SLOW, slow.trend)
        assertEquals(2550, slow.suggestedKcal)
        assertEquals(BulkTrend.FAST, fast.trend)
        assertEquals(2350, fast.suggestedKcal)
    }

    @Test fun ignoresFutureMeasurements() {
        val r = LeanBulkTracker.analyze(sample(58.0, .2) +
            BodyProgressEntry(today.plusDays(2), 100.0), today)
        assertTrue(r.latestWeightKg!! < 100.0)
    }

    @Test fun progressIsClampedAndTargetsRemainConsistent() {
        assertEquals(0f, LeanBulkTracker.analyze(
            listOf(BodyProgressEntry(today, 55.0)), today).progress)
        assertEquals(1f, LeanBulkTracker.analyze(
            listOf(BodyProgressEntry(today, 70.0)), today).progress)
        assertEquals(2450, LeanBulkTargets.INITIAL_KCAL)
        assertEquals(120, LeanBulkTargets.PROTEIN_G)
        assertEquals(335, LeanBulkTargets.CARBS_G)
        assertEquals(70, LeanBulkTargets.FAT_G)
    }
}
