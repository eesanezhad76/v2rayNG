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
        assertEquals(6, report.markers, report.asText())
        assertEquals(3, report.completeSamples, report.asText())
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
        assertEquals(1, report.markers, report.asText())
        assertEquals(1, report.incompleteStarts, report.asText())
        assertTrue(report.warnings.any { it.contains("No complete") })
    }
    @Test fun keepsMultiplePendingFoundTargetsInFifoOrder() {
        val file = File(tempDir, "multiple-targets.jsonl")
        file.writeText(listOf(
            """{"time_ms":1000,"manual_marker":{"timestamp_ms":1000,"activity":"Ore","event":"target_found","tier":"T5","enchantment":"E1"}}""",
            """{"time_ms":1500,"manual_marker":{"timestamp_ms":1500,"activity":"Ore","event":"target_found","tier":"T4","enchantment":"E2"}}""",
            """{"time_ms":1750,"manual_marker":{"timestamp_ms":1750,"activity":"Ore","event":"gathering_started"}}""",
            """{"time_ms":2000,"entities":[{"kind":"harvestable","event_code":40,"entity_id":701,"mobile_type_id":574,"tier":5,"enchant":1}]}""",
            """{"time_ms":3000,"manual_marker":{"timestamp_ms":3000,"activity":"Ore","event":"gathering_completed"}}""",
            """{"time_ms":3500,"manual_marker":{"timestamp_ms":3500,"activity":"Ore","event":"gathering_started"}}""",
            """{"time_ms":4000,"entities":[{"kind":"harvestable","event_code":40,"entity_id":702,"mobile_type_id":573,"tier":4,"enchant":2}]}""",
            """{"time_ms":5000,"manual_marker":{"timestamp_ms":5000,"activity":"Ore","event":"gathering_completed"}}"""
        ).joinToString("\n"))
        val report = AlbionPatternMiner.analyze(file)
        assertEquals(4, report.markers, report.asText())
        assertEquals(2, report.completeSamples, report.asText())
        assertEquals(0, report.incompleteStarts, report.asText())
        val first = report.patterns.first { it.kind == "harvestable" && it.overlayTierLabel == "T5" }
        val second = report.patterns.first { it.kind == "harvestable" && it.overlayTierLabel == "T4" }
        assertEquals(574, first.typeId)
        assertEquals(573, second.typeId)
        assertEquals(1, first.distinctEntities)
        assertEquals(1, second.distinctEntities)
    }

    @Test fun doesNotAttachPacketsObservedBeforeFoundMarker() {
        val file = File(tempDir, "pre-marker.jsonl")
        file.writeText(listOf(
            """{"time_ms":1000,"entities":[{"kind":"harvestable","event_code":40,"entity_id":801,"mobile_type_id":574,"tier":5,"enchant":1}]}""",
            """{"time_ms":2000,"manual_marker":{"timestamp_ms":2000,"activity":"Ore","event":"target_found","tier":"T5","enchantment":"E1"}}""",
            """{"time_ms":2500,"manual_marker":{"timestamp_ms":2500,"activity":"Ore","event":"gathering_started"}}""",
            """{"time_ms":3000,"entities":[{"kind":"harvestable","event_code":40,"entity_id":802,"mobile_type_id":574,"tier":5,"enchant":1}]}""",
            """{"time_ms":4000,"manual_marker":{"timestamp_ms":4000,"activity":"Ore","event":"gathering_completed"}}"""
        ).joinToString("\n"))
        val report = AlbionPatternMiner.analyze(file)
        assertEquals(1, report.completeSamples, report.asText())
        val pattern = report.patterns.first { it.kind == "harvestable" }
        assertEquals(1, pattern.distinctEntities)
        assertTrue(pattern.signature.contains("574"))
    }

}
