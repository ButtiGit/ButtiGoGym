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
        .background(Brush.linearGradient(listOf(Color(0xFF083C15), Color(0xFF106119), Color(0xFF26763B))))
        .graphicsLayer { alpha = reveal.value; translationY = (1f - reveal.value) * 12.dp.toPx() }) {
        Canvas(Modifier.matchParentSize().clearAndSetSemantics {}) {
            val center = Offset(size.width * .97f, size.height * .15f)
            for (i in 1..4) drawCircle(Color.White.copy(alpha = .07f),
                radius = (30 + i * 24).dp.toPx(), center = center, style = Stroke(1.dp.toPx()))
        }
        Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(eyebrow, modifier = Modifier.weight(1f), color = Color(0xFFC0E5B9),
                    style = MaterialTheme.typography.labelSmall, letterSpacing = 1.3.sp)
                Text(emblem, color = Color(0xFFE1F7BF), fontSize = 24.sp,
                    modifier = Modifier.clearAndSetSemantics {})
            }
            Text(title, color = Color.White, style = MaterialTheme.typography.headlineSmall)
            Text(subtitle, color = Color(0xFFE0EEDD), style = MaterialTheme.typography.bodyMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.clearAndSetSemantics {}) {
                repeat(7) { index ->
                    Box(Modifier.width(if (index == 0) 28.dp else 8.dp).height(4.dp)
                        .background(Color(0xFFD3EE9F).copy(alpha = if (index == 0) 1f else .35f), CircleShape))
                }
            }
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
