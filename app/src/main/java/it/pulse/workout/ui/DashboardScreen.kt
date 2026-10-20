package it.pulse.workout.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DirectionsWalk
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.AccessibilityNew
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import it.pulse.workout.R
import kotlin.math.abs
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import it.pulse.workout.data.*
import java.text.NumberFormat
import java.time.DayOfWeek
import java.time.Instant
import it.pulse.workout.updates.AppUpdateStatus
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.util.Locale

/** Dashboard locale a moduli, ispirata al linguaggio dei widget iOS. */
@Composable
fun DashboardScreen(
    upcoming: WorkoutDay,
    days: List<WorkoutDay>,
    active: WorkoutDraft?,
    history: List<SessionRecord>,
    wellness: WellnessData,
    personalSettings: PersonalSettings,
    weeklyReport: WeeklyTrainingReport,
    weeklyMissions: WeeklyMissionSummary,
    weeklyReminderEnabled: Boolean,
    onToggleWeeklyReminder: (Boolean) -> Unit,
    todayCheckIn: PreWorkoutCheckIn?,
    onOpenCheckIn: () -> Unit,
    stepStatus: String,
    healthConnected: Boolean,
    healthSyncing: Boolean,
    healthStatus: String,
    onConnectHealth: () -> Unit,
    onRefreshHealth: () -> Unit,
    onRequestStepAccess: () -> Unit,
    onStart: (WorkoutDay) -> Unit,
    onStartQuick: (WorkoutDay) -> Unit,
    onSaveSteps: (Int) -> Unit,
    onToggleRest: () -> Unit,
    onOpenPlan: () -> Unit,
    onOpenCalendar: () -> Unit,
    reminderWaterEnabled: Boolean,
    reminderMoveEnabled: Boolean,
    reminderWaterProblem: String?,
    reminderMoveProblem: String?,
    onTestWaterReminder: () -> Unit,
    onTestMoveReminder: () -> Unit,
    onOpenReminderNotificationSettings: () -> Unit,
    remindersPermissionGranted: Boolean,
    onToggleWaterReminder: (Boolean) -> Unit,
    onToggleMoveReminder: (Boolean) -> Unit,
    onEnableReminderNotifications: () -> Unit,
    updateInstalledVersion: String,
    updateStatus: AppUpdateStatus,
    updateChecking: Boolean,
    updateDownloading: Boolean,
    updateProgress: Int,
    updateVerified: Boolean,
    onCheckUpdate: () -> Unit,
    onDownloadUpdate: () -> Unit,
    onInstallUpdate: () -> Unit,
    backupStatus: String,
    backupBusy: Boolean,
    onExportBackup: () -> Unit,
    onImportBackup: () -> Unit
) {

    val today = LocalDate.now()
    val current = active?.daySnapshot ?: days.firstOrNull { it.id == active?.dayId } ?: upcoming
    val summary = remember(today, wellness, history, personalSettings.weeklyWorkouts) {
        StreakCalculator.calculate(today, wellness, history, personalSettings.weeklyWorkouts)
    }
    val context = LocalContext.current
    val store = remember(context) { HomeDisplaySettingsStore(context) }
    var layout by remember { mutableStateOf(store.read()) }
    var modal by rememberSaveable { mutableStateOf<String?>(null) }
    var stepsInput by rememberSaveable(today.toString(), summary.todaySteps) {
        mutableStateOf(summary.todaySteps.toString())
    }
    var preferenceError by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(backupStatus) {
        if (backupStatus.startsWith("Ripristino riuscito")) layout = store.read()
    }
    fun saveLayout(value: HomeDisplaySettings) {
        runCatching { store.save(value) }.onSuccess { layout = value; preferenceError = null }
            .onFailure { preferenceError = it.message }
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize().background(PulseColors.Background),
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("ButtiGoGym", style = MaterialTheme.typography.headlineMedium, color = PulseColors.Foreground)
                    Text(today.format(DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.ITALIAN)),
                        color = PulseColors.Muted, style = MaterialTheme.typography.bodyMedium)
                }
                IconButton(onClick = onOpenCalendar) {
                    Icon(Icons.Default.CalendarMonth, "Calendario", tint = PulseColors.Foreground)
                }
                IconButton(onClick = { modal = "tools" }) {
                    Icon(Icons.Default.Settings, "Strumenti e aggiornamenti", tint = PulseColors.Foreground)
                }
            }
        }
        item {
            MascotCoachCard(MascotCoach.today(active != null, summary.restedToday, summary.workoutToday,
                summary.workoutDaysThisWeek, personalSettings.weeklyWorkouts), pose = when {
                active != null -> CoachPose.READY
                summary.workoutToday -> CoachPose.DONE
                summary.restedToday -> CoachPose.REST
                else -> CoachPose.HOME
            })
        }
        item {
            BGCard(padding = 20.dp) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(if (active != null) "In corso" else "Il tuo allenamento",
                        color = PulseColors.Muted, style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f))
                    IconButton(onClick = { modal = "workout" }) {
                        Icon(Icons.Default.MoreHoriz, "Opzioni allenamento", tint = PulseColors.Muted)
                    }
                }
                BGIdentityCard(if (active != null) "SESSIONE ATTIVA" else "IL TUO PROSSIMO PASSO",
                    current.name, "${current.exercises.size} esercizi · ${current.duration}", "↗")
                Spacer(Modifier.height(20.dp))
                BGPrimaryButton(if (active != null) "Riprendi" else "Inizia", { onStart(current) })
            }
        }
        item {
            BGCard(modifier = Modifier.clickable { modal = "week" }, padding = 16.dp) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("🎯 Obiettivo settimanale", modifier = Modifier.weight(1f),
                        color = PulseColors.Foreground, style = MaterialTheme.typography.titleSmall)
                    Text("${summary.workoutDaysThisWeek}/${personalSettings.weeklyWorkouts}",
                        color = PulseColors.Accent, style = MaterialTheme.typography.titleMedium)
                }
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth().padding(bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    repeat(personalSettings.weeklyWorkouts.coerceIn(1, 7)) { index ->
                        val done = index < summary.workoutDaysThisWeek
                        Surface(modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp),
                            color = if (done) PulseColors.Accent else PulseColors.SoftGreen) {
                            Box(Modifier.height(38.dp), contentAlignment = Alignment.Center) {
                                Text(if (done) "✓" else "${index + 1}",
                                    color = if (done) PulseColors.DarkInk else PulseColors.Accent,
                                    fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
                BGAnimatedProgress((summary.workoutDaysThisWeek.toFloat() /
                    personalSettings.weeklyWorkouts.coerceAtLeast(1)).coerceIn(0f, 1f))
                Spacer(Modifier.height(6.dp))
                Text(if (summary.workoutDaysThisWeek >= personalSettings.weeklyWorkouts)
                        "🏆 Obiettivo raggiunto!" else
                        "Ancora ${(personalSettings.weeklyWorkouts - summary.workoutDaysThisWeek).coerceAtLeast(0)} sedute al traguardo",
                    color = PulseColors.Muted, style = MaterialTheme.typography.bodySmall)
            }
        }
        if (updateStatus is AppUpdateStatus.Available || updateDownloading || updateVerified) item {
            HomeAction(if (updateVerified) "Aggiornamento pronto" else "Aggiornamento disponibile", "") { modal = "updates" }
        }
        // Existing visibility preferences become compact shortcuts, never expanded dashboards.
        if (layout.missions) item { HomeAction("Missioni") { modal = "missions" } }
        if (layout.weeklyReport) item { HomeAction("Report") { modal = "report" } }
        if (layout.details || layout.streaks) item { HomeAction("Progressi") { modal = "week" } }
        if (layout.steps) item { HomeAction("Passi", NumberFormat.getIntegerInstance(Locale.ITALIAN).format(summary.todaySteps)) { modal = "health" } }
        if (layout.recovery) item { HomeAction("Recupero") { modal = "recovery" } }
        if (layout.weekPlan) item { HomeAction("La scheda") { onOpenPlan() } }
        if (layout.reminders) item { HomeAction("Promemoria") { modal = "reminders" } }
        if (layout.backup) item { HomeAction("Backup") { modal = "backup" } }
    }
    if (modal != null) BGModal(when (modal) {
        "workout" -> "Allenamento"
        "week" -> "La settimana"
        "report" -> "Report"
        "missions" -> "Missioni"
        "health" -> "Passi"
        "steps" -> "Passi di oggi"
        "recovery" -> "Recupero"
        "updates" -> "Aggiornamenti"
        "reminders" -> "Promemoria"
        "backup" -> "I tuoi dati"
        "home" -> "Scorciatoie Home"
        else -> "Strumenti"
    }, { modal = null }) {
        when (modal) {
            "tools" -> {
                HomeAction("Passi") { modal = "health" }
                HomeAction("Recupero") { modal = "recovery" }
                HomeAction("Report") { modal = "report" }
                HomeAction("Missioni") { modal = "missions" }
                HomeAction("Promemoria") { modal = "reminders" }
                HomeAction("Backup e ripristino") { modal = "backup" }
                HomeAction("Aggiornamenti", updateInstalledVersion) { modal = "updates" }
                HomeAction("Personalizza Home") { modal = "home" }
            }
            "workout" -> {
                if (active == null && QuickWorkoutPlanner.create(current) != null)
                    HomeAction("Allenamento rapido", "35–45 min") { modal = null; onStartQuick(current) }
                HomeAction("Scegli dalla scheda") { modal = null; onOpenPlan() }
                HomeAction("Come ti senti?") { modal = null; onOpenCheckIn() }
            }
            "week" -> {
                Text("${summary.workoutDaysThisWeek} di ${personalSettings.weeklyWorkouts} allenamenti",
                    style = MaterialTheme.typography.titleLarge)
                LinearProgressIndicator(progress = { (summary.workoutDaysThisWeek.toFloat() /
                    personalSettings.weeklyWorkouts.coerceAtLeast(1)).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth(), color = PulseColors.Accent, trackColor = PulseColors.SoftGreen)
                HomeAction("La scheda") { modal = null; onOpenPlan() }
                HomeAction("Calendario") { modal = null; onOpenCalendar() }
                HomeAction("Report") { modal = "report" }
                HomeAction("Missioni") { modal = "missions" }
                Text("${summary.generalDays} giorni di abitudini · ${summary.workoutWeeks} settimane di allenamenti",
                    color = PulseColors.Muted)
            }
            "report" -> WeeklyReportCard(weeklyReport, todayCheckIn) { modal = null; onOpenCheckIn() }
            "missions" -> WeeklyMissionsCard(weeklyMissions, weeklyReminderEnabled, onToggleWeeklyReminder)
            "health" -> {
                Text(NumberFormat.getIntegerInstance(Locale.ITALIAN).format(summary.todaySteps),
                    style = MaterialTheme.typography.headlineMedium, color = PulseColors.Foreground)
                Text("Passi di oggi", color = PulseColors.Muted)
                TextButton(onClick = { modal = "steps" }) { Text("Inserisci totale") }
                TextButton(onClick = onRequestStepAccess) { Text("Attiva contapassi") }
                TextButton(onClick = if (healthConnected) onRefreshHealth else onConnectHealth, enabled = !healthSyncing) {
                    Text(if (healthSyncing) "Sincronizzazione…" else if (healthConnected) "Sincronizza Health Connect" else "Collega Health Connect")
                }
                if (healthStatus.isNotBlank()) Text(healthStatus, color = PulseColors.Muted)
                if (stepStatus.isNotBlank()) Text(stepStatus, color = PulseColors.Muted)
            }
            "steps" -> {
                OutlinedTextField(stepsInput, { next ->
                    if (next.length <= 6 && next.all(Char::isDigit)) stepsInput = next
                }, label = { Text("Totale giornaliero") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth())
                BGPrimaryButton("Salva", {
                    stepsInput.toIntOrNull()?.takeIf { it in 0..200000 }?.let { onSaveSteps(it); modal = "health" }
                }, enabled = stepsInput.toIntOrNull()?.let { it in 0..200000 } == true)
            }
            "recovery" -> {
                Text(if (summary.restedToday) "Oggi è un giorno di recupero" else "Come ti senti oggi?",
                    style = MaterialTheme.typography.titleMedium)
                BGPrimaryButton(if (summary.restedToday) "Annulla giorno di riposo" else "Segna giorno di riposo", onToggleRest)
                TextButton(onClick = { modal = null; onOpenCheckIn() }) { Text("Check-in") }
            }
            "updates" -> ReleaseUpdatePanel(updateInstalledVersion, updateStatus, updateChecking,
                updateDownloading, updateProgress, updateVerified, onCheckUpdate, onDownloadUpdate, onInstallUpdate)
            "backup" -> {
                BGPrimaryButton(if (backupBusy) "Attendi…" else "Esporta backup", onExportBackup, enabled = !backupBusy)
                TextButton(onClick = onImportBackup, enabled = !backupBusy) { Text("Ripristina backup") }
                if (backupStatus.isNotBlank()) Text(backupStatus, color = PulseColors.Muted)
            }
            "reminders" -> {
                HomeToggle("Bere acqua", reminderWaterEnabled, onToggleWaterReminder)
                HomeToggle("Muoversi", reminderMoveEnabled, onToggleMoveReminder)
                HomeToggle("Obiettivo settimanale", weeklyReminderEnabled, onToggleWeeklyReminder)
                if (!remindersPermissionGranted) TextButton(onClick = onEnableReminderNotifications) { Text("Consenti notifiche") }
                if (reminderWaterEnabled) TextButton(onClick = onTestWaterReminder) { Text("Prova acqua") }
                if (reminderMoveEnabled) TextButton(onClick = onTestMoveReminder) { Text("Prova movimento") }
                listOfNotNull(reminderWaterProblem.takeIf { reminderWaterEnabled },
                    reminderMoveProblem.takeIf { reminderMoveEnabled }).distinct().forEach { Text(it) }
                TextButton(onClick = onOpenReminderNotificationSettings) { Text("Impostazioni notifiche") }
            }
            "home" -> {
                HomeToggle("Missioni", layout.missions) { saveLayout(layout.copy(missions = it)) }
                HomeToggle("Report", layout.weeklyReport) { saveLayout(layout.copy(weeklyReport = it)) }
                HomeToggle("Progressi", layout.details || layout.streaks) { saveLayout(layout.copy(details = it, streaks = false)) }
                HomeToggle("Passi", layout.steps) { saveLayout(layout.copy(steps = it)) }
                HomeToggle("Recupero", layout.recovery) { saveLayout(layout.copy(recovery = it)) }
                HomeToggle("La scheda", layout.weekPlan) { saveLayout(layout.copy(weekPlan = it)) }
                HomeToggle("Promemoria", layout.reminders) { saveLayout(layout.copy(reminders = it)) }
                HomeToggle("Backup", layout.backup) { saveLayout(layout.copy(backup = it)) }
                TextButton(onClick = { saveLayout(HomeDisplaySettings()) }) { Text("Home essenziale") }
                preferenceError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        }
    }
}

@Composable
private fun HomeAction(title: String, detail: String = "", onClick: () -> Unit) {
    Surface(onClick = onClick, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp),
        color = PulseColors.Surface) {
        Row(Modifier.padding(horizontal = 18.dp, vertical = 15.dp).heightIn(min = 28.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Text(title, modifier = Modifier.weight(1f), color = PulseColors.Foreground,
                style = MaterialTheme.typography.bodyLarge)
            if (detail.isNotBlank()) Text(detail, color = PulseColors.Muted)
            Spacer(Modifier.width(8.dp))
            Icon(Icons.Default.ChevronRight, null, tint = PulseColors.Muted, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun HomeToggle(title: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 52.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, modifier = Modifier.weight(1f), color = PulseColors.Foreground)
        Switch(checked, onChange)
    }
}
