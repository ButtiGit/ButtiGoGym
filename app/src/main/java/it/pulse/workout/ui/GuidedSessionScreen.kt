package it.pulse.workout.ui

import android.os.Build
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import coil.request.ImageRequest
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import it.pulse.workout.data.*
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun formatDuration(seconds: Long): String {
    val safe = seconds.coerceAtLeast(0)
    val hours = safe / 3600
    val minutes = (safe % 3600) / 60
    val remainder = safe % 60
    return if (hours > 0) "%d:%02d:%02d".format(Locale.ITALIAN, hours, minutes, remainder)
    else "%02d:%02d".format(Locale.ITALIAN, minutes, remainder)
}

@Composable
fun GuidedSessionScreen(
    day: WorkoutDay,
    draft: WorkoutDraft,
    history: List<SessionRecord>,
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
    onRetryTimer: () -> Unit
) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(draft.startedAt) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1000L)
        }
    }
    val finishedSets = day.exercises.sumOf { ex ->
        (1..ex.sets).count { draft.entry(ex, it).done }
    }
    val optionalCompleted = day.exercises.filter { it.optional }.sumOf { ex ->
        (1..ex.sets).count { draft.entry(ex, it).done }
    }
    val recordedSets = remember(day, draft.entries) { draft.recordedSets(day) }
    val canUndo = recordedSets.any { it.entry.completedAt > 0L }
    val mainCompleted = finishedSets - optionalCompleted
    val progressTotal = day.setCount + optionalCompleted
    val allDone = draft.guidedExerciseIndex >= day.exercises.size
    val inRest = !allDone && draft.restUntilMillis > now
    val remainingSeconds = ((draft.restUntilMillis - now + 999L) / 1000L).coerceAtLeast(0)
    val elapsed = ((now - draft.startedAt).coerceAtLeast(0L) / 1000L)
    val exercise = day.exercises.getOrNull(draft.guidedExerciseIndex)
    val progression = remember(exercise, day.id, history) {
        exercise?.let { WorkoutProgressionAdvisor.advise(it, day.id, history) }
    }
    var finishDialog by remember { mutableStateOf(false) }
    var resetDialog by remember { mutableStateOf(false) }
    var exercisePickerVisible by remember { mutableStateOf(false) }
    val scrollState = rememberLazyListState()
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val haptics = LocalHapticFeedback.current
    val previousExerciseIndex = draft.adjacentPendingExercise(day, -1)
    val nextExerciseIndex = draft.adjacentPendingExercise(day, 1)
    var quickInput by remember(exercise?.id, draft.guidedWorkSet, draft.startedAt) {
        mutableStateOf(
            exercise?.let { current ->
                draft.entry(current, draft.guidedWorkSet).let { preset ->
                    QuickSetValues(weight = preset.weight, reps = preset.reps)
                }
            } ?: QuickSetValues()
        )
    }
    fun navigate(index: Int) {
        keyboard?.hide()
        focusManager.clearFocus()
        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        onUpdate(draft.navigateToExercise(day, index))
    }
    fun saveQuickSet() {
        if (inRest || allDone || exercise == null || !quickInput.valid) return
        keyboard?.hide()
        focusManager.clearFocus()
        val updated = draft.recordSet(
            day = day, weight = quickInput.weight.replace(',', '.'),
            reps = quickInput.reps, warmup = quickInput.warmup,
            at = System.currentTimeMillis(),
            rir = if (quickInput.warmup) null else quickInput.actualRir
        )
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        onUpdate(updated)
    }
    LaunchedEffect(draft.guidedExerciseIndex, draft.guidedWorkSet) {
        scrollState.animateScrollToItem(0)
    }

    Column(Modifier.fillMaxSize().background(PulseColors.Background)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 13.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Torna alla dashboard", tint = PulseColors.Foreground)
            }
            Column(Modifier.weight(1f)) {
                Text("IN ALLENAMENTO", color = PulseColors.Accent, fontSize = 10.sp,
                    fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
                Text(day.name, color = PulseColors.Foreground, fontWeight = FontWeight.Bold,
                    fontSize = 16.sp, maxLines = 1)
            }
            TextButton(onClick = { resetDialog = true }) {
                Icon(Icons.Default.RestartAlt, contentDescription = null,
                    tint = PulseColors.Accent, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("Reset", fontSize = 12.sp, color = PulseColors.Accent)
            }
            Surface(color = PulseColors.SoftGreen, shape = RoundedCornerShape(14.dp)) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Timer, null, tint = PulseColors.Accent,
                        modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(formatDuration(elapsed), fontWeight = FontWeight.Bold,
                        color = PulseColors.Accent, fontSize = 16.sp)
                }
            }
        }
        Column(Modifier.padding(horizontal = 20.dp)) {
            LinearProgressIndicator(
                progress = { (finishedSets.toFloat() / progressTotal.coerceAtLeast(1)).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().height(7.dp).clip(CircleShape),
                color = PulseColors.Accent, trackColor = PulseColors.SoftGreen
            )
            Spacer(Modifier.height(7.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("$mainCompleted / ${day.setCount} serie" +
                        (if (optionalCompleted > 0) " · +$optionalCompleted extra" else ""),
                    color = PulseColors.Muted, fontSize = 11.sp,
                    modifier = Modifier.weight(1f))
                if (canUndo) {
                    TextButton(
                        onClick = { onUpdate(draft.undoLastRecordedSet(day)) },
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                    ) {
                        Icon(Icons.Default.Undo, contentDescription = null,
                            modifier = Modifier.size(17.dp), tint = PulseColors.Accent)
                        Spacer(Modifier.width(4.dp))
                        Text("Annulla ultima", color = PulseColors.Accent,
                            fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        LazyColumn(
            state = scrollState,
            modifier = Modifier.fillMaxWidth().weight(1f),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Surface(
                    color = if (notificationsVisible && timerStatus == "running")
                        PulseColors.SoftGreen else PulseColors.Raised,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(horizontal = 12.dp, vertical = 7.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                if (notificationsVisible) Icons.Default.NotificationsActive
                                else Icons.Default.NotificationsOff,
                                contentDescription = null,
                                tint = PulseColors.Accent,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                when {
                                    !notificationsVisible -> "Notifiche non attive"
                                    timerStatus.startsWith("error:") -> "Errore timer in background"
                                    timerStatus == "running" -> "Timer attivo nelle notifiche"
                                    else -> "Avvio timer in background…"
                                },
                                color = PulseColors.Foreground,
                                fontWeight = FontWeight.SemiBold, fontSize = 12.sp
                            )
                        }
                        if (!notificationsVisible || timerStatus.startsWith("error:") ||
                            timerStatus != "running") {
                            Spacer(Modifier.height(6.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                TextButton(onClick = onEnableNotifications) {
                                    Text("Abilita", color = PulseColors.Accent, fontSize = 11.sp)
                                }
                                TextButton(onClick = onNotificationSettings) {
                                    Text("Impostazioni", color = PulseColors.Accent, fontSize = 11.sp)
                                }
                                TextButton(onClick = onRetryTimer) {
                                    Text("Riprova", color = PulseColors.Accent, fontSize = 11.sp)
                                }
                            }
                            if (notificationProblem != null) {
                                Text(notificationProblem,
                                    color = PulseColors.Muted, fontSize = 11.sp)
                            }
                            if (timerStatus.startsWith("error:")) {
                                Text(timerStatus.removePrefix("error:"),
                                    color = PulseColors.Muted, fontSize = 10.sp)
                            }
                        } else {
                            TextButton(onClick = onTestNotification) {
                                Text("Invia notifica di prova",
                                    color = PulseColors.Accent, fontSize = 11.sp)
                            }
                        }
                    }
                }
            }
            if (allDone) {
                item {
                    Panel {
                        Box(Modifier.size(56.dp).clip(CircleShape)
                            .background(PulseColors.SoftGreen), contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Check, null, tint = PulseColors.Accent,
                                modifier = Modifier.size(30.dp))
                        }
                        Spacer(Modifier.height(16.dp))
                        Text("Allenamento completato!", fontSize = 26.sp,
                            fontWeight = FontWeight.ExtraBold, color = PulseColors.Foreground)
                        Spacer(Modifier.height(8.dp))
                        Text("Hai registrato $finishedSets serie allenanti in ${formatDuration(elapsed)}. Salva la sessione per aggiungerla allo storico.",
                            color = PulseColors.Muted, fontSize = 14.sp)
                        if (day.cooldown.isNotBlank()) {
                            Spacer(Modifier.height(14.dp))
                            Text("CAMMINATA FINALE", fontWeight = FontWeight.Bold,
                                color = PulseColors.Accent, fontSize = 11.sp)
                            Text(day.cooldown, color = PulseColors.Foreground, fontSize = 13.sp)
                        }
                        Spacer(Modifier.height(22.dp))
                        Text("Puoi ancora correggere le serie registrate qui sotto.",
                            color = PulseColors.Muted, fontSize = 12.sp)
                    }
                }
            } else if (exercise != null) {
                item(key = "exercise-hero-${exercise.id}") {
                    var showInstructions by remember(exercise.id) { mutableStateOf(false) }
                    var showGif by remember(exercise.id) { mutableStateOf(false) }
                    Surface(
                        shape = RoundedCornerShape(28.dp),
                        color = PulseColors.AccentDeep,
                        modifier = Modifier.fillMaxWidth().animateContentSize(animationSpec = tween(270))
                    ) {
                        Column(
                            Modifier.background(Brush.linearGradient(
                                listOf(PulseColors.AccentDeep, PulseColors.Accent, Color(0xFF267D36))
                            )).padding(21.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("ESERCIZIO ${draft.guidedExerciseIndex + 1} / ${day.exercises.size}",
                                    color = Color.White.copy(alpha = .78f), fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold, letterSpacing = .8.sp)
                                Spacer(Modifier.weight(1f))
                                Surface(color = Color.White.copy(alpha = .17f),
                                    shape = RoundedCornerShape(18.dp)) {
                                    Text(if (exercise.optional) "EXTRA" else exercise.muscle,
                                        Modifier.padding(horizontal = 11.dp, vertical = 6.dp),
                                        color = Color.White, fontWeight = FontWeight.Bold,
                                        fontSize = 10.sp, maxLines = 1)
                                }
                            }
                            Spacer(Modifier.height(17.dp))
                            Text(exercise.name, color = Color.White,
                                fontSize = 29.sp, fontWeight = FontWeight.Black,
                                lineHeight = 34.sp)
                            Spacer(Modifier.height(15.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                ExerciseHeroChip("${exercise.sets} × ${exercise.target}" +
                                    (if (exercise.timeBased) " sec" else " rep"))
                                ExerciseHeroChip("RIR ${exercise.rir}")
                                ExerciseHeroChip("${exercise.restSeconds}s pausa")
                            }
                            Spacer(Modifier.height(19.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                                (1..exercise.sets).forEach { number ->
                                    val complete = draft.entry(exercise, number).done
                                    val current = !complete && number == draft.guidedWorkSet
                                    Box(
                                        Modifier.size(35.dp).clip(RoundedCornerShape(12.dp))
                                            .background(
                                                if (complete) Color.White else
                                                    if (current) Color.White.copy(alpha = .30f)
                                                    else Color.White.copy(alpha = .11f)
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (complete) Icon(Icons.Default.Check,
                                            contentDescription = "Serie $number completata",
                                            tint = PulseColors.Accent, modifier = Modifier.size(20.dp))
                                        else Text(number.toString(), color = Color.White,
                                            fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                                Spacer(Modifier.weight(1f))
                                Text("${(1..exercise.sets).count { draft.entry(exercise, it).done }}/${exercise.sets}",
                                    color = Color.White.copy(alpha = .85f), fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold)
                            }
                            if (exercise.note.isNotBlank() || exercise.alternative.isNotBlank()) {
                                Spacer(Modifier.height(14.dp))
                                TextButton(onClick = { showInstructions = !showInstructions }) {
                                    Icon(Icons.Default.Info, contentDescription = null,
                                        tint = Color.White, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(7.dp))
                                    Text(if (showInstructions) "Nascondi tecnica" else "Tecnica e alternative",
                                        color = Color.White, fontSize = 12.sp)
                                }
                                if (showInstructions) {
                                    if (exercise.note.isNotBlank())
                                        Text(exercise.note, color = Color.White.copy(alpha = .95f),
                                            fontSize = 12.sp)
                                    if (exercise.alternative.isNotBlank()) {
                                        Spacer(Modifier.height(7.dp))
                                        Text("Alternativa: ${exercise.alternative}",
                                            color = Color.White.copy(alpha = .8f), fontSize = 11.sp)
                                    }
                                }
                            }
                            if (exercise.gifUrl.isNotBlank()) {
                                TextButton(onClick = { showGif = !showGif }) {
                                    Icon(Icons.Default.PlayCircle, contentDescription = null,
                                        tint = Color.White, modifier = Modifier.size(17.dp))
                                    Spacer(Modifier.width(7.dp))
                                    Text(if (showGif) "Chiudi dimostrazione" else "Guarda il movimento",
                                        color = Color.White, fontSize = 12.sp)
                                }
                            }
                            if (exercise.optional) {
                                TextButton(onClick = { onUpdate(draft.skipOptionalExercise(day)) }) {
                                    Icon(Icons.Default.SkipNext, contentDescription = null,
                                        tint = Color.White, modifier = Modifier.size(18.dp))
                                    Text("Salta il richiamo", color = Color.White, fontSize = 12.sp)
                                }
                            }
                        }
                    }
                    if (showGif && exercise.gifUrl.isNotBlank()) {
                        Spacer(Modifier.height(9.dp))
                        val context = LocalContext.current
                        val loader = remember(context) {
                            ImageLoader.Builder(context).components {
                                if (Build.VERSION.SDK_INT >= 28)
                                    add(ImageDecoderDecoder.Factory())
                                else add(GifDecoder.Factory())
                            }.build()
                        }
                        AsyncImage(
                            model = ImageRequest.Builder(context).data(exercise.gifUrl).build(),
                            contentDescription = "Esecuzione ${exercise.name}",
                            imageLoader = loader, contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxWidth().height(206.dp)
                                .clip(RoundedCornerShape(22.dp)).background(PulseColors.Surface)
                        )
                    }
                }

                if (progression != null) {
                    item(key = "progression-${exercise.id}") {
                        ProgressionAdvicePanel(progression)
                    }
                }

                if (inRest) {
                    item {
                        Surface(shape = RoundedCornerShape(26.dp), color = PulseColors.SoftGreen,
                            modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("RECUPERO", color = PulseColors.Accent,
                                    fontSize = 12.sp, fontWeight = FontWeight.ExtraBold,
                                    letterSpacing = 1.2.sp)
                                Spacer(Modifier.height(8.dp))
                                Text(formatDuration(remainingSeconds), fontSize = 68.sp,
                                    fontWeight = FontWeight.Black, color = PulseColors.Accent,
                                    letterSpacing = (-2).sp)
                                Spacer(Modifier.height(10.dp))
                                val ratio by animateFloatAsState(
                                    targetValue = (remainingSeconds.toFloat() /
                                        exercise.restSeconds.coerceAtLeast(1)).coerceIn(0f, 1f),
                                    animationSpec = tween(650), label = "Recupero rimanente"
                                )
                                LinearProgressIndicator(progress = { ratio },
                                    modifier = Modifier.fillMaxWidth().height(7.dp).clip(CircleShape),
                                    color = PulseColors.Accent, trackColor = Color.White)
                                Spacer(Modifier.height(16.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                                    OutlinedButton(
                                        onClick = { onUpdate(draft.copy(restUntilMillis =
                                            (draft.restUntilMillis - 15_000L).coerceAtLeast(now))) },
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(14.dp),
                                        border = BorderStroke(1.dp, PulseColors.Border)
                                    ) { Text("−15 s", color = PulseColors.Accent) }
                                    OutlinedButton(
                                        onClick = { onUpdate(draft.copy(restUntilMillis =
                                            draft.restUntilMillis + 15_000L)) },
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(14.dp),
                                        border = BorderStroke(1.dp, PulseColors.Border)
                                    ) { Text("+15 s", color = PulseColors.Accent) }
                                }
                                Spacer(Modifier.height(8.dp))
                                Text("Quando sei pronto, usa il pulsante in basso.",
                                    color = PulseColors.Muted, fontSize = 11.sp)
                            }
                        }
                    }
                } else {
                    item(key = "series-${exercise.id}-${draft.guidedWorkSet}-${draft.entries.count { it.key.startsWith("warmup:${exercise.id}:") }}") {
                        GuidedSetForm(
                            exercise = exercise,
                            draft = draft,
                            advice = progression,
                            input = quickInput,
                            onInputChange = { quickInput = it }
                        )
                    }
                }

            }
            if (recordedSets.isNotEmpty()) {
                item(key = "session-recorded-series") {
                    RecordedSetsEditor(
                        day = day,
                        recorded = recordedSets,
                        onCorrect = { target, weight, reps, actualRir ->
                            onUpdate(draft.correctRecordedSet(
                                day, target.exercise.id, target.number, target.warmup,
                                weight, reps, actualRir
                            ))
                        },
                        onCancel = { target ->
                            onUpdate(draft.cancelRecordedSet(
                                day, target.exercise.id, target.number, target.warmup
                            ))
                        }
                    )
                }
            }
            item {
                if (day.cooldown.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text("Fine allenamento: ${day.cooldown}",
                        color = PulseColors.Muted, fontSize = 11.sp)
                }
                if (!allDone && finishedSets > 0) {
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = { finishDialog = true }) {
                        Text("Termina in anticipo e salva", color = PulseColors.Accent)
                    }
                }
            }
        }

        // Azione principale sempre in fondo, anche quando i dettagli scorrono.
        Surface(
            color = PulseColors.Surface,
            shadowElevation = 8.dp,
            border = BorderStroke(1.dp, PulseColors.Border),
            modifier = Modifier.fillMaxWidth().imePadding()
        ) {
            Column(Modifier.fillMaxWidth().padding(
                start = 16.dp, end = 16.dp, top = 10.dp, bottom = 12.dp
            )) {
                if (!allDone) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = { previousExerciseIndex?.let(::navigate) },
                            enabled = previousExerciseIndex != null,
                            shape = RoundedCornerShape(13.dp),
                            modifier = Modifier.size(46.dp),
                            contentPadding = PaddingValues(0.dp),
                            border = BorderStroke(1.dp, PulseColors.Border)
                        ) {
                            Icon(Icons.Default.ChevronLeft, "Esercizio precedente",
                                modifier = Modifier.size(24.dp))
                        }
                        OutlinedButton(
                            onClick = { keyboard?.hide(); focusManager.clearFocus(); exercisePickerVisible = true },
                            modifier = Modifier.weight(1f).height(46.dp),
                            shape = RoundedCornerShape(13.dp),
                            border = BorderStroke(1.dp, PulseColors.Border)
                        ) {
                            Icon(Icons.Default.List, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(7.dp))
                            Text("Scegli esercizio", fontWeight = FontWeight.SemiBold,
                                fontSize = 12.sp, maxLines = 1)
                        }
                        OutlinedButton(
                            onClick = { nextExerciseIndex?.let(::navigate) },
                            enabled = nextExerciseIndex != null,
                            shape = RoundedCornerShape(13.dp),
                            modifier = Modifier.size(46.dp),
                            contentPadding = PaddingValues(0.dp),
                            border = BorderStroke(1.dp, PulseColors.Border)
                        ) {
                            Icon(Icons.Default.ChevronRight, "Esercizio successivo",
                                modifier = Modifier.size(24.dp))
                        }
                    }
                    Spacer(Modifier.height(9.dp))
                }
                SolidButton(
                    when {
                        allDone -> "Salva allenamento"
                        inRest -> "Pronto · termina recupero"
                        quickInput.warmup -> "Salva riscaldamento"
                        else -> "Completa serie ${draft.guidedWorkSet}"
                    },
                    enabled = allDone || inRest || quickInput.valid,
                    onClick = {
                        when {
                            allDone -> finishDialog = true
                            inRest -> {
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onUpdate(draft.copy(restUntilMillis = 0L))
                            }
                            else -> saveQuickSet()
                        }
                    }
                )
            }
        }
    }

    if (exercisePickerVisible) {
        AlertDialog(
            onDismissRequest = { exercisePickerVisible = false },
            title = { Text("Vai a un esercizio") },
            text = {
                Column(
                    Modifier.heightIn(max = 430.dp)
                        .verticalScroll(androidx.compose.foundation.rememberScrollState())
                ) {
                    day.exercises.forEachIndexed { index, candidate ->
                        val finished = (1..candidate.sets).all { draft.entry(candidate, it).done }
                        TextButton(
                            onClick = {
                                exercisePickerVisible = false
                                navigate(index)
                            },
                            enabled = !finished,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("${index + 1}. ${candidate.name}",
                                color = if (finished) PulseColors.Muted else PulseColors.Foreground,
                                modifier = Modifier.weight(1f), maxLines = 2)
                            Text(if (finished) "Fatto" else
                                "${(1..candidate.sets).count { draft.entry(candidate, it).done }}/${candidate.sets}",
                                color = PulseColors.Accent, fontSize = 12.sp)
                        }
                    }
                    Text("Per correggere un esercizio completato, usa Serie registrate.",
                        color = PulseColors.Muted, fontSize = 11.sp)
                }
            },
            confirmButton = {
                TextButton(onClick = { exercisePickerVisible = false }) {
                    Text("Chiudi", color = PulseColors.Accent)
                }
            }
        )
    }

    if (resetDialog) {
        AlertDialog(
            onDismissRequest = { resetDialog = false },
            title = { Text("Ricominciare da zero?") },
            text = { Text("Cancellerà tutte le serie di questa sessione e riavvierà cronometro e recupero. Gli allenamenti già salvati nello storico non cambiano.") },
            confirmButton = {
                Button(
                    onClick = { resetDialog = false; onReset() },
                    colors = ButtonDefaults.buttonColors(containerColor = PulseColors.Accent)
                ) { Text("Reset allenamento", color = Color.White) }
            },
            dismissButton = {
                TextButton(onClick = { resetDialog = false }) {
                    Text("Annulla", color = PulseColors.Accent)
                }
            }
        )
    }

    if (finishDialog) {
        AlertDialog(
            onDismissRequest = { finishDialog = false },
            title = { Text("Salvare la sessione?") },
            text = { Text("$finishedSets serie allenanti · ${formatDuration(elapsed)} trascorsi. Le serie di riscaldamento saranno conservate nello storico.") },
            confirmButton = {
                Button(onClick = { finishDialog = false; onFinish() },
                    colors = ButtonDefaults.buttonColors(containerColor = PulseColors.Accent)) {
                    Text("Salva", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { finishDialog = false }) { Text("Continua") }
            }
        )
    }
}

@Composable
private fun ExerciseHeroChip(label: String) {
    Surface(color = Color.White.copy(alpha = .16f), shape = RoundedCornerShape(9.dp)) {
        Text(label, Modifier.padding(horizontal = 8.dp, vertical = 7.dp),
            color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold,
            maxLines = 1)
    }
}

@Composable
private fun GuidedSetForm(
    exercise: Exercise,
    draft: WorkoutDraft,
    advice: ExerciseProgression?,
    onSave: (String, String, Boolean, Int?) -> Unit
) {
    val preset = draft.entry(exercise, draft.guidedWorkSet)
    var weight by remember(exercise.id, draft.guidedWorkSet, draft.startedAt) {
        mutableStateOf(preset.weight)
    }
    var reps by remember(exercise.id, draft.guidedWorkSet, draft.startedAt) {
        mutableStateOf(preset.reps)
    }
    var warmup by remember(exercise.id, draft.guidedWorkSet, draft.startedAt) {
        mutableStateOf(false)
    }
    var actualRir by remember(exercise.id, draft.guidedWorkSet, draft.startedAt) {
        mutableStateOf<Int?>(null)
    }
    val previousSet = advice?.previous(draft.guidedWorkSet)
    val suggestedKg = advice?.suggestedWeight(draft.guidedWorkSet)
    val numericWeight = weight.replace(',', '.').toDoubleOrNull()
    val valid = numericWeight != null && numericWeight in 0.0..10000.0 &&
        (reps.toIntOrNull() ?: 0) in 1..999

    fun adjustWeight(amount: Double) {
        val next = ((numericWeight ?: 0.0) + amount).coerceAtLeast(0.0)
        weight = String.format(Locale.US, "%.1f", next)
            .trimEnd('0').trimEnd('.')
    }
    fun adjustReps(amount: Int) {
        val next = ((reps.toIntOrNull() ?: 0) + amount).coerceIn(0, 999)
        reps = next.toString()
    }

    Surface(
        shape = RoundedCornerShape(26.dp),
        color = PulseColors.Surface,
        border = BorderStroke(1.dp, PulseColors.Border),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(19.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(46.dp).clip(RoundedCornerShape(15.dp))
                    .background(PulseColors.SoftGreen),
                    contentAlignment = Alignment.Center) {
                    Text(draft.guidedWorkSet.toString().padStart(2, '0'),
                        fontWeight = FontWeight.Black, fontSize = 22.sp,
                        color = PulseColors.Accent)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("SERIE ATTUALE", color = PulseColors.Accent,
                        fontWeight = FontWeight.Bold, letterSpacing = 1.sp, fontSize = 10.sp)
                    Text("${draft.guidedWorkSet} di ${exercise.sets}",
                        color = PulseColors.Foreground, fontWeight = FontWeight.ExtraBold,
                        fontSize = 19.sp)
                }
                Surface(color = PulseColors.Raised, shape = RoundedCornerShape(12.dp)) {
                    Text(if (exercise.timeBased) "TEMPO" else "FORZA",
                        Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                        color = PulseColors.Muted, fontSize = 10.sp,
                        fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(13.dp)) {
                Column(Modifier.weight(1f)) {
                    Text("PESO · KG", color = PulseColors.Muted, fontSize = 11.sp,
                        fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(7.dp))
                    OutlinedTextField(
                        value = weight,
                        onValueChange = { next ->
                            if (next.length <= 7 && next.all { it.isDigit() || it == ',' || it == '.' })
                                weight = next
                        },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        placeholder = { Text("0") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        textStyle = androidx.compose.ui.text.TextStyle(
                            fontSize = 25.sp, fontWeight = FontWeight.Bold,
                            color = PulseColors.Foreground
                        ),
                        shape = RoundedCornerShape(15.dp)
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        TextButton(onClick = { adjustWeight(-2.5) },
                            contentPadding = PaddingValues(horizontal = 5.dp)) {
                            Text("−2,5", fontSize = 12.sp, color = PulseColors.Accent)
                        }
                        TextButton(onClick = { adjustWeight(2.5) },
                            contentPadding = PaddingValues(horizontal = 5.dp)) {
                            Text("+2,5", fontSize = 12.sp, color = PulseColors.Accent)
                        }
                    }
                }
                Column(Modifier.weight(1f)) {
                    Text(if (exercise.timeBased) "DURATA · SEC" else "RIPETIZIONI",
                        color = PulseColors.Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(7.dp))
                    OutlinedTextField(
                        value = reps,
                        onValueChange = { next ->
                            if (next.length <= 3 && next.all(Char::isDigit)) reps = next
                        },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        placeholder = { Text("0") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        textStyle = androidx.compose.ui.text.TextStyle(
                            fontSize = 25.sp, fontWeight = FontWeight.Bold,
                            color = PulseColors.Foreground
                        ),
                        shape = RoundedCornerShape(15.dp)
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        TextButton(onClick = { adjustReps(-1) },
                            contentPadding = PaddingValues(horizontal = 5.dp)) {
                            Text("−1", fontSize = 12.sp, color = PulseColors.Accent)
                        }
                        TextButton(onClick = { adjustReps(1) },
                            contentPadding = PaddingValues(horizontal = 5.dp)) {
                            Text("+1", fontSize = 12.sp, color = PulseColors.Accent)
                        }
                    }
                }
            }
            Surface(
                color = if (warmup) PulseColors.SoftGreen else PulseColors.Raised,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth().clickable { warmup = !warmup }
            ) {
                Row(Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = warmup, onCheckedChange = { warmup = it },
                        colors = CheckboxDefaults.colors(checkedColor = PulseColors.Accent))
                    Spacer(Modifier.width(4.dp))
                    Text("Riscaldamento", fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold, color = PulseColors.Foreground)
                    Spacer(Modifier.weight(1f))
                    Text("non conta nelle serie", fontSize = 10.sp, color = PulseColors.Muted)
                }
            }
            if (!warmup) {
                Spacer(Modifier.height(9.dp))
                if (previousSet != null) {
                    Text("Precedente: ${formatKgForWorkout(previousSet.weightKg)} kg · " +
                        "${previousSet.repetitions} ${if (exercise.timeBased) "sec" else "rep"} · " +
                        (previousSet.actualRir?.let { "RIR $it" } ?: "RIR non registrato"),
                        color = PulseColors.Muted, fontSize = 11.sp)
                }
                if (suggestedKg != null) {
                    Spacer(Modifier.height(6.dp))
                    TextButton(onClick = { weight = formatKgForWorkout(suggestedKg) }) {
                        Text("Usa suggerimento: ${formatKgForWorkout(suggestedKg)} kg",
                            color = PulseColors.Accent, fontSize = 12.sp)
                    }
                }
                Spacer(Modifier.height(9.dp))
                ActualRirPicker(actualRir, onValueChange = { actualRir = it })
            }
            Spacer(Modifier.height(15.dp))
            SolidButton(
                if (warmup) "Salva riscaldamento" else "Completa serie",
                enabled = valid,
                onClick = { onSave(weight, reps, warmup, actualRir) }
            )
            if (!valid) {
                Spacer(Modifier.height(7.dp))
                Text("Inserisci peso (anche 0 kg) e ${if (exercise.timeBased) "secondi" else "ripetizioni"}.",
                    color = PulseColors.Muted, fontSize = 11.sp)
            }
        }
    }
}

@Composable
private fun Panel(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = PulseColors.Surface,
        shape = RoundedCornerShape(25.dp),
        border = BorderStroke(1.dp, PulseColors.Border),
        shadowElevation = 1.dp
    ) {
        Column(Modifier.padding(20.dp), content = content)
    }
}

@Composable
private fun SolidButton(label: String, enabled: Boolean = true, onClick: () -> Unit) {
    Button(
        onClick = onClick, enabled = enabled,
        modifier = Modifier.fillMaxWidth().height(52.dp),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = PulseColors.Accent, contentColor = Color.White
        )
    ) {
        Text(label, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        Spacer(Modifier.width(8.dp))
        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null,
            modifier = Modifier.size(16.dp))
    }
}
