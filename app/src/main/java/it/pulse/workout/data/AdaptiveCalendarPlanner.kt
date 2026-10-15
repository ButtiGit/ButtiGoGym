package it.pulse.workout.data

import java.time.LocalDate

/** Suggerimenti, non spostamenti automatici. Nessun evento viene sovrascritto. */
data class SuggestedWorkoutDate(val date: LocalDate, val recoveryWarning: Boolean)

object AdaptiveCalendarPlanner {
    fun suggestions(
        source: LocalDate,
        entries: List<CalendarEntry>,
        completed: Set<LocalDate>,
        rest: Set<LocalDate>,
        today: LocalDate,
        limit: Int = 3
    ): List<SuggestedWorkoutDate> {
        if (limit <= 0 || source in completed ||
            entries.none { it.date == source && it.kind == CalendarEntryKind.WORKOUT })
            return emptyList()

        val blocked = entries.map { it.date }.toSet() + completed + rest
        val training = entries.filter {
            it.kind == CalendarEntryKind.WORKOUT && it.date != source
        }.map { it.date }.toSet() + completed

        // Per un allenamento passato riparti da oggi; per uno futuro, dal giorno dopo.
        val first = maxOf(today, source.plusDays(1))
        val candidates = (0L..20L).map { first.plusDays(it) }
            .filter { it !in blocked && it.year in 2000..2100 }
            .map { date ->
                SuggestedWorkoutDate(date,
                    date.minusDays(1) in training || date.plusDays(1) in training)
            }

        // Privilegia recupero di almeno un giorno, poi vicinanza. Se non è
        // possibile, mostra le alternative con un avviso, senza bloccare l'utente.
        val optimal = candidates.filterNot { it.recoveryWarning }.take(limit)
        return if (optimal.size >= limit) optimal
        else optimal + candidates.filter { it.recoveryWarning }
            .take(limit - optimal.size)
    }
}
