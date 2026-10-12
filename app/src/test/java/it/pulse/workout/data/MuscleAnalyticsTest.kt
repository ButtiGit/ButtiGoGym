package it.pulse.workout.data

import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.*
import org.junit.Test

class MuscleAnalyticsTest {
    private val zone = ZoneId.of("Europe/Rome")
    private fun time(date: String) = LocalDate.parse(date).atTime(19, 0).atZone(zone).toInstant().toEpochMilli()
    private val chest = Exercise(
        id = "bench", name = "Chest press", muscle = "PETTO",
        sets = 2, target = "8-12",
        primaryMuscles = listOf("chest"), secondaryMuscles = listOf("triceps")
    )
    private val day = WorkoutDay("fri", "VENERDÌ", "Chest", "", "60m", listOf(chest))
    private fun record(date: String, weight: String = "30", reps: String = "10",
                       warmup: Boolean = false, withSnapshot: Boolean = true): SessionRecord {
        val at = time(date)
        return SessionRecord(
            id = at, dayId = "fri", completedAt = at, durationMinutes = 50,
            completedSets = 1, totalSets = 2,
            daySnapshot = if (withSnapshot) day else null,
            entries = mapOf(
                "bench:1" to SetEntry(weight, reps, done = true, warmup = warmup, completedAt = at)
            )
        )
    }

    @Test fun noWorkoutsGivesZeroXpAndNoFakeStreak() {
        val stats = MuscleAnalytics.evaluate(emptyList(), time("2026-10-12"), zone)
        assertEquals(16, stats.size)
        assertTrue(stats.all { it.xp == 0 && it.level == 1 && it.streakWeeks == 0 })
        assertTrue(stats.all { it.state == MuscleRecovery.NEVER })
    }

    @Test fun warmupsDoNotContributeToXpOrStreak() {
        val stats = MuscleAnalytics.evaluate(
            listOf(record("2026-10-12", warmup = true)), time("2026-10-12") + 1000, zone)
        assertEquals(0, stats.first { it.group.id == "chest" }.xp)
    }

    @Test fun higherWeightAndRepsLeadToMoreXp() {
        assertTrue(MuscleAnalytics.xpForSet(60.0, 10, true) >
            MuscleAnalytics.xpForSet(20.0, 10, true))
        assertTrue(MuscleAnalytics.xpForSet(20.0, 15, true) >
            MuscleAnalytics.xpForSet(20.0, 8, true))
        assertTrue(MuscleAnalytics.xpForSet(20.0, 10, true) >
            MuscleAnalytics.xpForSet(20.0, 10, false))
    }

    @Test fun muscleStatsUsePrimaryAndSecondaryWithDifferentXp() {
        val result = MuscleAnalytics.evaluate(
            listOf(record("2026-10-12")), time("2026-10-12") + 1000, zone)
        val p = result.first { it.group.id == "chest" }
        val secondary = result.first { it.group.id == "triceps" }
        assertTrue(p.xp > secondary.xp)
        assertEquals(1, p.workingSets)
        assertEquals(0, secondary.workingSets)
        assertEquals(MuscleRecovery.TRAINED_TODAY, p.state)
        assertEquals(1, p.streakWeeks)
        assertEquals(300.0, p.volumeKg, 0.0001)
        assertNotNull(p.topSet)
    }

    @Test fun weeklyMuscleStreakContinuesWhenCurrentWeekIncomplete() {
        val today = LocalDate.parse("2026-10-12").atTime(12, 0).atZone(zone).toInstant().toEpochMilli()
        val result = MuscleAnalytics.evaluate(
            listOf(record("2026-10-09"), record("2026-10-02")),
            today, zone)
        assertEquals(2, result.first { it.group.id == "chest" }.streakWeeks)
    }

    @Test fun recoveryElapsedShowsReady() {
        val current = time("2026-10-12")
        val old = record("2026-10-09")
        val result = MuscleAnalytics.evaluate(listOf(old), current, zone)
        assertEquals(MuscleRecovery.READY, result.first { it.group.id == "chest" }.state)
        assertEquals(0, result.first { it.group.id == "chest" }.hoursToReady)
    }

    @Test fun nextLevelProgressIsMonotonic() {
        val low = MuscleAnalytics.levelFromXp(0)
        val middle = MuscleAnalytics.levelFromXp(150)
        val high = MuscleAnalytics.levelFromXp(2000)
        assertEquals(1, low)
        assertTrue(middle >= low)
        assertTrue(high > middle)
        assertTrue(MuscleAnalytics.levelThreshold(high + 1) > MuscleAnalytics.levelThreshold(high))
    }

    @Test fun historicWorkoutWithoutSnapshotUsesLegacyPlan() {
        val stats = MuscleAnalytics.evaluate(
            listOf(record("2026-10-12", withSnapshot = false)), time("2026-10-12") + 1000, zone)
        assertTrue(stats.first { it.group.id == "chest" }.xp > 0)
    }
}
