package it.pulse.workout.data

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class WeeklyMissionCalculatorTest {
    private val zone = ZoneId.of("Europe/Rome")
    private val today = LocalDate.of(2026,10,15)
    private val day = WorkoutDay("fri","VEN","Petto","","55 min",
        listOf(Exercise("bench","Bench","PETTO",1,"8–12")))
    private fun completed(date:String, kg:String = "40"):SessionRecord {
        val millis = LocalDate.parse(date).atTime(17,0).atZone(zone).toInstant().toEpochMilli()
        return SessionRecord(millis,"fri",millis,55,1,1,
            mapOf("bench:1" to SetEntry(kg,"10",true,false,millis,2)),
            daySnapshot=day)
    }
    private fun weigh(day:String) = BodyProgressEntry(LocalDate.parse(day),58.3)
    private fun check(day:String) = PreWorkoutCheckIn(LocalDate.parse(day),
        WorkoutEnergy.NORMAL,LastNightSleep.NORMAL,WorkoutSoreness.NONE)

    @Test fun earnsOnlyActuallyCompletedMissionXpNotMuscleXp() {
        val history = listOf(
            completed("2026-10-06","40"),completed("2026-10-13","42"),
            completed("2026-10-14","42"),completed("2026-10-15","42"))
        val report = WeeklyMissionCalculator.summarize(history,
            listOf(weigh("2026-10-13"),weigh("2026-10-14"),weigh("2026-10-15")),
            listOf(check("2026-10-15")),3,today,zone)
        assertEquals(4,report.missions.size)
        assertEquals(300,report.earnedXp)
        assertTrue(report.missions.all { it.completed })
    }

    @Test fun currentWeekIsNonPunitiveAndCountsDistinctDays() {
        val report=WeeklyMissionCalculator.summarize(
            listOf(completed("2026-10-13"),completed("2026-10-13")),
            listOf(weigh("2026-10-13"),weigh("2026-10-13")),emptyList(),
            3,today,zone)
        assertEquals(1,report.missions.first().progress)
        assertEquals(1,report.missions[1].progress)
        assertEquals(0,report.completedWeeksLastFour)
        assertFalse(report.missions.last().available)
    }

    @Test fun fourWeekConsistencyDoesNotResetAfterSingleMiss() {
        val history = listOf("2026-09-17","2026-09-18","2026-09-19",
            "2026-09-24","2026-09-25","2026-09-26",
            "2026-10-08","2026-10-09","2026-10-10").map(::completed)
        val report=WeeklyMissionCalculator.summarize(history,emptyList(),
            emptyList(),3,today,zone)
        assertEquals(3,report.completedWeeksLastFour)
    }

    @Test fun missingHistoricComparisonsNeverAwardFakeProgressXp() {
        val report=WeeklyMissionCalculator.summarize(
            listOf(completed("2026-10-15","60")),emptyList(),
            emptyList(),3,today,zone)
        assertFalse(report.missions.last().available)
        assertEquals(0,report.earnedXp)
    }

    @Test fun invalidWeightsAndFutureCheckInsAreIgnored() {
        val report=WeeklyMissionCalculator.summarize(emptyList(),
            listOf(BodyProgressEntry(today,Double.NaN),
                BodyProgressEntry(today.plusDays(1),59.0)),
            listOf(check("2026-10-16")),3,today,zone)
        assertEquals(0,report.missions[1].progress)
        assertEquals(0,report.missions[2].progress)
    }
}
