package it.pulse.workout.data

import org.junit.Assert.*
import org.junit.Test

class ExerciseSubstitutionCatalogTest {
    private val chest = Exercise(
        id="incline_chest_machine", name="Chest press inclinata",
        muscle="PETTO", sets=3, target="8–12", equipment="Chest press",
        primaryMuscles=listOf("chest")
    )
    private val row = Exercise("row_chest", "Rematore macchina", "SCHIENA",3,"8–12")
    private val day=WorkoutDay("fri","VENERDÌ","Petto e schiena","","80–100 min",
        listOf(chest,row))

    @Test fun substitutesOnlyCurrentSnapshotAndNotOriginalPlan() {
        val draft=WorkoutDraft(dayId=day.id,startedAt=123L,daySnapshot=day)
        val alternative=ExerciseSubstitutionCatalog.alternatives(chest,day).first().exercise
        val changed=draft.substituteCurrentExercise(day,alternative)
        assertEquals("incline_chest_machine",day.exercises.first().id)
        assertEquals(alternative.id,changed.daySnapshot!!.exercises.first().id)
        assertEquals(row,changed.daySnapshot!!.exercises.last())
        assertEquals(3,changed.daySnapshot!!.exercises.first().sets)
        assertNotEquals(chest.id,alternative.id)
    }

    @Test fun alreadyRecordedSeriesBlocksReplacementWithoutLosingData() {
        val draft=WorkoutDraft(day.id,123L,daySnapshot=day)
            .recordSet(day,"30","10",false,1000L,rir=2)
        val alternative=ExerciseSubstitutionCatalog.alternatives(chest,day).first().exercise
        assertThrows(IllegalArgumentException::class.java) {
            draft.copy(guidedExerciseIndex=0,guidedWorkSet=2)
                .substituteCurrentExercise(day,alternative)
        }
        assertEquals("30",draft.entry(chest,1).weight)
    }

    @Test fun completedWarmupAlsoPreventsReplacement() {
        val warmed=WorkoutDraft(day.id,123L,daySnapshot=day)
            .recordSet(day,"10","12",true,1000L)
        val replacement=ExerciseSubstitutionCatalog.alternatives(chest,day).first().exercise
        assertThrows(IllegalArgumentException::class.java) {
            warmed.substituteCurrentExercise(day,replacement)
        }
    }

    @Test fun alternativeNeverSharesMachineIdentifierWithOriginal() {
        val choices=ExerciseSubstitutionCatalog.alternatives(chest,day)
        assertTrue(choices.size > 2)
        assertTrue(choices.any { it.exercise.id.startsWith("panatta_") })
        assertTrue(choices.none { it.exercise.id == chest.id })
        assertTrue(choices.all { it.exercise.gifUrl.isEmpty() })
    }

    @Test fun otherExercisesAndPendingValuesSurviveReplacement() {
        val preset=WorkoutDraft(day.id,123L,
            entries=mapOf(WorkoutDraft.key(chest.id,1) to SetEntry("40","10"),
                WorkoutDraft.key(row.id,1) to SetEntry("20","12")),
            daySnapshot=day)
        val replacement=ExerciseSubstitutionCatalog.alternatives(chest,day).first().exercise
        val next=preset.substituteCurrentExercise(day,replacement)
        assertFalse(next.entries.containsKey(WorkoutDraft.key(chest.id,1)))
        assertEquals("20",next.entries[WorkoutDraft.key(row.id,1)]!!.weight)
        assertEquals(0L,next.restUntilMillis)
    }
}
