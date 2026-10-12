package it.pulse.workout.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkoutRestartTest {
    @Test fun restartClearsSetsWarmupsExerciseIndexAndRestButPreservesProgram() {
        val day = WorkoutDay(
            id = "mon", weekday = "LUNEDÌ", name = "Spalle e braccia",
            description = "", duration = "75 min",
            exercises = listOf(Exercise(
                id = "shoulder_press", name = "Shoulder press",
                muscle = "SPALLE", sets = 3, target = "8–12", restSeconds = 90
            ))
        )
        val previous = WorkoutDraft(
            dayId = "mon",
            startedAt = 100L,
            daySnapshot = day,
            entries = mapOf(
                "shoulder_press:1" to SetEntry(weight = "30", reps = "10", done = true),
                "warmup:shoulder_press:1" to SetEntry(weight = "15", reps = "10",
                    done = true, warmup = true),
                "shoulder_press:2" to SetEntry(weight = "35", reps = "8", done = false)
            ),
            guidedExerciseIndex = 1,
            guidedWorkSet = 3,
            restUntilMillis = 230_000L
        )
        val now = 500_000L
        val newDraft = previous.restarted(now)
        assertEquals("mon", newDraft.dayId)
        assertEquals(day, newDraft.daySnapshot)
        assertEquals(now, newDraft.startedAt)
        assertTrue(newDraft.entries.isEmpty())
        assertEquals(0, newDraft.guidedExerciseIndex)
        assertEquals(1, newDraft.guidedWorkSet)
        assertEquals(0L, newDraft.restUntilMillis)
        // Verifica che l'oggetto originario rimanga immutato durante la conferma.
        assertEquals(3, previous.entries.size)
        assertEquals(100L, previous.startedAt)
    }

    @Test fun restartOnEmptyWorkoutStartsFreshTimer() {
        val session = WorkoutDraft(dayId = "fri", startedAt = 120L)
        val restarted = session.restarted(350L)
        assertEquals(350L, restarted.startedAt)
        assertTrue(restarted.entries.isEmpty())
        assertEquals(0L, restarted.restUntilMillis)
    }
}
