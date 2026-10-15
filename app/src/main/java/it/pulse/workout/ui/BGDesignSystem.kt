package it.pulse.workout.ui

import android.animation.ValueAnimator
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Shared visual and interaction scale. All actionable surfaces are at least 48dp. */
object BGLayout {
    val pageHorizontal: Dp = 20.dp
    val controlHeight: Dp = 52.dp
    val minTouch: Dp = 48.dp
    val cardRadius: Dp = 22.dp
    val contentGap: Dp = 14.dp
}

/** Obeys the Android "remove animations" system preference (API 26+). */
object BGMotion {
    fun duration(normalMillis: Int): Int =
        if (ValueAnimator.areAnimatorsEnabled()) normalMillis else 0
}

@Composable
fun BGCard(
    modifier: Modifier = Modifier,
    padding: Dp = 18.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(modifier = modifier.fillMaxWidth(),
        color = PulseColors.Surface,
        shape = RoundedCornerShape(BGLayout.cardRadius),
        border = BorderStroke(1.dp, PulseColors.Border)) {
        Column(Modifier.fillMaxWidth().padding(padding), content = content)
    }
}

/** Exactly the same back affordance, baseline and type hierarchy for subpages. */
@Composable
fun BGBackHeader(
    section: String,
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    trailing: @Composable RowScope.() -> Unit = {}
) {
    BackHandler(onBack = onBack)
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack, modifier = Modifier.size(BGLayout.minTouch)) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Torna indietro",
                tint = PulseColors.Foreground)
        }
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(section.uppercase(), color = PulseColors.Accent,
                style = MaterialTheme.typography.labelMedium,
                letterSpacing = 0.8.sp)
            Text(title, color = PulseColors.Foreground,
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.semantics { heading() },
                maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        trailing()
    }
}

@Composable
fun BGPrimaryButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null
) {
    Button(onClick = onClick, enabled = enabled,
        modifier = modifier.fillMaxWidth().heightIn(min = BGLayout.controlHeight),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = PulseColors.Accent,
            contentColor = PulseColors.DarkInk
        )) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(19.dp))
            Spacer(Modifier.width(9.dp))
        }
        Text(label, style = MaterialTheme.typography.labelLarge,
            maxLines = 2)
    }
}

@Composable
fun BGEmptyState(
    title: String,
    description: String,
    modifier: Modifier = Modifier,
    symbol: ImageVector = Icons.Default.Info,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    BGCard(modifier, padding = 20.dp) {
        Surface(color = PulseColors.SoftGreen,
            shape = RoundedCornerShape(16.dp)) {
            Icon(symbol, contentDescription = null, tint = PulseColors.Accent,
                modifier = Modifier.padding(14.dp).size(24.dp))
        }
        Spacer(Modifier.height(14.dp))
        Text(title, style = MaterialTheme.typography.titleMedium,
            color = PulseColors.Foreground, modifier = Modifier.semantics { heading() })
        Spacer(Modifier.height(6.dp))
        Text(description, style = MaterialTheme.typography.bodyMedium,
            color = PulseColors.Muted)
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(15.dp))
            BGPrimaryButton(actionLabel, onAction)
        }
    }
}
