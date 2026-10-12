package it.pulse.workout.data

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class WellnessHealthConnectTest {

    @Test
    fun healthConnectOverridesLocalSensorInsteadOfSumming() {
        val day = LocalDate.parse("2026-10-12")
        val local = WellnessData(
            stepsByDate = mapOf("2026-10-12" to 3100),
            healthStepsByDate = mapOf("2026-10-12" to 6150)
        )
        assertEquals(6150, local.stepsOn(day))
    }

    @Test
    fun zeroFromHealthConnectIsAuthoritative() {
        val day = LocalDate.parse("2026-10-12")
        val result = WellnessData(
            stepsByDate = mapOf("2026-10-12" to 3200),
            healthStepsByDate = mapOf("2026-10-12" to 0)
        )
        assertEquals(0, result.stepsOn(day))
    }

    @Test
    fun sensorFallbackRemainsAfterHealthAccessRevoked() {
        val day = LocalDate.parse("2026-10-12")
        val data = WellnessData(
            stepsByDate = mapOf("2026-10-12" to 3100)
        )
        assertEquals(3100, data.stepsOn(day))
    }

    @Test
    fun previousDaysAreUsedForStepStreakAfterSync() {
        val today = LocalDate.parse("2026-10-12")
        val data = WellnessData(
            healthStepsByDate = mapOf(
                "2026-10-12" to 4200,
                "2026-10-11" to 6400,
                "2026-10-10" to 6200
            )
        )
        val summary = StreakCalculator.calculate(today, data, emptyList())
        assertEquals(2, summary.stepDays)
        assertEquals(4200, summary.todaySteps)
    }
}
