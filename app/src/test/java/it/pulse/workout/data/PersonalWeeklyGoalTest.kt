package it.pulse.workout.data

import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.*
import org.junit.Test

/** Weekly target is configurable, but duplicate saves on the same date are never bonus days. */
class PersonalWeeklyGoalTest {
    private val zone = ZoneId.of("Europe/Rome")
    private fun session(date: String, dayId: String): SessionRecord {
        val millis = LocalDate.parse(date).atTime(17, 0).atZone(zone).toInstant().toEpochMilli()
        return SessionRecord(id = millis, dayId = dayId, completedAt = millis,
            durationMinutes = 40, completedSets = 3, totalSets = 3)
    }

    @Test fun customGoalsCountDistinctTrainingDaysIncludingOptionalSessions() {
        val history = listOf(
            session("2026-10-12", "mon"),
            session("2026-10-13", "bonus"),
            session("2026-10-14", "wed"),
            session("2026-10-15", "fri")
        )
        val now = LocalDate.parse("2026-10-16")
        val targetFour = StreakCalculator.calculate(now, WellnessData(), history, 4)
        assertEquals(4, targetFour.workoutDaysThisWeek)
        assertEquals(1, targetFour.workoutWeeks)
        val targetFive = StreakCalculator.calculate(now, WellnessData(), history, 5)
        assertEquals(4, targetFive.workoutDaysThisWeek)
        assertEquals(0, targetFive.workoutWeeks)
        val traditional = StreakCalculator.calculate(now, WellnessData(), history)
        assertEquals(3, traditional.workoutDaysThisWeek)
        assertEquals(1, traditional.workoutWeeks)
    }

    @Test fun twoSessionsOnOneDayCountOnlyOnceForCustomTarget() {
        val history = listOf(session("2026-10-12", "mon"), session("2026-10-12", "bonus"))
        val result = StreakCalculator.calculate(LocalDate.parse("2026-10-13"),
            WellnessData(), history, 2)
        assertEquals(1, result.workoutDaysThisWeek)
    }
}
