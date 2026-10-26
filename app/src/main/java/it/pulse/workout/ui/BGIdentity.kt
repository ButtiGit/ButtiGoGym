package it.pulse.workout.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Static ornament, finite entrance motion: no distracting infinite animation. */
@Composable
fun BGIdentityCard(eyebrow: String, title: String, subtitle: String, emblem: String) {
    val reveal = remember { Animatable(0f) }
    LaunchedEffect(Unit) { reveal.animateTo(1f, tween(BGMotion.duration(420))) }
    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp))
        .background(Brush.linearGradient(listOf(Color(0xFF181818), Color(0xFF242424), Color(0xFF303030))))
        .graphicsLayer { alpha = reveal.value; translationY = (1f - reveal.value) * 12.dp.toPx() }) {
        Canvas(Modifier.matchParentSize().clearAndSetSemantics {}) {
            val center = Offset(size.width * .97f, size.height * .15f)
            for (i in 1..4) drawCircle(Color.White.copy(alpha = .07f),
                radius = (30 + i * 24).dp.toPx(), center = center, style = Stroke(1.dp.toPx()))
        }
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(eyebrow, modifier = Modifier.weight(1f), color = Color(0xFFBDBDBD),
                    style = MaterialTheme.typography.labelSmall, letterSpacing = 1.3.sp)
                Text(emblem, color = Color.White, fontSize = 24.sp,
                    modifier = Modifier.clearAndSetSemantics {})
            }
            Text(title, color = Color.White, style = MaterialTheme.typography.titleLarge)
            Text(subtitle, color = Color(0xFFCCCCCC), style = MaterialTheme.typography.bodyMedium)

        }
    }
}

@Composable
fun BGRankMedallion(symbol: String, tint: Color) {
    Surface(shape = RoundedCornerShape(20.dp),
        color = tint.copy(alpha = .12f), border = BorderStroke(1.dp, tint.copy(alpha = .5f))) {
        Box(Modifier.size(58.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.matchParentSize().padding(5.dp).clearAndSetSemantics {}) {
                drawCircle(tint.copy(alpha = .25f), style = Stroke(1.dp.toPx()))
            }
            Text(symbol, fontSize = 26.sp, fontWeight = FontWeight.Black,
                color = PulseColors.Foreground, modifier = Modifier.clearAndSetSemantics {})
        }
    }
}

/** A short sparkle burst on a saved set; never blocks controls or loops. */
@Composable
fun BGSetSuccess() {
    val burst = remember { Animatable(0f) }
    LaunchedEffect(Unit) { burst.animateTo(1f, tween(BGMotion.duration(850))) }
    Surface(color = PulseColors.SoftGreen, shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 6.dp)) {
        Box {
            val tint = PulseColors.Accent
            Canvas(Modifier.matchParentSize().clearAndSetSemantics {}) {
                val t = burst.value
                repeat(12) { i ->
                    val x = size.width * (i + .5f) / 12f
                    val y = size.height * .5f + kotlin.math.sin(i.toFloat() * 2f) * t * size.height * .45f
                    drawCircle(tint.copy(alpha = (1f - t) * .6f),
                        radius = (2f + i % 3).dp.toPx(), center = Offset(x, y))
                }
            }
            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("✓", color = tint, fontSize = 24.sp, fontWeight = FontWeight.Black)
                Column {
                    Text("Serie registrata!", color = PulseColors.Foreground,
                        style = MaterialTheme.typography.titleSmall)
                    Text("Un altro passo avanti.", color = PulseColors.Accent,
                        style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}
