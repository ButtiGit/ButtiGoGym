package it.pulse.workout.data

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import java.time.LocalDate

/**
 * Step Counter hardware (TYPE_STEP_COUNTER): valore cumulativo dal riavvio.
 * La lettura corrente sottratta all'ultima lettura dello stesso giorno evita doppi conteggi.
 * Il primo utilizzo stabilisce il riferimento, senza inventare passi già fatti.
 *
 * Non è un servizio di monitoraggio in background: quando il giorno cambia senza che
 * l'app abbia ricevuto eventi, il conteggio del nuovo giorno riparte da una nuova base.
 */
class AutomaticStepTracker(
    private val context: Context,
    private val store: WellnessStore,
    private val onUpdated: (WellnessData) -> Unit
) : SensorEventListener {
    private val manager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val sensor = manager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
    val available: Boolean get() = sensor != null
    private val prefs = context.getSharedPreferences("buttigogym_step_sensor_v1", Context.MODE_PRIVATE)

    fun start(): Boolean {
        val deviceSensor = sensor ?: return false
        return manager.registerListener(this, deviceSensor, SensorManager.SENSOR_DELAY_NORMAL)
    }

    fun stop() = manager.unregisterListener(this)

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type != Sensor.TYPE_STEP_COUNTER) return
        val total = event.values.firstOrNull()?.toLong() ?: return
        if (total < 0L) return
        val today = LocalDate.now().toString()
        val anchorDate = prefs.getString("last_day", null)
        val lastTotal = prefs.getLong("last_total", -1)
        // Primo campione, nuovo giorno o reboot: ricomincia la base da qui.
        val delta = if (anchorDate == today && lastTotal in 0L..total) {
            (total - lastTotal).coerceAtMost(100_000)
        } else 0L
        if (delta > 0) {
            val before = store.read().stepsOn(LocalDate.now())
            val nextCount = (before.toLong() + delta).coerceAtMost(200_000).toInt()
            onUpdated(store.updateSteps(LocalDate.now(), nextCount))
        }
        prefs.edit().putString("last_day", today).putLong("last_total", total).apply()
    }
}
