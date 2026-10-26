package it.pulse.workout.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import it.pulse.workout.R
import it.pulse.workout.data.SessionRecord
import it.pulse.workout.data.WorkoutDay
import it.pulse.workout.data.WorkoutRecapCalculator
import java.io.File
import java.io.FileOutputStream
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Foto locali: nessun upload online e nessun permesso globale alla galleria. */
object WorkoutPhotoStorage {
    private val allowedName = Regex("session_[0-9]+_[0-9]+\\.jpg")
    private fun photoDir(context: Context) = File(context.filesDir, "session_photos").apply { mkdirs() }

    fun fileFor(context: Context, record: SessionRecord): File? {
        val name = record.photoFileName.takeIf { allowedName.matches(it) } ?: return null
        return File(photoDir(context), name).takeIf { it.isFile }
    }

    fun createCameraPhoto(context: Context, recordId: Long): Pair<File, Uri> {
        val target = File(photoDir(context), "session_${recordId}_${System.currentTimeMillis()}.jpg")
        val uri = FileProvider.getUriForFile(
            context, "${context.packageName}.fileprovider", target
        )
        return target to uri
    }

    /** Normalizza dimensioni e orientamento e conserva la foto in JPEG. */
    fun normalizePhoto(file: File): File {
        require(file.exists() && file.length() in 1..25_000_000L) {
            "Immagine assente o troppo grande"
        }
        val bitmap = decodeUpright(file, 2048)
        val normalized = File(file.parentFile, file.name + ".partial")
        FileOutputStream(normalized).use { stream ->
            check(bitmap.compress(Bitmap.CompressFormat.JPEG, 88, stream)) {
                "Errore nel salvataggio della foto"
            }
        }
        bitmap.recycle()
        check(normalized.renameTo(file) || run {
            normalized.copyTo(file, overwrite = true)
            normalized.delete()
            true
        }) { "Impossibile sostituire la foto" }
        return file
    }

    fun importFromGallery(context: Context, source: Uri, recordId: Long): File {
        val target = File(photoDir(context), "session_${recordId}_${System.currentTimeMillis()}.jpg")
        try {
            context.contentResolver.openInputStream(source).use { input ->
                requireNotNull(input) { "Impossibile aprire l'immagine" }
                FileOutputStream(target).use { output ->
                    val buffer = ByteArray(8192)
                    var length = 0L
                    while (true) {
                        val bytes = input.read(buffer)
                        if (bytes < 0) break
                        length += bytes
                        require(length <= 25_000_000L) { "Foto troppo grande (max 25 MB)" }
                        output.write(buffer, 0, bytes)
                    }
                }
            }
            return normalizePhoto(target)
        } catch (error: Exception) {
            target.delete()
            throw error
        }
    }

    fun decodeUpright(file: File, longestEdge: Int): Bitmap {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        require(bounds.outWidth > 0 && bounds.outHeight > 0) { "Formato immagine non supportato" }
        var sample = 1
        while (bounds.outWidth / sample > longestEdge * 2 ||
               bounds.outHeight / sample > longestEdge * 2) sample *= 2
        val bitmap = BitmapFactory.decodeFile(file.absolutePath,
            BitmapFactory.Options().apply { inSampleSize = sample })
            ?: error("Immagine non leggibile")

        val rotation = try {
            when (ExifInterface(file.absolutePath).getAttributeInt(
                ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL
            )) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
        } catch (_: Exception) { 0f }
        val transformed = if (rotation != 0f) {
            Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height,
                Matrix().apply { postRotate(rotation) }, true).also { bitmap.recycle() }
        } else bitmap
        if (maxOf(transformed.width, transformed.height) <= longestEdge) return transformed
        val ratio = longestEdge.toFloat() / maxOf(transformed.width, transformed.height)
        return Bitmap.createScaledBitmap(transformed,
            (transformed.width * ratio).toInt().coerceAtLeast(1),
            (transformed.height * ratio).toInt().coerceAtLeast(1), true)
            .also { if (it != transformed) transformed.recycle() }
    }
}

