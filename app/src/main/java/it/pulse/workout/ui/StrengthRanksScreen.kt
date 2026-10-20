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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.animation.core.tween
import androidx.compose.animation.animateContentSize
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType

/** ARGB Long must use Color(Long), never the packed Compose Color(ULong) constructor. */
internal fun strengthTierColor(tier: StrengthTier): Color = Color(tier.colorHex)

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
            BGIdentityCard("STRENGTH CLUB", "La tua forza, dimostrata.",
                "${ranks.count { it.tier != null }} / ${ranks.size} muscoli classificati",
                ranks.firstOrNull { it.tier != null }?.tier?.symbol ?: "✦")
        }
        item {
            Text("La migliore serie decide il badge. XP e frequenza restano separati.",
                color = PulseColors.Muted, style = MaterialTheme.typography.bodyMedium)
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
            val badgeColor = if (tier == null) PulseColors.Muted else strengthTierColor(tier)
            var expanded by rememberSaveable(rank.group.id) { mutableStateOf(false) }
            val haptics = LocalHapticFeedback.current
            BGCard(modifier = Modifier.animateContentSize(tween(BGMotion.duration(240)))) {
                Row(Modifier.fillMaxWidth().clickable {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    expanded = !expanded
                }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    BGRankMedallion(tier?.symbol ?: "?", badgeColor)
                    Column(Modifier.weight(1f)) {
                        Text(rank.group.label, style = MaterialTheme.typography.titleSmall,
                            color = PulseColors.Foreground)
                        Text(tier?.name ?: "Non classificato", color = PulseColors.Foreground,
                            style = MaterialTheme.typography.labelLarge)
                        Text(if (expanded) "Chiudi dettagli −" else "Prestazione e prossimo badge +",
                            color = PulseColors.Muted, style = MaterialTheme.typography.labelSmall)
                    }
                    rank.ratio?.let {
                        Text(format.format(it) + "×", color = PulseColors.Foreground,
                            fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                }
                if (expanded) {
                    Spacer(Modifier.height(12.dp))
                    Text(rank.evidence ?: "Registra una serie valida e inserisci il peso corporeo per iniziare.",
                        color = PulseColors.Muted, style = MaterialTheme.typography.bodyMedium)
                    val ratio = rank.ratio
                    val next = StrengthRanking.tiers.firstOrNull { it.minRatio > (ratio ?: -1.0) }
                    if (ratio != null && tier != null && next != null) {
                        Spacer(Modifier.height(12.dp))
                        BGAnimatedProgress(((ratio - tier.minRatio) / (next.minRatio - tier.minRatio)).toFloat())
                        Spacer(Modifier.height(6.dp))
                        Text("Prossimo: ${next.name} · da ${format.format(next.minRatio)}×",
                            color = PulseColors.Foreground, style = MaterialTheme.typography.labelMedium)
                    } else if (ratio != null && next == null) {
                        Text("Livello massimo · Leggenda", color = PulseColors.Accent)
                    }
                    TextButton(onClick = { onSelectMuscle(rank.group.id) }) { Text("Vedi il muscolo →") }
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
                            .background(strengthTierColor(tier).copy(alpha = .13f)),
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
                Text("Indice: 1RM stimato (1–20 rip.) / riferimento dell’esercizio proporzionato al peso corporeo. Macchine e carichi per manubrio possono non essere confrontabili. Non classificato = peso non inserito, serie assenti o esercizio senza un riferimento affidabile. I rank sono una gamification personalizzata, non standard scientifici di forza.",
                    color = PulseColors.Muted, style = MaterialTheme.typography.bodySmall)
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}
