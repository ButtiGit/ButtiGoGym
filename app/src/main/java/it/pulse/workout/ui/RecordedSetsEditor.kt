package it.pulse.workout.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import it.pulse.workout.data.RecordedWorkoutSet
import it.pulse.workout.data.WorkoutDay

/**
 * Registro visibile anche quando si è già passati a un altro esercizio
 * o quando la sessione è pronta per il salvataggio nello storico.
 */
@Composable
internal fun RecordedSetsEditor(
    day: WorkoutDay,
    recorded: List<RecordedWorkoutSet>,
    onCorrect: (RecordedWorkoutSet, String, String, Int?) -> Unit,
    onCancel: (RecordedWorkoutSet) -> Unit
) {
    if (recorded.isEmpty()) return
    var expanded by remember { mutableStateOf(true) }
    var editing by remember { mutableStateOf<RecordedWorkoutSet?>(null) }
    var removing by remember { mutableStateOf<RecordedWorkoutSet?>(null) }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = PulseColors.Surface,
        border = BorderStroke(1.dp, PulseColors.Border),
        shape = RoundedCornerShape(23.dp)
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 13.dp)) {
            Row(
                Modifier.fillMaxWidth().clickable { expanded = !expanded },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Serie registrate", color = PulseColors.Foreground,
                        fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
                    Text("${recorded.size} registrazioni · Tocca per correggere",
                        color = PulseColors.Muted, fontSize = 11.sp)
                }
                Icon(
                    if (expanded) Icons.Default.KeyboardArrowUp
                    else Icons.Default.KeyboardArrowDown,
                    contentDescription = if (expanded) "Nascondi serie" else "Mostra serie",
                    tint = PulseColors.Accent
                )
            }

            if (expanded) {
                Spacer(Modifier.height(13.dp))
                day.exercises.forEach { exercise ->
                    val selected = recorded.filter { it.exercise.id == exercise.id }
                    if (selected.isNotEmpty()) {
                        HorizontalDivider(color = PulseColors.Border)
                        Spacer(Modifier.height(11.dp))
                        Text(exercise.name, color = PulseColors.Foreground,
                            fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Spacer(Modifier.height(5.dp))
                        selected.forEach { saved ->
                            Row(
                                Modifier.fillMaxWidth().padding(vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Check, contentDescription = null,
                                    tint = PulseColors.Accent,
                                    modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        if (saved.warmup) "Risc. ${saved.number}"
                                        else "Serie ${saved.number}",
                                        color = PulseColors.Muted, fontSize = 11.sp
                                    )
                                    Text(
                                        "${saved.entry.weight} kg  ·  ${saved.entry.reps} " +
                                            (if (exercise.timeBased) "sec" else "rep") +
                                            (saved.entry.rir?.let { " · RIR $it" } ?: ""),
                                        color = PulseColors.Foreground,
                                        fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                                        maxLines = 1
                                    )
                                }
                                IconButton(onClick = { editing = saved }) {
                                    Icon(Icons.Default.Edit,
                                        contentDescription = "Correggi ${exercise.name}, " +
                                            (if (saved.warmup) "riscaldamento" else "serie") +
                                            " ${saved.number}",
                                        tint = PulseColors.Accent,
                                        modifier = Modifier.size(20.dp))
                                }
                                IconButton(onClick = { removing = saved }) {
                                    Icon(Icons.Default.DeleteOutline,
                                        contentDescription = "Annulla ${exercise.name}, " +
                                            (if (saved.warmup) "riscaldamento" else "serie") +
                                            " ${saved.number}",
                                        tint = PulseColors.Muted,
                                        modifier = Modifier.size(20.dp))
                                }
                            }
                        }
                        Spacer(Modifier.height(7.dp))
                    }
                }
                Text("Le correzioni modificano solo la sessione in corso.",
                    color = PulseColors.Muted, fontSize = 10.sp)
            }
        }
    }

    editing?.let { target ->
        // Reset dei campi ogni volta che si seleziona una registrazione diversa.
        key(target.exercise.id, target.number, target.warmup, target.entry.completedAt) {
            var weight by remember { mutableStateOf(target.entry.weight) }
            var reps by remember { mutableStateOf(target.entry.reps) }
            var actualRir by remember { mutableStateOf(target.entry.rir) }
            val kg = weight.trim().replace(',', '.').toDoubleOrNull()
            val valid = kg != null && kg.isFinite() && kg in 0.0..10_000.0 &&
                (reps.toIntOrNull() ?: 0) in 1..999

            AlertDialog(
                onDismissRequest = { editing = null },
                title = { Text("Correggi ${target.exercise.name}") },
                text = {
                    Column {
                        Text(
                            if (target.warmup) "Riscaldamento ${target.number}"
                            else "Serie ${target.number}",
                            color = PulseColors.Muted, fontSize = 12.sp)
                        Spacer(Modifier.height(15.dp))
                        OutlinedTextField(
                            value = weight,
                            onValueChange = { candidate ->
                                if (candidate.length <= 9 &&
                                    candidate.all { it.isDigit() || it == '.' || it == ',' })
                                    weight = candidate
                            },
                            label = { Text("Peso · kg") },
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(10.dp))
                        OutlinedTextField(
                            value = reps,
                            onValueChange = { candidate ->
                                if (candidate.length <= 3 && candidate.all { it.isDigit() })
                                    reps = candidate
                            },
                            label = { Text(if (target.exercise.timeBased)
                                "Durata · secondi" else "Ripetizioni") },
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        if (!target.warmup) {
                            Spacer(Modifier.height(13.dp))
                            ActualRirPicker(value = actualRir,
                                onValueChange = { actualRir = it }, compact = true)
                        }
                        if (!valid) {
                            Spacer(Modifier.height(8.dp))
                            Text("Inserisci un peso valido e un valore da 1 a 999.",
                                color = MaterialTheme.colorScheme.error,
                                fontSize = 11.sp)
                        }
                    }
                },
                confirmButton = {
                    Button(
                        enabled = valid,
                        onClick = {
                            onCorrect(target, weight, reps, actualRir)
                            editing = null
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = PulseColors.Accent)
                    ) { Text("Salva correzione", color = Color.White) }
                },
                dismissButton = {
                    TextButton(onClick = { editing = null }) {
                        Text("Annulla", color = PulseColors.Accent)
                    }
                }
            )
        }
    }

    removing?.let { target ->
        AlertDialog(
            onDismissRequest = { removing = null },
            title = {
                Text(if (target.warmup) "Eliminare il riscaldamento?"
                    else "Annullare la serie ${target.number}?")
            },
            text = {
                Text(if (target.warmup)
                    "Rimuoverà questo riscaldamento. Le serie allenanti resteranno invariate."
                    else "La serie non risulterà più completata. Potrai registrarla di nuovo, " +
                        "senza perdere le altre serie.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        onCancel(target)
                        removing = null
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = PulseColors.Accent)
                ) {
                    Text(if (target.warmup) "Elimina" else "Annulla serie",
                        color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { removing = null }) {
                    Text("Indietro", color = PulseColors.Accent)
                }
            }
        )
    }
}
