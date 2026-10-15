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
import it.pulse.workout.data.PersonalSettings
import it.pulse.workout.data.PersonalSettingsStore
import it.pulse.workout.data.WorkoutStore
import it.pulse.workout.data.WeeklyTrainingReportCalculator
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import java.util.concurrent.TimeUnit

/** Una sola opportunità per settimana, nel primo giorno/orario consentito dopo lunedì. */
object WeeklyNudgeClock {
    fun nextAfter(now: LocalDateTime, settings: PersonalSettings): LocalDateTime {
        val monday = now.toLocalDate()
            .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val chosenTime = maxOf(settings.reminderStart.plusMinutes(15), LocalTime.of(9, 0))
            .coerceAtMost(settings.reminderEnd.minusMinutes(15))
        for (week in 0L..2L) {
            for (offset in 0L..6L) {
                val day = monday.plusWeeks(week).plusDays(offset)
                if (day.dayOfWeek !in settings.reminderDays) continue
                val candidate = day.atTime(chosenTime)
                if (candidate.isAfter(now)) return candidate
            }
        }
        error("Nessuna finestra valida per riepilogo settimanale")
    }

    fun weekToReport(due: LocalDateTime) = due.toLocalDate()
        .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).minusWeeks(1)
}

object WeeklyMotivationScheduler {
    private const val PREFS = "buttigogym_reminders_v1"
    private const val ENABLED = "weekly_motivation_optin_v1"
    private const val SENT = "weekly_motivation_last_week_v1"
    private const val WORK = "buttigogym_weekly_motivation"
    private const val CHANNEL = "buttigogym_weekly_motivation_v1"
    private val lock = Any()

    fun enabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(ENABLED, false)

    fun setEnabled(context: Context, value: Boolean) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        check(prefs.edit().putBoolean(ENABLED, value).commit())
        sync(context)
    }

    fun sync(context: Context) {
        val wm = WorkManager.getInstance(context.applicationContext)
        if (!enabled(context)) {
            wm.cancelUniqueWork(WORK)
            return
        }
        context.getSystemService(NotificationManager::class.java)
            .createNotificationChannel(NotificationChannel(
                CHANNEL, "Riepilogo settimanale ButtiGoGym",
                NotificationManager.IMPORTANCE_LOW
            ).apply { description = "Un riepilogo discreto della settimana precedente" })
        val now = LocalDateTime.now()
        val due = WeeklyNudgeClock.nextAfter(now, PersonalSettingsStore(context).read())
        val millis = due.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val wait = (millis - System.currentTimeMillis()).coerceAtLeast(1000L)
        val request = OneTimeWorkRequestBuilder<WeeklyMotivationWorker>()
            .setInitialDelay(wait, TimeUnit.MILLISECONDS)
            .setInputData(workDataOf(WeeklyMotivationWorker.DUE_EPOCH to millis))
            .addTag("buttigogym_weekly")
            .build()
        wm.enqueueUniqueWork(WORK, ExistingWorkPolicy.REPLACE, request)
    }

    fun deliver(context: Context, due: LocalDateTime): Boolean = synchronized(lock) {
        if (!enabled(context) ||
            (Build.VERSION.SDK_INT >= 33 &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) ||
            !context.getSystemService(NotificationManager::class.java).areNotificationsEnabled())
            return@synchronized false
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val previousMonday = WeeklyNudgeClock.weekToReport(due)
        if (prefs.getString(SENT, null) == previousMonday.toString()) return@synchronized false
        // Usare il giorno della settimana attuale, NON il momento del worker,
        // per non inserire nel report una settimana diversa se Android è in ritardo.
        val report = WeeklyTrainingReportCalculator.evaluate(
            WorkoutStore(context).history(), emptyList(),
            PersonalSettingsStore(context).read().weeklyWorkouts, due.toLocalDate()
        )
        if (report.monday != previousMonday || report.completedDays == 0)
            return@synchronized false
        val title = "Il tuo ritmo continua"
        val body = if (report.completedDays >= report.goalDays)
            "Settimana scorsa: ${report.completedDays} sedute. Ottimo lavoro!"
        else "Settimana scorsa: ${report.completedDays} sedute. Ogni passo conta."
        val intent = PendingIntent.getActivity(context, 5291,
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_workout_notification)
            .setContentTitle(title).setContentText(body).setContentIntent(intent)
            .setAutoCancel(true).setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW).build()
        return@synchronized runCatching {
            context.getSystemService(NotificationManager::class.java).notify(5291, notification)
            prefs.edit().putString(SENT, previousMonday.toString()).commit()
        }.getOrDefault(false)
    }
}

class WeeklyMotivationWorker(context: Context, parameters: WorkerParameters) :
    Worker(context, parameters) {
    companion object { const val DUE_EPOCH = "weekly_due_epoch" }
    override fun doWork(): Result {
        try {
            val dueEpoch = inputData.getLong(DUE_EPOCH, 0L)
            val now = System.currentTimeMillis()
            val elapsed = now - dueEpoch
            if (dueEpoch > 0 && elapsed >= -60_000L && elapsed <= 90 * 60_000L) {
                val due = java.time.LocalDateTime.ofInstant(
                    java.time.Instant.ofEpochMilli(dueEpoch), ZoneId.systemDefault())
                WeeklyMotivationScheduler.deliver(applicationContext, due)
            }
        } catch (error: Exception) {
            android.util.Log.w("ButtiGoGym/Weekly", "Weekly reminder skipped", error)
        } finally {
            WeeklyMotivationScheduler.sync(applicationContext)
        }
        return Result.success()
    }
}
