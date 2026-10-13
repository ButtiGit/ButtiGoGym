package it.pulse.workout.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import it.pulse.workout.R
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import android.os.Build
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import coil.request.ImageRequest
import it.pulse.workout.data.*
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private val pagePad = 24.dp

@Composable
private fun BrandHeader() {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 24.dp, bottom = 28.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Image(painterResource(R.drawable.buttigogym_mascot),
                contentDescription = "Logo ButtiGoGym",
                modifier = Modifier.size(48.dp).clip(RoundedCornerShape(14.dp)))
            Spacer(Modifier.width(11.dp))
            Text("ButtiGoGym", color = PulseColors.Accent,
                fontWeight = FontWeight.Black, fontSize = 19.sp)
        }
    }
}

@Composable
private fun Eyebrow(label: String, color: Color = PulseColors.Muted) {
    Text(label, color = color, style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold, letterSpacing = 1.35.sp)
}

@Composable
private fun PageTitle(title: String) {
    Text(title, color = PulseColors.Foreground, fontSize = 34.sp,
        fontWeight = FontWeight.ExtraBold, lineHeight = 38.sp, letterSpacing = (-0.9).sp)
}

@Composable
private fun LabelChip(label: String, selected: Boolean = false) {
    Surface(
        color = if (selected) PulseColors.Accent else PulseColors.Raised,
        shape = RoundedCornerShape(50),
    ) {
        Text(label,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
            color = if (selected) PulseColors.DarkInk else PulseColors.Muted,
            fontSize = 10.sp, fontWeight = FontWeight.ExtraBold,
            letterSpacing = 0.6.sp)
    }
}

@Composable
private fun PulseCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    BGCard(modifier = modifier, padding = 20.dp, content = content)
}

@Composable
private fun LimeButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.fillMaxWidth().height(54.dp),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = PulseColors.Accent,
            contentColor = PulseColors.DarkInk,
            disabledContainerColor = PulseColors.Raised,
            disabledContentColor = PulseColors.Muted
        )
    ) {
        Text(label, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
        Spacer(Modifier.width(8.dp))
        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(18.dp))
    }
}

private fun italianDate(date: Long): String =
    SimpleDateFormat("EEEE d MMMM", Locale.ITALIAN).format(Date(date))

private fun weekStartMillis(): Long {
    val calendar = Calendar.getInstance()
    val offset = (calendar.get(Calendar.DAY_OF_WEEK) + 5) % 7
    calendar.add(Calendar.DAY_OF_MONTH, -offset)
    calendar.set(Calendar.HOUR_OF_DAY, 0)
    calendar.set(Calendar.MINUTE, 0)
    calendar.set(Calendar.SECOND, 0)
    calendar.set(Calendar.MILLISECOND, 0)
    return calendar.timeInMillis
}

