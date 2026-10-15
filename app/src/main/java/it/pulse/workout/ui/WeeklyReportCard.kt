package it.pulse.workout.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import it.pulse.workout.data.*
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun WeeklyReportCard(report: WeeklyTrainingReport, checkIn: PreWorkoutCheckIn?,
                     onCheckIn: () -> Unit) {
    Surface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp),
        color = PulseColors.Surface, border = BorderStroke(1.dp, PulseColors.Border)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Text("REPORT · SETTIMANA CONCLUSA",
                color = PulseColors.Accent, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
            val dateFormat = DateTimeFormatter.ofPattern("dd MMM", Locale.ITALIAN)
            Text("${report.monday.format(dateFormat)} – ${report.sunday.format(dateFormat)}",
                color = PulseColors.Foreground, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Text("Allenamenti: ${report.completedDays}/${report.goalDays} giorni · " +
                "${report.completedSets} serie", color = PulseColors.Foreground, fontSize = 13.sp)
            if (report.quickSessions > 0) Text(
                "${report.quickSessions} sessioni rapide (volume ridotto)",
                color = PulseColors.Muted, fontSize = 12.sp)
            Text(if (report.comparableExercises == 0)
                "Forza: non ci sono ancora esercizi confrontabili tra le due settimane."
                else "Forza: ${report.improvedExercises}/${report.comparableExercises} esercizi " +
                    "con stima migliorata di almeno l'1%.",
                color = PulseColors.Foreground, fontSize = 12.sp)
            Text(if (report.averageWeightKg != null && report.weightChangeKg != null)
                "Peso medio: ${String.format(Locale.ITALIAN,"%.2f",report.averageWeightKg)} kg " +
                    "(${String.format(Locale.ITALIAN,"%+.2f",report.weightChangeKg)} kg rispetto alla precedente)."
                else "Peso: servono 3 pesate per ciascuna delle due settimane.",
                color = PulseColors.Foreground, fontSize = 12.sp)
            Text("La forza stimata non equivale a una misurazione della crescita muscolare.",
                color = PulseColors.Muted, fontSize = 11.sp)
            HorizontalDivider(color = PulseColors.Border)
            Text("Check-in di oggi: " + (checkIn?.let {
                "energia ${it.energy.label.lowercase()} · sonno ${it.sleep.label} · " +
                    "indolenzimento ${it.soreness.label.lowercase()}"
            } ?: "non registrato"), color = PulseColors.Muted, fontSize = 12.sp)
            OutlinedButton(onClick = onCheckIn, modifier = Modifier.fillMaxWidth()) {
                Text(if (checkIn == null) "Check-in preallenamento" else "Aggiorna check-in",
                    color = PulseColors.Accent)
            }
        }
    }
}
