package it.pulse.workout.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import it.pulse.workout.R
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import it.pulse.workout.data.*
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val recoveryColor = Color(0xFFFFC987)
private val trainedColor = Color(0xFF106119)
private val readyColor = Color(0xFFA8D9B0)
private val neverColor = Color(0xFFE1E8E2)

/** Interfaccia Myself: fotografia anatomica e regioni autentiche MuscleMap rese nativamente. */
@Composable
fun MyselfScreen(
    history: List<SessionRecord>,
    active: WorkoutDraft?
) {
    var bodyDiaryOpen by rememberSaveable { mutableStateOf(false) }
    if (bodyDiaryOpen) {
        BodyProgressScreen(onBack = { bodyDiaryOpen = false })
        return
    }
    var detailedAnalytics by rememberSaveable { mutableStateOf(false) }
    if (detailedAnalytics) {
        TrainingAnalyticsScreen(history = history, onBack = { detailedAnalytics = false })
        return
    }
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(painterResource(R.drawable.buttigogym_mascot),
                    contentDescription = "Logo ButtiGoGym",
                    modifier = Modifier.size(60.dp).clip(RoundedCornerShape(18.dp)))
                Spacer(Modifier.width(13.dp))
                Column {
                    Text("I TUOI PROGRESSI", color = PulseColors.Accent,
                        fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.2.sp)
                    Text("Myself", color = PulseColors.Foreground,
                        fontSize = 34.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-1.3).sp)
                }
            }
            // Tocca i muscoli sulla mappa per aprire direttamente i progressi.
        }
        item {
            Button(
                onClick = { detailedAnalytics = true },
                modifier = Modifier.fillMaxWidth().height(53.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = PulseColors.Accent)
            ) {
                Icon(Icons.Default.BarChart, contentDescription = null,
                    modifier = Modifier.size(19.dp))
                Spacer(Modifier.width(10.dp))
                Text("Grafici, forza e record", fontWeight = FontWeight.ExtraBold,
                    fontSize = 15.sp, color = Color.White)
            }
        }
        item {
            OutlinedButton(
                onClick = { bodyDiaryOpen = true },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, PulseColors.Accent)
            ) {
                Text("Misure, peso e foto progresso",
                    color = PulseColors.Accent, fontWeight = FontWeight.Bold)
            }
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
                        modifier = Modifier.fillMaxWidth().height(505.dp)
                    )
                }
                Spacer(Modifier.height(7.dp))
                Text("Anatomia MuscleMap · licenza MIT · Jsplice/MuscleMap",
                    color = PulseColors.Muted, fontSize = 10.sp)
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
            Text("Recupero stimato (36–72 h) e livelli gamificati: non sono misure mediche.",
                color = PulseColors.Muted, fontSize = 10.sp, lineHeight = 15.sp)
            if (active != null) {
                Spacer(Modifier.height(8.dp))
                Text("Il workout in corso conta dopo il salvataggio.",
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
    val animated by animateFloatAsState(progress.coerceIn(0f,1f),
        animationSpec = tween(950), label = "Avanzamento livello")
    LinearProgressIndicator(
        progress = { animated },
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


/** Il rendering è nativo, a partire dai veri asset MuscleMap e senza JavaScript. */
@Composable
private fun MuscleFigure(
    back: Boolean,
    muscles: List<MuscleProgress>,
    selected: String,
    onSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    AndroidView(
        factory = { context -> NativeMuscleMapView(context) },
        modifier = modifier,
        update = { image -> image.bind(back, muscles, selected, onSelected) }
    )
}
