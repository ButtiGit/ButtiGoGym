package it.pulse.workout.data

import java.util.Locale

enum class ProgressionDirection {
    FIRST_SESSION, ADD_REPETITIONS, KEEP_WEIGHT, CONSIDER_INCREASE, REVIEW_EFFORT, TIME_BASED
}

data class PreviousWorkingSet(
    val number: Int, val weightKg: Double, val repetitions: Int, val actualRir: Int?
)

data class ExerciseProgression(
    val direction: ProgressionDirection,
    val headline: String,
    val explanation: String,
    val previousDateMillis: Long?,
    val previousSets: List<PreviousWorkingSet>,
    val increaseStepKg: Double? = null,
    val nextRepetitionTarget: Int? = null
) {
    fun previous(number: Int): PreviousWorkingSet? = previousSets.firstOrNull { it.number == number }

    /** Esempio per la singola serie; non sostituisce il valore inserito dall'utente. */
    fun suggestedWeight(number: Int): Double? =
        increaseStepKg?.let { increment ->
            previous(number)?.weightKg?.takeIf { it > 0.0 }?.plus(increment)
        }
}

/** Consiglio conservativo a doppia progressione: ripetizioni prima del peso. */
object WorkoutProgressionAdvisor {
    private val rangeRegex = Regex("^([0-9]{1,3})\\s*(?:[-–—]\\s*([0-9]{1,3}))?$")

    fun repetitionRange(raw: String): IntRange? {
        val m = rangeRegex.matchEntire(raw.trim()) ?: return null
        val lower = m.groupValues[1].toIntOrNull() ?: return null
        val upper = m.groupValues[2].takeIf { it.isNotBlank() }?.toIntOrNull() ?: lower
        return if (lower in 1..999 && upper in lower..999) lower..upper else null
    }

    fun advise(exercise: Exercise, dayId: String, history: List<SessionRecord>): ExerciseProgression {
        val previous = history.asSequence()
            .filter { it.dayId == dayId }
            .sortedByDescending { it.completedAt }
            .mapNotNull { record ->
                val snapshot = record.daySnapshot
                    ?: WorkoutPlan.days.firstOrNull { it.id == record.dayId }
                val oldExercise = snapshot?.exercises?.firstOrNull { it.id == exercise.id }
                    ?: return@mapNotNull null
                if (oldExercise.timeBased != exercise.timeBased) return@mapNotNull null
                val sets = (1..oldExercise.sets).mapNotNull { number ->
                    val entry = record.entries[WorkoutDraft.key(exercise.id, number)]
                        ?: return@mapNotNull null
                    val kg = entry.weight.replace(',', '.').toDoubleOrNull()
                    val reps = entry.reps.toIntOrNull()
                    if (!entry.done || entry.warmup ||
                        kg == null || !kg.isFinite() || kg !in 0.0..10_000.0 ||
                        reps == null || reps !in 1..999) null
                    else PreviousWorkingSet(number, kg, reps, entry.rir?.takeIf { it in 0..5 })
                }
                if (sets.isEmpty()) null else record.completedAt to sets
            }.firstOrNull()

        if (previous == null) return ExerciseProgression(
            ProgressionDirection.FIRST_SESSION,
            "Prima sessione registrata",
            "Nessun risultato confrontabile: scegli un carico controllabile e annota il RIR reale.",
            null, emptyList()
        )
        val (date, sets) = previous
        val range = repetitionRange(exercise.target)
        if (exercise.timeBased) return ExerciseProgression(
            ProgressionDirection.TIME_BASED, "Confronta la durata",
            "Le serie a tempo non ricevono incrementi di carico automatici.",
            date, sets
        )
        if (range == null) return ExerciseProgression(
            ProgressionDirection.KEEP_WEIGHT, "Controlla le ripetizioni",
            "Il target non è un intervallo numerico: mantieni i valori sotto controllo.",
            date, sets
        )

        val allSetsAvailable = (1..exercise.sets).all { n -> sets.any { it.number == n } }
        val allHitTop = allSetsAvailable && sets
            .filter { it.number <= exercise.sets }
            .all { it.repetitions >= range.last }

        if (allHitTop) {
            val comparable = sets.filter { it.number <= exercise.sets }
            val allRirRecorded = comparable.all { it.actualRir != null }
            val sufficientlyEasy = comparable.all { (it.actualRir ?: -1) >= 2 }
            val allLoaded = comparable.all { it.weightKg > 0.0 }
            if (allRirRecorded && sufficientlyEasy && allLoaded) {
                val step = when (exercise.muscle.uppercase(Locale.ROOT)) {
                    "BICIPITI", "TRICIPITI", "SPALLE", "AVAMBRACCI", "POLPACCI" -> 1.0
                    else -> 2.5
                }
                return ExerciseProgression(
                    ProgressionDirection.CONSIDER_INCREASE,
                    "Valuta +${formatKg(step)} kg",
                    "Tutte le serie erano al limite alto (${range.last} rep) con RIR reale almeno 2. " +
                        "Prova il piccolo incremento solo se la tecnica resta buona.",
                    date, sets, increaseStepKg = step
                )
            }
            return ExerciseProgression(
                ProgressionDirection.KEEP_WEIGHT, "Conferma il carico",
                if (!allRirRecorded) "Hai raggiunto il target, ma manca il RIR reale di alcune serie. " +
                    "Registralo per valutare un aumento."
                else "Target raggiunto con fatica: consolida tecnica e recupero prima di aumentare.",
                date, sets
            )
        }

        val worstEffort = sets.any { it.repetitions < range.first && (it.actualRir ?: 5) <= 1 }
        if (worstEffort) return ExerciseProgression(
            ProgressionDirection.REVIEW_EFFORT, "Non aumentare il peso",
            "Almeno una serie era sotto il minimo di ${range.first} rep con RIR 0–1. " +
                "Considera più recupero o un carico leggermente inferiore.",
            date, sets
        )

        val candidate = sets.filter { it.repetitions in 1 until range.last }
            .minOfOrNull { (it.repetitions + 1).coerceAtMost(range.last) }
        return if (candidate != null) ExerciseProgression(
            ProgressionDirection.ADD_REPETITIONS, "Punta a una ripetizione in più",
            "Prima porta tutte le serie verso ${range.last} ripetizioni, senza forzare la tecnica.",
            date, sets, nextRepetitionTarget = candidate
        ) else ExerciseProgression(
            ProgressionDirection.KEEP_WEIGHT, "Consolida la prestazione",
            "Riprova con un carico controllato e registra il RIR effettivo.",
            date, sets
        )
    }

    private fun formatKg(value: Double): String =
        if (value % 1.0 == 0.0) value.toInt().toString()
        else String.format(Locale.ITALIAN, "%.1f", value)
}
