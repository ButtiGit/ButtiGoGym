package it.pulse.workout.data

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import java.time.DayOfWeek
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** Gruppi muscolari visualizzati dal manichino. Gli ID sono stabili e usati anche nella scheda remota. */
data class MuscleGroup(
    val id: String, val label: String,
    val recoveryHours: Int, val view: String
) {
    companion object {
        val all = listOf(
            MuscleGroup("traps", "Trapezi", 48, "back"),
            MuscleGroup("shoulders", "Spalle", 48, "front"),
            MuscleGroup("rear_delts", "Deltoidi posteriori", 48, "back"),
            MuscleGroup("chest", "Pettorali", 48, "front"),
            MuscleGroup("biceps", "Bicipiti", 48, "front"),
            MuscleGroup("triceps", "Tricipiti", 48, "back"),
            MuscleGroup("forearms", "Avambracci", 36, "front"),
            MuscleGroup("lats", "Dorsali", 60, "back"),
            MuscleGroup("upper_back", "Schiena alta", 60, "back"),
            MuscleGroup("lower_back", "Lombari", 60, "back"),
            MuscleGroup("abs", "Addominali", 36, "front"),
            MuscleGroup("obliques", "Obliqui", 36, "front"),
            MuscleGroup("quads", "Quadricipiti", 72, "front"),
            MuscleGroup("hamstrings", "Femorali", 72, "back"),
            MuscleGroup("glutes", "Glutei", 72, "back"),
            MuscleGroup("calves", "Polpacci", 48, "back")
        )
        val ids: Set<String> = all.map { it.id }.toSet()
    }
}

/** Fallback per gli storici creati prima del campo primaryMuscles. */
object ExerciseMuscleMapping {
    private val map: Map<String, Pair<List<String>, List<String>>> = mapOf(
        "shoulder_press" to (listOf("shoulders") to listOf("triceps", "traps")),
        "leg_press" to (listOf("quads", "glutes") to listOf("hamstrings", "calves")),
        "incline" to (listOf("chest") to listOf("shoulders", "triceps")),
        "pulldown" to (listOf("lats") to listOf("biceps", "upper_back")),
        "lateral_raise" to (listOf("shoulders") to listOf("traps")),
        "lateral_raise_db" to (listOf("shoulders") to listOf("traps")),
        "tricep_pushdown" to (listOf("triceps") to emptyList()),
        "bicep_curl" to (listOf("biceps") to listOf("forearms")),
        "rdl" to (listOf("hamstrings", "glutes") to listOf("lower_back")),
        "leg_curl" to (listOf("hamstrings") to emptyList()),
        "leg_extension" to (listOf("quads") to emptyList()),
        "seated_row" to (listOf("upper_back", "lats") to listOf("biceps")),
        "calf_raise" to (listOf("calves") to emptyList()),
        "crunch" to (listOf("abs") to listOf("obliques")),
        "bench" to (listOf("chest") to listOf("triceps", "shoulders")),
        "row_chest" to (listOf("upper_back", "lats") to listOf("biceps")),
        "split_squat" to (listOf("quads", "glutes") to listOf("hamstrings")),
        "reverse_fly" to (listOf("rear_delts", "upper_back") to listOf("traps")),
        "triceps_overhead" to (listOf("triceps") to emptyList()),
        "bonus_cable_lateral" to (listOf("shoulders") to listOf("traps")),
        "bonus_hammer" to (listOf("biceps", "forearms") to emptyList()),
        "bonus_pushdown" to (listOf("triceps") to emptyList()),
        "bonus_calf" to (listOf("calves") to emptyList()),
        "bonus_knee" to (listOf("abs") to listOf("obliques")),
        // Prima scheda dell'app, compatibilità storica.
        "hammer_curl" to (listOf("biceps", "forearms") to emptyList()),
        "wrist_curl" to (listOf("forearms") to emptyList()),
        "squat" to (listOf("quads", "glutes") to listOf("hamstrings")),
        "fly" to (listOf("chest") to emptyList()),
        "pullover" to (listOf("lats") to listOf("triceps"))
    )
    fun forExercise(exercise: Exercise): Pair<List<String>, List<String>> {
        if (exercise.primaryMuscles.isNotEmpty()) {
            val primary = exercise.primaryMuscles.filter { it in MuscleGroup.ids }.distinct()
            val secondary = exercise.secondaryMuscles.filter { it in MuscleGroup.ids && it !in primary }.distinct()
            return primary to secondary
        }
        return map[exercise.id] ?: when (exercise.muscle.uppercase()) {
            "SPALLE" -> listOf("shoulders") to emptyList()
            "SPALLE POSTERIORI" -> listOf("rear_delts") to emptyList()
            "PETTO" -> listOf("chest") to emptyList()
            "SCHIENA" -> listOf("upper_back", "lats") to emptyList()
            "BICIPITI" -> listOf("biceps") to emptyList()
            "TRICIPITI" -> listOf("triceps") to emptyList()
            "AVAMBRACCI" -> listOf("forearms") to emptyList()
            "FEMORALI" -> listOf("hamstrings") to emptyList()
            "QUADRICIPITI", "GAMBE" -> listOf("quads", "glutes") to emptyList()
            "POLPACCI" -> listOf("calves") to emptyList()
            "ADDOME" -> listOf("abs") to emptyList()
            else -> emptyList<String>() to emptyList()
        }
    }
}

enum class MuscleRecovery { NEVER, TRAINED_TODAY, RECOVERING, READY }

