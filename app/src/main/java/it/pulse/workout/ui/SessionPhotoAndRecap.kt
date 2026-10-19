package it.pulse.workout.ui

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import it.pulse.workout.data.SessionRecord
import it.pulse.workout.data.WorkoutDay
import it.pulse.workout.data.WorkoutRecapCalculator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.NumberFormat
import java.util.Locale

/** Ogni sessione gestisce i propri lanciatori ed usa soltanto immagini private dell'app. */
@Composable
internal fun SessionPhotoAndRecap(
    record: SessionRecord,
    day: WorkoutDay?,
    onPhotoSaved: (Long, String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var pendingCameraName by remember { mutableStateOf<String?>(null) }
    val photo = WorkoutPhotoStorage.fileFor(context, record)
    val stats = remember(record, day) { WorkoutRecapCalculator.calculate(record, day) }
    val formatter = remember { NumberFormat.getNumberInstance(Locale.ITALIAN).apply { maximumFractionDigits = 1 } }
    var feedback by remember { mutableStateOf("") }

    fun notifyUser(message: String) {
        feedback = message
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }

    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            busy = true
            scope.launch {
                runCatching {
                    withContext(Dispatchers.IO) {
                        WorkoutPhotoStorage.importFromGallery(context, uri, record.id).name
                    }
                }.onSuccess { fileName ->
                    onPhotoSaved(record.id, fileName)
                    notifyUser("Foto allegata alla sessione")
                }.onFailure { notifyUser("Foto non salvata: ${it.message.orEmpty().take(75)}") }
                busy = false
            }
        }
    }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        val name = pendingCameraName
        pendingCameraName = null
        if (success && name != null) {
            busy = true
            scope.launch {
                runCatching {
                    withContext(Dispatchers.IO) {
                        val file = File(context.filesDir, "session_photos/$name")
                        WorkoutPhotoStorage.normalizePhoto(file).name
                    }
                }.onSuccess { fileName ->
                    onPhotoSaved(record.id, fileName)
                    notifyUser("Scatto allegato alla sessione")
                }.onFailure { notifyUser("Scatto non salvato: ${it.message.orEmpty().take(75)}") }
                busy = false
            }
        } else if (name != null) {
            File(context.filesDir, "session_photos/$name").delete()
        }
    }
    val exporter = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("image/png")
    ) { uri ->
        if (uri != null) {
            busy = true
            scope.launch {
                runCatching {
                    withContext(Dispatchers.IO) {
                        WorkoutRecapImage.export(context, record, day, uri)
                    }
                }.onSuccess { notifyUser("Immagine recap salvata in PNG") }
                    .onFailure { notifyUser("Recap non salvato: ${it.message.orEmpty().take(75)}") }
                busy = false
            }
        }
    }

    Spacer(Modifier.height(16.dp))
    Text("FOTO DELLA SESSIONE", color = PulseColors.Accent,
        fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.2.sp)
    Spacer(Modifier.height(10.dp))
    if (photo != null) {
        AsyncImage(
            model = photo,
            contentDescription = "Foto allegata all'allenamento",
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxWidth().height(230.dp),
        )
        Spacer(Modifier.height(12.dp))
    } else {
        Text("Aggiungi una foto dalla galleria o scattane una. Rimane privata sul telefono.",
            color = PulseColors.Muted, fontSize = 12.sp)
        Spacer(Modifier.height(10.dp))
    }
    Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
        OutlinedButton(
            onClick = { gallery.launch("image/*") }, enabled = !busy,
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(13.dp),
            border = BorderStroke(1.dp, PulseColors.Border)
        ) { Text(if (photo == null) "Allega foto" else "Cambia foto",
            fontSize = 12.sp, color = PulseColors.Accent) }
        OutlinedButton(
            onClick = {
                runCatching { WorkoutPhotoStorage.createCameraPhoto(context, record.id) }
                    .onSuccess { (file, uri) ->
                        pendingCameraName = file.name
                        camera.launch(uri)
                    }.onFailure { notifyUser("Fotocamera non disponibile") }
            }, enabled = !busy,
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(13.dp),
            border = BorderStroke(1.dp, PulseColors.Border)
        ) { Text("Scatta foto", fontSize = 12.sp, color = PulseColors.Accent) }
    }
    Spacer(Modifier.height(17.dp))
    Surface(color = PulseColors.Raised, shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) {
            Text("STATISTICHE DEL WORKOUT", color = PulseColors.Muted,
                fontSize = 10.sp, fontWeight = FontWeight.ExtraBold)
            Spacer(Modifier.height(11.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatRecap("VOLUME", "${formatter.format(stats.volumeKg)} kg", Modifier.weight(1f))
                StatRecap("DURATA", formatDuration(stats.durationSeconds.toLong()), Modifier.weight(1f))
                StatRecap("KCAL ≈", "${stats.estimatedCalories}", Modifier.weight(1f))
            }

        }
    }
    Spacer(Modifier.height(13.dp))
    Button(
        onClick = {
            if (!busy && photo != null) {
                busy = true
                scope.launch {
                    runCatching {
                        withContext(Dispatchers.IO) {
                            WorkoutRecapImage.share(context, record, day)
                        }
                    }.onSuccess { intent ->
                        runCatching {
                            context.startActivity(android.content.Intent.createChooser(intent, "Condividi il workout"))
                        }.onFailure { notifyUser("Nessuna app disponibile per condividere") }
                    }.onFailure {
                        notifyUser("Condivisione non riuscita: ${it.message.orEmpty().take(75)}")
                    }
                    busy = false
                }
            }
        },
        enabled = photo != null && !busy,
        modifier = Modifier.fillMaxWidth().height(52.dp),
        shape = RoundedCornerShape(15.dp),
        colors = ButtonDefaults.buttonColors(containerColor = PulseColors.Accent)
    ) {
        Text("Condividi il recap ↗", fontWeight = FontWeight.Bold, color = Color.White)
    }
    Spacer(Modifier.height(8.dp))
    OutlinedButton(
        onClick = { exporter.launch("ButtiGoGym-Recap-${record.id}.png") },
        enabled = photo != null && !busy,
        modifier = Modifier.fillMaxWidth().height(52.dp),
        shape = RoundedCornerShape(15.dp),
        border = BorderStroke(1.dp, PulseColors.Border)
    ) {
        Text("Salva immagine", fontWeight = FontWeight.SemiBold,
            color = PulseColors.Accent)
    }
    if (photo == null) {
        Text("Allega una foto per condividere o salvare il recap.",
            color = PulseColors.Muted, fontSize = 11.sp)
    }
    if (feedback.isNotEmpty()) {
        Spacer(Modifier.height(5.dp))
        Text(feedback, color = PulseColors.Accent, fontSize = 11.sp)
    }
}

@Composable
private fun StatRecap(title: String, value: String, modifier: Modifier) {
    Column(modifier) {
        Text(title, color = PulseColors.Muted, fontSize = 9.sp,
            fontWeight = FontWeight.Bold)
        Text(value, color = PulseColors.Foreground,
            fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
    }
}
