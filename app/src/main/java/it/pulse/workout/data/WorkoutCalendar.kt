package it.pulse.workout.data

import android.content.Context
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/**
 * Explicit calendar decisions. REST remains in WellnessStore so the dashboard and
 * existing streaks share exactly the same recovery days.
 */
enum class CalendarEntryKind { WORKOUT, SKIPPED }

data class CalendarEntry(
    val date: LocalDate,
    val kind: CalendarEntryKind,
    val workoutDayId: String? = null
)

enum class CalendarDayStatus { COMPLETED, PLANNED, SKIPPED, REST, FREE }

data class CalendarMonthSummary(
    val completedDays: Int,
    val plannedUpcoming: Int,
    val skippedDays: Int,
    val restDays: Int,
    val plannedDue: Int,
    val plannedCompleted: Int,
    val unplannedCompleted: Int
) {
    /** null is more honest than 100% when nothing was planned. */
    val adherencePercent: Int?
        get() = if (plannedDue == 0) null
        else (plannedCompleted * 100.0 / plannedDue).toInt().coerceIn(0, 100)
}

object WorkoutCalendarRules {
    private val allowedDayId = Regex("^[a-zA-Z0-9_-]{1,80}$")

    fun validate(entry: CalendarEntry): CalendarEntry {
        require(entry.date.year in 2000..2100) { "Data fuori intervallo" }
        require(entry.workoutDayId == null ||
            allowedDayId.matches(entry.workoutDayId)) { "Scheda non valida" }
        require(entry.kind != CalendarEntryKind.WORKOUT || entry.workoutDayId != null) {
            "Seleziona un allenamento"
        }
        return entry
    }

    fun place(entries: List<CalendarEntry>, entry: CalendarEntry): List<CalendarEntry> {
        validate(entry)
        return (entries.filterNot { it.date == entry.date } + entry).sortedBy { it.date }
    }

    fun clear(entries: List<CalendarEntry>, date: LocalDate): List<CalendarEntry> =
        entries.filterNot { it.date == date }

    /** No silent overwrite; a destination plan must first be explicitly cleared. */
    fun move(
        entries: List<CalendarEntry>,
        from: LocalDate,
        to: LocalDate,
        destinationRest: Boolean = false,
        sourceCompleted: Boolean = false
    ): List<CalendarEntry> {
        val original = entries.firstOrNull { it.date == from &&
            it.kind == CalendarEntryKind.WORKOUT }
            ?: throw IllegalArgumentException("Nessun allenamento da spostare")
        require(!sourceCompleted) { "Un allenamento già completato non può essere spostato" }
        if (from == to) return entries
        require(!destinationRest && entries.none { it.date == to }) {
            "La nuova data contiene già un'attività: liberala prima"
        }
        return place(clear(entries, from), original.copy(date = to))
    }

    /** Deduplicate completed sessions by local calendar day. */
    fun completedDates(
        history: List<SessionRecord>, zone: ZoneId = ZoneId.systemDefault()
    ): Set<LocalDate> = history.asSequence()
        .filter { it.completedSets > 0 && it.completedAt > 0L }
        .map { Instant.ofEpochMilli(it.completedAt).atZone(zone).toLocalDate() }
        .toSet()

    fun status(
        date: LocalDate,
        entries: List<CalendarEntry>,
        completedDates: Set<LocalDate>,
        restDates: Set<LocalDate>,
        today: LocalDate
    ): CalendarDayStatus {
        if (date in completedDates) return CalendarDayStatus.COMPLETED
        if (date in restDates) return CalendarDayStatus.REST
        return when (entries.firstOrNull { it.date == date }?.kind) {
            CalendarEntryKind.SKIPPED -> CalendarDayStatus.SKIPPED
            CalendarEntryKind.WORKOUT ->
                if (date.isBefore(today)) CalendarDayStatus.SKIPPED
                else CalendarDayStatus.PLANNED
            null -> CalendarDayStatus.FREE
        }
    }

    fun summary(
        month: YearMonth,
        entries: List<CalendarEntry>,
        completedDates: Set<LocalDate>,
        restDates: Set<LocalDate>,
        today: LocalDate
    ): CalendarMonthSummary {
        val days = (1..month.lengthOfMonth()).map { month.atDay(it) }
        val completed = days.filter { it in completedDates }
        val due = days.filter { date ->
            val entry = entries.firstOrNull { it.date == date } ?: return@filter false
            date !in restDates &&
                (entry.kind == CalendarEntryKind.SKIPPED || date.isBefore(today))
        }
        val plannedCompleted = due.count { it in completedDates }
        val skipped = days.count {
            status(it, entries, completedDates, restDates, today) == CalendarDayStatus.SKIPPED
        }
        return CalendarMonthSummary(
            completedDays = completed.size,
            plannedUpcoming = days.count {
                status(it, entries, completedDates, restDates, today) == CalendarDayStatus.PLANNED
            },
            skippedDays = skipped,
            restDays = days.count {
                status(it, entries, completedDates, restDates, today) == CalendarDayStatus.REST
            },
            plannedDue = due.size,
            plannedCompleted = plannedCompleted,
            // Completed on a scheduled date (even today) is never an "extra".
            unplannedCompleted = completed.count { date ->
                entries.none { it.date == date &&
                    it.kind in setOf(CalendarEntryKind.WORKOUT, CalendarEntryKind.SKIPPED) }
            }
        )
    }
}

/**
 * Stores explicit workout/skip markers inside wellness_v1, already exported to
 * ZIP V2. Older backups without the key simply yield an empty calendar.
 */
class WorkoutCalendarStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        "buttigogym_wellness_v1", Context.MODE_PRIVATE
    )
    private val key = "workout_calendar_v1"

    fun read(): List<CalendarEntry> = runCatching {
        val root = JSONObject(preferences.getString(key, "{}") ?: "{}")
        val iterator = root.keys()
        val entries = buildList {
            while (iterator.hasNext()) {
                val day = iterator.next()
                runCatching {
                    val value = root.getJSONObject(day)
                    WorkoutCalendarRules.validate(CalendarEntry(
                        date = LocalDate.parse(day),
                        kind = CalendarEntryKind.valueOf(value.getString("kind")),
                        workoutDayId = value.optString("workoutDayId", "")
                            .takeIf { it.isNotBlank() }
                    ))
                }.getOrNull()?.let(::add)
            }
        }.sortedBy { it.date }
        require(entries.size <= 4000) { "Calendario troppo grande" }
        entries
    }.getOrElse {
        android.util.Log.w("ButtiGoGym/Calendar", "Cannot read calendar", it)
        emptyList()
    }

    fun save(entries: List<CalendarEntry>): List<CalendarEntry> {
        require(entries.size <= 4000) { "Calendario troppo grande" }
        require(entries.map { it.date }.distinct().size == entries.size) {
            "Date duplicate nel calendario"
        }
        val root = JSONObject()
        entries.forEach { entry ->
            WorkoutCalendarRules.validate(entry)
            root.put(entry.date.toString(), JSONObject().apply {
                put("kind", entry.kind.name)
                entry.workoutDayId?.let { put("workoutDayId", it) }
            })
        }
        check(preferences.edit().putString(key, root.toString()).commit()) {
            "Non è stato possibile salvare il calendario"
        }
        return entries.sortedBy { it.date }
    }
}
