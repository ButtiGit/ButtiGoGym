package it.pulse.workout

import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import java.io.ByteArrayOutputStream
import it.pulse.workout.data.AppBackupStore
import android.content.Intent
import android.net.Uri
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import android.os.Build
import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.AccessibilityNew
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import it.pulse.workout.data.WorkoutCatalogRepository
import it.pulse.workout.data.WorkoutDay
import it.pulse.workout.data.WorkoutDraft
import it.pulse.workout.data.WorkoutPlan
import it.pulse.workout.data.WorkoutStore
import it.pulse.workout.data.WorkoutCalendarStore
import it.pulse.workout.data.WorkoutCalendarRules
import it.pulse.workout.data.CalendarEntry
import it.pulse.workout.data.CalendarEntryKind
import it.pulse.workout.data.WellnessStore
import it.pulse.workout.data.BodyProgressStore
import it.pulse.workout.data.PersonalSettings
import it.pulse.workout.data.PersonalSettingsStore
import it.pulse.workout.data.AppearancePreference
import it.pulse.workout.updates.GitHubReleaseRepository
import it.pulse.workout.updates.SafeApkUpdater
import it.pulse.workout.updates.AppUpdateStatus
import it.pulse.workout.notifications.RestAlertScheduler
import it.pulse.workout.reminders.ReminderScheduler
import it.pulse.workout.reminders.ReminderKind
import it.pulse.workout.data.AutomaticStepTracker
import it.pulse.workout.data.HealthConnectStepsRepository
import it.pulse.workout.ui.HistoryScreen
import it.pulse.workout.ui.MyselfScreen
import it.pulse.workout.ui.DashboardScreen
import it.pulse.workout.ui.WorkoutCalendarScreen
import it.pulse.workout.ui.PlanScreen
import it.pulse.workout.ui.PulseColors
import it.pulse.workout.ui.PulseTheme
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.SideEffect
import it.pulse.workout.ui.GuidedSessionScreen

class MainActivity : ComponentActivity() {
    private val openWorkoutFromNotification = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = android.graphics.Color.rgb(246, 248, 246)
        window.navigationBarColor = android.graphics.Color.WHITE
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        openWorkoutFromNotification.value =
            intent?.getBooleanExtra(WorkoutTimerService.EXTRA_OPEN_WORKOUT, false) == true
        setContent { PulseRoot(openWorkoutFromNotification) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.getBooleanExtra(WorkoutTimerService.EXTRA_OPEN_WORKOUT, false)) {
            openWorkoutFromNotification.value = true
        }
    }
}

/**
 * Settings own the root theme and recompose every tab immediately after Save or ZIP restore.
 * Personal preferences live in the existing backup-covered reminder namespace.
 */
@Composable
private fun PulseRoot(openWorkoutFromNotification: MutableState<Boolean>) {
    val context = LocalContext.current
    val preferences = remember(context) { PersonalSettingsStore(context) }
    val bodyStore = remember(context) { BodyProgressStore(context) }
    var settings by remember { mutableStateOf(preferences.read()) }
    var latestWeight by remember { mutableStateOf(bodyStore.latestWeightKg()) }
    val systemDark = isSystemInDarkTheme()
    val dark = when (settings.appearance) {
        AppearancePreference.DARK -> true
        AppearancePreference.LIGHT -> false
        AppearancePreference.SYSTEM -> systemDark
    }

    SideEffect {
        (context as? android.app.Activity)?.window?.let { window ->
            window.statusBarColor = if (dark) android.graphics.Color.rgb(13, 21, 16)
                else android.graphics.Color.rgb(246, 248, 246)
            window.navigationBarColor = if (dark) android.graphics.Color.rgb(23, 33, 26)
                else android.graphics.Color.WHITE
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = if (dark) 0 else
                View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        }
    }
    PulseTheme(darkTheme = dark) {
        PulseApp(openWorkoutFromNotification, settings, latestWeight,
            onSaveSettings = { next, newWeight ->
                runCatching {
                    val old = settings
                    val saved = preferences.save(next)
                    try {
                        if (newWeight != latestWeight) bodyStore.recordTodayWeight(newWeight)
                        ReminderScheduler.sync(context)
                    } catch (error: Exception) {
                        preferences.save(old)
                        ReminderScheduler.sync(context)
                        throw error
                    }
                    latestWeight = bodyStore.latestWeightKg()
                    settings = saved
                }.exceptionOrNull()?.message
            },
            onSettingsRestored = {
                settings = preferences.read()
                latestWeight = bodyStore.latestWeightKg()
            }
        )
    }
}

