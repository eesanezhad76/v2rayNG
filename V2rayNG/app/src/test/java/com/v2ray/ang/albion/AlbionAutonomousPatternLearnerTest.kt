package com.v2ray.ang.albion

import android.content.Context
import android.content.ContextWrapper
import org.json.JSONObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class AlbionAutonomousPatternLearnerTest {
    @TempDir lateinit var tempDir: File

    private fun context(): Context = object : ContextWrapper(null) {
        override fun getApplicationContext(): Context = this
        override fun getFilesDir(): File = tempDir
    }

    private fun sample(learner: AlbionAutonomousPatternLearner, start: Long) {
        learner.observeMarker(JSONObject().put("event", "target_found").put("activity", "Ore")
            .put("tier", "T3").put("enchantment", "E1"), start)
        learner.observe(start + 1, 40, mapOf(0 to 123L, 6 to 574, 7 to 3))
        learner.observe(start + 2, 40, mapOf(0 to 124L, 6 to 574, 7 to 3))
        learner.observeMarker(JSONObject().put("event", "gathering_completed"), start + 3)
        learner.flush()
    }

    @Test fun learnsUnlabelledSignaturesAndCorroboratesRepeatedManualLabelsAcrossSessions() {
        val firstSession = AlbionAutonomousPatternLearner(context(), "capture-A")
        sample(firstSession, 1_000L)
        sample(firstSession, 2_000L)

        val secondSession = AlbionAutonomousPatternLearner(context(), "capture-B")
        sample(secondSession, 3_000L)

        val rows = org.json.JSONArray(secondSession.snapshotJson())
        assertEquals(1, rows.length())
        val pattern = rows.getJSONObject(0)
        assertEquals(6, pattern.getInt("sightings"))
        assertEquals("label_corroborated_candidate", pattern.getString("status"))
        assertEquals(2, pattern.getJSONArray("capture_sessions").length())
        assertEquals(3, pattern.getJSONArray("labels").getJSONObject(0).getInt("completed_samples"))
        assertTrue(secondSession.summaryText().contains("marker-correlated: 1"))
    }

    @Test fun mobFightLabelRemainsOpenUntilExplicitKillMarker() {
        val learner = AlbionAutonomousPatternLearner(context(), "capture-mob")
        learner.observeMarker(JSONObject().put("event", "pvp_started").put("activity", "Mob"), 1_000L)
        learner.observe(16 * 60_000L + 1L, 47, mapOf(0 to 777L, 1 to 0.75f))
        learner.observeMarker(JSONObject().put("event", "mob_killed"), 16 * 60_000L + 2L)

        val rows = org.json.JSONArray(learner.snapshotJson())
        assertEquals(1, rows.length())
        val labels = rows.getJSONObject(0).getJSONArray("labels")
        assertEquals(1, labels.length())
        assertEquals(1, labels.getJSONObject(0).getInt("completed_samples"))
    }

    @Test fun replacementStartDiscardsAnIncompletePreviousActivity() {
        val learner = AlbionAutonomousPatternLearner(context(), "capture-replaced")
        learner.observeMarker(JSONObject().put("event", "target_found").put("activity", "Ore")
            .put("tier", "T5").put("enchantment", "E3"), 1_000L)
        learner.observe(1_100L, 40, mapOf(0 to 100L, 6 to 574, 7 to 5))
        learner.observeMarker(JSONObject().put("event", "target_found").put("activity", "Mob"), 2_000L)
        learner.observe(2_100L, 47, mapOf(0 to 777L, 1 to 0.5f))
        learner.observeMarker(JSONObject().put("event", "mob_killed"), 2_200L)

        val rows = org.json.JSONArray(learner.snapshotJson())
        assertEquals(2, rows.length())
        val ore = (0 until rows.length()).map { rows.getJSONObject(it) }
            .single { it.getInt("event_code") == 40 }
        val mob = (0 until rows.length()).map { rows.getJSONObject(it) }
            .single { it.getInt("event_code") == 47 }
        assertEquals(0, ore.getJSONArray("labels").length())
        assertEquals(1, mob.getJSONArray("labels").getJSONObject(0).getInt("completed_samples"))
    }

    @Test fun historicalSignaturesSeedRuntimeMemoryAndMergeWithoutDoubleCounting() {
        val signature = "a".repeat(64)
        val label = JSONObject().put("label_key", "Ore|T5|E1")
            .put("activity", "Ore").put("tier", "T5").put("enchantment", "E1")
            .put("completed_samples", 3)
            .put("sample_sessions", org.json.JSONArray().put("historical-A").put("historical-B"))
        val historical = org.json.JSONArray().put(
            JSONObject().put("signature", signature).put("event_code", 40)
                .put("parameter_shape", "0:integer,6:integer")
                .put("first_seen_ms", 100).put("last_seen_ms", 200).put("sightings", 12)
                .put("capture_sessions", org.json.JSONArray().put("historical-A").put("historical-B"))
                .put("labels", org.json.JSONArray().put(label))
                .put("source_bundles", org.json.JSONArray().put("bundle-sha"))
                .put("status", "label_corroborated_candidate")
        )
        val local = org.json.JSONArray().put(
            JSONObject().put("signature", signature).put("event_code", 40)
                .put("parameter_shape", "0:integer,6:integer")
                .put("first_seen_ms", 50).put("last_seen_ms", 250).put("sightings", 8)
                .put("capture_sessions", org.json.JSONArray().put("local-session"))
                .put("labels", org.json.JSONArray())
        )
        File(tempDir, "albion-autonomous-patterns.json").writeText(local.toString(), Charsets.UTF_8)

        val learner = AlbionAutonomousPatternLearner(context(), "current-session", historical.toString())
        val rows = org.json.JSONArray(learner.snapshotJson())
        assertEquals(1, rows.length())
        val merged = rows.getJSONObject(0)
        assertEquals(12, merged.getInt("sightings"), "Cumulative exports must not double-count sightings")
        assertEquals(50, merged.getLong("first_seen_ms"))
        assertEquals(250, merged.getLong("last_seen_ms"))
        assertEquals(3, merged.getJSONArray("capture_sessions").length())
        assertEquals(1, merged.getJSONArray("labels").length())
        assertEquals(3, merged.getJSONArray("labels").getJSONObject(0).getInt("completed_samples"))
        assertEquals("label_corroborated_candidate", merged.getString("status"))
        assertTrue(File(tempDir, "albion-autonomous-patterns.json").isFile)
    }

    @Test fun eventWithoutParametersIsStillRecordedAsAnUnknownSignature() {
        val learner = AlbionAutonomousPatternLearner(context(), "capture-empty")
        learner.observe(1_000L, 325, emptyMap())
        learner.flush()
        val rows = org.json.JSONArray(learner.snapshotJson())
        assertEquals(1, rows.length())
        assertEquals("unknown_candidate", rows.getJSONObject(0).getString("status"))
    }
}
