package it.pulse.workout.data

import java.util.UUID

/** Modifiche locali alle singole sedute, senza cambiare il catalogo di GitHub. */
object CustomPlanRules {
    const val MAX_EXERCISES = 30

    fun validate(exercises: List<Exercise>): List<Exercise> {
        require(exercises.size in 1..MAX_EXERCISES) {
            "Una seduta deve contenere da 1 a $MAX_EXERCISES esercizi"
        }
        require(exercises.map { it.id }.distinct().size == exercises.size) {
            "Gli esercizi devono avere identificatori diversi"
        }
        exercises.forEach { exercise ->
            require(exercise.id.matches(Regex("[A-Za-z0-9_-]{1,100}"))) {
                "Identificatore esercizio non valido"
            }
            require(exercise.name.trim().length in 2..90) {
                "Il nome deve avere da 2 a 90 caratteri"
            }
            require(exercise.muscle.trim().length in 2..40) { "Indica un gruppo muscolare" }
            require(exercise.sets in 1..8) { "Sono ammesse da 1 a 8 serie" }
            require(exercise.target.trim().length in 1..32) { "Indica il range di ripetizioni" }
            require(exercise.restSeconds in 30..300) { "Recupero da 30 a 300 secondi" }
        }
        return exercises
    }

    /** Conserva l'identità dell'esercizio, fondamentale per lo storico dei pesi. */
    fun replace(exercises: List<Exercise>, updated: Exercise): List<Exercise> =
        validate(exercises.map { if (it.id == updated.id) updated else it }.also {
            require(exercises.any { old -> old.id == updated.id }) {
                "Esercizio da modificare non trovato"
            }
        })

    fun move(exercises: List<Exercise>, from: Int, to: Int): List<Exercise> {
        require(from in exercises.indices && to in exercises.indices)
        return validate(exercises.toMutableList().apply { add(to, removeAt(from)) })
    }

    fun remove(exercises: List<Exercise>, id: String): List<Exercise> {
        require(exercises.any { it.id == id }) { "Esercizio inesistente" }
        return validate(exercises.filterNot { it.id == id })
    }

    fun append(exercises: List<Exercise>, exercise: Exercise): List<Exercise> =
        validate(exercises + exercise)

    fun newExerciseId(): String = "personal_${UUID.randomUUID().toString().replace("-", "")}"
}
