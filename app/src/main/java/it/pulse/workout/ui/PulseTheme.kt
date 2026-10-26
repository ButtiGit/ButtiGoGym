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

/** Fixed monochrome identity: black surfaces, white actions, no light-mode override. */
private val DarkPalette = PulsePalette(
    Color.Black, Color(0xFF111111), Color(0xFF202020),
    Color(0xFF242424), Color.White, Color(0xFFE4E4E4),
    Color(0xFFBDBDBD), Color(0xFFF5F5F5), Color(0xFFAAAAAA),
    Color(0xFF363636), Color.Black
)
private val LightPalette = DarkPalette

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
    val palette = DarkPalette
    val action = palette.accent
    val onAction = palette.onAccent
    val scheme = darkColorScheme(
        primary = action,
        onPrimary = onAction,
        primaryContainer = palette.softGreen,
        onPrimaryContainer = palette.foreground,
        secondary = action,
        onSecondary = onAction,
        secondaryContainer = palette.softGreen,
        onSecondaryContainer = palette.foreground,
        tertiary = palette.accentDeep,
        tertiaryContainer = palette.softGreen,
        onTertiaryContainer = palette.foreground,
        surfaceContainer = palette.surface,
        surfaceContainerHigh = palette.raised,
        surfaceContainerHighest = palette.raised,
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
