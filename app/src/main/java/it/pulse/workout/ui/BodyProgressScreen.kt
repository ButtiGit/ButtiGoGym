package it.pulse.workout.ui

import android.app.DatePickerDialog
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import it.pulse.workout.data.BodyMeasure
import it.pulse.workout.data.BodyProgressEntry
import it.pulse.workout.data.BodyProgressRules
import it.pulse.workout.data.BodyProgressStore
import it.pulse.workout.data.ProgressPhotoAngle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val dateLabel = DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ITALIAN)
private fun metric(value: Double?, suffix: String): String =
    if (value == null) "—"
    else String.format(Locale.ITALIAN, "%.1f", value) + " $suffix"

/** Diary-only data: photos never uploaded, shared, or placed in public gallery. */
@Composable
fun BodyProgressScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val store = remember(context) { BodyProgressStore(context) }
    var entries by remember { mutableStateOf(store.entries()) }
    var editorDate by remember { mutableStateOf<LocalDate?>(null) }
    var deleteDate by remember { mutableStateOf<LocalDate?>(null) }
    var photoDate by remember { mutableStateOf<LocalDate?>(null) }
    var photoAngle by remember { mutableStateOf(ProgressPhotoAngle.FRONT) }
    var compareAngle by remember { mutableStateOf(ProgressPhotoAngle.FRONT) }
    var oldDate by remember { mutableStateOf<LocalDate?>(null) }
    var newerDate by remember { mutableStateOf<LocalDate?>(null) }
    var mediaBusy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    var pendingPhoto by remember {
        mutableStateOf<Triple<LocalDate, ProgressPhotoAngle, File>?>(null)
    }

    fun message(text: String) {
        Toast.makeText(context, text, Toast.LENGTH_LONG).show()
    }

    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        val target = pendingPhoto
        pendingPhoto = null
        if (uri != null && target != null) {
            mediaBusy = true
            scope.launch {
                try {
                    val file = withContext(Dispatchers.IO) {
                        store.importFromGallery(uri, target.first, target.second)
                    }
                    entries = store.savePhoto(target.first, target.second, file)
                    message("Foto progresso salvata")
                } catch (error: Exception) {
                    message("Foto non salvata: ${error.message.orEmpty().take(90)}")
                } finally { mediaBusy = false }
            }
        }
    }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val target = pendingPhoto
        pendingPhoto = null
        if (target != null) {
            if (!ok) {
                target.third.delete()
            } else {
                mediaBusy = true
                scope.launch {
                    try {
                        val file = withContext(Dispatchers.IO) {
                            store.finalizeCameraPhoto(target.third)
                        }
                        entries = store.savePhoto(target.first, target.second, file)
                        message("Foto progresso salvata")
                    } catch (error: Exception) {
                        target.third.delete()
                        message("Scatto non salvato: ${error.message.orEmpty().take(90)}")
                    } finally { mediaBusy = false }
                }
            }
        }
    }

    fun chooseDate(afterSelection: (LocalDate) -> Unit) {
        val today = LocalDate.now()
        val dialog = DatePickerDialog(
            context,
            { _, year, month, day ->
                afterSelection(LocalDate.of(year, month + 1, day))
            }, today.year, today.monthValue - 1, today.dayOfMonth
        )
        dialog.datePicker.maxDate = System.currentTimeMillis()
        dialog.show()
    }

    val before = entries.firstOrNull { it.date == oldDate } ?: entries.firstOrNull()
    val after = entries.firstOrNull { it.date == newerDate } ?: entries.lastOrNull()
    val comparable = before != null && after != null && before.date != after.date

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(PulseColors.Background),
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 16.dp, bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Indietro",
                        tint = PulseColors.Foreground)
                }
                Spacer(Modifier.width(4.dp))
                Column(Modifier.weight(1f)) {
                    Text("MYSELF", color = PulseColors.Accent, fontSize = 10.sp,
                        fontWeight = FontWeight.ExtraBold)
                    Text("Evoluzione fisica", color = PulseColors.Foreground,
                        fontSize = 27.sp, fontWeight = FontWeight.ExtraBold)
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = { chooseDate { editorDate = it } },
                    modifier = Modifier.weight(1f), shape = RoundedCornerShape(15.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = PulseColors.Accent)
                ) {
                    Icon(Icons.Default.Add, null, modifier = Modifier.size(17.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Registra misure", color = Color.White, fontSize = 12.sp)
                }
                OutlinedButton(
                    onClick = { chooseDate { photoDate = it } },
                    modifier = Modifier.weight(1f), enabled = !mediaBusy,
                    shape = RoundedCornerShape(15.dp),
                    border = BorderStroke(1.dp, PulseColors.Border)
                ) {
                    Icon(Icons.Default.CameraAlt, null, modifier = Modifier.size(17.dp),
                        tint = PulseColors.Accent)
                    Spacer(Modifier.width(4.dp))
                    Text("Aggiungi foto", color = PulseColors.Accent, fontSize = 12.sp)
                }
            }
        }
        if (entries.isEmpty()) {
            item {
                Surface(
                    color = PulseColors.Surface, shape = RoundedCornerShape(20.dp),
                    border = BorderStroke(1.dp, PulseColors.Border)
                ) {
                    Column(Modifier.fillMaxWidth().padding(20.dp)) {
                        Text("Inizia da oggi", color = PulseColors.Foreground,
                            fontSize = 19.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(6.dp))
                        Text("Registra una misura o scatta una foto. Potrai confrontare " +
                            "i cambiamenti scegliendo due date diverse.",
                            color = PulseColors.Muted, fontSize = 13.sp)
                    }
                }
            }
        } else {
            item {
                Surface(
                    color = PulseColors.Surface, shape = RoundedCornerShape(22.dp),
                    border = BorderStroke(1.dp, PulseColors.Border)
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text("PRIMA / DOPO", color = PulseColors.Accent,
                            fontWeight = FontWeight.ExtraBold, fontSize = 11.sp)
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                            Box(Modifier.weight(1f)) {
                                DateDropdown("Prima", before?.date, entries) { oldDate = it }
                            }
                            Box(Modifier.weight(1f)) {
                                DateDropdown("Dopo", after?.date, entries) { newerDate = it }
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                            ProgressPhotoAngle.entries.forEach { angle ->
                                FilterChip(
                                    selected = compareAngle == angle,
                                    onClick = { compareAngle = angle },
                                    label = { Text(angle.label, fontSize = 11.sp) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = PulseColors.Accent,
                                        selectedLabelColor = Color.White
                                    )
                                )
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                            ComparePhoto(before, compareAngle,
                                before?.let { store.photoFile(it, compareAngle) },
                                "Prima", Modifier.weight(1f))
                            ComparePhoto(after, compareAngle,
                                after?.let { store.photoFile(it, compareAngle) },
                                "Dopo", Modifier.weight(1f))
                        }
                        if (comparable) {
                            Spacer(Modifier.height(14.dp))
                            val older = requireNotNull(before)
                            val newer = requireNotNull(after)
                            DeltaRow("Peso", older.weightKg, newer.weightKg, "kg")
                            BodyMeasure.entries.forEach { field ->
                                DeltaRow(field.label, older.measurementsCm[field],
                                    newer.measurementsCm[field], "cm")
                            }
                            Spacer(Modifier.height(6.dp))
                            Text("Le variazioni non indicano da sole guadagno o perdita di muscolo.",
                                color = PulseColors.Muted, fontSize = 10.sp)
                        } else {
                            Text("Seleziona due date diverse per confrontare le misure.",
                                color = PulseColors.Muted, fontSize = 11.sp)
                        }
                    }
                }
            }
            item {
                Text("DIARIO", color = PulseColors.Accent,
                    fontWeight = FontWeight.ExtraBold, fontSize = 11.sp)
            }
            items(entries.asReversed(), key = { it.date.toString() }) { entry ->
                Surface(
                    color = PulseColors.Surface, shape = RoundedCornerShape(19.dp),
                    border = BorderStroke(1.dp, PulseColors.Border)
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(entry.date.format(dateLabel),
                                    color = PulseColors.Foreground,
                                    fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                Text(metric(entry.weightKg, "kg") + " · " +
                                    "${entry.measurementsCm.size} misure · " +
                                    "${entry.photos.size} foto",
                                    color = PulseColors.Muted, fontSize = 11.sp)
                            }
                            IconButton(onClick = { editorDate = entry.date }) {
                                Icon(Icons.Default.Edit, "Modifica misure",
                                    tint = PulseColors.Accent)
                            }
                            IconButton(onClick = { deleteDate = entry.date }) {
                                Icon(Icons.Default.DeleteOutline, "Elimina registrazione",
                                    tint = PulseColors.Muted)
                            }
                        }
                        OutlinedButton(
                            onClick = { photoDate = entry.date },
                            enabled = !mediaBusy,
                            modifier = Modifier.fillMaxWidth(),
                            border = BorderStroke(1.dp, PulseColors.Border),
                            shape = RoundedCornerShape(13.dp)
                        ) {
                            Icon(Icons.Default.PhotoLibrary, null,
                                modifier = Modifier.size(16.dp), tint = PulseColors.Accent)
                            Spacer(Modifier.width(6.dp))
                            Text("Foto fronte, profilo o retro", color = PulseColors.Accent,
                                fontSize = 12.sp)
                        }
                    }
                }
            }
        }
        item {
            Text("Foto e misure sono private, conservate nell'app e nel backup ZIP " +
                "manuale (non cifrato). Usa pose, luce e distanza simili per confronti più utili.",
                fontSize = 11.sp, color = PulseColors.Muted)
        }
    }

    editorDate?.let { date ->
        val previous = entries.firstOrNull { it.date == date }
        BodyMeasurementsEditor(
            date = date, previous = previous,
            onDismiss = { editorDate = null },
            onSave = { weight, values ->
                val error = runCatching {
                    entries = store.saveMeasurements(date, weight, values)
                }.exceptionOrNull()
                if (error == null) editorDate = null
                error?.message
            }
        )
    }

    deleteDate?.let { date ->
        AlertDialog(
            onDismissRequest = { deleteDate = null },
            title = { Text("Eliminare la registrazione?") },
            text = {
                Text("Eliminerai misure e foto progresso del ${date.format(dateLabel)} " +
                    "dal telefono. Questa azione non è reversibile.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        runCatching { entries = store.deleteEntry(date) }
                            .onFailure { message(it.message.orEmpty()) }
                        deleteDate = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PulseColors.Accent)
                ) { Text("Elimina", color = Color.White) }
            },
            dismissButton = {
                TextButton(onClick = { deleteDate = null }) {
                    Text("Annulla", color = PulseColors.Accent)
                }
            }
        )
    }

    photoDate?.let { date ->
        val existing = entries.firstOrNull { it.date == date }
        AlertDialog(
            onDismissRequest = { photoDate = null },
            title = { Text("Foto · ${date.format(dateLabel)}") },
            text = {
                Column {
                    Text("Scegli la vista da registrare.",
                        fontSize = 12.sp, color = PulseColors.Muted)
                    Spacer(Modifier.height(6.dp))
                    ProgressPhotoAngle.entries.forEach { angle ->
                        val exists = existing?.photos?.containsKey(angle) == true
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(
                                selected = photoAngle == angle,
                                onClick = { photoAngle = angle },
                                colors = RadioButtonDefaults.colors(
                                    selectedColor = PulseColors.Accent)
                            )
                            Text(angle.label + if (exists) " · già presente" else "",
                                color = PulseColors.Foreground, fontSize = 13.sp)
                            if (exists) {
                                Spacer(Modifier.weight(1f))
                                TextButton(onClick = {
                                    runCatching { entries = store.deletePhoto(date, angle) }
                                        .onFailure { message(it.message.orEmpty()) }
                                }) { Text("Rimuovi", color = PulseColors.Accent) }
                            }
                        }
                    }
                    if (existing?.photos?.containsKey(photoAngle) == true) {
                        Spacer(Modifier.height(5.dp))
                        Text("La nuova foto sostituirà quella della stessa vista e data.",
                            color = PulseColors.Muted, fontSize = 11.sp)
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !mediaBusy,
                    onClick = {
                        pendingPhoto = Triple(date, photoAngle, File(""))
                        photoDate = null
                        gallery.launch("image/*")
                    }
                ) { Text("Galleria", color = PulseColors.Accent) }
            },
            dismissButton = {
                TextButton(
                    enabled = !mediaBusy,
                    onClick = {
                        runCatching { store.cameraDestination(date, photoAngle) }
                            .onSuccess { (file, uri) ->
                                pendingPhoto = Triple(date, photoAngle, file)
                                photoDate = null
                                camera.launch(uri)
                            }.onFailure {
                                message("Fotocamera non disponibile: ${it.message.orEmpty()}")
                            }
                    }
                ) { Text("Fotocamera", color = PulseColors.Accent) }
            }
        )
    }
}

