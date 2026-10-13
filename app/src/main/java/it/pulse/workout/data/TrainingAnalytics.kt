package it.pulse.workout.data

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.DayOfWeek
import java.time.temporal.TemporalAdjusters
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Serie completate e verificabili: mai contare riscaldamenti, timer o serie soltanto precompilate. */
data class AnalyticsSet(
    val sessionId: Long,
    val at: LocalDate,
    val exerciseId: String,
    val exerciseName: String,
    val primaryMuscles: Set<String>,
    val kg: Double,
    val reps: Int
) {
    val volumeKg: Double get() = kg * reps
    /** Epley è solo una stima, significativa su serie positive fino a 30 ripetizioni. */
    val estimatedOneRepMaxKg: Double?
        get() = if (kg > 0.0 && reps in 1..30) kg * (1.0 + reps / 30.0) else null
}

enum class AnalyticsPeriod { WEEK, MONTH }
enum class AnalyticsMeasure { VOLUME, WORKING_SETS, ESTIMATED_STRENGTH }

data class AnalyticsPoint(
    val start: LocalDate,
    val label: String,
    val volumeKg: Double,
    val workingSets: Int,
    val estimatedStrengthKg: Double?
) {
    fun value(measure: AnalyticsMeasure): Double = when (measure) {
        AnalyticsMeasure.VOLUME -> volumeKg
        AnalyticsMeasure.WORKING_SETS -> workingSets.toDouble()
        AnalyticsMeasure.ESTIMATED_STRENGTH -> estimatedStrengthKg ?: 0.0
    }
}

data class AnalyticsExercise(val id: String, val name: String)

data class ExercisePersonalRecords(
    val bestWeightKg: Double?,
    val bestEstimated1RmKg: Double?,
    val bestRepsAtAnyWeight: Int?,
    val bestSessionVolumeKg: Double,
    val totalVolumeKg: Double,
    val totalWorkingSets: Int,
    val trainedSessions: Int
)

data class AnalyticsComparison(val current: Double, val previous: Double) {
    /** null quando prima non c'è un valore di confronto significativo. */
    val percentageChange: Double? get() =
        if (previous > 0.0) ((current - previous) / previous) * 100.0 else null
}

/**
 * Analisi puramente locale e deterministica. Gli exercise ID delle snapshot
 * impediscono di attribuire esercizi rinominati ad altri movimenti.
 * Per i muscoli si conta il VOLUME SOLTANTO per muscoli primari (nessun
 * doppio conteggio dello stesso set nello stesso muscolo).
 */
object TrainingAnalytics {
    private val italian = Locale.ITALIAN

    fun completedSets(
        history: List<SessionRecord>,
        zone: ZoneId = ZoneId.systemDefault()
    ): List<AnalyticsSet> = history.flatMap { session ->
        val day = session.daySnapshot
            ?: WorkoutPlan.days.firstOrNull { it.id == session.dayId }
            ?: return@flatMap emptyList()
        val date = Instant.ofEpochMilli(session.completedAt).atZone(zone).toLocalDate()
        day.exercises.filterNot { it.timeBased }.flatMap { ex ->
            val primary = ExerciseMuscleMapping.forExercise(ex).first
                .filter { it in MuscleGroup.ids }.toSet()
            (1..ex.sets).mapNotNull { index ->
                val entry = session.entries[WorkoutDraft.key(ex.id, index)]
                    ?: return@mapNotNull null
                if (!entry.done || entry.warmup) return@mapNotNull null
                val kg = entry.weight.trim().replace(',', '.').toDoubleOrNull()
                    ?: return@mapNotNull null
                val reps = entry.reps.trim().toIntOrNull() ?: return@mapNotNull null
                if (!kg.isFinite() || kg !in 0.0..10_000.0 || reps !in 1..999)
                    return@mapNotNull null
                AnalyticsSet(session.id, date, ex.id, ex.name, primary, kg, reps)
            }
        }
    }

    fun exerciseOptions(history: List<SessionRecord>): List<AnalyticsExercise> {
        val seen = LinkedHashMap<String, AnalyticsExercise>()
        history.sortedByDescending { it.completedAt }.forEach { session ->
            val day = session.daySnapshot
                ?: WorkoutPlan.days.firstOrNull { it.id == session.dayId }
                ?: return@forEach
            day.exercises.filterNot { it.timeBased }.forEach { ex ->
                if (seen[ex.id] == null) seen[ex.id] = AnalyticsExercise(ex.id, ex.name)
            }
        }
        return seen.values.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
    }

