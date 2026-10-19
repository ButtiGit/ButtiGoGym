package it.pulse.workout.data

/** A single answer per screen; a set is persisted only after explicit review. */
enum class GuidedSetStep {
    WEIGHT, REPS, EFFORT, REVIEW;

    fun accepts(input: QuickSetValues): Boolean = when (this) {
        WEIGHT -> input.weight.replace(',', '.').toDoubleOrNull()
            ?.let { it.isFinite() && it in 0.0..10_000.0 } == true
        REPS -> input.reps.toIntOrNull()?.let { it in 1..999 } == true
        EFFORT, REVIEW -> input.valid
    }

    fun next(input: QuickSetValues): GuidedSetStep = when {
        !accepts(input) -> this
        this == WEIGHT -> REPS
        this == REPS -> if (input.warmup) REVIEW else EFFORT
        else -> REVIEW
    }

    fun previous(input: QuickSetValues): GuidedSetStep = when (this) {
        WEIGHT -> WEIGHT
        REPS -> WEIGHT
        EFFORT -> REPS
        REVIEW -> if (input.warmup) REPS else EFFORT
    }
}
