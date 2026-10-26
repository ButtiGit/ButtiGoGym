package it.pulse.workout.sharing

import it.pulse.workout.data.*
import org.junit.Assert.*
import org.junit.Test
import java.util.Base64

class WorkoutHistoryPayloadTest {
    private val exercise = Exercise("machine", "Panatta \"test\"\n💪", "PETTO", 3, "8–12", equipment = "1FW037")
    private val day = WorkoutDay("fri", "VEN", "Petto", "", "60 min", listOf(exercise))
    private val record = SessionRecord(100, day.id, 200, 60, 2, 3,
        mapOf("machine:1" to SetEntry("32,5", "10", done = true, rir = 2),
            "warmup:machine:1" to SetEntry("10", "12", done = true, warmup = true),
            "orphan:1" to SetEntry("15", "8", done = false)), daySnapshot = day,
        photoFileName = "private_photo.jpg")
    @Test fun exportsAllRecordsAndRawEntriesWithoutPhotosOrCredentials() {
        val out = WorkoutHistoryPayload.build(listOf(record, record.copy(id = 101)), listOf(day), "2.0.0")
        assertEquals(2, out.workouts); assertEquals(4, out.sets)
        assertTrue(out.json.contains("orphan:1")); assertTrue(out.json.contains("32,5"))
        assertTrue(out.json.contains("1FW037")); assertTrue(out.json.contains("\\\"test\\\"\\n"))
        assertFalse(out.json.contains("private_photo")); assertFalse(out.json.contains("token"))
    }
    @Test fun deterministicHashDoesNotDependOnHistoryListOrder() {
        val other = record.copy(id = 101, completedAt = 300)
        assertEquals(WorkoutHistoryPayload.build(listOf(record, other), listOf(day), "2").hash,
            WorkoutHistoryPayload.build(listOf(other, record), listOf(day), "2").hash)
        assertNotEquals(WorkoutHistoryPayload.build(listOf(record), listOf(day), "2").hash,
            WorkoutHistoryPayload.build(listOf(other), listOf(day), "2").hash)
    }
    @Test fun chunkingReconstructsUnicodeByteForByte() {
        val payload = WorkoutHistoryPayload.build(listOf(record), listOf(day), "2")
        val bytes = payload.parts(7).flatMap { Base64.getDecoder().decode(it).asList() }.toByteArray()
        assertEquals(payload.json, String(bytes, Charsets.UTF_8))
        assertTrue(payload.parts().all { it.length <= 40_000 })
    }
    @Test fun missingSnapshotIsMarkedAndRawEntriesAreKept() {
        val out = WorkoutHistoryPayload.build(listOf(record.copy(daySnapshot = null)), emptyList(), "2")
        assertTrue(out.json.contains("unavailable")); assertTrue(out.json.contains("orphan:1"))
    }
    @Test fun escapesControlCharactersAndMarkdownIsOnlyData() {
        assertEquals("\"a\\t\\r\\n\\u0001\\\\\\\"```\"", WorkoutHistoryPayload.quote("a\t\r\n\u0001\\\"```"))
    }
}
