package it.pulse.workout.ui

import android.app.TimePickerDialog
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import it.pulse.workout.data.*
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private val clockFormat = DateTimeFormatter.ofPattern("HH:mm")
private val dayLabels = mapOf(
    DayOfWeek.MONDAY to "Lun", DayOfWeek.TUESDAY to "Mar",
    DayOfWeek.WEDNESDAY to "Mer", DayOfWeek.THURSDAY to "Gio",
    DayOfWeek.FRIDAY to "Ven", DayOfWeek.SATURDAY to "Sab",
    DayOfWeek.SUNDAY to "Dom"
)

/** Changes are staged until Save: Back never silently commits half a configuration. */
@Composable
fun PersonalSettingsScreen(
    settings: PersonalSettings,
    latestWeightKg: Double?,
    onSave: (PersonalSettings, Double?) -> String?,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var draft by remember(settings) { mutableStateOf(settings) }
    var weight by remember(latestWeightKg) {
        mutableStateOf(latestWeightKg?.let {
            String.format(Locale.ITALIAN, "%.1f", it)
        }.orEmpty())
    }
    var error by remember { mutableStateOf<String?>(null) }
    var saved by remember { mutableStateOf(false) }

    fun chooseTime(current: LocalTime, apply: (LocalTime) -> Unit) {
        TimePickerDialog(context, { _, hours, minutes ->
            apply(LocalTime.of(hours, minutes)); error = null; saved = false
        }, current.hour, current.minute, true).show()
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(PulseColors.Background),
        contentPadding = PaddingValues(BGLayout.pageHorizontal),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            BGBackHeader("Myself / profilo", "Impostazioni personali", onBack)
        }
        item {
            SettingsPanel("Obiettivo") {
                FitnessGoal.entries.forEach { goal ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(
                            selected = draft.goal == goal,
                            onClick = { draft = draft.copy(goal = goal); saved = false },
                            colors = RadioButtonDefaults.colors(selectedColor = PulseColors.Accent)
                        )
                        Text(goal.label, color = PulseColors.Foreground, fontSize = 13.sp)
                    }
                }
            }
        }
        item {
            SettingsPanel("Il tuo ritmo") {
                Text("Allenamenti a settimana", color = PulseColors.Foreground,
                    fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${draft.weeklyWorkouts}", color = PulseColors.Accent,
                        fontSize = 32.sp, fontWeight = FontWeight.ExtraBold)
                    Spacer(Modifier.width(12.dp))
                    Slider(
                        value = draft.weeklyWorkouts.toFloat(),
                        onValueChange = {
                            draft = draft.copy(weeklyWorkouts = it.toInt().coerceIn(1, 7))
                            saved = false
                        },
                        valueRange = 1f..7f,
                        steps = 5,
                        modifier = Modifier.weight(1f)
                    )
                }
                Text("Personalizza il traguardo delle sedute nella dashboard. " +
                    "Le sessioni bonus possono contribuire al conteggio.",
                    color = PulseColors.Muted, fontSize = 11.sp)
                Spacer(Modifier.height(14.dp))
                OutlinedTextField(
                    value = weight,
                    onValueChange = { if (it.length <= 7) { weight = it; saved = false } },
                    label = { Text("Peso attuale (kg)") },
                    supportingText = { Text("Modificando il peso registrerai una pesata di oggi nel diario fisico.") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
        item {
            SettingsPanel("Promemoria durante il lavoro") {
                Text("Giorni attivi", color = PulseColors.Foreground,
                    fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                Spacer(Modifier.height(6.dp))
                listOf(DayOfWeek.entries.take(4), DayOfWeek.entries.drop(4)).forEach { days ->
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        days.forEach { day ->
                            FilterChip(
                                selected = day in draft.reminderDays,
                                onClick = {
                                    draft = draft.copy(reminderDays =
                                        if (day in draft.reminderDays) draft.reminderDays - day
                                        else draft.reminderDays + day)
                                    saved = false
                                },
                                label = { Text(dayLabels.getValue(day), fontSize = 11.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = PulseColors.Accent,
                                    selectedLabelColor = PulseColors.DarkInk
                                )
                            )
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text("Fascia oraria", color = PulseColors.Foreground,
                    fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                Spacer(Modifier.height(7.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(
                        onClick = { chooseTime(draft.reminderStart) {
                            draft = draft.copy(reminderStart = it) } },
                        modifier = Modifier.weight(1f),
                        border = BorderStroke(1.dp, PulseColors.Border)
                    ) { Text("Da ${draft.reminderStart.format(clockFormat)}",
                        fontSize = 12.sp, color = PulseColors.Accent) }
                    OutlinedButton(
                        onClick = { chooseTime(draft.reminderEnd) {
                            draft = draft.copy(reminderEnd = it) } },
                        modifier = Modifier.weight(1f),
                        border = BorderStroke(1.dp, PulseColors.Border)
                    ) { Text("A ${draft.reminderEnd.format(clockFormat)}",
                        fontSize = 12.sp, color = PulseColors.Accent) }
                }
                Text("Acqua ogni 30 minuti, movimento ogni ora a partire da un'ora dopo l'inizio. " +
                    "I promemoria possono arrivare in ritardo per i limiti Android.",
                    color = PulseColors.Muted, fontSize = 11.sp)
            }
        }
        item {
            SettingsPanel("Aspetto e interazioni") {
                Text("Tema nero · dettagli bianchi", color = PulseColors.Foreground,
                    fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Vibrazioni in allenamento",
                            fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                            color = PulseColors.Foreground)
                        Text("Feedback aptico quando registri una serie o cambi esercizio.",
                            fontSize = 11.sp, color = PulseColors.Muted)
                    }
                    Switch(
                        checked = draft.hapticFeedback,
                        onCheckedChange = {
                            draft = draft.copy(hapticFeedback = it)
                            saved = false
                        }
                    )
                }
            }
        }
        item {
            if (error != null) {
                Text(error.orEmpty(), color = MaterialTheme.colorScheme.error,
                    fontSize = 12.sp)
                Spacer(Modifier.height(8.dp))
            }
            if (saved) {
                Text("Preferenze salvate sul dispositivo.",
                    color = PulseColors.Accent, fontSize = 12.sp)
                Spacer(Modifier.height(8.dp))
            }
            Button(
                onClick = {
                    val result = runCatching {
                        val parsedWeight = if (weight.isBlank()) {
                            require(latestWeightKg == null) {
                                "Per eliminare una pesata usa il diario fisico."
                            }
                            null
                        } else {
                            requireNotNull(weight.trim().replace(',', '.').toDoubleOrNull()) {
                                "Inserisci un peso valido"
                            }.also { require(it.isFinite() && it in 20.0..400.0) {
                                "Peso valido: 20–400 kg"
                            } }
                        }
                        PersonalSettingsRules.validate(draft)
                        onSave(draft, parsedWeight)
                    }
                    error = result.getOrElse { it.message ?: "Impostazioni non valide" }
                    saved = error == null
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = PulseColors.Accent),
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(Icons.Default.Save, null, tint = PulseColors.DarkInk)
                Spacer(Modifier.width(8.dp))
                Text("Salva impostazioni", color = PulseColors.DarkInk,
                    fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(25.dp))
        }
    }
}

@Composable
private fun SettingsPanel(title: String, content: @Composable ColumnScope.() -> Unit) {
    BGCard(padding = 18.dp) {
        Text(title, style = MaterialTheme.typography.titleMedium,
            color = PulseColors.Foreground)
        Spacer(Modifier.height(12.dp))
        content()
    }
}
