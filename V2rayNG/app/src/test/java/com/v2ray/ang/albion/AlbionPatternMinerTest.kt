package com.v2ray.ang.albion

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class AlbionPatternMinerTest {
    @TempDir lateinit var tempDir: File

    @Test fun groupsRepeatedResourceObservationsAcrossLabeledSamples() {
        val file = File(tempDir, "samples.jsonl")
        val lines = mutableListOf<String>()
        repeat(3) { index ->
            val base = 10_000L + index * 60_000L
            val id = 500L + index
            lines += """{"time_ms":${base},"manual_marker":{"timestamp_ms":${base},"activity":"Ore","event":"target_found","tier":"T6","enchantment":"E2"}}"""
            lines += """{"time_ms":${base + 1000},"entities":[{"kind":"harvestable","event_code":40,"entity_id":${id},"mobile_type_id":1001,"tier":6,"enchant":2}],"messages":[{"kind":"event","code":40,"param_keys":[0,5,7,8,11]}]}"""
            lines += """{"time_ms":${base + 5000},"manual_marker":{"timestamp_ms":${base + 5000},"activity":"Ore","event":"gathering_completed"}}"""
        }
        file.writeText(lines.joinToString("\n"))
        val report = AlbionPatternMiner.analyze(file)
        assertEquals(3, report.completeSamples)
        val pattern = report.patterns.first { it.kind == "harvestable" && it.activity == "Ore" }
        assertEquals(3, pattern.samples)
        assertEquals(3, pattern.distinctEntities)
        assertEquals("T6", pattern.overlayTierLabel)
        assertEquals("E2", pattern.overlayEnchantLabel)
        assertTrue(pattern.score >= 50)
        val eventPattern = report.patterns.first { it.kind == "photon_event" && it.eventCode == 40 }
        assertEquals(3, eventPattern.samples)
        assertEquals("0,5,7,8,11", eventPattern.parameterKeys)
    }

    @Test fun doesNotTreatAnUnclosedFoundMarkerAsACompleteSample() {
        val file = File(tempDir, "incomplete.jsonl")
        file.writeText("""{"time_ms":1000,"manual_marker":{"timestamp_ms":1000,"activity":"Ore","event":"target_found"}}""")
        val report = AlbionPatternMiner.analyze(file)
        assertEquals(0, report.completeSamples)
        assertEquals(1, report.incompleteStarts)
        assertTrue(report.warnings.any { it.contains("No complete") })
    }
}
