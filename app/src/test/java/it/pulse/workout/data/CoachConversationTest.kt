package it.pulse.workout.data

import org.junit.Assert.*
import org.junit.Test

class CoachConversationTest {
    private val chest = Exercise("bench", "Panca", "PETTO", 3, "8-12")

    @Test fun muscleMappingUsesRelevantPoses() {
        assertEquals(CoachPose.CHEST, CoachConversation.poseForMuscle("petto"))
        assertEquals(CoachPose.BACK, CoachConversation.poseForMuscle("DORSALI"))
        assertEquals(CoachPose.ARMS, CoachConversation.poseForMuscle("BICIPITI"))
        assertEquals(CoachPose.SHOULDERS, CoachConversation.poseForMuscle("SPALLE"))
        assertEquals(CoachPose.FOREARMS, CoachConversation.poseForMuscle("AVAMBRACCI"))
        assertEquals(CoachPose.ABS, CoachConversation.poseForMuscle("CORE"))
        assertEquals(CoachPose.LEGS, CoachConversation.poseForMuscle("FEMORALI"))
    }

    @Test fun coachDoesNotAllowUnfilledSetToBeRecorded() {
        val line = CoachConversation.inSession(chest, 1, false, 0, false,
            "", "", false, null, false)
        assertEquals(CoachAction.NONE, line.action)
        assertNull(line.actionLabel)
        assertTrue(line.message.contains("Quanti kg"))
    }

    @Test fun validSetCanBeRecordedWithActualValues() {
        val line = CoachConversation.inSession(chest, 2, false, 0, false,
            "32.5", "10", true, 2, false)
        assertEquals(CoachAction.RECORD_SET, line.action)
        assertEquals(CoachPose.CHEST, line.pose)
        assertTrue(line.message.contains("32.5"))
        assertTrue(line.message.contains("10"))
    }

    @Test fun restAndFinishHaveDifferentActions() {
        val rest = CoachConversation.inSession(chest, 1, true, 38, false,
            "", "", false, null, false)
        assertEquals(CoachAction.END_REST, rest.action)
        assertEquals(CoachPose.REST, rest.pose)
        val finish = CoachConversation.inSession(null, 1, false, 0, true,
            "", "", false, null, false)
        assertEquals(CoachAction.FINISH, finish.action)
        assertEquals(CoachPose.DONE, finish.pose)
    }

    @Test fun negativeRestIsClamped() {
        val line = CoachConversation.inSession(chest, 1, true, -11, false,
            "", "", false, null, false)
        assertTrue(line.message.contains("0 secondi"))
    }
}
