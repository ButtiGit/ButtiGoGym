package it.pulse.workout.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import it.pulse.workout.data.*
import java.time.LocalDate

@Composable
fun PreWorkoutCheckInDialog(
    existing: PreWorkoutCheckIn?,
    onSave: (PreWorkoutCheckIn) -> String?,
    onSkip: () -> Unit,
    onDismiss: () -> Unit
) {
    var energy by rememberSaveable(existing) { mutableStateOf(existing?.energy) }
    var sleep by rememberSaveable(existing) { mutableStateOf(existing?.sleep) }
    var soreness by rememberSaveable(existing) { mutableStateOf(existing?.soreness) }
    var step by rememberSaveable { mutableIntStateOf(0) }
    var error by remember { mutableStateOf<String?>(null) }
    BGModal("Prima di iniziare", onDismiss) {
        val record = if (energy != null && sleep != null && soreness != null)
            PreWorkoutCheckIn(LocalDate.now(), energy!!, sleep!!, soreness!!) else null
        Text(text = when (step) {
            0 -> "Come va l'energia oggi?"
            1 -> "Quanto hai dormito stanotte?"
            2 -> "Hai i muscoli indolenziti?"
            else -> record?.let(PreWorkoutCheckInRules::recommendation).orEmpty()
        }, color = PulseColors.Foreground, style = MaterialTheme.typography.titleMedium)
        when (step) {
            0 -> WorkoutEnergy.entries.forEach { option ->
                OutlinedButton(onClick = { energy = option; step = 1 }, modifier = Modifier.fillMaxWidth()) { Text(option.label) }
            }
            1 -> LastNightSleep.entries.forEach { option ->
                OutlinedButton(onClick = { sleep = option; step = 2 }, modifier = Modifier.fillMaxWidth()) { Text(option.label) }
            }
            2 -> WorkoutSoreness.entries.forEach { option ->
                OutlinedButton(onClick = { soreness = option; step = 3 }, modifier = Modifier.fillMaxWidth()) { Text(option.label) }
            }
            else -> {
                Text("${energy?.label} energia · ${sleep?.label} sonno · ${soreness?.label} indolenzimento", color = PulseColors.Muted)
                BGPrimaryButton("Continua", {
                    if (record != null) error = onSave(record)
                }, enabled = record != null)
            }
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        if (step > 0) TextButton(onClick = { error = null; step-- }) { Text("Indietro") }
        TextButton(onClick = onSkip) { Text("Salta check-in") }
    }
}
