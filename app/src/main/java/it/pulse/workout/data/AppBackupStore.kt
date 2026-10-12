package it.pulse.workout.data

import android.content.Context
import org.json.JSONObject
import java.time.Instant

/** Backup manuale locale dei progressi, senza server né permessi di archiviazione. */
class AppBackupStore(context: Context) {
    private val app = context.applicationContext

    companion object {
        const val MIME_TYPE = "application/json"
        const val MAX_SIZE = 2_000_000
        private val namespaces = listOf(
            "pulse_v1", "buttigogym_wellness_v1", "pulse_plan"
        )
    }

    fun export(): String = JSONObject().apply {
        put("format", "ButtiGoGymBackup")
        put("schemaVersion", 1)
        put("package", app.packageName)
        put("createdAt", Instant.now().toString())
        put("data", JSONObject().apply {
            namespaces.forEach { namespace ->
                val values = JSONObject()
                app.getSharedPreferences(namespace, Context.MODE_PRIVATE)
                    .all.forEach { (key, value) ->
                        if (value is String) values.put(key, value)
                    }
                put(namespace, values)
            }
        })
    }.toString(2)

    fun validate(raw: String): JSONObject {
        require(raw.toByteArray(Charsets.UTF_8).size <= MAX_SIZE) { "Backup troppo grande" }
        val root = JSONObject(raw)
        require(root.optString("format") == "ButtiGoGymBackup") { "Formato non riconosciuto" }
        require(root.optInt("schemaVersion") == 1) { "Versione di backup non supportata" }
        require(root.optString("package") == app.packageName) { "Backup di un'altra applicazione" }
        val data = root.getJSONObject("data")
        for (namespace in namespaces) {
            val values = data.getJSONObject(namespace)
            val keys = values.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                require(key.length in 1..100 && values.opt(key) is String) { "Valore non valido: $key" }
                val value = values.getString(key)
                require(value.length < MAX_SIZE) { "Dato troppo grande" }
                if (namespace == "pulse_v1" && key in listOf("history", "active_draft"))
                    if (key == "history") org.json.JSONArray(value) else JSONObject(value)
                if (namespace == "buttigogym_wellness_v1" && key == "wellness") JSONObject(value)
                if (namespace == "pulse_plan" && key == "valid_catalog") JSONObject(value)
            }
        }
        return data
    }

    /** Sostituzione esplicita, eseguita soltanto dopo conferma dell'utente. */
    fun restore(raw: String) {
        val data = validate(raw)
        namespaces.forEach { namespace ->
            val values = data.getJSONObject(namespace)
            val editor = app.getSharedPreferences(namespace, Context.MODE_PRIVATE).edit().clear()
            val keys = values.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                editor.putString(key, values.getString(key))
            }
            check(editor.commit()) { "Ripristino non riuscito: $namespace" }
        }
    }
}
