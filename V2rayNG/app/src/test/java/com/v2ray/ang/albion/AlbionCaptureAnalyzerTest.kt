package com.v2ray.ang.albion

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.DataOutputStream
import java.io.File
import java.io.FileOutputStream

class AlbionCaptureAnalyzerTest {
    @TempDir lateinit var tempDir: File

    @Test fun reportsLocalOverlayCheckpointsFromPcap() {
        val marker = AlbionCaptureMarker.build(
            """{"timestamp_ms":1234,"elapsed_realtime_ms":5678,"event":"mob_killed","activity":"Mobs","tier":"T6","enchantment":"E2","source":"manual_overlay"}"""
        )
        val file = File(tempDir, "checkpoint-test.pcap")
        DataOutputStream(FileOutputStream(file)).use { out ->
            out.writeInt(Integer.reverseBytes(0xa1b2c3d4.toInt()))
            out.writeShort(java.lang.Short.reverseBytes(2.toShort()).toInt())
            out.writeShort(java.lang.Short.reverseBytes(4.toShort()).toInt())
            out.writeInt(0)
            out.writeInt(0)
            out.writeInt(Integer.reverseBytes(65535))
            out.writeInt(Integer.reverseBytes(101))
            out.writeInt(Integer.reverseBytes(1))
            out.writeInt(Integer.reverseBytes(234000))
            out.writeInt(Integer.reverseBytes(marker.size))
            out.writeInt(Integer.reverseBytes(marker.size))
            out.write(marker)
        }
        val report = AlbionCaptureAnalyzer.analyze(file)
        assertEquals(1, report.ipPackets)
        assertEquals(listOf("Mobs: mob_killed @ 1234"), report.checkpoints)
    }
}
