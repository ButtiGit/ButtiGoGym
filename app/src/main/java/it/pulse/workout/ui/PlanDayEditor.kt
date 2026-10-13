package it.pulse.workout.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import it.pulse.workout.data.CustomPlanRules
import it.pulse.workout.data.Exercise
import it.pulse.workout.data.WorkoutDay

private val planMuscles = listOf(
    "PETTO", "SCHIENA", "SPALLE", "BICIPITI", "TRICIPITI", "AVAMBRACCI",
    "QUADRICIPITI", "FEMORALI", "GLUTEI", "POLPACCI", "ADDOME", "CORE",
    "GAMBE", "DORSALI", "TRAPEZI"
)

/** L'editor usa una bozza: un solo salvataggio quando l'utente conferma. */
@Composable
fun PlanDayEditor(
    day: WorkoutDay,
    customized: Boolean,
    onSave: (List<Exercise>) -> String?,
    onRestore: () -> String?,
    onClose: () -> Unit
) {
    var exercises by remember(day.id) { mutableStateOf(day.exercises) }
    var exerciseEditing by remember { mutableStateOf<Exercise?>(null) }
    var removeTarget by remember { mutableStateOf<Exercise?>(null) }
    var confirmRestore by remember { mutableStateOf(false) }
    var confirmDiscard by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val changed = exercises != day.exercises

    fun closeEditor() {
        if (changed) confirmDiscard = true else onClose()
    }

    Dialog(
        onDismissRequest = ::closeEditor,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(.96f).fillMaxHeight(.92f),
            shape = RoundedCornerShape(25.dp),
            color = PulseColors.Background
        ) {
            Column(Modifier.fillMaxSize()) {
                Row(
                    Modifier.fillMaxWidth().padding(start = 18.dp, end = 10.dp, top = 17.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("PERSONALIZZA LA SCHEDA", color = PulseColors.Accent,
                            fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                        Text(day.name, color = PulseColors.Foreground,
                            fontSize = 21.sp, fontWeight = FontWeight.ExtraBold)
                    }
                    IconButton(onClick = ::closeEditor) {
                        Icon(Icons.Default.Close, "Chiudi editor", tint = PulseColors.Foreground)
                    }
                }

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(start = 14.dp, end = 14.dp, bottom = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(9.dp)
                ) {
                    item {
                        Text("${exercises.size} esercizi · modifica ordine, serie, ripetizioni e pause",
                            color = PulseColors.Muted, fontSize = 12.sp)
                        Spacer(Modifier.height(7.dp))
                    }
                    itemsIndexed(exercises, key = { _, exercise -> exercise.id }) { index, exercise ->
                        Surface(
                            color = PulseColors.Surface,
                            shape = RoundedCornerShape(17.dp),
                            border = BorderStroke(1.dp, PulseColors.Border)
                        ) {
                            Row(
                                Modifier.fillMaxWidth().padding(start = 13.dp, end = 3.dp, top = 8.dp, bottom = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("${index + 1}".padStart(2, '0'),
                                    color = PulseColors.Accent, fontWeight = FontWeight.ExtraBold,
                                    fontSize = 13.sp)
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(exercise.name, color = PulseColors.Foreground,
                                        fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 2)
                                    Spacer(Modifier.height(4.dp))
                                    Text("${exercise.sets} × ${exercise.target} · ${exercise.restSeconds}s riposo",
                                        color = PulseColors.Muted, fontSize = 11.sp)
                                    if (exercise.optional)
                                        Text("FACOLTATIVO", color = PulseColors.Accent, fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold)
                                }
                                Column {
                                    IconButton(
                                        onClick = {
                                            exercises = CustomPlanRules.move(exercises, index, index - 1)
                                        },
                                        enabled = index > 0,
                                        modifier = Modifier.size(34.dp)
                                    ) {
                                        Icon(Icons.Default.ArrowUpward,
                                            contentDescription = "Sposta ${exercise.name} sopra",
                                            modifier = Modifier.size(18.dp))
                                    }
                                    IconButton(
                                        onClick = {
                                            exercises = CustomPlanRules.move(exercises, index, index + 1)
                                        },
                                        enabled = index < exercises.lastIndex,
                                        modifier = Modifier.size(34.dp)
                                    ) {
                                        Icon(Icons.Default.ArrowDownward,
                                            contentDescription = "Sposta ${exercise.name} sotto",
                                            modifier = Modifier.size(18.dp))
                                    }
                                }
                                Column {
                                    IconButton(onClick = { exerciseEditing = exercise },
                                        modifier = Modifier.size(34.dp)) {
                                        Icon(Icons.Default.Edit,
                                            contentDescription = "Modifica ${exercise.name}",
                                            tint = PulseColors.Accent, modifier = Modifier.size(18.dp))
                                    }
                                    IconButton(
                                        onClick = { removeTarget = exercise },
                                        enabled = exercises.size > 1,
                                        modifier = Modifier.size(34.dp)
                                    ) {
                                        Icon(Icons.Default.DeleteOutline,
                                            contentDescription = "Rimuovi ${exercise.name}",
                                            modifier = Modifier.size(18.dp))
                                    }
                                }
                            }
                        }
                    }
                    item {
                        Spacer(Modifier.height(4.dp))
                        OutlinedButton(
                            onClick = {
                                if (exercises.size < CustomPlanRules.MAX_EXERCISES) {
                                    exerciseEditing = Exercise(
                                        id = CustomPlanRules.newExerciseId(),
                                        name = "", muscle = "PETTO",
                                        sets = 3, target = "8–12", restSeconds = 90
                                    )
                                }
                            },
                            enabled = exercises.size < CustomPlanRules.MAX_EXERCISES,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(15.dp)
                        ) {
                            Icon(Icons.Default.Add, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(7.dp))
                            Text("Aggiungi esercizio personale")
                        }
                        if (customized) {
                            TextButton(
                                onClick = { confirmRestore = true },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.Restore, null, modifier = Modifier.size(17.dp),
                                    tint = PulseColors.Accent)
                                Spacer(Modifier.width(6.dp))
                                Text("Ripristina la scheda originale", color = PulseColors.Accent)
                            }
                        }
                    }
                }

                HorizontalDivider(color = PulseColors.Border)
                Column(Modifier.padding(15.dp)) {
                    if (errorMessage != null) {
                        Text(errorMessage.orEmpty(), color = MaterialTheme.colorScheme.error,
                            fontSize = 12.sp)
                        Spacer(Modifier.height(7.dp))
                    }
                    Text("Le sessioni già iniziate o salvate non verranno modificate.",
                        color = PulseColors.Muted, fontSize = 11.sp)
                    Spacer(Modifier.height(9.dp))
                    Button(
                        onClick = {
                            val error = onSave(exercises)
                            if (error == null) onClose() else errorMessage = error
                        },
                        enabled = changed,
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = PulseColors.Accent),
                        shape = RoundedCornerShape(14.dp)
                    ) { Text("Salva personalizzazioni", fontWeight = FontWeight.Bold, color = Color.White) }
                }
            }
        }
    }

    exerciseEditing?.let { chosen ->
        PlanExerciseForm(
            exercise = chosen,
            isNew = exercises.none { it.id == chosen.id },
            onDismiss = { exerciseEditing = null },
            onSave = { updated ->
                exercises = if (exercises.any { it.id == updated.id })
                    CustomPlanRules.replace(exercises, updated)
                else CustomPlanRules.append(exercises, updated)
                exerciseEditing = null
                errorMessage = null
            }
        )
    }

    removeTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { removeTarget = null },
            title = { Text("Rimuovere ${target.name}?") },
            text = { Text("L'esercizio sarà tolto solo dalla scheda futura. " +
                "Le sessioni precedenti manterranno i propri dati.") },
            confirmButton = {
                Button(
                    onClick = {
                        exercises = CustomPlanRules.remove(exercises, target.id)
                        removeTarget = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PulseColors.Accent)
                ) { Text("Rimuovi", color = Color.White) }
            },
            dismissButton = {
                TextButton(onClick = { removeTarget = null }) { Text("Annulla") }
            }
        )
    }

    if (confirmRestore) {
        AlertDialog(
            onDismissRequest = { confirmRestore = false },
            title = { Text("Ripristinare questa seduta?") },
            text = { Text("Rimuoverà tutte le personalizzazioni locali del giorno. " +
                "Gli allenamenti già registrati rimarranno intatti.") },
            confirmButton = {
                Button(
                    onClick = {
                        errorMessage = onRestore()
                        confirmRestore = false
                        if (errorMessage == null) onClose()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PulseColors.Accent)
                ) { Text("Ripristina", color = Color.White) }
            },
            dismissButton = {
                TextButton(onClick = { confirmRestore = false }) { Text("Annulla") }
            }
        )
    }

    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text("Scartare le modifiche?") },
            text = { Text("Le modifiche non ancora salvate andranno perse.") },
            confirmButton = {
                Button(
                    onClick = { confirmDiscard = false; onClose() },
                    colors = ButtonDefaults.buttonColors(containerColor = PulseColors.Accent)
                ) { Text("Scarta", color = Color.White) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDiscard = false }) { Text("Continua a modificare") }
            }
        )
    }
}