@Composable
private fun DateDropdown(
    label: String,
    selected: LocalDate?,
    entries: List<BodyProgressEntry>,
    onSelect: (LocalDate) -> Unit
) {
    var open by remember { mutableStateOf(false) }
    Column {
        Text(label, color = PulseColors.Muted, fontSize = 11.sp)
        OutlinedButton(
            onClick = { open = true },
            modifier = Modifier.fillMaxWidth(),
            border = BorderStroke(1.dp, PulseColors.Border),
            contentPadding = PaddingValues(horizontal = 7.dp, vertical = 3.dp)
        ) {
            Text(selected?.format(dateLabel) ?: "Scegli",
                color = PulseColors.Accent, fontSize = 12.sp,
                maxLines = 1)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            entries.forEach { entry ->
                DropdownMenuItem(
                    text = { Text(entry.date.format(dateLabel)) },
                    onClick = { onSelect(entry.date); open = false }
                )
            }
        }
    }
}

@Composable
private fun ComparePhoto(
    entry: BodyProgressEntry?,
    angle: ProgressPhotoAngle,
    file: File?,
    name: String,
    modifier: Modifier
) {
    Column(modifier) {
        Surface(color = PulseColors.Raised, shape = RoundedCornerShape(15.dp)) {
            if (file != null) {
                AsyncImage(
                    model = file,
                    contentDescription = "Foto $name, ${angle.label}, " +
                        entry?.date?.format(dateLabel),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth().aspectRatio(.78f)
                )
            } else {
                Box(
                    Modifier.fillMaxWidth().aspectRatio(.78f),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Nessuna foto", fontSize = 11.sp, color = PulseColors.Muted)
                }
            }
        }
        Spacer(Modifier.height(5.dp))
        Text(name + " · " + (entry?.date?.format(dateLabel) ?: "—"),
            fontSize = 11.sp, color = PulseColors.Muted, maxLines = 1)
    }
}

