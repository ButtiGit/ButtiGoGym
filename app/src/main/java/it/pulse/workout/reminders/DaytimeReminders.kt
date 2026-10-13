package it.pulse.workout.reminders

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.Worker
import androidx.work.WorkerParameters
import androidx.work.WorkManager
import androidx.work.workDataOf
import it.pulse.workout.MainActivity
import it.pulse.workout.R
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.concurrent.TimeUnit

/** Promemoria locali: disattivabili, privati, senza foreground service continuo. */
object ReminderScheduler {
    private const val PREFS = "buttigogym_reminders_v1"
    private const val CHANNEL_WATER = "buttigogym_water_v1"
    private const val CHANNEL_MOVE = "buttigogym_move_v1"
    private const val DELIVERED = "buttigogym_reminder_last_delivery"
    private val deliveryLock = Any()

    fun enabled(context: Context, kind: ReminderKind): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(kind.key, true)

    fun setEnabled(context: Context, kind: ReminderKind, value: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(kind.key, value).apply()
        scheduleNext(context, kind)
    }

    fun sync(context: Context) {
        ensureChannels(context)
        ReminderKind.entries.forEach { scheduleNext(context, it) }
    }

    fun notificationsGranted(context: Context): Boolean =
        (Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED) &&
            context.getSystemService(NotificationManager::class.java).areNotificationsEnabled()

    fun channelProblem(context: Context, kind: ReminderKind): String? {
        if (!notificationsGranted(context)) return "Permesso o notifiche dell'app disattivati"
        ensureChannels(context)
        val id = channelId(kind)
        val channel = context.getSystemService(NotificationManager::class.java)
            .getNotificationChannel(id)
        return if (channel?.importance == NotificationManager.IMPORTANCE_NONE)
            "Canale ${if (kind == ReminderKind.WATER) "Bere acqua" else "Alzarsi e muoversi"} disattivato"
        else null
    }

    private fun channelId(kind: ReminderKind): String =
        if (kind == ReminderKind.WATER) CHANNEL_WATER else CHANNEL_MOVE

    private fun uniqueName(kind: ReminderKind) = "buttigogym_remind_${kind.key}"

    fun scheduleNext(context: Context, kind: ReminderKind, now: LocalDateTime = LocalDateTime.now()) {
        val wm = WorkManager.getInstance(context.applicationContext)
        val name = uniqueName(kind)
        if (!enabled(context, kind)) {
            wm.cancelUniqueWork(name)
            return
        }
        val due = ReminderClock.nextAfter(kind, now)
        // java.time locale -> istante considerando l'eventuale cambio ora legale.
        val dueMillis = due.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val delay = (dueMillis - System.currentTimeMillis()).coerceAtLeast(1000L)
        val request = OneTimeWorkRequestBuilder<DaytimeReminderWorker>()
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .setInputData(workDataOf(
                DaytimeReminderWorker.KIND to kind.key,
                DaytimeReminderWorker.DUE_EPOCH to dueMillis
            ))
            .addTag("buttigogym_daytime_reminders")
            .build()
        wm.enqueueUniqueWork(name, ExistingWorkPolicy.REPLACE, request)
    }

    private fun ensureChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(
            CHANNEL_WATER, "Bere acqua", NotificationManager.IMPORTANCE_DEFAULT
        ).apply { description = "Promemoria idratazione ogni mezz'ora dalle 08:30 alle 18:00" })
        manager.createNotificationChannel(NotificationChannel(
            CHANNEL_MOVE, "Alzarsi e muoversi", NotificationManager.IMPORTANCE_DEFAULT
        ).apply { description = "Promemoria movimento ogni ora nella fascia 08:30–18:00" })
    }

    /** Un solo avviso per slot, anche dopo reboot o ri-esecuzione del Worker. */
    fun post(context: Context, kind: ReminderKind, slotMillis: Long): Boolean =
        synchronized(deliveryLock) {
            if (!enabled(context, kind) || channelProblem(context, kind) != null)
                return@synchronized false
            val prefs = context.getSharedPreferences(DELIVERED, Context.MODE_PRIVATE)
            val key = kind.key
            if (prefs.getLong(key, Long.MIN_VALUE) == slotMillis)
                return@synchronized false

            val title = if (kind == ReminderKind.WATER) "Un sorso d'acqua 💧"
                        else "Alzati un momento 🚶"
            val message = if (kind == ReminderKind.WATER)
                "È il momento di bere un po' d'acqua."
                else "Fai due passi e sgranchisci le gambe."
            val id = if (kind == ReminderKind.WATER) 5201 else 5202
            val intent = Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            val pending = PendingIntent.getActivity(context, id, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            val notification = NotificationCompat.Builder(context, channelId(kind))
                .setSmallIcon(R.drawable.ic_workout_notification)
                .setContentTitle(title)
                .setContentText(message)
                .setContentIntent(pending)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .setWhen(slotMillis)
                .setShowWhen(true)
                .build()
            return@synchronized runCatching {
                context.getSystemService(NotificationManager::class.java)
                    .notify(id, notification)
                prefs.edit().putLong(key, slotMillis).commit()
            }.getOrDefault(false)
        }

    /** Un test che riporta fallimento se il canale è bloccato. */
    fun test(context: Context, kind: ReminderKind): Boolean =
        post(context, kind, System.currentTimeMillis())

}

class DaytimeReminderWorker(context: Context, parameters: WorkerParameters) :
    Worker(context, parameters) {
    companion object {
        const val KIND = "kind"
        const val DUE_EPOCH = "due_epoch"
    }

    override fun doWork(): Result {
        val kind = ReminderKind.entries.firstOrNull {
            it.key == inputData.getString(KIND)
        } ?: return Result.success()
        if (!ReminderScheduler.enabled(applicationContext, kind)) return Result.success()
        val now = System.currentTimeMillis()
        val due = inputData.getLong(DUE_EPOCH, 0L)
        val elapsed = now - due
        try {
            // WorkManager è inexact: se Android ci risveglia molto in ritardo,
            // non inviare promemoria della mattina nel pomeriggio.
            if (due > 0 && elapsed >= -60_000 && elapsed <= 12 * 60_000L) {
                ReminderScheduler.post(applicationContext, kind, due)
            }
        } catch (error: Exception) {
            android.util.Log.w("ButtiGoGym/Reminders", "Reminder not delivered", error)
        } finally {
            // Prenota lo slot successivo anche dopo notifiche bloccate o errori.
            ReminderScheduler.scheduleNext(applicationContext, kind)
        }
        return Result.success()
    }
}
