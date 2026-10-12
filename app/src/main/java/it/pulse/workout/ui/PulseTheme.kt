package it.pulse.workout.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** Palette chiara: bianco, superfici neutre e verde #106119. */
object PulseColors {
    val Background = Color(0xFFF6F8F6)
    val Surface = Color(0xFFFFFFFF)
    val Raised = Color(0xFFF1F5F2)
    val SoftGreen = Color(0xFFE9F3EA)
    val Accent = Color(0xFF106119)
    val Foreground = Color(0xFF1A2920)
    val Muted = Color(0xFF738276)
    val Border = Color(0xFFE5ECE6)
    // Il testo sopra gli sfondi verde pieno deve essere bianco.
    val DarkInk = Color(0xFFFFFFFF)
}

private val pulseScheme = lightColorScheme(
    primary = PulseColors.Accent,
    onPrimary = Color.White,
    primaryContainer = PulseColors.SoftGreen,
    onPrimaryContainer = PulseColors.Accent,
    secondary = PulseColors.Accent,
    onSecondary = Color.White,
    background = PulseColors.Background,
    onBackground = PulseColors.Foreground,
    surface = PulseColors.Surface,
    onSurface = PulseColors.Foreground,
    surfaceVariant = PulseColors.Raised,
    onSurfaceVariant = PulseColors.Muted,
    outline = PulseColors.Border,
    outlineVariant = PulseColors.Border
)

@Composable
fun PulseTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = pulseScheme,
        typography = Typography(),
        content = content
    )
}
