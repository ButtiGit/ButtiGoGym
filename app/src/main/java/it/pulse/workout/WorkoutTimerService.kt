package it.pulse.workout

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.app.NotificationManagerCompat
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
        private const val WORKOUT_CHANNEL = "buttigogym_workout_timer_v2"
        private const val REST_CHANNEL = "buttigogym_rest_alerts_v2"
        private const val LIVE_ID = 3010
        private const val REST_DONE_ID = 3011
        private const val TEST_ID = 3012
        private const val TIMER_PREFS = "buttigogym_timer_service"
        private const val LAST_STATE = "last_state"

        fun notificationsVisible(context: Context): Boolean {
            if (Build.VERSION.SDK_INT >= 33 &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) return false
            if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return false
            val manager = context.getSystemService(NotificationManager::class.java)
            return listOf(WORKOUT_CHANNEL, REST_CHANNEL).all { channel ->
                manager.getNotificationChannel(channel)?.importance != NotificationManager.IMPORTANCE_NONE
            }
        }

        /** Stato aggiornato dal servizio in background, leggibile dalla schermata workout. */
        fun state(context: Context): String = context.getSharedPreferences(TIMER_PREFS,
            Context.MODE_PRIVATE).getString(LAST_STATE, "not_started").orEmpty()

        /** Notifica manuale per verificare se il sistema visualizza davvero gli avvisi. */
        fun testNotification(context: Context): Boolean {
            if (!notificationsVisible(context)) return false
            val manager = context.getSystemService(NotificationManager::class.java)
            ensureChannels(manager)
            manager.notify(TEST_ID, NotificationCompat.Builder(context, REST_CHANNEL)
                .setSmallIcon(R.drawable.ic_workout_notification)
                .setContentTitle("Notifiche ButtiGoGym attive")
                .setContentText("La prova ha funzionato. Torna all'allenamento!")
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .build())
            return true
        }

        private fun ensureChannels(manager: NotificationManager) {
            manager.createNotificationChannel(NotificationChannel(
                WORKOUT_CHANNEL, "Timer allenamento", NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notifica persistente con durata sessione e recupero"
                setShowBadge(false)
                setSound(null, null)
            })
            manager.createNotificationChannel(NotificationChannel(
                REST_CHANNEL, "Avvisi fine recupero", NotificationManager.IMPORTANCE_DEFAULT
            ).apply { description = "Avviso al termine del recupero tra le serie" })
        }
        const val EXTRA_OPEN_WORKOUT = "openWorkoutTimer"
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
            context.getSharedPreferences(TIMER_PREFS, Context.MODE_PRIVATE).edit()
                .putString(LAST_STATE, "stopped").apply()
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
        ensureChannels(manager)
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

        val failure = runCatching {
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
            getSharedPreferences(TIMER_PREFS, Context.MODE_PRIVATE).edit()
                .putString(LAST_STATE, "running").apply()
            scheduleRestCompletion()
        }.exceptionOrNull()
        if (failure != null) {
            android.util.Log.e("ButtiGoGym/Timer", "Cannot start foreground workout timer", failure)
            getSharedPreferences(TIMER_PREFS, Context.MODE_PRIVATE).edit()
                .putString(LAST_STATE, "error:" + (failure.message ?: failure.javaClass.simpleName).take(140))
                .apply()
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    private fun openApp(): PendingIntent {
        val intent = Intent(this, MainActivity::class.java)
            .putExtra(EXTRA_OPEN_WORKOUT, true)
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
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
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
        if (getSharedPreferences(TIMER_PREFS, Context.MODE_PRIVATE)
                .getString(LAST_STATE, "") == "running") {
            getSharedPreferences(TIMER_PREFS, Context.MODE_PRIVATE).edit()
                .putString(LAST_STATE, "stopped").apply()
        }
        super.onDestroy()
    }
}