@Composable
private fun DeltaRow(label: String, first: Double?, second: Double?, unit: String) {
    if (first == null && second == null) return
    val diff = if (first != null && second != null) second - first else null
    Row(
        Modifier.fillMaxWidth().padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = PulseColors.Foreground, fontSize = 12.sp,
            modifier = Modifier.weight(1f))
        Text(
            if (diff == null) "—"
            else (if (diff > 0) "+" else "") + metric(diff, unit),
            color = PulseColors.Accent, fontSize = 12.sp, fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.width(12.dp))
        Text(metric(second, unit), color = PulseColors.Muted, fontSize = 11.sp)
    }
}

@Composable
private fun BodyMeasurementsEditor(
    date: LocalDate,
    previous: BodyProgressEntry?,
    onDismiss: () -> Unit,
    onSave: (Double?, Map<BodyMeasure, Double>) -> String?
) {
    var weight by remember(date) { mutableStateOf(previous?.weightKg?.toString().orEmpty()) }
    val fields = remember(date) {
        BodyMeasure.entries.associateWith { field ->
            mutableStateOf(previous?.measurementsCm?.get(field)?.toString().orEmpty())
        }
    }
    var error by remember(date) { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Misure · ${date.format(dateLabel)}") },
        text = {
            Column(
                Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState())
            ) {
                Text("Lascia vuoti i campi non misurati.",
                    color = PulseColors.Muted, fontSize = 12.sp)
                Spacer(Modifier.height(9.dp))
                OutlinedTextField(
                    value = weight,
                    onValueChange = { if (it.length <= 7) weight = it },
                    label = { Text("Peso corporeo · kg") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
                BodyMeasure.entries.forEach { field ->
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = fields.getValue(field).value,
                        onValueChange = { if (it.length <= 7) fields.getValue(field).value = it },
                        label = { Text("${field.label} · cm") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                if (error != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(error.orEmpty(), color = MaterialTheme.colorScheme.error,
                        fontSize = 11.sp)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val result = runCatching {
                        fun parse(raw: String): Double? =
                            raw.trim().takeIf { it.isNotEmpty() }
                                ?.replace(',', '.')?.toDoubleOrNull()
                                ?: if (raw.isBlank()) null else throw IllegalArgumentException("Numero non valido")
                        val value = parse(weight)
                        val cm = BodyMeasure.entries.mapNotNull { field ->
                            parse(fields.getValue(field).value)?.let { field to it }
                        }.toMap()
                        val currentPhotos = previous?.photos.orEmpty()
                        BodyProgressRules.validate(BodyProgressEntry(date, value, cm, currentPhotos))
                        onSave(value, cm)
                    }
                    error = result.getOrElse { it.message ?: "Valore non valido" }
                },
                colors = ButtonDefaults.buttonColors(containerColor = PulseColors.Accent)
            ) { Text("Salva", color = Color.White) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Annulla", color = PulseColors.Accent) }
        }
    )
}
