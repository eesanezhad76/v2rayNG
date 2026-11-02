package com.v2ray.ang.albion

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import java.util.zip.ZipFile
import android.content.Context
import android.content.ContextWrapper
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.DataOutputStream
import java.io.File
import java.io.FileOutputStream

class AlbionCaptureReplayEngineTest {
    @TempDir lateinit var tempDir: File

    private fun context(): Context = object : ContextWrapper(null) {
        override fun getApplicationContext(): Context = this
        override fun getFilesDir(): File = tempDir
        override fun getCacheDir(): File = tempDir
    }

    @Test fun replayPipelineUsesIsolatedStoresAndProducesOverlayCompatibleSnapshot() {
        val capture = File(tempDir, "pipeline.pcap")
        writePcap(capture, listOf(byteArrayOf(1, 2, 3), byteArrayOf(4, 5, 6)))
        val pipeline = AlbionCaptureReplayPipeline.replay(capture, context(), nativeTapDrops = 3L)
        assertEquals(0, pipeline.entityCount)
        assertEquals(0, pipeline.overlayAcceptedEntityCount)
        assertEquals(0, pipeline.invalidCoordinateCount)
        assertTrue(pipeline.overlaySnapshotValid)
        assertEquals("[]", pipeline.knowledgeSnapshotJson)
        assertTrue(!File(tempDir, "albion-live-knowledge.json").exists())
        assertTrue(!File(tempDir, "albion-protocol-evidence.json").exists())
        assertTrue(tempDir.listFiles()?.none { it.name.startsWith("albion-replay-") } != false)
        assertTrue(pipeline.replay.report.ipPackets >= 2)
        assertEquals(3L, pipeline.replay.report.quality.nativeTapDrops)
        assertTrue(pipeline.protocolEvidenceSummary.contains("No item ID, stack delta, or Ore identity is asserted"))

        val output = File(tempDir, "reports/pipeline.json")
        val liveSnapshot = """[{"entity_id":"9","last_kind":"harvestable","x":1,"y":2}]"""
        AlbionCaptureReplayPipeline.writeJson(
            pipeline, output, liveSnapshot, "Overlay pipeline health: PIPELINE_ACTIVE"
        )
        val json = output.readText()
        assertTrue(json.contains("\"isolated_knowledge_store_replayed\": true"))
        assertTrue(json.contains("\"daemon_live_store_mutated\": false"))
        assertTrue(json.contains("\"overlay_delivery_verified\": false"))
        assertTrue(json.contains("\"overlay_snapshot_valid\": true"))
        assertTrue(json.contains("\"overlay_accepted_entity_count\": 0"))
        assertTrue(json.contains("\"capture_analysis_report\""))
        assertTrue(json.contains("\"manual_checkpoints\""))
        assertTrue(json.contains("\"capture_quality\""))
        assertTrue(json.contains("\"native_tap_drops\": 3"))
        assertTrue(json.contains("\"live_knowledge_snapshot_valid\": true"))
        assertTrue(json.contains("\"live_knowledge_entity_count\": 1"))
        assertTrue(json.contains("\"live_overlay_health_summary\": \"Overlay pipeline health: PIPELINE_ACTIVE\""))
    }

    @Test fun replaysCommittedInboxCapturesThroughKnowledgeEvidenceAndOverlayParser() {
        val inbox = listOf(
            File("../research/inbox"),
            File("../../research/inbox"),
            File("research/inbox")
        ).firstOrNull { candidate ->
            candidate.isDirectory && candidate.listFiles()?.any {
                it.isFile && it.extension.equals("zip", ignoreCase = true)
            } == true
        }
        assumeTrue(inbox != null, "No committed PCAP inbox directory is available in this checkout")
        val archives = inbox!!.listFiles()?.filter { it.isFile && it.extension.equals("zip", ignoreCase = true) }
            ?.sortedBy { it.name }.orEmpty()

        var decodedPhotonPackets = 0
        var harvestFinishedEvents = 0
        var inventoryEvents = 0
        var validOverlaySnapshots = 0
        archives.forEachIndexed { archiveIndex, archiveFile ->
            ZipFile(archiveFile).use { zip ->
                val entries = zip.entries().asSequence()
                    .filter { !it.isDirectory && it.name.endsWith(".pcap", ignoreCase = true) }
                    .toList()
                entries.forEachIndexed { entryIndex, entry ->
                    val capture = File(tempDir, "inbox-$archiveIndex-$entryIndex.pcap")
                    zip.getInputStream(entry).use { input ->
                        capture.outputStream().use { output -> input.copyTo(output) }
                    }
                    val result = AlbionCaptureReplayPipeline.replay(capture, context())
                    if (result.replay.report.validPhotonPackets > 0) {
                        decodedPhotonPackets += result.replay.report.validPhotonPackets
                        val harvest = Regex("HarvestFinished events retained across captures: (\\d+)")
                            .find(result.protocolEvidenceSummary)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 0
                        val inventory = Regex("Inventory events retained across captures: (\\d+)")
                            .find(result.protocolEvidenceSummary)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 0
                        harvestFinishedEvents += harvest
                        inventoryEvents += inventory
                        assertTrue(result.overlaySnapshotValid, "Overlay snapshot rejected for ${archiveFile.name}:${entry.name}")
                        validOverlaySnapshots++
                    }
                    capture.delete()
                }
            }
        }
        assertTrue(decodedPhotonPackets > 0, "No committed PCAP produced valid Photon packets")
        assertTrue(harvestFinishedEvents > 0, "No HarvestFinished events were replayed from committed captures")
        assertTrue(inventoryEvents > 0, "No Inventory events were replayed from committed captures")
        assertTrue(validOverlaySnapshots > 0, "No overlay-compatible snapshots were produced")
        assertTrue(!File(tempDir, "albion-live-knowledge.json").exists())
        assertTrue(!File(tempDir, "albion-protocol-evidence.json").exists())
    }

