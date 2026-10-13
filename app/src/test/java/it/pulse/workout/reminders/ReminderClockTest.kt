package it.pulse.workout.reminders

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.LocalTime

class ReminderClockTest {
    @Test fun waterEveryThirtyMinutesWithLastAtSixPm() {
        val hours = ReminderClock.times(ReminderKind.WATER)
        assertEquals(20, hours.size)
        assertEquals(LocalTime.of(8, 30), hours.first())
        assertEquals(LocalTime.of(9, 0), hours[1])
        assertEquals(LocalTime.of(18, 0), hours.last())
        assertTrue(ReminderClock.dueAt(ReminderKind.WATER,
            LocalDateTime.of(2026, 10, 13, 17, 30)))
        assertFalse(ReminderClock.dueAt(ReminderKind.WATER,
            LocalDateTime.of(2026, 10, 13, 18, 30)))
    }

    @Test fun moveOncePerHourFromNineThirtyToSeventeenThirty() {
        val hours = ReminderClock.times(ReminderKind.MOVE)
        assertEquals(9, hours.size)
        assertEquals(LocalTime.of(9, 30), hours.first())
        assertEquals(LocalTime.of(17, 30), hours.last())
    }

    @Test fun endOfDaySchedulesNextMorningInsteadOfNight() {
        assertEquals(LocalDateTime.of(2026, 10, 14, 8, 30),
            ReminderClock.nextAfter(ReminderKind.WATER,
                LocalDateTime.of(2026, 10, 13, 18, 0)))
        assertEquals(LocalDateTime.of(2026, 10, 14, 9, 30),
            ReminderClock.nextAfter(ReminderKind.MOVE,
                LocalDateTime.of(2026, 10, 13, 17, 30)))
    }

    @Test fun nextBoundaryIsAnchoredToClockRatherThanLaunchTime() {
        assertEquals(LocalDateTime.of(2026, 10, 13, 10, 0),
            ReminderClock.nextAfter(ReminderKind.WATER,
                LocalDateTime.of(2026, 10, 13, 9, 45)))
        assertEquals(LocalDateTime.of(2026, 10, 13, 10, 30),
            ReminderClock.nextAfter(ReminderKind.MOVE,
                LocalDateTime.of(2026, 10, 13, 9, 45)))
    }
}
