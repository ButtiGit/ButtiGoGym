package it.pulse.workout.data

import org.junit.Assert.*
import org.junit.Test

class WorkoutSetCueTest {
    private val ex=Exercise("curl","Curl","BICIPITI",2,"8–12")
    private fun advice(direction:ProgressionDirection,reps:Int,rir:Int?) =
        ExerciseProgression(direction,"Target","",1000L,
            listOf(PreviousWorkingSet(1,10.0,reps,rir)))

    @Test fun suggestsOneMoreRepOnlyBelowUpperLimit() {
        assertTrue(WorkoutSetCue.description(ex,
            advice(ProgressionDirection.ADD_REPETITIONS,10,3),1)!!.contains("11"))
        assertFalse(WorkoutSetCue.description(ex,
            advice(ProgressionDirection.KEEP_WEIGHT,12,3),1)!!.contains("13"))
    }

    @Test fun respectsFatigueAndTimedSets() {
        assertTrue(WorkoutSetCue.description(ex,
            advice(ProgressionDirection.ADD_REPETITIONS,8,0),1)!!.contains("Consolida"))
        assertTrue(WorkoutSetCue.description(ex.copy(timeBased=true),
            advice(ProgressionDirection.TIME_BASED,35,2),1)!!.contains("durata"))
    }

    @Test fun handlesMissingHistoryAndNonNumericTargets() {
        assertNull(WorkoutSetCue.description(ex,null,1))
        assertNull(WorkoutSetCue.description(ex,
            advice(ProgressionDirection.KEEP_WEIGHT,10,2),2))
        assertTrue(WorkoutSetCue.description(ex.copy(target="AMRAP"),
            advice(ProgressionDirection.KEEP_WEIGHT,10,2),1)!!.contains("controllata"))
    }
}
