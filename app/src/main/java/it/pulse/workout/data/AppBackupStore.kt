package it.pulse.workout.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import java.time.Instant
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Backup offline V2: manifest tipizzato + fotografie e recap interni.
 * Non esporta credenziali o autorizzazioni di Android/Health Connect.
 * Il file .zip contiene dati personali in chiaro: l'utente deve conservarlo privatamente.
 */
class AppBackupStore(context: Context) {
    private val app = context.applicationContext

    data class Preview(
        val schemaVersion: Int,
        val workouts: Int,
        val photos: Int,
        val recaps: Int,
        val createdAt: String
    )

    private data class Archive(
        val manifest: JSONObject,
        val stage: File,
        val preview: Preview
    )

    companion object {
        const val MIME_TYPE = "application/zip"
        const val FILE_EXTENSION = ".zip"
        private const val FORMAT = "ButtiGoGymBackup"
        private const val MANIFEST = "backup.json"
        private const val MAX_MANIFEST_BYTES = 8 * 1024 * 1024
        private const val MAX_FILE_BYTES = 24L * 1024 * 1024
        private const val MAX_TOTAL_BYTES = 600L * 1024 * 1024
        private const val MAX_MEDIA_FILES = 800
        private val photoName = Regex("session_[0-9]+_[0-9]+\\.jpg")
        private val recapName = Regex("recap_[0-9]+_[0-9]+\\.png")
        private val namespaces = listOf(
            "pulse_v1", "buttigogym_wellness_v1", "pulse_plan",
            "buttigogym_reminders_v1", "buttigogym_reminder_permission"
        )
        private val legacyNamespaces = namespaces.take(3)

        /** Percorsi attesi; la struttura ZIP non può scrivere altrove. */
        fun allowedMediaPath(path: String): Boolean {
            val name = path.substringAfterLast('/')
            return (path == "photos/$name" && photoName.matches(name)) ||
                (path == "recaps/$name" && recapName.matches(name))
        }
    }

    fun exportTo(output: OutputStream): Preview {
        val prefs = capturePreferences()
        val refs = referencedPhotos(prefs)
        val photos = listFiles("session_photos", photoName)
        val actual = photos.map { it.name }.toSet()
        require(refs.all { it in actual }) {
            "Foto mancanti nello storico: il backup non sarebbe completo"
        }
        val recaps = listFiles("session_recaps", recapName)
        val media = photos.map { "photos/${it.name}" to it } +
            recaps.map { "recaps/${it.name}" to it }
        require(media.size <= MAX_MEDIA_FILES) { "Troppe immagini nel backup" }
        var bytes = 0L
        val descriptors = JSONArray()
        media.forEach { (path, file) ->
            val size = file.length()
            require(size in 1..MAX_FILE_BYTES) { "File troppo grande o vuoto: ${file.name}" }
            bytes += size
            require(bytes <= MAX_TOTAL_BYTES) { "Archivio troppo grande" }
            descriptors.put(JSONObject().apply {
                put("path", path)
                put("bytes", size)
                put("sha256", sha256(file))
            })
        }
        val root = JSONObject().apply {
            put("format", FORMAT)
            put("schemaVersion", 2)
            put("package", app.packageName)
            put("createdAt", Instant.now().toString())
            put("data", prefs)
            put("media", descriptors)
        }
        val encoded = root.toString().toByteArray(Charsets.UTF_8)
        require(encoded.size <= MAX_MANIFEST_BYTES) { "Troppe informazioni nel backup" }
        ZipOutputStream(BufferedOutputStream(output)).use { zip ->
            zip.putNextEntry(ZipEntry(MANIFEST))
            zip.write(encoded)
            zip.closeEntry()
            media.forEach { (path, file) ->
                zip.putNextEntry(ZipEntry(path))
                file.inputStream().buffered().use { it.copyTo(zip) }
                zip.closeEntry()
            }
            zip.finish()
        }
        return preview(root)
    }

    /** Legge e verifica integralmente il backup, senza toccare i dati correnti. */
    fun inspect(input: InputStream): Preview {
        val archive = extractAndValidate(input)
        return try { archive.preview } finally { archive.stage.deleteRecursively() }
    }

