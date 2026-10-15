package it.pulse.workout.data

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class AdaptiveCalendarPlannerTest {
    private fun d(value: String) = LocalDate.parse(value)
    private fun plan(value: String) = CalendarEntry(d(value), CalendarEntryKind.WORKOUT, "mon")

    @Test fun suggestsFreeDatesAndPrefersRecoverySpacing() {
        val source = d("2026-10-15")
        val entries = listOf(plan("2026-10-15"), plan("2026-10-17"))
        val choices = AdaptiveCalendarPlanner.suggestions(source, entries,
            emptySet(), emptySet(), source)
        assertEquals(3, choices.size)
        assertEquals(d("2026-10-19"), choices.first().date)
        assertTrue(choices.none { it.date == d("2026-10-17") })
        assertTrue(choices.none { it.recoveryWarning })
    }

    @Test fun destinationNeverOverwritesRestSkippedOrCompleted() {
        val today=d("2026-10-15")
        val entries=listOf(plan("2026-10-15"),
            CalendarEntry(d("2026-10-16"),CalendarEntryKind.SKIPPED),
            plan("2026-10-18"))
        val choices=AdaptiveCalendarPlanner.suggestions(today,entries,
            setOf(d("2026-10-20")),setOf(d("2026-10-19")),today,limit=12)
        assertFalse(choices.any { it.date in setOf(
            d("2026-10-16"),d("2026-10-18"),d("2026-10-19"),d("2026-10-20")) })
    }

    @Test fun cannotRescheduleCompletedSourceOrMissingSource() {
        val source=d("2026-10-15")
        assertTrue(AdaptiveCalendarPlanner.suggestions(source,listOf(plan("2026-10-15")),
            setOf(source),emptySet(),source).isEmpty())
        assertTrue(AdaptiveCalendarPlanner.suggestions(source,emptyList(),
            emptySet(),emptySet(),source).isEmpty())
    }

    @Test fun oldSourceSuggestsOnlyTodayOrFuture() {
        val source=d("2026-10-01")
        val today=d("2026-10-15")
        val choices=AdaptiveCalendarPlanner.suggestions(source,listOf(plan("2026-10-01")),
            emptySet(),emptySet(),today)
        assertTrue(choices.all { !it.date.isBefore(today) })
    }

    @Test fun tightCalendarProvidesWarningInsteadOfOverwritingPlans() {
        val from=d("2026-10-15")
        val planned=(15..28).filter { it % 2 == 1 }.map {
            plan("2026-10-$it")
        }
        val choices=AdaptiveCalendarPlanner.suggestions(from,planned,
            emptySet(),emptySet(),from)
        assertTrue(choices.isNotEmpty())
        assertTrue(choices.all { it.date !in planned.map(CalendarEntry::date) })
    }
}
