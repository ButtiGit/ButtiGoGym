package it.pulse.workout.data

import java.util.Locale

/** Unica fonte per validazione e stepper della serie in corso. */
data class QuickSetValues(
    val weight: String = "",
    val reps: String = "",
    val warmup: Boolean = false,
    val actualRir: Int? = null
) {
    val valid: Boolean get() {
        val kg = weight.replace(',', '.').toDoubleOrNull()
        val amount = reps.toIntOrNull()
        return kg != null && kg.isFinite() && kg in 0.0..10_000.0 &&
            amount != null && amount in 1..999 &&
            (actualRir == null || actualRir in 0..5)
    }

    fun adjustKg(delta: Double): QuickSetValues {
        val old = weight.replace(',', '.').toDoubleOrNull() ?: 0.0
        val next = (old + delta).coerceIn(0.0, 10_000.0)
        val formatted = String.format(Locale.US, "%.1f", next)
            .trimEnd('0').trimEnd('.')
        return copy(weight = formatted)
    }

    fun adjustReps(delta: Int): QuickSetValues {
        val previous = reps.toIntOrNull() ?: 0
        return copy(reps = (previous + delta).coerceIn(0, 999).toString())
    }

    fun withPreviousSet(previous: SetEntry): QuickSetValues =
        if (!previous.done || previous.warmup) this
        else copy(weight = previous.weight, reps = previous.reps, actualRir = null)
}
