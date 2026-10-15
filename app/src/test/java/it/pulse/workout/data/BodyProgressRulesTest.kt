package it.pulse.workout.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class BodyProgressRulesTest {
    private val sept = LocalDate.of(2026, 9, 1)
    private val oct = LocalDate.of(2026, 10, 1)
    private val before = BodyProgressEntry(
        sept, 57.2, mapOf(BodyMeasure.CHEST to 89.5, BodyMeasure.WAIST to 74.0)
    )
    private val after = BodyProgressEntry(
        oct, 58.1, mapOf(BodyMeasure.CHEST to 92.0, BodyMeasure.FOREARM to 25.0)
    )

    @Test fun comparisonsUseOnlyActuallyRecordedValues() {
        assertEquals(.9, BodyProgressRules.compareWeight(before, after).change!!, .001)
        val compared = BodyProgressRules.comparison(before, after)
        assertEquals(2.5, compared.getValue(BodyMeasure.CHEST).change!!, .001)
        assertNull(compared.getValue(BodyMeasure.WAIST).change)
        assertNull(compared.getValue(BodyMeasure.FOREARM).change)
        assertNull(compared.getValue(BodyMeasure.CALF).change)
    }

    @Test fun datedEntriesRemainSortedAndCorrectionsReplaceOnlyThatDate() {
        val one = BodyProgressRules.insertOrReplace(emptyList(), after)
        val both = BodyProgressRules.insertOrReplace(one, before)
        assertEquals(listOf(sept, oct), both.map { it.date })
        val corrected = before.copy(weightKg = 57.5)
        val replaced = BodyProgressRules.insertOrReplace(both, corrected)
        assertEquals(2, replaced.size)
        assertEquals(57.5, replaced.first().weightKg!!, .001)
        assertEquals(after, replaced.last())
        assertEquals(57.2, before.weightKg!!, .001)
    }

    @Test fun allowsPhotoOnlyEntryWithoutInventingMeasurements() {
        val filename = "body_front_20261001_1760000000000.jpg"
        val photoOnly = BodyProgressEntry(
            oct, photos = mapOf(ProgressPhotoAngle.FRONT to filename)
        )
        assertEquals(photoOnly, BodyProgressRules.validate(photoOnly))
        assertNull(photoOnly.weightKg)
        assertTrue(photoOnly.measurementsCm.isEmpty())
    }

    @Test fun rejectsFutureDateOrNonsenseMeasurements() {
        assertThrows(IllegalArgumentException::class.java) {
            BodyProgressRules.validate(BodyProgressEntry(LocalDate.now().plusDays(1), 58.0))
        }
        assertThrows(IllegalArgumentException::class.java) {
            BodyProgressRules.validate(BodyProgressEntry(oct, -1.0))
        }
        assertThrows(IllegalArgumentException::class.java) {
            BodyProgressRules.validate(BodyProgressEntry(oct, 58.0,
                mapOf(BodyMeasure.ARM to Double.NaN)))
        }
        assertThrows(IllegalArgumentException::class.java) {
            BodyProgressRules.validate(BodyProgressEntry(oct, null, emptyMap()))
        }
    }

    @Test fun acceptsOnlyDatedAndAngleMatchedLocalPhotoNames() {
        assertTrue(BodyProgressRules.validPhotoName(
            "body_front_20261001_1760000000000.jpg", ProgressPhotoAngle.FRONT, oct))
        assertFalse(BodyProgressRules.validPhotoName(
            "body_back_20261001_1760000000000.jpg", ProgressPhotoAngle.FRONT, oct))
        assertFalse(BodyProgressRules.validPhotoName(
            "body_front_20260901_1760000000000.jpg", ProgressPhotoAngle.FRONT, oct))
        assertFalse(BodyProgressRules.validPhotoName(
            "../body_front_20261001_1760000000000.jpg", ProgressPhotoAngle.FRONT, oct))
        assertFalse(BodyProgressRules.validPhotoName(
            "body_front_20261001_1760000000000.png", ProgressPhotoAngle.FRONT, oct))
    }
}
