package it.pulse.workout.data

import java.time.LocalDate

enum class WorkoutEnergy(val label: String) {
    LOW("Bassa"), NORMAL("Media"), HIGH("Alta")
}
enum class LastNightSleep(val label: String) {
    SHORT("<6 h"), NORMAL("6–8 h"), LONG(">8 h")
}
enum class WorkoutSoreness(val label: String) {
    NONE("Nessuno"), MILD("Lieve"), STRONG("Forte")
}
data class PreWorkoutCheckIn(
    val date: LocalDate,
    val energy: WorkoutEnergy,
    val sleep: LastNightSleep,
    val soreness: WorkoutSoreness
)
enum class TrainingReadiness { AS_PLANNED, REDUCE_LOAD, CONSIDER_REST }

/** Orientamento prudente basato esclusivamente su autovalutazioni, non diagnosi. */
object PreWorkoutCheckInRules {
    fun evaluate(entry: PreWorkoutCheckIn): TrainingReadiness = when {
        entry.energy == WorkoutEnergy.LOW && entry.sleep == LastNightSleep.SHORT ->
            TrainingReadiness.CONSIDER_REST
        entry.soreness == WorkoutSoreness.STRONG ||
            entry.energy == WorkoutEnergy.LOW || entry.sleep == LastNightSleep.SHORT ->
            TrainingReadiness.REDUCE_LOAD
        else -> TrainingReadiness.AS_PLANNED
    }
    fun recommendation(entry: PreWorkoutCheckIn): String = when (evaluate(entry)) {
        TrainingReadiness.AS_PLANNED ->
            "Puoi valutare la seduta prevista. Mantieni comunque tecnica e RIR."
        TrainingReadiness.REDUCE_LOAD ->
            "Valuta la seduta rapida o un carico più leggero, senza forzare muscoli doloranti."
        TrainingReadiness.CONSIDER_REST ->
            "Poco sonno e poca energia: considera recupero o una seduta molto leggera."
    }
}
