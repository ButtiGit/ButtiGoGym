package it.pulse.workout.data

import kotlin.math.roundToInt

/** Statistiche ricavate dalle serie davvero completate, senza dati inventati. */
data class WorkoutRecapStats(
    val volumeKg: Double,
    val durationSeconds: Int,
    val estimatedCalories: Int,
    val workingSets: Int
)

object WorkoutRecapCalculator {
    // MET 5.0 = stima indicativa di una seduta pesi moderata/intensa; non e un sensore calorico.
    private const val WEIGHTS_MET = 5.0
    private const val DEFAULT_BODY_MASS_KG = 58.0

    fun calculate(
        record: SessionRecord,
        day: WorkoutDay? = record.daySnapshot,
        bodyMassKg: Double = DEFAULT_BODY_MASS_KG
    ): WorkoutRecapStats {
        val exerciseById = day?.exercises?.associateBy { it.id }.orEmpty()
        val valid = record.entries.filter { (key, entry) ->
            entry.done && !entry.warmup && !key.startsWith("warmup:") &&
                key.substringAfterLast(':').toIntOrNull() != null
        }
        var volume = 0.0
        valid.forEach { (key, entry) ->
            val id = key.substringBeforeLast(':')
            if (exerciseById[id]?.timeBased == true || id == "farmers_walk") return@forEach
            val kg = entry.weight.trim().replace(',', '.').toDoubleOrNull() ?: return@forEach
            val reps = entry.reps.toIntOrNull() ?: return@forEach
            if (kg.isFinite() && kg >= 0.0 && reps in 1..999) volume += kg * reps
        }
        val seconds = record.durationSeconds.coerceAtLeast(0)
        val safeMass = bodyMassKg.takeIf { it.isFinite() && it > 0 } ?: DEFAULT_BODY_MASS_KG
        val calories = (WEIGHTS_MET * 3.5 * safeMass / 200.0 * seconds / 60.0)
            .roundToInt().coerceAtLeast(0)
        return WorkoutRecapStats(
            volumeKg = volume,
            durationSeconds = seconds,
            estimatedCalories = calories,
            workingSets = valid.size
        )
    }
}
