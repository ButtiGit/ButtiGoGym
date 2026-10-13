package it.pulse.workout.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color

private data class PulsePalette(
    val background: Color, val surface: Color, val raised: Color,
    val softGreen: Color, val accent: Color, val accentDeep: Color,
    val rose: Color, val foreground: Color, val muted: Color,
    val border: Color, val onAccent: Color
)

/** Light-mode identity: #106119. Dark mode retains a contrasting, recognizable green. */
private val LightPalette = PulsePalette(
    Color(0xFFF6F8F6), Color.White, Color(0xFFF1F5F2),
    Color(0xFFE9F3EA), Color(0xFF106119), Color(0xFF083C15),
    Color(0xFFF1A6B7), Color(0xFF1A2920), Color(0xFF738276),
    Color(0xFFE5ECE6), Color.White
)
private val DarkPalette = PulsePalette(
    Color(0xFF0D1510), Color(0xFF17211A), Color(0xFF222D25),
    Color(0xFF203C27), Color(0xFF2B8643), Color(0xFF8FDCA0),
    Color(0xFFE5A2B2), Color(0xFFE6F0E8), Color(0xFFA4B7A8),
    Color(0xFF34483A), Color.White
)

private val LocalPulsePalette = compositionLocalOf { LightPalette }

/** Existing screen colors remain source compatible but now follow the active theme. */
object PulseColors {
    val Background: Color @Composable get() = LocalPulsePalette.current.background
    val Surface: Color @Composable get() = LocalPulsePalette.current.surface
    val Raised: Color @Composable get() = LocalPulsePalette.current.raised
    val SoftGreen: Color @Composable get() = LocalPulsePalette.current.softGreen
    val Accent: Color @Composable get() = LocalPulsePalette.current.accent
    val AccentDeep: Color @Composable get() = LocalPulsePalette.current.accentDeep
    val Rose: Color @Composable get() = LocalPulsePalette.current.rose
    val Foreground: Color @Composable get() = LocalPulsePalette.current.foreground
    val Muted: Color @Composable get() = LocalPulsePalette.current.muted
    val Border: Color @Composable get() = LocalPulsePalette.current.border
    val DarkInk: Color @Composable get() = LocalPulsePalette.current.onAccent
}


/** One typographic scale instead of a different unreadable micro-font on each page. */
private val pulseTypography = Typography(
    headlineMedium = TextStyle(fontSize = 30.sp, lineHeight = 36.sp,
        fontWeight = FontWeight.ExtraBold),
    headlineSmall = TextStyle(fontSize = 26.sp, lineHeight = 32.sp,
        fontWeight = FontWeight.ExtraBold),
    titleLarge = TextStyle(fontSize = 22.sp, lineHeight = 28.sp,
        fontWeight = FontWeight.Bold),
    titleMedium = TextStyle(fontSize = 18.sp, lineHeight = 24.sp,
        fontWeight = FontWeight.Bold),
    titleSmall = TextStyle(fontSize = 16.sp, lineHeight = 22.sp,
        fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 21.sp),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 18.sp),
    labelLarge = TextStyle(fontSize = 14.sp, lineHeight = 20.sp,
        fontWeight = FontWeight.SemiBold),
    labelMedium = TextStyle(fontSize = 12.sp, lineHeight = 17.sp,
        fontWeight = FontWeight.SemiBold),
    labelSmall = TextStyle(fontSize = 11.sp, lineHeight = 16.sp,
        fontWeight = FontWeight.Bold)
)

private val pulseShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun PulseTheme(darkTheme: Boolean = false, content: @Composable () -> Unit) {
    val palette = if (darkTheme) DarkPalette else LightPalette
    val scheme = if (darkTheme) darkColorScheme(
        primary = palette.accent,
        onPrimary = palette.onAccent,
        primaryContainer = palette.softGreen,
        onPrimaryContainer = palette.foreground,
        secondary = palette.accent,
        onSecondary = palette.onAccent,
        background = palette.background,
        onBackground = palette.foreground,
        surface = palette.surface,
        onSurface = palette.foreground,
        surfaceVariant = palette.raised,
        onSurfaceVariant = palette.muted,
        outline = palette.border,
        outlineVariant = palette.border
    ) else lightColorScheme(
        primary = palette.accent,
        onPrimary = palette.onAccent,
        primaryContainer = palette.softGreen,
        onPrimaryContainer = palette.accent,
        secondary = palette.accent,
        onSecondary = palette.onAccent,
        background = palette.background,
        onBackground = palette.foreground,
        surface = palette.surface,
        onSurface = palette.foreground,
        surfaceVariant = palette.raised,
        onSurfaceVariant = palette.muted,
        outline = palette.border,
        outlineVariant = palette.border
    )
    CompositionLocalProvider(LocalPulsePalette provides palette) {
        MaterialTheme(colorScheme = scheme, typography = pulseTypography,
            shapes = pulseShapes, content = content)
    }
}
