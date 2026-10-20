package it.pulse.workout.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import it.pulse.workout.R
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.KeyboardArrowDown
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
import androidx.compose.ui.platform.LocalContext
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
    active: WorkoutDraft?,
    settings: PersonalSettings,
    latestWeightKg: Double?,
    onSaveSettings: (PersonalSettings, Double?) -> String?
) {

    var page by rememberSaveable { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    // Refresh the bodyweight after returning from the measurements diary too.
    val rankingWeightKg = remember(page, latestWeightKg) {
        BodyProgressStore(context).latestWeightKg() ?: latestWeightKg
    }
    var selected by rememberSaveable { mutableStateOf("chest") }
    var showBack by rememberSaveable { mutableStateOf(false) }
    when (page) {
        "settings" -> { PersonalSettingsScreen(settings, latestWeightKg, onSaveSettings) { page = null }; return }
        "body" -> { BodyProgressScreen(onBack = { page = null }); return }
        "analytics" -> { TrainingAnalyticsScreen(history) { page = null }; return }
        "strength" -> {
            StrengthRanksScreen(history, rankingWeightKg,
                onBack = { page = null },
                onSelectMuscle = { id -> selected = id; page = null },
                onEditWeight = { page = "settings" })
            return
        }
    }
    var modal by rememberSaveable { mutableStateOf<String?>(null) }
    var clock by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) { clock = System.currentTimeMillis(); kotlinx.coroutines.delay(60_000) }
    }
    val report = remember(history, clock) { MuscleAnalytics.analyze(history, clock) }
    val strength = remember(history, rankingWeightKg) {
        StrengthRanking.evaluate(history, rankingWeightKg).associateBy { it.group.id }
    }
    val muscle = report.muscles.firstOrNull { it.group.id == selected } ?: report.muscles.first()
    val totalXp = report.muscles.sumOf { it.xp }
    val level = MuscleAnalytics.levelFromXp(totalXp / 4)
    val number = remember { DecimalFormat("#,##0") }
    LazyColumn(modifier = Modifier.fillMaxSize().background(PulseColors.Background),
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Myself", style = MaterialTheme.typography.headlineMedium,
                    color = PulseColors.Foreground, modifier = Modifier.weight(1f))
                IconButton(onClick = { modal = "tools" }) {
                    Icon(Icons.Default.MoreHoriz, "Opzioni progressi", tint = PulseColors.Foreground)
                }
            }
        }
        item {
            BGIdentityCard("BUTTIGOGYM / MYSELF", "Costruisci la tua versione migliore.",
                "Livello $level · ${number.format(totalXp)} XP", "✦")
        }
        item {
            MyselfCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Mappa muscolare", modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.titleLarge,
                        color = PulseColors.Foreground)
                    Text("Recupero", color = PulseColors.Muted,
                        style = MaterialTheme.typography.labelMedium)
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = !showBack, onClick = { showBack = false },
                        label = { Text("Fronte") })
                    FilterChip(selected = showBack, onClick = { showBack = true },
                        label = { Text("Retro") })
                }
                Spacer(Modifier.height(6.dp))
                MuscleFigure(back = showBack, muscles = report.muscles,
                    selected = selected, onSelected = { selected = it },
                    modifier = Modifier.fillMaxWidth().height(390.dp)
                        .clip(RoundedCornerShape(18.dp)))
                Spacer(Modifier.height(11.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    LegendDot("Pronti", readyColor)
                    LegendDot("Recupero", recoveryColor)
                    LegendDot("Oggi", trainedColor)
                }
                Spacer(Modifier.height(5.dp))
                LegendDot("Senza storico", neverColor)
                Spacer(Modifier.height(9.dp))
                Text(muscle.group.label + " · " + shortStatus(muscle),
                    color = PulseColors.Foreground,
                    style = MaterialTheme.typography.titleSmall)
                Text("Tocca il muscolo per selezionarlo. Il recupero resta una stima.",
                    color = PulseColors.Muted,
                    style = MaterialTheme.typography.bodySmall)
            }
        }
        item {
            TextButton(onClick = { modal = "muscles" }) {
                Text(muscle.group.label, style = MaterialTheme.typography.titleLarge)
                Icon(Icons.Default.KeyboardArrowDown, "Scegli muscolo")
            }
        }
        item {
            BGPrimaryButton("🏅 Rank di forza", { page = "strength" })
        }
        item {
            TextButton(onClick = { modal = "detail" }, modifier = Modifier.fillMaxWidth()) {
                Text("Dettagli e livello di " + muscle.group.label)
            }
        }
        item {
            TextButton(onClick = { modal = "level" }, modifier = Modifier.fillMaxWidth()) {
                Text("Livello $level · ${number.format(totalXp)} XP")
            }
        }
    }
    if (modal != null) BGModal(when (modal) {
        "muscles" -> "Scegli muscolo"
        "detail" -> muscle.group.label
        "balance" -> "Equilibrio del programma"
        "level" -> "I tuoi progressi"
        else -> "Progressi"
    }, { modal = null }) {
        when (modal) {
            "tools" -> {
                TextButton(onClick = { modal = null; page = "analytics" }) { Text("Grafici e record") }
                TextButton(onClick = { modal = null; page = "body" }) { Text("Misure, peso e foto") }
                TextButton(onClick = { modal = "balance" }) { Text("Equilibrio del programma") }
                TextButton(onClick = { modal = "level" }) { Text("Livello e recupero") }
                TextButton(onClick = { modal = null; page = "strength" }) { Text("Rank di forza e legenda") }
                TextButton(onClick = { modal = null; page = "settings" }) { Text("Impostazioni personali") }
            }
            "muscles" -> report.muscles.forEach { value ->
                TextButton(onClick = { selected = value.group.id; modal = null }, modifier = Modifier.fillMaxWidth()) {
                    Text(value.group.label, modifier = Modifier.weight(1f))
                    Text(shortStatus(value), color = PulseColors.Muted)
                }
            }
            "detail" -> {
                Text(statusText(muscle), color = PulseColors.Accent)
                muscle.lastTrainingMillis?.let { Text("Ultimo stimolo: " +
                    SimpleDateFormat("dd MMM · HH:mm", Locale.ITALIAN).format(Date(it)), color = PulseColors.Muted) }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SmallStat("Serie", muscle.workingSets.toString(), Modifier.weight(1f))
                    SmallStat("Volume", "${number.format(muscle.volumeKg)} kg", Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SmallStat("7gg dirette", muscle.directSets7Days.toString(), Modifier.weight(1f))
                    SmallStat("7gg indirette", muscle.indirectSets7Days.toString(), Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SmallStat("28gg dirette", muscle.directSets28Days.toString(), Modifier.weight(1f))
                    SmallStat("28gg indirette", muscle.indirectSets28Days.toString(), Modifier.weight(1f))
                }
                Text("Livello ${muscle.level} · Streak ${muscle.streakWeeks} settimane", color = PulseColors.Foreground)
                LevelTrack(muscle.progressToNext)
                Text("${number.format(muscle.levelCurrentXp)} / ${number.format(muscle.levelRequiredXp)} XP", color = PulseColors.Muted)
                muscle.bestEstimatedStrengthKg?.let { Text("Miglior 1RM stimato: ${number.format(it)} kg") }
                muscle.topSet?.let { Text("Miglior serie: $it") }
                strength[muscle.group.id]?.let { result ->
                    Text("Rank di forza: " + (result.tier?.name ?: "Non classificato"),
                        color = PulseColors.Accent,
                        style = MaterialTheme.typography.titleSmall)
                    result.evidence?.let { Text("Prestazione migliore: " + it) }
                    Text("Il rank dipende da una singola prestazione confrontabile, non dalle serie accumulate.",
                        color = PulseColors.Muted,
                        style = MaterialTheme.typography.bodySmall)
                }
                Text("Il recupero è una stima. Volume, XP e livelli descrivono le serie registrate.", color = PulseColors.Muted)
                if (active != null) Text("La sessione in corso conta dopo il salvataggio.", color = PulseColors.Muted)
            }
            "level" -> {
                Text("Livello $level", style = MaterialTheme.typography.headlineSmall)
                Text("${number.format(totalXp)} XP muscolari", color = PulseColors.Accent)
                report.muscles.groupBy { it.state }.forEach { (state, entries) ->
                    Text(when (state) {
                        MuscleRecovery.NEVER -> "Non allenati: ${entries.size}"
                        MuscleRecovery.TRAINED_TODAY -> "Allenati oggi: ${entries.size}"
                        MuscleRecovery.RECOVERING -> "In recupero: ${entries.size}"
                        MuscleRecovery.READY -> "Pronti: ${entries.size}"
                    })
                }
                Text("XP e livelli derivano dalle serie salvate. Il recupero è una stima.", color = PulseColors.Muted)
            }
            "balance" -> {
                val balance = report.balance
                Text("${balance.sessions28Days} sedute · ultimi 28 giorni", color = PulseColors.Muted)
                MuscleBalancePair("Spinta", balance.pushDirectSets, "Trazione", balance.pullDirectSets)
                MuscleBalancePair("Quadricipiti", balance.quadDirectSets, "Femorali", balance.hamstringDirectSets)
                Text("Core: ${balance.coreDirectSets} serie · Avambracci: ${balance.forearmDirectSets}")
                if (balance.enoughHistoryForGaps && balance.unstimulatedGroups.isNotEmpty())
                    Text("Senza stimoli registrati: " + balance.unstimulatedGroups.joinToString(" · "))
                Text("Conteggi delle serie, non un obiettivo di volume o un punteggio di simmetria.", color = PulseColors.Muted)
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
        color = PulseColors.Surface, border = BorderStroke(1.dp, PulseColors.Border)) {
        Column(Modifier.padding(18.dp), content = content)
    }
}

@Composable
private fun LevelTrack(progress: Float, height: androidx.compose.ui.unit.Dp = 9.dp) {
    val animated by animateFloatAsState(progress.coerceIn(0f,1f),
        animationSpec = tween(BGMotion.duration(750)), label = "Avanzamento livello")
    LinearProgressIndicator(
        progress = { animated },
        modifier = Modifier.fillMaxWidth().height(height).clip(CircleShape),
        color = PulseColors.Accent,
        trackColor = PulseColors.SoftGreen
    )
}

/** Barre normalizzate solo all'interno della coppia, non rispetto a un target fisiologico. */
@Composable
private fun MuscleBalancePair(
    leftLabel: String, left: Int, rightLabel: String, right: Int
) {
    val peak = maxOf(left, right, 1).toFloat()
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f)) {
            Text("$leftLabel · $left", color = PulseColors.Foreground,
                fontWeight = FontWeight.Bold, fontSize = 12.sp)
            Spacer(Modifier.height(5.dp))
            LinearProgressIndicator(progress = { left / peak },
                modifier = Modifier.fillMaxWidth().height(7.dp).clip(CircleShape),
                color = PulseColors.Accent, trackColor = PulseColors.SoftGreen)
        }
        Column(Modifier.weight(1f)) {
            Text("$rightLabel · $right", color = PulseColors.Foreground,
                fontWeight = FontWeight.Bold, fontSize = 12.sp)
            Spacer(Modifier.height(5.dp))
            LinearProgressIndicator(progress = { right / peak },
                modifier = Modifier.fillMaxWidth().height(7.dp).clip(CircleShape),
                color = PulseColors.Accent, trackColor = PulseColors.SoftGreen)
        }
    }
}

