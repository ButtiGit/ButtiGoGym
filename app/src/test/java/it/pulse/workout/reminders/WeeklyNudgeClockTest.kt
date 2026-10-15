package it.pulse.workout.reminders

import org.junit.Assert.*
import org.junit.Test
import it.pulse.workout.data.PersonalSettings
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.LocalTime

class WeeklyNudgeClockTest {
    @Test fun respectsConfiguredDaysAndTimeWindow() {
        val configured=PersonalSettings(
            reminderDays=setOf(DayOfWeek.THURSDAY),
            reminderStart=LocalTime.of(10,0),
            reminderEnd=LocalTime.of(16,0)
        )
        val scheduled=WeeklyNudgeClock.nextAfter(
            LocalDateTime.of(2026,10,15,16,30),configured)
        assertEquals(LocalDateTime.of(2026,10,22,10,15),scheduled)
    }
    @Test fun doesNotScheduleInPastOrDuringNight() {
        val settings=PersonalSettings()
        val scheduled=WeeklyNudgeClock.nextAfter(
            LocalDateTime.of(2026,10,15,22,15),settings)
        assertTrue(scheduled.isAfter(LocalDateTime.of(2026,10,15,22,15)))
        assertEquals(LocalTime.of(9,0),scheduled.toLocalTime())
    }
    @Test fun choosesPreviousWeekForSummary() {
        assertEquals(java.time.LocalDate.of(2026,10,5),
            WeeklyNudgeClock.weekToReport(LocalDateTime.of(2026,10,15,9,0)))
    }
}
