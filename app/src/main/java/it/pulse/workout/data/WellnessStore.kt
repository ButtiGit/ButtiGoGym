package it.pulse.workout.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

/** Registro locale delle abitudini. Nessun dato viene trasmesso a GitHub. */
data class WellnessData(
    val stepsByDate: Map<String, Int> = emptyMap(),
    val restDates: Set<String> = emptySet(),
    val healthStepsByDate: Map<String, Int> = emptyMap()
) {
    /** Health Connect replaces, never adds to, manually/sensor counted steps. */
    fun stepsOn(day: LocalDate): Int =
        healthStepsByDate[day.toString()] ?: stepsByDate[day.toString()] ?: 0
    fun restedOn(day: LocalDate): Boolean = restDates.contains(day.toString())
}

class WellnessStore(context: Context) {
    private val preferences = context.applicationContext
        .getSharedPreferences("buttigogym_wellness_v1", Context.MODE_PRIVATE)

    fun read(): WellnessData = runCatching {
        val json = JSONObject(preferences.getString("wellness", "{}") ?: "{}")
        val values = json.optJSONObject("steps") ?: JSONObject()
        val steps = buildMap {
            val keys = values.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                if (runCatching { LocalDate.parse(key) }.isSuccess) {
                    put(key, values.optInt(key, 0).coerceIn(0, 200000))
                }
            }
        }
        val health = json.optJSONObject("healthSteps") ?: JSONObject()
        val healthByDate = buildMap {
            val keys = health.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                if (runCatching { LocalDate.parse(key) }.isSuccess) {
                    put(key, health.optInt(key, 0).coerceIn(0, 200000))
                }
            }
        }
        val entries = json.optJSONArray("rest") ?: JSONArray()
        val rest = (0 until entries.length()).mapNotNull {
            val str = entries.optString(it)
            str.takeIf { runCatching { LocalDate.parse(it) }.isSuccess }
        }.toSet()
        WellnessData(steps, rest, healthByDate)
    }.getOrElse { WellnessData() }

    fun updateSteps(day: LocalDate, steps: Int): WellnessData {
        require(steps in 0..200000)
        val data = read()
        return save(data.copy(stepsByDate = data.stepsByDate + (day.toString() to steps)))
    }

    /** Atomic import of daily totals; previous entries remain for days outside the window. */
    fun updateHealthSteps(days: Map<String, Int>): WellnessData {
        require(days.all { (date, steps) ->
            runCatching { LocalDate.parse(date) }.isSuccess && steps in 0..200000
        })
        val data = read()
        return save(data.copy(healthStepsByDate = data.healthStepsByDate + days))
    }

    /** Called when permission is revoked: do not retain Health Connect data. */
    fun clearHealthSteps(): WellnessData {
        val data = read()
        return if (data.healthStepsByDate.isEmpty()) data
            else save(data.copy(healthStepsByDate = emptyMap()))
    }

    /** A shared source for dashboard recovery and calendar-marked rest days. */
    fun setRest(day: LocalDate, resting: Boolean): WellnessData {
        val data = read()
        val dates = data.restDates.toMutableSet()
        if (resting) dates.add(day.toString()) else dates.remove(day.toString())
        return save(data.copy(restDates = dates))
    }

    fun toggleRest(day: LocalDate): WellnessData = setRest(day, !read().restedOn(day))

    private fun save(data: WellnessData): WellnessData {
        val limit = LocalDate.now().minusDays(730)
        val validSteps = data.stepsByDate.filterKeys {
            runCatching { LocalDate.parse(it) >= limit }.getOrDefault(false)
        }
        val validRest = data.restDates.filter {
            runCatching { LocalDate.parse(it) >= limit }.getOrDefault(false)
        }.toSet()
        val validHealth = data.healthStepsByDate.filterKeys {
            runCatching { LocalDate.parse(it) >= limit }.getOrDefault(false)
        }
        val persisted = WellnessData(validSteps, validRest, validHealth)
        val json = JSONObject().apply {
            put("steps", JSONObject().apply {
                persisted.stepsByDate.forEach { (day, count) -> put(day, count) }
            })
            put("rest", JSONArray().apply { persisted.restDates.sorted().forEach { put(it) } })
            put("healthSteps", JSONObject().apply {
                persisted.healthStepsByDate.forEach { (day, count) -> put(day, count) }
            })
        }
        check(preferences.edit().putString("wellness", json.toString()).commit()) {
            "Impossibile salvare i dati locali"
        }
        return persisted
    }
}

