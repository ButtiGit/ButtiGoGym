package it.pulse.workout.data

import java.util.Calendar

data class Exercise(
    val id: String,
    val name: String,
    val muscle: String,
    val sets: Int,
    val target: String,
    val restSeconds: Int = 90,
    val equipment: String = "",
    val rir: String = "1–3",
    val note: String = "",
    val alternative: String = "",
    val gifUrl: String = "",
    val primaryMuscles: List<String> = emptyList(),
    val secondaryMuscles: List<String> = emptyList(),
    /** Richiami facoltativi, non necessari per completare la seduta. */
    val optional: Boolean = false,
    /** Il valore inserito nel campo reps rappresenta secondi, non ripetizioni. */
    val timeBased: Boolean = false,
    val group: String = ""
)

data class WorkoutDay(
    val id: String,
    val weekday: String,
    val name: String,
    val description: String,
    val duration: String,
    val exercises: List<Exercise>,
    val optional: Boolean = false,
    val cooldown: String = ""
) {
    /** Obiettivo principale: i richiami opzionali non riducono la percentuale di completamento. */
    val setCount: Int get() = exercises.filterNot { it.optional }.sumOf { it.sets }
    val optionalSetCount: Int get() = exercises.filter { it.optional }.sumOf { it.sets }
    val allSetCount: Int get() = setCount + optionalSetCount
}

/** Scheda iniziale modificabile nel codice; nessun peso personale è inventato. */
object WorkoutPlan {
    val days = listOf(
        WorkoutDay(
            id = "mon", weekday = "LUNEDÌ", name = "Spalle & braccia",
            description = "Deltoidi, bicipiti, tricipiti e avambracci",
            duration = "50–65 min",
            exercises = listOf(
                Exercise("shoulder_press", "Shoulder press manubri", "SPALLE", 3, "8–10"),
                Exercise("lateral_raise", "Alzate laterali", "SPALLE", 3, "12–15"),
                Exercise("bicep_curl", "Curl manubri", "BICIPITI", 3, "10–12"),
                Exercise("hammer_curl", "Hammer curl", "BICIPITI", 3, "10–12"),
                Exercise("tricep_pushdown", "Pushdown tricipiti", "TRICIPITI", 3, "10–12"),
                Exercise("wrist_curl", "Wrist curl", "AVAMBRACCI", 3, "12–15")
            )
        ),
        WorkoutDay(
            id = "wed", weekday = "MERCOLEDÌ", name = "Gambe & core",
            description = "Gambe complete e addominali",
            duration = "55–70 min",
            exercises = listOf(
                Exercise("squat", "Squat / Leg press", "QUADRICIPITI", 3, "8–12"),
                Exercise("rdl", "Stacco rumeno", "FEMORALI", 3, "8–10"),
                Exercise("leg_extension", "Leg extension", "QUADRICIPITI", 3, "12–15"),
                Exercise("leg_curl", "Leg curl", "FEMORALI", 3, "10–12"),
                Exercise("calf_raise", "Calf raise", "POLPACCI", 3, "12–15"),
                Exercise("crunch", "Crunch al cavo / macchina", "ADDOME", 3, "12–15")
            )
        ),
        WorkoutDay(
            id = "fri", weekday = "VENERDÌ", name = "Petto & schiena",
            description = "Spinte, trazioni e rematori",
            duration = "55–70 min",
            exercises = listOf(
                Exercise("bench", "Chest press / Panca", "PETTO", 3, "8–10"),
                Exercise("incline", "Panca inclinata manubri", "PETTO", 3, "8–12"),
                Exercise("fly", "Croci ai cavi", "PETTO", 3, "12–15"),
                Exercise("pulldown", "Lat machine", "SCHIENA", 3, "8–12"),
                Exercise("seated_row", "Rematore al cavo", "SCHIENA", 3, "8–12"),
                Exercise("pullover", "Pullover al cavo", "SCHIENA", 3, "12–15")
            )
        )
    )

    fun upcoming(days: List<WorkoutDay> = WorkoutPlan.days): WorkoutDay {
        val day = Calendar.getInstance().get(Calendar.DAY_OF_WEEK)
        return when (day) {
            Calendar.MONDAY, Calendar.SATURDAY, Calendar.SUNDAY -> days.first { !it.optional }
            Calendar.TUESDAY, Calendar.WEDNESDAY -> days.filterNot { it.optional }[1]
            else -> days.filterNot { it.optional }[2]
        }
    }
}

data class SetEntry(
    val weight: String = "",
    val reps: String = "",
    val done: Boolean = false,
    val warmup: Boolean = false,
    val completedAt: Long = 0L,
    /** RIR realmente percepito nella serie, distinto dal target della scheda. null = non registrato. */
    val rir: Int? = null
)

