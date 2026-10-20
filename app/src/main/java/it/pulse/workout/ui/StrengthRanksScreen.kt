package it.pulse.workout.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import it.pulse.workout.data.*
import java.util.Locale

/** Prestazione dimostrata: il badge NON deriva da XP, streak o numero di serie. */
@Composable
fun StrengthRanksScreen(
    history: List<SessionRecord>,
    bodyWeightKg: Double?,
    onBack: () -> Unit,
    onSelectMuscle: (String) -> Unit,
    onEditWeight: () -> Unit
) {
    val ranks = remember(history, bodyWeightKg) {
        StrengthRanking.evaluate(history, bodyWeightKg)
            .sortedWith(compareByDescending<MuscleStrengthRank> { it.ratio ?: -1.0 }
                .thenBy { it.group.label })
    }
    val format = remember { java.text.DecimalFormat("0.00") }
    LazyColumn(
        modifier = Modifier.fillMaxSize().background(PulseColors.Background),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { BGBackHeader("Myself", "Rank di forza", onBack) }
        item {
            BGCard {
                Text("🏆 La tua classifica", style = MaterialTheme.typography.titleLarge,
                    color = PulseColors.Foreground)
                Spacer(Modifier.height(6.dp))
                Text("I rank vengono dalla migliore serie registrata per ogni muscolo: più serie uguali non alzano il badge.",
                    style = MaterialTheme.typography.bodyMedium, color = PulseColors.Muted)
                Spacer(Modifier.height(6.dp))
                Text("Calcolo indicativo: 1RM stimato dalla serie (1–20 rip.) diviso per un riferimento interno dell'esercizio proporzionato al tuo peso. Macchine diverse e carichi per manubrio possono dare risultati non confrontabili.",
                    color = PulseColors.Muted, style = MaterialTheme.typography.bodySmall)
            }
        }
        if (!StrengthRanking.hasBodyWeight(bodyWeightKg)) {
            item {
                BGCard {
                    Text("⚖️ Inserisci il peso corporeo per sbloccare i badge.",
                        color = PulseColors.Foreground,
                        style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    BGPrimaryButton("Imposta peso", onEditWeight)
                }
            }
        }
        item {
            Text("I tuoi 16 gruppi muscolari", color = PulseColors.Foreground,
                style = MaterialTheme.typography.titleMedium)
        }
        items(ranks, key = { it.group.id }) { rank ->
            val tier = rank.tier
            val badgeColor = if (tier == null) PulseColors.Muted else Color(tier.colorHex.toULong())
            Surface(
                modifier = Modifier.fillMaxWidth()
                    .clickable { onSelectMuscle(rank.group.id) },
                shape = RoundedCornerShape(19.dp),
                color = PulseColors.Surface,
                border = BorderStroke(1.dp, PulseColors.Border)
            ) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.size(46.dp).clip(CircleShape)
                        .background(badgeColor.copy(alpha = .13f)),
                        contentAlignment = Alignment.Center) {
                        Text(tier?.symbol ?: "—", fontSize = 23.sp)
                    }
                    Column(Modifier.weight(1f)) {
                        Text(rank.group.label, style = MaterialTheme.typography.titleSmall,
                            color = PulseColors.Foreground)
                        Text(tier?.name ?: "Non classificato", color = badgeColor,
                            style = MaterialTheme.typography.labelLarge)
                        Text(rank.evidence ?: "Nessuna serie valida e confrontabile",
                            color = PulseColors.Muted, style = MaterialTheme.typography.bodySmall,
                            maxLines = 2)
                    }
                    if (rank.ratio != null) {
                        Text(format.format(rank.ratio) + "×",
                            color = badgeColor, fontWeight = FontWeight.Bold,
                            fontSize = 16.sp)
                    }
                }
            }
        }
        item {
            BGCard {
                Text("Legenda · dal livello più basso al più alto",
                    color = PulseColors.Foreground, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                StrengthRanking.tiers.forEach { tier ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(35.dp).clip(CircleShape)
                            .background(Color(tier.colorHex.toULong()).copy(alpha = .13f)),
                            contentAlignment = Alignment.Center) {
                            Text(tier.symbol, fontSize = 18.sp)
                        }
                        Spacer(Modifier.width(10.dp))
                        Text(tier.name, modifier = Modifier.weight(1f),
                            color = PulseColors.Foreground)
                        Text("da " + String.format(Locale.ITALIAN, "%.2f", tier.minRatio) + "×",
                            color = PulseColors.Muted, style = MaterialTheme.typography.labelMedium)
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text("Non classificato = peso non inserito, serie assenti o esercizio senza un riferimento affidabile. I rank sono una gamification personalizzata, non standard scientifici di forza.",
                    color = PulseColors.Muted, style = MaterialTheme.typography.bodySmall)
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}
