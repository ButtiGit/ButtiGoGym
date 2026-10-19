package it.pulse.workout.data

import org.junit.Assert.*
import org.junit.Test

class MascotCoachTest {
    @Test fun resumeActiveSessionFirst() {
        assertTrue(MascotCoach.today(true, true, true, 3, 3).contains("in corso"))
    }
    @Test fun restAndCompletedWorkoutsAreRespected() {
        assertTrue(MascotCoach.today(false, true, false, 0, 3).contains("recuperiamo"))
        assertTrue(MascotCoach.today(false, false, true, 1, 3).contains("Allenamento fatto"))
    }
    @Test fun guidanceDoesNotInventCompletedWorkouts() {
        assertTrue(MascotCoach.today(false, false, false, 0, 3).contains("prossimo"))
        assertTrue(MascotCoach.today(false, false, false, 3, 3).contains("raggiunto"))
    }
    @Test fun messagesFollowSetState() {
        assertTrue(MascotCoach.inSession(true, false).contains("Recupera"))
        assertTrue(MascotCoach.inSession(false, true).contains("Salva"))
    }
    @Test fun homeDefaultsMinimalButAllowsEveryModule() {
        val simple = HomeDisplaySettings()
        assertEquals(0, simple.shownCount())
        val full = HomeDisplaySettings(true,true,true,true,true,true,true,true,true)
        assertEquals(9, full.shownCount())
    }
}
