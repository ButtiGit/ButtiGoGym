package it.pulse.workout.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import it.pulse.workout.updates.AppUpdateStatus

/** Un solo pannello piccolo: serve un'azione esplicita per scaricare e installare. */
@Composable
fun ReleaseUpdatePanel(
    installedVersion: String,
    status: AppUpdateStatus,
    checking: Boolean,
    downloading: Boolean,
    downloadProgress: Int,
    verified: Boolean,
    onCheck: () -> Unit,
    onDownload: () -> Unit,
    onInstall: () -> Unit
) {
    val newRelease = status as? AppUpdateStatus.Available
    Surface(
        color = if (newRelease != null) PulseColors.SoftGreen else PulseColors.Surface,
        border = BorderStroke(1.dp,
            if (newRelease != null) PulseColors.Accent.copy(alpha = .24f) else PulseColors.Border),
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (newRelease != null) Icons.Default.SystemUpdate else Icons.Default.Security,
                    contentDescription = null,
                    tint = PulseColors.Accent,
                    modifier = Modifier.size(23.dp)
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Aggiornamenti", fontWeight = FontWeight.Bold,
                        color = PulseColors.Foreground, fontSize = 15.sp)
                    Text("Versione installata: $installedVersion",
                        color = PulseColors.Muted, fontSize = 11.sp)
                }
                if (status == AppUpdateStatus.Current) {
                    Icon(Icons.Default.CheckCircle, contentDescription = "App aggiornata",
                        tint = PulseColors.Accent, modifier = Modifier.size(21.dp))
                }
            }

            Spacer(Modifier.height(11.dp))
            val message = when {
                checking -> "Controllo le GitHub Releases…"
                downloading -> "Scaricamento APK verificato: $downloadProgress%"
                newRelease != null -> "Disponibile ButtiGoGym ${newRelease.release.version}"
                status == AppUpdateStatus.NoRelease -> "Ancora nessuna GitHub Release pubblicata."
                status == AppUpdateStatus.Current -> "Hai già l'ultima versione disponibile."
                status is AppUpdateStatus.Error -> status.explanation
                else -> "Controlla GitHub: se c'è una nuova versione, verrà verificata e installata con conferma Android."
            }
            Text(message, color = PulseColors.Foreground,
                fontWeight = if (newRelease != null) FontWeight.SemiBold else FontWeight.Normal,
                fontSize = 12.sp)

            if (downloading) {
                Spacer(Modifier.height(10.dp))
                LinearProgressIndicator(
                    progress = { downloadProgress.coerceIn(0, 100) / 100f },
                    modifier = Modifier.fillMaxWidth(),
                    color = PulseColors.Accent,
                    trackColor = Color.White
                )
            }

            Spacer(Modifier.height(12.dp))
            if (newRelease == null) {
                OutlinedButton(
                    onClick = onCheck, enabled = !checking && !downloading,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(13.dp),
                    border = BorderStroke(1.dp, PulseColors.Border)
                ) {
                    Text("Verifica aggiornamenti", color = PulseColors.Accent,
                        fontWeight = FontWeight.SemiBold)
                }
            } else {
                if (verified) {
                    Button(
                        onClick = onInstall, enabled = !downloading && !checking,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(13.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = PulseColors.Accent)
                    ) { Text("Installa aggiornamento", color = Color.White) }
                } else {
                    Button(
                        onClick = onDownload, enabled = !downloading && !checking,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(13.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = PulseColors.Accent)
                    ) {
                        Icon(Icons.Default.CloudDownload, contentDescription = null,
                            modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Scarica versione ${newRelease.release.version}", color = Color.White)
                    }
                }
                TextButton(onClick = onCheck, enabled = !checking && !downloading) {
                    Text("Controlla di nuovo", color = PulseColors.Accent, fontSize = 11.sp)
                }
            }
            if (newRelease != null) {
                Text("APK da GitHub, firma verificata. Il controllo manuale avvia " +
                    "il download quando disponibile; Android chiede conferma per installare.",
                    color = PulseColors.Muted, fontSize = 10.sp)
            }
        }
    }
}
