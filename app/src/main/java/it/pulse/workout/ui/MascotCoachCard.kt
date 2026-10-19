package it.pulse.workout.ui

import android.speech.tts.TextToSpeech
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import it.pulse.workout.R
import it.pulse.workout.data.CoachPose
import it.pulse.workout.data.MascotAssetPack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * La mascotte pone domande collegate a dati e comandi reali del workout.
 * Le immagini sono un pack locale facoltativo: fallback al logo esistente.
 */
@Composable
fun MascotCoachCard(
    message: String,
    small: Boolean = false,
    pose: CoachPose = CoachPose.HOME,
    title: String = "IL TUO COACH",
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    selectedRir: Int? = null,
    onRir: ((Int) -> Unit)? = null,
    showImport: Boolean = false
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var refresh by remember { mutableIntStateOf(0) }
    var importing by remember { mutableStateOf(false) }
    var importStatus by remember { mutableStateOf<String?>(null) }
    val imageFile = remember(context, pose, refresh) { MascotAssetPack.imageFor(context, pose) }
    val importPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) scope.launch {
            importing = true
            val outcome = runCatching {
                withContext(Dispatchers.IO) { MascotAssetPack.importZip(context, uri) }
            }
            importing = false
            outcome.onSuccess {
                refresh++
                importStatus = "$it pose installate. Il coach le userà nelle prossime schermate."
            }.onFailure {
                importStatus = "Importazione non riuscita: ${it.message.orEmpty()}"
            }
        }
    }

    // Voce solo su richiesta: mai un messaggio ogni secondo durante il recupero.
    var readyToSpeak by remember { mutableStateOf(false) }
    var voiceEngine by remember { mutableStateOf<TextToSpeech?>(null) }
    DisposableEffect(context) {
        val engine = TextToSpeech(context.applicationContext) { status ->
            readyToSpeak = status == TextToSpeech.SUCCESS
        }
        voiceEngine = engine
        onDispose {
            engine.stop()
            engine.shutdown()
            voiceEngine = null
        }
    }
    Surface(
        modifier = Modifier.fillMaxWidth().semantics {
            contentDescription = "Coach ButtiGoGym: $title. $message"
        },
        color = PulseColors.Surface,
        shape = RoundedCornerShape(22.dp),
        border = BorderStroke(1.dp, PulseColors.Border)
    ) {
        Column(Modifier.padding(if (small) 10.dp else 14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                val dy = with(LocalDensity.current) { pose.panY.dp.toPx() }
                Box(
                    Modifier.width(if (small) 90.dp else 106.dp)
                        .height(if (small) 116.dp else 134.dp)
                        .clip(RoundedCornerShape(17.dp))
                        .background(PulseColors.SoftGreen),
                    contentAlignment = Alignment.Center
                ) {
                    if (imageFile != null) {
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(imageFile)
                                .memoryCacheKey("${imageFile.absolutePath}:${imageFile.lastModified()}")
                                .build(),
                            contentDescription = "Mascotte nella posa ${pose.name.lowercase(Locale.ITALIAN)}",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize().graphicsLayer {
                                scaleX = pose.zoom
                                scaleY = pose.zoom
                                translationY = dy
                            }
                        )
                    } else {
                        Image(
                            painter = painterResource(R.drawable.buttigogym_mascot),
                            contentDescription = "Mascotte ButtiGoGym",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
                Column(Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(title, modifier = Modifier.weight(1f),
                            color = PulseColors.Accent,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = if (small) 11.sp else 12.sp)
                        IconButton(
                            onClick = {
                                runCatching {
                                    voiceEngine?.apply {
                                        language = Locale.ITALIAN
                                        speak(message, TextToSpeech.QUEUE_FLUSH, null, "bg-coach")
                                    }
                                }
                            },
                            modifier = Modifier.size(38.dp),
                            enabled = readyToSpeak
                        ) {
                            Icon(Icons.Default.RecordVoiceOver,
                                contentDescription = "Ascolta il coach",
                                tint = PulseColors.Accent,
                                modifier = Modifier.size(20.dp))
                        }
                    }
                    Text(message, color = PulseColors.Foreground,
                        fontSize = if (small) 12.sp else 13.sp,
                        lineHeight = if (small) 17.sp else 19.sp)
                }
            }
            if (onRir != null) {
                Text("Come ti è sembrata la serie? (facoltativo)",
                    color = PulseColors.Muted, fontSize = 11.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp),
                    modifier = Modifier.fillMaxWidth()) {
                    listOf(0 to "Dura", 2 to "Giusta", 4 to "Facile").forEach { (rir, label) ->
                        FilterChip(
                            selected = selectedRir == rir,
                            onClick = { onRir(rir) },
                            label = { Text(label, fontSize = 11.sp, maxLines = 1) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
            if (actionLabel != null && onAction != null) {
                FilledTonalButton(
                    onClick = onAction,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = PulseColors.SoftGreen,
                        contentColor = PulseColors.Accent
                    ),
                    shape = RoundedCornerShape(14.dp)
                ) { Text(actionLabel, fontWeight = FontWeight.Bold, fontSize = 12.sp) }
            }
            if (showImport) {
                TextButton(
                    onClick = { importPicker.launch(arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream")) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !importing
                ) {
                    Text(
                        when {
                            importing -> "Sto importando le pose…"
                            MascotAssetPack.hasPack(context) -> "Cambia pacchetto mascotte"
                            else -> "Carica le pose della mascotte (ZIP)"
                        },
                        color = PulseColors.Accent, fontSize = 12.sp
                    )
                }
                importStatus?.let {
                    Text(it, color = PulseColors.Muted, fontSize = 11.sp)
                }
            }
        }
    }
}
