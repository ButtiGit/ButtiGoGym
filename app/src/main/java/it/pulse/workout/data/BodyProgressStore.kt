package it.pulse.workout.data

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import it.pulse.workout.ui.WorkoutPhotoStorage
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.time.LocalDate
import java.util.UUID

/**
 * Diario fisico in locale. Usa una chiave autonoma nel namespace wellness già
 * incluso nel backup ZIP V2: non altera il registro di passi e riposi.
 */
class BodyProgressStore(context: Context) {
    private val app = context.applicationContext
    private val preferences = app.getSharedPreferences("buttigogym_wellness_v1", Context.MODE_PRIVATE)
    private val key = "body_progress_v1"
    private val photosDir = File(app.filesDir, "body_progress").apply { mkdirs() }

    fun entries(): List<BodyProgressEntry> = runCatching {
        val root = JSONObject(preferences.getString(key, "{}").orEmpty())
        val dates = root.keys()
        buildList {
            while (dates.hasNext()) {
                val dayKey = dates.next()
                val date = LocalDate.parse(dayKey)
                val value = root.getJSONObject(dayKey)
                val cm = value.optJSONObject("measurements") ?: JSONObject()
                val names = value.optJSONObject("photos") ?: JSONObject()
                val entry = BodyProgressEntry(
                    date = date,
                    weightKg = if (value.has("weightKg") && !value.isNull("weightKg"))
                        value.getDouble("weightKg") else null,
                    measurementsCm = BodyMeasure.entries.mapNotNull { field ->
                        if (cm.has(field.key) && !cm.isNull(field.key))
                            field to cm.getDouble(field.key) else null
                    }.toMap(),
                    photos = ProgressPhotoAngle.entries.mapNotNull { angle ->
                        names.optString(angle.key, "").takeIf { it.isNotBlank() }
                            ?.let { angle to it }
                    }.toMap()
                )
                add(BodyProgressRules.validate(entry))
            }
        }.sortedBy { it.date }
    }.getOrElse {
        android.util.Log.w("ButtiGoGym/Body", "Unable to read body diary", it)
        emptyList()
    }

    private fun persist(entries: List<BodyProgressEntry>) {
        val root = JSONObject()
        entries.forEach { entry ->
            BodyProgressRules.validate(entry)
            root.put(entry.date.toString(), JSONObject().apply {
                if (entry.weightKg != null) put("weightKg", entry.weightKg)
                put("measurements", JSONObject().apply {
                    entry.measurementsCm.forEach { (field, cm) -> put(field.key, cm) }
                })
                put("photos", JSONObject().apply {
                    entry.photos.forEach { (angle, filename) -> put(angle.key, filename) }
                })
            })
        }
        check(preferences.edit().putString(key, root.toString()).commit()) {
            "Impossibile salvare il diario fisico"
        }
    }

    /** Preserva foto esistenti quando l'utente corregge soltanto le misure. */
    fun saveMeasurements(
        date: LocalDate, weightKg: Double?, values: Map<BodyMeasure, Double>
    ): List<BodyProgressEntry> {
        val current = entries()
        val old = current.firstOrNull { it.date == date }
        val changed = BodyProgressEntry(date, weightKg, values, old?.photos.orEmpty())
        val next = BodyProgressRules.insertOrReplace(current, changed)
        persist(next)
        return next
    }

    /** Un'unica foto per vista e data. Sostituisce l'originale solo dopo salvataggio riuscito. */
    fun savePhoto(date: LocalDate, angle: ProgressPhotoAngle, file: File): List<BodyProgressEntry> {
        require(file.isFile && file.parentFile?.canonicalFile == photosDir.canonicalFile) {
            "File foto non valido"
        }
        require(BodyProgressRules.validPhotoName(file.name, angle, date)) {
            "Foto non valida per la data selezionata"
        }
        val current = entries()
        val previous = current.firstOrNull { it.date == date }
        val updated = (previous ?: BodyProgressEntry(date)).copy(
            photos = (previous?.photos.orEmpty()) + (angle to file.name)
        )
        val next = BodyProgressRules.insertOrReplace(current, updated)
        persist(next)
        previous?.photos?.get(angle)?.takeIf { it != file.name }?.let {
            safePhotoFile(it)?.delete()
        }
        return next
    }

    fun deletePhoto(date: LocalDate, angle: ProgressPhotoAngle): List<BodyProgressEntry> {
        val current = entries()
        val previous = current.firstOrNull { it.date == date } ?: return current
        val removed = previous.photos[angle] ?: return current
        val changed = previous.copy(photos = previous.photos - angle)
        val next = if (changed.hasContent()) BodyProgressRules.insertOrReplace(current, changed)
            else current.filterNot { it.date == date }
        persist(next)
        safePhotoFile(removed)?.delete()
        return next
    }

    fun deleteEntry(date: LocalDate): List<BodyProgressEntry> {
        val current = entries()
        val previous = current.firstOrNull { it.date == date } ?: return current
        val next = current.filterNot { it.date == date }
        persist(next)
        previous.photos.values.forEach { safePhotoFile(it)?.delete() }
        return next
    }

    private fun safePhotoFile(name: String): File? {
        val allowed = Regex("^body_(front|side|back)_[0-9]{8}_[0-9]{10,17}\\.jpg$")
        return name.takeIf { allowed.matches(it) }?.let { File(photosDir, it) }
    }

    fun photoFile(entry: BodyProgressEntry, angle: ProgressPhotoAngle): File? =
        entry.photos[angle]?.takeIf {
            BodyProgressRules.validPhotoName(it, angle, entry.date)
        }?.let { safePhotoFile(it) }?.takeIf { it.isFile }

    private fun photoFilename(date: LocalDate, angle: ProgressPhotoAngle): String =
        "body_${angle.key}_${date.toString().replace("-", "")}_${System.currentTimeMillis()}.jpg"

    /** Uri FileProvider per la fotocamera esterna full-resolution. */
    fun cameraDestination(date: LocalDate, angle: ProgressPhotoAngle): Pair<File, Uri> {
        val file = File(photosDir, photoFilename(date, angle))
        return file to FileProvider.getUriForFile(
            app, "${app.packageName}.fileprovider", file
        )
    }

    /** Ridimensiona/normalizza immagini private come le foto delle sessioni. */
    fun finalizeCameraPhoto(file: File): File {
        require(file.parentFile?.canonicalFile == photosDir.canonicalFile)
        return WorkoutPhotoStorage.normalizePhoto(file)
    }

    fun importFromGallery(source: Uri, date: LocalDate, angle: ProgressPhotoAngle): File {
        val target = File(photosDir, photoFilename(date, angle))
        try {
            requireNotNull(app.contentResolver.openInputStream(source)) { "Immagine non accessibile" }
                .use { input ->
                    FileOutputStream(target).use { output ->
                        val buffer = ByteArray(8192)
                        var total = 0L
                        while (true) {
                            val size = input.read(buffer)
                            if (size == -1) break
                            total += size
                            require(total <= 24L * 1024 * 1024) {
                                "Foto troppo grande (massimo 24 MB)"
                            }
                            output.write(buffer, 0, size)
                        }
                    }
                }
            return WorkoutPhotoStorage.normalizePhoto(target)
        } catch (error: Exception) {
            target.delete()
            throw error
        }
    }
}