data class StreakSummary(
    val generalDays: Int,
    val workoutWeeks: Int,
    val restDays: Int,
    val stepDays: Int,
    val todaySteps: Int,
    val workoutDaysThisWeek: Int,
    val restedToday: Boolean,
    val workoutToday: Boolean
)

/**
 * Regole:
 * - generale: passi >= 6000 + allenamento registrato OPPURE riposo segnato ogni giorno;
 * - allenamenti: settimane consecutive con tre sessioni principali DISTINTE (lun/mer/ven);
 * - riposo: giorni di riposo registrati consecutivamente, saltando i giorni allenati;
 * - passi: giorni consecutivi con almeno 6000 passi.
 *
 * Il giorno corrente non spezza uno streak ancora incompleto: si parte da ieri.
 */
object StreakCalculator {
    const val STEP_GOAL = 6000
    private const val HISTORY_LIMIT = 365
    private val requiredWorkoutIds = setOf("mon", "wed", "fri")

    fun calculate(
        today: LocalDate,
        data: WellnessData,
        history: List<SessionRecord>,
        weeklyWorkoutTarget: Int = 3
    ): StreakSummary {
        require(weeklyWorkoutTarget in 1..7)

        val timezone = ZoneId.systemDefault()
        val byDate = history.filter { it.completedSets > 0 }.groupBy {
            Instant.ofEpochMilli(it.completedAt).atZone(timezone).toLocalDate()
        }
        fun hasWorkout(day: LocalDate): Boolean = !byDate[day].isNullOrEmpty()
        fun stepsMet(day: LocalDate): Boolean = data.stepsOn(day) >= STEP_GOAL
        fun dailyMet(day: LocalDate): Boolean =
            stepsMet(day) && (hasWorkout(day) || data.restedOn(day))

        fun dailyStreak(predicate: (LocalDate) -> Boolean): Int {
            var day = if (predicate(today)) today else today.minusDays(1)
            var count = 0
            while (count < HISTORY_LIMIT && predicate(day)) {
                count++
                day = day.minusDays(1)
            }
            return count
        }

        // I giorni allenati vengono ignorati, non azzerano lo streak di recupero.
        // Contatore preciso: interrompe la ricerca al primo giorno non recuperato.
        fun recoveryStreak(): Int {
            var day = if (!hasWorkout(today) && !data.restedOn(today)) today.minusDays(1) else today
            var count = 0
            var scanned = 0
            while (scanned++ < HISTORY_LIMIT) {
                if (hasWorkout(day)) { day = day.minusDays(1); continue }
                if (!data.restedOn(day)) break
                count++
                day = day.minusDays(1)
            }
            return count
        }

        fun weekStart(day: LocalDate): LocalDate =
            day.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val workoutsInWeek: (LocalDate) -> Int = { start ->
            history.asSequence().filter { it.completedSets > 0 }
                .filter {
                    val date = Instant.ofEpochMilli(it.completedAt).atZone(timezone).toLocalDate()
                    date >= start && date < start.plusDays(7)
                }
                // Preserve historical 3-split streaks; custom goals count distinct
                // training dates (including optional workouts), never duplicate saves.
                .let { sessions ->
                    if (weeklyWorkoutTarget == 3) sessions
                        .filter { it.dayId in requiredWorkoutIds }
                        .map { it.dayId }.distinct().count()
                    else sessions.map {
                        Instant.ofEpochMilli(it.completedAt).atZone(timezone).toLocalDate()
                    }.distinct().count()
                }
        }
        val currentWeek = weekStart(today)
        val completedThisWeek = workoutsInWeek(currentWeek)
        var cursor = if (completedThisWeek >= weeklyWorkoutTarget)
            currentWeek else currentWeek.minusWeeks(1)
        var weeks = 0
        while (weeks < 104 && workoutsInWeek(cursor) >= weeklyWorkoutTarget) {
            weeks++
            cursor = cursor.minusWeeks(1)
        }

        return StreakSummary(
            generalDays = dailyStreak(::dailyMet),
            workoutWeeks = weeks,
            restDays = recoveryStreak(),
            stepDays = dailyStreak(::stepsMet),
            todaySteps = data.stepsOn(today),
            workoutDaysThisWeek = completedThisWeek,
            restedToday = data.restedOn(today),
            workoutToday = hasWorkout(today)
        )
    }
}
