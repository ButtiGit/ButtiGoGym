package it.pulse.workout.data

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

class WorkoutCalendarRulesTest {
    private fun day(raw: String) = LocalDate.parse(raw)
    private fun workout(raw: String, id: String = "mon") =
        CalendarEntry(day(raw), CalendarEntryKind.WORKOUT, id)
    private val today = day("2026-10-13")

    @Test fun emptyMonthDoesNotInventMissedDaysOrPerfectAdherence() {
        val stats = WorkoutCalendarRules.summary(
            YearMonth.of(2026, 10), emptyList(), emptySet(), emptySet(), today
        )
        assertEquals(0, stats.skippedDays)
        assertEquals(0, stats.completedDays)
        assertEquals(0, stats.plannedDue)
        assertNull(stats.adherencePercent)
    }

    @Test fun scheduledPastBecomesMissedButTodayAndFutureRemainPlanned() {
        val entries = listOf(workout("2026-10-12"), workout("2026-10-13"),
            workout("2026-10-14"))
        assertEquals(CalendarDayStatus.SKIPPED, WorkoutCalendarRules.status(
            day("2026-10-12"), entries, emptySet(), emptySet(), today))
        assertEquals(CalendarDayStatus.PLANNED, WorkoutCalendarRules.status(
            today, entries, emptySet(), emptySet(), today))
        val stats = WorkoutCalendarRules.summary(YearMonth.of(2026, 10),
            entries, emptySet(), emptySet(), today)
        assertEquals(1, stats.plannedDue)
        assertEquals(2, stats.plannedUpcoming)
        assertEquals(0, stats.adherencePercent)
    }

    @Test fun completedTakesPrecedenceAndRestDaysDoNotPenalizeAdherence() {
        val entries = listOf(workout("2026-10-10"), workout("2026-10-11"))
        val completed = setOf(day("2026-10-10"), day("2026-10-12"))
        val rest = setOf(day("2026-10-11"), day("2026-10-09"))
        val stats = WorkoutCalendarRules.summary(YearMonth.of(2026, 10),
            entries, completed, rest, today)
        assertEquals(2, stats.completedDays)
        assertEquals(1, stats.plannedDue)
        assertEquals(1, stats.plannedCompleted)
        assertEquals(1, stats.unplannedCompleted)
        assertEquals(100, stats.adherencePercent)
        assertEquals(2, stats.restDays)
        assertEquals(0, stats.skippedDays)
    }

    @Test fun explicitSkippedIsDifferentFromUnplannedRestAndPreservesName() {
        val skipped = CalendarEntry(today, CalendarEntryKind.SKIPPED, "wed")
        val entries = WorkoutCalendarRules.place(listOf(workout("2026-10-12")), skipped)
        val stats = WorkoutCalendarRules.summary(YearMonth.of(2026, 10),
            entries, emptySet(), emptySet(), today)
        assertEquals(2, stats.plannedDue)
        assertEquals(2, stats.skippedDays)
        assertEquals(0, stats.adherencePercent)
        assertEquals("wed", entries.last().workoutDayId)
    }

    @Test fun movingWorkoutClearsOldDateWithoutCreatingAbsence() {
        val old = workout("2026-10-12")
        val entries = WorkoutCalendarRules.move(listOf(old), old.date, day("2026-10-16"))
        assertFalse(entries.any { it.date == old.date })
        assertEquals("mon", entries.single().workoutDayId)
        val month = WorkoutCalendarRules.summary(
            YearMonth.of(2026, 10), entries, emptySet(), emptySet(), today)
        assertEquals(0, month.skippedDays)
        assertEquals(1, month.plannedUpcoming)
    }

    @Test fun moveRefusesOccupiedOrRestingDestinationAndCompletedSource() {
        val one = workout("2026-10-12")
        val two = workout("2026-10-15", "wed")
        assertThrows(IllegalArgumentException::class.java) {
            WorkoutCalendarRules.move(listOf(one,two),one.date,two.date)
        }
        assertThrows(IllegalArgumentException::class.java) {
            WorkoutCalendarRules.move(listOf(one),one.date,day("2026-10-16"),
                destinationRest = true)
        }
        assertThrows(IllegalArgumentException::class.java) {
            WorkoutCalendarRules.move(listOf(one),one.date,day("2026-10-16"),
                sourceCompleted = true)
        }
    }

    @Test fun completedSessionsCountOncePerDateAndInLocalTimezone() {
        val zone = ZoneId.of("Europe/Rome")
        val timestamp = today.atTime(21,0).atZone(zone).toInstant().toEpochMilli()
        val history = listOf(
            SessionRecord(timestamp, "mon", timestamp, 30, 2, 2),
            SessionRecord(timestamp + 1000, "wed", timestamp + 1000, 25, 1, 1),
            SessionRecord(timestamp + 2000, "bonus", timestamp + 2000, 10, 0, 2)
        )
        assertEquals(setOf(today), WorkoutCalendarRules.completedDates(history,zone))
    }

    @Test fun todaysPlannedCompletedSessionIsNotClassifiedAsExtra() {
        val scheduled = listOf(workout("2026-10-13"))
        val summary = WorkoutCalendarRules.summary(
            YearMonth.of(2026, 10), scheduled, setOf(today), emptySet(), today)
        assertEquals(1, summary.completedDays)
        assertEquals(0, summary.unplannedCompleted)
        assertEquals(0, summary.plannedDue) // Today has not become an overdue plan.
        assertNull(summary.adherencePercent)
    }

    @Test fun monthlyFiguresNeverIncludeNextOrPreviousMonth() {
        val entries = listOf(workout("2026-09-30"), workout("2026-10-12"),
            workout("2026-11-01"))
        val summary = WorkoutCalendarRules.summary(YearMonth.of(2026,10),
            entries, emptySet(), emptySet(), today)
        assertEquals(1,summary.skippedDays)
        assertEquals(1,summary.plannedDue)
    }
}
