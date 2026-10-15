package it.pulse.workout.data

import org.junit.Assert.*
import org.junit.Test

class QuickWorkoutPlannerTest {
    private fun complete(id: String, names: List<String>): WorkoutDay = WorkoutDay(
        id, "TEST", "Allenamento completo", "", "90–105 min",
        names.map { Exercise(it, it, "TEST", 3, "8–12", 90) },
        cooldown = "Camminata"
    )
    private val mon = listOf("shoulder_press", "lateral_raise", "reverse_fly",
        "bicep_curl", "hammer_curl", "tricep_pushdown", "triceps_overhead",
        "mon_chest_recall", "mon_lat_recall", "reverse_curl_ez", "wrist_curl")
    private val wed = listOf("hack_squat", "split_squat", "leg_extension", "rdl",
        "leg_curl", "calf_raise", "crunch", "hanging_knee")
    private val fri = listOf("incline_chest_machine", "bench", "pec_deck",
        "pulldown", "row_chest", "fri_lateral_recall", "fri_leg_press_recall",
        "fri_leg_curl_recall", "reverse_wrist_curl", "farmers_walk")

    @Test fun reducesSetsAndPreservesExerciseIdsAndDays() {
        for ((id, names, sets) in listOf(Triple("mon",mon,12),
            Triple("wed",wed,10), Triple("fri",fri,11))) {
            val full = complete(id,names)
            val short = requireNotNull(QuickWorkoutPlanner.create(full))
            assertEquals(sets,short.setCount)
            assertEquals(full.id,short.id)
            assertTrue(QuickWorkoutPlanner.isQuick(short))
            assertTrue(short.exercises.all { e ->
                full.exercises.any { it.id == e.id && it.sets >= e.sets }
            })
            assertEquals(names.size*3,full.setCount)
            assertNull(QuickWorkoutPlanner.create(short))
        }
    }

    @Test fun failsSafeIfCustomExerciseRemoved() {
        assertNull(QuickWorkoutPlanner.create(complete("mon",
            mon.filterNot { it == "mon_lat_recall" })))
        assertNull(QuickWorkoutPlanner.create(complete("bonus",mon)))
    }

    @Test fun respectsLocallyReducedSetsAndReordering() {
        val full=complete("mon",mon.reversed())
        val customized=full.copy(exercises=full.exercises.map {
            if(it.id=="lateral_raise") it.copy(sets=1) else it
        })
        val short=requireNotNull(QuickWorkoutPlanner.create(customized))
        assertEquals(11,short.setCount)
        assertEquals(1,short.exercises.first { it.id=="lateral_raise" }.sets)
        assertEquals(customized.exercises.filter { ex ->
            short.exercises.any { it.id==ex.id }
        }.map { it.id },short.exercises.map { it.id })
    }

    @Test fun snapshotCanRecordWithoutChangingFullDay() {
        val full=complete("wed",wed)
        val short=requireNotNull(QuickWorkoutPlanner.create(full))
        val draft=WorkoutDraft(short.id,1000L,daySnapshot=short)
        val result=draft.recordSet(short,"35","10",false,2000L,rir=2)
        assertTrue(result.entry(short.exercises.first(),1).done)
        assertEquals(10,short.setCount)
        assertEquals(24,full.setCount)
    }
}