    /**
     * Ripristino in due fasi: verifica file e SHA prima delle modifiche,
     * poi sposta le directory e aggiorna le preferenze. In caso di errore
     * cerca di ripristinare i valori precedenti e le immagini originali.
     */
    fun restoreFrom(input: InputStream): Preview {
        val archive = extractAndValidate(input)
        try {
            val incoming = archive.manifest.getJSONObject("data")
            val snapshot = namespaces.associateWith { name ->
                app.getSharedPreferences(name, Context.MODE_PRIVATE).all.toMap()
            }
            val previous = File(archive.stage, "previous").apply { mkdirs() }
            val moved = mutableListOf<Pair<File, File>>()
            val installed = mutableListOf<File>()
            try {
                listOf("session_photos" to "photos", "session_recaps" to "recaps")
                    .forEach { (destinationName, archiveName) ->
                        val target = File(app.filesDir, destinationName)
                        val old = File(previous, destinationName)
                        if (target.exists()) {
                            check(target.renameTo(old)) {
                                "Impossibile mettere in sicurezza le foto originali"
                            }
                            moved.add(target to old)
                        }
                        val source = File(archive.stage, archiveName)
                        check(source.renameTo(target)) { "Impossibile ripristinare le foto" }
                        installed.add(target)
                    }
                // V1 JSON non aveva le impostazioni dei promemoria:
                // non cancellarle se l'archivio non le contiene.
                val groups = if (archive.preview.schemaVersion == 1)
                    legacyNamespaces else namespaces
                groups.forEach { namespace ->
                    applyPreferences(namespace, incoming.getJSONObject(namespace))
                }
            } catch (error: Exception) {
                installed.forEach { it.deleteRecursively() }
                moved.asReversed().forEach { (target, old) ->
                    check(old.renameTo(target)) { "Ripristino precedente non riuscito: ${target.name}" }
                }
                snapshot.forEach { (namespace, values) ->
                    restoreSnapshot(namespace, values)
                }
                throw error
            }
            return archive.preview
        } finally {
            archive.stage.deleteRecursively()
        }
    }

    private fun extractAndValidate(input: InputStream): Archive {
        val stage = File(app.filesDir, ".backup-stage-${UUID.randomUUID()}").apply { mkdirs() }
        try {
            File(stage, "photos").mkdirs()
            File(stage, "recaps").mkdirs()
            val buffered = BufferedInputStream(input)
            buffered.mark(4)
            val magic1 = buffered.read()
            val magic2 = buffered.read()
            buffered.reset()
            if (magic1 != 0x50 || magic2 != 0x4b) {
                // Compatibilità con il precedente backup JSON.
                val bytes = buffered.readBytesLimited(MAX_MANIFEST_BYTES)
                val legacy = JSONObject(String(bytes, Charsets.UTF_8))
                validateLegacy(legacy)
                return Archive(legacy, stage, preview(legacy))
            }

            var manifest: JSONObject? = null
            val actual = linkedMapOf<String, Pair<Long, String>>()
            var total = 0L
            ZipInputStream(buffered).use { zip ->
                var count = 0
                while (true) {
                    val entry = zip.nextEntry ?: break
                    count++
                    require(count <= MAX_MEDIA_FILES + 1) { "Archivio con troppi file" }
                    val path = entry.name
                    require(!entry.isDirectory && (path == MANIFEST || allowedMediaPath(path))) {
                        "File inatteso nell'archivio"
                    }
                    require(path !in actual && (manifest == null || path != MANIFEST)) {
                        "File duplicato nell'archivio"
                    }
                    if (path == MANIFEST) {
                        require(count == 1) { "Intestazione ZIP mancante" }
                        val bytes = zip.readBytesLimited(MAX_MANIFEST_BYTES)
                        manifest = JSONObject(String(bytes, Charsets.UTF_8))
                    } else {
                        require(manifest != null) { "Intestazione ZIP mancante" }
                        val file = File(stage, if (path.startsWith("photos/"))
                            "photos/${path.substringAfter('/')}" else
                            "recaps/${path.substringAfter('/')}")
                        val digest = MessageDigest.getInstance("SHA-256")
                        var length = 0L
                        file.outputStream().buffered().use { out ->
                            val buffer = ByteArray(8192)
                            while (true) {
                                val read = zip.read(buffer)
                                if (read == -1) break
                                length += read
                                total += read
                                require(length <= MAX_FILE_BYTES && total <= MAX_TOTAL_BYTES) {
                                    "Foto o backup troppo grande"
                                }
                                digest.update(buffer, 0, read)
                                out.write(buffer, 0, read)
                            }
                        }
                        require(length > 0) { "Foto vuota nell'archivio" }
                        actual[path] = length to digest.digest().hex()
                    }
                    zip.closeEntry()
                }
            }
            val root = requireNotNull(manifest) { "Manifest ZIP assente" }
            validateRoot(root, actual)
            val refs = referencedPhotos(root.getJSONObject("data"))
            require(refs.all { "photos/$it" in actual }) {
                "Il backup non contiene tutte le foto collegate allo storico"
            }
            return Archive(root, stage, preview(root))
        } catch (error: Exception) {
            stage.deleteRecursively()
            throw error
        }
    }

