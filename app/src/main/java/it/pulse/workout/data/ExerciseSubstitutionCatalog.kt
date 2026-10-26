package it.pulse.workout.data

/**
 * Alternative curate di MOVIMENTO, mai conversioni automatiche dei carichi.
 * L'ID include l'esercizio originale e la variante per non contaminare
 * gli storici (i kg dei manubri non sono quelli della chest press).
 */
data class ExerciseSubstitution(val exercise: Exercise, val guidance: String)

object ExerciseSubstitutionCatalog {
    private data class Candidate(val key: String, val name: String,
                                 val equipment: String, val guidance: String)
    private fun alt(key: String, name: String, equipment: String, guidance: String) =
        Candidate(key, name, equipment, guidance)

    private val options = mapOf(
        "shoulder_press" to listOf(
            alt("db", "Shoulder press manubri", "Manubri", "Mantieni controllo e schiena stabile."),
            alt("smith", "Shoulder press alla Smith machine", "Smith machine", "Regola seduta e traiettoria.")
        ),
        "incline_chest_machine" to listOf(
            alt("db", "Distensioni inclinate con manubri", "Panca inclinata, manubri", "Ricalibra il peso per lato."),
            alt("smith", "Panca inclinata alla Smith", "Smith machine", "Non trasferire i kg dalla macchina.")
        ),
        "bench" to listOf(
            alt("db", "Distensioni manubri su panca piana", "Panca e manubri", "Ogni manubrio ha un carico distinto."),
            alt("barbell", "Panca piana bilanciere", "Panca e bilanciere", "Usa supporti di sicurezza o uno spotter.")
        ),
        "pec_deck" to listOf(
            alt("cable", "Croci ai cavi", "Cavi", "Controlla la fase di ritorno."),
            alt("db", "Croci su panca con manubri", "Panca e manubri", "Usa un carico leggero senza forzare le spalle.")
        ),
        "pulldown" to listOf(
            alt("assisted", "Trazioni assistite", "Macchina trazioni assistite", "Registra l'assistenza, non confrontarla coi kg della lat."),
            alt("cable", "Lat pulldown al cavo alto", "Cavo alto e barra", "Ricalibra il carico sul nuovo attrezzo.")
        ),
        "mon_lat_recall" to listOf(
            alt("assisted", "Trazioni assistite", "Macchina trazioni assistite", "Registra l'assistenza separatamente."),
            alt("cable", "Lat pulldown al cavo alto", "Cavo alto", "Carico non confrontabile con la lat machine.")
        ),
        "row_chest" to listOf(
            alt("seated", "Rematore al cavo seduto", "Cavi", "Controlla busto e scapole."),
            alt("db", "Rematore manubrio su panca", "Panca e manubrio", "Registra il carico per braccio.")
        ),
        "lateral_raise" to listOf(
            alt("db", "Alzate laterali con manubri", "Manubri", "Niente slanci; carico per mano."),
            alt("machine", "Alzate laterali alla macchina", "Macchina deltoidi", "Ricalibra il carico.")
        ),
        "fri_lateral_recall" to listOf(
            alt("db", "Alzate laterali con manubri", "Manubri", "Usa il carico per manubrio."),
            alt("machine", "Alzate laterali alla macchina", "Macchina deltoidi", "Controlla la traiettoria.")
        ),
        "hack_squat" to listOf(
            alt("legpress", "Leg press", "Leg press", "Usa posizione e ampiezza controllate."),
            alt("goblet", "Goblet squat", "Manubrio", "Riduci il carico e cura la profondità.")
        ),
        "fri_leg_press_recall" to listOf(
            alt("goblet", "Goblet squat", "Manubrio", "Mantieni il richiamo leggero."),
            alt("split", "Affondi indietro con manubri", "Manubri", "Serie per gamba; annota il peso per mano.")
        ),
        "bonus_leg_press" to listOf(
            alt("goblet", "Goblet squat", "Manubrio", "Volume moderato, non andare a cedimento."),
            alt("split", "Affondi indietro con manubri", "Manubri", "Conta le ripetizioni per gamba.")
        ),
        "leg_curl" to listOf(
            alt("lying", "Leg curl sdraiato", "Macchina leg curl sdraiato", "Carichi diversi dalla versione seduta."),
            alt("band", "Leg curl con elastico", "Elastico", "Registra resistenza indicativa e ripetizioni.")
        ),
        "fri_leg_curl_recall" to listOf(
            alt("lying", "Leg curl sdraiato", "Macchina leg curl sdraiato", "Mantieni carico moderato."),
            alt("band", "Leg curl con elastico", "Elastico", "Controlla la tensione.")
        ),
        "bonus_leg_curl" to listOf(
            alt("lying", "Leg curl sdraiato", "Macchina leg curl sdraiato", "Carico moderato."),
            alt("band", "Leg curl con elastico", "Elastico", "Resistenza progressiva.")
        ),
        "leg_extension" to listOf(
            alt("stepup", "Step-up su box", "Box e manubri", "Ripetizioni per gamba, tecnica stabile."),
            alt("split", "Split squat con manubri", "Manubri", "Carico per mano, ginocchio controllato.")
        ),
        "calf_raise" to listOf(
            alt("db", "Calf raise in piedi con manubri", "Manubri", "Pausa breve in alto e discesa lenta."),
            alt("single", "Calf raise monopodalico", "Gradino", "Ripetizioni per polpaccio.")
        ),
        "tricep_pushdown" to listOf(
            alt("db", "French press manubrio", "Manubrio", "Gomiti stabili."),
            alt("ez", "French press bilanciere EZ", "Panca e bilanciere EZ", "Evita fastidi ai gomiti.")
        )
    )

