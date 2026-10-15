package it.pulse.workout.data

import java.time.LocalDate

/** Misure in centimetri, sempre facoltative; nessun dato demo. */
enum class BodyMeasure(val key: String, val label: String) {
    CHEST("chest", "Torace"),
    SHOULDERS("shoulders", "Spalle"),
    WAIST("waist", "Vita"),
    HIPS("hips", "Fianchi"),
    ARM("arm", "Braccio"),
    FOREARM("forearm", "Avambraccio"),
    THIGH("thigh", "Coscia"),
    CALF("calf", "Polpaccio")
}

enum class ProgressPhotoAngle(val key: String, val label: String) {
    FRONT("front", "Fronte"), SIDE("side", "Profilo"), BACK("back", "Retro")
}

data class BodyProgressEntry(
    val date: LocalDate,
    val weightKg: Double? = null,
    val measurementsCm: Map<BodyMeasure, Double> = emptyMap(),
    val photos: Map<ProgressPhotoAngle, String> = emptyMap()
) {
    fun hasContent(): Boolean =
        weightKg != null || measurementsCm.isNotEmpty() || photos.isNotEmpty()
}

data class BodyValueComparison(
    val before: Double?, val after: Double?
) {
    /** Nessuna interpolazione se almeno uno dei due valori non è registrato. */
    val change: Double? get() = if (before != null && after != null) after - before else null
}

object BodyProgressRules {
    private val photoNamePattern =
        Regex("^body_(front|side|back)_[0-9]{8}_[0-9]{10,17}\\.jpg$")

    fun validPhotoName(name: String, angle: ProgressPhotoAngle, date: LocalDate): Boolean =
        photoNamePattern.matches(name) &&
            name.startsWith("body_${angle.key}_${date.toString().replace("-", "")}_")

    fun validate(entry: BodyProgressEntry): BodyProgressEntry {
        require(!entry.date.isAfter(LocalDate.now())) { "La data non può essere futura" }
        require(entry.date.year >= 2000) { "Data non valida" }
        require(entry.hasContent()) { "Inserisci almeno una misura o una foto" }
        require(entry.weightKg == null ||
            (entry.weightKg.isFinite() && entry.weightKg in 20.0..400.0)) {
            "Peso valido: 20–400 kg"
        }
        entry.measurementsCm.forEach { (_, cm) ->
            require(cm.isFinite() && cm in 10.0..300.0) {
                "Circonferenza valida: 10–300 cm"
            }
        }
        entry.photos.forEach { (angle, name) ->
            require(validPhotoName(name, angle, entry.date)) { "Nome immagine non valido" }
        }
        return entry
    }

    fun comparison(before: BodyProgressEntry, after: BodyProgressEntry): Map<BodyMeasure, BodyValueComparison> =
        BodyMeasure.entries.associateWith { field ->
            BodyValueComparison(before.measurementsCm[field], after.measurementsCm[field])
        }

    fun compareWeight(before: BodyProgressEntry, after: BodyProgressEntry) =
        BodyValueComparison(before.weightKg, after.weightKg)

    fun insertOrReplace(all: List<BodyProgressEntry>, entry: BodyProgressEntry): List<BodyProgressEntry> {
        validate(entry)
        return (all.filterNot { it.date == entry.date } + entry).sortedBy { it.date }
    }
}
