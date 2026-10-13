package it.pulse.workout.data

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class MuscleBalanceAnalyticsTest {
    private val zone = ZoneId.of("Europe/Rome")
    private fun at(day: String): Long =
        LocalDate.parse(day).atTime(19, 0).atZone(zone).toInstant().toEpochMilli()

    private fun session(day: String, exercise: Exercise = press, weight: String = "30",
                        reps: String = "10", done: Boolean = true, warmup: Boolean = false): SessionRecord {
        val t = at(day)
        val plan = WorkoutDay("custom", "TEST", "Test", "", "30m", listOf(exercise))
        return SessionRecord(
            id = t, dayId = "custom", completedAt = t, durationMinutes = 30,
            completedSets = if (done) 1 else 0, totalSets = 1,
            entries = mapOf(WorkoutDraft.key(exercise.id, 1) to
                SetEntry(weight, reps, done, warmup, t)),
            daySnapshot = plan
        )
    }

    companion object {
        private val press = Exercise(
            "press_test", "Chest press", "PETTO", 1, "8-12",
            primaryMuscles = listOf("chest"), secondaryMuscles = listOf("triceps")
        )
    }

    @Test fun recentDirectAndIndirectOnlyUseRealSetsInRollingWindows() {
        val now = at("2026-10-13")
        val report = MuscleAnalytics.analyze(listOf(
            session("2026-10-12"), session("2026-10-03"), session("2026-09-20"),
            session("2026-09-10"), session("2026-10-10", done = false),
            session("2026-10-09", warmup = true)
        ), now, zone)
        val chest = report.muscles.first { it.group.id == "chest" }
        val triceps = report.muscles.first { it.group.id == "triceps" }
        assertEquals(1, chest.directSets7Days)
        assertEquals(3, chest.directSets28Days)
        assertEquals(0, chest.indirectSets28Days)
        assertEquals(0, triceps.directSets28Days)
        assertEquals(1, triceps.indirectSets7Days)
        assertEquals(3, triceps.indirectSets28Days)
        assertEquals(3, report.balance.sessions28Days)
        assertEquals(3, report.balance.pushDirectSets)
        assertEquals(0, report.balance.pullDirectSets)
        assertTrue(report.balance.enoughHistoryForGaps)
        assertTrue("Forearms has no stimulation", "Avambracci" in report.balance.unstimulatedGroups)
        assertFalse("Triceps has indirect work", "Tricipiti" in report.balance.unstimulatedGroups)
    }

    @Test fun gapsAreNotInferredFromSparseOrEmptyHistory() {
        val none = MuscleAnalytics.analyze(emptyList(), at("2026-10-13"), zone).balance
        val one = MuscleAnalytics.analyze(
            listOf(session("2026-10-12")), at("2026-10-13"), zone).balance
        assertFalse(none.enoughHistoryForGaps)
        assertFalse(one.enoughHistoryForGaps)
        assertTrue(one.unstimulatedGroups.isEmpty())
        assertEquals(0, none.sessions28Days)
    }

    @Test fun futureSessionsDoNotContribute() {
        val result = MuscleAnalytics.analyze(
            listOf(session("2026-10-14")), at("2026-10-13"), zone)
        assertEquals(0, result.balance.sessions28Days)
        assertEquals(0, result.muscles.first { it.group.id == "chest" }.xp)
    }

    @Test fun timeBasedCarryIsNotMisinterpretedAsStrengthOrVolume() {
        val carry = Exercise(
            "carry_test", "Farmer's walk", "AVAMBRACCI", 1, "40 s",
            timeBased = true, primaryMuscles = listOf("forearms")
        )
        val result = MuscleAnalytics.analyze(
            listOf(session("2026-10-12", carry, "20", "40")), at("2026-10-13"), zone)
        val forearms = result.muscles.first { it.group.id == "forearms" }
        assertEquals(1, forearms.directSets7Days)
        assertEquals(1, forearms.workingSets)
        assertEquals(0.0, forearms.volumeKg, 0.001)
        assertNull(forearms.bestEstimatedStrengthKg)
        assertNull(forearms.topSet)
    }

    @Test fun estimatedStrengthUsesOnlyPositiveRepetitionBasedSets() {
        val result = MuscleAnalytics.analyze(
            listOf(session("2026-10-12")), at("2026-10-13"), zone)
        val chest = result.muscles.first { it.group.id == "chest" }
        assertEquals(40.0, chest.bestEstimatedStrengthKg!!, 0.001)
        assertEquals(300.0, chest.volumeKg, 0.001)
        val heavyReps = MuscleAnalytics.analyze(
            listOf(session("2026-10-12", reps = "50")), at("2026-10-13"), zone)
        assertNull(heavyReps.muscles.first { it.group.id == "chest" }.bestEstimatedStrengthKg)
    }
}