    @Test fun replayIsDeterministicAndWritesAuditableScopeMetadata() {
        val capture = File(tempDir, "replay.pcap")
        writePcap(capture, listOf(byteArrayOf(1, 2, 3), byteArrayOf(4, 5, 6)))
        val first = AlbionCaptureReplayEngine.replay(capture)
        val second = AlbionCaptureReplayEngine.replay(capture)
        assertEquals(first.sourceSha256, second.sourceSha256)
        assertEquals(first.fingerprint, second.fingerprint)
        assertTrue(AlbionCaptureReplayEngine.compare(first, second).identicalFingerprint)

        val output = File(tempDir, "reports/replay.json")
        AlbionCaptureReplayEngine.writeJson(first, output)
        val json = output.readText()
        assertTrue(json.contains("\"replay_scope\": \"pcap_photon_protocol18_event_mapper\""))
        assertTrue(json.contains("\"live_store_replayed\": false"))
        assertTrue(json.contains("\"overlay_delivery_verified\": false"))
    }

    @Test fun comparisonFlagsARegressionWithoutInventingEntityIdentity() {
        val capture = File(tempDir, "same-source.pcap")
        writePcap(capture, listOf(byteArrayOf(1), byteArrayOf(2), byteArrayOf(3), byteArrayOf(4), byteArrayOf(5)))
        val previous = AlbionCaptureReplayEngine.replay(capture)
        val degradedReport = previous.report.copy(ipPackets = previous.report.ipPackets - 3)
        val current = previous.copy(report = degradedReport, fingerprint = "synthetic-regression")
        val diff = AlbionCaptureReplayEngine.compare(previous, current)
        assertFalse(diff.identicalFingerprint)
        assertTrue(diff.sameSourceCapture)
        assertEquals(-3, diff.ipPacketDelta)
        assertTrue(diff.regressionSignals.any { it.contains("IP packet count decreased") })
    }

    @Test fun differentSourceCapturesProduceDescriptiveDeltasNotRegressionClaims() {
        val firstFile = File(tempDir, "first.pcap")
        val secondFile = File(tempDir, "second.pcap")
        writePcap(firstFile, listOf(byteArrayOf(1), byteArrayOf(2), byteArrayOf(3)))
        writePcap(secondFile, listOf(byteArrayOf(1), byteArrayOf(2)))
        val first = AlbionCaptureReplayEngine.replay(firstFile)
        val second = AlbionCaptureReplayEngine.replay(secondFile)
        val diff = AlbionCaptureReplayEngine.compare(first, second)
        assertFalse(diff.sameSourceCapture)
        assertTrue(diff.regressionSignals.isEmpty())
        assertTrue(diff.comparisonNote.contains("descriptive only"))
    }

    private fun writePcap(file: File, packets: List<ByteArray>) {
        DataOutputStream(FileOutputStream(file)).use { out ->
            fun intLe(value: Int) = out.writeInt(Integer.reverseBytes(value))
            fun shortLe(value: Int) = out.writeShort(java.lang.Short.reverseBytes(value.toShort()).toInt())
            intLe(0xa1b2c3d4.toInt())
            shortLe(2); shortLe(4)
            intLe(0); intLe(0); intLe(65535); intLe(101)
            packets.forEachIndexed { index, packet ->
                intLe(10); intLe(index * 100_000)
                intLe(packet.size); intLe(packet.size); out.write(packet)
            }
        }
    }
}
