package it.pulse.workout.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoGraph
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import it.pulse.workout.data.ExerciseProgression
import it.pulse.workout.data.ProgressionDirection
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** RIR percepito dell'ULTIMA serie appena svolta, non il RIR obiettivo. */
@Composable
internal fun ActualRirPicker(
    value: Int?, onValueChange: (Int?) -> Unit,
    compact: Boolean = false
) {
    Column {
        Text("RIR reale · ripetizioni che avevi ancora",
            fontSize = if (compact) 11.sp else 12.sp,
            fontWeight = FontWeight.SemiBold, color = PulseColors.Foreground)
        Spacer(Modifier.height(6.dp))
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            listOf(null, 0, 1, 2, 3, 4, 5).forEach { option ->
                FilterChip(
                    selected = value == option,
                    onClick = { onValueChange(option) },
                    label = {
                        Text(option?.toString() ?: "—",
                            fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    },
                    shape = RoundedCornerShape(12.dp),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = PulseColors.Accent,
                        selectedLabelColor = Color.White),
                    border = BorderStroke(1.dp, PulseColors.Border)
                )
            }
        }
        if (!compact) {
            Text("0 = nessun'altra rep · 2 = circa 2 rep ancora possibili · — = non so",
                fontSize = 10.sp, color = PulseColors.Muted)
        }
    }
}

@Composable
internal fun ProgressionAdvicePanel(advice: ExerciseProgression) {
    // Dettagli su richiesta, così i campi serie rimangono vicini al nome dell'esercizio.
    var expanded by remember(advice.previousDateMillis, advice.direction) {
        mutableStateOf(false)
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = PulseColors.Surface,
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, PulseColors.Border)
    ) {
        Column(Modifier.padding(15.dp)) {
            Row(
                Modifier.fillMaxWidth().clickable { expanded = !expanded },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.AutoGraph, contentDescription = null,
                    tint = PulseColors.Accent, modifier = Modifier.size(19.dp))
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text("Progressione", color = PulseColors.Muted,
                        fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    Text(advice.headline, color = PulseColors.Foreground,
                        fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
                }
                Text(if (expanded) "−" else "+", color = PulseColors.Accent,
                    fontSize = 22.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 8.dp))
                if (advice.direction == ProgressionDirection.CONSIDER_INCREASE) {
                    Surface(color = PulseColors.SoftGreen,
                        shape = RoundedCornerShape(11.dp)) {
                        Text("DA PROVARE", color = PulseColors.Accent,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                            fontWeight = FontWeight.Bold, fontSize = 9.sp)
                    }
                }
            }
            if (expanded) {
                Spacer(Modifier.height(8.dp))
                Text(advice.explanation, color = PulseColors.Muted, fontSize = 12.sp)
            }
            if (expanded && advice.previousSets.isNotEmpty()) {
                Spacer(Modifier.height(13.dp))
                Text("ULTIMA SESSIONE" +
                    (advice.previousDateMillis?.let {
                        " · " + SimpleDateFormat("dd/MM/yyyy", Locale.ITALIAN)
                            .format(Date(it))
                    } ?: ""), color = PulseColors.Accent,
                    fontSize = 10.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(5.dp))
                advice.previousSets.take(8).forEach { set ->
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Serie ${set.number}", color = PulseColors.Muted,
                            fontSize = 11.sp, modifier = Modifier.width(60.dp))
                        Text("${formatKgForWorkout(set.weightKg)} kg × ${set.repetitions}",
                            color = PulseColors.Foreground, fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f))
                        Text(set.actualRir?.let { "RIR $it" } ?: "RIR —",
                            color = PulseColors.Muted, fontSize = 11.sp)
                    }
                }
            }
        }
    }
}

/** UI-only format; calculation always uses Double internally. */
internal fun formatKgForWorkout(kg: Double): String =
    if (kg % 1.0 == 0.0) kg.toLong().toString()
    else String.format(Locale.ITALIAN, "%.2f", kg).trimEnd('0').trimEnd(',')
