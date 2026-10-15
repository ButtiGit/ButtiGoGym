package it.pulse.workout.notifications

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RestAlertPolicyTest {
    private val started = 1_700_000_000_000L
    private val due = started + 90_000L

    @Test fun sendsOnlyForActiveSavedRestAndAtOrAfterDeadline() {
        assertTrue(RestAlertPolicy.shouldNotify(started, due, started, due, due))
        assertTrue(RestAlertPolicy.shouldNotify(started, due, started, due, due + 7_000))
        assertFalse(RestAlertPolicy.shouldNotify(started, due, started, due, due - 1))
    }

    @Test fun rejectsAlertsFromResetOrReplacedWorkout() {
        assertFalse(RestAlertPolicy.shouldNotify(started + 1_000, due, started, due, due))
        assertFalse(RestAlertPolicy.shouldNotify(started, due + 10_000, started, due, due))
        assertFalse(RestAlertPolicy.shouldNotify(started, 0L, started, due, due))
    }

    @Test fun preventsStaleAlertLongAfterRestWasDue() {
        assertFalse(RestAlertPolicy.shouldNotify(started, due, started, due, due + 15 * 60_000L + 1))
        assertFalse(RestAlertPolicy.shouldNotify(0L, due, 0L, due, due))
    }
}
