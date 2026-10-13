package it.pulse.workout.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.time.DayOfWeek
import java.time.LocalTime

/** Preferences stay on device and are included by the existing ZIP V2 reminder namespace. */
enum class FitnessGoal(val key: String, val label: String, val compactLabel: String) {
    LEAN_BULK("lean_bulk", "Massa muscolare definita", "LEAN BULK"),
    HYPERTROPHY("hypertrophy", "Crescita muscolare", "IPERTROFIA"),
    STRENGTH("strength", "Forza", "FORZA"),
    RECOMPOSITION("recomposition", "Ricomposizione corporea", "RICOMPOSIZIONE"),
    WELLNESS("wellness", "Benessere e costanza", "BENESSERE")
}

enum class AppearancePreference(val key: String, val label: String) {
    LIGHT("light", "Chiaro"), DARK("dark", "Scuro"), SYSTEM("system", "Sistema")
}

data class PersonalSettings(
    val goal: FitnessGoal = FitnessGoal.LEAN_BULK,
    val weeklyWorkouts: Int = 3,
    /** All days by default, to preserve pre-existing reminder behaviour. */
    val reminderDays: Set<DayOfWeek> = DayOfWeek.entries.toSet(),
    val reminderStart: LocalTime = LocalTime.of(8, 30),
    val reminderEnd: LocalTime = LocalTime.of(18, 0),
    val appearance: AppearancePreference = AppearancePreference.LIGHT,
    val hapticFeedback: Boolean = true
)

object PersonalSettingsRules {
    fun validate(value: PersonalSettings): PersonalSettings {
        require(value.weeklyWorkouts in 1..7) { "Allenamenti settimanali: da 1 a 7" }
        require(value.reminderDays.isNotEmpty()) { "Seleziona almeno un giorno per i promemoria" }
        require(value.reminderEnd.isAfter(value.reminderStart.plusHours(1))) {
            "La fascia dei promemoria deve durare più di un'ora"
        }
        return value
    }
}

/**
 * Kept in the already-backed-up reminders namespace (not a new mandatory backup namespace).
 * Existing reminder water/move flags are intentionally untouched.
 */
class PersonalSettingsStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        "buttigogym_reminders_v1", Context.MODE_PRIVATE
    )
    private val key = "personal_settings_v1"

    fun read(): PersonalSettings = runCatching {
        val raw = preferences.getString(key, null) ?: return@runCatching PersonalSettings()
        val json = JSONObject(raw)
        val days = json.optJSONArray("reminderDays")
        val parsed = PersonalSettings(
            goal = FitnessGoal.entries.firstOrNull {
                it.key == json.optString("goal")
            } ?: FitnessGoal.LEAN_BULK,
            weeklyWorkouts = json.optInt("weeklyWorkouts", 3),
            reminderDays = if (days == null) DayOfWeek.entries.toSet()
                else (0 until days.length()).mapNotNull { index ->
                    runCatching { DayOfWeek.valueOf(days.getString(index)) }.getOrNull()
                }.toSet(),
            reminderStart = LocalTime.parse(json.optString("reminderStart", "08:30")),
            reminderEnd = LocalTime.parse(json.optString("reminderEnd", "18:00")),
            appearance = AppearancePreference.entries.firstOrNull {
                it.key == json.optString("appearance")
            } ?: AppearancePreference.LIGHT,
            hapticFeedback = json.optBoolean("hapticFeedback", true)
        )
        PersonalSettingsRules.validate(parsed)
    }.getOrElse {
        android.util.Log.w("ButtiGoGym/Settings", "Invalid personal settings, using defaults", it)
        PersonalSettings()
    }

    fun save(settings: PersonalSettings): PersonalSettings {
        val safe = PersonalSettingsRules.validate(settings)
        val json = JSONObject().apply {
            put("goal", safe.goal.key)
            put("weeklyWorkouts", safe.weeklyWorkouts)
            put("reminderDays", JSONArray(safe.reminderDays.map { it.name }.sorted()))
            put("reminderStart", safe.reminderStart.toString())
            put("reminderEnd", safe.reminderEnd.toString())
            put("appearance", safe.appearance.key)
            put("hapticFeedback", safe.hapticFeedback)
        }
        check(preferences.edit().putString(key, json.toString()).commit()) {
            "Impossibile salvare le impostazioni"
        }
        return safe
    }
}
