package it.pulse.workout.reminders

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

enum class ReminderKind(val key: String, val description: String) {
    WATER("water", "Bevi acqua"),
    MOVE("move", "Alzati e muoviti")
}

/**
 * Orari locali, ogni giorno: acqua 08:30–18:00 (inclusi);
 * movimento 09:30–17:30, ossia dopo ogni ora trascorsa dalle 08:30.
 * Nessuna notifica notturna; allineamento agli orari, non al momento
 * in cui l'utente apre l'app.
 */
object ReminderClock {
    private val start = LocalTime.of(8, 30)

    fun times(kind: ReminderKind): List<LocalTime> =
        when (kind) {
            ReminderKind.WATER -> (0..19).map { start.plusMinutes(it * 30L) }
            ReminderKind.MOVE -> (1..9).map { start.plusHours(it.toLong()) }
        }

    fun nextAfter(kind: ReminderKind, now: LocalDateTime): LocalDateTime {
        val today = now.toLocalDate()
        for (day in 0..1L) {
            val date = today.plusDays(day)
            for (time in times(kind)) {
                val candidate = LocalDateTime.of(date, time)
                if (candidate.isAfter(now)) return candidate
            }
        }
        error("Non è stato possibile calcolare un promemoria futuro")
    }

    fun dueAt(kind: ReminderKind, expected: LocalDateTime): Boolean =
        expected.toLocalTime() in times(kind)

    fun slotKey(kind: ReminderKind, whenDue: LocalDateTime): String =
        "${kind.key}_${whenDue.toLocalDate()}_${whenDue.toLocalTime()}"
}
