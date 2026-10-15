package it.pulse.workout.data

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class PreWorkoutCheckInRulesTest {
    private val day=LocalDate.of(2026,10,15)
    private fun entry(e:WorkoutEnergy,s:LastNightSleep,p:WorkoutSoreness) =
        PreWorkoutCheckIn(day,e,s,p)

    @Test fun normalStateDoesNotPromiseFullRecovery() {
        val report=entry(WorkoutEnergy.NORMAL,LastNightSleep.NORMAL,WorkoutSoreness.NONE)
        assertEquals(TrainingReadiness.AS_PLANNED,PreWorkoutCheckInRules.evaluate(report))
        assertTrue(PreWorkoutCheckInRules.recommendation(report).contains("valutare"))
    }

    @Test fun shortSleepAndLowEnergyTriggerRestSuggestion() {
        assertEquals(TrainingReadiness.CONSIDER_REST,PreWorkoutCheckInRules.evaluate(
            entry(WorkoutEnergy.LOW,LastNightSleep.SHORT,WorkoutSoreness.NONE)))
    }

    @Test fun severeSorenessOrSingleLowFactorTriggerReducedLoad() {
        assertEquals(TrainingReadiness.REDUCE_LOAD,PreWorkoutCheckInRules.evaluate(
            entry(WorkoutEnergy.HIGH,LastNightSleep.NORMAL,WorkoutSoreness.STRONG)))
        assertEquals(TrainingReadiness.REDUCE_LOAD,PreWorkoutCheckInRules.evaluate(
            entry(WorkoutEnergy.LOW,LastNightSleep.NORMAL,WorkoutSoreness.NONE)))
    }
}
