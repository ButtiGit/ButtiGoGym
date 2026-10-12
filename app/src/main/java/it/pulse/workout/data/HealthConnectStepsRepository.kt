package it.pulse.workout.data

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.request.AggregateGroupByPeriodRequest
import androidx.health.connect.client.time.TimeRangeFilter
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.Period

/**
 * Accesso in sola lettura ai passi di Health Connect.
 *
 * Usiamo l'aggregazione SENZA filtro DataOrigin: Health Connect deduplica secondo
 * le priorità impostate dall'utente e include Samsung Health se sincronizzato.
 * Non sommare questi numeri ai passi del sensore locale.
 */
class HealthConnectStepsRepository(private val context: Context) {
    companion object {
        val READ_STEPS = HealthPermission.getReadPermission(StepsRecord::class)
        const val PROVIDER_PACKAGE = "com.google.android.apps.healthdata"
    }

    fun sdkStatus(): Int = HealthConnectClient.getSdkStatus(context)

    private fun client(): HealthConnectClient = HealthConnectClient.getOrCreate(context)

    suspend fun granted(): Boolean =
        sdkStatus() == HealthConnectClient.SDK_AVAILABLE &&
            READ_STEPS in client().permissionController.getGrantedPermissions()

    /**
     * Al massimo 28 giorni, entro il limite standard di cronologia (30 giorni),
     * senza chiedere ulteriori permessi sensibili.
     */
    suspend fun recentSteps(today: LocalDate = LocalDate.now()): Map<String, Int> {
        check(granted()) { "Lettura passi non autorizzata" }

        val begin = today.minusDays(27)
        val result = client().aggregateGroupByPeriod(
            AggregateGroupByPeriodRequest(
                metrics = setOf(StepsRecord.COUNT_TOTAL),
                timeRangeFilter = TimeRangeFilter.between(
                    begin.atStartOfDay(),
                    LocalDateTime.now()
                ),
                timeRangeSlicer = Period.ofDays(1)
            )
        )

        // Anche lo zero è autorevole: evita di riutilizzare passi locali non aggiornati.
        val byDay = (0L..27L).associate { days ->
            begin.plusDays(days).toString() to 0
        }.toMutableMap()

        result.forEach { bucket ->
            val key = bucket.startTime.toLocalDate().toString()
            if (key in byDay) {
                val steps = bucket.result[StepsRecord.COUNT_TOTAL] ?: 0L
                byDay[key] = steps.coerceIn(0L, 200_000L).toInt()
            }
        }
        return byDay
    }
}