    private fun validateRoot(root: JSONObject, actual: Map<String, Pair<Long, String>>) {
        require(root.optString("format") == FORMAT &&
            root.optInt("schemaVersion") == 2 &&
            root.optString("package") == app.packageName) {
            "Backup incompatibile con ButtiGoGym"
        }
        val data = root.getJSONObject("data")
        require(namespaces.all { data.has(it) }) { "Preferenze mancanti nel backup" }
        validatePreferences(data, namespaces)
        val descriptions = root.getJSONArray("media")
        require(descriptions.length() <= MAX_MEDIA_FILES &&
            descriptions.length() == actual.size) { "Elenco foto non corrispondente" }
        val expected = mutableSetOf<String>()
        for (index in 0 until descriptions.length()) {
            val entry = descriptions.getJSONObject(index)
            val path = entry.getString("path")
            require(allowedMediaPath(path) && expected.add(path)) { "Percorso foto non valido" }
            val received = actual[path] ?: error("Immagine mancante: $path")
            require(received.first == entry.getLong("bytes") &&
                received.second == entry.getString("sha256")) {
                "Immagine danneggiata: $path"
            }
        }
    }

    private fun validateLegacy(root: JSONObject) {
        require(root.optString("format") == FORMAT &&
            root.optInt("schemaVersion") == 1 &&
            root.optString("package") == app.packageName) {
            "Backup JSON non compatibile"
        }
        validatePreferences(root.getJSONObject("data"), legacyNamespaces, legacy = true)
    }

