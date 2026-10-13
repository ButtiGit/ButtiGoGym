package it.pulse.workout.reminders

import it.pulse.workout.data.PersonalSettings
import org.junit.Assert.*
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.LocalTime

class PersonalReminderClockTest {
    private val mondayOnly = PersonalSettings(
        reminderDays = setOf(DayOfWeek.MONDAY),
        reminderStart = LocalTime.of(10, 0),
        reminderEnd = LocalTime.of(14, 0)
    )

    @Test fun adjustedHoursAndMovementOffsetDoNotLeakOutOfWindow() {
        assertEquals(
            listOf("10:00", "10:30", "11:00", "11:30", "12:00",
                "12:30", "13:00", "13:30", "14:00"),
            ReminderClock.times(ReminderKind.WATER, mondayOnly).map { it.toString() }
        )
        assertEquals(listOf("11:00", "12:00", "13:00"),
            ReminderClock.times(ReminderKind.MOVE, mondayOnly).map { it.toString() })
    }

    @Test fun nextAfterSkipsOffDaysAndStrictlyExcludesPastSlot() {
        assertEquals(LocalDateTime.of(2026, 10, 19, 10, 0),
            ReminderClock.nextAfter(ReminderKind.WATER,
                LocalDateTime.of(2026, 10, 13, 11, 0), mondayOnly))
        assertEquals(LocalDateTime.of(2026, 10, 19, 10, 30),
            ReminderClock.nextAfter(ReminderKind.WATER,
                LocalDateTime.of(2026, 10, 19, 10, 0), mondayOnly))
        assertFalse(ReminderClock.dueAt(ReminderKind.WATER,
            LocalDateTime.of(2026, 10, 20, 10, 0), mondayOnly))
        assertTrue(ReminderClock.dueAt(ReminderKind.WATER,
            LocalDateTime.of(2026, 10, 19, 13, 30), mondayOnly))
    }
}
