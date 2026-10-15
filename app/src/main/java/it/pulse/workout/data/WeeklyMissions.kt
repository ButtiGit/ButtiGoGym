package it.pulse.workout.data

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

/**
 * XP delle missioni separati dagli XP MUSCOLARI. Non sono una misura
 * di massa, forza o idoneità clinica. Ricostruibili dallo storico vero:
 * nessun contatore manuale da sincronizzare o perdere con il backup.
 */
data class WeeklyMission(
    val id: String,
    val name: String,
    val detail: String,
    val progress: Int,
    val target: Int,
    val rewardXp: Int,
    val available: Boolean = true
) {
    val completed: Boolean get() = available && progress >= target
}

data class WeeklyMissionSummary(
    val weekStart: LocalDate,
    val missions: List<WeeklyMission>,
    val earnedXp: Int,
    val completedWeeksLastFour: Int,
    val workoutsLastFourWeeks: Int,
    val weeksObserved: Int = 4
)

object WeeklyMissionCalculator {
    fun summarize(
        history: List<SessionRecord>,
        weights: List<BodyProgressEntry>,
        checkIns: List<PreWorkoutCheckIn>,
        weeklyWorkoutGoal: Int,
        today: LocalDate = LocalDate.now(),
        zone: ZoneId = ZoneId.systemDefault()
    ): WeeklyMissionSummary {
        val start = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val end = start.plusWeeks(1)
        val target = weeklyWorkoutGoal.coerceIn(1, 7)
        fun sessionDate(session: SessionRecord): LocalDate? =
            session.completedAt.takeIf { it > 0 }?.let {
                Instant.ofEpochMilli(it).atZone(zone).toLocalDate()
            }
        val validDays = history.asSequence()
            .filter { it.completedSets > 0 }
            .mapNotNull(::sessionDate)
            .filter { !it.isAfter(today) }
            .toList()
        val currentDays = validDays.filter { it >= start && it < end }.distinct().size
        val weightsThisWeek = weights.asSequence()
            .filter { it.date >= start && it.date <= today }
            .filter { it.weightKg?.let { kg -> kg.isFinite() && kg in 20.0..400.0 } == true }
            .map { it.date }.distinct().count()
        val checkInsThisWeek = checkIns.map { it.date }
            .filter { it >= start && it <= today }.distinct().size

        // Progresso performance SOLO a parità di exercise.id, non confonde
        // attrezzi sostitutivi. Mancanza di storico = missione da sbloccare.
        val series = TrainingAnalytics.completedSets(history, zone)
        fun best(from: LocalDate, until: LocalDate): Map<String, Double> =
            series.asSequence()
                .filter { it.at >= from && it.at < until && !it.at.isAfter(today) }
                .mapNotNull { set ->
                    set.estimatedOneRepMaxKg?.let { max -> set.exerciseId to max }
                }
                .groupBy({ it.first }, { it.second })
                .mapValues { (_, values) -> values.max() }
        val currentBest = best(start, end)
        val previousBest = best(start.minusWeeks(1), start)
        val shared = currentBest.keys.intersect(previousBest.keys)
        val improved = shared.count { id ->
            currentBest.getValue(id) >= previousBest.getValue(id) * 1.01
        }

        val missions = listOf(
            WeeklyMission("workouts", "La tua routine",
                "Giornate allenate, anche in modalità rapida", currentDays.coerceAtMost(target),
                target, 120),
            WeeklyMission("weigh", "Controlla il percorso",
                "Pesate valide in giorni differenti", weightsThisWeek.coerceAtMost(3), 3, 60),
            WeeklyMission("checkin", "Ascolta il recupero",
                "Check-in facoltativo prima di una seduta", checkInsThisWeek.coerceAtMost(1), 1, 40),
            WeeklyMission("progress", "Un piccolo miglioramento",
                if (shared.isEmpty()) "Disponibile dopo due settimane confrontabili"
                else "Almeno +1% nella stima di forza di un esercizio",
                improved.coerceAtMost(1), 1, 80, shared.isNotEmpty())
        )
        val previousFour = (1L..4L).map { start.minusWeeks(it) }
        val weeksCompleted = previousFour.count { begin ->
            validDays.filter { it >= begin && it < begin.plusWeeks(1) }.distinct().size >= target
        }
        val workoutsInFour = validDays.count { date ->
            previousFour.any { begin -> date >= begin && date < begin.plusWeeks(1) }
        }
        return WeeklyMissionSummary(
            start, missions, missions.filter { it.completed }.sumOf { it.rewardXp },
            weeksCompleted, workoutsInFour
        )
    }
}