    fun alternatives(current: Exercise, day: WorkoutDay): List<ExerciseSubstitution> =
        (options[current.id].orEmpty().mapNotNull { candidate ->
            val id = "alt_${current.id}_${candidate.key}"
            if (day.exercises.any { it.id == id }) null
            else ExerciseSubstitution(
                current.copy(id = id, name = candidate.name,
                    equipment = candidate.equipment,
                    note = candidate.guidance + " Carico e progressi separati dall'originale.",
                    alternative = "", gifUrl = ""),
                candidate.guidance
            )
        } + ExerciseLibrary.alternatives(current, day).map { candidate ->
            ExerciseSubstitution(candidate.asExercise(current),
                "Alternativa per gruppo muscolare; ricalibra il carico sul nuovo attrezzo.")
        }).distinctBy { it.exercise.id }
}

/** Cambia SOLO la snapshot della sessione in corso, mai il piano su GitHub. */
fun WorkoutDraft.substituteCurrentExercise(
    day: WorkoutDay, replacement: Exercise
): WorkoutDraft {
    val index = guidedExerciseIndex
    require(index in day.exercises.indices) { "Non c'è un esercizio da sostituire" }
    val original = day.exercises[index]
    require(ExerciseSubstitutionCatalog.alternatives(original, day)
        .any { it.exercise.id == replacement.id }) { "Alternativa non prevista" }
    require(day.exercises.none { it.id == replacement.id }) { "Esercizio già presente" }
    require((1..original.sets).none { entry(original, it).done } &&
        warmups(original).isEmpty()) {
        "Non puoi sostituire un esercizio con serie già registrate"
    }
    val updated = day.copy(exercises = day.exercises.toMutableList().also {
        it[index] = replacement
    })
    // Elimina solo campi precompilati della vecchia variante, mai altre serie.
    val staleKeys = (1..original.sets).map { WorkoutDraft.key(original.id, it) }.toSet()
    return copy(daySnapshot = updated, entries = entries.filterKeys { it !in staleKeys },
        guidedWorkSet = 1, restUntilMillis = 0L)
}
