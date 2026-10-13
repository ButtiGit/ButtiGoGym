package it.pulse.workout.updates

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppVersionTest {
    @Test fun ordersNumerically() {
        assertTrue(UpdateVersionPolicy.shouldOffer("0.9.5", "v0.9.10"))
        assertTrue(UpdateVersionPolicy.shouldOffer("0.9.5", "v1.0.0"))
        assertTrue(UpdateVersionPolicy.shouldOffer("1.1.9", "v1.2.0"))
    }
    @Test fun rejectsOldSameAndPrerelease() {
        assertFalse(UpdateVersionPolicy.shouldOffer("0.9.5", "v0.9.5"))
        assertFalse(UpdateVersionPolicy.shouldOffer("1.0.0", "v0.9.9"))
        assertFalse(UpdateVersionPolicy.shouldOffer("1.0.0", "v1.1.0-beta.1"))
        assertFalse(UpdateVersionPolicy.shouldOffer("1.0.0", "latest"))
    }
    @Test fun supportsThreeComponentsAndPrefixOnly() {
        assertEquals(AppVersion(1, 2, 3), AppVersion.parse("v1.2.3"))
        assertNull(AppVersion.parse("v1.2"))
        assertNull(AppVersion.parse("1.2.3.4"))
        assertNull(AppVersion.parse("01.2.3"))
    }
}