@Composable
private fun SmallStat(label: String, value: String, modifier: Modifier) {
    Column(modifier.background(PulseColors.Raised, RoundedCornerShape(13.dp))
        .padding(horizontal = 8.dp, vertical = 12.dp)) {
        Text(label, color = PulseColors.Muted, fontSize = 12.sp, maxLines = 1)
        Spacer(Modifier.height(5.dp))
        Text(value, color = PulseColors.Foreground, fontWeight = FontWeight.ExtraBold,
            fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun StatusCount(label: String, count: Int, tint: Color, modifier: Modifier) {
    Column(modifier.background(PulseColors.Surface, RoundedCornerShape(16.dp))
        .padding(vertical = 13.dp, horizontal = 10.dp)) {
        Box(Modifier.size(9.dp).background(tint, CircleShape))
        Spacer(Modifier.height(7.dp))
        Text("$count", color = PulseColors.Foreground, fontSize = 23.sp,
            fontWeight = FontWeight.ExtraBold)
        Text(label, color = PulseColors.Muted, fontSize = 11.sp,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
@Composable
private fun LegendDot(label: String, tint: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).background(tint, CircleShape))
        Spacer(Modifier.width(4.dp))
        Text(label, fontSize = 11.sp, color = PulseColors.Muted)
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
