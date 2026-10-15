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
import it.pulse.workout.data.WeeklyMissionSummary

@Composable
fun WeeklyMissionsCard(
    report: WeeklyMissionSummary,
    notifyWeekly: Boolean,
    onToggleWeekly: (Boolean) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = PulseColors.Surface,
        border = BorderStroke(1.dp, PulseColors.Border)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(11.dp)) {
            Text("MISSIONI SETTIMANALI · AVATAR BUILD",
                color = PulseColors.Accent, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
            Text("${report.missions.count { it.completed }}/${report.missions.count { it.available }} " +
                "missioni · ${report.earnedXp} XP missioni",
                color = PulseColors.Foreground, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Text("XP delle missioni separati dai livelli muscolari; nessuna penalità " +
                "se una settimana non va come previsto.",
                color = PulseColors.Muted, fontSize = 11.sp)
            report.missions.forEach { task ->
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(task.name, color = PulseColors.Foreground,
                            fontSize = 13.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f))
                        Text(if (!task.available) "Da sbloccare"
                            else if (task.completed) "+${task.rewardXp} XP"
                            else "${task.progress}/${task.target}",
                            color = if (task.completed) PulseColors.Accent else PulseColors.Muted,
                            fontSize = 11.sp)
                    }
                    LinearProgressIndicator(
                        progress = {
                            if (!task.available) 0f
                            else (task.progress.toFloat() / task.target.coerceAtLeast(1))
                                .coerceIn(0f, 1f)
                        },
                        modifier = Modifier.fillMaxWidth().height(5.dp),
                        color = PulseColors.Accent,
                        trackColor = PulseColors.SoftGreen
                    )
                    Text(task.detail, color = PulseColors.Muted, fontSize = 11.sp)
                }
            }
            HorizontalDivider(color = PulseColors.Border)
            Text("COSTANZA SENZA RESET",
                color = PulseColors.Accent, fontWeight = FontWeight.ExtraBold, fontSize = 11.sp)
            Text("${report.completedWeeksLastFour}/4 settimane complete in obiettivo",
                color = PulseColors.Foreground, fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold)
            Text("Conta le ultime quattro settimane concluse, non la streak di ogni giorno. " +
                "Anche una settimana difficile non azzera i progressi precedenti.",
                color = PulseColors.Muted, fontSize = 11.sp)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(Modifier.weight(1f)) {
                    Text("Riepilogo gentile", color = PulseColors.Foreground,
                        fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Text("Max 1 notifica a settimana, senza rumore e senza giudizi. " +
                        "Rispetta i giorni e gli orari consentiti.",
                        color = PulseColors.Muted, fontSize = 11.sp)
                }
                Switch(checked = notifyWeekly, onCheckedChange = onToggleWeekly,
                    colors = SwitchDefaults.colors(checkedTrackColor = PulseColors.Accent))
            }
            Text("Disattivato per impostazione predefinita. Non modifica i promemoria " +
                "di acqua e movimento già configurati.",
                color = PulseColors.Muted, fontSize = 11.sp)
        }
    }
}
