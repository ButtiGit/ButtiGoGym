package it.pulse.workout.data

/**
 * Riferimenti visuali per la settimana: provengono dalla scheda in uso, non
 * da numeri fissi che diventano errati dopo aggiornamenti e personalizzazioni.
 * Sono stime di pianificazione, NON obiettivi di volume allenante prescritti.
 */
data class WeeklyPlanGoals(val sets: Int?, val minutes: Int?)

object WeeklyPlanGoalsCalculator {
    private val duration = Regex("""(\d+)\s*[–-]\s*(\d+)\s*min""", RegexOption.IGNORE_CASE)

    fun from(days: List<WorkoutDay>, weeklyWorkouts: Int): WeeklyPlanGoals {
        val available = days.filterNot { it.optional } + days.filter { it.optional }
        if (weeklyWorkouts !in 1..7 || available.size < weeklyWorkouts) {
            // Non inventare una quinta seduta se la scheda ne definisce quattro.
            return WeeklyPlanGoals(sets = null, minutes = null)
        }
        val selected = available.take(weeklyWorkouts)
        val sets = selected.sumOf { it.setCount }.takeIf { it > 0 }
        val minutes = selected.mapNotNull { day ->
            val match = duration.find(day.duration) ?: return@mapNotNull null
            val min = match.groupValues[1].toInt()
            val max = match.groupValues[2].toInt()
            if (min <= 0 || max < min) null else (min + max) / 2
        }.takeIf { it.size == selected.size }?.sum()
        return WeeklyPlanGoals(sets, minutes)
    }
}
