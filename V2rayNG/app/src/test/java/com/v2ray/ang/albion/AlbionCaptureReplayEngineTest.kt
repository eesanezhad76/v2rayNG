package com.v2ray.ang.albion

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
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
        val pipeline = AlbionCaptureReplayPipeline.replay(capture, context())
        assertEquals(0, pipeline.entityCount)
        assertEquals(0, pipeline.overlayAcceptedEntityCount)
        assertEquals(0, pipeline.invalidCoordinateCount)
        assertTrue(pipeline.overlaySnapshotValid)
        assertEquals("[]", pipeline.knowledgeSnapshotJson)
        assertTrue(!File(tempDir, "albion-live-knowledge.json").exists())
        assertTrue(!File(tempDir, "albion-protocol-evidence.json").exists())
        assertTrue(tempDir.listFiles()?.none { it.name.startsWith("albion-replay-") } != false)
        assertTrue(pipeline.replay.report.ipPackets >= 2)
        assertTrue(pipeline.protocolEvidenceSummary.contains("No item ID, stack delta, or Ore identity is asserted"))

        val output = File(tempDir, "reports/pipeline.json")
        AlbionCaptureReplayPipeline.writeJson(pipeline, output)
        val json = output.readText()
        assertTrue(json.contains("\"isolated_knowledge_store_replayed\": true"))
        assertTrue(json.contains("\"daemon_live_store_mutated\": false"))
        assertTrue(json.contains("\"overlay_delivery_verified\": false"))
        assertTrue(json.contains("\"overlay_snapshot_valid\": true"))
        assertTrue(json.contains("\"overlay_accepted_entity_count\": 0"))
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
