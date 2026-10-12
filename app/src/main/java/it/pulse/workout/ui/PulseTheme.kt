package it.pulse.workout.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

object PulseColors {
    val Background = Color(0xFF0C1110)
    val Surface = Color(0xFF17201C)
    val Raised = Color(0xFF202A24)
    val Accent = Color(0xFFC9FB67)
    val Foreground = Color(0xFFF3F7F1)
    val Muted = Color(0xFF95A49C)
    val Border = Color(0xFF303C35)
    val DarkInk = Color(0xFF12200D)
}

private val pulseScheme = darkColorScheme(
    primary = PulseColors.Accent,
    onPrimary = PulseColors.DarkInk,
    primaryContainer = PulseColors.Raised,
    onPrimaryContainer = PulseColors.Accent,
    secondary = PulseColors.Muted,
    background = PulseColors.Background,
    onBackground = PulseColors.Foreground,
    surface = PulseColors.Surface,
    onSurface = PulseColors.Foreground,
    surfaceVariant = PulseColors.Raised,
    onSurfaceVariant = PulseColors.Muted,
    outline = PulseColors.Border
)

@Composable
fun PulseTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = pulseScheme,
        typography = Typography(),
        content = content
    )
}
