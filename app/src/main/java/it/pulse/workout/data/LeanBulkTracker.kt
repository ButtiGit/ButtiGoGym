package it.pulse.workout.data

import java.time.LocalDate

/** Analisi locale del peso: non deduce percentuali di massa magra o kcal assunte. */
object LeanBulkTargets {
    const val START_KG = 58.0
    const val FIRST_GOAL_LOW_KG = 64.0
    const val FIRST_GOAL_HIGH_KG = 65.0
    const val INITIAL_KCAL = 2450
    const val PROTEIN_G = 120
    const val CARBS_G = 335
    const val FAT_G = 70
    const val MIN_GAIN_KG_PER_WEEK = 0.15
    const val MAX_GAIN_KG_PER_WEEK = 0.25
}

enum class BulkTrend { NEED_MORE_DATA, SLOW, ON_TRACK, FAST }

data class LeanBulkReport(
    val latestWeightKg: Double?,
    val sevenDayAverageKg: Double?,
    val sevenDayReadings: Int,
    val averageWeeklyChangeKg: Double?,
    val trend: BulkTrend,
    val progress: Float,
    val suggestedKcal: Int?,
    val dataWeeks: Int
)

object LeanBulkTracker {
    fun analyze(entries: List<BodyProgressEntry>, today: LocalDate = LocalDate.now(),
                currentKcal: Int = LeanBulkTargets.INITIAL_KCAL): LeanBulkReport {
        val weights = entries.asSequence()
            .filter { !it.date.isAfter(today) }
            .mapNotNull { e -> e.weightKg?.takeIf { it.isFinite() && it in 20.0..400.0 }
                ?.let { e.date to it } }
            .toList().toMap()
        val latest = weights.maxByOrNull { it.key }?.value
        fun week(index: Int): Pair<Double?, Int> {
            val end = today.plusDays(1).minusWeeks(index.toLong())
            val start = end.minusDays(7)
            val values = weights.filterKeys { !it.isBefore(start) && it.isBefore(end) }.values
            return values.takeIf { it.size >= 3 }?.average() to values.size
        }
        val current = week(0)
        val previous = week(1)
        val oldest = week(2)
        val count = listOf(current.first, previous.first, oldest.first).count { it != null }
        // 3 settimane consecutive, >= 3 pesate per settimana: niente consigli prematuri.
        val weekly = if (count == 3)
            (requireNotNull(current.first) - requireNotNull(oldest.first)) / 2.0
        else null
        val trend = when {
            weekly == null -> BulkTrend.NEED_MORE_DATA
            weekly < 0.10 -> BulkTrend.SLOW
            weekly > 0.30 -> BulkTrend.FAST
            else -> BulkTrend.ON_TRACK
        }
        val suggested = when (trend) {
            BulkTrend.SLOW -> (currentKcal + 100).coerceAtMost(4000)
            BulkTrend.FAST -> (currentKcal - 100).coerceAtLeast(1800)
            else -> null
        }?.takeUnless { it == currentKcal }
        val progress = ((latest ?: LeanBulkTargets.START_KG) -
            LeanBulkTargets.START_KG) /
            (LeanBulkTargets.FIRST_GOAL_HIGH_KG - LeanBulkTargets.START_KG)
        return LeanBulkReport(latest, current.first, current.second, weekly, trend,
            progress.toFloat().coerceIn(0f, 1f), suggested, count)
    }
}
