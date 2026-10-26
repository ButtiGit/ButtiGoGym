package it.pulse.workout.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import it.pulse.workout.data.*
import java.time.LocalDate
import java.util.Locale

/** Card compatta nel diario fisico; nessun doppio archivio del peso. */
@Composable
fun LeanBulkTrackerCard(
    entries: List<BodyProgressEntry>,
    currentKcal: Int,
    lastAdjustment: LocalDate?,
    onSaveWeight: (Double) -> String?,
    onApplyCalories: (Int) -> String?
) {
    val today = LocalDate.now()
    val report = remember(entries, currentKcal, today) {
        LeanBulkTracker.analyze(entries, today, currentKcal)
    }
    var weighing by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val canAdjust = report.suggestedKcal != null &&
        (lastAdjustment == null || !lastAdjustment.isAfter(today.minusDays(14)))
    fun kg(value: Double?): String = value?.let {
        String.format(Locale.ITALIAN, "%.2f kg", it)
    } ?: "—"
    Surface(
        color = PulseColors.Surface, border = BorderStroke(1.dp, PulseColors.Border),
        shape = RoundedCornerShape(22.dp), modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("🎯  Obiettivo peso · 65 kg", color = PulseColors.Foreground,
                fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text("Ultimo peso", color = PulseColors.Muted, fontSize = 11.sp)
                    Text(kg(report.latestWeightKg), color = PulseColors.Foreground,
                        fontSize = 20.sp, fontWeight = FontWeight.Bold)
                }
                Column {
                    Text("Media ultimi 7 giorni", color = PulseColors.Muted, fontSize = 11.sp)
                    Text(kg(report.sevenDayAverageKg), color = PulseColors.Foreground,
                        fontSize = 20.sp, fontWeight = FontWeight.Bold)
                }
            }
            LinearProgressIndicator(
                progress = { report.progress }, modifier = Modifier.fillMaxWidth().height(8.dp),
                color = PulseColors.Accent, trackColor = PulseColors.SoftGreen
            )
            Text("${(report.progress * 100).toInt()}% verso l'obiettivo · partenza 58 kg",
                color = PulseColors.Muted, fontSize = 12.sp)
            Button(
                onClick = { error = null; weighing = true }, modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = PulseColors.Accent)
            ) { Text("Registra peso di oggi", color = PulseColors.DarkInk) }
            var showInsights by remember { mutableStateOf(false) }
            TextButton(onClick = { showInsights = !showInsights }) {
                Text(if (showInsights) "Nascondi dettagli" else "Mostra andamento e consigli",
                    color = PulseColors.Accent, fontSize = 12.sp)
            }
            if (showInsights) {
            HorizontalDivider(color = PulseColors.Border)
            val change = report.averageWeeklyChangeKg
            val interpretation = when (report.trend) {
                BulkTrend.NEED_MORE_DATA -> "Servono almeno 3 pesate per settimana per 3 settimane consecutive."
                BulkTrend.SLOW -> "Crescita lenta: controlla prima pasti, costanza e recupero."
                BulkTrend.ON_TRACK -> "La crescita è nel margine previsto: mantieni la strategia."
                BulkTrend.FAST -> "Crescita rapida: controlla anche l'andamento del girovita."
            }
            Text("Andamento: " + (change?.let {
                String.format(Locale.ITALIAN, "%+.2f kg/settimana", it)
            } ?: "dati insufficienti"), color = PulseColors.Foreground,
                fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            Text(interpretation, color = PulseColors.Muted, fontSize = 12.sp)
            Text("Riferimento: $currentKcal kcal/giorno · 120 g proteine · " +
                "335 g carboidrati · 70 g grassi (macro della quota iniziale).",
                color = PulseColors.Foreground, fontSize = 12.sp)
            val suggestion = report.suggestedKcal
            if (suggestion != null) {
                Text("Possibile aggiustamento: $suggestion kcal. Valuta anche il girovita, " +
                    "il recupero e i cambi di creatina. Non viene applicato automaticamente.",
                    color = PulseColors.Foreground, fontSize = 12.sp)
                OutlinedButton(
                    onClick = { error = onApplyCalories(suggestion) },
                    enabled = canAdjust, modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (canAdjust) "Applica $suggestion kcal"
                        else "Rivaluta dopo 14 giorni")
                }
            }
            Text("Pesati al mattino in condizioni simili.",
                color = PulseColors.Muted, fontSize = 11.sp)
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp) }
        }
    }
    if (weighing) {
        var weight by remember { mutableStateOf(report.latestWeightKg?.toString().orEmpty()) }
        val value = weight.trim().replace(',', '.').toDoubleOrNull()
        AlertDialog(
            onDismissRequest = { weighing = false },
            title = { Text("Peso di oggi") },
            text = {
                OutlinedTextField(
                    value = weight,
                    onValueChange = { if (it.length <= 8) weight = it },
                    label = { Text("Chilogrammi") },
                    supportingText = { Text("Non elimina le misure e foto già salvate oggi.") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true
                )
            },
            confirmButton = {
                Button(enabled = value != null && value in 20.0..400.0,
                    onClick = {
                        val weightKg = value ?: return@Button
                        val problem = onSaveWeight(weightKg)
                        if (problem == null) { weighing = false; error = null }
                        else error = problem
                    }) { Text("Salva peso") }
            },
            dismissButton = {
                TextButton(onClick = { weighing = false }) { Text("Annulla") }
            }
        )
    }
}
