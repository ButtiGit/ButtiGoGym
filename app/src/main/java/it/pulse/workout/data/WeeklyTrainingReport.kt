package it.pulse.workout.data

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

data class WeeklyTrainingReport(
    val monday: LocalDate,
    val sunday: LocalDate,
    val completedDays: Int,
    val goalDays: Int,
    val completedSets: Int,
    val quickSessions: Int,
    val comparableExercises: Int,
    val improvedExercises: Int,
    val averageWeightKg: Double?,
    val weightChangeKg: Double?
)

/** Ultima settimana COMPLETA: metriche solo da dati registrati, niente valori demo. */
object WeeklyTrainingReportCalculator {
    fun evaluate(history: List<SessionRecord>, weights: List<BodyProgressEntry>,
                 goalDays: Int, today: LocalDate = LocalDate.now(),
                 zone: ZoneId = ZoneId.systemDefault()): WeeklyTrainingReport {
        val thisMonday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val monday = thisMonday.minusWeeks(1)
        val previous = monday.minusWeeks(1)
        fun sessionDate(session: SessionRecord): LocalDate? =
            session.completedAt.takeIf { it > 0L }?.let {
                Instant.ofEpochMilli(it).atZone(zone).toLocalDate()
            }
        val recent = history.filter { session ->
            session.completedSets > 0 && sessionDate(session)?.let {
                it >= monday && it < thisMonday
            } == true
        }
        fun best(from: LocalDate, until: LocalDate): Map<String, Double> =
            TrainingAnalytics.completedSets(history, zone)
                .filter { it.at >= from && it.at < until }
                .mapNotNull { set ->
                    set.estimatedOneRepMaxKg?.takeIf { it > 0.0 }?.let {
                        set.exerciseId to it
                    }
                }.groupBy({ it.first }, { it.second })
                .mapValues { (_, values) -> values.max() }
        val olderSets = best(previous, monday)
        val newSets = best(monday, thisMonday)
        val common = olderSets.keys.intersect(newSets.keys)
        val improved = common.count { id ->
            newSets.getValue(id) >= olderSets.getValue(id) * 1.01
        }

        fun average(from: LocalDate, until: LocalDate): Double? {
            val samples = weights.filter { it.date >= from && it.date < until }
                .mapNotNull { it.weightKg?.takeIf { kg ->
                    kg.isFinite() && kg in 20.0..400.0
                } }
            return samples.takeIf { it.size >= 3 }?.average()
        }
        val thisWeight = average(monday, thisMonday)
        val oldWeight = average(previous, monday)
        return WeeklyTrainingReport(
            monday = monday, sunday = thisMonday.minusDays(1),
            completedDays = recent.mapNotNull(::sessionDate).distinct().size,
            goalDays = goalDays.coerceIn(1, 7),
            completedSets = recent.sumOf { it.completedSets.coerceAtLeast(0) },
            quickSessions = recent.count { it.daySnapshot?.name?.endsWith(" · Rapido") == true },
            comparableExercises = common.size, improvedExercises = improved,
            averageWeightKg = thisWeight,
            weightChangeKg = if (thisWeight != null && oldWeight != null)
                thisWeight - oldWeight else null
        )
    }
}
