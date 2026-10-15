package it.pulse.workout.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import it.pulse.workout.data.WorkoutStore
import it.pulse.workout.notifications.RestAlertScheduler

/**
 * Ricalcola gli orari locali dopo riavvio, cambio d'ora manuale o fuso.
 * Nessun timer o foreground service viene avviato dal broadcast.
 */
class ReminderRescheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        when (intent?.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED -> {
                runCatching {
                    ReminderScheduler.sync(context.applicationContext)
                    WeeklyMotivationScheduler.sync(context.applicationContext)
                    RestAlertScheduler.sync(context.applicationContext,
                        WorkoutStore(context).loadDraft())
                }.onFailure { error ->
                    Log.w("ButtiGoGym/Reminders", "Cannot reschedule reminders", error)
                }
            }
        }
    }
}
