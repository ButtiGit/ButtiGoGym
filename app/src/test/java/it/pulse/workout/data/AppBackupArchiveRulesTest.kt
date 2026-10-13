package it.pulse.workout.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Difese fondamentali contro percorsi ZIP non validi o manipolati. */
class AppBackupArchiveRulesTest {
    @Test fun acceptsOnlyPrivateSessionPhotosAndOwnedRecaps() {
        assertTrue(AppBackupStore.allowedMediaPath("photos/session_1760354249000_1760354250000.jpg"))
        assertTrue(AppBackupStore.allowedMediaPath("recaps/recap_1760354249000_1760354250000.png"))
        assertTrue(AppBackupStore.allowedMediaPath("body/body_front_20261013_1760354250000.jpg"))
        assertTrue(AppBackupStore.allowedMediaPath("body/body_side_20261013_1760354250001.jpg"))
    }

    @Test fun rejectsTraversalAndUnexpectedFiles() {
        val invalid = listOf(
            "../shared_prefs/pulse_v1.xml",
            "photos/../recaps/recap_1_2.png",
            "photos/session_1_2.jpg/../../secret",
            "photos/session_1_2.png",
            "recaps/recap_1_2.jpg",
            "photos/.hidden.jpg",
            "files/session_1_2.jpg",
            "photos/session_1_2.jpg\\\\evil",
            "recaps//recap_1_2.png",
            "body/../body_front_20261013_1760354250000.jpg",
            "body/body_front_20261013_1760354250000.png",
            "body/body_side_20261013_1760354250000.jpg/traversal",
            "body/body_random_20261013_1760354250000.jpg"
        )
        invalid.forEach { assertFalse("Rejected path: $it", AppBackupStore.allowedMediaPath(it)) }
    }
}