@Composable
private fun PulseApp(
    openWorkoutFromNotification: MutableState<Boolean>,
    settings: PersonalSettings,
    latestWeightKg: Double?,
    onSaveSettings: (PersonalSettings, Double?) -> String?,
    onSettingsRestored: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val store = remember { WorkoutStore(context.applicationContext) }
    val wellnessStore = remember { WellnessStore(context.applicationContext) }
    var wellness by remember { mutableStateOf(wellnessStore.read()) }
    val calendarStore = remember { WorkoutCalendarStore(context.applicationContext) }
    var calendarEntries by remember { mutableStateOf(calendarStore.read()) }
    val healthRepo = remember { HealthConnectStepsRepository(context.applicationContext) }
    val coroutineScope = rememberCoroutineScope()
    var healthConnected by remember { mutableStateOf(false) }
    var healthSyncing by remember { mutableStateOf(false) }
    var healthStatus by remember {
        mutableStateOf("Collega Health Connect per leggere i passi di Samsung Health")
    }

    suspend fun syncHealth() {
        if (healthSyncing) return
        healthSyncing = true
        try {
            when (healthRepo.sdkStatus()) {
                HealthConnectClient.SDK_UNAVAILABLE -> {
                    healthConnected = false
                    wellness = wellnessStore.clearHealthSteps()
                    healthStatus = "Health Connect non supportato su questo telefono"
                }
                HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> {
                    healthConnected = false
                    wellness = wellnessStore.clearHealthSteps()
                    healthStatus = "Installa o aggiorna Health Connect per continuare"
                }
                else -> {
                    if (!healthRepo.granted()) {
                        healthConnected = false
                        wellness = wellnessStore.clearHealthSteps()
                        healthStatus = "Collega Health Connect e autorizza la lettura dei passi"
                    } else {
                        healthConnected = true
                        healthStatus = "Sincronizzazione passi da Health Connect…"
                        val values = withContext(Dispatchers.IO) { healthRepo.recentSteps() }
                        wellness = wellnessStore.updateHealthSteps(values)
                        healthStatus = "Health Connect connesso · passi sincronizzati"
                    }
                }
            }
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            healthStatus = if (healthConnected)
                "Health Connect connesso · sincronizzazione non riuscita"
            else "Health Connect non disponibile: usa il contapassi locale"
        } finally {
            healthSyncing = false
        }
    }

    val healthPermissionLauncher = rememberLauncherForActivityResult(
        PermissionController.createRequestPermissionResultContract()
    ) { granted ->
        coroutineScope.launch {
            if (HealthConnectStepsRepository.READ_STEPS in granted) syncHealth()
            else {
                healthConnected = false
                wellness = wellnessStore.clearHealthSteps()
                healthStatus = "Permesso passi non concesso · usa il sensore o inseriscili manualmente"
            }
        }
    }

    var notificationUiTick by remember { mutableIntStateOf(0) }
    val lifecycleOwner = LocalLifecycleOwner.current
    var inForeground by remember { mutableStateOf(true) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    inForeground = true
                    notificationUiTick++
                    coroutineScope.launch { syncHealth() }
                }
                Lifecycle.Event.ON_PAUSE -> inForeground = false
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(Unit) { syncHealth() }
    LaunchedEffect(healthConnected, inForeground) {
        while (healthConnected && inForeground) {
            kotlinx.coroutines.delay(60_000L)
            syncHealth()
        }
    }

    var stepPermissionGranted by remember {
        mutableStateOf(Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACTIVITY_RECOGNITION) ==
                PackageManager.PERMISSION_GRANTED)
    }
    val tracker = remember(wellnessStore) {
        AutomaticStepTracker(context.applicationContext, wellnessStore) { refreshed ->
            wellness = refreshed
        }
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> stepPermissionGranted = granted }
    var waterRemindersEnabled by remember {
        mutableStateOf(ReminderScheduler.enabled(context, ReminderKind.WATER))
    }
    var moveRemindersEnabled by remember {
        mutableStateOf(ReminderScheduler.enabled(context, ReminderKind.MOVE))
    }
    LaunchedEffect(Unit) {
        ReminderScheduler.sync(context)
    }
    var notificationsAllowed by remember {
        mutableStateOf(Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED)
    }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        notificationsAllowed = granted
        notificationUiTick++
    }
    // L'utente può cambiare i permessi dalle Impostazioni mentre l'app è in pausa.
    LaunchedEffect(inForeground) {
        if (inForeground) {
            notificationsAllowed = Build.VERSION.SDK_INT < 33 ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
            notificationUiTick++
        }
    }

    // Una sola richiesta dopo l'aggiornamento, perché i promemoria sono già
    // attivi per default: i permessi possono sempre essere gestiti dall'utente.
    LaunchedEffect(Unit) {
        val prefs = context.getSharedPreferences("buttigogym_reminder_permission", 0)
        if (Build.VERSION.SDK_INT >= 33 && !notificationsAllowed &&
            !prefs.getBoolean("asked", false) &&
            (waterRemindersEnabled || moveRemindersEnabled)) {
            prefs.edit().putBoolean("asked", true).apply()
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    fun requestTimerNotifications() {
        notificationsAllowed = Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (Build.VERSION.SDK_INT >= 33 && !notificationsAllowed) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        notificationUiTick++
    }

    fun openNotificationSettings() {
        val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        }
        runCatching { context.startActivity(intent) }.onFailure {
            Toast.makeText(context, "Apri Impostazioni → App → ButtiGoGym → Notifiche",
                Toast.LENGTH_LONG).show()
        }
    }

    LaunchedEffect(Unit) {
        if (tracker.available && !stepPermissionGranted &&
            healthRepo.sdkStatus() != HealthConnectClient.SDK_AVAILABLE &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            permissionLauncher.launch(Manifest.permission.ACTIVITY_RECOGNITION)
        }
    }
    // Health Connect is authoritative when connected: the local sensor must stop.
    DisposableEffect(stepPermissionGranted, healthConnected, tracker) {
        if (stepPermissionGranted && !healthConnected) tracker.start()
        onDispose { tracker.stop() }
    }
    val stepStatus = when {
        !tracker.available -> "Sensore passi non disponibile · inserimento manuale"
        !stepPermissionGranted -> "Consenti Attività fisica per il contapassi"
        else -> "Passi automatici dal sensore Android"
    }
    val repository = remember { WorkoutCatalogRepository(context.applicationContext) }
    var catalog by remember { mutableStateOf(repository.initial()) }
    var customizedDayIds by remember { mutableStateOf(repository.customizedDayIds()) }
    var refreshState by remember { mutableStateOf("Scheda salvata sul telefono") }
    var refreshing by remember { mutableStateOf(false) }
    suspend fun fetchCatalog() {
        if (refreshing) return
        refreshing = true
        refreshState = "Aggiornamento in corso…"
        runCatching { withContext(Dispatchers.IO) { repository.refresh() } }
            .onSuccess {
                catalog = it
                customizedDayIds = repository.customizedDayIds()
                refreshState = "Sincronizzata da GitHub · v${it.revision}" +
                    if (customizedDayIds.isNotEmpty())
                        " · ${customizedDayIds.size} giorni personalizzati conservati"
                    else ""
            }
            .onFailure { refreshState = "Offline: utilizzo scheda salvata" }
        refreshing = false
    }

    LaunchedEffect(Unit) { fetchCatalog() }
    var draft by remember { mutableStateOf(store.loadDraft()) }
    var history by remember { mutableStateOf(store.history()) }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var calendarVisible by rememberSaveable { mutableStateOf(false) }
    var sessionVisible by rememberSaveable { mutableStateOf(draft != null) }
    var pendingNewWorkout by remember { mutableStateOf<WorkoutDay?>(null) }
    LaunchedEffect(openWorkoutFromNotification.value, draft) {
        if (openWorkoutFromNotification.value && draft != null) {
            sessionVisible = true
            openWorkoutFromNotification.value = false
        }
    }

    /**
     * Save calendar and shared rest marks together, restoring the original
     * calendar if a wellness preference write fails. Never alter workout history.
     */
    fun saveCalendar(next: List<CalendarEntry>, date: java.time.LocalDate?,
                     rest: Boolean?): String? = runCatching {
        val before = calendarEntries
        calendarEntries = calendarStore.save(next)
        try {
            if (date != null && rest != null) {
                wellness = wellnessStore.setRest(date, rest)
            }
        } catch (error: Exception) {
            calendarEntries = calendarStore.save(before)
            throw error
        }
    }.exceptionOrNull()?.message

    fun start(day: WorkoutDay) {
        if (draft != null && draft?.dayId != day.id) {
            pendingNewWorkout = day
            return
        }
        if (draft == null) {
            draft = store.newDraft(day)
            store.saveDraft(draft)
        }
        sessionVisible = true
        requestTimerNotifications()
    }

    // Il servizio mantiene la notifica cronometro e il timer riposo
    // anche quando l'utente esce dalla schermata o passa ad altre app.
    // Rileva anche servizi arrestati o canali disabilitati mentre l'utente era fuori dall'app.
    LaunchedEffect(draft?.startedAt, inForeground) {
        while (draft != null && inForeground) {
            notificationUiTick++
            kotlinx.coroutines.delay(1500L)
        }
    }
    val timerStatus = remember(notificationUiTick, draft) { WorkoutTimerService.state(context) }
    val timerNotificationProblem = remember(notificationUiTick, notificationsAllowed) {
        WorkoutTimerService.notificationProblem(context)
    }
    val notificationsVisible = timerNotificationProblem == null
    val waterReminderProblem = remember(notificationUiTick, notificationsAllowed) {
        ReminderScheduler.channelProblem(context, ReminderKind.WATER)
    }
    val moveReminderProblem = remember(notificationUiTick, notificationsAllowed) {
        ReminderScheduler.channelProblem(context, ReminderKind.MOVE)
    }

    LaunchedEffect(draft, notificationsAllowed, inForeground) {
        val current = draft
        // Il Worker prosegue il recupero anche se Android termina il servizio.
        // Non tenta di far partire un FGS mentre l'app è in background.
        RestAlertScheduler.sync(context, current)
        if (current == null) {
            WorkoutTimerService.stop(context)
        } else if (inForeground) {
            val day = current.daySnapshot ?: catalog.days.firstOrNull { it.id == current.dayId }
            if (day != null) {
                runCatching { WorkoutTimerService.sync(context, current, day) }
                    .onFailure { WorkoutTimerService.recordLaunchFailure(context, it) }
            }
        }
    }

    val currentDay = draft?.daySnapshot ?: catalog.days.firstOrNull { it.id == draft?.dayId }
    val today = java.time.LocalDate.now()
    val calendarCompleted = remember(history) { WorkoutCalendarRules.completedDates(history) }
    val plannedNext = calendarEntries.asSequence()
        .filter { it.kind == CalendarEntryKind.WORKOUT && !it.date.isBefore(today) }
        .filter { !wellness.restedOn(it.date) && it.date !in calendarCompleted }
        .sortedBy { it.date }
        .mapNotNull { entry -> catalog.days.firstOrNull { it.id == entry.workoutDayId } }
        .firstOrNull()
    val backupStore = remember { AppBackupStore(context.applicationContext) }
    var backupStatus by rememberSaveable { mutableStateOf("") }
    var backupBusy by remember { mutableStateOf(false) }
    var pendingBackup by remember { mutableStateOf<Uri?>(null) }
    var pendingBackupPreview by remember { mutableStateOf<AppBackupStore.Preview?>(null) }

    val exportBackup = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(AppBackupStore.MIME_TYPE)
    ) { uri ->
        if (uri != null) {
            backupBusy = true
            coroutineScope.launch {
                runCatching {
                    withContext(Dispatchers.IO) {
                        val exported = checkNotNull(context.contentResolver.openOutputStream(uri)) {
                            "Impossibile aprire il file di destinazione"
                        }.use { output -> backupStore.exportTo(output) }
                        // Verifica l'archivio effettivamente scritto, non solo lo stream originale.
                        val verified = checkNotNull(context.contentResolver.openInputStream(uri)) {
                            "Backup creato ma impossibile rileggere il file: verifica manualmente"
                        }.use { input -> backupStore.inspect(input) }
                        check(verified == exported) {
                            "Il backup scritto non corrisponde al contenuto atteso"
                        }
                        verified
                    }
                }.onSuccess { preview ->
                    backupStatus = "Backup ZIP verificato: ${preview.workouts} sessioni, " +
                        "${preview.photos} foto workout, ${preview.bodyPhotos} foto progresso, " +
                        "${preview.recaps} recap."
                }.onFailure { error ->
                    backupStatus = "Backup non salvato: ${error.message.orEmpty().take(140)}"
                }
                backupBusy = false
            }
        }
    }
    val importBackup = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            backupBusy = true
            coroutineScope.launch {
                runCatching {
                    withContext(Dispatchers.IO) {
                        checkNotNull(context.contentResolver.openInputStream(uri)) {
                            "Impossibile aprire il backup"
                        }.use { input -> backupStore.inspect(input) }
                    }
                }.onSuccess { preview ->
                    pendingBackup = uri
                    pendingBackupPreview = preview
                    backupStatus = ""
                }.onFailure { error ->
                    backupStatus = "Backup non valido: ${error.message.orEmpty().take(140)}"
                }
                backupBusy = false
            }
        }
    }

    // GitHub Releases è la sola sorgente di aggiornamenti installabili.
    // Non verifica automaticamente più di una volta ogni 24 ore.
    val releaseRepository = remember { GitHubReleaseRepository(context.applicationContext) }
    val secureUpdater = remember { SafeApkUpdater(context.applicationContext) }
    val installedVersion = remember { releaseRepository.installedVersion() }
    var updateStatus by remember { mutableStateOf(releaseRepository.cachedOrNotChecked()) }
    var updateChecking by remember { mutableStateOf(false) }
    var updateDownloading by remember { mutableStateOf(false) }
    var updateProgress by remember { mutableIntStateOf(0) }
    var downloadedUpdateApk by remember { mutableStateOf<java.io.File?>(null) }

    fun checkUpdates(force: Boolean) {
        if (updateChecking || updateDownloading) return
        updateChecking = true
        coroutineScope.launch {
            try {
                val found = withContext(Dispatchers.IO) { releaseRepository.check(force) }
                updateStatus = found
                downloadedUpdateApk = null
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                updateStatus = AppUpdateStatus.Error(
                    "Controllo non riuscito: ${error.message.orEmpty().take(110)}"
                )
            } finally {
                updateChecking = false
            }
        }
    }

    LaunchedEffect(Unit) { checkUpdates(force = false) }

    fun downloadUpdate() {
        val release = (updateStatus as? AppUpdateStatus.Available)?.release ?: return
        if (updateChecking || updateDownloading) return
        updateDownloading = true
        updateProgress = 0
        downloadedUpdateApk = null
        coroutineScope.launch {
            try {
                val file = secureUpdater.download(release) { percent -> updateProgress = percent }
                downloadedUpdateApk = file
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                updateStatus = AppUpdateStatus.Error(
                    "Download non riuscito: ${error.message.orEmpty().take(150)}"
                )
            } finally {
                updateDownloading = false
            }
        }
    }

    val installPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        val release = (updateStatus as? AppUpdateStatus.Available)?.release
        val file = downloadedUpdateApk
        if (secureUpdater.canRequestInstallation() && release != null && file != null) {
            runCatching {
                secureUpdater.verifyPackage(file, release)
                context.startActivity(secureUpdater.installationIntent(file))
            }.onFailure { error ->
                updateStatus = AppUpdateStatus.Error(
                    "Installer Android non disponibile: ${error.message.orEmpty().take(130)}"
                )
            }
        } else {
            Toast.makeText(context,
                "Per aggiornare, consenti l'installazione da ButtiGoGym nelle impostazioni Android.",
                Toast.LENGTH_LONG).show()
        }
    }

    fun installUpdate() {
        val release = (updateStatus as? AppUpdateStatus.Available)?.release ?: return
        val file = downloadedUpdateApk ?: return
        runCatching {
            // Ricontrollo al click: impedisce di installare file sovrascritti.
            secureUpdater.verifyPackage(file, release)
            if (secureUpdater.canRequestInstallation()) {
                context.startActivity(secureUpdater.installationIntent(file))
            } else {
                installPermissionLauncher.launch(secureUpdater.settingsIntent())
            }
        }.onFailure { error ->
            updateStatus = AppUpdateStatus.Error(
                "Impossibile aprire l'aggiornamento: ${error.message.orEmpty().take(120)}"
            )
        }
    }

    Scaffold(
        containerColor = PulseColors.Background,
        bottomBar = {
            if (!sessionVisible && !calendarVisible) {
                NavigationBar(containerColor = PulseColors.Surface, tonalElevation = 0.dp) {
                    listOf(
                        Triple("Oggi", Icons.Default.Home, 0),
                        Triple("Scheda", Icons.Default.FitnessCenter, 1),
                        Triple("Myself", Icons.Default.AccessibilityNew, 2),
                        Triple("Storico", Icons.Default.History, 3)
                    ).forEach { (label, icon, index) ->
                        NavigationBarItem(
                            selected = tab == index,
                            onClick = { tab = index },
                            modifier = Modifier.semantics {
                                contentDescription = "Sezione $label"
                            },
                            alwaysShowLabel = true,
                            icon = { Icon(icon, contentDescription = null) },
                            label = { Text(label) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = PulseColors.DarkInk,
                                selectedTextColor = PulseColors.Accent,
                                indicatorColor = PulseColors.Accent,
                                unselectedIconColor = PulseColors.Muted,
                                unselectedTextColor = PulseColors.Muted
                            )
                        )
                    }
                }
            }
        }
    ) { contentPadding ->
        Box(Modifier.fillMaxSize().padding(contentPadding)) {
            if (sessionVisible && draft != null && currentDay != null) {
                key(draft!!.startedAt) {
                    GuidedSessionScreen(
                        day = currentDay,
                        draft = draft!!,
                        history = history,
                        hapticFeedbackEnabled = settings.hapticFeedback,
                        onBack = { sessionVisible = false },
                        onUpdate = { updated ->
                            draft = updated
                            store.saveDraft(updated)
                        },
                        onFinish = {
                            draft?.let { active -> store.complete(active, currentDay) }
                            draft = null
                            history = store.history()
                            sessionVisible = false
                            tab = 3
                        },
                        onReset = {
                            val restarted = draft?.restarted(System.currentTimeMillis())
                            if (restarted != null) {
                                store.saveDraft(restarted)
                                draft = restarted
                            }
                        },
                        notificationsVisible = notificationsVisible,
                        notificationProblem = timerNotificationProblem,
                        timerStatus = timerStatus,
                        onEnableNotifications = ::requestTimerNotifications,
                        onNotificationSettings = ::openNotificationSettings,
                        onRetryTimer = {
                            val active = draft
                            if (active != null) {
                                runCatching { WorkoutTimerService.sync(context, active, currentDay) }
                                    .onFailure {
                                        WorkoutTimerService.recordLaunchFailure(context, it)
                                        Toast.makeText(context, "Timer non avviato: ${it.message}",
                                            Toast.LENGTH_LONG).show()
                                    }
                                notificationUiTick++
                            }
                        },
                        onTestNotification = {
                            val test = runCatching {
                                WorkoutTimerService.testNotification(context)
                            }.getOrDefault(false)
                            Toast.makeText(context,
                                if (test) "Notifica di prova inviata"
                                else "Notifiche bloccate: controlla i permessi",
                                Toast.LENGTH_SHORT).show()
                        }
                    )
                }
            } else if (calendarVisible) {
                WorkoutCalendarScreen(
                    days = catalog.days,
                    entries = calendarEntries,
                    history = history,
                    wellness = wellness,
                    onBack = { calendarVisible = false },
                    onSchedule = { date, workoutId ->
                        if (date in calendarCompleted) "La giornata contiene un allenamento completato"
                        else if (catalog.days.none { it.id == workoutId }) "Scheda non disponibile"
                        else saveCalendar(
                            WorkoutCalendarRules.place(calendarEntries,
                                CalendarEntry(date, CalendarEntryKind.WORKOUT, workoutId)),
                            date, false)
                    },
                    onRest = { date ->
                        if (date in calendarCompleted) "Non puoi segnare riposo su una seduta completata"
                        else saveCalendar(
                            WorkoutCalendarRules.clear(calendarEntries, date), date, true)
                    },
                    onSkip = { date ->
                        when {
                            date in calendarCompleted -> "La seduta è già completata"
                            date.isAfter(java.time.LocalDate.now()) ->
                                "Puoi segnare saltata solo una seduta di oggi o passata"
                            else -> saveCalendar(
                                WorkoutCalendarRules.place(calendarEntries,
                                    CalendarEntry(date, CalendarEntryKind.SKIPPED,
                                        calendarEntries.firstOrNull { it.date == date }?.workoutDayId)),
                                date, false)
                        }
                    },
                    onClear = { date -> saveCalendar(
                        WorkoutCalendarRules.clear(calendarEntries, date), date, false)
                    },
                    onMove = { from, to ->
                        runCatching {
                            WorkoutCalendarRules.move(calendarEntries, from, to,
                                destinationRest = wellness.restedOn(to),
                                sourceCompleted = from in calendarCompleted)
                        }.fold(
                            onSuccess = { revised -> saveCalendar(revised, null, null) },
                            onFailure = { it.message ?: "Impossibile spostare la seduta" }
                        )
                    },
                    onStart = { day ->
                        calendarVisible = false
                        start(day)
                    }
                )
            } else {
                Crossfade(targetState = tab, animationSpec = tween(it.pulse.workout.ui.BGMotion.duration(180)),
                    label = "Cambio schermata") { selectedTab ->
                when (selectedTab) {
                    0 -> DashboardScreen(
                        upcoming = plannedNext ?: WorkoutPlan.upcoming(catalog.days),
                        days = catalog.days,
                        active = draft,
                        history = history,
                        wellness = wellness,
                        personalSettings = settings,
                        stepStatus = stepStatus,
                        healthConnected = healthConnected,
                        healthSyncing = healthSyncing,
                        healthStatus = healthStatus,
                        onConnectHealth = {
                            when (healthRepo.sdkStatus()) {
                                HealthConnectClient.SDK_AVAILABLE ->
                                    healthPermissionLauncher.launch(setOf(HealthConnectStepsRepository.READ_STEPS))
                                HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> {
                                    val intent = Intent(Intent.ACTION_VIEW,
                                        Uri.parse("market://details?id=${HealthConnectStepsRepository.PROVIDER_PACKAGE}"))
                                    runCatching { context.startActivity(intent) }.onFailure {
                                        runCatching {
                                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(
                                                "https://play.google.com/store/apps/details?id=${HealthConnectStepsRepository.PROVIDER_PACKAGE}"
                                            )))
                                        }
                                    }
                                }
                                else -> healthStatus = "Il telefono non supporta Health Connect"
                            }
                        },
                        onRefreshHealth = { coroutineScope.launch { syncHealth() } },
                        onRequestStepAccess = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
                                permissionLauncher.launch(Manifest.permission.ACTIVITY_RECOGNITION)
                        },
                        onStart = ::start,
                        onSaveSteps = { steps -> wellness = wellnessStore.updateSteps(java.time.LocalDate.now(), steps) },
                        onToggleRest = {
                            val date = java.time.LocalDate.now()
                            val newRest = !wellness.restedOn(date)
                            val revised = if (newRest) WorkoutCalendarRules.clear(calendarEntries, date)
                                else calendarEntries
                            saveCalendar(revised, date, newRest)
                        },
                        onOpenPlan = { tab = 1 },
                        onOpenCalendar = { calendarVisible = true },
                        reminderWaterEnabled = waterRemindersEnabled,
                        reminderMoveEnabled = moveRemindersEnabled,
                        reminderWaterProblem = waterReminderProblem,
                        reminderMoveProblem = moveReminderProblem,
                        onTestWaterReminder = {
                            val ok = runCatching {
                                ReminderScheduler.test(context, ReminderKind.WATER)
                            }.getOrDefault(false)
                            Toast.makeText(context,
                                if (ok) "Notifica acqua inviata" else
                                    (ReminderScheduler.channelProblem(context, ReminderKind.WATER)
                                        ?: "Verifica notifica non riuscita"),
                                Toast.LENGTH_LONG).show()
                        },
                        onTestMoveReminder = {
                            val ok = runCatching {
                                ReminderScheduler.test(context, ReminderKind.MOVE)
                            }.getOrDefault(false)
                            Toast.makeText(context,
                                if (ok) "Notifica movimento inviata" else
                                    (ReminderScheduler.channelProblem(context, ReminderKind.MOVE)
                                        ?: "Verifica notifica non riuscita"),
                                Toast.LENGTH_LONG).show()
                        },
                        onOpenReminderNotificationSettings = ::openNotificationSettings,
                        remindersPermissionGranted = notificationsAllowed,
                        onToggleWaterReminder = { value ->
                            waterRemindersEnabled = value
                            ReminderScheduler.setEnabled(context, ReminderKind.WATER, value)
                            if (value) requestTimerNotifications()
                        },
                        onToggleMoveReminder = { value ->
                            moveRemindersEnabled = value
                            ReminderScheduler.setEnabled(context, ReminderKind.MOVE, value)
                            if (value) requestTimerNotifications()
                        },
                        onEnableReminderNotifications = ::requestTimerNotifications,
                        updateInstalledVersion = installedVersion,
                        updateStatus = updateStatus,
                        updateChecking = updateChecking,
                        updateDownloading = updateDownloading,
                        updateProgress = updateProgress,
                        updateVerified = downloadedUpdateApk != null,
                        onCheckUpdate = { checkUpdates(force = true) },
                        onDownloadUpdate = ::downloadUpdate,
                        onInstallUpdate = ::installUpdate,
                        backupStatus = backupStatus,
                        backupBusy = backupBusy,
                        onExportBackup = {
                            exportBackup.launch("ButtiGoGym-backup-${java.time.LocalDate.now()}.zip")
                        },
                        onImportBackup = {
                            importBackup.launch(arrayOf("*/*"))
                        }
                    )
                    1 -> PlanScreen(
                        activeDayId = draft?.dayId,
                        days = catalog.days,
                        version = catalog.revision,
                        refreshState = refreshState,
                        refreshing = refreshing,
                        guidelines = catalog.guidelines,
                        customizedDayIds = customizedDayIds,
                        onRefresh = { coroutineScope.launch { fetchCatalog() } },
                        onSaveDay = { dayId, exercises ->
                            runCatching {
                                catalog = repository.saveCustomExercises(catalog, dayId, exercises)
                                customizedDayIds = repository.customizedDayIds()
                            }.exceptionOrNull()?.message
                        },
                        onRestoreDay = { dayId ->
                            runCatching {
                                catalog = repository.resetCustomExercises(dayId)
                                customizedDayIds = repository.customizedDayIds()
                            }.exceptionOrNull()?.message
                        },
                        onStart = ::start
                    )
                    2 -> MyselfScreen(
                        history = history,
                        active = draft,
                        settings = settings,
                        latestWeightKg = latestWeightKg,
                        onSaveSettings = onSaveSettings
                    )
                    else -> HistoryScreen(
                        history = history,
                        days = catalog.days,
                        onPhotoSaved = { id, filename ->
                            if (store.attachPhoto(id, filename)) history = store.history()
                        }
                    )
                }
                }
            }
        }
    }

    pendingBackup?.let { uri ->
        val preview = pendingBackupPreview
        AlertDialog(
            onDismissRequest = {
                if (!backupBusy) { pendingBackup = null; pendingBackupPreview = null }
            },
            title = { Text("Ripristinare il backup?") },
            text = {
                Column {
                    if (preview != null) {
                        Text("${preview.workouts} allenamenti · ${preview.photos} foto WO · " +
                            "${preview.bodyPhotos} foto fisico · ${preview.recaps} recap", fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                        Spacer(Modifier.height(8.dp))
                        if (preview.schemaVersion == 1) {
                            Text("Vecchio backup JSON: non contiene foto o recap. " +
                                "Le immagini già presenti sul telefono resteranno invariate.")
                            Spacer(Modifier.height(8.dp))
                        }
                    }
                    Text("Sostituirà allenamenti, eventuale sessione in corso, " +
                        "passi e impostazioni salvate. Verifica di aver selezionato il file corretto. " +
                        "Il backup non è cifrato: conservalo privatamente.")
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !backupBusy,
                    onClick = {
                        backupBusy = true
                        coroutineScope.launch {
                            runCatching {
                                withContext(Dispatchers.IO) {
                                    checkNotNull(context.contentResolver.openInputStream(uri)) {
                                        "Impossibile leggere il backup selezionato"
                                    }.use { input -> backupStore.restoreFrom(input) }
                                }
                            }.onSuccess { restored ->
                                history = store.history()
                                draft = store.loadDraft()
                                wellness = wellnessStore.read()
                                calendarEntries = calendarStore.read()
                                calendarVisible = false
                                catalog = repository.initial()
                                customizedDayIds = repository.customizedDayIds()
                                waterRemindersEnabled =
                                    ReminderScheduler.enabled(context, ReminderKind.WATER)
                                moveRemindersEnabled =
                                    ReminderScheduler.enabled(context, ReminderKind.MOVE)
                                ReminderScheduler.sync(context)
                                onSettingsRestored()
                                sessionVisible = false
                                tab = 0
                                backupStatus = "Ripristino riuscito: ${restored.workouts} sessioni, " +
                                    "${restored.photos} foto WO, ${restored.bodyPhotos} foto fisico, " +
                                    "${restored.recaps} recap."
                            }.onFailure { error ->
                                backupStatus = "Ripristino non riuscito: " +
                                    error.message.orEmpty().take(140)
                            }
                            backupBusy = false
                            pendingBackup = null
                            pendingBackupPreview = null
                        }
                    }
                ) { Text(if (backupBusy) "Verifico…" else "Ripristina") }
            },
            dismissButton = {
                TextButton(enabled = !backupBusy, onClick = {
                    pendingBackup = null; pendingBackupPreview = null
                }) { Text("Annulla") }
            }
        )
    }

    pendingNewWorkout?.let { next ->
        AlertDialog(
            onDismissRequest = { pendingNewWorkout = null },
            title = { Text("Sostituire l'allenamento?") },
            text = { Text("La sessione in corso verrà eliminata. Lo storico già salvato resterà intatto.") },
            confirmButton = {
                TextButton(onClick = {
                    draft = store.newDraft(next)
                    store.saveDraft(draft)
                    sessionVisible = true
                    pendingNewWorkout = null
                }) { Text("Sostituisci") }
            },
            dismissButton = {
                TextButton(onClick = { pendingNewWorkout = null }) { Text("Annulla") }
            }
        )
    }
}
