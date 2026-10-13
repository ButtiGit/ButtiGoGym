package it.pulse.workout.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import it.pulse.workout.data.*
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.abs

private enum class AnalyticsScope { EXERCISE, MUSCLE }

@Composable
fun TrainingAnalyticsScreen(history: List<SessionRecord>, onBack: () -> Unit) {
    val available = remember(history) { TrainingAnalytics.exerciseOptions(history) }
    var scope by remember { mutableStateOf(AnalyticsScope.EXERCISE) }
    var exerciseId by remember { mutableStateOf<String?>(null) }
    var muscleId by remember { mutableStateOf("chest") }
    var period by remember { mutableStateOf(AnalyticsPeriod.WEEK) }
    var measure by remember { mutableStateOf(AnalyticsMeasure.VOLUME) }
    var expanded by remember { mutableStateOf(false) }

    val selectedExercise = available.firstOrNull { it.id == exerciseId }
        ?: available.firstOrNull()
    val selectedMuscle = MuscleGroup.all.firstOrNull { it.id == muscleId }
        ?: MuscleGroup.all.first()
    val activeMeasure = if (scope == AnalyticsScope.MUSCLE &&
        measure == AnalyticsMeasure.ESTIMATED_STRENGTH)
        AnalyticsMeasure.VOLUME else measure
    val points = remember(history, scope, selectedExercise?.id, selectedMuscle.id, period) {
        TrainingAnalytics.trend(
            history = history, period = period,
            exerciseId = if (scope == AnalyticsScope.EXERCISE) selectedExercise?.id else null,
            muscleId = if (scope == AnalyticsScope.MUSCLE) selectedMuscle.id else null
        )
    }
    val records = remember(history, scope, selectedExercise?.id, selectedMuscle.id) {
        if (scope == AnalyticsScope.EXERCISE) {
            selectedExercise?.let { TrainingAnalytics.exerciseRecords(history, it.id) }
        } else TrainingAnalytics.muscleSummary(history, selectedMuscle.id)
    }
    val comparison = TrainingAnalytics.compareRecent(points, activeMeasure)
    val previousPeriodName = if (period == AnalyticsPeriod.WEEK) "settimana scorsa" else "mese scorso"
    val currentPeriodName = if (period == AnalyticsPeriod.WEEK) "questa settimana" else "questo mese"
    val number = remember { NumberFormat.getNumberInstance(Locale.ITALIAN).apply {
        maximumFractionDigits = 1
    } }

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(PulseColors.Background),
        contentPadding = PaddingValues(horizontal = BGLayout.pageHorizontal, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            BGBackHeader("Storico e risultati", "Grafici & record", onBack) {
                Icon(Icons.Default.BarChart, null, tint = PulseColors.Accent,
                    modifier = Modifier.size(24.dp))
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                AnalyticsChoice("Esercizi", scope == AnalyticsScope.EXERCISE,
                    Modifier.weight(1f)) { scope = AnalyticsScope.EXERCISE }
                AnalyticsChoice("Muscoli", scope == AnalyticsScope.MUSCLE,
                    Modifier.weight(1f)) { scope = AnalyticsScope.MUSCLE }
            }
        }

        item {
            if (scope == AnalyticsScope.EXERCISE && available.isEmpty()) {
                BGEmptyState(
                    title = "Qui vedrai i tuoi progressi",
                    description = "Completa una seduta dalla scheda: grafici e record si aggiorneranno " +
                        "soltanto con le serie salvate.",
                    symbol = Icons.Default.BarChart
                )
            } else {
                AnalyticsContainer {
                    Text(if (scope == AnalyticsScope.EXERCISE) "ESERCIZIO" else "GRUPPO MUSCOLARE",
                        color = PulseColors.Muted, fontSize = 12.sp,
                        fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))
                    Box {
                        OutlinedButton(onClick = { expanded = true },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(15.dp)) {
                            Text(
                                if (scope == AnalyticsScope.EXERCISE)
                                    selectedExercise?.name ?: "Scegli esercizio"
                                else selectedMuscle.label,
                                maxLines = 1, overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                                color = PulseColors.Foreground)
                            Icon(Icons.Default.ExpandMore, null, tint = PulseColors.Accent)
                        }
                        DropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false }
                        ) {
                            if (scope == AnalyticsScope.EXERCISE) {
                                available.forEach { ex ->
                                    DropdownMenuItem(
                                        text = { Text(ex.name) },
                                        onClick = { exerciseId = ex.id; expanded = false }
                                    )
                                }
                            } else {
                                MuscleGroup.all.forEach { group ->
                                    DropdownMenuItem(
                                        text = { Text(group.label) },
                                        onClick = { muscleId = group.id; expanded = false }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                AnalyticsChoice("8 settimane", period == AnalyticsPeriod.WEEK,
                    Modifier.weight(1f)) { period = AnalyticsPeriod.WEEK }
                AnalyticsChoice("6 mesi", period == AnalyticsPeriod.MONTH,
                    Modifier.weight(1f)) { period = AnalyticsPeriod.MONTH }
            }
        }

        item {
            AnalyticsContainer {
                Text("ANDAMENTO", color = PulseColors.Muted,
                    fontSize = 10.sp, fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 1.1.sp)
                Spacer(Modifier.height(11.dp))
                Row(Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    AnalyticsMetricChoice("Volume", activeMeasure == AnalyticsMeasure.VOLUME) {
                        measure = AnalyticsMeasure.VOLUME
                    }
                    AnalyticsMetricChoice("Serie", activeMeasure == AnalyticsMeasure.WORKING_SETS) {
                        measure = AnalyticsMeasure.WORKING_SETS
                    }
                    if (scope == AnalyticsScope.EXERCISE) {
                        AnalyticsMetricChoice("Forza stimata", activeMeasure == AnalyticsMeasure.ESTIMATED_STRENGTH) {
                            measure = AnalyticsMeasure.ESTIMATED_STRENGTH
                        }
                    }
                }
                Spacer(Modifier.height(15.dp))
                val unit = if (activeMeasure == AnalyticsMeasure.WORKING_SETS) "serie" else "kg"
                val current = points.lastOrNull()?.value(activeMeasure) ?: 0.0
                Text(number.format(current) + " " + unit,
                    color = PulseColors.Foreground,
                    fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
                Text(currentPeriodName, color = PulseColors.Muted, fontSize = 12.sp)
                Spacer(Modifier.height(15.dp))
                AnalyticsTrendChart(points, activeMeasure)
                Spacer(Modifier.height(10.dp))
                if (comparison != null) {
                    val delta = comparison.percentageChange
                    Text(
                        if (delta == null)
                            "Nessun valore precedente per confrontare il periodo."
                        else (if (delta >= 0) "+" else "") +
                            number.format(delta) + "% rispetto a " + previousPeriodName,
                        color = if ((delta ?: 0.0) > 0.0) PulseColors.Accent else PulseColors.Muted,
                        fontWeight = FontWeight.SemiBold, fontSize = 12.sp
                    )
                }
                if (activeMeasure == AnalyticsMeasure.ESTIMATED_STRENGTH) {
                    Text("Forza = 1RM stimato con Epley; non è un massimale misurato.",
                        color = PulseColors.Muted, fontSize = 12.sp)
                }
                if (scope == AnalyticsScope.MUSCLE) {
                    Text("Volume attribuito alle serie dei muscoli primari; uno stesso esercizio può coinvolgerne più di uno.",
                        color = PulseColors.Muted, fontSize = 12.sp)
                }
            }
        }

        item {
            AnalyticsContainer {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.EmojiEvents, null, tint = PulseColors.Accent,
                        modifier = Modifier.size(23.dp))
                    Spacer(Modifier.width(9.dp))
                    Text(if (scope == AnalyticsScope.EXERCISE) "Record personali" else "Totali muscolari",
                        color = PulseColors.Foreground,
                        fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
                }
                Spacer(Modifier.height(11.dp))
                if (records == null) {
                    Text("Ancora nessuna serie completata e valida per questa selezione.",
                        color = PulseColors.Muted, fontSize = 12.sp)
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                        AnalyticsNumberCard("Volume totale",
                            number.format(records.totalVolumeKg) + " kg", Modifier.weight(1f))
                        AnalyticsNumberCard("Serie completate",
                            records.totalWorkingSets.toString(), Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                        AnalyticsNumberCard("Sedute",
                            records.trainedSessions.toString(), Modifier.weight(1f))
                        AnalyticsNumberCard("Miglior volume/seduta",
                            number.format(records.bestSessionVolumeKg) + " kg",
                            Modifier.weight(1f))
                    }
                    if (scope == AnalyticsScope.EXERCISE) {
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                            AnalyticsNumberCard("Peso maggiore",
                                records.bestWeightKg?.let { number.format(it) + " kg" } ?: "—",
                                Modifier.weight(1f))
                            AnalyticsNumberCard("Miglior 1RM stimato",
                                records.bestEstimated1RmKg?.let {
                                    number.format(it) + " kg"
                                } ?: "—", Modifier.weight(1f))
                        }
                        Spacer(Modifier.height(8.dp))
                        Text("Ripetizioni massime in una serie: " +
                            (records.bestRepsAtAnyWeight?.toString() ?: "—"),
                            color = PulseColors.Muted, fontSize = 11.sp)
                    }
                }
            }
        }

        item {
            Text("Calcoli basati solo sulle serie allenanti completate. " +
                "Esclusi riscaldamenti, esercizi a tempo e serie senza dati validi. " +
                "I grafici seguono la data di salvataggio delle sedute.",
                color = PulseColors.Muted, fontSize = 11.sp)
        }
    }
}

@Composable
private fun AnalyticsContainer(content: @Composable ColumnScope.() -> Unit) {
    Surface(color = PulseColors.Surface,
        shape = RoundedCornerShape(22.dp),
        modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(17.dp), content = content)
    }
}

