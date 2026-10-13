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
            entries = entries,
            daySnapshot = json.optJSONObject("daySnapshot")?.let { WorkoutCatalogRepository.parseDay(it) },
            guidedExerciseIndex = json.optInt("guidedExerciseIndex", 0),
            guidedWorkSet = json.optInt("guidedWorkSet", 1),
            restUntilMillis = json.optLong("restUntilMillis", 0L)
        )
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
            put("guidedExerciseIndex", draft.guidedExerciseIndex)
            put("guidedWorkSet", draft.guidedWorkSet)
            put("restUntilMillis", draft.restUntilMillis)
            draft.daySnapshot?.let { put("daySnapshot", WorkoutCatalogRepository.dayToJson(it)) }
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
                durationSeconds = json.optInt("seconds", json.getInt("minutes") * 60),
                completedSets = json.getInt("completedSets"),
                totalSets = json.getInt("totalSets"),
                entries = entriesFromJson(json.optJSONObject("entries") ?: JSONObject()),
                daySnapshot = json.optJSONObject("daySnapshot")?.let { WorkoutCatalogRepository.parseDay(it) },
                photoFileName = json.optString("photoFileName", "")
            )
        }.sortedByDescending { it.completedAt }
    }.getOrDefault(emptyList())

    /** Associa una foto alla sessione senza modificare carichi, durata o altre sessioni. */
    fun attachPhoto(recordId: Long, fileName: String): Boolean {
        require(fileName.matches(Regex("session_[0-9]+_[0-9]+\\.jpg"))) { "Nome foto non valido" }
        val history = JSONArray(preferences.getString("history", "[]"))
        var found = false
        for (i in 0 until history.length()) {
            val record = history.optJSONObject(i) ?: continue
            if (record.optLong("id") == recordId) {
                record.put("photoFileName", fileName)
                found = true
                break
            }
        }
        return found && preferences.edit().putString("history", history.toString()).commit()
    }

    /** Riutilizza kg/reps dell'ultimo allenamento, senza segnare già completate le serie. */
    fun newDraft(day: WorkoutDay): WorkoutDraft {
        val previous = history().firstOrNull { it.dayId == day.id }
        // Quando la scheda cambia, non trasciniamo le serie di esercizi rimossi
        // o le serie extra se il numero di set è diminuito.
        val validSetKeys = day.exercises.flatMap { exercise ->
            (1..exercise.sets).map { number -> WorkoutDraft.key(exercise.id, number) }
        }.toSet()
        return WorkoutDraft(
            dayId = day.id,
            daySnapshot = day,
            startedAt = System.currentTimeMillis(),
            entries = previous?.entries.orEmpty()
                .filterKeys { it in validSetKeys }
                .mapValues { (_, entry) -> entry.copy(done = false, warmup = false, completedAt = 0L) }
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
                done = value.optBoolean("done", false),
                warmup = value.optBoolean("warmup", false),
                completedAt = value.optLong("completedAt", 0L)
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
                put("warmup", entry.warmup)
                put("completedAt", entry.completedAt)
            })
        }
    }

    fun complete(draft: WorkoutDraft, day: WorkoutDay): SessionRecord {
        val end = System.currentTimeMillis()
        val completed = day.exercises.sumOf { ex ->
            (1..ex.sets).count { draft.entry(ex, it).done }
        }
        val chosenOptionalSets = day.exercises.filter { it.optional }.sumOf { ex ->
            (1..ex.sets).count { draft.entry(ex, it).done }
        }
        val record = SessionRecord(
            id = end,
            dayId = day.id,
            completedAt = end,
            durationMinutes = ((end - draft.startedAt).coerceAtLeast(0L) / 60_000L).toInt(),
            completedSets = completed,
            totalSets = day.setCount + chosenOptionalSets,
            durationSeconds = ((end - draft.startedAt).coerceAtLeast(0L) / 1000L).coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
            entries = draft.entries,
            daySnapshot = day
        )
        val updated = listOf(record) + history()
        val json = JSONArray()
        updated.take(200).forEach { item ->
            json.put(JSONObject().apply {
                put("id", item.id)
                put("dayId", item.dayId)
                put("completedAt", item.completedAt)
                put("minutes", item.durationMinutes)
                put("seconds", item.durationSeconds)
                put("completedSets", item.completedSets)
                put("totalSets", item.totalSets)
                put("photoFileName", item.photoFileName)
                put("entries", entriesToJson(item.entries))
                item.daySnapshot?.let { put("daySnapshot", WorkoutCatalogRepository.dayToJson(it)) }
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
