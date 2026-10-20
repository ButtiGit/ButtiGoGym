package it.pulse.workout.ui

import android.speech.tts.TextToSpeech
import android.view.accessibility.AccessibilityManager
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import it.pulse.workout.data.CoachPose
import it.pulse.workout.data.MascotAssetPack
import kotlinx.coroutines.delay
import java.text.BreakIterator
import java.util.Locale

/** Top-anchored framing preserves face and hands instead of zooming around the image center. */
@Composable
fun MascotPoseImage(pose: CoachPose, modifier: Modifier = Modifier, portrait: Boolean = false) {
    val context = LocalContext.current
    val drawable = remember(context, pose) { MascotAssetPack.bundledDrawable(context, pose) }
    val painter = painterResource(drawable)
    if (!portrait) {
        Image(painter, "Mascotte", modifier, contentScale = ContentScale.Fit)
        return
    }
    Canvas(modifier.clearAndSetSemantics { contentDescription = "Mascotte" }) {
        val visibleFraction = if (pose == CoachPose.LEGS) .86f else .63f
        val native = painter.intrinsicSize
        val scale = minOf(size.width / native.width, size.height / (native.height * visibleFraction))
        val width = native.width * scale
        val height = native.height * scale
        clipRect {
            translate((size.width - width) / 2f, 0f) {
                with(painter) { draw(Size(width, height)) }
            }
        }
    }
}

/** Visual-novel dialogue, with one complete sentence exposed to accessibility services. */
@Composable
fun MascotCoachCard(
    message: String,
    small: Boolean = false,
    pose: CoachPose = CoachPose.HOME,
    title: String = "Butty",
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    selectedRir: Int? = null,
    onRir: ((Int) -> Unit)? = null,
    showImport: Boolean = false
) {
    val context = LocalContext.current
    val accessibility = remember(context) { context.getSystemService(AccessibilityManager::class.java) }
    val animate = !small && BGMotion.duration(1) > 0 && accessibility?.isTouchExplorationEnabled != true
    var visible by rememberSaveable(message) { mutableIntStateOf(if (animate) 0 else message.length) }
    val boundaries = remember(message) {
        val iterator = BreakIterator.getCharacterInstance(Locale.ITALIAN).apply { setText(message) }
        buildList { var end = iterator.next(); while (end != BreakIterator.DONE) {
            add(end); end = iterator.next()
        } }
    }
    LaunchedEffect(message, animate) {
        if (!animate) visible = message.length
        else for (end in boundaries) {
            if (visible >= message.length) break
            if (end > visible) { delay(24); visible = end }
        }
    }
    var voiceOpen by remember { mutableStateOf(false) }
    if (small) {
        Surface(color = PulseColors.SoftGreen, shape = RoundedCornerShape(20.dp)) {
            Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MascotPoseImage(pose, Modifier.width(68.dp).height(80.dp), portrait = true)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(title, color = PulseColors.Accent, style = MaterialTheme.typography.labelMedium)
                    Text(message, color = PulseColors.Foreground, style = MaterialTheme.typography.bodyMedium)
                }
                IconButton(onClick = { voiceOpen = true }, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Default.VolumeUp, "Ascolta il dialogo", tint = PulseColors.Accent)
                }
            }
        }
        if (voiceOpen) CoachVoiceDialog(message, onDismiss = { voiceOpen = false })
        return
    }
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        val floating = rememberInfiniteTransition(label = "Butty fluttua")
        val wave by floating.animateFloat(
            initialValue = 0f, targetValue = -4f,
            animationSpec = infiniteRepeatable(
                animation = tween(1900, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ), label = "Movimento mascotte"
        )
        Box(Modifier.fillMaxWidth().height(if (small) 160.dp else 220.dp)
            .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
            .background(Brush.verticalGradient(listOf(
                PulseColors.Rose.copy(alpha = .22f), PulseColors.Raised
            ))), contentAlignment = Alignment.BottomCenter) {
            AnimatedContent(
                targetState = pose,
                transitionSpec = {
                    fadeIn(tween(BGMotion.duration(240))) togetherWith
                        fadeOut(tween(BGMotion.duration(180)))
                },
                label = "Posa di Butty"
            ) { currentPose ->
                MascotPoseImage(
                    currentPose,
                    Modifier.fillMaxSize().padding(top = 8.dp)
                        .offset(y = if (BGMotion.duration(1) > 0) wave.dp else 0.dp),
                    portrait = true
                )
            }
        }
        Surface(color = PulseColors.Surface, shape = RoundedCornerShape(
            bottomStart = 24.dp, bottomEnd = 24.dp)) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Butty", color = PulseColors.Accent, fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    IconButton(onClick = { voiceOpen = true }, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.Default.VolumeUp, "Ascolta il dialogo", modifier = Modifier.size(20.dp),
                            tint = PulseColors.Muted)
                    }
                }
                Box(Modifier.fillMaxWidth().clickable(
                    enabled = visible < message.length, onClickLabel = "Mostra tutto il dialogo"
                ) { visible = message.length }.clearAndSetSemantics {
                    contentDescription = "Butty: $message"
                }) {
                    // Reserve the complete sentence's height to keep inputs still while typing.
                    Text(message, color = androidx.compose.ui.graphics.Color.Transparent,
                        style = MaterialTheme.typography.bodyLarge)
                    Text(message.take(visible), color = PulseColors.Foreground,
                        style = MaterialTheme.typography.bodyLarge)
                }
                if (onRir != null) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(0 to "Dura", 2 to "Giusta", 4 to "Facile").forEach { (value, label) ->
                        FilterChip(selectedRir == value, { onRir(value) }, label = { Text(label) })
                    }
                }
                if (onAction != null && actionLabel != null)
                    TextButton(onClick = onAction) { Text(actionLabel) }
            }
        }
    }
    if (voiceOpen) CoachVoiceDialog(message, onDismiss = { voiceOpen = false })
}

@Composable
private fun CoachVoiceDialog(message: String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var engine by remember { mutableStateOf<TextToSpeech?>(null) }
    var ready by remember { mutableStateOf(false) }
    DisposableEffect(context) {
        val tts = TextToSpeech(context.applicationContext) { ready = it == TextToSpeech.SUCCESS }
        engine = tts
        onDispose { tts.stop(); tts.shutdown() }
    }
    LaunchedEffect(ready) {
        if (ready) engine?.apply {
            language = Locale.ITALIAN
            speak(message, TextToSpeech.QUEUE_FLUSH, null, "butty-dialogue")
        }
    }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Butty") },
        text = { Text(message) }, confirmButton = {
            TextButton(onClick = onDismiss) { Text("Chiudi") }
        })
}
