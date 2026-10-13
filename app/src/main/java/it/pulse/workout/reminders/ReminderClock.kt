package it.pulse.workout.reminders

import it.pulse.workout.data.PersonalSettings
import it.pulse.workout.data.PersonalSettingsRules
import java.time.LocalDateTime
import java.time.LocalTime

enum class ReminderKind(val key: String, val description: String) {
    WATER("water", "Bevi acqua"),
    MOVE("move", "Alzati e muoviti")
}

/** Local wall-clock schedule; water every 30 minutes, movement every 60 minutes. */
object ReminderClock {
    fun times(kind: ReminderKind, settings: PersonalSettings = PersonalSettings()): List<LocalTime> {
        val valid = PersonalSettingsRules.validate(settings)
        val firstOffset = if (kind == ReminderKind.WATER) 0 else 60
        val step = if (kind == ReminderKind.WATER) 30 else 60
        val minutes = java.time.Duration.between(valid.reminderStart, valid.reminderEnd).toMinutes()
        return generateSequence(firstOffset.toLong()) { it + step }
            .takeWhile { if (kind == ReminderKind.MOVE) it < minutes else it <= minutes }
            .map { valid.reminderStart.plusMinutes(it) }.toList()
    }

    fun nextAfter(
        kind: ReminderKind, now: LocalDateTime, settings: PersonalSettings = PersonalSettings()
    ): LocalDateTime {
        val valid = PersonalSettingsRules.validate(settings)
        val hours = times(kind, valid)
        for (days in 0..7L) {
            val day = now.toLocalDate().plusDays(days)
            if (day.dayOfWeek !in valid.reminderDays) continue
            for (time in hours) {
                val candidate = LocalDateTime.of(day, time)
                if (candidate.isAfter(now)) return candidate
            }
        }
        error("Non è stato possibile calcolare un promemoria futuro")
    }

    fun dueAt(
        kind: ReminderKind, expected: LocalDateTime, settings: PersonalSettings = PersonalSettings()
    ): Boolean =
        expected.dayOfWeek in settings.reminderDays &&
            expected.toLocalTime() in times(kind, settings)

    fun slotKey(kind: ReminderKind, whenDue: LocalDateTime): String =
        "${kind.key}_${whenDue.toLocalDate()}_${whenDue.toLocalTime()}"
}
