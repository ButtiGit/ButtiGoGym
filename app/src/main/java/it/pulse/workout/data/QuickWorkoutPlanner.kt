package it.pulse.workout.data

/**
 * La modalità rapida è una snapshot di sessione, NON modifica la scheda.
 * Giorno ed esercizi conservano gli ID, così storico e calendario restano compatibili.
 * Se mancano esercizi della variante, non indovinare sostituzioni per l'utente.
 */
object QuickWorkoutPlanner {
    private val selected = mapOf(
        "mon" to listOf(
            "shoulder_press" to 2, "lateral_raise" to 2,
            "bicep_curl" to 2, "tricep_pushdown" to 2,
            "mon_chest_recall" to 1, "mon_lat_recall" to 1,
            "reverse_curl_ez" to 2
        ),
        "wed" to listOf(
            "hack_squat" to 2, "rdl" to 2, "leg_curl" to 2,
            "calf_raise" to 2, "crunch" to 2
        ),
        "fri" to listOf(
            "incline_chest_machine" to 2, "pulldown" to 2,
            "row_chest" to 2, "fri_lateral_recall" to 1,
            "fri_leg_press_recall" to 1, "fri_leg_curl_recall" to 1,
            "reverse_wrist_curl" to 2
        )
    )

    fun isQuick(day: WorkoutDay): Boolean = day.name.endsWith(" · Rapido")

    fun create(day: WorkoutDay): WorkoutDay? {
        if (day.optional || isQuick(day)) return null
        val limits = selected[day.id] ?: return null
        val byId = day.exercises.associateBy { it.id }
        if (limits.any { (id, _) -> id !in byId }) return null
        val targets = limits.toMap()
        val exercises = day.exercises.filter { it.id in targets }.map { exercise ->
            exercise.copy(sets = minOf(exercise.sets, targets.getValue(exercise.id)),
                optional = false)
        }
        if (exercises.size != limits.size) return null
        return day.copy(
            name = day.name + " · Rapido",
            description = "Modalità rapida: volumi ridotti rispetto alla scheda completa. " +
                "Per mantenere la costanza quando hai poco tempo.",
            duration = "35–45 min circa",
            exercises = exercises,
            cooldown = "Se possibile fai 5–10 minuti di camminata facile. " +
                "Non saltare il riscaldamento prima dei carichi pesanti."
        )
    }
}
