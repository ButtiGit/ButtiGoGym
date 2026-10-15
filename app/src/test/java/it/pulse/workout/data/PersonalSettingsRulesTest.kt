package it.pulse.workout.data

import org.junit.Assert.*
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalTime

class PersonalSettingsRulesTest {
    @Test fun defaultsKeepOriginalScheduleAndWorkoutGoal() {
        val defaults = PersonalSettingsRules.validate(PersonalSettings())
        assertEquals(3, defaults.weeklyWorkouts)
        assertEquals(FitnessGoal.LEAN_BULK, defaults.goal)
        assertEquals(DayOfWeek.entries.toSet(), defaults.reminderDays)
        assertEquals(LocalTime.of(8, 30), defaults.reminderStart)
        assertEquals(LocalTime.of(18, 0), defaults.reminderEnd)
    }

    @Test fun rejectImpossibleGoalsAndMissingDays() {
        assertThrows(IllegalArgumentException::class.java) {
            PersonalSettingsRules.validate(PersonalSettings(weeklyWorkouts = 0))
        }
        assertThrows(IllegalArgumentException::class.java) {
            PersonalSettingsRules.validate(PersonalSettings(weeklyWorkouts = 8))
        }
        assertThrows(IllegalArgumentException::class.java) {
            PersonalSettingsRules.validate(PersonalSettings(reminderDays = emptySet()))
        }
        assertThrows(IllegalArgumentException::class.java) {
            PersonalSettingsRules.validate(PersonalSettings(
                reminderStart = LocalTime.of(18, 0),
                reminderEnd = LocalTime.of(8, 30)
            ))
        }
    }
}