@Composable
fun HomeScreen(
    upcoming: WorkoutDay,
    days: List<WorkoutDay>,
    active: WorkoutDraft?,
    history: List<SessionRecord>,
    onStart: (WorkoutDay) -> Unit
) {
    val today = System.currentTimeMillis()
    val current = active?.daySnapshot ?: days.firstOrNull { it.id == active?.dayId } ?: upcoming
    val count = history.count { it.completedAt >= weekStartMillis() && it.dayId != "bonus" }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = pagePad, end = pagePad, bottom = 28.dp)
    ) {
        item {
            BrandHeader()
            Eyebrow(italianDate(today).uppercase(Locale.ITALIAN))
            Spacer(Modifier.height(12.dp))
            PageTitle("Il prossimo passo\nparte da qui.")
            Spacer(Modifier.height(22.dp))
            LabelChip("$count / 3 ALLENAMENTI QUESTA SETTIMANA")
            Spacer(Modifier.height(22.dp))

            PulseCard {
                Box(Modifier.fillMaxWidth().height(4.dp).clip(CircleShape).background(PulseColors.Accent))
                Spacer(Modifier.height(15.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LabelChip(if (active != null) "IN CORSO" else "PROSSIMA SESSIONE", selected = true)
                }
                Spacer(Modifier.height(22.dp))
                Text(current.name, color = PulseColors.Foreground, fontSize = 25.sp, fontWeight = FontWeight.ExtraBold)
                Spacer(Modifier.height(5.dp))
                Text("${current.exercises.size} esercizi  ·  ${current.duration}", color = PulseColors.Muted,
                    style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(24.dp))
                LimeButton(if (active != null) "Riprendi allenamento" else "Inizia allenamento", onClick = { onStart(current) })
            }
            Spacer(Modifier.height(26.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically) {
                Eyebrow("IL TUO PIANO")
                Text("3 GIORNI", color = PulseColors.Accent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(14.dp))
        }
        items(days.filter { it.id != current.id }, key = { it.id }) { day ->
            SmallWorkoutRow(day = day, onClick = { onStart(day) })
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun SmallWorkoutRow(day: WorkoutDay, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        color = PulseColors.Surface,
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, PulseColors.Border)
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(52.dp).background(PulseColors.Raised, RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(day.weekday.take(3), color = PulseColors.Accent, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(day.name, color = PulseColors.Foreground, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                Spacer(Modifier.height(3.dp))
                Text("${day.exercises.size} esercizi · ${day.duration}", color = PulseColors.Muted, fontSize = 12.sp)
            }
            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = PulseColors.Muted)
        }
    }
}

@Composable
fun PlanScreen(
    activeDayId: String?,
    days: List<WorkoutDay>,
    version: String,
    refreshState: String,
    refreshing: Boolean,
    guidelines: List<String>,
    customizedDayIds: Set<String>,
    onRefresh: () -> Unit,
    onSaveDay: (String, List<Exercise>) -> String?,
    onRestoreDay: (String) -> String?,
    onStart: (WorkoutDay) -> Unit
) {
    var editingDayId by remember { mutableStateOf<String?>(null) }
    var showGuidelines by remember { mutableStateOf(false) }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = pagePad, end = pagePad, bottom = 30.dp)
    ) {
        item {
            BrandHeader()
            Eyebrow("SCHEDA")
            Spacer(Modifier.height(12.dp))
            PageTitle("La tua scheda.")
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                LabelChip("3 GIORNI + BONUS")
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onRefresh, enabled = !refreshing) {
                    Text(if (refreshing) "Sincronizzo…" else "↻ Aggiorna",
                        color = PulseColors.Accent)
                }
            }
            if (refreshState.startsWith("Offline") || refreshState.startsWith("Aggiornamento"))
                Text(refreshState, color = PulseColors.Muted, fontSize = 11.sp)
            Spacer(Modifier.height(22.dp))
        }
        if (days.isEmpty()) {
            item {
                BGEmptyState(
                    title = "Scheda ancora non disponibile",
                    description = "Controlla la connessione o riprova ad aggiornare la scheda. " +
                        "Il tuo storico resta salvato sul telefono.",
                    symbol = Icons.Default.PlayArrow,
                    actionLabel = "Riprova aggiornamento",
                    onAction = onRefresh
                )
            }
        }
        items(days, key = { it.id }) { day ->
            var expanded by remember(day.id) { mutableStateOf(false) }
            PulseCard(Modifier.fillMaxWidth().animateContentSize(animationSpec = tween(BGMotion.duration(240)))) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(if (day.optional) "★" else "0${days.indexOf(day) + 1}",
                        color = PulseColors.Accent, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(18.dp))
                    Eyebrow(day.weekday)
                    Spacer(Modifier.weight(1f))
                    if (activeDayId == day.id) LabelChip("IN CORSO", selected = true)
                    else if (day.id in customizedDayIds) LabelChip("PERSONALIZZATA")
                    else if (day.optional) LabelChip("FACOLTATIVO")
                }
                Spacer(Modifier.height(17.dp))
                Text(day.name, color = PulseColors.Foreground,
                    fontSize = 21.sp, fontWeight = FontWeight.ExtraBold)
                Spacer(Modifier.height(7.dp))
                Text("${day.exercises.size} esercizi · ${day.duration}",
                    color = PulseColors.Muted, fontSize = 12.sp)
                Spacer(Modifier.height(13.dp))
                Eyebrow("${day.exercises.count { !it.optional }} ESERCIZI PRINCIPALI · ${day.setCount} SERIE" +
                    (if (day.optionalSetCount > 0) " · +${day.optionalSetCount} SERIE FACOLTATIVE" else ""),
                    PulseColors.Accent)
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { expanded = !expanded }) {
                        Text(if (expanded) "Chiudi ↑" else "Vedi esercizi ↓",
                            color = PulseColors.Accent, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.weight(1f))
                    OutlinedButton(
                        onClick = { editingDayId = day.id },
                        shape = RoundedCornerShape(13.dp),
                        border = BorderStroke(1.dp, PulseColors.Accent)
                    ) {
                        Text("Modifica", color = PulseColors.Accent, fontWeight = FontWeight.Bold)
                    }
                }
                if (expanded) {
                    HorizontalDivider(color = PulseColors.Border)
                    Spacer(Modifier.height(9.dp))
                    day.exercises.forEachIndexed { idx, exercise ->
                        if (idx > 0) Spacer(Modifier.height(12.dp))
                        if (exercise.optional) {
                            Text("RICHIAMO FACOLTATIVO", color = PulseColors.Accent,
                                fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        } else if (exercise.group.isNotBlank() &&
                            (idx == 0 || day.exercises[idx - 1].group != exercise.group)) {
                            Text(exercise.group.uppercase(Locale.ITALIAN), color = PulseColors.Accent,
                                fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                        Text(exercise.name, color = PulseColors.Foreground,
                            fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        Text("${exercise.sets} × ${exercise.target} · recupero ${exercise.restSeconds} s" +
                            (if (exercise.optional) " · saltabile" else ""),
                            color = PulseColors.Muted, fontSize = 12.sp)
                    }
                    if (day.cooldown.isNotBlank()) {
                        Spacer(Modifier.height(14.dp))
                        Text("FINE WO", color = PulseColors.Accent,
                            fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        Text(day.cooldown, color = PulseColors.Muted, fontSize = 12.sp)
                    }
                }
                Spacer(Modifier.height(12.dp))
                Button(onClick = { onStart(day) }, modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = PulseColors.Accent),
                    shape = RoundedCornerShape(14.dp)) {
                    Text(if (activeDayId == day.id) "Riprendi allenamento" else
                        if (day.optional) "Inizia bonus facoltativo" else "Inizia allenamento")
                }
            }
            Spacer(Modifier.height(13.dp))
        }
        item {
            Spacer(Modifier.height(9.dp))
            PulseCard(Modifier.fillMaxWidth()
                .animateContentSize(animationSpec = tween(BGMotion.duration(240)))
                .clickable(
                    onClickLabel = if (showGuidelines) "Nascondi note della scheda"
                        else "Mostra note della scheda",
                    role = androidx.compose.ui.semantics.Role.Button
                ) { showGuidelines = !showGuidelines }
                .semantics {
                    stateDescription = if (showGuidelines) "Dettagli aperti" else "Dettagli chiusi"
                }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Note e progressione", fontSize = 15.sp,
                        fontWeight = FontWeight.Bold, color = PulseColors.Foreground)
                    Spacer(Modifier.weight(1f))
                    Icon(Icons.Default.ChevronRight, contentDescription = null,
                        tint = PulseColors.Accent)
                }
                if (showGuidelines) {
                    Spacer(Modifier.height(13.dp))
                    guidelines.forEach { guideline ->
                        Text("• $guideline", color = PulseColors.Muted, fontSize = 12.sp,
                            modifier = Modifier.padding(bottom = 10.dp))
                    }
                }
            }
        }
    }

    val selectedDay = days.firstOrNull { it.id == editingDayId }
    if (selectedDay != null) {
        PlanDayEditor(
            day = selectedDay,
            customized = selectedDay.id in customizedDayIds,
            onSave = { exercises -> onSaveDay(selectedDay.id, exercises) },
            onRestore = { onRestoreDay(selectedDay.id) },
            onClose = { editingDayId = null }
        )
    }
}

