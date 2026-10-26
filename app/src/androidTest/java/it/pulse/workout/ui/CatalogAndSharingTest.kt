package it.pulse.workout.ui

import androidx.compose.ui.test.*
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import it.pulse.workout.data.*
import it.pulse.workout.sharing.*
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.IOException

class CatalogAndSharingTest {
    @get:Rule val rule = createComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun searchAndAddPanattaThenRemoveOnlyFromTheDraft() {
        val original = Exercise("bench", "Panca", "PETTO", 3, "8–12")
        val day = WorkoutDay("db-test", "LUN", "Petto", "", "60 min", listOf(original))
        var saved: List<Exercise>? = null
        val currentDay = mutableStateOf(day)
        rule.setContent { PulseTheme {
            PlanDayEditor(currentDay.value, false, { saved = it; currentDay.value = currentDay.value.copy(exercises = it); null }, { null }, {})
        } }
        rule.onNodeWithContentDescription("Aggiungi dal database").performClick()
        rule.onNodeWithTag("exercise-search").performTextInput("1FW080")
        rule.onNodeWithText("Panatta Super Pendulum Squat").performClick()
        rule.onNodeWithText("Panatta Super Pendulum Squat").assertExists()
        rule.runOnIdle { assertNull(saved); assertEquals(1, day.exercises.size) }
        saveTestScreenshot(rule, "14-catalog-added")
        rule.onNodeWithText("Salva personalizzazioni").performClick()
        rule.runOnIdle { assertEquals(2, saved!!.size) }
        rule.onNodeWithContentDescription("Rimuovi Panatta Super Pendulum Squat").performClick()
        rule.onNodeWithText("Rimuovi", substring = false).performClick()
        rule.onNodeWithText("Salva personalizzazioni").performClick()
        rule.runOnIdle { assertEquals(listOf(original), saved) }
    }

    @Test fun historyPanelShowsPublicPreviewBeforeAnyConnectionOrUpload() {
        GitHubTokenStore(context).clear()
        val record = SessionRecord(1, "test", 100, 30, 1, 1,
            mapOf("bench:1" to SetEntry("30", "10", done = true)))
        rule.setContent { PulseTheme { HistoryUploadPanel(listOf(record), emptyList(), {}) } }
        rule.waitUntil(5000) { rule.onAllNodesWithText("Il repository è pubblico").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("Il repository è pubblico").assertIsDisplayed()
        rule.onNodeWithText("Anteprima dei dati").performClick()
        rule.onNodeWithText("Invia storico su GitHub pubblico").assertDoesNotExist()
        saveTestScreenshot(rule, "15-history-preview")
    }

    @Test fun payloadIsValidJsonAndIncludesOrphanEntries() {
        val record = SessionRecord(1, "old-day", 100, 30, 1, 1,
            mapOf("missing:1" to SetEntry("32,5", "10", done = true, rir = 2)))
        val payload = WorkoutHistoryPayload.build(listOf(record), emptyList(), "2.0.0")
        val decoded = JSONObject(payload.json).getJSONArray("workouts").getJSONObject(0)
        assertEquals("unavailable", decoded.getString("metadataSource"))
        assertEquals("32,5", decoded.getJSONArray("entries").getJSONObject(0).getString("weightKg"))
    }

    @Test fun tokenIsEncryptedAndCanBeRemoved() {
        val store = GitHubTokenStore(context)
        store.clear()
        val token = "github_pat_test_only_not_real_1234567890"
        store.save(token)
        assertEquals(token, GitHubTokenStore(context).read())
        val prefs = context.getSharedPreferences("github_history_token", 0)
        assertFalse(prefs.all.values.joinToString().contains(token))
        store.clear(); assertNull(store.read())
    }

    @Test fun interruptedUploadResumesWithoutDuplicatePartsAndOnlyThenCompletes() {
        context.getSharedPreferences("github_history_upload", 0).edit().clear().commit()
        val comments = mutableListOf<String>()
        var createCalls = 0
        var failOnce = true
        val fake = GitHubHistoryTransport { method, path, _, body ->
            when {
                method == "GET" -> JSONArray().also { array -> comments.forEach { array.put(JSONObject().put("body", it)) } }.toString()
                path == "issues" -> {
                    createCalls++
                    JSONObject().put("number", 42).put("html_url", "${GitHubHistoryUploader.REPOSITORY}/issues/42").toString()
                }
                else -> {
                    val text = JSONObject(body!!).getString("body")
                    comments += text
                    if (":part:2 -->" in text && failOnce) { failOnce = false; throw IOException("Simulated timeout after server accepted part") }
                    JSONObject().put("id", comments.size).toString()
                }
            }
        }
        val payload = HistoryPayload("{\"data\":\"${"💪".repeat(35_000)}\"}", "a".repeat(64), 1, 1)
        val uploader = GitHubHistoryUploader(context, fake)
        assertThrows(IOException::class.java) { uploader.send(payload, "fake-token") }
        assertFalse(comments.any { ":complete -->" in it })
        val url = uploader.send(payload, "fake-token")
        assertEquals("${GitHubHistoryUploader.REPOSITORY}/issues/42", url)
        assertEquals(1, createCalls)
        assertEquals(payload.parts().size, comments.count { ":part:" in it })
        assertEquals(1, comments.count { ":complete -->" in it })
        val count = comments.size
        uploader.send(payload, "fake-token")
        assertEquals(count, comments.size)
        context.getSharedPreferences("github_history_upload", 0).edit().clear().commit()
    }
}
