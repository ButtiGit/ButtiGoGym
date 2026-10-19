package it.pulse.workout.data

import android.content.Context
import org.json.JSONObject

/**
 * Solo VISIBILITÀ: nascondere un widget non disattiva tracciamento,
 * notifiche o aggiornamenti. Preferenze incluse nei backup esistenti.
 */
data class HomeDisplaySettings(
    val missions: Boolean = false,
    val weeklyReport: Boolean = false,
    val details: Boolean = false,
    val streaks: Boolean = false,
    val steps: Boolean = false,
    val recovery: Boolean = false,
    val weekPlan: Boolean = false,
    val reminders: Boolean = false,
    val backup: Boolean = false
) {
    fun shownCount(): Int = listOf(
        missions, weeklyReport, details, streaks, steps,
        recovery, weekPlan, reminders, backup
    ).count { it }
}

class HomeDisplaySettingsStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        "buttigogym_reminders_v1", Context.MODE_PRIVATE
    )
    private val key = "home_display_v2"

    fun read(): HomeDisplaySettings = runCatching {
        val json = JSONObject(preferences.getString(key, "{}") ?: "{}")
        HomeDisplaySettings(
            missions = json.optBoolean("missions", false),
            weeklyReport = json.optBoolean("weeklyReport", false),
            details = json.optBoolean("details", false),
            streaks = json.optBoolean("streaks", false),
            steps = json.optBoolean("steps", false),
            recovery = json.optBoolean("recovery", false),
            weekPlan = json.optBoolean("weekPlan", false),
            reminders = json.optBoolean("reminders", false),
            backup = json.optBoolean("backup", false)
        )
    }.getOrDefault(HomeDisplaySettings())

    fun save(value: HomeDisplaySettings) {
        val json = JSONObject().apply {
            put("missions", value.missions)
            put("weeklyReport", value.weeklyReport)
            put("details", value.details)
            put("streaks", value.streaks)
            put("steps", value.steps)
            put("recovery", value.recovery)
            put("weekPlan", value.weekPlan)
            put("reminders", value.reminders)
            put("backup", value.backup)
        }
        check(preferences.edit().putString(key, json.toString()).commit()) {
            "Impossibile salvare la personalizzazione Home"
        }
    }
}

/** Messaggi brevi deterministici basati su eventi reali, non su una chat AI. */
object MascotCoach {
    fun today(active: Boolean, resting: Boolean, trained: Boolean,
              completedWeek: Int, goal: Int): String = when {
        active -> "Hai una seduta in corso. Riprendiamo da dove eri rimasto!"
        resting -> "Oggi recuperiamo. Una passeggiata tranquilla può bastare."
        trained -> "Allenamento fatto! Ottimo. Ora recupera e ricaricati."
        completedWeek >= goal -> "Obiettivo settimanale raggiunto. Procedi con calma."
        else -> "Pronto? Ti guido io. Partiamo dal prossimo allenamento."
    }

    fun inSession(resting: Boolean, completed: Boolean): String = when {
        completed -> "Bel lavoro! Salva la seduta per aggiornare i progressi."
        resting -> "Recupera e respira. Quando sei pronto, ripartiamo."
        else -> "Una serie alla volta: movimento controllato, poi registra i risultati."
    }
}