@Composable
fun HistoryScreen(
    history: List<SessionRecord>,
    days: List<WorkoutDay>,
    onPhotoSaved: (Long, String) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = pagePad, end = pagePad, bottom = 30.dp)
    ) {
        item {
            BrandHeader()
            Eyebrow("IL TUO PERCORSO")
            Spacer(Modifier.height(12.dp))
            PageTitle("Ogni serie\nconta.")
            Spacer(Modifier.height(22.dp))
            LabelChip("${history.size} ALLENAMENTI REGISTRATI")
            Spacer(Modifier.height(26.dp))
        }
        if (history.isEmpty()) {
            item {
                BGEmptyState(
                    title = "Ogni storia inizia con una serie",
                    description = "Apri Scheda, completa il tuo primo allenamento e ritroverai " +
                        "qui carichi, ripetizioni, durata e foto.",
                    symbol = Icons.Default.PlayArrow
                )
            }
        } else {
            items(history, key = { it.id }) { record ->
                val day = record.daySnapshot ?: days.firstOrNull { it.id == record.dayId }
                var expanded by remember(record.id) { mutableStateOf(false) }
                PulseCard(Modifier.fillMaxWidth()
                    .animateContentSize(animationSpec = tween(BGMotion.duration(240)))
                    .clickable(
                        onClickLabel = if (expanded) "Nascondi dettagli allenamento"
                            else "Mostra dettagli allenamento",
                        role = androidx.compose.ui.semantics.Role.Button
                    ) { expanded = !expanded }
                    .semantics {
                        stateDescription = if (expanded) "Dettagli aperti" else "Dettagli chiusi"
                    }) {
                    Eyebrow(italianDate(record.completedAt).uppercase(Locale.ITALIAN), PulseColors.Accent)
                    Spacer(Modifier.height(10.dp))
                    Text(day?.name ?: "Allenamento", color = PulseColors.Foreground,
                        fontWeight = FontWeight.Bold, fontSize = 20.sp)
                    Spacer(Modifier.height(8.dp))
                    Text("${record.completedSets}/${record.totalSets} serie · ${formatDuration(record.durationSeconds.toLong())}",
                        color = PulseColors.Muted, fontSize = 13.sp)
                    Spacer(Modifier.height(12.dp))
                    LinearProgressIndicator(
                        progress = { (record.completedSets.toFloat() / record.totalSets.coerceAtLeast(1)).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
                        color = PulseColors.Accent,
                        trackColor = PulseColors.Raised
                    )
                    Spacer(Modifier.height(15.dp))
                    Eyebrow(if (expanded) "DETTAGLI APERTI  ↑" else "APRI KG E RIPETIZIONI  ↓", PulseColors.Accent)
                    if (expanded) {
                        Spacer(Modifier.height(14.dp))
                        HorizontalDivider(color = PulseColors.Border)
                        SessionPhotoAndRecap(record = record, day = day, onPhotoSaved = onPhotoSaved)
                        day?.exercises?.forEach { exercise ->
                            val warmups = record.entries.entries
                                .filter { it.key.startsWith("warmup:${exercise.id}:") && it.value.done }
                                .sortedBy { it.value.completedAt }
                                .map { it.value }
                            val sets = (1..exercise.sets).mapNotNull { index ->
                                record.entries[WorkoutDraft.key(exercise.id, index)]?.takeIf { it.done }
                            }
                            if (sets.isNotEmpty() || warmups.isNotEmpty()) {
                                Spacer(Modifier.height(14.dp))
                                Text(exercise.name, color = PulseColors.Foreground,
                                    fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                Spacer(Modifier.height(3.dp))
                                if (warmups.isNotEmpty()) {
                                    Text("Riscaldamento: " + warmups.joinToString(" · ") {
                                        if (exercise.timeBased) "${it.weight} kg · ${it.reps} sec"
                                        else "${it.weight} kg × ${it.reps}"
                                    }, color = PulseColors.Muted, fontSize = 12.sp)
                                }
                                if (sets.isNotEmpty()) {
                                    Text(sets.joinToString("  ·  ") { entry ->
                                        if (exercise.timeBased) "${entry.weight.ifBlank { "–" }} kg · ${entry.reps.ifBlank { "–" }} sec"
                                        else "${entry.weight.ifBlank { "–" }} kg × ${entry.reps.ifBlank { "–" }}"
                                    }, color = PulseColors.Muted, fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
            }
        }
    }
}

@Composable
fun SessionScreen(
    day: WorkoutDay,
    draft: WorkoutDraft,
    onBack: () -> Unit,
    onUpdate: (WorkoutDraft) -> Unit,
    onFinish: () -> Unit
) {
    var askToFinish by remember { mutableStateOf(false) }
    val done = day.exercises.sumOf { exercise ->
        (1..exercise.sets).count { draft.entry(exercise, it).done }
    }
    val total = day.setCount
    val fraction = done.toFloat() / total.coerceAtLeast(1)

    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 12.dp, top = 15.dp, end = pagePad),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Torna indietro", tint = PulseColors.Foreground)
            }
            Eyebrow("ALLENAMENTO IN CORSO", PulseColors.Accent)
        }
        Column(Modifier.padding(horizontal = pagePad)) {
            Spacer(Modifier.height(15.dp))
            PageTitle(day.name)
            Spacer(Modifier.height(8.dp))
            Text("Iniziato ${SimpleDateFormat("HH:mm", Locale.ITALIAN).format(Date(draft.startedAt))}",
                color = PulseColors.Muted, fontSize = 13.sp)
            Spacer(Modifier.height(20.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically) {
                LabelChip("$done / $total SERIE", selected = true)
                Text("${(fraction * 100).toInt()}% completato", color = PulseColors.Muted,
                    fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(14.dp))
            LinearProgressIndicator(
                progress = { fraction },
                modifier = Modifier.fillMaxWidth().height(7.dp).clip(CircleShape),
                color = PulseColors.Accent,
                trackColor = PulseColors.Raised
            )
            Spacer(Modifier.height(16.dp))
        }
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(start = pagePad, end = pagePad, bottom = 10.dp),
            verticalArrangement = Arrangement.spacedBy(13.dp)
        ) {
            items(day.exercises, key = { it.id }) { exercise ->
                ExerciseCard(
                    exercise = exercise,
                    exerciseNumber = day.exercises.indexOf(exercise) + 1,
                    draft = draft,
                    onUpdate = onUpdate
                )
            }
        }
        Surface(color = PulseColors.Background) {
            Column(Modifier.padding(start = pagePad, end = pagePad, top = 13.dp, bottom = 18.dp)) {
                LimeButton("Termina allenamento", onClick = { askToFinish = true }, enabled = done > 0)
            }
        }
    }

    if (askToFinish) {
        AlertDialog(
            onDismissRequest = { askToFinish = false },
            title = { Text("Salvare l'allenamento?") },
            text = { Text("Hai completato $done serie su $total. La sessione sarà salvata nello storico.") },
            confirmButton = { TextButton(onClick = { askToFinish = false; onFinish() }) { Text("Salva e termina") } },
            dismissButton = { TextButton(onClick = { askToFinish = false }) { Text("Continua") } }
        )
    }
}

@Composable
private fun ExerciseCard(
    exercise: Exercise,
    exerciseNumber: Int,
    draft: WorkoutDraft,
    onUpdate: (WorkoutDraft) -> Unit
) {
    var showDemo by remember(exercise.id) { mutableStateOf(false) }
    PulseCard(Modifier.fillMaxWidth()) {
        Eyebrow("${exerciseNumber.toString().padStart(2, '0')}   ·   ${exercise.muscle}", PulseColors.Accent)
        Spacer(Modifier.height(9.dp))
        Text(exercise.name, color = PulseColors.Foreground, fontWeight = FontWeight.ExtraBold,
            fontSize = 20.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(5.dp))
        Text("${exercise.sets} serie · ${exercise.target} ripetizioni · recupero ${exercise.restSeconds}s · RIR ${exercise.rir}",
            color = PulseColors.Muted, fontSize = 12.sp)
        if (exercise.equipment.isNotBlank()) {
            Spacer(Modifier.height(7.dp))
            Text("Attrezzo: ${exercise.equipment}", color = PulseColors.Muted, fontSize = 12.sp)
        }
        if (exercise.note.isNotBlank()) {
            Spacer(Modifier.height(7.dp))
            Text(exercise.note, color = PulseColors.Foreground, fontSize = 12.sp)
        }
        if (exercise.alternative.isNotBlank()) {
            Spacer(Modifier.height(5.dp))
            Text("Alternativa: ${exercise.alternative}", color = PulseColors.Muted, fontSize = 12.sp)
        }
        if (exercise.gifUrl.isNotBlank()) {
            Spacer(Modifier.height(10.dp))
            TextButton(onClick = { showDemo = !showDemo }) {
                Text(if (showDemo) "Nascondi GIF ↑" else "▶ Guarda esecuzione GIF", color = PulseColors.Accent)
            }
            if (showDemo) ExerciseGif(exercise.gifUrl, exercise.name)
        }
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Eyebrow("SERIE", PulseColors.Muted)
            Spacer(Modifier.weight(1f))
            Eyebrow("KG", PulseColors.Muted)
            Spacer(Modifier.width(62.dp))
            Eyebrow("REPS", PulseColors.Muted)
            Spacer(Modifier.width(40.dp))
        }
        Spacer(Modifier.height(6.dp))
        repeat(exercise.sets) { index ->
            val setNumber = index + 1
            val entry = draft.entry(exercise, setNumber)
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("$setNumber", modifier = Modifier.width(27.dp), color = PulseColors.Muted,
                    fontWeight = FontWeight.Bold, fontSize = 13.sp)
                SetNumberField(
                    value = entry.weight,
                    hint = "0",
                    modifier = Modifier.weight(1f),
                    onValueChange = { new -> onUpdate(draft.update(exercise, setNumber, entry.copy(weight = new))) }
                )
                SetNumberField(
                    value = entry.reps,
                    hint = "reps",
                    modifier = Modifier.weight(1f),
                    onValueChange = { new -> onUpdate(draft.update(exercise, setNumber, entry.copy(reps = new))) }
                )
                Checkbox(
                    checked = entry.done,
                    onCheckedChange = { checked -> onUpdate(draft.update(exercise, setNumber, entry.copy(done = checked))) },
                    modifier = Modifier.size(42.dp),
                    colors = CheckboxDefaults.colors(
                        checkedColor = PulseColors.Accent,
                        checkmarkColor = PulseColors.DarkInk,
                        uncheckedColor = PulseColors.Muted
                    )
                )
            }
        }
    }
}

@Composable
private fun SetNumberField(
    value: String,
    hint: String,
    modifier: Modifier,
    onValueChange: (String) -> Unit
) {
    OutlinedTextField(
        value = value,
        onValueChange = { incoming ->
            // Consente decimali per i kg e limita la lunghezza per evitare overflow.
            if (incoming.length <= 6 && incoming.all { it.isDigit() || it == '.' || it == ',' }) {
                onValueChange(incoming)
            }
        },
        modifier = modifier.height(55.dp),
        singleLine = true,
        placeholder = { Text(hint, color = PulseColors.Muted, fontSize = 12.sp) },
        textStyle = MaterialTheme.typography.bodyMedium.copy(color = PulseColors.Foreground),
        shape = RoundedCornerShape(12.dp),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = PulseColors.Accent,
            unfocusedBorderColor = PulseColors.Border,
            focusedContainerColor = PulseColors.Raised,
            unfocusedContainerColor = PulseColors.Raised
        )
    )
}

/** GIF esterne caricate soltanto su richiesta dell'utente. */
@Composable
private fun ExerciseGif(url: String, title: String) {
    val context = LocalContext.current
    val loader = remember(context) {
        ImageLoader.Builder(context).components {
            if (Build.VERSION.SDK_INT >= 28) add(ImageDecoderDecoder.Factory())
            else add(GifDecoder.Factory())
        }.build()
    }
    AsyncImage(
        model = ImageRequest.Builder(context).data(url).crossfade(false).build(),
        imageLoader = loader,
        contentDescription = "Dimostrazione di $title",
        contentScale = ContentScale.Fit,
        modifier = Modifier.fillMaxWidth().height(200.dp)
            .clip(RoundedCornerShape(14.dp)).background(PulseColors.Raised)
    )
    Spacer(Modifier.height(10.dp))
    Text("GIF esterna; è necessaria una connessione internet.", color = PulseColors.Muted, fontSize = 11.sp)
}
