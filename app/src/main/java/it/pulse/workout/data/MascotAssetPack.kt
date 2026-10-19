package it.pulse.workout.data

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.File
import java.util.zip.ZipInputStream

/**
 * Import offline del pack WebP creato dall'utente.
 * Non carica immagini in rete, non usa archivi temporanei esterni e non tocca i dati WO.
 * Il pack resta in filesDir anche quando l'APK viene aggiornato in-place.
 */
object MascotAssetPack {
    private val names = CoachPose.entries.map { it.asset }.toSet()
    private const val MAX_IMAGE = 2_500_000L
    private const val MAX_TOTAL = 15_000_000L

    fun hasPack(context: Context): Boolean =
        File(context.filesDir, "coach-poses/home.webp").isFile

    fun imageFor(context: Context, pose: CoachPose): File? {
        val dir = File(context.filesDir, "coach-poses")
        val preferred = File(dir, pose.asset)
        val file = if (preferred.isFile) preferred else File(dir, "home.webp")
        return file.takeIf { it.isFile }
    }

    fun importZip(context: Context, source: Uri): Int {
        val stream = context.contentResolver.openInputStream(source)
            ?: error("Non riesco ad aprire lo ZIP selezionato")
        val staging = File(context.cacheDir, "coach-import-${System.nanoTime()}")
        val destination = File(context.filesDir, "coach-poses")
        val backup = File(context.filesDir, "coach-poses-old")
        require(staging.mkdirs()) { "Impossibile preparare l'importazione" }
        var entries = 0
        var count = 0
        var total = 0L
        try {
            ZipInputStream(stream.buffered()).use { archive ->
                while (true) {
                    val item = archive.nextEntry ?: break
                    entries++
                    require(entries <= 20) { "Lo ZIP contiene troppi elementi" }
                    val name = item.name
                    require(!item.isDirectory && (name == "manifest.json" || name in names)) {
                        "Lo ZIP contiene un file non previsto: $name"
                    }
                    if (name in names) {
                        val output = File(staging, name)
                        var bytes = 0L
                        output.outputStream().buffered().use { target ->
                            val buffer = ByteArray(16 * 1024)
                            while (true) {
                                val read = archive.read(buffer)
                                if (read < 0) break
                                bytes += read
                                total += read
                                require(bytes <= MAX_IMAGE && total <= MAX_TOTAL) {
                                    "Le immagini superano il limite di dimensione"
                                }
                                target.write(buffer, 0, read)
                            }
                        }
                        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                        BitmapFactory.decodeFile(output.absolutePath, opts)
                        require(opts.outWidth in 200..4096 && opts.outHeight in 200..4096 &&
                            opts.outMimeType == "image/webp") {
                            "Immagine non valida: $name"
                        }
                        count++
                    }
                    archive.closeEntry()
                }
            }
            require(count >= 7 && File(staging, "home.webp").isFile) {
                "Pacchetto incompleto: mancano le pose della mascotte"
            }
            if (backup.exists()) backup.deleteRecursively()
            if (destination.exists()) check(destination.renameTo(backup)) {
                "Impossibile sostituire il vecchio pacchetto"
            }
            if (!staging.renameTo(destination)) {
                if (backup.exists()) backup.renameTo(destination)
                error("Impossibile installare le immagini")
            }
            backup.deleteRecursively()
            return count
        } finally {
            if (staging.exists()) staging.deleteRecursively()
        }
    }
}