@Composable
private fun PlanExerciseForm(
    exercise: Exercise,
    isNew: Boolean,
    onDismiss: () -> Unit,
    onSave: (Exercise) -> Unit
) {
    var name by remember(exercise.id) { mutableStateOf(exercise.name) }
    var muscle by remember(exercise.id) { mutableStateOf(exercise.muscle) }
    var sets by remember(exercise.id) { mutableStateOf(exercise.sets.toString()) }
    var target by remember(exercise.id) { mutableStateOf(exercise.target) }
    var rest by remember(exercise.id) { mutableStateOf(exercise.restSeconds.toString()) }
    var rir by remember(exercise.id) { mutableStateOf(exercise.rir) }
    var optional by remember(exercise.id) { mutableStateOf(exercise.optional) }
    var timed by remember(exercise.id) { mutableStateOf(exercise.timeBased) }
    var chooseMuscle by remember { mutableStateOf(false) }

    val validated = name.trim().length in 2..90 &&
        muscle.trim().length in 2..40 &&
        (sets.toIntOrNull() ?: 0) in 1..8 &&
        target.trim().length in 1..32 &&
        (rest.toIntOrNull() ?: 0) in 30..300

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isNew) "Nuovo esercizio" else "Modifica esercizio") },
        text = {
            Column(Modifier.heightIn(max = 510.dp).verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = name, onValueChange = { if (it.length <= 90) name = it },
                    label = { Text("Nome esercizio") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                Box {
                    OutlinedButton(
                        onClick = { chooseMuscle = true },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Muscolo: $muscle", color = PulseColors.Accent) }
                    DropdownMenu(
                        expanded = chooseMuscle,
                        onDismissRequest = { chooseMuscle = false }
                    ) {
                        planMuscles.forEach { choice ->
                            DropdownMenuItem(
                                text = { Text(choice) },
                                onClick = { muscle = choice; chooseMuscle = false }
                            )
                        }
                    }
                }
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    OutlinedTextField(
                        value = sets,
                        onValueChange = { if (it.length <= 2 && it.all(Char::isDigit)) sets = it },
                        label = { Text("Serie") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = target,
                        onValueChange = { if (it.length <= 32) target = it },
                        label = { Text(if (timed) "Secondi" else "Rep (8–12)") },
                        singleLine = true,
                        modifier = Modifier.weight(1.5f)
                    )
                }
                Spacer(Modifier.height(7.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    OutlinedTextField(
                        value = rest,
                        onValueChange = { if (it.length <= 3 && it.all(Char::isDigit)) rest = it },
                        label = { Text("Recupero (sec)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true, modifier = Modifier.weight(1.5f)
                    )
                    OutlinedTextField(
                        value = rir,
                        onValueChange = { if (it.length <= 16) rir = it },
                        label = { Text("RIR") },
                        singleLine = true, modifier = Modifier.weight(1f)
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Esercizio facoltativo", fontSize = 12.sp,
                        color = PulseColors.Foreground, modifier = Modifier.weight(1f))
                    Switch(checked = optional, onCheckedChange = { optional = it })
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Durata in secondi", fontSize = 12.sp,
                        color = PulseColors.Foreground, modifier = Modifier.weight(1f))
                    Switch(checked = timed, onCheckedChange = { timed = it })
                }
                Text("Da 1 a 8 serie · recupero 30–300 secondi",
                    color = PulseColors.Muted, fontSize = 11.sp)
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val selectedMuscle = muscle.trim().uppercase()
                    val mappingChanged = isNew || selectedMuscle != exercise.muscle.uppercase()
                    onSave(exercise.copy(
                        name = name.trim(), muscle = selectedMuscle,
                        sets = sets.toInt(), target = target.trim(), restSeconds = rest.toInt(),
                        rir = rir.trim(), optional = optional, timeBased = timed,
                        primaryMuscles = if (mappingChanged)
                            CustomPlanRules.muscleIds(selectedMuscle) else exercise.primaryMuscles,
                        secondaryMuscles = if (mappingChanged) emptyList()
                            else exercise.secondaryMuscles
                    ))
                },
                enabled = validated,
                colors = ButtonDefaults.buttonColors(containerColor = PulseColors.Accent)
            ) { Text("Conferma", color = Color.White) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Annulla", color = PulseColors.Accent) }
        }
    )
}
