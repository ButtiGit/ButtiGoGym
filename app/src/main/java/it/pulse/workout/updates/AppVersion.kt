package it.pulse.workout.updates

/**
 * Confronto numerico delle versioni stabili, senza ordinamenti lessicografici
 * (1.0.10 è più recente di 1.0.9). Tag prerelease esclusi.
 */
data class AppVersion(val major: Int, val minor: Int, val patch: Int) : Comparable<AppVersion> {
    override fun compareTo(other: AppVersion): Int =
        compareValuesBy(this, other, AppVersion::major, AppVersion::minor, AppVersion::patch)

    override fun toString(): String = "$major.$minor.$patch"

    companion object {
        private val semanticVersion = Regex("^v?(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)$")
        fun parse(input: String): AppVersion? {
            val m = semanticVersion.matchEntire(input.trim()) ?: return null
            val numbers = m.groupValues.drop(1).map { it.toIntOrNull() ?: return null }
            return AppVersion(numbers[0], numbers[1], numbers[2])
        }
    }
}

/** Il sistema non propone mai downgrade, stessa versione o tag non stabili. */
object UpdateVersionPolicy {
    fun shouldOffer(installed: String, candidate: String): Boolean {
        val current = AppVersion.parse(installed) ?: return false
        val available = AppVersion.parse(candidate) ?: return false
        return available > current
    }
}
