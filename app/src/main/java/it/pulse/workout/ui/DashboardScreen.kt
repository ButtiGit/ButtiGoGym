package it.pulse.workout.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DirectionsWalk
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import it.pulse.workout.R
import kotlin.math.abs
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import it.pulse.workout.data.*
import java.text.NumberFormat
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.util.Locale

/** Dashboard locale a moduli, ispirata al linguaggio dei widget iOS. */
@Composable
fun DashboardScreen(
    upcoming: WorkoutDay,
    days: List<WorkoutDay>,
    active: WorkoutDraft?,
    history: List<SessionRecord>,
    wellness: WellnessData,
    stepStatus: String,
    healthConnected: Boolean,
    healthSyncing: Boolean,
    healthStatus: String,
    onConnectHealth: () -> Unit,
    onRefreshHealth: () -> Unit,
    onRequestStepAccess: () -> Unit,
    onStart: (WorkoutDay) -> Unit,
    onSaveSteps: (Int) -> Unit,
    onToggleRest: () -> Unit,
    onOpenPlan: () -> Unit
) {
    val today = LocalDate.now()
    val summary = remember(today, wellness, history) {
        StreakCalculator.calculate(today, wellness, history)
    }
    val current = active?.daySnapshot ?: days.firstOrNull { it.id == active?.dayId } ?: upcoming
    var stepsDialog by remember { mutableStateOf(false) }
    val number = NumberFormat.getIntegerInstance(Locale.ITALIAN)
    val weekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    val weekSessions = remember(history, today) {
        history.filter {
            val date = Instant.ofEpochMilli(it.completedAt).atZone(ZoneId.systemDefault()).toLocalDate()
            !date.isBefore(weekStart) && date.isBefore(weekStart.plusDays(7))
        }
    }
    val totalSetsThisWeek = weekSessions.sumOf { it.completedSets }
    val trainingMinutes = weekSessions.sumOf { it.durationMinutes.coerceAtLeast(0) }
    val readyMuscles = remember(history) {
        MuscleAnalytics.evaluate(history).count { it.state == MuscleRecovery.READY }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(PulseColors.Background),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        item {
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Image(painter = painterResource(R.drawable.buttigogym_mascot),
                        contentDescription = "Logo ButtiGoGym",
                        modifier = Modifier.size(52.dp).clip(RoundedCornerShape(16.dp)))
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text("ButtiGoGym", color = PulseColors.Accent,
                            fontSize = 19.sp, fontWeight = FontWeight.Black, letterSpacing = (-0.5).sp)
                        Text("IL TUO SPAZIO", fontSize = 10.sp, letterSpacing = 1.7.sp,
                            color = PulseColors.Muted, fontWeight = FontWeight.Bold)
                    }
                }
                Surface(color = PulseColors.SoftGreen, shape = CircleShape) {
                    Text("LEAN BULK", Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                        fontSize = 10.sp, fontWeight = FontWeight.Bold, color = PulseColors.Accent)
                }
            }
        }

        item {
            Text(
                today.format(DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.ITALIAN))
                    .replaceFirstChar { it.titlecase(Locale.ITALIAN) },
                color = PulseColors.Muted, fontSize = 13.sp, fontWeight = FontWeight.Medium
            )
            Spacer(Modifier.height(5.dp))
            Text("Oggi, un passo\npiù forte.", color = PulseColors.Foreground,
                fontSize = 35.sp, fontWeight = FontWeight.ExtraBold,
                lineHeight = 39.sp, letterSpacing = (-1.15).sp)
            Spacer(Modifier.height(9.dp))
            Text("Costanza, allenamento e recupero. Tutto qui.",
                color = PulseColors.Muted, fontSize = 13.sp)
        }

        item {
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = PulseColors.Accent,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.background(Brush.linearGradient(
                    listOf(Color(0xFF083C15), PulseColors.Accent, Color(0xFF237B36))
                )).padding(22.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(color = Color.White.copy(alpha = .16f),
                            shape = RoundedCornerShape(50)) {
                            Text(if (active != null) "SESSIONE IN CORSO" else "PROSSIMO ALLENAMENTO",
                                Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                                color = Color.White, fontWeight = FontWeight.Bold,
                                fontSize = 10.sp, letterSpacing = 0.7.sp)
                        }
                        Spacer(Modifier.weight(1f))
                        Image(painter = painterResource(R.drawable.buttigogym_mascot),
                            contentDescription = null,
                            modifier = Modifier.size(66.dp).clip(RoundedCornerShape(18.dp)))
                    }
                    Spacer(Modifier.height(20.dp))
                    Text(current.name, color = Color.White, fontSize = 25.sp,
                        fontWeight = FontWeight.ExtraBold, lineHeight = 29.sp)
                    Spacer(Modifier.height(7.dp))
                    Text("${current.exercises.size} esercizi · ${current.duration}",
                        color = Color.White.copy(alpha = .82f), fontSize = 13.sp)
                    Spacer(Modifier.height(20.dp))
                    Button(
                        onClick = { onStart(current) },
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.White, contentColor = PulseColors.Accent
                        )
                    ) {
                        Text(if (active != null) "Riprendi allenamento" else "Inizia allenamento",
                            fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Spacer(Modifier.weight(1f))
                        Icon(Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }

        item {
            DashboardSectionTitle("La tua evoluzione", "Ogni numero arriva dalle tue sessioni")
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                ProgressMetric("SEDUTE", "${summary.workoutDaysThisWeek}/3",
                    "questa settimana", summary.workoutDaysThisWeek / 3f, Modifier.weight(1f))
                ProgressMetric("SERIE", totalSetsThisWeek.toString(),
                    "questa settimana", totalSetsThisWeek / 62f, Modifier.weight(1f))
                ProgressMetric("TEMPO", "${trainingMinutes}m",
                    "questa settimana", trainingMinutes / 255f, Modifier.weight(1f))
            }
            Spacer(Modifier.height(10.dp))
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(19.dp), color = Color.White,
                border = BorderStroke(1.dp, PulseColors.Border)
            ) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(43.dp).background(PulseColors.SoftGreen, RoundedCornerShape(14.dp)),
                        contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.FitnessCenter, null, tint = PulseColors.Accent)
                    }
                    Spacer(Modifier.width(13.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Muscoli pronti", fontSize = 13.sp,
                            fontWeight = FontWeight.ExtraBold, color = PulseColors.Foreground)
                        Text("Stima del recupero dopo i pesi",
                            color = PulseColors.Muted, fontSize = 11.sp)
                    }
                    Text("${readyMuscles}/16", fontWeight = FontWeight.Black,
                        color = PulseColors.Accent, fontSize = 20.sp)
                }
            }
        }

        item {
            DashboardSectionTitle("Le tue streak", "Piccoli progressi, ogni giorno")
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StreakTile(
                    icon = Icons.Default.LocalFireDepartment, value = summary.generalDays,
                    unit = "giorni", title = "Generale",
                    detail = "Movimento + recupero", accentBackground = true,
                    animateFlame = true,
                    modifier = Modifier.weight(1f)
                )
                StreakTile(
                    icon = Icons.Default.FitnessCenter, value = summary.workoutWeeks,
                    unit = "settimane", title = "Allenamenti",
                    detail = "3 sedute principali", modifier = Modifier.weight(1f)
                )
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StreakTile(
                    icon = Icons.Default.Bedtime, value = summary.restDays,
                    unit = "giorni", title = "Riposo",
                    detail = "Recupero registrato", modifier = Modifier.weight(1f)
                )
                StreakTile(
                    icon = Icons.Default.DirectionsWalk, value = summary.stepDays,
                    unit = "giorni", title = "Passi",
                    detail = "Almeno 6.000", modifier = Modifier.weight(1f)
                )
            }
        }

        item {
            DashboardSectionTitle("Movimento quotidiano", "Obiettivo · 6.000 passi")
            Spacer(Modifier.height(12.dp))
            WidgetCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    StepsRing(summary.todaySteps, Modifier.size(116.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Passi di oggi", color = PulseColors.Muted, fontSize = 12.sp)
                        Spacer(Modifier.height(5.dp))
                        Text(number.format(summary.todaySteps), color = PulseColors.Foreground,
                            fontSize = 27.sp, fontWeight = FontWeight.ExtraBold)
                        Text("di 6.000 passi", color = PulseColors.Muted, fontSize = 12.sp)
                        Spacer(Modifier.height(12.dp))
                        Text(
                            if (summary.todaySteps >= 6000) "Obiettivo raggiunto ✓"
                            else "Mancano ${number.format(6000 - summary.todaySteps)} passi",
                            fontSize = 11.sp, color = PulseColors.Accent, fontWeight = FontWeight.Bold
                        )
                    }
                }
                Spacer(Modifier.height(18.dp))
                Surface(color = PulseColors.SoftGreen, shape = RoundedCornerShape(14.dp)) {
                    Column(Modifier.fillMaxWidth().padding(13.dp)) {
                        Text(if (healthConnected) "● Health Connect collegato" else "Health Connect",
                            color = PulseColors.Accent, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(4.dp))
                        Text(healthStatus, color = PulseColors.Foreground, fontSize = 11.sp)
                        Spacer(Modifier.height(4.dp))
                        Text("Samsung Health → Impostazioni → Health Connect: abilita la condivisione dei passi.",
                            color = PulseColors.Muted, fontSize = 10.sp)
                    }
                }
                Spacer(Modifier.height(11.dp))
                if (healthConnected) {
                    Button(
                        onClick = onRefreshHealth,
                        enabled = !healthSyncing,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = PulseColors.Accent)
                    ) {
                        Text(if (healthSyncing) "Sincronizzazione…" else "Aggiorna passi da Health Connect",
                            color = Color.White, fontWeight = FontWeight.SemiBold)
                    }
                    Spacer(Modifier.height(7.dp))
                    Text("Passi aggregati senza doppi conteggi. La sincronizzazione si aggiorna anche riaprendo l'app.",
                        color = PulseColors.Muted, fontSize = 10.sp)
                } else {
                    Button(
                        onClick = onConnectHealth,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = PulseColors.Accent)
                    ) { Text("Collega Health Connect", color = Color.White, fontWeight = FontWeight.Bold) }
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = { stepsDialog = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = PulseColors.Accent),
                        border = BorderStroke(1.dp, PulseColors.Border)
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(9.dp))
                        Text("Inserisci / correggi passi", fontWeight = FontWeight.SemiBold)
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(stepStatus, color = PulseColors.Muted, fontSize = 10.sp)
                    if (stepStatus.startsWith("Consenti")) {
                        TextButton(onClick = onRequestStepAccess) {
                            Text("Attiva contapassi del telefono", color = PulseColors.Accent)
                        }
                    }
                }
            }
        }

        item {
            DashboardSectionTitle("Recupero", "Allenarsi bene significa anche riposare")
            Spacer(Modifier.height(12.dp))
            WidgetCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(47.dp).clip(RoundedCornerShape(16.dp))
                            .background(PulseColors.SoftGreen),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.NightsStay, contentDescription = null,
                            tint = PulseColors.Accent, modifier = Modifier.size(23.dp))
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Giornata di recupero", color = PulseColors.Foreground,
                            fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        Text(
                            when {
                                summary.restedToday -> "Riposo segnato per oggi"
                                summary.workoutToday -> "Allenamento completato oggi"
                                else -> "Segna oggi come giorno di riposo"
                            },
                            color = PulseColors.Muted, fontSize = 11.sp
                        )
                    }
                    if (summary.restedToday)
                        Icon(Icons.Default.CheckCircle, contentDescription = "Riposo segnato",
                            tint = PulseColors.Accent)
                }
                Spacer(Modifier.height(14.dp))
                if (!summary.workoutToday || summary.restedToday) {
                    Button(
                        onClick = onToggleRest, modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = PulseColors.SoftGreen,
                            contentColor = PulseColors.Accent
                        )
                    ) {
                        Text(if (summary.restedToday) "Annulla riposo" else "Segna riposo oggi",
                            fontWeight = FontWeight.Bold)
                    }
                } else {
                    Text("La giornata conta già come attiva grazie all'allenamento.",
                        color = PulseColors.Accent, fontSize = 12.sp)
                }
            }
        }

        item {
            DashboardSectionTitle("La settimana", "${summary.workoutDaysThisWeek} su 3 allenamenti completati")
            Spacer(Modifier.height(12.dp))
            WidgetCard {
                val animatedWeek by animateFloatAsState(
                    (summary.workoutDaysThisWeek / 3f).coerceIn(0f, 1f),
                    animationSpec = tween(900), label = "Sedute settimanali"
                )
                LinearProgressIndicator(
                    progress = { animatedWeek },
                    modifier = Modifier.fillMaxWidth().height(7.dp).clip(CircleShape),
                    color = PulseColors.Accent,
                    trackColor = PulseColors.SoftGreen
                )
                Spacer(Modifier.height(18.dp))
                val start = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                val completed = history.filter { it.completedSets > 0 }.filter {
                    val date = Instant.ofEpochMilli(it.completedAt)
                        .atZone(ZoneId.systemDefault()).toLocalDate()
                    !date.isBefore(start) && date.isBefore(start.plusWeeks(1))
                }.map { it.dayId }.toSet()
                Row(horizontalArrangement = Arrangement.spacedBy(9.dp),
                    modifier = Modifier.fillMaxWidth()) {
                    days.filterNot { it.optional }.take(3).forEach { day ->
                        val done = day.id in completed
                        Column(
                            modifier = Modifier.weight(1f).clip(RoundedCornerShape(14.dp))
                                .background(if (done) PulseColors.SoftGreen else PulseColors.Raised)
                                .clickable { onStart(day) }
                                .padding(vertical = 13.dp, horizontal = 8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(day.weekday.take(3),
                                color = if (done) PulseColors.Accent else PulseColors.Muted,
                                fontWeight = FontWeight.Bold, fontSize = 11.sp)
                            Spacer(Modifier.height(7.dp))
                            Icon(if (done) Icons.Default.CheckCircle else Icons.Default.FitnessCenter,
                                contentDescription = if (done) "Completato" else "Da completare",
                                tint = if (done) PulseColors.Accent else PulseColors.Muted,
                                modifier = Modifier.size(19.dp))
                        }
                    }
                }
                Spacer(Modifier.height(14.dp))
                TextButton(onClick = onOpenPlan, modifier = Modifier.fillMaxWidth()) {
                    Text("Vai alla scheda", color = PulseColors.Accent,
                        fontWeight = FontWeight.Bold)
                    Spacer(Modifier.weight(1f))
                    Icon(Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null, tint = PulseColors.Accent)
                }
            }
        }

        item {
            Text("Gli streak iniziano dai dati realmente registrati, senza valori demo.",
                color = PulseColors.Muted, fontSize = 11.sp,
                modifier = Modifier.padding(bottom = 4.dp))
        }
    }

    if (stepsDialog) {
        var input by rememberSaveable(summary.todaySteps) {
            mutableStateOf(summary.todaySteps.takeIf { it > 0 }?.toString() ?: "")
        }
        val value = input.toIntOrNull()
        AlertDialog(
            onDismissRequest = { stepsDialog = false },
            title = {
                Text("Passi di oggi", fontWeight = FontWeight.Bold,
                    color = PulseColors.Foreground)
            },
            text = {
                Column {
                    Text("Inserisci il totale giornaliero, non i passi da aggiungere.",
                        color = PulseColors.Muted, fontSize = 13.sp)
                    Spacer(Modifier.height(14.dp))
                    OutlinedTextField(
                        value = input,
                        onValueChange = { next ->
                            if (next.length <= 6 && next.all(Char::isDigit)) input = next
                        },
                        label = { Text("Passi") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp)
                    )
                }
            },
            confirmButton = {
                Button(
                    enabled = value != null && value in 0..200000,
                    onClick = { value?.let(onSaveSteps); stepsDialog = false },
                    colors = ButtonDefaults.buttonColors(containerColor = PulseColors.Accent)
                ) { Text("Salva", color = Color.White) }
            },
            dismissButton = {
                TextButton(onClick = { stepsDialog = false }) {
                    Text("Annulla", color = PulseColors.Accent)
                }
            }
        )
    }
}

