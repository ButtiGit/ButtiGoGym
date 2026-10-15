package it.pulse.workout.data

import android.content.Context
import java.time.LocalDate

/** Solo target kcal di riferimento; lo storico cibo rimane fuori dall'app. */
class LeanBulkTargetStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(
        "buttigogym_wellness_v1", Context.MODE_PRIVATE
    )
    fun currentKcal(): Int = prefs.getInt("lean_bulk_kcal_v1",
        LeanBulkTargets.INITIAL_KCAL).takeIf { it in 1800..4000 }
        ?: LeanBulkTargets.INITIAL_KCAL
    fun lastAdjustedOn(): LocalDate? = runCatching {
        prefs.getString("lean_bulk_kcal_changed_on_v1", null)?.let(LocalDate::parse)
    }.getOrNull()
    fun saveTarget(kcal: Int, today: LocalDate = LocalDate.now()) {
        require(kcal in 1800..4000 && kcal % 50 == 0) { "Target kcal non valido" }
        check(prefs.edit().putInt("lean_bulk_kcal_v1", kcal)
            .putString("lean_bulk_kcal_changed_on_v1", today.toString()).commit()) {
            "Salvataggio non riuscito"
        }
    }
}
