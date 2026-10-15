package it.pulse.workout.data

import android.content.Context
import org.json.JSONObject
import java.time.LocalDate

/** Nel namespace wellness incluso nel backup ZIP. Conservazione massima 60 giorni. */
class PreWorkoutCheckInStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(
        "buttigogym_wellness_v1", Context.MODE_PRIVATE
    )
    private val key = "preworkout_checkins_v1"

    fun readOn(date: LocalDate): PreWorkoutCheckIn? = runCatching {
        val json = JSONObject(prefs.getString(key, "{}") ?: "{}")
            .optJSONObject(date.toString()) ?: return@runCatching null
        PreWorkoutCheckIn(date,
            WorkoutEnergy.valueOf(json.getString("energy")),
            LastNightSleep.valueOf(json.getString("sleep")),
            WorkoutSoreness.valueOf(json.getString("soreness")))
    }.getOrNull()

    fun save(record: PreWorkoutCheckIn): PreWorkoutCheckIn {
        val today = LocalDate.now()
        require(!record.date.isAfter(today) &&
            !record.date.isBefore(today.minusDays(60))) { "Data non valida" }
        val root = runCatching {
            JSONObject(prefs.getString(key, "{}") ?: "{}")
        }.getOrDefault(JSONObject())
        root.keys().asSequence().toList().forEach { raw ->
            val date = runCatching { LocalDate.parse(raw) }.getOrNull()
            if (date == null || date.isBefore(today.minusDays(60)) || date.isAfter(today))
                root.remove(raw)
        }
        root.put(record.date.toString(), JSONObject().apply {
            put("energy", record.energy.name)
            put("sleep", record.sleep.name)
            put("soreness", record.soreness.name)
        })
        check(prefs.edit().putString(key, root.toString()).commit()) {
            "Salvataggio check-in non riuscito"
        }
        return record
    }
}
