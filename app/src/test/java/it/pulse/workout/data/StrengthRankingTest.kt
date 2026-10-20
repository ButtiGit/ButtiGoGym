package it.pulse.workout.data

import org.junit.Assert.*
import org.junit.Test

class StrengthRankingTest {
    private val exercise = Exercise(
        id = "bench", name = "Chest press", muscle = "PETTO",
        sets = 2, target = "8–12",
        primaryMuscles = listOf("chest"), secondaryMuscles = listOf("triceps"))
    private val day = WorkoutDay(
        id = "strength-test", weekday = "LUN", name = "Petto",
        description = "", duration = "30 min", exercises = listOf(exercise))

    private fun entry(kg: String, reps: String, warmup: Boolean = false,
                      done: Boolean = true) =
        SetEntry(kg, reps, done = done, warmup = warmup, completedAt = 100L)

    private fun record(vararg sets: Pair<String, SetEntry>): SessionRecord =
        SessionRecord(id = 100L, dayId = day.id, completedAt = 100L,
            durationMinutes = 30, completedSets = sets.size,
            totalSets = exercise.sets, daySnapshot = day,
            entries = sets.toMap())

    private fun rank(history: List<SessionRecord>, mass: Double? = 60.0,
                     group: String = "chest") =
        StrengthRanking.evaluate(history, mass).first { it.group.id == group }

    @Test fun allMusclesStartUnrankedUntilARealComparableSet() {
        val result = StrengthRanking.evaluate(emptyList(), 60.0)
        assertEquals(16, result.size)
        assertTrue(result.all { it.tier == null && it.ratio == null })
    }

    @Test fun multipleIdenticalSetsDoNotIncreaseRank() {
        val first = record("bench:1" to entry("45", "10"))
        val two = record("bench:1" to entry("45", "10"),
            "bench:2" to entry("45", "10"))
        assertEquals(rank(listOf(first)).ratio, rank(listOf(two)).ratio)
        assertEquals(rank(listOf(first)).tier, rank(listOf(first, first, first)).tier)
    }

    @Test fun heavierPerformanceCanIncreaseBadge() {
        val lighter = record("bench:1" to entry("15", "8"))
        val heavier = record("bench:1" to entry("65", "8"))
        assertTrue(rank(listOf(heavier)).ratio!! > rank(listOf(lighter)).ratio!!)
        assertTrue(StrengthRanking.tiers.indexOf(rank(listOf(heavier)).tier) >
            StrengthRanking.tiers.indexOf(rank(listOf(lighter)).tier))
    }

    @Test fun onlyPrimaryMuscleReceivesRank() {
        val trained = listOf(record("bench:1" to entry("50", "9")))
        assertNotNull(rank(trained, group = "chest").tier)
        assertNull(rank(trained, group = "triceps").tier)
    }

    @Test fun warmupsAndInvalidDataAreIgnored() {
        val invalid = listOf(record(
            "bench:1" to entry("85", "10", warmup = true),
            "bench:2" to entry("100", "25")))
        assertNull(rank(invalid).tier)
        assertNull(rank(listOf(record("bench:1" to entry("oops", "10")))).tier)
        assertNull(rank(listOf(record("bench:1" to entry("0", "10")))).tier)
    }

    @Test fun missingBodyweightDoesNotInventBenchmark() {
        val history = listOf(record("bench:1" to entry("55", "10")))
        assertNull(rank(history, mass = null).tier)
        assertFalse(StrengthRanking.hasBodyWeight(null))
    }

    @Test fun timeBasedExerciseIsNotCountedAsRepetitions() {
        val carry = exercise.copy(id = "farmers_walk", name = "Farmer walk",
            timeBased = true, primaryMuscles = listOf("forearms"))
        val exerciseDay = day.copy(exercises = listOf(carry))
        val timed = record("farmers_walk:1" to entry("50", "90"))
            .copy(daySnapshot = exerciseDay)
        assertNull(rank(listOf(timed), group = "forearms").tier)
    }

    @Test fun tiersHaveAscendingThresholds() {
        assertTrue(StrengthRanking.tiers.zipWithNext().all { (a, b) ->
            b.minRatio > a.minRatio
        })
        assertEquals("Bronzo", StrengthRanking.tierFor(.79).name)
        assertEquals("Leggenda", StrengthRanking.tierFor(2.4).name)
    }
}