/** Una singola serie effettivamente salvata, incluso il riscaldamento. */
data class RecordedWorkoutSet(
    val exercise: Exercise,
    val number: Int,
    val warmup: Boolean,
    val entry: SetEntry
)

data class WorkoutDraft(
    val dayId: String,
    val startedAt: Long,
    val entries: Map<String, SetEntry> = emptyMap(),
    val daySnapshot: WorkoutDay? = null,
    val guidedExerciseIndex: Int = 0,
    val guidedWorkSet: Int = 1,
    val restUntilMillis: Long = 0L
) {
    /** Ricomincia la seduta da zero: cancella anche bozze di ripetizioni,
     *  riscaldamenti, serie completate, indice esercizio e recupero.
     *  Mantiene la snapshot del programma e NON modifica lo storico.
     */
    fun restarted(atMillis: Long): WorkoutDraft = copy(
        startedAt = atMillis,
        entries = emptyMap(),
        guidedExerciseIndex = 0,
        guidedWorkSet = 1,
        restUntilMillis = 0L
    )

    fun entry(exercise: Exercise, setIndex: Int): SetEntry = entries[key(exercise.id, setIndex)] ?: SetEntry()
    fun update(exercise: Exercise, setIndex: Int, entry: SetEntry): WorkoutDraft =
        copy(entries = entries + (key(exercise.id, setIndex) to entry))

    /** Mostra le serie salvate di TUTTI gli esercizi, anche quelli già superati. */
    fun recordedSets(day: WorkoutDay): List<RecordedWorkoutSet> =
        day.exercises.flatMap { exercise ->
            val savedWarmups = entries.mapNotNull { (key, entry) ->
                val prefix = "warmup:${exercise.id}:"
                if (key.startsWith(prefix) && entry.done && entry.warmup) {
                    val number = key.removePrefix(prefix).toIntOrNull()
                    number?.takeIf { it >= 1 }?.let {
                        RecordedWorkoutSet(exercise, it, true, entry)
                    }
                } else null
            }.sortedBy { it.number }
            val working = (1..exercise.sets).mapNotNull { number ->
                entry(exercise, number).takeIf { it.done }?.let {
                    RecordedWorkoutSet(exercise, number, false, it)
                }
            }
            savedWarmups + working
        }

    fun warmups(exercise: Exercise): List<SetEntry> =
        entries.entries.filter { it.key.startsWith("warmup:${exercise.id}:") && it.value.done }
            .sortedBy { it.value.completedAt }.map { it.value }

    private fun validatedSetValues(weight: String, reps: String): Pair<String, String> {
        val normalizedWeight = weight.trim().replace(',', '.')
        val kg = normalizedWeight.toDoubleOrNull()
        require(kg != null && kg.isFinite() && kg in 0.0..10_000.0) {
            "Inserisci un peso compreso tra 0 e 10.000 kg"
        }
        val normalizedReps = reps.trim()
        require((normalizedReps.toIntOrNull() ?: 0) in 1..999) {
            "Inserisci un valore compreso tra 1 e 999"
        }
        return normalizedWeight to normalizedReps
    }

    /** Corregge SOLO i valori della serie: non riavvia timer o recupero. */
    fun correctRecordedSet(
        day: WorkoutDay, exerciseId: String, number: Int,
        warmup: Boolean, weight: String, reps: String, rir: Int? = null
    ): WorkoutDraft {
        val exercise = day.exercises.firstOrNull { it.id == exerciseId }
            ?: error("Esercizio non appartenente a questa sessione")
        require(number >= 1 && (warmup || number <= exercise.sets))
        val key = if (warmup) "warmup:$exerciseId:$number" else key(exerciseId, number)
        val previous = entries[key]
        require(previous != null && previous.done && previous.warmup == warmup) {
            "La serie non è più registrata"
        }
        val (kg, amount) = validatedSetValues(weight, reps)
        require(rir == null || rir in 0..5) { "RIR valido da 0 a 5" }
        return copy(entries = entries + (key to previous.copy(
            weight = kg, reps = amount, rir = if (warmup) null else rir
        )))
    }

    /** Annulla una serie completata. Le serie successive rimangono salvate. */
    fun cancelRecordedSet(
        day: WorkoutDay, exerciseId: String, number: Int, warmup: Boolean
    ): WorkoutDraft {
        val exerciseIndex = day.exercises.indexOfFirst { it.id == exerciseId }
        require(exerciseIndex >= 0) { "Esercizio non trovato" }
        val exercise = day.exercises[exerciseIndex]
        require(number >= 1 && (warmup || number <= exercise.sets))
        val eventKey = if (warmup) "warmup:$exerciseId:$number" else key(exerciseId, number)
        val previous = entries[eventKey]
        require(previous != null && previous.done && previous.warmup == warmup) {
            "La serie è già stata annullata"
        }
        val revisedEntries = if (warmup) entries - eventKey else
            entries + (eventKey to previous.copy(done = false, warmup = false, completedAt = 0L))
        // In caso di annullamento di una serie precedente, torniamo a quella.
        // Non cancelliamo le serie successive già salvate.
        val rewind = !warmup && (exerciseIndex < guidedExerciseIndex ||
            (exerciseIndex == guidedExerciseIndex && number < guidedWorkSet))
        return copy(
            entries = revisedEntries,
            guidedExerciseIndex = if (rewind) exerciseIndex else guidedExerciseIndex,
            guidedWorkSet = if (rewind) number else guidedWorkSet,
            restUntilMillis = 0L
        )
    }

    /** Un tap annulla davvero la registrazione più recente, anche se era un warmup. */
    fun undoLastRecordedSet(day: WorkoutDay): WorkoutDraft {
        val latest = recordedSets(day).filter { it.entry.completedAt > 0L }
            .maxByOrNull { it.entry.completedAt } ?: return this
        return cancelRecordedSet(day, latest.exercise.id, latest.number, latest.warmup)
    }

    /** Salta un richiamo facoltativo senza registrare serie artificiali. */
    fun skipOptionalExercise(day: WorkoutDay): WorkoutDraft {
        require(day.exercises.getOrNull(guidedExerciseIndex)?.optional == true) {
            "Non puoi saltare un esercizio principale"
        }
        return copy(guidedExerciseIndex = guidedExerciseIndex + 1, guidedWorkSet = 1,
            restUntilMillis = 0L)
    }

    fun recordSet(
        day: WorkoutDay,
        weight: String,
        reps: String,
        warmup: Boolean,
        at: Long,
        rir: Int? = null
    ): WorkoutDraft {
        require(guidedExerciseIndex in day.exercises.indices) { "Sessione completata" }
        val (kg, amount) = validatedSetValues(weight, reps)
        require(rir == null || rir in 0..5) { "RIR valido da 0 a 5" }
        val exercise = day.exercises[guidedExerciseIndex]
        val eventKey = if (warmup) {
            // Evita collisioni se è stata annullata una serie riscaldamento intermedia.
            val prefix = "warmup:${exercise.id}:"
            val last = entries.keys.mapNotNull {
                if (it.startsWith(prefix)) it.removePrefix(prefix).toIntOrNull()
                else null
            }.maxOrNull() ?: 0
            "$prefix${last + 1}"
        } else {
            require(guidedWorkSet in 1..exercise.sets)
            require(!entry(exercise, guidedWorkSet).done) {
                "Questa serie è già completata"
            }
            key(exercise.id, guidedWorkSet)
        }
        val completed = SetEntry(kg, amount, done = true, warmup = warmup,
            completedAt = at, rir = if (warmup) null else rir)
        val nextEntries = entries + (eventKey to completed)
        val next = if (warmup) copy(entries = nextEntries) else {
            // Dopo una correzione retroattiva, salta le serie successive già fatte.
            var nextExerciseIndex = guidedExerciseIndex
            var nextSet = guidedWorkSet + 1
            while (nextExerciseIndex < day.exercises.size) {
                val target = day.exercises[nextExerciseIndex]
                while (nextSet <= target.sets &&
                    nextEntries[key(target.id, nextSet)]?.done == true) nextSet++
                if (nextSet <= target.sets) break
                nextExerciseIndex++
                nextSet = 1
            }
            copy(entries = nextEntries, guidedExerciseIndex = nextExerciseIndex,
                guidedWorkSet = nextSet)
        }
        val hasNext = next.guidedExerciseIndex < day.exercises.size
        val seconds = if (warmup) exercise.restSeconds.coerceAtMost(90) else exercise.restSeconds
        return next.copy(restUntilMillis = if (hasNext) at + seconds * 1000L else 0L)
    }

    companion object {
        fun key(exerciseId: String, setIndex: Int) = "$exerciseId:$setIndex"
    }
}

data class SessionRecord(
    val id: Long,
    val dayId: String,
    val completedAt: Long,
    val durationMinutes: Int,
    val completedSets: Int,
    val totalSets: Int,
    val entries: Map<String, SetEntry> = emptyMap(),
    val daySnapshot: WorkoutDay? = null,
    val durationSeconds: Int = durationMinutes * 60,
    /** Solo il nome di un'immagine salvata nella directory privata dell'app. */
    val photoFileName: String = ""
)

/** Versione validata della scheda scaricata da GitHub, separata dai progressi locali. */
data class WorkoutCatalog(
    val revision: String,
    val title: String,
    val days: List<WorkoutDay>,
    val source: String,
    val guidelines: List<String> = emptyList()
)
