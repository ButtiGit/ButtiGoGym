package it.pulse.workout.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class TrainingAnalyticsTest {
    private val utc = ZoneId.of("UTC")
    private val bench = Exercise("bench", "Panca", "PETTO", 3, "8–12",
        primaryMuscles = listOf("chest"), secondaryMuscles = listOf("triceps"))
    private val row = Exercise("row", "Rematore", "SCHIENA", 3, "8–12",
        primaryMuscles = listOf("upper_back", "lats"),
        secondaryMuscles = listOf("biceps"))
    private val carry = Exercise("farmers_walk", "Farmer", "AVAMBRACCI",
        2, "40 sec", timeBased = true, primaryMuscles = listOf("forearms"))
    private val day = WorkoutDay("fri", "VEN", "Petto e schiena", "", "60 min",
        listOf(bench, row, carry))

    private fun at(date: String): Long =
        LocalDate.parse(date).atStartOfDay(utc).toInstant().toEpochMilli()

    private fun record(date: String, id: Long, vararg sets: Pair<String, SetEntry>): SessionRecord =
        SessionRecord(
            id = id, dayId = "fri", completedAt = at(date),
            durationMinutes = 50, completedSets = sets.size,
            totalSets = 8, entries = mapOf(*sets), daySnapshot = day
        )

    private fun logged(weight: String, reps: String,
        done: Boolean = true, warmup: Boolean = false): SetEntry =
        SetEntry(weight = weight, reps = reps, done = done, warmup = warmup)

    @Test fun onlyCompletedWorkingSetsCountAndDecimalsAreSupported() {
        val saved = record("2026-10-12", 1L,
            "bench:1" to logged("42,5", "10"),
            "bench:2" to logged("50", "8", done = false),
            "bench:3" to logged("20", "10", warmup = true),
            "warmup:bench:1" to logged("20", "12", warmup = true),
            "row:1" to logged("30", "12"),
            "farmers_walk:1" to logged("25", "40"),
            "row:2" to logged("-10", "10"),
            "row:3" to logged("Infinity", "9")
        )
        val sets = TrainingAnalytics.completedSets(listOf(saved), utc)
        assertEquals(2, sets.size)
        assertEquals(785.0, sets.sumOf { it.volumeKg }, 0.0001)
        assertEquals(1, sets.count { it.exerciseId == "bench" })
        assertFalse(sets.any { "forearms" in it.primaryMuscles })
    }

    @Test fun personalRecordsTrackWeightEstimatedStrengthAndBestSessionTonnage() {
        val first = record("2026-10-05", 1L,
            "bench:1" to logged("40", "10"),
            "bench:2" to logged("50", "8"))
        val second = record("2026-10-12", 2L,
            "bench:1" to logged("60", "5"),
            "bench:2" to logged("45", "12"))
        val summary = TrainingAnalytics.exerciseRecords(listOf(first, second), "bench")!!
        assertEquals(60.0, summary.bestWeightKg!!, 0.001)
        assertEquals(70.0, summary.bestEstimated1RmKg!!, 0.001) // 60*(1+5/30)
        assertEquals(12, summary.bestRepsAtAnyWeight)
        assertEquals(840.0, summary.bestSessionVolumeKg, 0.001)
        assertEquals(4, summary.totalWorkingSets)
        assertEquals(2, summary.trainedSessions)
        assertEquals(1640.0, summary.totalVolumeKg, 0.001)
    }

    @Test fun snapshotsKeepOldExerciseNamesAndIgnoreEditedPlans() {
        val oldDay = day.copy(exercises = listOf(bench.copy(name = "Panca originale")))
        val session = record("2026-10-12", 3L,
            "bench:1" to logged("40", "8")).copy(daySnapshot = oldDay)
        val sets = TrainingAnalytics.completedSets(listOf(session), utc)
        assertEquals("Panca originale", sets.single().exerciseName)
        assertEquals("Panca originale",
            TrainingAnalytics.exerciseOptions(listOf(session)).single().name)
    }

    @Test fun primaryMusclesOnlyAreCountedSecondaryNotDuplicated() {
        val session = record("2026-10-12", 5L,
            "bench:1" to logged("40", "10"),
            "row:1" to logged("50", "8"))
        val chest = TrainingAnalytics.muscleSummary(listOf(session), "chest")!!
        val triceps = TrainingAnalytics.muscleSummary(listOf(session), "triceps")
        val lats = TrainingAnalytics.muscleSummary(listOf(session), "lats")!!
        val upper = TrainingAnalytics.muscleSummary(listOf(session), "upper_back")!!
        assertEquals(400.0, chest.totalVolumeKg, 0.001)
        assertNull(triceps)
        assertEquals(400.0, lats.totalVolumeKg, 0.001)
        assertEquals(400.0, upper.totalVolumeKg, 0.001)
        // Ogni muscolo singolarmente conta una volta per serie.
        assertEquals(1, lats.totalWorkingSets)
    }

    @Test fun weeksStartOnMondayAndEmptyWeeksArePreserved() {
        val session = record("2026-10-07", 1L,
            "bench:1" to logged("40", "10"))
        val points = TrainingAnalytics.trend(
            listOf(session), AnalyticsPeriod.WEEK, exerciseId = "bench",
            now = LocalDate.of(2026, 10, 14), zone = utc)
        assertEquals(8, points.size)
        assertEquals(LocalDate.of(2026, 10, 12), points.last().start)
        assertEquals(LocalDate.of(2026, 10, 5), points[points.lastIndex - 1].start)
        assertEquals(400.0, points[points.lastIndex - 1].volumeKg, 0.001)
        assertEquals(0.0, points.last().volumeKg, 0.001)
        assertEquals(-100.0,
            TrainingAnalytics.compareRecent(points, AnalyticsMeasure.VOLUME)!!
                .percentageChange!!, 0.001)
    }

    @Test fun monthlyBucketsIncludeZeroMonthsAndNeverIncludeFutureWorkouts() {
        val september = record("2026-09-14", 3L,
            "bench:1" to logged("40", "8"))
        val future = record("2026-11-12", 4L,
            "bench:1" to logged("100", "10"))
        val points = TrainingAnalytics.trend(
            listOf(september, future), AnalyticsPeriod.MONTH, exerciseId = "bench",
            now = LocalDate.of(2026, 10, 14), zone = utc)
        assertEquals(6, points.size)
        assertEquals(LocalDate.of(2026, 10, 1), points.last().start)
        assertEquals(320.0, points[points.lastIndex - 1].volumeKg, 0.001)
        assertEquals(0.0, points.last().volumeKg, 0.001)
    }

    @Test fun strengthIsEstimatedPerBucketButNotForTimedOrZeroWeightSets() {
        val session = record("2026-10-12", 2L,
            "bench:1" to logged("50", "10"),
            "bench:2" to logged("0", "20"),
            "bench:3" to logged("100", "40"))
        val points = TrainingAnalytics.trend(
            listOf(session), AnalyticsPeriod.WEEK, exerciseId = "bench",
            now = LocalDate.of(2026, 10, 12), zone = utc)
        assertEquals(50.0 * (1.0 + 10.0 / 30.0),
            points.last().estimatedStrengthKg!!, 0.001)
        val noData = TrainingAnalytics.trend(
            listOf(session), AnalyticsPeriod.WEEK, exerciseId = "row",
            now = LocalDate.of(2026, 10, 12), zone = utc)
        assertNull(noData.last().estimatedStrengthKg)
    }

    @Test fun changesAreUndefinedIfPreviousPeriodWasZero() {
        val session = record("2026-10-12", 1L,
            "bench:1" to logged("40", "10"))
        val points = TrainingAnalytics.trend(
            listOf(session), AnalyticsPeriod.WEEK,
            exerciseId = "bench", now = LocalDate.of(2026, 10, 14), zone = utc)
        val comparison = TrainingAnalytics.compareRecent(points, AnalyticsMeasure.VOLUME)!!
        assertNull(comparison.percentageChange)
        assertEquals(400.0, comparison.current, 0.001)
    }

    @Test fun noHistoryGivesNoInventedRecordsAndZeroValueCharts() {
        assertNull(TrainingAnalytics.exerciseRecords(emptyList(), "bench"))
        assertTrue(TrainingAnalytics.exerciseOptions(emptyList()).isEmpty())
        val series = TrainingAnalytics.trend(
            emptyList(), AnalyticsPeriod.WEEK, exerciseId = "bench",
            now = LocalDate.of(2026, 10, 14), zone = utc)
        assertEquals(8, series.size)
        assertTrue(series.all { it.volumeKg == 0.0 && it.workingSets == 0 })
    }
}