/** Story con foto e risultati: niente logo né nome dell'app. */
object WorkoutRecapImage {
    /** URI privato con permessi temporanei per il foglio di condivisione Android. */
    fun share(context: Context, record: SessionRecord, day: WorkoutDay?): android.content.Intent {
        val target = File(context.filesDir, "session_photos").apply { mkdirs() }
            .resolve("recap_share_${record.id}.png")
        if (!target.exists()) check(target.createNewFile()) { "Impossibile preparare l'immagine" }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", target)
        try {
            export(context, record, day, uri, archive = false)
        } catch (error: Exception) {
            target.delete()
            throw error
        }
        return android.content.Intent(android.content.Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(android.content.Intent.EXTRA_STREAM, uri)
            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    fun export(context: Context, record: SessionRecord, day: WorkoutDay?,
               destination: Uri, archive: Boolean = true) {
        val photo = requireNotNull(WorkoutPhotoStorage.fileFor(context, record)) {
            "Allega una foto prima di creare il recap"
        }
        val original = WorkoutPhotoStorage.decodeUpright(photo, 2100)
        val output = Bitmap.createBitmap(1080, 1350, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val imagePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        val factor = maxOf(1080f / original.width, 1350f / original.height)
        val width = original.width * factor
        val height = original.height * factor
        canvas.drawBitmap(original, null,
            RectF((1080f - width) / 2f, (1350f - height) / 2f,
                (1080f + width) / 2f, (1350f + height) / 2f), imagePaint)
        original.recycle()

        val dim = Paint().apply {
            shader = LinearGradient(0f, 480f, 0f, 1350f,
                intArrayOf(Color.TRANSPARENT, Color.argb(214, 4, 4, 4),
                    Color.argb(248, 8, 8, 8)), null, Shader.TileMode.CLAMP)
        }
        canvas.drawRect(0f, 0f, 1080f, 1350f, dim)

        val white = Color.WHITE
        val muted = Color.rgb(212, 212, 212)

        fun type(size: Float, color: Int, bold: Boolean = false): Paint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize = size
                this.color = color
                typeface = Typeface.create("sans-serif", if (bold) Typeface.BOLD else Typeface.NORMAL)
            }
        canvas.drawText("IL MIO ALLENAMENTO", 55f, 955f, type(23f, muted, true))
        val name = day?.name ?: "Allenamento"
        val namePaint = type(59f, white, true)
        val maxNameWidth = 980f
        var shownName = name
        while (namePaint.measureText(shownName) > maxNameWidth && shownName.length > 3) {
            shownName = shownName.dropLast(2) + "…"
        }
        canvas.drawText(shownName, 54f, 1026f, namePaint)
        canvas.drawText(SimpleDateFormat("dd MMMM yyyy", Locale.ITALIAN).format(Date(record.completedAt)),
            56f, 1070f, type(27f, muted))

        val stats = WorkoutRecapCalculator.calculate(record, day)
        val formatter = NumberFormat.getIntegerInstance(Locale.ITALIAN)
        val volume = if (stats.volumeKg % 1.0 == 0.0)
            formatter.format(stats.volumeKg.toLong())
        else String.format(Locale.ITALIAN, "%,.1f", stats.volumeKg)
        val metricLabels = arrayOf("VOLUME", "DURATA", "CALORIE ≈")
        val metricValues = arrayOf(
            "$volume kg",
            formatDuration(stats.durationSeconds.toLong()),
            "${stats.estimatedCalories} kcal"
        )
        metricLabels.forEachIndexed { index, label ->
            val x = 54f + index * 344f
            canvas.drawText(label, x, 1140f, type(20f, muted, true))
            canvas.drawText(metricValues[index], x, 1201f, type(40f, white, true))
        }
        canvas.drawText("Volume = kg × ripetizioni delle serie registrate", 54f, 1275f,
            type(22f, muted))
        canvas.drawText("Calorie: stima MET 5,0 · peso 58 kg", 54f, 1307f, type(22f, muted))

        try {
            // Ogni nuovo recap ha una copia locale per il backup ZIP,
            // indipendente dalla copia esportata nella cartella scelta dall'utente.
            val saved = java.io.File(context.filesDir, "session_recaps").apply { mkdirs() }
            val internalCopy = java.io.File(
                saved, "recap_${record.id}_${System.currentTimeMillis()}.png"
            )
            try {
                if (archive) internalCopy.outputStream().buffered().use {
                    check(output.compress(Bitmap.CompressFormat.PNG, 100, it)) {
                        "Impossibile archiviare il recap"
                    }
                }
                requireNotNull(context.contentResolver.openOutputStream(destination)) {
                    "Destinazione non accessibile"
                }.use {
                    check(output.compress(Bitmap.CompressFormat.PNG, 100, it)) {
                        "Impossibile esportare il PNG"
                    }
                }
            } catch (error: Exception) {
                // Non lasciare una falsa copia di backup dopo un errore.
                internalCopy.delete()
                throw error
            }
        } finally {
            output.recycle()
        }
    }
}
