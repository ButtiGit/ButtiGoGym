package it.pulse.workout.updates

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.security.MessageDigest
import java.util.Locale

/**
 * File scaricato soltanto da GitHub Releases e verificato PRIMA
 * di passarlo al sistema Android:
 * - digest SHA-256 della release, se dichiarato
 * - nome pacchetto, versione Android e versione semantica
 * - certificato del pacchetto identico a quello già installato
 *
 * Android esegue comunque la verifica finale della firma durante
 * l'installazione, sempre con il consenso dell'utente.
 */
class SafeApkUpdater(context: Context) {
    private val app = context.applicationContext
    private val pm = app.packageManager

    companion object {
        private val redirectHosts = setOf(
            "github.com", "release-assets.githubusercontent.com",
            "objects.githubusercontent.com"
        )
        private const val APK_MIME = "application/vnd.android.package-archive"
    }

    suspend fun download(
        release: AppRelease,
        onProgress: suspend (Int) -> Unit = {}
    ): File = withContext(Dispatchers.IO) {
        val directory = File(app.filesDir, "updates").apply { mkdirs() }
        val target = File(directory, "ButtiGoGym-${release.version}.apk")
        val temporary = File(directory, "ButtiGoGym-${release.version}.part")
        target.delete()
        temporary.delete()

        try {
            val source = openTrustedDownload(release.apkUrl)
            try {
                require(source.responseCode == HttpURLConnection.HTTP_OK) {
                    "GitHub non ha consentito il download (HTTP ${source.responseCode})"
                }
                val headerSize = source.contentLengthLong
                if (headerSize > 0) {
                    require(headerSize == release.sizeBytes) {
                        "Dimensione APK diversa da quella della Release"
                    }
                }
                val hash = MessageDigest.getInstance("SHA-256")
                var downloaded = 0L
                var lastPercent = -1
                source.inputStream.buffered().use { input ->
                    temporary.outputStream().buffered().use { output ->
                        val buffer = ByteArray(32 * 1024)
                        while (true) {
                            val n = input.read(buffer)
                            if (n == -1) break
                            downloaded += n
                            require(downloaded <= release.sizeBytes &&
                                downloaded <= GitHubReleaseRepository.MAX_APK_BYTES) {
                                "File APK più grande del previsto"
                            }
                            output.write(buffer, 0, n)
                            hash.update(buffer, 0, n)
                            val percent = ((downloaded * 100L) / release.sizeBytes).toInt()
                            if (percent >= lastPercent + 5) {
                                lastPercent = percent
                                withContext(Dispatchers.Main) { onProgress(percent.coerceIn(0, 100)) }
                            }
                        }
                    }
                }
                require(downloaded == release.sizeBytes) { "Download APK incompleto" }
                val computed = hash.digest().joinToString("") { "%02x".format(it.toInt() and 0xff) }
                if (release.sha256 != null) {
                    require(release.sha256.equals(computed, ignoreCase = true)) {
                        "Il checksum dell'APK non coincide con GitHub"
                    }
                }
                verifyPackage(temporary, release)
                check(temporary.renameTo(target)) { "Impossibile completare il file APK" }
                withContext(Dispatchers.Main) { onProgress(100) }
                target
            } finally {
                source.disconnect()
            }
        } catch (error: Exception) {
            temporary.delete()
            target.delete()
            throw error
        }
    }

    /** Rifiuta redirect fuori dai domini GitHub consentiti e HTTP non cifrati. */
    private fun openTrustedDownload(url: String): HttpURLConnection {
        var next = URI(url)
        for (redirect in 0..5) {
            require(next.scheme == "https" &&
                next.host?.lowercase(Locale.ROOT) in redirectHosts &&
                next.userInfo == null && next.port == -1) {
                "Il download è stato reindirizzato a un indirizzo non attendibile"
            }
            val connection = next.toURL().openConnection() as HttpURLConnection
            connection.connectTimeout = 12_000
            connection.readTimeout = 25_000
            connection.instanceFollowRedirects = false
            connection.setRequestProperty("User-Agent", "ButtiGoGym-Android-Updater")
            val response = connection.responseCode
            if (response == HttpURLConnection.HTTP_OK) return connection
            if (response !in 300..399) {
                connection.disconnect()
                error("GitHub ha risposto HTTP $response")
            }
            val location = connection.getHeaderField("Location")
            connection.disconnect()
            require(!location.isNullOrBlank()) { "Reindirizzamento GitHub incompleto" }
            next = next.resolve(location)
        }
        error("Troppi reindirizzamenti durante il download APK")
    }

    fun verifyPackage(file: File, release: AppRelease) {
        require(file.isFile && file.length() == release.sizeBytes) {
            "APK incompleto o inesistente"
        }
        @Suppress("DEPRECATION")
        val flags = if (Build.VERSION.SDK_INT >= 28)
            PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES
        @Suppress("DEPRECATION")
        val incoming = pm.getPackageArchiveInfo(file.absolutePath, flags)
            ?: error("Il file scaricato non è un APK Android valido")
        @Suppress("DEPRECATION")
        val installed = pm.getPackageInfo(app.packageName, flags)

        require(incoming.packageName == app.packageName) {
            "L'APK non appartiene a ButtiGoGym"
        }
        val currentVersionCode = if (Build.VERSION.SDK_INT >= 28)
            installed.longVersionCode else @Suppress("DEPRECATION") installed.versionCode.toLong()
        val nextVersionCode = if (Build.VERSION.SDK_INT >= 28)
            incoming.longVersionCode else @Suppress("DEPRECATION") incoming.versionCode.toLong()
        require(nextVersionCode > currentVersionCode) {
            "Il numero di versione Android non è superiore a quello installato"
        }
        require(AppVersion.parse(incoming.versionName.orEmpty()) == release.version) {
            "La versione contenuta nell'APK non corrisponde alla Release GitHub"
        }
        val installedCerts = signatures(installed)
        val incomingCerts = signatures(incoming)
        require(installedCerts.isNotEmpty() && incomingCerts.isNotEmpty() &&
            installedCerts.size == incomingCerts.size &&
            installedCerts.map { it.toList() }.toSet() ==
                incomingCerts.map { it.toList() }.toSet()) {
            "Firma APK diversa: aggiornamento annullato per evitare conflitti"
        }
    }

    @Suppress("DEPRECATION")
    private fun signatures(info: PackageInfo): List<ByteArray> =
        if (Build.VERSION.SDK_INT >= 28) {
            info.signingInfo?.apkContentsSigners?.map { it.toByteArray() }.orEmpty()
        } else {
            info.signatures?.map { it.toByteArray() }.orEmpty()
        }

    fun canRequestInstallation(): Boolean =
        Build.VERSION.SDK_INT < 26 || pm.canRequestPackageInstalls()

    fun settingsIntent(): Intent =
        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            Uri.parse("package:${app.packageName}")).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

    fun installationIntent(verifiedApk: File): Intent {
        require(verifiedApk.isFile &&
            verifiedApk.canonicalFile.parentFile == File(app.filesDir, "updates").canonicalFile) {
            "Percorso APK non valido"
        }
        val uri = FileProvider.getUriForFile(
            app, "${app.packageName}.fileprovider", verifiedApk
        )
        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, APK_MIME)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }
}
