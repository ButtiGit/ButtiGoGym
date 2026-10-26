package it.pulse.workout.data

import org.junit.Assert.*
import org.junit.Test

class ExerciseLibraryTest {
    @Test fun broadCatalogHasUniqueValidRecordsAndExplicitPanattaModels() {
        assertTrue(ExerciseLibrary.all.size >= 290)
        assertEquals(ExerciseLibrary.all.size, ExerciseLibrary.all.map { it.id }.distinct().size)
        assertEquals(36, ExerciseLibrary.all.count { it.brand == "Panatta" })
        ExerciseLibrary.all.forEach { CustomPlanRules.validate(listOf(it.asExercise())) }
        assertTrue(ExerciseLibrary.all.filter { it.brand == "Panatta" }.all { it.model.startsWith("1FW") })
    }
    @Test fun searchWorksByModelAndIgnoresCaseAndAccents() {
        assertEquals("panatta_1fw080", ExerciseLibrary.search("1FW080").single().id)
        assertEquals(ExerciseLibrary.search("LATERALI"), ExerciseLibrary.search("laterali"))
        assertEquals("e", ExerciseLibrary.normalized("È"))
        assertTrue(ExerciseLibrary.search("panatta", panattaOnly = true).all { it.brand == "Panatta" })
        assertTrue(ExerciseLibrary.search("", muscle = "PETTO", equipment = "Manubri").all { it.muscle == "PETTO" && it.equipment == "Manubri" })
    }
    @Test fun substitutionKeepsPrescriptionButHasItsOwnLoadIdentity() {
        val original = Exercise("bench", "Panca", "PETTO", 4, "6–8", 120)
        val replacement = ExerciseLibrary.search("1fw037").single().asExercise(original)
        assertNotEquals(original.id, replacement.id)
        assertEquals(4, replacement.sets); assertEquals("6–8", replacement.target)
        assertEquals(120, replacement.restSeconds)
        assertEquals(listOf("chest"), replacement.primaryMuscles)
    }
    @Test fun alternativesExcludeAlreadyPresentExercisesAndIncludePanatta() {
        val original = Exercise("bench", "Panca", "PETTO", 3, "8–12")
        val machine = ExerciseLibrary.search("1fw037").single().asExercise()
        val day = WorkoutDay("day", "LUN", "Petto", "", "60 min", listOf(original, machine))
        val options = ExerciseLibrary.alternatives(original, day)
        assertTrue(options.any { it.brand == "Panatta" })
        assertTrue(options.none { it.id == machine.id || it.muscle != "PETTO" })
        assertEquals("bench", original.id)
    }
    @Test fun timedVariantsDoNotInheritRepetitionPrescriptions() {
        val old = Exercise("bench", "Panca", "PETTO", 3, "8–12")
        val plank = ExerciseLibrary.all.first { it.name == "Plank" }.asExercise(old)
        assertTrue(plank.timeBased); assertEquals("30–60", plank.target)
    }
}
