package it.pulse.workout.data

import kotlin.math.max

/**
 * Badge ludici di prestazione, separati dagli XP/volume e dal recupero.
 * Il rapporto confronta la migliore serie Epley con un riferimento INTERNO
 * dello stesso esercizio, rapportato al peso corporeo. Non è uno standard
 * clinico o una misura assoluta fra macchine/palestre differenti.
 */
data class StrengthTier(
    val name: String,
    val minRatio: Double,
    val symbol: String,
    val colorHex: Long
)

data class MuscleStrengthRank(
    val group: MuscleGroup,
    val tier: StrengthTier?,
    val ratio: Double?,
    val evidence: String?,
    val exerciseId: String?
)

object StrengthRanking {
    val tiers = listOf(
        StrengthTier("Novizio", 0.0, "🌱", 0xFF8B9690),
        StrengthTier("Ferro", 0.45, "⚙️", 0xFF74828B),
        StrengthTier("Rame", 0.60, "🟠", 0xFFB7774D),
        StrengthTier("Bronzo", 0.75, "🥉", 0xFFAA774B),
        StrengthTier("Argento", 0.90, "🥈", 0xFF919EAF),
        StrengthTier("Oro", 1.05, "🥇", 0xFFC39737),
        StrengthTier("Platino", 1.20, "✦", 0xFF6EACAA),
        StrengthTier("Smeraldo", 1.35, "💚", 0xFF279661),
        StrengthTier("Rubino", 1.55, "♦", 0xFFCC5571),
        StrengthTier("Diamante", 1.75, "💎", 0xFF478BCC),
        StrengthTier("Titanio", 2.0, "⚡", 0xFF7F69CE),
        StrengthTier("Leggenda", 2.30, "👑", 0xFFAD69CE)
    )

    // Fattori di riferimento dichiarati nell'app: kg di 1RM stimato
    // per kg di massa corporea. Sono INDICI DI GIOCO, non norme di forza.
    // Movimenti di macchina e carichi per-manubrio non sono confrontabili
    // con il bilanciere; ogni esercizio ha un riferimento distinto.
    private val reference = mapOf(
        "shoulder_press" to 0.50,
        "lateral_raise" to 0.16,
        "lateral_raise_db" to 0.16,
        "bonus_cable_lateral" to 0.16,
        "fri_lateral_recall" to 0.16,
        "reverse_fly" to 0.23,
        "bicep_curl" to 0.25,
        "hammer_curl" to 0.30,
        "bonus_hammer" to 0.30,
        "tricep_pushdown" to 0.50,
        "bonus_pushdown" to 0.50,
        "triceps_overhead" to 0.46,
        "reverse_curl_ez" to 0.35,
        "wrist_curl" to 0.25,
        "reverse_wrist_curl" to 0.22,
        "hack_squat" to 1.65,
        "squat" to 1.45,
        "leg_press" to 1.85,
        "fri_leg_press_recall" to 1.85,
        "bonus_leg_press" to 1.85,
        "split_squat" to 0.58,
        "leg_extension" to 0.83,
        "rdl" to 1.25,
        "leg_curl" to 0.80,
        "fri_leg_curl_recall" to 0.80,
        "bonus_leg_curl" to 0.80,
        "calf_raise" to 1.35,
        "bonus_calf" to 1.35,
        "crunch" to 0.55,
        "bonus_crunch" to 0.55,
        "incline_chest_machine" to 0.84,
        "incline" to 0.50,
        "bench" to 0.90,
        "mon_chest_recall" to 0.90,
        "pec_deck" to 0.62,
        "fly" to 0.36,
        "pulldown" to 0.90,
        "mon_lat_recall" to 0.90,
        "row_chest" to 0.90,
        "seated_row" to 0.90,
        "pullover" to 0.40
    )

    fun tierFor(ratio: Double): StrengthTier =
        tiers.lastOrNull { ratio >= it.minRatio } ?: tiers.first()

    /**
     * Best demonstrated set per primary muscle; a hundred identical series
     * do not increase rank. Reject warmups, invalid weights, time-based sets,
     * estimated reps > 20 and exercises without explicit reference.
     */
    fun evaluate(history: List<SessionRecord>, bodyWeightKg: Double?): List<MuscleStrengthRank> {
        val baseline = bodyWeightKg?.takeIf { it.isFinite() && it in 25.0..350.0 }
        val best = mutableMapOf<String, Triple<Double, String, String>>()
        if (baseline != null) {
            history.forEach { record ->
                val day = record.daySnapshot
                    ?: WorkoutPlan.days.firstOrNull { it.id == record.dayId }
                    ?: return@forEach
                day.exercises.forEach { exercise ->
                    if (exercise.timeBased) return@forEach
                    val factor = reference[exercise.id] ?: return@forEach
                    val primary = ExerciseMuscleMapping.forExercise(exercise).first
                    if (primary.isEmpty()) return@forEach
                    for (number in 1..exercise.sets) {
                        val entry = record.entries[WorkoutDraft.key(exercise.id, number)]
                            ?: continue
                        if (!entry.done || entry.warmup) continue
                        val kilograms = entry.weight.replace(',', '.').toDoubleOrNull() ?: continue
                        val reps = entry.reps.toIntOrNull() ?: continue
                        if (!kilograms.isFinite() || kilograms <= 0.0 ||
                            kilograms > 1000.0 || reps !in 1..20) continue
                        val estimated = kilograms * (1.0 + reps / 30.0)
                        val score = estimated / (baseline * factor)
                        val proof = exercise.name + " · " + entry.weight + " kg × " +
                            reps + " rip. (1RM stimato: " +
                            kotlin.math.round(estimated * 10.0) / 10.0 + " kg)"
                        primary.forEach { muscle ->
                            if (muscle !in MuscleGroup.ids) return@forEach
                            val old = best[muscle]
                            if (old == null || score > old.first) {
                                best[muscle] = Triple(score, proof, exercise.id)
                            }
                        }
                    }
                }
            }
        }
        return MuscleGroup.all.map { group ->
            val evidence = best[group.id]
            MuscleStrengthRank(
                group, evidence?.let { tierFor(it.first) },
                evidence?.first, evidence?.second, evidence?.third
            )
        }
    }

    fun hasBodyWeight(value: Double?): Boolean = value != null &&
        value.isFinite() && value in 25.0..350.0
}