@Composable
private fun AnalyticsChoice(label: String, selected: Boolean,
    modifier: Modifier = Modifier, onClick: () -> Unit
) {
    Surface(onClick = onClick, modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = if (selected) PulseColors.Accent else PulseColors.Surface) {
        Box(Modifier.padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
            Text(label, color = if (selected) Color.White else PulseColors.Foreground,
                fontWeight = FontWeight.Bold, fontSize = 12.sp)
        }
    }
}

@Composable
private fun AnalyticsMetricChoice(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(selected = selected, onClick = onClick, label = {
        Text(label, fontSize = 11.sp)
    }, shape = RoundedCornerShape(12.dp),
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = PulseColors.SoftGreen,
            selectedLabelColor = PulseColors.Accent))
}

@Composable
private fun AnalyticsNumberCard(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier.background(PulseColors.Raised, RoundedCornerShape(15.dp))
        .padding(12.dp)) {
        Text(label, color = PulseColors.Muted, fontSize = 12.sp, maxLines = 2)
        Spacer(Modifier.height(6.dp))
        Text(value, color = PulseColors.Foreground, fontSize = 15.sp,
            fontWeight = FontWeight.ExtraBold, maxLines = 1,
            overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun AnalyticsTrendChart(points: List<AnalyticsPoint>, measure: AnalyticsMeasure) {
    val values = points.map { it.value(measure).coerceAtLeast(0.0).toFloat() }
    val max = values.maxOrNull()?.takeIf { it > 0.0f } ?: 1f
    val accent = PulseColors.Accent
    val muted = PulseColors.Border
    val description = points.joinToString("; ") { point ->
        point.label + ": " + String.format(Locale.ITALIAN, "%.1f", point.value(measure))
    }
    Canvas(Modifier.fillMaxWidth().height(183.dp)
        .semantics { contentDescription = "Andamento: $description" }) {
        val padTop = 12.dp.toPx()
        val padBottom = 12.dp.toPx()
        val availableHeight = size.height - padTop - padBottom
        val width = size.width / points.size.coerceAtLeast(1)
        for (i in 0..3) {
            val y = padTop + (availableHeight * i / 3f)
            drawLine(muted, Offset(0f, y), Offset(size.width, y),
                strokeWidth = 1.dp.toPx())
        }
        if (measure == AnalyticsMeasure.ESTIMATED_STRENGTH) {
            var previous: Offset? = null
            values.forEachIndexed { index, value ->
                if (value > 0.0f) {
                    val center = Offset((index + .5f) * width,
                        padTop + availableHeight * (1.0f - value / max))
                    previous?.let { from ->
                        if (index > 0 && values[index - 1] > 0f)
                            drawLine(accent, from, center, strokeWidth = 3.dp.toPx(),
                                cap = StrokeCap.Round)
                    }
                    drawCircle(accent, radius = 4.dp.toPx(), center = center)
                    previous = center
                } else previous = null
            }
        } else {
            values.forEachIndexed { index, value ->
                val height = availableHeight * (value / max)
                if (height > 0f) {
                    val w = width * .55f
                    val left = index * width + (width - w) / 2f
                    drawRoundRect(
                        color = accent,
                        topLeft = Offset(left, padTop + availableHeight - height),
                        size = Size(w, height),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(5.dp.toPx())
                    )
                }
            }
        }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        points.forEach { point ->
            Text(point.label.replace(" ", "\n"),
                color = PulseColors.Muted, fontSize = 11.sp,
                modifier = Modifier.weight(1f),
                maxLines = 2, lineHeight = 11.sp)
        }
    }
    if (values.all { it <= 0f }) {
        Spacer(Modifier.height(9.dp))
        Text("Nessun dato registrato per questo intervallo.",
            fontSize = 12.sp, color = PulseColors.Muted)
    }
}