    private fun validatePreferences(
        data: JSONObject, groups: List<String>, legacy: Boolean = false
    ) {
        groups.forEach { namespace ->
            val values = data.getJSONObject(namespace)
            val keys = values.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                require(key.length in 1..120) { "Chiave di preferenza non valida" }
                val typed = if (legacy) JSONObject().apply {
                    put("type", "string"); put("value", values.getString(key))
                } else values.getJSONObject(key)
                val type = typed.getString("type")
                require(type in setOf("string", "boolean", "int", "long", "float", "stringSet")) {
                    "Tipo di preferenza non supportato"
                }
                val value = typed.get("value")
                require(value.toString().length <= MAX_MANIFEST_BYTES) {
                    "Preferenza troppo grande"
                }
                if (type == "stringSet") require(value is JSONArray)
                if (type == "string") {
                    val raw = typed.getString("value")
                    if (namespace == "pulse_v1" && key == "history") JSONArray(raw)
                    if (namespace == "pulse_v1" && key == "active_draft") JSONObject(raw)
                    if (namespace == "buttigogym_wellness_v1" && key == "wellness") JSONObject(raw)
                    if (namespace == "pulse_plan" && key == "valid_catalog") JSONObject(raw)
                }
            }
        }
    }

    private fun referencedPhotos(preferences: JSONObject): Set<String> {
        val pulse = preferences.optJSONObject("pulse_v1") ?: return emptySet()
        val item = pulse.opt("history") ?: return emptySet()
        val raw = if (item is JSONObject) item.optString("value", "[]")
                  else item.toString()
        val history = JSONArray(raw)
        return (0 until history.length()).mapNotNull { index ->
            history.getJSONObject(index).optString("photoFileName", "")
                .takeIf { it.isNotBlank() }
                ?.also { require(photoName.matches(it)) { "Nome foto nello storico non valido" } }
        }.toSet()
    }

    private fun preview(root: JSONObject): Preview {
        val data = root.getJSONObject("data")
        val pulse = data.getJSONObject("pulse_v1")
        val item = pulse.opt("history")
        val historyRaw = if (item is JSONObject) item.optString("value", "[]")
                         else item?.toString() ?: "[]"
        val media = root.optJSONArray("media") ?: JSONArray()
        val mediaPaths = (0 until media.length()).map { media.getJSONObject(it).getString("path") }
        return Preview(
            root.getInt("schemaVersion"),
            JSONArray(historyRaw).length(),
            mediaPaths.count { it.startsWith("photos/") },
            mediaPaths.count { it.startsWith("recaps/") },
            root.optString("createdAt", "")
        )
    }

    private fun capturePreferences(): JSONObject = JSONObject().apply {
        namespaces.forEach { namespace ->
            val values = JSONObject()
            app.getSharedPreferences(namespace, Context.MODE_PRIVATE).all.forEach { (key, value) ->
                val typed = JSONObject()
                when (value) {
                    is String -> { typed.put("type", "string"); typed.put("value", value) }
                    is Boolean -> { typed.put("type", "boolean"); typed.put("value", value) }
                    is Int -> { typed.put("type", "int"); typed.put("value", value) }
                    is Long -> { typed.put("type", "long"); typed.put("value", value) }
                    is Float -> { typed.put("type", "float"); typed.put("value", value.toDouble()) }
                    is Set<*> -> {
                        require(value.all { it is String }) { "Preferenza non supportata" }
                        typed.put("type", "stringSet")
                        typed.put("value", JSONArray(value.toList()))
                    }
                    else -> error("Preferenza sconosciuta: $key")
                }
                values.put(key, typed)
            }
            put(namespace, values)
        }
    }

    private fun listFiles(folder: String, namePattern: Regex): List<File> =
        File(app.filesDir, folder).listFiles().orEmpty()
            .filter { it.isFile && namePattern.matches(it.name) }
            .sortedBy { it.name }

    private fun applyPreferences(namespace: String, values: JSONObject) {
        val editor = app.getSharedPreferences(namespace, Context.MODE_PRIVATE).edit().clear()
        val keys = values.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val typed = values.getJSONObject(key)
            when (typed.getString("type")) {
                "string" -> editor.putString(key, typed.getString("value"))
                "boolean" -> editor.putBoolean(key, typed.getBoolean("value"))
                "int" -> editor.putInt(key, typed.getInt("value"))
                "long" -> editor.putLong(key, typed.getLong("value"))
                "float" -> editor.putFloat(key, typed.getDouble("value").toFloat())
                "stringSet" -> {
                    val valuesArray = typed.getJSONArray("value")
                    editor.putStringSet(key, (0 until valuesArray.length())
                        .map { valuesArray.getString(it) }.toSet())
                }
                else -> error("Tipo sconosciuto")
            }
        }
        check(editor.commit()) { "Salvataggio fallito: $namespace" }
    }

    private fun restoreSnapshot(namespace: String, values: Map<String, *>) {
        val editor = app.getSharedPreferences(namespace, Context.MODE_PRIVATE).edit().clear()
        values.forEach { (key, value) ->
            when (value) {
                is String -> editor.putString(key, value)
                is Boolean -> editor.putBoolean(key, value)
                is Int -> editor.putInt(key, value)
                is Long -> editor.putLong(key, value)
                is Float -> editor.putFloat(key, value)
                is Set<*> -> @Suppress("UNCHECKED_CAST")
                    editor.putStringSet(key, value as Set<String>)
            }
        }
        check(editor.commit()) { "Ripristino delle preferenze precedenti fallito" }
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(8192)
            while (true) {
                val read = input.read(buffer)
                if (read == -1) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().hex()
    }

    private fun ByteArray.hex() = joinToString("") { "%02x".format(it.toInt() and 0xff) }

    private fun InputStream.readBytesLimited(max: Int): ByteArray {
        val result = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val count = read(buffer)
            if (count < 0) break
            require(result.size() + count <= max) { "Intestazione di backup troppo grande" }
            result.write(buffer, 0, count)
        }
        return result.toByteArray()
    }
}
