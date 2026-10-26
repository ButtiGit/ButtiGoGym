package it.pulse.workout.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import it.pulse.workout.BuildConfig
import it.pulse.workout.data.*
import it.pulse.workout.sharing.*
import kotlinx.coroutines.*

@Composable
fun HistoryUploadPanel(history: List<SessionRecord>, days: List<WorkoutDay>, onClose: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val tokenStore = remember(context) { GitHubTokenStore(context) }
    val uploader = remember(context) { GitHubHistoryUploader(context) }
    val prepared = produceState<HistoryPayload?>(null, history, days) {
        value = withContext(Dispatchers.Default) { WorkoutHistoryPayload.build(history, days, BuildConfig.VERSION_NAME) }
    }
    val payload = prepared.value
    if (payload == null) {
        BGModal("Invia storico", onClose) { Text("Preparazione dello storico…"); LinearProgressIndicator() }
        return
    }
    var linked by remember { mutableStateOf(tokenStore.read() != null) }
    // Credentials must never enter rememberSaveable, the clipboard, logs or history payloads.
    var token by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    var url by remember { mutableStateOf(uploader.lastUrl()) }
    var preview by remember { mutableStateOf(false) }
    fun open(address: String) {
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(address))) }
            .onFailure { status = "Nessuna app disponibile per aprire GitHub" }
    }
    BGModal("Invia storico", { if (!busy) onClose() }) {
        Text("${payload.workouts} allenamenti · ${payload.sets} serie registrate",
            style = MaterialTheme.typography.titleMedium)
        Text("Destinazione: ButtiGit/ButtiGoGym → Issues", color = PulseColors.Muted)
        BGCard {
            Text("Il repository è pubblico", style = MaterialTheme.typography.titleSmall)
            Text("L’invio pubblica date, durata, esercizi, attrezzi, pesi, ripetizioni/secondi e RIR di tutti i WO salvati. " +
                "Non include foto, misure corporee, token o la seduta ancora in corso.", color = PulseColors.Muted)
        }
        TextButton(onClick = { preview = !preview }, enabled = !busy) { Text(if (preview) "Nascondi anteprima" else "Anteprima dei dati") }
        if (preview) {
            Text(payload.json.take(3500) + if (payload.json.length > 3500) "\n… anteprima abbreviata; verrà inviato lo storico completo." else "",
                style = MaterialTheme.typography.bodySmall, color = PulseColors.Muted)
        }
        if (!linked) {
            Text("Collega GitHub una volta", style = MaterialTheme.typography.titleMedium)
            Text("Crea un token fine-grained con accesso solo a ButtiGoGym e permesso Issues: Read and write. " +
                "Non servono permessi sul codice. Il token viene salvato cifrato su questo telefono.",
                color = PulseColors.Muted, style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = {
                open("https://github.com/settings/personal-access-tokens/new?name=ButtiGoGym%20storico&target_name=ButtiGit&issues=write&expires_in=90")
            }) { Text("Crea token GitHub") }
            OutlinedTextField(token, { token = it }, label = { Text("Token GitHub") },
                visualTransformation = PasswordVisualTransformation(), singleLine = true,
                modifier = Modifier.fillMaxWidth())
            BGPrimaryButton("Collega GitHub", {
                runCatching { tokenStore.save(token.trim()) }.onSuccess {
                    token = ""; linked = true; status = "GitHub collegato. Nessun dato è stato ancora inviato."
                }.onFailure { status = "Impossibile salvare il token. Verifica il valore e riprova." }
            }, enabled = token.trim().length in 20..512)
        } else {
            Text("GitHub collegato", color = PulseColors.Foreground)
            BGPrimaryButton(if (busy) "Invio in corso…" else "Invia storico su GitHub pubblico", {
                busy = true; status = "Preparazione dell’invio…"
                scope.launch {
                    try {
                        val credential = tokenStore.read() ?: error("Ricollega GitHub prima di inviare.")
                        val result = withContext(Dispatchers.IO) {
                            uploader.send(payload, credential) { message ->
                                scope.launch { status = message }
                            }
                        }
                        url = result; status = "Invio completo. Il report contiene tutti i ${payload.workouts} allenamenti."
                    } catch (error: Exception) {
                        url = uploader.lastUrl()
                        status = if (error is java.io.IOException) "Connessione interrotta. Premi di nuovo Invia per riprendere il report."
                            else error.message ?: "Invio non completato. Puoi riprovare."
                    } finally { busy = false }
                }
            }, enabled = !busy && payload.workouts > 0)
            TextButton(onClick = { tokenStore.clear(); linked = false; token = ""; status = "Collegamento rimosso dal telefono." },
                enabled = !busy) { Text("Rimuovi collegamento") }
        }
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        status?.let { Text(it, color = PulseColors.Foreground, style = MaterialTheme.typography.bodyMedium) }
        url?.let { address -> TextButton(onClick = { open(address) }, enabled = !busy) { Text("Apri ultimo report GitHub") } }
        if (payload.workouts == 0) Text("Completa e salva un allenamento prima di inviare lo storico.", color = PulseColors.Muted)
    }
}
