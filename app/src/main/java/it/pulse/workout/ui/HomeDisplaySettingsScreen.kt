package it.pulse.workout.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import it.pulse.workout.data.HomeDisplaySettings
import it.pulse.workout.updates.AppUpdateStatus

/**
 * Tutti gli strumenti importanti restano accessibili anche quando i
 * rispettivi widget sono nascosti nella Home. Non tocca i dati sportivi.
 */
@Composable
fun HomeDisplaySettingsScreen(
    settings: HomeDisplaySettings,
    onSettings: (HomeDisplaySettings) -> Unit,
    onBack: () -> Unit,
    installedVersion: String,
    updateStatus: AppUpdateStatus,
    checking: Boolean,
    downloading: Boolean,
    downloadProgress: Int,
    verified: Boolean,
    onCheckUpdate: () -> Unit,
    onDownloadUpdate: () -> Unit,
    onInstallUpdate: () -> Unit,
    backupStatus: String,
    backupBusy: Boolean,
    onExportBackup: () -> Unit,
    onImportBackup: () -> Unit,
    waterEnabled: Boolean,
    moveEnabled: Boolean,
    onWaterChanged: (Boolean) -> Unit,
    onMoveChanged: (Boolean) -> Unit,
    onTestWater: () -> Unit,
    onTestMove: () -> Unit,
    remindersPermissionGranted: Boolean,
    onEnableReminders: () -> Unit
) {
    var failure by remember { mutableStateOf<String?>(null) }

    fun apply(value: HomeDisplaySettings) {
        runCatching { onSettings(value) }
            .onSuccess { failure = null }
            .onFailure { failure = it.message ?: "Impostazione non salvata" }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            BGBackHeader("PERSONALIZZA", "La tua Home", onBack)
            Text("Scegli solo ciò che vuoi vedere. Nessun dato viene cancellato.",
                color = PulseColors.Muted, fontSize = 13.sp)
        }
        item {
            BGCard {
                Text("Mostra nella Home", color = PulseColors.Foreground,
                    fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(10.dp))
                @Composable
                fun HomeSwitch(title: String, checked: Boolean, onChange: (Boolean) -> Unit) {
                    Row(
                        modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(title, modifier = Modifier.weight(1f),
                            color = PulseColors.Foreground, fontSize = 14.sp)
                        Switch(checked = checked, onCheckedChange = onChange,
                            colors = SwitchDefaults.colors(
                                checkedTrackColor = PulseColors.Accent))
                    }
                }
                HomeSwitch("Missioni", settings.missions) { apply(settings.copy(missions = it)) }
                HomeSwitch("Report settimanale", settings.weeklyReport) { apply(settings.copy(weeklyReport = it)) }
                HomeSwitch("Statistiche dettagliate", settings.details) { apply(settings.copy(details = it)) }
                HomeSwitch("Streak", settings.streaks) { apply(settings.copy(streaks = it)) }
                HomeSwitch("Passi", settings.steps) { apply(settings.copy(steps = it)) }
                HomeSwitch("Recupero", settings.recovery) { apply(settings.copy(recovery = it)) }
                HomeSwitch("Programma della settimana", settings.weekPlan) { apply(settings.copy(weekPlan = it)) }
                HomeSwitch("Promemoria", settings.reminders) { apply(settings.copy(reminders = it)) }
                HomeSwitch("Backup", settings.backup) { apply(settings.copy(backup = it)) }
                if (settings.shownCount() > 0) {
                    Spacer(Modifier.height(5.dp))
                    TextButton(onClick = { apply(HomeDisplaySettings()) }) {
                        Text("Torna alla Home essenziale", color = PulseColors.Accent)
                    }
                }
                if (failure != null) Text(failure.orEmpty(),
                    color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
            }
        }
        item {
            Text("AGGIORNAMENTI", color = PulseColors.Accent,
                fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            ReleaseUpdatePanel(
                installedVersion, updateStatus, checking, downloading,
                downloadProgress, verified, onCheckUpdate, onDownloadUpdate, onInstallUpdate
            )
        }
        item {
            BGCard {
                Text("Notifiche", color = PulseColors.Foreground,
                    fontSize = 17.sp, fontWeight = FontWeight.Bold)
                HomeToggleRow("Bere acqua", waterEnabled, onWaterChanged)
                HomeToggleRow("Muoversi", moveEnabled, onMoveChanged)
                if (!remindersPermissionGranted && (waterEnabled || moveEnabled)) {
                    TextButton(onClick = onEnableReminders) {
                        Text("Consenti notifiche", color = PulseColors.Accent)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (waterEnabled) TextButton(onClick = onTestWater) {
                        Text("Prova acqua", color = PulseColors.Accent)
                    }
                    if (moveEnabled) TextButton(onClick = onTestMove) {
                        Text("Prova movimento", color = PulseColors.Accent)
                    }
                }
            }
        }
        item {
            BGCard {
                Text("I tuoi dati", fontSize = 17.sp,
                    fontWeight = FontWeight.Bold, color = PulseColors.Foreground)
                Spacer(Modifier.height(5.dp))
                Text("Conserva una copia ZIP prima di cambiare telefono.",
                    color = PulseColors.Muted, fontSize = 12.sp)
                Spacer(Modifier.height(10.dp))
                Button(onClick = onExportBackup, enabled = !backupBusy,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = PulseColors.Accent)) {
                    Text("Esporta backup", color = Color.White)
                }
                Spacer(Modifier.height(5.dp))
                OutlinedButton(onClick = onImportBackup, enabled = !backupBusy,
                    modifier = Modifier.fillMaxWidth(),
                    border = BorderStroke(1.dp, PulseColors.Border),
                    shape = RoundedCornerShape(13.dp)) {
                    Text("Ripristina backup", color = PulseColors.Accent)
                }
                if (backupStatus.isNotBlank()) Text(backupStatus,
                    color = PulseColors.Muted, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun HomeToggleRow(
    name: String, checked: Boolean, onChanged: (Boolean) -> Unit
) {
    Row(Modifier.fillMaxWidth().heightIn(min = 52.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Text(name, modifier = Modifier.weight(1f),
            color = PulseColors.Foreground, fontSize = 14.sp)
        Switch(checked = checked, onCheckedChange = onChanged,
            colors = SwitchDefaults.colors(checkedTrackColor = PulseColors.Accent))
    }
}
