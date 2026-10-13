package it.pulse.workout.notifications

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
import it.pulse.workout.WorkoutTimerService
import it.pulse.workout.data.WorkoutDraft
import it.pulse.workout.data.WorkoutStore
import java.util.concurrent.TimeUnit

/** Requisiti controllabili senza dipendenze da Android, per i test unitari. */
object RestAlertPolicy {
    fun shouldNotify(
        savedSessionStart: Long,
        savedRestUntil: Long,
        expectedSessionStart: Long,
        expectedRestUntil: Long,
        currentTime: Long
    ): Boolean =
        expectedSessionStart > 0 &&
        expectedRestUntil > expectedSessionStart &&
        savedSessionStart == expectedSessionStart &&
        savedRestUntil == expectedRestUntil &&
        currentTime >= expectedRestUntil &&
        currentTime - expectedRestUntil <= 15 * 60_000L
}

/**
 * Fallback del timer recupero: il foreground service invia immediatamente
 * se il processo resta vivo; WorkManager prova a consegnare anche dopo la
 * terminazione del processo. L'avviso non è un allarme esatto garantito.
 */
object RestAlertScheduler {
    private const val WORK_NAME = "buttigogym_active_rest_alert"
    private const val INPUT_START = "session_started_at"
    private const val INPUT_DUE = "rest_ends_at"
    private const val PREFS = "buttigogym_rest_alert_delivery"
    private const val DELIVERED = "last_delivered_slot"
    private const val CHANNEL = "buttigogym_rest_alerts_v2"
    private const val NOTIFICATION_ID = 3011
    private val guard = Any()

    fun sync(context: Context, draft: WorkoutDraft?) {
        val wm = WorkManager.getInstance(context.applicationContext)
        if (draft == null || draft.startedAt <= 0 ||
            draft.restUntilMillis <= System.currentTimeMillis()) {
            wm.cancelUniqueWork(WORK_NAME)
            return
        }
        val delay = (draft.restUntilMillis - System.currentTimeMillis()).coerceAtLeast(1_000)
        val request = OneTimeWorkRequestBuilder<RestFinishedWorker>()
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .setInputData(workDataOf(
                INPUT_START to draft.startedAt,
                INPUT_DUE to draft.restUntilMillis
            ))
            .addTag("buttigogym_rest")
            .build()
        wm.enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.REPLACE, request)
    }

    /** Consegna condivisa fra servizio e worker, per evitare notifiche doppie. */
    fun notifyIfDue(context: Context, sessionStart: Long, restDue: Long): Boolean =
        synchronized(guard) {
            val draft = WorkoutStore(context).loadDraft() ?: return@synchronized false
            if (!RestAlertPolicy.shouldNotify(
                    draft.startedAt, draft.restUntilMillis,
                    sessionStart, restDue, System.currentTimeMillis()
                )) return@synchronized false

            val slot = "${sessionStart}_$restDue"
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            if (prefs.getString(DELIVERED, "") == slot) return@synchronized false

            if (Build.VERSION.SDK_INT >= 33 &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) return@synchronized false
            val nm = context.getSystemService(NotificationManager::class.java)
            if (!nm.areNotificationsEnabled()) return@synchronized false
            // Creazione idempotente: non cambia le scelte dell'utente sul canale.
            nm.createNotificationChannel(NotificationChannel(
                CHANNEL, "Avvisi fine recupero", NotificationManager.IMPORTANCE_DEFAULT
            ))
            if (nm.getNotificationChannel(CHANNEL)?.importance ==
                NotificationManager.IMPORTANCE_NONE) return@synchronized false

            val pending = PendingIntent.getActivity(context, NOTIFICATION_ID,
                Intent(context, MainActivity::class.java)
                    .putExtra(WorkoutTimerService.EXTRA_OPEN_WORKOUT, true)
                    .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            val notification = NotificationCompat.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_workout_notification)
                .setContentTitle("Recupero terminato")
                .setContentText("Puoi iniziare la prossima serie")
                .setContentIntent(pending)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .build()
            return@synchronized runCatching {
                nm.notify(NOTIFICATION_ID, notification)
                prefs.edit().putString(DELIVERED, slot).commit()
            }.getOrDefault(false)
        }
}

class RestFinishedWorker(context: Context, parameters: WorkerParameters) :
    Worker(context, parameters) {
    override fun doWork(): Result {
        val started = inputData.getLong("session_started_at", 0L)
        val due = inputData.getLong("rest_ends_at", 0L)
        runCatching { RestAlertScheduler.notifyIfDue(applicationContext, started, due) }
            .onFailure {
                android.util.Log.w("ButtiGoGym/Rest", "Background rest alert failed", it)
            }
        return Result.success()
    }
}