    fun exerciseRecords(history: List<SessionRecord>, exerciseId: String): ExercisePersonalRecords? {
        val sets = completedSets(history).filter { it.exerciseId == exerciseId }
        if (sets.isEmpty()) return null
        return ExercisePersonalRecords(
            bestWeightKg = sets.map { it.kg }.filter { it > 0.0 }.maxOrNull(),
            bestEstimated1RmKg = sets.mapNotNull { it.estimatedOneRepMaxKg }.maxOrNull(),
            bestRepsAtAnyWeight = sets.map { it.reps }.maxOrNull(),
            bestSessionVolumeKg = sets.groupBy { it.sessionId }.values.maxOf { series ->
                series.sumOf { it.volumeKg }
            },
            totalVolumeKg = sets.sumOf { it.volumeKg },
            totalWorkingSets = sets.size,
            trainedSessions = sets.map { it.sessionId }.distinct().size
        )
    }

    fun trend(
        history: List<SessionRecord>,
        period: AnalyticsPeriod,
        exerciseId: String? = null,
        muscleId: String? = null,
        now: LocalDate = LocalDate.now(),
        zone: ZoneId = ZoneId.systemDefault()
    ): List<AnalyticsPoint> {
        require(exerciseId == null || muscleId == null) { "Seleziona esercizio o muscolo" }
        val starts = bucketStarts(period, now)
        val valid = completedSets(history, zone).filter { series ->
            (exerciseId == null || series.exerciseId == exerciseId) &&
                (muscleId == null || muscleId in series.primaryMuscles) &&
                !series.at.isAfter(now)
        }
        val grouped = valid.groupBy { bucketStart(it.at, period) }
        return starts.map { start ->
            val samples = grouped[start].orEmpty()
            AnalyticsPoint(
                start = start,
                label = when (period) {
                    AnalyticsPeriod.WEEK -> start.format(
                        DateTimeFormatter.ofPattern("dd MMM", italian))
                    AnalyticsPeriod.MONTH -> start.format(
                        DateTimeFormatter.ofPattern("MMM yy", italian))
                },
                volumeKg = samples.sumOf { it.volumeKg },
                workingSets = samples.size,
                estimatedStrengthKg = if (exerciseId != null)
                    samples.mapNotNull { it.estimatedOneRepMaxKg }.maxOrNull()
                else null
            )
        }
    }

    /** Totali per gruppo su un solo periodo, senza sommare i secondari. */
    fun muscleSummary(history: List<SessionRecord>, muscleId: String): ExercisePersonalRecords? {
        require(muscleId in MuscleGroup.ids)
        val sets = completedSets(history).filter { muscleId in it.primaryMuscles }
        if (sets.isEmpty()) return null
        return ExercisePersonalRecords(
            bestWeightKg = null,
            bestEstimated1RmKg = null,
            bestRepsAtAnyWeight = null,
            bestSessionVolumeKg = sets.groupBy { it.sessionId }
                .values.maxOf { it.sumOf { series -> series.volumeKg } },
            totalVolumeKg = sets.sumOf { it.volumeKg },
            totalWorkingSets = sets.size,
            trainedSessions = sets.map { it.sessionId }.distinct().size
        )
    }

    fun compareRecent(points: List<AnalyticsPoint>, measure: AnalyticsMeasure):
        AnalyticsComparison? =
        if (points.size < 2) null else AnalyticsComparison(
            points[points.lastIndex].value(measure),
            points[points.lastIndex - 1].value(measure)
        )

    fun bucketStart(date: LocalDate, period: AnalyticsPeriod): LocalDate = when (period) {
        AnalyticsPeriod.WEEK -> date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        AnalyticsPeriod.MONTH -> date.withDayOfMonth(1)
    }

    fun bucketStarts(period: AnalyticsPeriod, now: LocalDate): List<LocalDate> {
        val current = bucketStart(now, period)
        return when (period) {
            AnalyticsPeriod.WEEK -> (7 downTo 0).map { current.minusWeeks(it.toLong()) }
            AnalyticsPeriod.MONTH -> (5 downTo 0).map { current.minusMonths(it.toLong()) }
        }
    }
}
