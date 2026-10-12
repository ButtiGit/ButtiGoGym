package it.pulse.workout.data

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class StreakCalculatorTest {
    private fun record(day: String, id: String): SessionRecord {
        val epoch = LocalDate.parse(day).atTime(12, 0).atZone(ZoneId.systemDefault())
            .toInstant().toEpochMilli()
        return SessionRecord(id = epoch + id.length, dayId = id, completedAt = epoch,
            durationMinutes = 55, completedSets = 12, totalSets = 18)
    }

    @Test fun startsAtYesterdayWhenTodayNotDone() {
        val date = LocalDate.parse("2026-10-12")
        val data = WellnessData(
            stepsByDate = mapOf("2026-10-11" to 6500, "2026-10-10" to 6200),
            restDates = setOf("2026-10-11", "2026-10-10")
        )
        val s = StreakCalculator.calculate(date, data, emptyList())
        assertEquals(2, s.stepDays)
        assertEquals(2, s.generalDays)
        assertEquals(2, s.restDays)
    }

    @Test fun workoutDoesNotBreakRestStreak() {
        val date = LocalDate.parse("2026-10-12")
        val data = WellnessData(restDates = setOf("2026-10-11", "2026-10-09"))
        val s = StreakCalculator.calculate(date, data, listOf(record("2026-10-10", "bonus")))
        assertEquals(2, s.restDays)
    }

    @Test fun workoutWeeksRequireAllThreeDistinctSessions() {
        val date = LocalDate.parse("2026-10-12")
        val history = listOf(
            record("2026-10-05", "mon"), record("2026-10-07", "wed"),
            record("2026-10-09", "fri"), record("2026-10-10", "bonus")
        )
        val s = StreakCalculator.calculate(date, WellnessData(), history)
        assertEquals(1, s.workoutWeeks)
        assertEquals(0, s.workoutDaysThisWeek)
    }

    @Test fun twoOfThreeDoesNotCountAsFullTrainingWeek() {
        val date = LocalDate.parse("2026-10-12")
        val history = listOf(record("2026-10-05", "mon"), record("2026-10-07", "wed"))
        assertEquals(0, StreakCalculator.calculate(date, WellnessData(), history).workoutWeeks)
    }
}