data class MuscleProgress(
    val group: MuscleGroup,
    val state: MuscleRecovery,
    val hoursToReady: Int,
    val lastTrainingMillis: Long?,
    val streakWeeks: Int,
    val trainedWeeks: Int,
    val workingSets: Int,
    val volumeKg: Double,
    val xp: Int,
    val level: Int,
    val levelCurrentXp: Int,
    val levelRequiredXp: Int,
    val progressToNext: Float,
    val topSet: String?
)

object MuscleAnalytics {
    /** Livelli gamificati, NON livelli scientifici di forza o massa muscolare. */
    fun xpForSet(weightKg: Double, reps: Int, primary: Boolean): Int {
        val weight = weightKg.coerceIn(0.0, 400.0)
        val repetitions = reps.coerceIn(1, 40)
        val factor = if (primary) 1.0 else 0.35
        return (repetitions * (1.0 + sqrt(weight) / 4.0) * factor).roundToInt().coerceAtLeast(1)
    }

    fun levelThreshold(level: Int): Int {
        if (level <= 1) return 0
        return (150.0 * (level - 1).toDouble().pow(1.55))
            .roundToInt()
    }

    fun levelFromXp(xp: Int): Int {
        var level = 1
        while (level < 99 && xp >= levelThreshold(level + 1)) level++
        return level
    }

    fun evaluate(
        history: List<SessionRecord>,
        nowMillis: Long = System.currentTimeMillis(),
        zone: ZoneId = ZoneId.systemDefault()
    ): List<MuscleProgress> {
        data class Acc(
            var xp: Int = 0,
            var sets: Int = 0,
            var volume: Double = 0.0,
            var last: Long? = null,
            var readyAt: Long = 0,
            val trainedWeekStarts: MutableSet<LocalDate> = mutableSetOf(),
            var bestEstimated1Rm: Double = 0.0,
            var topSet: String? = null
        )
        val groups = MuscleGroup.all.associate { it.id to Acc() }
        val today = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()

        history.forEach { record ->
            val day = record.daySnapshot ?: WorkoutPlan.days.firstOrNull { it.id == record.dayId } ?: return@forEach
            day.exercises.forEach { exercise ->
                val (primary, secondary) = ExerciseMuscleMapping.forExercise(exercise)
                val targets = primary.map { it to true } + secondary.map { it to false }
                (1..exercise.sets).forEach { setNumber ->
                    val entry = record.entries[WorkoutDraft.key(exercise.id, setNumber)] ?: return@forEach
                    if (!entry.done || entry.warmup) return@forEach
                    val weight = entry.weight.replace(',', '.').toDoubleOrNull() ?: 0.0
                    val reps = entry.reps.toIntOrNull() ?: return@forEach
                    if (weight < 0 || !weight.isFinite() || reps <= 0) return@forEach
                    val timestamp = entry.completedAt.takeIf { it > 0L } ?: record.completedAt
                    if (timestamp > nowMillis + 60_000L) return@forEach
                    val date = Instant.ofEpochMilli(timestamp).atZone(zone).toLocalDate()
                    val week = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                    targets.forEach { (id, isPrimary) ->
                        val acc = groups[id] ?: return@forEach
                        val definition = MuscleGroup.all.firstOrNull { it.id == id } ?: return@forEach
                        acc.xp += xpForSet(weight, reps, isPrimary)
                        if (isPrimary) {
                            acc.sets++
                            acc.volume += weight * reps
                            val estimated = weight * (1.0 + reps.coerceIn(1, 30) / 30.0)
                            if (estimated > acc.bestEstimated1Rm) {
                                acc.bestEstimated1Rm = estimated
                                acc.topSet = "${exercise.name}: ${entry.weight} kg × ${entry.reps}"
                            }
                        }
                        acc.trainedWeekStarts.add(week)
                        acc.last = maxOf(acc.last ?: 0L, timestamp)
                        val hours = if (isPrimary) definition.recoveryHours else (definition.recoveryHours / 2)
                        acc.readyAt = maxOf(acc.readyAt, timestamp + hours * 3_600_000L)
                    }
                }
            }
        }
        val thisWeek = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        return MuscleGroup.all.map { group ->
            val acc = groups.getValue(group.id)
            val last = acc.last
            val state = when {
                last == null -> MuscleRecovery.NEVER
                Instant.ofEpochMilli(last).atZone(zone).toLocalDate() == today -> MuscleRecovery.TRAINED_TODAY
                acc.readyAt > nowMillis -> MuscleRecovery.RECOVERING
                else -> MuscleRecovery.READY
            }
            var cursor = if (thisWeek in acc.trainedWeekStarts) thisWeek else thisWeek.minusWeeks(1)
            var streak = 0
            while (streak < 104 && cursor in acc.trainedWeekStarts) {
                streak++
                cursor = cursor.minusWeeks(1)
            }
            val xp = acc.xp.coerceAtLeast(0)
            val level = levelFromXp(xp)
            val base = levelThreshold(level)
            val next = levelThreshold((level + 1).coerceAtMost(100))
            val needed = (next - base).coerceAtLeast(1)
            MuscleProgress(
                group = group,
                state = state,
                hoursToReady = if (acc.readyAt > nowMillis) {
                    ((acc.readyAt - nowMillis + 3_599_999) / 3_600_000L).coerceAtMost(500L).toInt()
                } else 0,
                lastTrainingMillis = last,
                streakWeeks = streak,
                trainedWeeks = acc.trainedWeekStarts.size,
                workingSets = acc.sets,
                volumeKg = acc.volume,
                xp = xp,
                level = level,
                levelCurrentXp = (xp - base).coerceAtLeast(0),
                levelRequiredXp = needed,
                progressToNext = ((xp - base).toFloat() / needed).coerceIn(0f, 1f),
                topSet = acc.topSet
            )
        }
    }
}
