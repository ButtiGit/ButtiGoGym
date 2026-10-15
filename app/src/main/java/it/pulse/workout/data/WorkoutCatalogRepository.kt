package it.pulse.workout.data

import android.content.Context
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONArray
import org.json.JSONObject

/** Scheda JSON su GitHub, validata e mantenuta in cache per l'uso offline. */
class WorkoutCatalogRepository(context: Context) {
    private val app = context.applicationContext
    private val preferences = app.getSharedPreferences("pulse_plan", Context.MODE_PRIVATE)

    companion object {
        const val SOURCE_URL = "https://raw.githubusercontent.com/ButtiGit/ButtiGoGym/main/workouts/plan.json"
        private const val MAX_JSON_BYTES = 256 * 1024

        private fun readMuscles(obj: JSONObject, key: String): List<String> {
            val arr = obj.optJSONArray(key) ?: return emptyList()
            return (0 until arr.length()).mapNotNull { index ->
                arr.optString(index).takeIf { it in MuscleGroup.ids }
            }.distinct().take(5)
        }

        fun parseDay(data: JSONObject): WorkoutDay {
            val exercises = data.getJSONArray("exercises")
            require(exercises.length() in 1..30) { "Numero esercizi non valido" }
            val items = (0 until exercises.length()).map { index ->
                val obj = exercises.getJSONObject(index)
                val sets = obj.getInt("sets")
                require(sets in 1..8) { "Numero serie non valido" }
                Exercise(
                    id = obj.getString("id"),
                    name = obj.getString("name"),
                    muscle = obj.getString("muscle"),
                    sets = sets,
                    target = obj.getString("target"),
                    restSeconds = obj.optInt("restSeconds", 90).coerceIn(30, 300),
                    equipment = obj.optString("equipment", ""),
                    rir = obj.optString("rir", "1–3"),
                    note = obj.optString("note", ""),
                    alternative = obj.optString("alternative", ""),
                    gifUrl = obj.optString("gifUrl", "").takeIf {
                        it.startsWith("https://") && it.length <= 500
                    }.orEmpty(),
                    primaryMuscles = readMuscles(obj, "primaryMuscles"),
                    secondaryMuscles = readMuscles(obj, "secondaryMuscles"),
                    optional = obj.optBoolean("optional", false),
                    timeBased = obj.optBoolean("timeBased", false),
                    group = obj.optString("group", "").take(70)
                )
            }
            require(items.map { it.id }.distinct().size == items.size)
            return WorkoutDay(
                id = data.getString("id"),
                weekday = data.getString("weekday"),
                name = data.getString("name"),
                description = data.optString("description", ""),
                duration = data.optString("duration", ""),
                exercises = items,
                optional = data.optBoolean("optional", false),
                cooldown = data.optString("cooldown", "").take(280)
            )
        }

        fun dayToJson(day: WorkoutDay): JSONObject = JSONObject().apply {
            put("id", day.id)
            put("weekday", day.weekday)
            put("name", day.name)
            put("description", day.description)
            put("duration", day.duration)
            put("optional", day.optional)
            put("cooldown", day.cooldown)
            put("exercises", JSONArray().apply {
                day.exercises.forEach { ex ->
                    put(JSONObject().apply {
                        put("id", ex.id); put("name", ex.name); put("muscle", ex.muscle)
                        put("sets", ex.sets); put("target", ex.target)
                        put("restSeconds", ex.restSeconds); put("equipment", ex.equipment)
                        put("rir", ex.rir); put("note", ex.note)
                        put("alternative", ex.alternative); put("gifUrl", ex.gifUrl)
                        put("primaryMuscles", JSONArray(ex.primaryMuscles))
                        put("secondaryMuscles", JSONArray(ex.secondaryMuscles))
                        put("optional", ex.optional)
                        put("timeBased", ex.timeBased)
                        put("group", ex.group)
                    })
                }
            })
        }

        fun parse(raw: String): WorkoutCatalog {
            val root = JSONObject(raw)
            require(root.getInt("schemaVersion") == 1) { "Schema API non supportato" }
            val arr = root.getJSONArray("days")
            require(arr.length() in 3..7) { "Numero sedute non valido" }
            val days = (0 until arr.length()).map { parseDay(arr.getJSONObject(it)) }
            require(days.map { it.id }.distinct().size == days.size)
            require(days.count { !it.optional } >= 3) { "Mancano sedute obbligatorie" }
            return WorkoutCatalog(
                revision = root.optString("revision", "1"),
                title = root.optString("title", "Scheda allenamento"),
                days = days,
                source = SOURCE_URL,
                guidelines = root.optJSONArray("guidelines")?.let { list ->
                    (0 until list.length()).map { list.optString(it) }
                }.orEmpty()
            )
        }
    }

