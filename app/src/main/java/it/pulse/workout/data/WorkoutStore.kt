package it.pulse.workout.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Persistenza locale minima, senza rete, account o permessi. */
class WorkoutStore(context: Context) {
    private val preferences = context.getSharedPreferences("pulse_v1", Context.MODE_PRIVATE)

    fun loadDraft(): WorkoutDraft? = runCatching {
        val raw = preferences.getString("active_draft", null) ?: return@runCatching null
        val json = JSONObject(raw)
        val entries = entriesFromJson(json.optJSONObject("entries") ?: JSONObject())
        WorkoutDraft(
            dayId = json.getString("dayId"),
            startedAt = json.getLong("startedAt"),
            entries = entries
        ).takeIf { draft -> WorkoutPlan.days.any { it.id == draft.dayId } }
    }.getOrNull()

    fun saveDraft(draft: WorkoutDraft?) {
        if (draft == null) {
            preferences.edit().remove("active_draft").apply()
            return
        }
        val entries = entriesToJson(draft.entries)
        val json = JSONObject().apply {
            put("dayId", draft.dayId)
            put("startedAt", draft.startedAt)
            put("entries", entries)
        }
        preferences.edit().putString("active_draft", json.toString()).apply()
    }

    fun history(): List<SessionRecord> = runCatching {
        val arr = JSONArray(preferences.getString("history", "[]"))
        (0 until arr.length()).mapNotNull { i ->
            val json = arr.optJSONObject(i) ?: return@mapNotNull null
            SessionRecord(
                id = json.getLong("id"),
                dayId = json.getString("dayId"),
                completedAt = json.getLong("completedAt"),
                durationMinutes = json.getInt("minutes"),
                completedSets = json.getInt("completedSets"),
                totalSets = json.getInt("totalSets"),
                entries = entriesFromJson(json.optJSONObject("entries") ?: JSONObject())
            )
        }.sortedByDescending { it.completedAt }
    }.getOrDefault(emptyList())

    /** Riutilizza kg/reps dell'ultimo allenamento, senza segnare già completate le serie. */
    fun newDraft(dayId: String): WorkoutDraft {
        val previous = history().firstOrNull { it.dayId == dayId }
        return WorkoutDraft(
            dayId = dayId,
            startedAt = System.currentTimeMillis(),
            entries = previous?.entries.orEmpty().mapValues { (_, entry) -> entry.copy(done = false) }
        )
    }

    private fun entriesFromJson(json: JSONObject): Map<String, SetEntry> {
        val entries = mutableMapOf<String, SetEntry>()
        val keys = json.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val value = json.optJSONObject(key) ?: continue
            entries[key] = SetEntry(
                weight = value.optString("weight", ""),
                reps = value.optString("reps", ""),
                done = value.optBoolean("done", false)
            )
        }
        return entries
    }

    private fun entriesToJson(entries: Map<String, SetEntry>): JSONObject = JSONObject().apply {
        entries.forEach { (key, entry) ->
            put(key, JSONObject().apply {
                put("weight", entry.weight)
                put("reps", entry.reps)
                put("done", entry.done)
            })
        }
    }

    fun complete(draft: WorkoutDraft, day: WorkoutDay): SessionRecord {
        val end = System.currentTimeMillis()
        val completed = day.exercises.sumOf { ex ->
            (1..ex.sets).count { draft.entry(ex, it).done }
        }
        val record = SessionRecord(
            id = end,
            dayId = day.id,
            completedAt = end,
            durationMinutes = ((end - draft.startedAt).coerceAtLeast(0L) / 60_000L).toInt(),
            completedSets = completed,
            totalSets = day.setCount,
            entries = draft.entries
        )
        val updated = listOf(record) + history()
        val json = JSONArray()
        updated.take(200).forEach { item ->
            json.put(JSONObject().apply {
                put("id", item.id)
                put("dayId", item.dayId)
                put("completedAt", item.completedAt)
                put("minutes", item.durationMinutes)
                put("completedSets", item.completedSets)
                put("totalSets", item.totalSets)
                put("entries", entriesToJson(item.entries))
            })
        }
        // Un commit sincrono evita di perdere la cronologia in caso di chiusura immediata.
        preferences.edit()
            .putString("history", json.toString())
            .remove("active_draft")
            .commit()
        return record
    }
}
