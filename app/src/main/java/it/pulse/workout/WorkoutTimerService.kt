package it.pulse.workout

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import it.pulse.workout.data.WorkoutDay
import it.pulse.workout.data.WorkoutDraft
import it.pulse.workout.data.WorkoutStore

/** Servizio attivo SOLO durante una sessione iniziata dall'utente.
 *  La notifica usa un cronometro di sistema: niente loop al secondo in background.
 */
class WorkoutTimerService : Service() {
    companion object {
        private const val WORKOUT_CHANNEL = "buttigogym_workout_timer_v1"
        private const val REST_CHANNEL = "buttigogym_rest_alerts_v1"
        private const val LIVE_ID = 3010
        private const val REST_DONE_ID = 3011
        private const val ACTION_SYNC = "it.pulse.workout.SYNC_WORKOUT_TIMER"
        private const val EXTRA_STARTED = "started"
        private const val EXTRA_REST_UNTIL = "restUntil"
        private const val EXTRA_NAME = "name"
        private const val EXTRA_EXERCISE = "exercise"

        fun sync(context: Context, draft: WorkoutDraft, day: WorkoutDay) {
            val current = day.exercises.getOrNull(draft.guidedExerciseIndex)?.name.orEmpty()
            val intent = Intent(context, WorkoutTimerService::class.java).apply {
                action = ACTION_SYNC
                putExtra(EXTRA_STARTED, draft.startedAt)
                putExtra(EXTRA_REST_UNTIL, draft.restUntilMillis)
                putExtra(EXTRA_NAME, day.name)
                putExtra(EXTRA_EXERCISE, current)
            }
            // È una sessione iniziata esplicitamente dall'utente, mentre l'app è aperta.
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, WorkoutTimerService::class.java))
        }
    }

    private val handler = Handler(Looper.getMainLooper())
    private var restCallback: Runnable? = null
    private var sessionStart = 0L
    private var restEnd = 0L
    private var title = "Allenamento"
    private var exercise = ""
    private var foregroundStarted = false

    private val manager get() = getSystemService(NotificationManager::class.java)

    override fun onCreate() {
        super.onCreate()
        manager.createNotificationChannel(NotificationChannel(
            WORKOUT_CHANNEL, "Sessione attiva", NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Cronometro della sessione e conto alla rovescia del recupero"
            setShowBadge(false)
            enableVibration(false)
        })
        manager.createNotificationChannel(NotificationChannel(
            REST_CHANNEL, "Fine recupero", NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Avviso quando puoi iniziare la serie successiva"
        })
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_SYNC) {
            sessionStart = intent.getLongExtra(EXTRA_STARTED, 0L)
            restEnd = intent.getLongExtra(EXTRA_REST_UNTIL, 0L)
            title = intent.getStringExtra(EXTRA_NAME)?.take(64).orEmpty().ifBlank { "Allenamento" }
            exercise = intent.getStringExtra(EXTRA_EXERCISE)?.take(70).orEmpty()
        } else {
            // Dopo un riavvio del servizio, riprendi solo una sessione realmente salvata.
            val draft = WorkoutStore(this).loadDraft()
            if (draft == null) {
                stopSelf()
                return START_NOT_STICKY
            }
            sessionStart = draft.startedAt
            restEnd = draft.restUntilMillis
            title = draft.daySnapshot?.name ?: "Allenamento"
            exercise = draft.daySnapshot?.exercises
                ?.getOrNull(draft.guidedExerciseIndex)?.name.orEmpty()
        }
        if (sessionStart <= 0) {
            stopSelf()
            return START_NOT_STICKY
        }

        val notification = buildActiveNotification()
        if (!foregroundStarted) {
            ServiceCompat.startForeground(
                this, LIVE_ID, notification,
                if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0
            )
            foregroundStarted = true
        } else {
            manager.notify(LIVE_ID, notification)
        }
        scheduleRestCompletion()
        return START_STICKY
    }

    private fun openApp(): PendingIntent {
        val intent = Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        return PendingIntent.getActivity(this, 3010, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    private fun buildActiveNotification(): Notification {
        val now = System.currentTimeMillis()
        val resting = restEnd > now
        return NotificationCompat.Builder(this, WORKOUT_CHANNEL)
            .setSmallIcon(R.drawable.ic_workout_notification)
            .setContentTitle(if (resting) "Recupero in corso" else "ButtiGoGym · $title")
            .setContentText(if (resting) "Prossima serie: ${exercise.ifBlank { title }}"
                else exercise.ifBlank { "Allenamento in corso" })
            .setContentIntent(openApp())
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setShowWhen(true)
            .setWhen(if (resting) restEnd else sessionStart)
            .setUsesChronometer(true)
            .setChronometerCountDown(resting)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()
    }

    private fun scheduleRestCompletion() {
        restCallback?.let { handler.removeCallbacks(it) }
        restCallback = null
        val remaining = restEnd - System.currentTimeMillis()
        if (remaining <= 0L) return
        val target = restEnd
        val callback = Runnable {
            if (target != restEnd) return@Runnable
            if (System.currentTimeMillis() < target) {
                scheduleRestCompletion()
                return@Runnable
            }
            restEnd = 0L
            if (foregroundStarted) manager.notify(LIVE_ID, buildActiveNotification())
            manager.notify(REST_DONE_ID, NotificationCompat.Builder(this, REST_CHANNEL)
                .setSmallIcon(R.drawable.ic_workout_notification)
                .setContentTitle("Recupero terminato")
                .setContentText("Puoi iniziare la prossima serie")
                .setContentIntent(openApp())
                .setAutoCancel(true)
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .build())
        }
        restCallback = callback
        handler.postDelayed(callback, remaining)
    }

    override fun onDestroy() {
        restCallback?.let { handler.removeCallbacks(it) }
        restCallback = null
        super.onDestroy()
    }
}
