package it.pulse.workout

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import it.pulse.workout.data.WorkoutCatalogRepository
import it.pulse.workout.data.WorkoutDay
import it.pulse.workout.data.WorkoutDraft
import it.pulse.workout.data.WorkoutPlan
import it.pulse.workout.data.WorkoutStore
import it.pulse.workout.ui.HistoryScreen
import it.pulse.workout.ui.HomeScreen
import it.pulse.workout.ui.PlanScreen
import it.pulse.workout.ui.PulseColors
import it.pulse.workout.ui.PulseTheme
import it.pulse.workout.ui.SessionScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = android.graphics.Color.rgb(12, 17, 16)
        window.navigationBarColor = android.graphics.Color.rgb(12, 17, 16)
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = 0
        setContent { PulseTheme { PulseApp() } }
    }
}

@Composable
private fun PulseApp() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val store = remember { WorkoutStore(context.applicationContext) }
    val repository = remember { WorkoutCatalogRepository(context.applicationContext) }
    var catalog by remember { mutableStateOf(repository.initial()) }
    var refreshState by remember { mutableStateOf("Scheda salvata sul telefono") }
    var refreshing by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    suspend fun fetchCatalog() {
        if (refreshing) return
        refreshing = true
        refreshState = "Aggiornamento in corso…"
        runCatching { withContext(Dispatchers.IO) { repository.refresh() } }
            .onSuccess { catalog = it; refreshState = "Sincronizzata da GitHub · v${it.revision}" }
            .onFailure { refreshState = "Offline: utilizzo scheda salvata" }
        refreshing = false
    }

    LaunchedEffect(Unit) { fetchCatalog() }
    var draft by remember { mutableStateOf(store.loadDraft()) }
    var history by remember { mutableStateOf(store.history()) }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var sessionVisible by rememberSaveable { mutableStateOf(draft != null) }
    var pendingNewWorkout by remember { mutableStateOf<WorkoutDay?>(null) }

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
    }

    val currentDay = draft?.daySnapshot ?: catalog.days.firstOrNull { it.id == draft?.dayId }

    Scaffold(
        containerColor = PulseColors.Background,
        bottomBar = {
            if (!sessionVisible) {
                NavigationBar(containerColor = PulseColors.Surface) {
                    listOf(
                        Triple("Oggi", Icons.Default.Home, 0),
                        Triple("Scheda", Icons.Default.FitnessCenter, 1),
                        Triple("Storico", Icons.Default.History, 2)
                    ).forEach { (label, icon, index) ->
                        NavigationBarItem(
                            selected = tab == index,
                            onClick = { tab = index },
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
                SessionScreen(
                    day = currentDay,
                    draft = draft!!,
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
                        tab = 2
                    }
                )
            } else {
                when (tab) {
                    0 -> HomeScreen(
                        upcoming = WorkoutPlan.upcoming(catalog.days),
                        days = catalog.days,
                        active = draft,
                        history = history,
                        onStart = ::start
                    )
                    1 -> PlanScreen(
                        activeDayId = draft?.dayId,
                        days = catalog.days,
                        version = catalog.revision,
                        refreshState = refreshState,
                        refreshing = refreshing,
                        guidelines = catalog.guidelines,
                        onRefresh = { coroutineScope.launch { fetchCatalog() } },
                        onStart = ::start
                    )
                    else -> HistoryScreen(history = history, days = catalog.days)
                }
            }
        }
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
