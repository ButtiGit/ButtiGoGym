package it.pulse.workout.ui

import android.speech.tts.TextToSpeech
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import it.pulse.workout.data.CoachPose
import it.pulse.workout.data.MascotAssetPack
import java.util.Locale

/** Unica fonte per tutte le pose: risorse incorporate, senza import ZIP. */
@Composable
fun MascotPoseImage(
    pose: CoachPose,
    modifier: Modifier = Modifier,
    portrait: Boolean = false
) {
    val context = LocalContext.current
    val drawable = remember(context, pose) { MascotAssetPack.bundledDrawable(context, pose) }
    val translation = with(LocalDensity.current) { pose.panY.dp.toPx() }
    Image(
        painter = painterResource(drawable),
        contentDescription = "Mascotte: ${pose.name.lowercase(Locale.ITALIAN)}",
        contentScale = if (portrait) ContentScale.Crop else ContentScale.Fit,
        modifier = modifier.clip(RoundedCornerShape(14.dp)).graphicsLayer {
            scaleX = if (portrait) pose.zoom else 1f
            scaleY = if (portrait) pose.zoom else 1f
            translationY = if (portrait) translation else 0f
        }
    )
}

/** Messaggio breve, immagine caratteriale e una sola azione collegata alla sessione. */
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
    var voiceReady by remember { mutableStateOf(false) }
    var voice by remember { mutableStateOf<TextToSpeech?>(null) }
    DisposableEffect(context) {
        val engine = TextToSpeech(context.applicationContext) { voiceReady = it == TextToSpeech.SUCCESS }
        voice = engine
        onDispose { engine.stop(); engine.shutdown(); voice = null }
    }
    val mood = when (pose) {
        CoachPose.DONE -> "🎉"
        CoachPose.REST -> "😴"
        CoachPose.ARMS, CoachPose.FOREARMS, CoachPose.CHEST, CoachPose.BACK,
        CoachPose.ABS, CoachPose.LEGS, CoachPose.SHOULDERS -> "💪"
        else -> "✨"
    }
    Surface(
        modifier = Modifier.fillMaxWidth().semantics {
            contentDescription = "Coach: $title. $message"
        },
        color = PulseColors.Surface,
        border = BorderStroke(1.dp, PulseColors.Border),
        shape = RoundedCornerShape(19.dp)
    ) {
        Column(Modifier.padding(if (small) 10.dp else 12.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    Modifier.width(if (small) 73.dp else 90.dp)
                        .height(if (small) 91.dp else 110.dp)
                        .background(PulseColors.SoftGreen, RoundedCornerShape(14.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    MascotPoseImage(pose, Modifier.fillMaxSize(), portrait = false)
                }
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("$mood  $title", Modifier.weight(1f),
                            color = PulseColors.Accent, fontSize = 12.sp,
                            fontWeight = FontWeight.Bold, maxLines = 1)
                        IconButton(
                            onClick = {
                                voice?.apply {
                                    language = Locale.ITALIAN
                                    speak(message, TextToSpeech.QUEUE_FLUSH, null, "mascot-coach")
                                }
                            },
                            modifier = Modifier.size(36.dp), enabled = voiceReady
                        ) {
                            Icon(Icons.Default.VolumeUp, "Ascolta il coach",
                                tint = PulseColors.Accent, modifier = Modifier.size(19.dp))
                        }
                    }
                    Text(message, color = PulseColors.Foreground,
                        fontSize = if (small) 12.sp else 13.sp,
                        lineHeight = if (small) 17.sp else 19.sp)
                }
            }
            if (onRir != null) {
                Text("Come ti è sembrata?", color = PulseColors.Muted, fontSize = 12.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(0 to "🥵 Dura", 2 to "👌 Giusta", 4 to "⚡ Facile").forEach { (rir, label) ->
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
                OutlinedButton(onClick = onAction, modifier = Modifier.fillMaxWidth(),
                    border = BorderStroke(1.dp, PulseColors.Border),
                    shape = RoundedCornerShape(12.dp)) {
                    Text(actionLabel, fontWeight = FontWeight.SemiBold,
                        color = PulseColors.Accent, fontSize = 12.sp)
                }
            }
        }
    }
}
