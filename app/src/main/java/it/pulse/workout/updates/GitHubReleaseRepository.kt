package it.pulse.workout.updates

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

data class AppRelease(
    val tag: String,
    val version: AppVersion,
    val apkUrl: String,
    val apkName: String,
    val sizeBytes: Long,
    val sha256: String?,
    val releaseUrl: String
)

sealed interface AppUpdateStatus {
    data object NotChecked : AppUpdateStatus
    data object NoRelease : AppUpdateStatus
    data object Current : AppUpdateStatus
    data class Available(val release: AppRelease) : AppUpdateStatus
    data class Error(val explanation: String) : AppUpdateStatus
}

/**
 * GitHub Releases è l'unica fonte; commit, Actions artifacts e tag privi
 * di una Release pubblicata non sono considerati installabili.
 */
class GitHubReleaseRepository(context: Context) {
    private val app = context.applicationContext
    private val prefs = app.getSharedPreferences("buttigogym_release_updates_v1", Context.MODE_PRIVATE)

    companion object {
        const val LATEST_RELEASE_API =
            "https://api.github.com/repos/ButtiGit/ButtiGoGym/releases/latest"
        private const val ASSET_PREFIX =
            "https://github.com/ButtiGit/ButtiGoGym/releases/download/"
        private const val MAX_RELEASE_JSON = 256 * 1024
        const val MAX_APK_BYTES = 120L * 1024L * 1024L
        private const val CACHE_FOR_MS = 24 * 60 * 60 * 1000L
    }

    fun installedVersion(): String {
        val packageInfo = app.packageManager.getPackageInfo(app.packageName, 0)
        return packageInfo.versionName.orEmpty().ifBlank { "0.0.0" }
    }

    fun cachedOrNotChecked(): AppUpdateStatus {
        val checked = prefs.getLong("checked_at", 0)
        if (checked <= 0L) return AppUpdateStatus.NotChecked
        val json = prefs.getString("release_json", null)
        if (json == null) return AppUpdateStatus.NoRelease
        return runCatching { interpret(JSONObject(json)) }
            .getOrElse { AppUpdateStatus.NotChecked }
    }

    /** Da chiamare su Dispatchers.IO. Controllo automatico al massimo ogni 24h. */
    fun check(force: Boolean = false): AppUpdateStatus {
        val timestamp = System.currentTimeMillis()
        val last = prefs.getLong("checked_at", 0L)
        if (!force && last in 1..timestamp && timestamp - last < CACHE_FOR_MS) {
            val cached = cachedOrNotChecked()
            if (cached != AppUpdateStatus.NotChecked) return cached
        }
        val connection = URL(LATEST_RELEASE_API).openConnection() as HttpURLConnection
        connection.connectTimeout = 7_000
        connection.readTimeout = 9_000
        connection.instanceFollowRedirects = false
        connection.setRequestProperty("Accept", "application/vnd.github+json")
        connection.setRequestProperty("User-Agent", "ButtiGoGym-Android-UpdateChecker")
        connection.setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
        try {
            return when (val response = connection.responseCode) {
                HttpURLConnection.HTTP_NOT_FOUND -> {
                    prefs.edit().putLong("checked_at", timestamp)
                        .remove("release_json").apply()
                    AppUpdateStatus.NoRelease
                }
                HttpURLConnection.HTTP_OK -> {
                    val body = connection.inputStream.use { input ->
                        val out = ByteArrayOutputStream()
                        val bytes = ByteArray(4096)
                        while (true) {
                            val n = input.read(bytes)
                            if (n == -1) break
                            require(out.size() + n <= MAX_RELEASE_JSON) {
                                "Risposta GitHub troppo grande"
                            }
                            out.write(bytes, 0, n)
                        }
                        out.toString(Charsets.UTF_8.name())
                    }
                    val candidate = JSONObject(body)
                    val update = interpret(candidate)
                    if (update !is AppUpdateStatus.Error) {
                        prefs.edit().putLong("checked_at", timestamp)
                            .putString("release_json", body).apply()
                    }
                    update
                }
                403, 429 -> AppUpdateStatus.Error(
                    "Limite temporaneo di GitHub. Riprova più tardi."
                )
                else -> AppUpdateStatus.Error("Impossibile verificare le release (HTTP $response)")
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun interpret(json: JSONObject): AppUpdateStatus {
        if (json.optBoolean("draft", false) || json.optBoolean("prerelease", false)) {
            return AppUpdateStatus.NoRelease
        }
        val tag = json.optString("tag_name", "")
        val version = AppVersion.parse(tag)
            ?: return AppUpdateStatus.Error("Formato versione GitHub non riconosciuto")
        val current = AppVersion.parse(installedVersion())
            ?: return AppUpdateStatus.Error("Versione installata non riconosciuta")
        if (version <= current) return AppUpdateStatus.Current

        val releases = json.optJSONArray("assets")
            ?: return AppUpdateStatus.Error("Release senza APK allegata")
        val matches = (0 until releases.length()).mapNotNull { index ->
            releases.optJSONObject(index)
        }.filter { asset ->
            val name = asset.optString("name", "")
            name.endsWith(".apk", ignoreCase = true) &&
                !name.contains("debug", ignoreCase = true) &&
                (name.startsWith("ButtiGoGym", ignoreCase = true) ||
                    name.equals("app-release.apk", ignoreCase = true))
        }
        if (matches.size != 1) {
            return AppUpdateStatus.Error(
                if (matches.isEmpty()) "La release è pubblicata ma non contiene un APK STABLE"
                else "La release contiene più APK: impossibile scegliere in sicurezza"
            )
        }
        val asset = matches.single()
        val apkUrl = asset.optString("browser_download_url", "")
        if (!validAssetUrl(apkUrl, tag)) {
            return AppUpdateStatus.Error("Indirizzo APK non valido nella release")
        }
        val size = asset.optLong("size", 0)
        if (size !in 1..MAX_APK_BYTES) {
            return AppUpdateStatus.Error("Dimensione APK non valida")
        }
        val hashText = asset.optString("digest", "")
        if (hashText.isNotBlank() && hashText != "null" &&
            !Regex("^sha256:[A-Fa-f0-9]{64}$").matches(hashText)) {
            return AppUpdateStatus.Error("Hash del file APK non riconosciuto")
        }
        val releaseUrl = json.optString("html_url", "")
        val expectedReleaseUrl = "https://github.com/ButtiGit/ButtiGoGym/releases/tag/$tag"
        if (releaseUrl != expectedReleaseUrl) {
            return AppUpdateStatus.Error("Release pubblicata da una sorgente non riconosciuta")
        }
        return AppUpdateStatus.Available(
            AppRelease(tag, version, apkUrl, asset.getString("name"), size,
                hashText.takeIf { it.startsWith("sha256:") }?.substringAfter("sha256:"),
                releaseUrl)
        )
    }

    private fun validAssetUrl(url: String, tag: String): Boolean {
        val expectedPrefix = "$ASSET_PREFIX$tag/"
        return url.startsWith(expectedPrefix) &&
            url.length > expectedPrefix.length &&
            runCatching {
                val uri = java.net.URI(url)
                uri.scheme == "https" && uri.host == "github.com" &&
                    uri.userInfo == null && uri.port == -1 &&
                    uri.query == null && uri.fragment == null
            }.getOrDefault(false)
    }
}
