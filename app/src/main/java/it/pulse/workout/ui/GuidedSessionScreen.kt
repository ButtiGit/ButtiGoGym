package it.pulse.workout.ui

import android.os.Build
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
    onBack: () -> Unit,
    onUpdate: (WorkoutDraft) -> Unit,
    onFinish: () -> Unit
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
    val mainCompleted = finishedSets - optionalCompleted
    val progressTotal = day.setCount + optionalCompleted
    val allDone = draft.guidedExerciseIndex >= day.exercises.size
    val inRest = !allDone && draft.restUntilMillis > now
    val remainingSeconds = ((draft.restUntilMillis - now + 999L) / 1000L).coerceAtLeast(0)
    val elapsed = ((now - draft.startedAt).coerceAtLeast(0L) / 1000L)
    val exercise = day.exercises.getOrNull(draft.guidedExerciseIndex)
    var finishDialog by remember { mutableStateOf(false) }

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
                Text("SESSIONE GUIDATA", color = PulseColors.Accent, fontSize = 10.sp,
                    fontWeight = FontWeight.Bold, letterSpacing = 1.3.sp)
                Text(day.name, color = PulseColors.Foreground, fontWeight = FontWeight.Bold,
                    fontSize = 16.sp, maxLines = 1)
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
            Text("$mainCompleted / ${day.setCount} serie principali" +
                    (if (day.optionalSetCount > 0) " · $optionalCompleted richiami eseguiti" else "") +
                    " · ${day.exercises.size} esercizi",
                color = PulseColors.Muted, fontSize = 11.sp)
        }

        LazyColumn(
            modifier = Modifier.fillMaxWidth().weight(1f),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
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
                        SolidButton("Salva allenamento", onClick = { finishDialog = true })
                    }
                }
            } else if (exercise != null) {
                item {
                    Panel {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(43.dp).clip(RoundedCornerShape(13.dp))
                                .background(PulseColors.SoftGreen), contentAlignment = Alignment.Center) {
                                Text("${draft.guidedExerciseIndex + 1}",
                                    color = PulseColors.Accent, fontWeight = FontWeight.ExtraBold)
                            }
                            Spacer(Modifier.width(13.dp))
                            Column {
                                Text("ESERCIZIO ${draft.guidedExerciseIndex + 1} DI ${day.exercises.size}",
                                    color = PulseColors.Accent, fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
                                Text(exercise.muscle, color = PulseColors.Muted, fontSize = 12.sp)
                            }
                        }
                        Spacer(Modifier.height(16.dp))
                        if (exercise.optional) {
                            Text("RICHIAMO FACOLTATIVO · PUOI SALTARLO",
                                color = PulseColors.Accent, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                            Spacer(Modifier.height(7.dp))
                        }
                        if (exercise.group.isNotBlank()) {
                            Text(exercise.group.uppercase(Locale.ITALIAN),
                                color = PulseColors.Accent, fontWeight = FontWeight.Bold, fontSize = 10.sp)
                            Spacer(Modifier.height(5.dp))
                        }
                        Text(exercise.name, color = PulseColors.Foreground,
                            fontSize = 25.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 29.sp)
                        Spacer(Modifier.height(8.dp))
                        Text("${exercise.sets} serie allenanti · ${exercise.target}" +
                                (if (exercise.target.contains("sec")) "" else " rep") +
                                " · RIR ${exercise.rir}",
                            color = PulseColors.Muted, fontSize = 13.sp)
                        if (exercise.note.isNotEmpty()) {
                            Spacer(Modifier.height(10.dp))
                            Text(exercise.note, color = PulseColors.Foreground, fontSize = 12.sp)
                        }
                        if (exercise.alternative.isNotEmpty()) {
                            Spacer(Modifier.height(6.dp))
                            Text("Alternativa: ${exercise.alternative}",
                                color = PulseColors.Muted, fontSize = 11.sp)
                        }
                        if (exercise.optional) {
                            Spacer(Modifier.height(12.dp))
                            OutlinedButton(
                                onClick = { onUpdate(draft.skipOptionalExercise(day)) },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(14.dp),
                                border = BorderStroke(1.dp, PulseColors.Border)
                            ) { Text("Salta questo richiamo", color = PulseColors.Accent) }
                        }
                        if (exercise.gifUrl.isNotBlank()) {
                            var showGif by remember(exercise.id) { mutableStateOf(false) }
                            Spacer(Modifier.height(7.dp))
                            TextButton(onClick = { showGif = !showGif }) {
                                Text(if (showGif) "Nascondi dimostrazione" else "▶ Guarda la GIF",
                                    color = PulseColors.Accent)
                            }
                            if (showGif) {
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
                                    modifier = Modifier.fillMaxWidth().height(190.dp)
                                        .clip(RoundedCornerShape(15.dp)).background(PulseColors.Raised)
                                )
                            }
                        }
                    }
                }

                if (inRest) {
                    item {
                        Panel {
                            Text("RECUPERO", color = PulseColors.Accent, fontSize = 11.sp,
                                fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
                            Spacer(Modifier.height(13.dp))
                            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                Text(formatDuration(remainingSeconds), fontSize = 61.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = PulseColors.Accent, letterSpacing = (-2).sp)
                            }
                            Spacer(Modifier.height(4.dp))
                            Text("Recupera prima della prossima serie. Il timer continua anche se lasci questa schermata.",
                                color = PulseColors.Muted, fontSize = 13.sp)
                            Spacer(Modifier.height(19.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                                OutlinedButton(
                                    onClick = { onUpdate(draft.copy(restUntilMillis = (draft.restUntilMillis - 15_000L).coerceAtLeast(now))) },
                                    modifier = Modifier.weight(1f),
                                    border = BorderStroke(1.dp, PulseColors.Border),
                                    shape = RoundedCornerShape(14.dp)
                                ) { Text("−15 s", color = PulseColors.Accent) }
                                OutlinedButton(
                                    onClick = { onUpdate(draft.copy(restUntilMillis = draft.restUntilMillis + 15_000L)) },
                                    modifier = Modifier.weight(1f),
                                    border = BorderStroke(1.dp, PulseColors.Border),
                                    shape = RoundedCornerShape(14.dp)
                                ) { Text("+15 s", color = PulseColors.Accent) }
                            }
                            Spacer(Modifier.height(10.dp))
                            SolidButton("Salta recupero", onClick = {
                                onUpdate(draft.copy(restUntilMillis = 0L))
                            })
                        }
                    }
                } else {
                    item(key = "series-${exercise.id}-${draft.guidedWorkSet}-${draft.entries.count { it.key.startsWith("warmup:${exercise.id}:") }}") {
                        GuidedSetForm(
                            exercise = exercise,
                            draft = draft,
                            onSave = { weight, reps, warmup ->
                                onUpdate(draft.recordSet(
                                    day = day,
                                    weight = weight.replace(',', '.'),
                                    reps = reps, warmup = warmup, at = System.currentTimeMillis()
                                ))
                            }
                        )
                    }
                }

                item {
                    val logged = (1..exercise.sets).mapNotNull { number ->
                        draft.entry(exercise, number).takeIf { it.done }?.let { number to it }
                    }
                    val warmups = draft.warmups(exercise)
                    if (logged.isNotEmpty() || warmups.isNotEmpty()) {
                        Panel {
                            Text("REGISTRATE", color = PulseColors.Muted, fontSize = 10.sp,
                                fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
                            warmups.forEachIndexed { index, it ->
                                LoggedRow("Risc. ${index + 1}", it, exercise.timeBased)
                            }
                            logged.forEach { (number, entry) ->
                                LoggedRow("Serie $number", entry, exercise.timeBased)
                            }
                        }
                    }
                }
            }
            item {
                Text("La durata viene salvata alla fine. Uscendo e rientrando puoi riprendere la sessione.",
                    color = PulseColors.Muted, fontSize = 11.sp)
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
private fun GuidedSetForm(
    exercise: Exercise,
    draft: WorkoutDraft,
    onSave: (String, String, Boolean) -> Unit
) {
    val preset = draft.entry(exercise, draft.guidedWorkSet)
    var weight by remember { mutableStateOf(preset.weight) }
    var reps by remember { mutableStateOf(preset.reps) }
    var warmup by remember { mutableStateOf(false) }
    val numericWeight = weight.replace(',', '.').toDoubleOrNull()
    val valid = numericWeight != null && numericWeight >= 0 && numericWeight <= 10000 &&
        (reps.toIntOrNull() ?: 0) in 1..999
    Panel {
        Text("SERIE ${draft.guidedWorkSet} DI ${exercise.sets}", color = PulseColors.Accent,
            fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
        Spacer(Modifier.height(6.dp))
        Text("Completa la serie e registra i risultati.",
            color = PulseColors.Muted, fontSize = 13.sp)
        Spacer(Modifier.height(19.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(
                value = weight,
                onValueChange = { new ->
                    if (new.length <= 7 && new.all { it.isDigit() || it == ',' || it == '.' })
                        weight = new
                },
                modifier = Modifier.weight(1f),
                singleLine = true,
                label = { Text("Peso (kg)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                shape = RoundedCornerShape(15.dp)
            )
            OutlinedTextField(
                value = reps,
                onValueChange = { new ->
                    if (new.length <= 3 && new.all(Char::isDigit)) reps = new
                },
                modifier = Modifier.weight(1f),
                singleLine = true,
                label = { Text(if (exercise.timeBased) "Secondi" else "Ripetizioni") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                shape = RoundedCornerShape(15.dp)
            )
        }
        Spacer(Modifier.height(12.dp))
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = PulseColors.Raised,
            shape = RoundedCornerShape(15.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp)
            ) {
                Checkbox(
                    checked = warmup,
                    onCheckedChange = { warmup = it },
                    colors = CheckboxDefaults.colors(checkedColor = PulseColors.Accent)
                )
                Column(Modifier.weight(1f)) {
                    Text("Serie di riscaldamento", fontWeight = FontWeight.Bold,
                        color = PulseColors.Foreground, fontSize = 13.sp)
                    Text("Registrata, ma non scala le serie allenanti",
                        color = PulseColors.Muted, fontSize = 11.sp)
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        SolidButton(if (warmup) "Registra riscaldamento" else "Serie completata · Avvia recupero",
            enabled = valid, onClick = { onSave(weight, reps, warmup) })
        if (!valid) {
            Spacer(Modifier.height(8.dp))
            Text(if (exercise.timeBased) "Indica il peso (anche 0 kg) e la durata in secondi."
                else "Indica il peso (anche 0 kg) e almeno una ripetizione.",
                color = PulseColors.Muted, fontSize = 11.sp)
        }
    }
}

@Composable
private fun LoggedRow(title: String, entry: SetEntry, timeBased: Boolean = false) {
    Spacer(Modifier.height(10.dp))
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Default.Check, null, tint = PulseColors.Accent,
            modifier = Modifier.size(15.dp))
        Spacer(Modifier.width(8.dp))
        Text(title, color = PulseColors.Foreground, fontSize = 12.sp,
            modifier = Modifier.weight(1f))
        Text(if (timeBased) "${entry.weight} kg · ${entry.reps} sec" else "${entry.weight} kg × ${entry.reps} rep",
            color = PulseColors.Accent,
            fontWeight = FontWeight.Bold, fontSize = 12.sp)
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
