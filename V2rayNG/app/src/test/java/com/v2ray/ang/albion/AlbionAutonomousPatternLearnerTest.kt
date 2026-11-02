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

    @Test fun eventWithoutParametersIsStillRecordedAsAnUnknownSignature() {
        val learner = AlbionAutonomousPatternLearner(context(), "capture-empty")
        learner.observe(1_000L, 325, emptyMap())
        learner.flush()
        val rows = org.json.JSONArray(learner.snapshotJson())
        assertEquals(1, rows.length())
        assertEquals("unknown_candidate", rows.getJSONObject(0).getString("status"))
    }
}
