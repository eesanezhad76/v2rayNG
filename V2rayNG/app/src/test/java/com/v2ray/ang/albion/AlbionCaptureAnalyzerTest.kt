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
    @Test fun classifiesInboundAndOutboundByTheServerPortSide() {
        val file = File(tempDir, "direction-test.pcap")
        DataOutputStream(FileOutputStream(file)).use { out ->
            putPcapHeader(out)
            putPcapRecord(out, photonUdpPacket(5056, 40000), 1)
            putPcapRecord(out, photonUdpPacket(40000, 5056), 2)
        }
        val report = AlbionCaptureAnalyzer.analyze(file)
        assertEquals(2, report.validPhotonPackets)
        assertEquals(1, report.inboundPackets)
        assertEquals(1, report.outboundPackets)
    }

    private fun putPcapHeader(out: DataOutputStream) {
        out.writeInt(Integer.reverseBytes(0xa1b2c3d4.toInt()))
        out.writeShort(java.lang.Short.reverseBytes(2.toShort()).toInt())
        out.writeShort(java.lang.Short.reverseBytes(4.toShort()).toInt())
        out.writeInt(0)
        out.writeInt(0)
        out.writeInt(Integer.reverseBytes(65535))
        out.writeInt(Integer.reverseBytes(101))
    }

    private fun putPcapRecord(out: DataOutputStream, packet: ByteArray, seconds: Int) {
        out.writeInt(Integer.reverseBytes(seconds))
        out.writeInt(0)
        out.writeInt(Integer.reverseBytes(packet.size))
        out.writeInt(Integer.reverseBytes(packet.size))
        out.write(packet)
    }

    private fun photonUdpPacket(srcPort: Int, dstPort: Int): ByteArray {
        val packet = ByteArray(52)
        packet[0] = 0x45
        put16(packet, 2, packet.size)
        packet[8] = 64
        packet[9] = 17
        packet[12] = 10
        packet[15] = 2
        packet[16] = 1
        packet[19] = 1
        put16(packet, 20, srcPort)
        put16(packet, 22, dstPort)
        put16(packet, 24, packet.size - 20)
        val photon = 28
        put16(packet, photon, 1)
        packet[photon + 3] = 1
        val command = photon + 12
        packet[command] = 7
        put32(packet, command + 4, 12)
        return packet
    }

    private fun put16(bytes: ByteArray, offset: Int, value: Int) {
        bytes[offset] = (value ushr 8).toByte()
        bytes[offset + 1] = value.toByte()
    }

    private fun put32(bytes: ByteArray, offset: Int, value: Int) {
        bytes[offset] = (value ushr 24).toByte()
        bytes[offset + 1] = (value ushr 16).toByte()
        bytes[offset + 2] = (value ushr 8).toByte()
        bytes[offset + 3] = value.toByte()
    }
}
