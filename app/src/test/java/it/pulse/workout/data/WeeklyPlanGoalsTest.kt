package it.pulse.workout.data

import org.junit.Assert.*
import org.junit.Test

class WeeklyPlanGoalsTest {
    private fun day(id: String, sets: Int, duration: String,
                    optional: Boolean = false): WorkoutDay =
        WorkoutDay(id, id.uppercase(), "Test", "", duration,
            exercises = listOf(Exercise("${id}_lift", "Lift", "GAMBE", sets, "8–12")),
            optional = optional)

    private val week = listOf(
        day("mon", 22, "80–100 min"),
        day("wed", 21, "75–90 min"),
        day("fri", 23, "85–105 min"),
        day("bonus", 8, "30–55 min", true)
    )

    @Test fun goalsComeFromTheActualPlan() {
        assertEquals(WeeklyPlanGoals(66, 267), WeeklyPlanGoalsCalculator.from(week, 3))
        assertEquals(WeeklyPlanGoals(74, 309), WeeklyPlanGoalsCalculator.from(week, 4))
        assertEquals(WeeklyPlanGoals(43, 172), WeeklyPlanGoalsCalculator.from(week, 2))
    }

    @Test fun changingThePlanChangesTheGoal() {
        val revised = week.toMutableList()
        revised[0] = day("mon", 18, "80–100 min")
        assertEquals(62, WeeklyPlanGoalsCalculator.from(revised, 3).sets)
    }

    @Test fun unsupportedFrequencyDoesNotInventWorkoutTargets() {
        assertEquals(WeeklyPlanGoals(null, null), WeeklyPlanGoalsCalculator.from(week, 5))
        assertEquals(WeeklyPlanGoals(null, null), WeeklyPlanGoalsCalculator.from(emptyList(), 3))
    }

    @Test fun unknownDurationDoesNotInventTrainingMinutes() {
        val revised = week.toMutableList()
        revised[1] = day("wed", 21, "tempo libero")
        assertEquals(WeeklyPlanGoals(66, null), WeeklyPlanGoalsCalculator.from(revised, 3))
    }

    @Test fun excludesBonusWhenThreeSessionsArePlanned() {
        assertEquals(66, WeeklyPlanGoalsCalculator.from(week, 3).sets)
        assertEquals(74, WeeklyPlanGoalsCalculator.from(week, 4).sets)
    }
}
