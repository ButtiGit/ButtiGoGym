package it.pulse.workout.sharing

import it.pulse.workout.data.*
import java.security.MessageDigest
import java.util.Base64

data class HistoryPayload(val json: String, val hash: String, val workouts: Int, val sets: Int) {
    /** Base64 parts can be rejoined byte-for-byte, even when an emoji crosses a boundary. */
    fun parts(bytesPerPart: Int = 24_000): List<String> {
        require(bytesPerPart in 1..30_000)
        val bytes = json.toByteArray(Charsets.UTF_8)
        return (bytes.indices step bytesPerPart).map { start ->
            Base64.getEncoder().encodeToString(bytes.copyOfRange(start, minOf(start + bytesPerPart, bytes.size)))
        }
    }
}

object WorkoutHistoryPayload {
    fun quote(value: String): String = buildString {
        append('"')
        value.forEach { c -> when (c) {
            '"' -> append("\\\"")
            '\\' -> append("\\\\")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            '\t' -> append("\\t")
            else -> if (c.code < 32) append("\\u" + c.code.toString(16).padStart(4, '0')) else append(c)
        } }
        append('"')
    }
    private fun obj(vararg pairs: Pair<String, String>) = pairs.joinToString(",", "{", "}") { quote(it.first) + ":" + it.second }
    private fun strings(values: List<String>) = values.joinToString(",", "[", "]", transform = ::quote)
    private fun exercise(e: Exercise) = obj(
        "id" to quote(e.id), "name" to quote(e.name), "muscle" to quote(e.muscle),
        "equipment" to quote(e.equipment), "sets" to e.sets.toString(), "target" to quote(e.target),
        "restSeconds" to e.restSeconds.toString(), "targetRir" to quote(e.rir),
        "optional" to e.optional.toString(), "timeBased" to e.timeBased.toString(),
        "primaryMuscles" to strings(e.primaryMuscles), "secondaryMuscles" to strings(e.secondaryMuscles),
        "note" to quote(e.note))
    fun build(history: List<SessionRecord>, days: List<WorkoutDay>, appVersion: String): HistoryPayload {
        val sessions = history.sortedWith(compareBy<SessionRecord> { it.completedAt }.thenBy { it.id }).map { record ->
            val snapshot = record.daySnapshot
            val day = snapshot ?: days.firstOrNull { it.id == record.dayId }
            val entries = record.entries.toSortedMap().map { (key, entry) -> obj(
                "key" to quote(key), "weightKg" to quote(entry.weight), "repsOrSeconds" to quote(entry.reps),
                "done" to entry.done.toString(), "warmup" to entry.warmup.toString(),
                "completedAt" to entry.completedAt.toString(), "rir" to (entry.rir?.toString() ?: "null")) }
            obj("id" to record.id.toString(), "dayId" to quote(record.dayId),
                "completedAt" to record.completedAt.toString(), "durationSeconds" to record.durationSeconds.toString(),
                "durationMinutes" to record.durationMinutes.toString(), "completedSets" to record.completedSets.toString(),
                "totalSets" to record.totalSets.toString(), "dayName" to quote(day?.name.orEmpty()),
                "metadataSource" to quote(if (snapshot != null) "snapshot" else if (day != null) "current-plan" else "unavailable"),
                "exercises" to (day?.exercises.orEmpty().joinToString(",", "[", "]", transform = ::exercise)),
                "entries" to entries.joinToString(",", "[", "]"))
        }
        val json = obj("schema" to quote("buttigogym-workout-history-v1"), "appVersion" to quote(appVersion),
            "timestampUnit" to quote("unix-milliseconds"), "workouts" to sessions.joinToString(",", "[", "]"))
        val hash = MessageDigest.getInstance("SHA-256").digest(json.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it.toInt() and 255) }
        return HistoryPayload(json, hash, history.size, history.sumOf { it.entries.values.count { e -> e.done } })
    }
}
