package it.pulse.workout.data

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class WeeklyTrainingReportTest {
    private val zone=ZoneId.of("Europe/Rome")
    private val today=LocalDate.of(2026,10,15)
    private val exercise=Exercise("bench","Chest press","PETTO",1,"8–12")
    private val day=WorkoutDay("fri","VENERDÌ","Petto","","55 min",listOf(exercise))
    private fun record(date:String,kg:String,quick:Boolean=false,offset:Long=0L):SessionRecord {
        val t=LocalDate.parse(date).atTime(17,0).atZone(zone).toInstant().toEpochMilli()+offset
        return SessionRecord(t,"fri",t,50,1,1,
            entries=mapOf("bench:1" to SetEntry(kg,"10",true,false,t,2)),
            daySnapshot=if(quick)day.copy(name="Petto · Rapido") else day)
    }
    private fun weigh(date:String,kg:Double):List<BodyProgressEntry> {
        val d=LocalDate.parse(date)
        return (0L..2L).map { BodyProgressEntry(d.plusDays(it), kg) }
    }

    @Test fun comparesOnlyLastCompletedWeekAndActualSets() {
        val report=WeeklyTrainingReportCalculator.evaluate(
            listOf(record("2026-10-01","40"),record("2026-10-06","42"),
                record("2026-10-08","42",true),record("2026-10-15","100")),
            emptyList(),3,today,zone)
        assertEquals(LocalDate.of(2026,10,5),report.monday)
        assertEquals(LocalDate.of(2026,10,11),report.sunday)
        assertEquals(2,report.completedDays)
        assertEquals(2,report.completedSets)
        assertEquals(1,report.quickSessions)
        assertEquals(1,report.comparableExercises)
        assertEquals(1,report.improvedExercises)
    }
    @Test fun missingComparisonRemainsUnknown() {
        val report=WeeklyTrainingReportCalculator.evaluate(
            listOf(record("2026-10-06","40")), emptyList(),3,today,zone)
        assertEquals(0,report.comparableExercises)
        assertNull(report.weightChangeKg)
    }
    @Test fun weightDeltaRequiresThreeEntriesInBothWeeks() {
        val weight=weigh("2026-09-28",58.0)+weigh("2026-10-05",58.2)
        val result=WeeklyTrainingReportCalculator.evaluate(emptyList(),weight,3,today,zone)
        assertEquals(0.2,result.weightChangeKg!!,0.0001)
        assertNull(WeeklyTrainingReportCalculator.evaluate(emptyList(),weight.take(4),
            3,today,zone).weightChangeKg)
    }
    @Test fun multipleSessionsOnSameDayCountOnce() {
        val report=WeeklyTrainingReportCalculator.evaluate(
            listOf(record("2026-10-06","40"),record("2026-10-06","42",offset=400)),
            emptyList(),3,today,zone)
        assertEquals(1,report.completedDays)
        assertEquals(2,report.completedSets)
    }
}
