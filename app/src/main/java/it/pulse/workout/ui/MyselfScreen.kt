package it.pulse.workout.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import it.pulse.workout.data.*
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val recoveryColor = Color(0xFFFFC987)
private val trainedColor = Color(0xFF106119)
private val readyColor = Color(0xFFA8D9B0)
private val neverColor = Color(0xFFE1E8E2)

/** La sagoma è originale e disegnata con Canvas; nessun asset ripreso da workout.cool. */
@Composable
fun MyselfScreen(
    history: List<SessionRecord>,
    active: WorkoutDraft?
) {
    var selected by remember { mutableStateOf("chest") }
    var back by remember { mutableStateOf(false) }
    var clock by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            clock = System.currentTimeMillis()
            kotlinx.coroutines.delay(60_000L)
        }
    }
    val muscles = remember(history, clock) { MuscleAnalytics.evaluate(history, clock) }
    val muscle = muscles.first { it.group.id == selected }
    val totalXp = muscles.sumOf { it.xp }
    val scaledXp = totalXp / 4
    val overallLevel = MuscleAnalytics.levelFromXp(scaledXp)
    val base = MuscleAnalytics.levelThreshold(overallLevel) * 4
    val next = MuscleAnalytics.levelThreshold(overallLevel + 1) * 4
    val overallProgress = ((totalXp - base).toFloat() / (next - base).coerceAtLeast(1)).coerceIn(0f,1f)
    val trained = muscles.count { it.state == MuscleRecovery.TRAINED_TODAY }
    val resting = muscles.count { it.state == MuscleRecovery.RECOVERING }
    val ready = muscles.count { it.state == MuscleRecovery.READY }
    val n = DecimalFormat("#,##0")

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(PulseColors.Background),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 22.dp, bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(15.dp)
    ) {
        item {
            Text("BUTTIGOGYM / PROFILE", color = PulseColors.Accent,
                fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.6.sp)
            Spacer(Modifier.height(8.dp))
            Text("Myself", color = PulseColors.Foreground,
                fontSize = 37.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-1.3).sp)
            Spacer(Modifier.height(4.dp))
            Text("La mappa dei tuoi allenamenti. Tocca un muscolo per esplorare i progressi.",
                fontSize = 12.sp, color = PulseColors.Muted)
        }
        item {
            MyselfCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(47.dp).background(PulseColors.SoftGreen, RoundedCornerShape(15.dp)),
                        contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Bolt, null, tint = PulseColors.Accent, modifier = Modifier.size(24.dp))
                    }
                    Spacer(Modifier.width(13.dp))
                    Column(Modifier.weight(1f)) {
                        Text("LIVELLO COMPLESSIVO", color = PulseColors.Muted,
                            fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.1.sp)
                        Text("Livello $overallLevel", color = PulseColors.Foreground,
                            fontSize = 23.sp, fontWeight = FontWeight.ExtraBold)
                    }
                    Text("${n.format(totalXp)} XP", color = PulseColors.Accent,
                        fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(15.dp))
                LevelTrack(overallProgress)
                Spacer(Modifier.height(8.dp))
                Text("${n.format((next - totalXp).coerceAtLeast(0))} XP al livello ${overallLevel + 1}",
                    color = PulseColors.Muted, fontSize = 12.sp)
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                StatusCount("Allenati oggi", trained, trainedColor, Modifier.weight(1f))
                StatusCount("In recupero", resting, recoveryColor, Modifier.weight(1f))
                StatusCount("Pronti", ready, readyColor, Modifier.weight(1f))
            }
        }
        item {
            MyselfCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Body map", color = PulseColors.Foreground,
                            fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
                        Text("Seleziona i gruppi muscolari",
                            color = PulseColors.Muted, fontSize = 11.sp)
                    }
                    Surface(color = PulseColors.Raised, shape = RoundedCornerShape(15.dp)) {
                        Row(Modifier.padding(3.dp)) {
                            listOf(false to "Fronte", true to "Retro").forEach { (side, label) ->
                                Surface(
                                    modifier = Modifier.clickable { back = side },
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (back == side) Color.White else Color.Transparent
                                ) {
                                    Text(label, modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                        color = if (back == side) PulseColors.Accent else PulseColors.Muted,
                                        fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    MuscleFigure(
                        back = back, muscles = muscles, selected = selected,
                        onSelected = { selected = it },
                        modifier = Modifier.width(260.dp).height(470.dp)
                    )
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    LegendDot("Oggi", trainedColor)
                    LegendDot("Recupero", recoveryColor)
                    LegendDot("Pronto", readyColor)
                    LegendDot("Mai", neverColor)
                }
            }
        }
        item {
            MyselfCard {
                Row(verticalAlignment = Alignment.Top) {
                    Column(Modifier.weight(1f)) {
                        Text("MUSCOLO SELEZIONATO", color = PulseColors.Muted,
                            fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
                        Spacer(Modifier.height(5.dp))
                        Text(muscle.group.label, color = PulseColors.Foreground,
                            fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
                    }
                    Surface(color = tintFor(muscle.state).copy(alpha = .34f),
                        shape = RoundedCornerShape(100.dp)) {
                        Text("LV ${muscle.level}", modifier = Modifier.padding(horizontal = 13.dp, vertical = 8.dp),
                            color = PulseColors.Foreground, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp)
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text(statusText(muscle), color = PulseColors.Accent,
                    fontWeight = FontWeight.Bold, fontSize = 13.sp)
                if (muscle.lastTrainingMillis != null) {
                    Spacer(Modifier.height(5.dp))
                    Text("Ultimo stimolo: " +
                        SimpleDateFormat("dd MMM · HH:mm", Locale.ITALIAN).format(Date(muscle.lastTrainingMillis)),
                        color = PulseColors.Muted, fontSize = 11.sp)
                }
                Spacer(Modifier.height(17.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    SmallStat("Streak", "${muscle.streakWeeks} sett.", Modifier.weight(1f))
                    SmallStat("Serie", muscle.workingSets.toString(), Modifier.weight(1f))
                    SmallStat("Volume", "${n.format(muscle.volumeKg)} kg", Modifier.weight(1f))
                }
                Spacer(Modifier.height(20.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Prossimo livello", color = PulseColors.Foreground,
                        fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
                    Spacer(Modifier.weight(1f))
                    Text("LV ${muscle.level + 1}", color = PulseColors.Accent,
                        fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
                Spacer(Modifier.height(10.dp))
                LevelTrack(muscle.progressToNext)
                Spacer(Modifier.height(9.dp))
                Text("${n.format(muscle.levelCurrentXp)} / ${n.format(muscle.levelRequiredXp)} XP",
                    color = PulseColors.Muted, fontSize = 12.sp)
                Text("Ancora ${n.format((muscle.levelRequiredXp - muscle.levelCurrentXp).coerceAtLeast(0))} XP",
                    color = PulseColors.Accent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                if (muscle.topSet != null) {
                    Spacer(Modifier.height(16.dp))
                    Text("Miglior serie registrata", color = PulseColors.Muted, fontSize = 11.sp)
                    Text(muscle.topSet, color = PulseColors.Foreground, fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold)
                }
            }
        }
        item {
            Text("Tutti i gruppi muscolari", color = PulseColors.Foreground,
                fontWeight = FontWeight.ExtraBold, fontSize = 19.sp)
            Spacer(Modifier.height(4.dp))
            Text("Apri un muscolo per vedere recupero, streak e livelli.",
                color = PulseColors.Muted, fontSize = 11.sp)
        }
        items(muscles, key = { it.group.id }) { value ->
            Surface(
                modifier = Modifier.fillMaxWidth().clickable {
                    selected = value.group.id
                    back = value.group.view == "back"
                },
                shape = RoundedCornerShape(18.dp),
                color = if (selected == value.group.id) PulseColors.SoftGreen else PulseColors.Surface,
                border = BorderStroke(1.dp, if (selected == value.group.id) PulseColors.Accent else PulseColors.Border)
            ) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(11.dp).background(tintFor(value.state), CircleShape))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(value.group.label, color = PulseColors.Foreground,
                            fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Text("Streak ${value.streakWeeks} sett. · ${shortStatus(value)}",
                            color = PulseColors.Muted, fontSize = 10.sp)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("LV ${value.level}", color = PulseColors.Accent,
                            fontWeight = FontWeight.ExtraBold, fontSize = 12.sp)
                        Spacer(Modifier.height(5.dp))
                        Box(Modifier.width(64.dp)) { LevelTrack(value.progressToNext, 4.dp) }
                    }
                }
            }
        }
        item {
            Text("Il recupero è una stima (36–72 h, secondo il gruppo muscolare e il ruolo nell’esercizio): non misura quanto sei realmente recuperato. I livelli sono punti di allenamento gamificati calcolati dalle serie registrate (kg, ripetizioni e muscoli coinvolti), non misure cliniche di forza o crescita.",
                color = PulseColors.Muted, fontSize = 10.sp, lineHeight = 15.sp)
            if (active != null) {
                Spacer(Modifier.height(8.dp))
                Text("La sessione attualmente in corso verrà conteggiata quando la salvi nello storico.",
                    color = PulseColors.Muted, fontSize = 10.sp)
            }
        }
    }
}

private fun statusText(m: MuscleProgress) = when(m.state) {
    MuscleRecovery.NEVER -> "Nessuna serie allenante registrata"
    MuscleRecovery.TRAINED_TODAY -> "Allenato oggi · circa ${m.hoursToReady} h di recupero stimate"
    MuscleRecovery.RECOVERING -> "In recupero · circa ${m.hoursToReady} h rimanenti"
    MuscleRecovery.READY -> "Pronto per essere allenato (stima)"
}
private fun shortStatus(m: MuscleProgress) = when(m.state) {
    MuscleRecovery.NEVER -> "Non allenato"
    MuscleRecovery.TRAINED_TODAY -> "Allenato oggi"
    MuscleRecovery.RECOVERING -> "Recupero ${m.hoursToReady} h"
    MuscleRecovery.READY -> "Pronto"
}
private fun tintFor(state: MuscleRecovery): Color = when(state) {
    MuscleRecovery.NEVER -> neverColor
    MuscleRecovery.TRAINED_TODAY -> trainedColor
    MuscleRecovery.RECOVERING -> recoveryColor
    MuscleRecovery.READY -> readyColor
}

@Composable
private fun MyselfCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(25.dp),
        color = Color.White, border = BorderStroke(1.dp, PulseColors.Border)) {
        Column(Modifier.padding(18.dp), content = content)
    }
}

@Composable
private fun LevelTrack(progress: Float, height: androidx.compose.ui.unit.Dp = 9.dp) {
    LinearProgressIndicator(
        progress = { progress.coerceIn(0f,1f) },
        modifier = Modifier.fillMaxWidth().height(height).clip(CircleShape),
        color = PulseColors.Accent,
        trackColor = PulseColors.SoftGreen
    )
}

@Composable
private fun SmallStat(label: String, value: String, modifier: Modifier) {
    Column(modifier.background(PulseColors.Raised, RoundedCornerShape(13.dp))
        .padding(horizontal = 8.dp, vertical = 12.dp)) {
        Text(label, color = PulseColors.Muted, fontSize = 10.sp, maxLines = 1)
        Spacer(Modifier.height(5.dp))
        Text(value, color = PulseColors.Foreground, fontWeight = FontWeight.ExtraBold,
            fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun StatusCount(label: String, count: Int, tint: Color, modifier: Modifier) {
    Column(modifier.background(Color.White, RoundedCornerShape(16.dp))
        .padding(vertical = 13.dp, horizontal = 10.dp)) {
        Box(Modifier.size(9.dp).background(tint, CircleShape))
        Spacer(Modifier.height(7.dp))
        Text("$count", color = PulseColors.Foreground, fontSize = 23.sp,
            fontWeight = FontWeight.ExtraBold)
        Text(label, color = PulseColors.Muted, fontSize = 9.sp,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
@Composable
private fun LegendDot(label: String, tint: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).background(tint, CircleShape))
        Spacer(Modifier.width(4.dp))
        Text(label, fontSize = 9.sp, color = PulseColors.Muted)
    }
}

/** Poligoni proprietari delle zone muscolari, in un sistema 260x500. */
private data class AnatomyPatch(val id: String, val points: List<Offset>)
private fun poly(id: String, vararg coords: Int) =
    AnatomyPatch(id, coords.toList().chunked(2).map { Offset(it[0].toFloat(), it[1].toFloat()) })
private fun mirror(p: AnatomyPatch) =
    AnatomyPatch(p.id, p.points.map { Offset(260f - it.x, it.y) })
private fun both(id: String, vararg coords: Int): List<AnatomyPatch> {
    val p = poly(id, *coords)
    return listOf(p, mirror(p))
}
private fun patches(back: Boolean): List<AnatomyPatch> = if (!back) buildList {
    addAll(both("traps", 109,100, 119,91, 130,105, 107,124))
    addAll(both("shoulders", 89,111, 108,119, 103,148, 84,155, 69,141, 74,121))
    addAll(both("chest", 104,128, 129,126, 127,172, 108,177, 98,154))
    addAll(both("biceps", 70,158, 89,162, 83,207, 60,207, 54,184))
    addAll(both("triceps", 89,158, 97,160, 90,201, 83,207))
    addAll(both("forearms", 58,212, 83,212, 70,276, 47,281, 40,259))
    addAll(both("obliques", 99,184, 113,188, 111,261, 99,250))
    // Addominali in porzioni distinte per evidenziare la separazione visiva.
    listOf(190,212,234).forEach { top ->
        addAll(both("abs", 115,top, 128,top, 127,top+20, 115,top+20))
    }
    addAll(both("quads", 102,281, 129,283, 126,383, 101,390, 92,339))
    addAll(both("calves", 104,396, 128,397, 125,471, 113,482, 102,446))
} else buildList {
    addAll(both("traps", 119,99, 130,111, 125,142, 101,137, 106,115))
    addAll(both("rear_delts", 93,116, 107,140, 96,160, 77,153, 70,137))
    addAll(both("triceps", 69,157, 94,163, 83,211, 61,208, 54,185))
    addAll(both("forearms", 57,211, 81,214, 70,277, 47,280, 41,255))
    addAll(both("upper_back", 107,145, 129,153, 127,210, 111,202, 98,168))
    addAll(both("lats", 107,204, 128,213, 127,269, 109,249, 98,215))
    addAll(both("lower_back", 112,254, 129,263, 128,282, 107,279))
    addAll(both("glutes", 103,286, 129,290, 129,338, 98,338, 94,315))
    addAll(both("hamstrings", 101,343, 127,343, 125,393, 105,398, 94,364))
    addAll(both("calves", 104,400, 127,400, 124,466, 113,483, 103,450))
}

private fun polygon(points: List<Offset>): Path = Path().apply {
    if (points.isNotEmpty()) {
        moveTo(points.first().x, points.first().y)
        points.drop(1).forEach { lineTo(it.x, it.y) }
        close()
    }
}

/** Point-in-polygon per la selezione di ogni area anatomica. */
private fun inside(point: Offset, points: List<Offset>): Boolean {
    var result = false
    var j = points.lastIndex
    for (i in points.indices) {
        val a = points[i]
        val b = points[j]
        if ((a.y > point.y) != (b.y > point.y) &&
            point.x < (b.x - a.x) * (point.y - a.y) / (b.y - a.y) + a.x
        ) result = !result
        j = i
    }
    return result
}

@Composable
private fun MuscleFigure(
    back: Boolean,
    muscles: List<MuscleProgress>,
    selected: String,
    onSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val areas = remember(back) { patches(back) }
    val values = remember(muscles) { muscles.associateBy { it.group.id } }
    Canvas(
        modifier.pointerInput(back) {
            detectTapGestures { tap ->
                val normalized = Offset(tap.x / size.width * 260f, tap.y / size.height * 500f)
                areas.asReversed().firstOrNull { inside(normalized, it.points) }?.let {
                    onSelected(it.id)
                }
            }
        }
    ) {
        val scaleX = size.width / 260f
        val scaleY = size.height / 500f
        with(drawContext.transform) {
            scale(scaleX, scaleY, Offset.Zero)
        }
        val silhouette = Color(0xFFF0F3F0)
        val outline = Color(0xFFD0DDD3)
        // Head and neck
        drawCircle(silhouette, 26f, Offset(130f,49f))
        drawCircle(outline, 26f, Offset(130f,49f), style = Stroke(1.5f))
        drawPath(polygon(listOf(
            Offset(118f,72f), Offset(142f,72f), Offset(145f,99f),
            Offset(115f,99f)
        )), silhouette)
        // Body background
        val bodyParts = listOf(
            poly("body", 101,101, 159,101, 188,113, 194,155, 175,226, 167,281, 175,292, 150,300, 130,286, 110,300, 85,292, 93,281, 85,226, 66,155, 72,113),
            poly("arm", 69,124, 90,150, 83,207, 71,280, 49,302, 38,291, 40,259, 54,178),
            poly("arm", 191,124, 170,150, 177,207, 189,280, 211,302, 222,291, 220,259, 206,178),
            poly("leg", 101,280, 131,286, 128,393, 125,471, 135,491, 108,496, 99,471, 97,396, 91,347),
            poly("leg", 159,280, 129,286, 132,393, 135,471, 125,491, 152,496, 161,471, 163,396, 169,347)
        )
        bodyParts.forEach {
            val path = polygon(it.points)
            drawPath(path, silhouette)
            drawPath(path, outline, style = Stroke(1.1f))
        }
        areas.forEach { area ->
            val model = values[area.id]
            val tone = tintFor(model?.state ?: MuscleRecovery.NEVER)
            val path = polygon(area.points)
            drawPath(path, tone)
            drawPath(path,
                if (selected == area.id) Color(0xFF064A12) else Color.White.copy(alpha = .92f),
                style = Stroke(if (selected == area.id) 2.5f else 1.5f, cap = StrokeCap.Round))
        }
    }
}
