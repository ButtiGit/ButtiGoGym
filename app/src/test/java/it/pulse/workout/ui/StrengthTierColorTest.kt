package it.pulse.workout.ui

import androidx.compose.ui.graphics.toArgb
import it.pulse.workout.data.StrengthRanking
import org.junit.Assert.*
import org.junit.Test

class StrengthTierColorTest {
    @Test fun everyTierIsAnArgbColorAndSupportsAlphaCopies() {
        StrengthRanking.tiers.forEach { tier ->
            val color = strengthTierColor(tier)
            assertEquals(tier.colorHex.toInt(), color.toArgb())
            assertEquals(1f, color.alpha, .001f)
            assertEquals(.13f, color.copy(alpha = .13f).alpha, .005f)
        }
    }
}