@Composable
private fun DashboardSectionTitle(title: String, subtitle: String) {
    Column {
        Text(title, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold,
            color = PulseColors.Foreground, letterSpacing = (-0.45).sp)
        Spacer(Modifier.height(3.dp))
        Text(subtitle, color = PulseColors.Muted, fontSize = 12.sp)
    }
}

@Composable
private fun WidgetCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color.White,
        shape = RoundedCornerShape(26.dp),
        border = BorderStroke(1.dp, PulseColors.Border),
        shadowElevation = 1.dp
    ) {
        Column(Modifier.padding(19.dp), content = content)
    }
}

@Composable
private fun StreakTile(
    icon: ImageVector,
    value: Int,
    unit: String,
    title: String,
    detail: String,
    modifier: Modifier,
    accentBackground: Boolean = false,
    animateFlame: Boolean = false
) {
    val wobble = remember { Animatable(0f) }
    LaunchedEffect(value, animateFlame) {
        wobble.snapTo(0f)
        if (animateFlame && value > 0) {
            // 6 × 500 ms: la fiamma si muove tre secondi e poi si ferma.
            repeat(6) {
                wobble.animateTo(9f, animationSpec = tween(125))
                wobble.animateTo(-9f, animationSpec = tween(125))
                wobble.animateTo(0f, animationSpec = tween(250))
            }
        }
    }
    Surface(
        modifier = modifier.height(167.dp),
        color = if (accentBackground) PulseColors.SoftGreen else Color.White,
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.dp, if (accentBackground) PulseColors.SoftGreen else PulseColors.Border)
    ) {
        Column(Modifier.padding(15.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(Modifier.size(33.dp).background(
                    if (accentBackground) Color.White else PulseColors.SoftGreen,
                    RoundedCornerShape(11.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, contentDescription = null,
                        tint = if (animateFlame && value > 0) Color(0xFFE6783D) else PulseColors.Accent,
                        modifier = Modifier.size(19.dp).graphicsLayer {
                            rotationZ = wobble.value
                            scaleX = 1f + abs(wobble.value) / 45f
                            scaleY = 1f + abs(wobble.value) / 55f
                        })
                }
                Text("STREAK", color = PulseColors.Muted, fontSize = 8.sp,
                    fontWeight = FontWeight.Bold, letterSpacing = 1.0.sp)
            }
            Spacer(Modifier.height(11.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(value.toString(), fontSize = 30.sp,
                    fontWeight = FontWeight.ExtraBold, color = PulseColors.Foreground,
                    lineHeight = 34.sp)
                Spacer(Modifier.width(5.dp))
                Text(unit, color = PulseColors.Muted, fontSize = 10.sp,
                    modifier = Modifier.padding(bottom = 5.dp))
            }
            Text(title, color = PulseColors.Foreground, fontSize = 13.sp,
                fontWeight = FontWeight.Bold, maxLines = 1)
            Text(detail, color = PulseColors.Muted, fontSize = 10.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun StepsRing(value: Int, modifier: Modifier) {
    val ratio = (value.toFloat() / StreakCalculator.STEP_GOAL).coerceIn(0f, 1f)
    val progress by animateFloatAsState(ratio,
        animationSpec = tween(durationMillis = 1000), label = "Progressi passi")
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(
            Modifier.fillMaxSize().semantics {
                contentDescription = "$value di 6000 passi"
            }
        ) {
            val stroke = 11.dp.toPx()
            drawArc(PulseColors.SoftGreen, 0f, 360f, false,
                style = Stroke(width = stroke, cap = StrokeCap.Round))
            if (progress > 0f) {
                drawArc(PulseColors.Accent, -90f, progress * 360f, false,
                    style = Stroke(width = stroke, cap = StrokeCap.Round))
            }
        }
        Icon(
            Icons.Default.DirectionsWalk, contentDescription = null,
            tint = PulseColors.Accent, modifier = Modifier.size(33.dp)
        )
    }
}

@Composable
private fun ProgressMetric(
    label: String, value: String, hint: String, fraction: Float, modifier: Modifier
) {
    val animated by animateFloatAsState(
        targetValue = fraction.coerceIn(0f, 1f),
        animationSpec = tween(950), label = "Indicatore $label"
    )
    Surface(modifier = modifier, color = Color.White, shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, PulseColors.Border)) {
        Column(Modifier.padding(12.dp)) {
            Text(label, fontSize = 9.sp, color = PulseColors.Muted,
                fontWeight = FontWeight.Bold, letterSpacing = .9.sp)
            Spacer(Modifier.height(8.dp))
            Text(value, fontWeight = FontWeight.Black, fontSize = 23.sp,
                color = PulseColors.Foreground, maxLines = 1)
            Text(hint, color = PulseColors.Muted, fontSize = 9.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(12.dp))
            LinearProgressIndicator(
                progress = { animated },
                modifier = Modifier.fillMaxWidth().height(5.dp).clip(CircleShape),
                color = PulseColors.Accent, trackColor = PulseColors.SoftGreen
            )
        }
    }
}
