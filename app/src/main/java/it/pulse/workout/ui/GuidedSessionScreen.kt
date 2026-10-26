package it.pulse.workout.ui

import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import coil.request.ImageRequest
import it.pulse.workout.data.*
import kotlinx.coroutines.delay
import java.util.Locale

fun formatDuration(seconds: Long): String {
    val safe = seconds.coerceAtLeast(0)
    return if (safe >= 3600) "%d:%02d:%02d".format(Locale.ITALIAN, safe / 3600, safe % 3600 / 60, safe % 60)
    else "%02d:%02d".format(Locale.ITALIAN, safe / 60, safe % 60)
}

private val PendingInputsSaver = Saver<Map<String, QuickSetValues>, ArrayList<String>>(
    save = { map -> ArrayList(map.flatMap { (key, value) ->
        listOf(key, value.weight, value.reps, value.warmup.toString(), value.actualRir?.toString().orEmpty())
    }) },
    restore = { values -> values.chunked(5).associate {
        it[0] to QuickSetValues(it[1], it[2], it[3].toBoolean(), it[4].toIntOrNull())
    } }
)

@Composable
fun GuidedSessionScreen(
    day: WorkoutDay,
    draft: WorkoutDraft,
    history: List<SessionRecord>,
    hapticFeedbackEnabled: Boolean = true,
    onBack: () -> Unit,
    onUpdate: (WorkoutDraft) -> Unit,
    onFinish: () -> Unit,
    onReset: () -> Unit,
    notificationsVisible: Boolean,
    notificationProblem: String?,
    timerStatus: String,
    onEnableNotifications: () -> Unit,
    onNotificationSettings: () -> Unit,
    onTestNotification: () -> Unit,
    onRetryTimer: () -> Unit,
    onDiscard: () -> Unit = {}
) {
    val context = LocalContext.current
    val entryPrefs = remember(context) { context.getSharedPreferences("workout_entry_ui", android.content.Context.MODE_PRIVATE) }
    var guidedInput by rememberSaveable { mutableStateOf(entryPrefs.getBoolean("guided", false)) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(draft.startedAt) {
        while (true) { now = System.currentTimeMillis(); delay(1000) }
    }
    val exercise = day.exercises.getOrNull(draft.guidedExerciseIndex)
    val allDone = exercise == null
    val inRest = !allDone && draft.restUntilMillis > now
    val remaining = ((draft.restUntilMillis - now + 999) / 1000).coerceAtLeast(0)
    val elapsed = (now - draft.startedAt).coerceAtLeast(0) / 1000
    val recorded = remember(day, draft.entries) { draft.recordedSets(day) }
    val complete = recorded.count { !it.warmup && !it.exercise.optional }
    val advice = remember(exercise, day.id, history) {
        exercise?.let { WorkoutProgressionAdvisor.advise(it, day.id, history) }
    }
    var pending by rememberSaveable(draft.startedAt, stateSaver = PendingInputsSaver) {
        mutableStateOf<Map<String, QuickSetValues>>(emptyMap())
    }
    val inputKey = "${exercise?.id}:${draft.guidedWorkSet}"
    val input = pending[inputKey] ?: exercise?.let {
        val entry = draft.entry(it, draft.guidedWorkSet)
        QuickSetValues(entry.weight, entry.reps)
    } ?: QuickSetValues()
    val warmupCount = exercise?.let { draft.warmups(it).size } ?: 0
    var step by rememberSaveable(draft.startedAt, inputKey, warmupCount) { mutableStateOf(GuidedSetStep.WEIGHT) }
    var modal by rememberSaveable { mutableStateOf<String?>(null) }
    var finishDialog by rememberSaveable { mutableStateOf(false) }
    var resetDialog by rememberSaveable { mutableStateOf(false) }
    var discardDialog by rememberSaveable { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var justSaved by remember { mutableStateOf(false) }
    LaunchedEffect(justSaved) {
        if (justSaved) { delay(1350); justSaved = false }
    }
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = LocalFocusManager.current
    val haptics = LocalHapticFeedback.current
    fun hideKeyboard() { keyboard?.hide(); focus.clearFocus() }
    fun change(value: QuickSetValues) { pending = pending + (inputKey to value) }
    fun navigate(index: Int) { hideKeyboard(); modal = null; onUpdate(draft.navigateToExercise(day, index)) }
    fun back() {
        if (guidedInput && !inRest && !allDone && step != GuidedSetStep.WEIGHT) {
            hideKeyboard(); step = step.previous(input)
        } else onBack()
    }
    BackHandler(onBack = ::back)
    fun advance() {
        if (allDone) { finishDialog = true; return }
        if (inRest) {
            onUpdate(draft.copy(restUntilMillis = 0)); return
        }
        if (if (guidedInput) !step.accepts(input) else !input.valid) return
        hideKeyboard()
        if (guidedInput && step != GuidedSetStep.REVIEW) { step = step.next(input); return }
        val updated = runCatching { draft.recordSet(
            day, input.weight.replace(',', '.'), input.reps, input.warmup,
            System.currentTimeMillis(), if (input.warmup) null else input.actualRir
        ) }
        updated.onSuccess {
            if (hapticFeedbackEnabled) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            pending = pending - inputKey
            step = GuidedSetStep.WEIGHT
            error = null
            justSaved = true
            onUpdate(it)
        }.onFailure { error = it.message }
    }
    val line = when {
        allDone -> "Bel lavoro! Salviamo il tuo allenamento?"
        inRest -> "Respira. Ripartiamo quando sei pronto."
        !guidedInput -> "Peso, ripetizioni e via. Salva quando hai finito la serie."
        step == GuidedSetStep.WEIGHT -> "Una serie alla volta. Quanti kg usi?"
        step == GuidedSetStep.REPS -> if (exercise?.timeBased == true)
            "Quanto è durata la serie?" else "Quante ripetizioni hai completato?"
        step == GuidedSetStep.EFFORT -> "Quanto margine avevi? Puoi anche saltare."
        else -> "Ricevuto: ${input.weight} kg × ${input.reps}${if (exercise?.timeBased == true) " secondi" else " ripetizioni"}. Salviamo?"
    }
    val scroll = rememberScrollState()
    LaunchedEffect(inputKey, step, inRest) { scroll.scrollTo(0) }
    Column(Modifier.fillMaxSize().background(PulseColors.Background)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = ::back) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Indietro", tint = PulseColors.Foreground)
            }
            Column(Modifier.weight(1f)) {
                Text(day.name, style = MaterialTheme.typography.titleSmall, color = PulseColors.Foreground)
                Text("${complete}/${day.setCount} serie · ${formatDuration(elapsed)}",
                    style = MaterialTheme.typography.labelMedium, color = PulseColors.AccentDeep)
            }
            IconButton(onClick = { hideKeyboard(); modal = "options" }) {
                Icon(Icons.Default.MoreHoriz, "Opzioni allenamento", tint = PulseColors.Foreground)
            }
        }
        BGAnimatedProgress((complete.toFloat() / day.setCount.coerceAtLeast(1)).coerceIn(0f, 1f),
            modifier = Modifier.padding(horizontal = 18.dp))
        AnimatedVisibility(
            visible = justSaved,
            enter = fadeIn(tween(BGMotion.duration(180))),
            exit = fadeOut(tween(BGMotion.duration(180)))
        ) {
            BGSetSuccess()
        }
        Column(Modifier.weight(1f).verticalScroll(scroll).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)) {
            if (exercise != null) {
                Text(exercise.name, style = MaterialTheme.typography.headlineSmall, color = PulseColors.Foreground)
                Text("Serie ${draft.guidedWorkSet} di ${exercise.sets} · ${exercise.target} ${if (exercise.timeBased) "sec" else "rep"}",
                    color = PulseColors.Muted, style = MaterialTheme.typography.bodyMedium)
            }
            Text(line, color = PulseColors.Muted, style = MaterialTheme.typography.bodyMedium)
            when {
                allDone -> {
                    Text("$complete serie · ${formatDuration(elapsed)}", style = MaterialTheme.typography.titleMedium,
                        color = PulseColors.Foreground)
                    if (day.cooldown.isNotBlank()) Text(day.cooldown, color = PulseColors.Muted)
                }
                inRest -> {
                    BGBreathingTimer(remaining, exercise?.restSeconds ?: 90)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                        TextButton(onClick = { onUpdate(draft.copy(restUntilMillis =
                            (draft.restUntilMillis - 15_000).coerceAtLeast(now))) }) { Text("−15 s") }
                        TextButton(onClick = { onUpdate(draft.copy(restUntilMillis = draft.restUntilMillis + 15_000)) }) {
                            Text("+15 s")
                        }
                    }
                }
                !guidedInput && exercise != null -> {
                    val lastInSession = recorded.lastOrNull { it.exercise.id == exercise.id && !it.warmup }?.entry
                    val old = advice?.previous(draft.guidedWorkSet)
                    val previous = lastInSession ?: old?.let { SetEntry(
                        weight = formatKgForWorkout(it.weightKg), reps = it.repetitions.toString(), done = true) }
                    key(draft.startedAt, inputKey) {
                        BGQuickSetCard(input, exercise.timeBased, previous, onChange = ::change)
                    }
                }
                step == GuidedSetStep.WEIGHT || step == GuidedSetStep.REPS -> {
                    val weight = step == GuidedSetStep.WEIGHT
                    OutlinedTextField(
                        value = if (weight) input.weight else input.reps,
                        onValueChange = { next ->
                            if (weight && next.length <= 7 && next.all { it.isDigit() || it == ',' || it == '.' })
                                change(input.copy(weight = next))
                            else if (!weight && next.length <= 3 && next.all(Char::isDigit))
                                change(input.copy(reps = next))
                        },
                        label = { Text(if (weight) "Peso · kg" else if (exercise?.timeBased == true) "Durata · secondi" else "Ripetizioni") },
                        supportingText = if (weight) ({ Text("Corpo libero: 0 kg") }) else null,
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        textStyle = MaterialTheme.typography.headlineMedium.copy(color = PulseColors.Foreground),
                        keyboardOptions = KeyboardOptions(keyboardType = if (weight) KeyboardType.Decimal else KeyboardType.Number,
                            imeAction = ImeAction.Next),
                        keyboardActions = KeyboardActions(onNext = { advance() })
                    )
                    BGValueScrubber(
                        raw = if (weight) input.weight else input.reps,
                        onValueChange = { next ->
                            if (weight) change(input.copy(weight = next))
                            else change(input.copy(reps = next))
                        },
                        unit = if (weight) "kg" else if (exercise?.timeBased == true) "sec" else "rep",
                        min = if (weight) 0f else 1f,
                        max = if (weight) 250f else if (exercise?.timeBased == true) 300f else 50f,
                        step = if (weight) 0.5f else 1f,
                        decimal = weight
                    )
                    if (weight) {
                        val previous = advice?.previous(draft.guidedWorkSet)?.weightKg
                        val suggestion = advice?.suggestedWeight(draft.guidedWorkSet)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (previous != null) SuggestionChip(onClick = {
                                change(input.copy(weight = formatKgForWorkout(previous)))
                            }, label = { Text("Ultima: ${formatKgForWorkout(previous)} kg") })
                            if (suggestion != null && suggestion != previous) SuggestionChip(onClick = {
                                change(input.copy(weight = formatKgForWorkout(suggestion)))
                            }, label = { Text("${formatKgForWorkout(suggestion)} kg") })
                        }
                    }
                }
                step == GuidedSetStep.EFFORT -> {
                    ActualRirPicker(input.actualRir, onValueChange = { change(input.copy(actualRir = it)) })
                }
                else -> {
                    Text(if (input.warmup) "Riscaldamento" else "Serie ${draft.guidedWorkSet}",
                        style = MaterialTheme.typography.titleMedium, color = PulseColors.Foreground)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        TextButton(onClick = { step = GuidedSetStep.WEIGHT }) { Text("${input.weight} kg · Modifica") }
                        TextButton(onClick = { step = GuidedSetStep.REPS }) {
                            Text("${input.reps} ${if (exercise?.timeBased == true) "sec" else "rep"} · Modifica")
                        }
                    }
                    input.actualRir?.takeIf { !input.warmup }?.let { Text("RIR $it", color = PulseColors.Muted) }
                }
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
        Surface(color = PulseColors.Surface, modifier = Modifier.fillMaxWidth().imePadding()) {
            BGPrimaryButton(label = when {
                allDone -> "Salva allenamento"
                inRest -> "Sono pronto"
                !guidedInput || step == GuidedSetStep.REVIEW -> if (input.warmup) "Salva riscaldamento" else "Salva serie"
                step == GuidedSetStep.EFFORT && input.actualRir == null -> "Salta"
                else -> "Continua"
            }, onClick = ::advance, enabled = allDone || inRest || (if (guidedInput) step.accepts(input) else input.valid),
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp))
        }
    }

    if (modal != null) BGModal(when (modal) {
        "technique" -> "Tecnica"
        "exercises" -> "Esercizi"
        "substitute" -> "Sostituisci per oggi"
        "recorded" -> "Registro"
        "timer" -> "Timer e notifiche"
        else -> "Allenamento"
    }, onDismiss = { modal = null }) {
        when (modal) {
            "options" -> {
                TextButton(onClick = {
                    guidedInput = !guidedInput
                    entryPrefs.edit().putBoolean("guided", guidedInput).apply()
                    step = GuidedSetStep.WEIGHT
                    modal = null
                }) { Text(if (guidedInput) "Passa all’inserimento rapido" else "Passa all’inserimento passo passo") }
                if (exercise != null) {
                    TextButton(onClick = { modal = "technique" }) { Text("Tecnica e dimostrazione") }
                    TextButton(onClick = { modal = "exercises" }) { Text("Cambia esercizio") }
                    if (ExerciseSubstitutionCatalog.alternatives(exercise, day).isNotEmpty() &&
                        (1..exercise.sets).none { draft.entry(exercise, it).done } && draft.warmups(exercise).isEmpty())
                        TextButton(onClick = { modal = "substitute" }) { Text("Sostituisci attrezzo") }
                    if (exercise.optional) TextButton(onClick = { modal = null; onUpdate(draft.skipOptionalExercise(day)) }) {
                        Text("Salta esercizio facoltativo")
                    }
                    if (!inRest) Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Riscaldamento", Modifier.weight(1f), color = PulseColors.Foreground)
                        Switch(input.warmup, {
                            change(input.copy(warmup = it, actualRir = null))
                            step = GuidedSetStep.WEIGHT
                            modal = null
                        })
                    }
                }
                if (recorded.isNotEmpty()) TextButton(onClick = { modal = "recorded" }) { Text("Serie registrate") }
                if (recorded.any { it.entry.completedAt > 0 }) TextButton(onClick = {
                    modal = null; onUpdate(draft.undoLastRecordedSet(day))
                }) { Text("Annulla ultima serie") }
                TextButton(onClick = { modal = "timer" }) { Text("Timer e notifiche") }
                if (recorded.any { !it.warmup }) TextButton(onClick = { modal = null; finishDialog = true }) {
                    Text("Termina e salva")
                }
                TextButton(onClick = { modal = null; resetDialog = true }) { Text("Ricomincia") }
                TextButton(onClick = { modal = null; discardDialog = true }) {
                    Text("Scarta allenamento · non salvare", color = MaterialTheme.colorScheme.error)
                }
            }
            "technique" -> if (exercise != null) {
                Text("${exercise.sets} × ${exercise.target} · RIR ${exercise.rir} · ${exercise.restSeconds} s recupero",
                    color = PulseColors.Muted)
                if (exercise.note.isNotBlank()) Text(exercise.note, color = PulseColors.Foreground)
                if (exercise.alternative.isNotBlank()) Text("Alternativa: ${exercise.alternative}", color = PulseColors.Muted)
                if (exercise.gifUrl.isNotBlank()) ExerciseDemonstration(exercise)
                advice?.let { ProgressionAdvicePanel(it) }
            }
            "exercises" -> day.exercises.forEachIndexed { index, item ->
                val done = (1..item.sets).count { draft.entry(item, it).done }
                TextButton(onClick = { navigate(index) }, enabled = done < item.sets,
                    modifier = Modifier.fillMaxWidth()) {
                    Text(item.name, modifier = Modifier.weight(1f))
                    Text("$done/${item.sets}")
                }
            }
            "substitute" -> if (exercise != null) {
                Text("Alternative per gruppo muscolare · carichi non equivalenti", color = PulseColors.Muted)
                ExerciseSubstitutionCatalog.alternatives(exercise, day).forEach { option ->
                    TextButton(onClick = {
                        runCatching { draft.substituteCurrentExercise(day, option.exercise) }
                            .onSuccess { pending = pending - inputKey; modal = null; onUpdate(it) }
                            .onFailure { error = it.message }
                    }) { Text(option.exercise.name) }
                }
            }
            "recorded" -> RecordedSetsEditor(day, recorded,
                onCorrect = { target, weight, reps, rir ->
                    onUpdate(draft.correctRecordedSet(day, target.exercise.id, target.number, target.warmup, weight, reps, rir))
                }, onCancel = { target ->
                    onUpdate(draft.cancelRecordedSet(day, target.exercise.id, target.number, target.warmup))
                })
            "timer" -> {
                Text(if (notificationsVisible && timerStatus == "running") "Timer attivo" else "Controlla il timer",
                    style = MaterialTheme.typography.titleMedium)
                notificationProblem?.let { Text(it) }
                if (timerStatus.startsWith("error:")) Text(timerStatus.removePrefix("error:"))
                if (!notificationsVisible) TextButton(onClick = onEnableNotifications) { Text("Attiva notifiche") }
                TextButton(onClick = onNotificationSettings) { Text("Impostazioni notifiche") }
                TextButton(onClick = onRetryTimer) { Text("Riavvia timer") }
                TextButton(onClick = onTestNotification) { Text("Prova notifica") }
            }
        }
    }
    if (discardDialog) AlertDialog(
        onDismissRequest = { discardDialog = false },
        title = { Text("Scartare l'allenamento?") },
        text = { Text("Elimino questa seduta di prova, comprese le serie già registrate. Non verrà conteggiata nello storico, nel recupero o negli obiettivi. Gli allenamenti precedenti rimangono intatti.") },
        confirmButton = {
            TextButton(onClick = {
                discardDialog = false
                pending = emptyMap()
                onDiscard()
            }) { Text("Scarta definitivamente", color = MaterialTheme.colorScheme.error) }
        },
        dismissButton = {
            TextButton(onClick = { discardDialog = false }) { Text("Continua ad allenarti") }
        }
    )
    if (resetDialog) AlertDialog(onDismissRequest = { resetDialog = false },
        title = { Text("Ricominciare?") },
        text = { Text("Le serie di questa sessione saranno cancellate. Lo storico rimane salvato.") },
        confirmButton = { TextButton(onClick = { resetDialog = false; pending = emptyMap(); onReset() }) { Text("Ricomincia") } },
        dismissButton = { TextButton(onClick = { resetDialog = false }) { Text("Annulla") } })
    if (finishDialog) AlertDialog(onDismissRequest = { finishDialog = false },
        title = { Text("Salvare l'allenamento?") }, text = { Text("$complete serie · ${formatDuration(elapsed)}") },
        confirmButton = { TextButton(onClick = { finishDialog = false; onFinish() }) { Text("Salva") } },
        dismissButton = { TextButton(onClick = { finishDialog = false }) { Text("Continua allenamento") } })
}

@Composable
private fun ExerciseDemonstration(exercise: Exercise) {
    val context = LocalContext.current
    val loader = remember(context) { ImageLoader.Builder(context).components {
        if (Build.VERSION.SDK_INT >= 28) add(ImageDecoderDecoder.Factory()) else add(GifDecoder.Factory())
    }.build() }
    AsyncImage(model = ImageRequest.Builder(context).data(exercise.gifUrl).build(),
        contentDescription = "Esecuzione ${exercise.name}", imageLoader = loader,
        contentScale = ContentScale.Fit, modifier = Modifier.fillMaxWidth().height(220.dp))
}
