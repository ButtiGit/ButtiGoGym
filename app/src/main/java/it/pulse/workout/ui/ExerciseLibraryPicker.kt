package it.pulse.workout.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import it.pulse.workout.data.*

@Composable
fun ExerciseLibraryPicker(
    existingIds: Set<String>, onPick: (LibraryExercise) -> Unit, onClose: () -> Unit
) {
    var query by rememberSaveable { mutableStateOf("") }
    var muscle by rememberSaveable { mutableStateOf<String?>(null) }
    var equipment by rememberSaveable { mutableStateOf<String?>(null) }
    var panatta by rememberSaveable { mutableStateOf(false) }
    var menu by remember { mutableStateOf<String?>(null) }
    val results = remember(query, muscle, equipment, panatta) {
        ExerciseLibrary.search(query, muscle, equipment, panatta)
    }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxWidth(.97f).fillMaxHeight(.94f), shape = RoundedCornerShape(24.dp),
            color = PulseColors.Background) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Database esercizi", style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.weight(1f))
                    TextButton(onClick = onClose) { Text("Chiudi") }
                }
                Text("${ExerciseLibrary.all.size} esercizi offline · ${ExerciseLibrary.all.count { it.brand == "Panatta" }} modelli Panatta",
                    color = PulseColors.Muted, style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(query, { query = it }, label = { Text("Cerca nome, attrezzo o codice modello") },
                    modifier = Modifier.fillMaxWidth().testTag("exercise-search"), singleLine = true)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(!panatta, { panatta = false }, label = { Text("Tutti") })
                    FilterChip(panatta, { panatta = !panatta }, label = { Text("Panatta") })
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.weight(1f)) {
                        OutlinedButton({ menu = "muscle" }, Modifier.fillMaxWidth()) { Text(muscle ?: "Tutti i muscoli") }
                        DropdownMenu(menu == "muscle", { menu = null }) {
                            DropdownMenuItem({ Text("Tutti i muscoli") }, { muscle = null; menu = null })
                            ExerciseLibrary.muscles.forEach { item ->
                                DropdownMenuItem({ Text(item) }, { muscle = item; menu = null })
                            }
                        }
                    }
                    Box(Modifier.weight(1f)) {
                        OutlinedButton({ menu = "equipment" }, Modifier.fillMaxWidth()) { Text(equipment ?: "Tutti gli attrezzi") }
                        DropdownMenu(menu == "equipment", { menu = null }) {
                            DropdownMenuItem({ Text("Tutti gli attrezzi") }, { equipment = null; menu = null })
                            ExerciseLibrary.equipment.forEach { item ->
                                DropdownMenuItem({ Text(item) }, { equipment = item; menu = null })
                            }
                        }
                    }
                }
                Text("${results.size} risultati", color = PulseColors.Muted)
                LazyColumn(Modifier.weight(1f).testTag("exercise-library-list"),
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (results.isEmpty()) item { Text("Nessun risultato. Prova un altro nome o rimuovi un filtro.") }
                    items(results, key = { it.id }) { item ->
                        val present = item.id in existingIds
                        Surface(onClick = { onPick(item) }, enabled = !present,
                            color = PulseColors.Surface, shape = RoundedCornerShape(16.dp)) {
                            Column(Modifier.fillMaxWidth().padding(14.dp)) {
                                Text(item.name, style = MaterialTheme.typography.titleSmall)
                                Text("${item.muscle} · ${item.equipment}" +
                                    if (item.model.isNotEmpty()) " · ${item.model}" else "",
                                    color = PulseColors.Muted, style = MaterialTheme.typography.bodySmall)
                                if (present) Text("Già nella scheda", color = PulseColors.Muted)
                            }
                        }
                    }
                }
                Text("Ogni macchina ha uno storico distinto. Aggiunta e rimozione diventano definitive quando salvi la scheda.",
                    color = PulseColors.Muted, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
