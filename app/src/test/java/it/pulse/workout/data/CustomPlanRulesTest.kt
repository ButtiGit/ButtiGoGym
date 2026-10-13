package it.pulse.workout.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class CustomPlanRulesTest {
    private val chest = Exercise("bench", "Chest press", "PETTO", 3, "8–12", restSeconds = 90)
    private val row = Exercise("row", "Rematore", "SCHIENA", 3, "10–12", restSeconds = 120)
    private val curl = Exercise("curl", "Curl", "BICIPITI", 2, "12–15", restSeconds = 60)
    private val original = listOf(chest, row, curl)

    @Test fun reorderPreservesExerciseIdentityAndParameters() {
        val moved = CustomPlanRules.move(original, 2, 0)
        assertEquals(listOf("curl", "bench", "row"), moved.map { it.id })
        assertEquals(curl, moved.first())
        assertEquals(original, listOf(chest, row, curl)) // immutable source unchanged
        assertEquals(original, CustomPlanRules.move(moved, 0, 2))
    }

    @Test fun editKeepsStableIdWhileChangingSetsTargetAndRest() {
        val updated = chest.copy(sets = 5, target = "6–8", restSeconds = 180)
        val result = CustomPlanRules.replace(original, updated)
        assertEquals(chest.id, result.first().id)
        assertEquals(5, result.first().sets)
        assertEquals("6–8", result.first().target)
        assertEquals(180, result.first().restSeconds)
        assertEquals(original[1], result[1])
        assertEquals(original[2], result[2])
    }

    @Test fun addCustomExerciseAndRemoveExistingExercise() {
        val custom = Exercise("personal_aabbcc", "Nuovo esercizio", "AVAMBRACCI",
            4, "10–15", restSeconds = 75, optional = true)
        val added = CustomPlanRules.append(original, custom)
        assertEquals(4, added.size)
        assertTrue(added.last().optional)
        assertEquals(original, CustomPlanRules.remove(added, custom.id))
        assertFalse(CustomPlanRules.newExerciseId() == CustomPlanRules.newExerciseId())
    }

    @Test fun doesNotAllowEmptyOrDuplicateExerciseLists() {
        assertThrows(IllegalArgumentException::class.java) {
            CustomPlanRules.remove(listOf(chest), chest.id)
        }
        assertThrows(IllegalArgumentException::class.java) {
            CustomPlanRules.append(original, chest)
        }
        assertThrows(IllegalArgumentException::class.java) {
            CustomPlanRules.validate(emptyList())
        }
    }

    @Test fun validatesSetsRestNamesAndTargets() {
        assertThrows(IllegalArgumentException::class.java) {
            CustomPlanRules.replace(original, chest.copy(sets = 0))
        }
        assertThrows(IllegalArgumentException::class.java) {
            CustomPlanRules.replace(original, chest.copy(sets = 9))
        }
        assertThrows(IllegalArgumentException::class.java) {
            CustomPlanRules.replace(original, chest.copy(restSeconds = 301))
        }
        assertThrows(IllegalArgumentException::class.java) {
            CustomPlanRules.replace(original, chest.copy(restSeconds = 29))
        }
        assertThrows(IllegalArgumentException::class.java) {
            CustomPlanRules.replace(original, chest.copy(target = ""))
        }
        assertThrows(IllegalArgumentException::class.java) {
            CustomPlanRules.replace(original, chest.copy(name = ""))
        }
    }

    @Test fun muscleSelectionMatchesTheAnatomicalProgressMap() {
        assertEquals(listOf("forearms"), CustomPlanRules.muscleIds("AVAMBRACCI"))
        assertEquals(listOf("chest"), CustomPlanRules.muscleIds("petto"))
        assertEquals(listOf("upper_back", "lats"), CustomPlanRules.muscleIds("SCHIENA"))
        assertEquals(listOf("abs", "obliques"), CustomPlanRules.muscleIds("CORE"))
        assertTrue(CustomPlanRules.muscleIds("UNKNOWN").isEmpty())
    }

    @Test fun limitExerciseCountToThirty() {
        val atLimit = (1..30).map { number ->
            chest.copy(id = "personal_$number", name = "Esercizio $number")
        }
        assertEquals(30, CustomPlanRules.validate(atLimit).size)
        assertThrows(IllegalArgumentException::class.java) {
            CustomPlanRules.append(atLimit, chest)
        }
    }

    @Test fun rejectsInvalidMoveAndUnknownEditWithoutMutatingInput() {
        assertThrows(IllegalArgumentException::class.java) {
            CustomPlanRules.move(original, -1, 0)
        }
        assertThrows(IllegalArgumentException::class.java) {
            CustomPlanRules.replace(original, chest.copy(id = "changed"))
        }
        assertThrows(IllegalArgumentException::class.java) {
            CustomPlanRules.remove(original, "unknown")
        }
        assertEquals(listOf("bench", "row", "curl"), original.map { it.id })
    }
}
