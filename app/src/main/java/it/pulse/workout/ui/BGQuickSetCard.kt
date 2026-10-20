package it.pulse.workout.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import it.pulse.workout.data.QuickSetValues
import it.pulse.workout.data.SetEntry

/** One explicit save outside this card. Editing and recalling never persist a set. */
@Composable
internal fun BGQuickSetCard(
    input: QuickSetValues, timeBased: Boolean, previous: SetEntry?,
    onChange: (QuickSetValues) -> Unit
) {
    var increment by rememberSaveable { mutableIntStateOf(2) }
    var showEffort by rememberSaveable { mutableStateOf(false) }
    val weightStep = listOf(.5, 1.0, 2.5, 5.0)[increment]
    BGCard(modifier = Modifier.animateContentSize(tween(BGMotion.duration(180))), padding = 16.dp) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically) {
            Text(if (input.warmup) "RISCALDAMENTO" else "LA TUA SERIE",
                color = PulseColors.Accent, style = MaterialTheme.typography.labelMedium)
            Text("Inserimento rapido", color = PulseColors.Muted, style = MaterialTheme.typography.labelSmall)
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            QuickValue(input.weight, "Peso · kg", true, Modifier.weight(1f),
                { if (it.length <= 7 && it.all { c -> c.isDigit() || c == ',' || c == '.' }) onChange(input.copy(weight = it)) },
                { onChange(input.adjustKg(-weightStep)) }, { onChange(input.adjustKg(weightStep)) })
            QuickValue(input.reps, if (timeBased) "Secondi" else "Ripetizioni", false, Modifier.weight(1f),
                { if (it.length <= 3 && it.all(Char::isDigit)) onChange(input.copy(reps = it)) },
                { onChange(input.adjustReps(-1)) }, { onChange(input.adjustReps(1)) })
        }
        TextButton(onClick = { increment = (increment + 1) % 4 }, modifier = Modifier.heightIn(min = 48.dp)) {
            Text("Passo peso: ${formatKgForWorkout(weightStep)} kg · cambia")
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            (if (timeBased) listOf(30, 45, 60) else listOf(8, 10, 12)).forEach { amount ->
                FilterChip(selected = input.reps == amount.toString(),
                    onClick = { onChange(input.copy(reps = amount.toString())) },
                    label = { Text("$amount ${if (timeBased) "sec" else "rep"}") },
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp))
            }
        }
        if (previous != null && previous.done && !previous.warmup) {
            TextButton(onClick = { onChange(input.withPreviousSet(previous)) }, modifier = Modifier.fillMaxWidth()) {
                Text("Ripeti ultima · ${previous.weight} kg × ${previous.reps}${if (timeBased) " sec" else " rep"}")
            }
        } else Text("Corpo libero? Imposta 0 kg.", color = PulseColors.Muted,
            style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 6.dp))
        if (!input.warmup) {
            TextButton(onClick = { showEffort = !showEffort }) {
                Text(if (input.actualRir != null) "Sforzo: RIR ${input.actualRir} · modifica"
                    else "Sforzo facoltativo ${if (showEffort) "−" else "+"}")
            }
            if (showEffort) ActualRirPicker(input.actualRir, { onChange(input.copy(actualRir = it)) }, compact = true)
        }
    }
}

@Composable
private fun QuickValue(
    value: String, label: String, decimal: Boolean, modifier: Modifier,
    onChange: (String) -> Unit, decrease: () -> Unit, increase: () -> Unit
) {
    val focus = LocalFocusManager.current
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        OutlinedTextField(value, onChange, label = { Text(label) },
            modifier = Modifier.fillMaxWidth().testTag(if (decimal) "quick-weight" else "quick-reps"), singleLine = true, shape = RoundedCornerShape(16.dp),
            textStyle = MaterialTheme.typography.headlineSmall.copy(
                textAlign = TextAlign.Center, fontWeight = FontWeight.Bold, fontSize = 28.sp),
            keyboardOptions = KeyboardOptions(keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Number,
                imeAction = if (decimal) ImeAction.Next else ImeAction.Done),
            keyboardActions = KeyboardActions(onNext = { focus.moveFocus(FocusDirection.Next) },
                onDone = { focus.clearFocus() }))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            FilledTonalIconButton(decrease, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Default.Remove, "Diminuisci $label")
            }
            FilledTonalIconButton(increase, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Default.Add, "Aumenta $label")
            }
        }
    }
}
