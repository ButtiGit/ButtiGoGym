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
import androidx.compose.material.icons.filled.MoreHoriz
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
    onStart: (WorkoutDay) -> Unit,
    onStartQuick: (WorkoutDay) -> Unit
) {

    var editingDayId by remember { mutableStateOf<String?>(null) }
    var selectedDayId by remember { mutableStateOf<String?>(null) }
    var toolsOpen by remember { mutableStateOf(false) }
    LazyColumn(modifier = Modifier.fillMaxSize().background(PulseColors.Background),
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("La scheda", style = MaterialTheme.typography.headlineMedium,
                    color = PulseColors.Foreground, modifier = Modifier.weight(1f))
                IconButton(onClick = { toolsOpen = true }) {
                    Icon(Icons.Default.MoreHoriz, "Opzioni scheda", tint = PulseColors.Foreground)
                }
            }
        }
        if (days.isEmpty()) item {
            BGPrimaryButton(if (refreshing) "Sincronizzazione…" else "Carica scheda", onRefresh, enabled = !refreshing)
        }
        items(days, key = { it.id }) { day ->
            BGCard(padding = 20.dp) {
                Text(day.weekday.lowercase(Locale.ITALIAN) + if (day.optional) " · Facoltativo" else "",
                    color = PulseColors.Muted, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(8.dp))
                Text(day.name, style = MaterialTheme.typography.titleLarge, color = PulseColors.Foreground)
                Spacer(Modifier.height(5.dp))
                Text("${day.exercises.size} esercizi · ${day.duration}", color = PulseColors.Muted)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { selectedDayId = day.id }) { Text("Esercizi") }
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = { editingDayId = day.id }) { Text("Modifica") }
                }
                BGPrimaryButton(if (activeDayId == day.id) "Riprendi" else "Inizia", { onStart(day) })
            }
        }
    }
    val selected = days.firstOrNull { it.id == selectedDayId }
    if (selected != null) BGModal(selected.name, { selectedDayId = null }) {
        selected.exercises.forEach { exercise ->
            Column {
                Text(exercise.name, style = MaterialTheme.typography.titleSmall, color = PulseColors.Foreground)
                Text("${exercise.sets} × ${exercise.target} ${if (exercise.timeBased) "sec" else "rep"} · ${exercise.restSeconds}s recupero" +
                    if (exercise.optional) " · Facoltativo" else "", color = PulseColors.Muted)
            }
        }
        if (selected.cooldown.isNotBlank()) Text(selected.cooldown, color = PulseColors.Muted)
        if (activeDayId != selected.id && QuickWorkoutPlanner.create(selected) != null)
            TextButton(onClick = { selectedDayId = null; onStartQuick(selected) }) { Text("Allenamento rapido · 35–45 min") }
    }
    if (toolsOpen) BGModal("La scheda", { toolsOpen = false }) {
        BGPrimaryButton(if (refreshing) "Sincronizzazione…" else "Sincronizza scheda", onRefresh, enabled = !refreshing)
        Text("Versione $version", color = PulseColors.Muted)
        if (refreshState.isNotBlank()) Text(refreshState, color = PulseColors.Muted)
        if (customizedDayIds.isNotEmpty()) Text("Le giornate personalizzate restano salvate. Puoi ripristinarle da Modifica.",
            color = PulseColors.Muted)
        guidelines.forEach { Text(it, color = PulseColors.Foreground) }
    }
    val editing = days.firstOrNull { it.id == editingDayId }
    if (editing != null) PlanDayEditor(day = editing, customized = editing.id in customizedDayIds,
        onSave = { onSaveDay(editing.id, it) }, onRestore = { onRestoreDay(editing.id) },
        onClose = { editingDayId = null })
}

@Composable
fun HistoryScreen(
    history: List<SessionRecord>,
    days: List<WorkoutDay>,
    onPhotoSaved: (Long, String) -> Unit
) {

    var showUpload by remember { mutableStateOf(false) }
    var selectedId by remember { mutableStateOf<Long?>(null) }
    LazyColumn(modifier = Modifier.fillMaxSize().background(PulseColors.Background),
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { Text("Storico", style = MaterialTheme.typography.headlineMedium, color = PulseColors.Foreground) }
        item { BGPrimaryButton("Invia storico", { showUpload = true }, enabled = history.isNotEmpty()) }
        if (history.isEmpty()) item {
            BGCard {
                Text("Il tuo primo allenamento ti aspetta.", color = PulseColors.Muted)
            }
        }
        items(history, key = { it.id }) { record ->
            val day = record.daySnapshot ?: days.firstOrNull { it.id == record.dayId }
            Surface(onClick = { selectedId = record.id }, shape = RoundedCornerShape(22.dp),
                color = PulseColors.Surface, modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(italianDate(record.completedAt), color = PulseColors.Muted)
                        Spacer(Modifier.height(6.dp))
                        Text(day?.name ?: "Allenamento", style = MaterialTheme.typography.titleMedium,
                            color = PulseColors.Foreground)
                        Spacer(Modifier.height(4.dp))
                        Text("${record.completedSets}/${record.totalSets} serie · ${formatDuration(record.durationSeconds.toLong())}",
                            color = PulseColors.Muted)
                    }
                    Icon(Icons.Default.ChevronRight, "Dettagli allenamento", tint = PulseColors.Muted)
                }
            }
        }
    }
    if (showUpload) HistoryUploadPanel(history, days, onClose = { showUpload = false })
    val record = history.firstOrNull { it.id == selectedId }
    if (record != null) {
        val day = record.daySnapshot ?: days.firstOrNull { it.id == record.dayId }
        BGModal(day?.name ?: "Allenamento", { selectedId = null }) {
            Text(italianDate(record.completedAt), color = PulseColors.Muted)
            SessionPhotoAndRecap(record, day, onPhotoSaved)
            day?.exercises?.forEach { exercise ->
                val warmups = record.entries.entries.filter { it.key.startsWith("warmup:${exercise.id}:") && it.value.done }
                    .sortedBy { it.value.completedAt }.map { it.value }
                val sets = (1..exercise.sets).mapNotNull { record.entries[WorkoutDraft.key(exercise.id, it)]?.takeIf { entry -> entry.done } }
                if (sets.isNotEmpty() || warmups.isNotEmpty()) {
                    Text(exercise.name, style = MaterialTheme.typography.titleSmall, color = PulseColors.Foreground)
                    fun label(entry: SetEntry) = "${entry.weight} kg × ${entry.reps} ${if (exercise.timeBased) "sec" else "rep"}" +
                        (entry.rir?.let { " · RIR $it" } ?: "")
                    if (warmups.isNotEmpty()) Text("Riscaldamento: " + warmups.joinToString(" · ", transform = ::label),
                        color = PulseColors.Muted)
                    if (sets.isNotEmpty()) Text(sets.joinToString("\n", transform = ::label), color = PulseColors.Foreground)
                }
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
