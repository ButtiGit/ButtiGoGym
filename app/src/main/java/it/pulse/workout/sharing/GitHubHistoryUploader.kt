package it.pulse.workout.sharing

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.io.ByteArrayOutputStream

fun interface GitHubHistoryTransport {
    fun request(method: String, path: String, token: String, body: String?): String
}

class GitHubHistoryHttp : GitHubHistoryTransport {
    override fun request(method: String, path: String, token: String, body: String?): String {
        require(path.matches(Regex("issues(?:/[0-9]+/comments(?:\\?per_page=100&page=[0-9]+)?)?")))
        val connection = URL("https://api.github.com/repos/ButtiGit/ButtiGoGym/$path").openConnection() as HttpURLConnection
        connection.connectTimeout = 10_000; connection.readTimeout = 20_000
        connection.instanceFollowRedirects = false
        connection.requestMethod = method
        connection.setRequestProperty("Authorization", "Bearer $token")
        connection.setRequestProperty("Accept", "application/vnd.github+json")
        connection.setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
        connection.setRequestProperty("User-Agent", "ButtiGoGym-Workout-History")
        try {
            if (body != null) {
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            }
            val status = connection.responseCode
            check(status in 200..299) { when (status) {
                401 -> "Token scaduto o non valido. Ricollega GitHub."
                403, 429 -> "GitHub ha rifiutato l’invio: controlla il permesso Issues in scrittura o riprova più tardi."
                404 -> "Repository o permessi Issues non disponibili per questo token."
                422 -> "GitHub non ha accettato i dati. Riprova più tardi."
                else -> "Invio GitHub non completato (HTTP $status). Puoi riprendere."
            } }
            return connection.inputStream.use { input ->
                val out = ByteArrayOutputStream(); val buffer = ByteArray(8192)
                while (true) {
                    val n = input.read(buffer); if (n < 0) break
                    require(out.size() + n <= 8 * 1024 * 1024) { "Risposta GitHub troppo grande" }
                    out.write(buffer, 0, n)
                }
                out.toString("UTF-8")
            }
        } finally { connection.disconnect() }
    }
}

/** Explicit user-triggered upload. Resume by content hash; markers prevent duplicate parts after a timeout. */
class GitHubHistoryUploader(context: Context, private val transport: GitHubHistoryTransport = GitHubHistoryHttp()) {
    private val prefs = context.applicationContext.getSharedPreferences("github_history_upload", Context.MODE_PRIVATE)
    companion object {
        const val REPOSITORY = "https://github.com/ButtiGit/ButtiGoGym"
        private val uploadLock = Any()
    }
    fun lastUrl(): String? = prefs.getLong("issue", 0).takeIf { it > 0 }?.let { "$REPOSITORY/issues/$it" }
    private fun store(hash: String, issue: Long) {
        check(prefs.edit().putString("hash", hash).putLong("issue", issue).commit()) { "Impossibile memorizzare l’invio" }
    }
    fun send(payload: HistoryPayload, token: String, progress: (String) -> Unit = {}): String =
        synchronized(uploadLock) { sendLocked(payload, token, progress) }
    private fun sendLocked(payload: HistoryPayload, token: String, progress: (String) -> Unit): String {
        require(payload.workouts > 0) { "Nessun allenamento da inviare" }
        val parts = payload.parts()
        var issue = if (prefs.getString("hash", null) == payload.hash) prefs.getLong("issue", 0) else 0L
        val complete = "<!-- buttigogym:${payload.hash}:complete -->"
        val existing = mutableSetOf<Int>()
        var alreadyComplete = false
        if (issue > 0) {
            progress("Controllo dell’invio precedente…")
            var page = 1
            while (true) {
                val comments = JSONArray(transport.request("GET", "issues/$issue/comments?per_page=100&page=$page", token, null))
                for (i in 0 until comments.length()) {
                    val text = comments.getJSONObject(i).optString("body")
                    if (complete in text) alreadyComplete = true
                    parts.indices.forEach { index ->
                        if ("<!-- buttigogym:${payload.hash}:part:${index + 1} -->" in text &&
                            "```base64\n${parts[index]}\n```" in text) existing += index
                    }
                }
                if (comments.length() < 100) break
                page++; check(page <= 100) { "Troppi commenti nella precedente esportazione" }
            }
        } else {
            progress("Creazione del report GitHub…")
            val body = "Esportazione volontaria dello storico ButtiGoGym.\n\n" +
                "Allenamenti: ${payload.workouts}; serie registrate: ${payload.sets}.\n\n" +
                "Formato: JSON UTF-8, parti Base64 ordinate nei commenti. Decodificare ogni parte e concatenare i byte.\n\n" +
                "SHA-256 dell’archivio JSON completo: `${payload.hash}`\n\nParti attese: ${parts.size}.\n\n" +
                "Usare i dati per l’analisi solo dopo il commento **COMPLETO**. Foto, misure corporee e token non sono inclusi."
            val result = JSONObject(transport.request("POST", "issues", token,
                JSONObject().put("title", "[Storico WO] ${payload.workouts} allenamenti · ${payload.hash.take(10)}")
                    .put("body", body).toString()))
            issue = result.getLong("number")
            check(issue > 0 && result.getString("html_url") == "$REPOSITORY/issues/$issue") { "Risposta GitHub non riconosciuta" }
            store(payload.hash, issue)
        }
        if (alreadyComplete && existing.size == parts.size) return "$REPOSITORY/issues/$issue"
        parts.forEachIndexed { index, encoded ->
            if (index !in existing) {
                progress("Invio dati ${index + 1}/${parts.size}…")
                val body = "<!-- buttigogym:${payload.hash}:part:${index + 1} -->\n" +
                    "Parte ${index + 1}/${parts.size} · JSON UTF-8 in Base64\n\n```base64\n$encoded\n```"
                transport.request("POST", "issues/$issue/comments", token, JSONObject().put("body", body).toString())
            }
        }
        transport.request("POST", "issues/$issue/comments", token,
            JSONObject().put("body", "$complete\n**COMPLETO** · ${payload.workouts} allenamenti · ${parts.size} parti · SHA-256 `${payload.hash}`").toString())
        return "$REPOSITORY/issues/$issue"
    }
}
