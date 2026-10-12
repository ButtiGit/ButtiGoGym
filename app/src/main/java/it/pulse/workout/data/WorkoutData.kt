package it.pulse.workout.data

import java.util.Calendar

data class Exercise(
    val id: String,
    val name: String,
    val muscle: String,
    val sets: Int,
    val target: String
)

data class WorkoutDay(
    val id: String,
    val weekday: String,
    val name: String,
    val description: String,
    val duration: String,
    val exercises: List<Exercise>
) {
    val setCount: Int get() = exercises.sumOf { it.sets }
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

    fun upcoming(): WorkoutDay {
        val day = Calendar.getInstance().get(Calendar.DAY_OF_WEEK)
        return when (day) {
            Calendar.MONDAY, Calendar.SATURDAY, Calendar.SUNDAY -> days[0]
            Calendar.TUESDAY, Calendar.WEDNESDAY -> days[1]
            else -> days[2]
        }
    }
}

data class SetEntry(
    val weight: String = "",
    val reps: String = "",
    val done: Boolean = false
)

data class WorkoutDraft(
    val dayId: String,
    val startedAt: Long,
    val entries: Map<String, SetEntry> = emptyMap()
) {
    fun entry(exercise: Exercise, setIndex: Int): SetEntry = entries[key(exercise.id, setIndex)] ?: SetEntry()
    fun update(exercise: Exercise, setIndex: Int, entry: SetEntry): WorkoutDraft =
        copy(entries = entries + (key(exercise.id, setIndex) to entry))

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
    val entries: Map<String, SetEntry> = emptyMap()
)
