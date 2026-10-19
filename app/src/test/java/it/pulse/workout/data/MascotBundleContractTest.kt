package it.pulse.workout.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MascotBundleContractTest {
    @Test fun allNamedPosesHaveDistinctBundledImageNames() {
        assertEquals(11, CoachPose.entries.size)
        assertEquals(11, CoachPose.entries.map { it.asset }.toSet().size)
        assertTrue(CoachPose.entries.all { it.asset.endsWith(".webp") })
    }
    @Test fun focusedMusclesUseRealMuscleSpecificPoses() {
        assertEquals(CoachPose.CHEST, CoachConversation.poseForMuscle("PETTORALI"))
        assertEquals(CoachPose.BACK, CoachConversation.poseForMuscle("SCHIENA"))
        assertEquals(CoachPose.FOREARMS, CoachConversation.poseForMuscle("AVAMBRACCI"))
        assertEquals(CoachPose.LEGS, CoachConversation.poseForMuscle("QUADRICIPITI"))
        assertEquals(CoachPose.ABS, CoachConversation.poseForMuscle("ADDOMINALI"))
    }
}