    /**
     * Solo gli esercizi vengono sovrascritti: nomi, giorni, linee guida e
     * revisioni di GitHub possono continuare ad aggiornarsi normalmente.
     * Salvato nelle preferenze pulse_plan, già incluse nel backup V2.
     */
    private val overrideKey = "custom_days_v1"

    fun customizedDayIds(): Set<String> = runCatching {
        val saved = JSONObject(preferences.getString(overrideKey, "{}").orEmpty())
        val keys = saved.keys()
        buildSet { while (keys.hasNext()) add(keys.next()) }
    }.getOrDefault(emptySet())

    private fun withCustomizations(base: WorkoutCatalog): WorkoutCatalog {
        val stored = runCatching {
            JSONObject(preferences.getString(overrideKey, "{}").orEmpty())
        }.getOrElse { return base }
        return base.copy(days = base.days.map { day ->
            val custom = stored.optJSONArray(day.id) ?: return@map day
            runCatching {
                // parseDay è anche usato per le snapshot e rifiuta set invalidi.
                val json = dayToJson(day).put("exercises", custom)
                val updated = parseDay(json)
                CustomPlanRules.validate(updated.exercises)
                updated
            }.getOrDefault(day)
        })
    }

    /** Rifiuta input invalidi senza scrivere in memoria. Ritorna la nuova scheda. */
    fun saveCustomExercises(
        catalog: WorkoutCatalog,
        dayId: String,
        exercises: List<Exercise>
    ): WorkoutCatalog {
        val base = catalog.days.firstOrNull { it.id == dayId }
            ?: error("Seduta non trovata")
        CustomPlanRules.validate(exercises)
        val updated = base.copy(exercises = exercises)
        val encoded = dayToJson(updated).getJSONArray("exercises")
        val stored = JSONObject(preferences.getString(overrideKey, "{}").orEmpty())
        stored.put(dayId, encoded)
        require(preferences.edit().putString(overrideKey, stored.toString()).commit()) {
            "Impossibile salvare la scheda personalizzata"
        }
        return catalog.copy(days = catalog.days.map { if (it.id == dayId) updated else it })
    }

    /** Reimposta solo questa seduta. Le sessioni già registrate sono immutate. */
    fun resetCustomExercises(dayId: String): WorkoutCatalog {
        val stored = JSONObject(preferences.getString(overrideKey, "{}").orEmpty())
        stored.remove(dayId)
        check(preferences.edit().putString(overrideKey, stored.toString()).commit()) {
            "Impossibile ripristinare la scheda predefinita"
        }
        return initial()
    }

    fun initial(): WorkoutCatalog {
        val cached = preferences.getString("valid_catalog", null)
        if (!cached.isNullOrBlank()) {
            runCatching { return withCustomizations(parse(cached)) }
        }
        val included = app.assets.open("workout-plan.json").bufferedReader().use { it.readText() }
        return withCustomizations(parse(included))
    }

    /** Da chiamare su Dispatchers.IO; mantiene intatta la versione valida se la rete fallisce. */
    fun refresh(): WorkoutCatalog {
        val url = URL("$SOURCE_URL?nocache=${System.currentTimeMillis()}")
        val connection = url.openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 6000
            connection.readTimeout = 8000
            connection.instanceFollowRedirects = true
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("Cache-Control", "no-cache")
            require(connection.responseCode == HttpURLConnection.HTTP_OK) { "HTTP ${connection.responseCode}" }
            require(connection.contentLengthLong <= MAX_JSON_BYTES) { "File troppo grande" }
            val bytes = connection.inputStream.use { stream ->
                val buffer = ByteArray(4096)
                val output = ByteArrayOutputStream()
                while (true) {
                    val n = stream.read(buffer)
                    if (n < 0) break
                    require(output.size() + n <= MAX_JSON_BYTES) { "File troppo grande" }
                    output.write(buffer, 0, n)
                }
                output.toByteArray()
            }
            val json = bytes.toString(Charsets.UTF_8)
            val valid = parse(json)
            preferences.edit().putString("valid_catalog", json).apply()
            return withCustomizations(valid)
        } finally {
            connection.disconnect()
        }
    }
}
