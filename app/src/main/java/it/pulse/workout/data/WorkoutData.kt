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
    val completedAt: Long = 0L
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

    fun warmups(exercise: Exercise): List<SetEntry> =
        entries.entries.filter { it.key.startsWith("warmup:${exercise.id}:") && it.value.done }
            .sortedBy { it.value.completedAt }.map { it.value }

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
        at: Long
    ): WorkoutDraft {
        require(guidedExerciseIndex in day.exercises.indices) { "Sessione completata" }
        require(weight.toDoubleOrNull() != null && weight.toDouble() >= 0.0)
        require((reps.toIntOrNull() ?: 0) in 1..999)
        val exercise = day.exercises[guidedExerciseIndex]
        val completed = SetEntry(weight, reps, done = true, warmup = warmup, completedAt = at)
        val eventKey = if (warmup) {
            "warmup:${exercise.id}:${warmups(exercise).size + 1}"
        } else {
            key(exercise.id, guidedWorkSet)
        }
        val nextEntries = entries + (eventKey to completed)
        val next = if (warmup) {
            copy(entries = nextEntries)
        } else if (guidedWorkSet < exercise.sets) {
            copy(entries = nextEntries, guidedWorkSet = guidedWorkSet + 1)
        } else {
            copy(entries = nextEntries, guidedExerciseIndex = guidedExerciseIndex + 1, guidedWorkSet = 1)
        }
        val remaining = next.guidedExerciseIndex < day.exercises.size
        val seconds = if (warmup) exercise.restSeconds.coerceAtMost(90) else exercise.restSeconds
        return next.copy(restUntilMillis = if (remaining) at + seconds * 1000L else 0L)
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
