package it.pulse.workout.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import it.pulse.workout.data.*
import java.time.LocalDate

@Composable
fun PreWorkoutCheckInDialog(
    existing: PreWorkoutCheckIn?,
    onSave: (PreWorkoutCheckIn) -> String?,
    onSkip: () -> Unit,
    onDismiss: () -> Unit
) {
    var energy by remember(existing) { mutableStateOf(existing?.energy) }
    var sleep by remember(existing) { mutableStateOf(existing?.sleep) }
    var soreness by remember(existing) { mutableStateOf(existing?.soreness) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Come ti senti oggi?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("3 risposte rapide e facoltative", color = PulseColors.Muted, fontSize = 12.sp)
                Text("Energia", fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    WorkoutEnergy.entries.forEach { option ->
                        FilterChip(selected = energy == option,
                            onClick = { energy = option },
                            label = { Text(option.label, fontSize = 11.sp, maxLines = 1) },
                            modifier = Modifier.weight(1f))
                    }
                }
                Text("Sonno di stanotte", fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    LastNightSleep.entries.forEach { option ->
                        FilterChip(selected = sleep == option,
                            onClick = { sleep = option },
                            label = { Text(option.label, fontSize = 11.sp, maxLines = 1) },
                            modifier = Modifier.weight(1f))
                    }
                }
                Text("Indolenzimento muscolare", fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    WorkoutSoreness.entries.forEach { option ->
                        FilterChip(selected = soreness == option,
                            onClick = { soreness = option },
                            label = { Text(option.label, fontSize = 11.sp, maxLines = 1) },
                            modifier = Modifier.weight(1f))
                    }
                }
                val e = energy
                val sl = sleep
                val so = soreness
                if (e != null && sl != null && so != null) {
                    Text(PreWorkoutCheckInRules.recommendation(
                        PreWorkoutCheckIn(LocalDate.now(), e, sl, so)),
                        color = PulseColors.Accent, fontSize = 12.sp)
                }
                Text("Non è una diagnosi. Con dolore acuto interrompi l'esercizio.",
                    color = PulseColors.Muted, fontSize = 11.sp)
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            Button(
                enabled = energy != null && sleep != null && soreness != null,
                onClick = {
                    val record = PreWorkoutCheckIn(
                        LocalDate.now(), energy ?: return@Button,
                        sleep ?: return@Button, soreness ?: return@Button)
                    error = onSave(record)
                }
            ) { Text("Salva e continua") }
        },
        dismissButton = { TextButton(onClick = onSkip) { Text("Salta") } }
    )
}
