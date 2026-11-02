package com.v2ray.ang.albion

import org.json.JSONArray
import org.json.JSONObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AlbionInventoryEvidenceAnalyzerTest {
    @Test fun reportsRepeatedNumericPathsOnlyAfterThreeIndependentHarvests() {
        val events = mutableListOf<JSONObject>()
        repeat(3) { index ->
            val finish = 10_000L + index * 20_000L
            events += JSONObject().put("kind", "harvest").put("event_code", 61).put("time_ms", finish)
            events += JSONObject().put("kind", "inventory").put("event_code", 26)
                .put("time_ms", finish + 500L)
                .put("parameters", JSONObject().put("0", 9001).put("1", index + 1)
                    .put("2", JSONArray().put(574).put(5)))
        }
        val candidates = AlbionInventoryEvidenceAnalyzer.analyze(events)
        assertEquals(4, candidates.size)
        val scalar = candidates.single { it.parameterPath == "1" }
        assertEquals(3, scalar.independentHarvests)
        assertEquals(3, scalar.observations)
        assertEquals(1.0, scalar.minimum)
        assertEquals(3.0, scalar.maximum)
        assertEquals(2, scalar.positiveDeltaCount)
        assertEquals(0, scalar.negativeDeltaCount)
        assertEquals(0, scalar.zeroDeltaCount)
        assertEquals(1.0, scalar.minimumDelta)
        assertEquals(1.0, scalar.maximumDelta)
        assertTrue(candidates.any { it.parameterPath == "2[0]" })
    }

    @Test fun doesNotPromoteOneOrTwoHarvestsIntoARepeatedSchemaCandidate() {
        val events = mutableListOf<JSONObject>()
        repeat(2) { index ->
            val finish = 1_000L + index * 10_000L
            events += JSONObject().put("kind", "harvest").put("event_code", 61).put("time_ms", finish)
            events += JSONObject().put("kind", "inventory").put("event_code", 26)
                .put("time_ms", finish + 100L)
                .put("parameters", JSONObject().put("0", 123).put("1", 1))
        }
        assertTrue(AlbionInventoryEvidenceAnalyzer.analyze(events).isEmpty())
    }

    @Test fun ignoresInventoryEventsOutsideHarvestWindowAndNonNumericFields() {
        val events = mutableListOf<JSONObject>()
        repeat(3) { index ->
            val finish = 2_000L + index * 10_000L
            events += JSONObject().put("kind", "harvest").put("event_code", 61).put("time_ms", finish)
            events += JSONObject().put("kind", "inventory").put("event_code", 26)
                .put("time_ms", finish + 30_000L)
                .put("parameters", JSONObject().put("0", "not-numeric"))
        }
        assertTrue(AlbionInventoryEvidenceAnalyzer.analyze(events).isEmpty())
    }
}
