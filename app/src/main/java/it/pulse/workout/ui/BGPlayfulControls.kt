package it.pulse.workout.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import java.util.Locale
import kotlin.math.roundToInt

/** A fast touch-first value ruler. The exact text input remains available. */
@Composable
fun BGValueScrubber(
    raw: String, onValueChange: (String) -> Unit, unit: String,
    min: Float, max: Float, step: Float, decimal: Boolean = false
) {
    require(max > min && step > 0f)
    val haptics = LocalHapticFeedback.current
    val parsed = raw.trim().replace(',', '.').toFloatOrNull()
    val current = (parsed ?: min).coerceIn(min, max)
    fun setValue(next: Float) {
        val count = ((next.coerceIn(min, max) - min) / step).roundToInt()
        val rounded = (min + count * step).coerceIn(min, max)
        val text = if (decimal) String.format(Locale.US, "%.1f", rounded)
            .trimEnd('0').trimEnd('.') else rounded.roundToInt().toString()
        if (text != raw.replace(',', '.')) {
            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onValueChange(text)
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            FilledTonalIconButton(onClick = { setValue(current - step) },
                modifier = Modifier.size(48.dp), shape = CircleShape,
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = PulseColors.SoftGreen,
                    contentColor = PulseColors.Accent)
            ) { Icon(Icons.Default.Remove, "Diminuisci") }
            Slider(value = current, onValueChange = ::setValue,
                valueRange = min..max, modifier = Modifier.weight(1f),
                colors = SliderDefaults.colors(
                    thumbColor = PulseColors.Accent,
                    activeTrackColor = PulseColors.Accent,
                    inactiveTrackColor = PulseColors.SoftGreen))
            FilledTonalIconButton(onClick = { setValue(current + step) },
                modifier = Modifier.size(48.dp), shape = CircleShape,
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = PulseColors.SoftGreen,
                    contentColor = PulseColors.Accent)
            ) { Icon(Icons.Default.Add, "Aumenta") }
        }
        Text("Trascina oppure usa + / − · " + unit,
            color = PulseColors.Muted,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.align(Alignment.CenterHorizontally))
    }
}

@Composable
fun BGAnimatedProgress(progress: Float, modifier: Modifier = Modifier) {
    val animated by animateFloatAsState(progress.coerceIn(0f, 1f),
        animationSpec = tween(BGMotion.duration(550)),
        label = "Avanzamento obiettivo")
    LinearProgressIndicator(progress = { animated },
        modifier = modifier.fillMaxWidth().height(8.dp),
        color = PulseColors.Accent, trackColor = PulseColors.SoftGreen)
}

@Composable
fun BGBreathingTimer(remaining: Long, total: Int) {
    val ratio = (remaining.toFloat() / total.coerceAtLeast(1)).coerceIn(0f, 1f)
    val progress by animateFloatAsState(ratio,
        animationSpec = tween(BGMotion.duration(500)), label = "Recupero")
    Box(Modifier.fillMaxWidth().padding(vertical = 12.dp),
        contentAlignment = Alignment.Center) {
        Surface(shape = CircleShape, color = PulseColors.SoftGreen,
            modifier = Modifier.size(164.dp)) {}
        CircularProgressIndicator(progress = { progress },
            modifier = Modifier.size(184.dp), color = PulseColors.Accent,
            trackColor = PulseColors.Border, strokeWidth = 7.dp)
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(formatDuration(remaining), color = PulseColors.Foreground,
                style = MaterialTheme.typography.headlineMedium)
            Text("Recupero", color = PulseColors.Accent,
                style = MaterialTheme.typography.labelMedium)
        }
    }
}
