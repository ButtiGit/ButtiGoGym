package it.pulse.workout.data

/** Promemoria visivo per la singola serie, sempre non vincolante. */
object WorkoutSetCue {
    fun description(exercise: Exercise, advice: ExerciseProgression?, number: Int): String? {
        val old = advice?.previous(number) ?: return null
        if (exercise.timeBased) return "Riparti dalla durata precedente mantenendo il controllo."
        val top = WorkoutProgressionAdvisor.repetitionRange(exercise.target)?.last
            ?: return "Ripeti con tecnica controllata: non forzare le ripetizioni."
        if (advice.direction == ProgressionDirection.REVIEW_EFFORT ||
            (old.actualRir != null && old.actualRir <= 1 && old.repetitions < top))
            return "Consolida la tecnica, non forzare peso o ripetizioni."
        if (advice.direction == ProgressionDirection.CONSIDER_INCREASE)
            return "Valuta un piccolo aumento solo se la tecnica rimane stabile."
        if (old.repetitions in 1 until top &&
            (old.actualRir == null || old.actualRir >= 2))
            return "Oggi prova ${old.repetitions + 1} ripetizioni con tecnica pulita."
        return "Conferma ${old.repetitions} ripetizioni con controllo e RIR adeguato."
    }
}
