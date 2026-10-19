package it.pulse.workout.data

import java.util.Locale

/**
 * Dialoghi deterministici: il coach non inventa dati e le sue scelte
 * scrivono soltanto nei normali campi e comandi della sessione.
 */
enum class CoachPose(val asset: String, val zoom: Float = 1.25f, val panY: Float = 0f) {
    HOME("home.webp", 1.15f),
    READY("ready.webp", 1.25f),
    REST("rest.webp", 1.15f),
    DONE("done.webp", 1.20f),
    ABS("abs.webp", 1.55f, 12f),
    CHEST("chest.webp", 1.65f, 36f),
    BACK("back.webp", 1.55f, 30f),
    ARMS("arms.webp", 1.60f, 34f),
    LEGS("legs.webp", 1.42f, -18f),
    SHOULDERS("shoulders.webp", 1.55f, 38f),
    FOREARMS("forearms.webp", 1.65f, 30f)
}

enum class CoachAction { NONE, RECORD_SET, END_REST, FINISH }
data class CoachLine(
    val title: String,
    val message: String,
    val pose: CoachPose,
    val action: CoachAction = CoachAction.NONE,
    val actionLabel: String? = null
)

object CoachConversation {
    fun poseForMuscle(raw: String): CoachPose {
        val muscle = raw.uppercase(Locale.ROOT)
        return when {
            listOf("AVAMBR", "WRIST", "GRIP", "PRESA").any(muscle::contains) -> CoachPose.FOREARMS
            listOf("SPALL", "DELTOID").any(muscle::contains) -> CoachPose.SHOULDERS
            listOf("PETT", "PECT", "CHEST").any(muscle::contains) -> CoachPose.CHEST
            listOf("SCHIEN", "DORS", "TRAPEZ", "BACK").any(muscle::contains) -> CoachPose.BACK
            listOf("BICIP", "TRICIP", "BRACC", "ARM").any(muscle::contains) -> CoachPose.ARMS
            listOf("ADDOME", "ADDOM", "CORE", "OBLIQ", "ABS").any(muscle::contains) -> CoachPose.ABS
            listOf("QUAD", "FEMOR", "GLUTE", "POLPACC", "GAMB", "LEG").any(muscle::contains) -> CoachPose.LEGS
            else -> CoachPose.READY
        }
    }

    fun inSession(
        exercise: Exercise?,
        setNumber: Int,
        resting: Boolean,
        remainingSeconds: Long,
        completed: Boolean,
        weight: String,
        reps: String,
        valid: Boolean,
        rir: Int?,
        warmup: Boolean
    ): CoachLine {
        if (completed) return CoachLine(
            "Allenamento completato!",
            "Hai chiuso tutte le serie previste. Vuoi salvare la seduta e i tuoi progressi?",
            CoachPose.DONE, CoachAction.FINISH, "Salva la seduta"
        )
        if (resting) return CoachLine(
            "Recuperiamo insieme",
            "Mancano ${remainingSeconds.coerceAtLeast(0)} secondi. Respira e recupera. Ti senti già pronto?",
            CoachPose.REST, CoachAction.END_REST, "Sono pronto"
        )
        if (exercise == null) return CoachLine(
            "Eccomi qui", "Scegli un esercizio e iniziamo.", CoachPose.READY
        )
        val title = "Serie ${setNumber.coerceAtLeast(1)} · ${exercise.muscle.lowercase(Locale.ITALIAN)}"
        val targetUnit = if (exercise.timeBased) "secondi" else "ripetizioni"
        if (!valid) return CoachLine(
            title,
            "Tocca a ${exercise.name}. Quanti kg e quanti $targetUnit hai fatto? Compila i campi sotto e ti aiuto a registrarli.",
            poseForMuscle(exercise.muscle)
        )
        val feeling = when (rir) {
            null -> "Com'è andata? Puoi indicarmi quanto margine avevi, oppure registrare subito."
            0 -> "Era al limite: tieni conto della fatica prima della prossima serie."
            2 -> "Ottimo, un buon margine per una serie controllata."
            4 -> "Ti è rimasto margine: valuta i carichi nelle prossime sedute."
            else -> "RIR $rir ricevuto, usiamolo nel registro della serie."
        }
        return CoachLine(
            title,
            "Hai inserito $reps $targetUnit a $weight kg. $feeling",
            poseForMuscle(exercise.muscle),
            CoachAction.RECORD_SET,
            if (warmup) "Salva riscaldamento" else "Registra questa serie"
        )
    }
}
