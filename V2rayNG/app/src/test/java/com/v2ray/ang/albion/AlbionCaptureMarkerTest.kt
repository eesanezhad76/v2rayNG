package com.v2ray.ang.albion

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class AlbionCaptureMarkerTest {
    @Test fun roundTripsCanonicalMarkerJson() {
        val json = """{"timestamp_ms":1234,"activity":"Ore","event":"target_found","tier":"T6"}"""
        val decoded = AlbionCaptureMarker.parse(AlbionCaptureMarker.build(json))
        assertEquals(1234L, decoded.getLong("timestamp_ms"))
        assertEquals("Ore", decoded.getString("activity"))
        assertEquals("target_found", decoded.getString("event"))
        assertEquals("T6", decoded.getString("tier"))
    }

    @Test fun acceptsLegacyEscapedNewlinePrefix() {
        val packet = legacyPacket("ALBION_TAG_V1\\n" + """{"activity":"Ore"}""")
        assertEquals("Ore", AlbionCaptureMarker.parse(packet).getString("activity"))
    }

    @Test fun rejectsNonMarkerAndMalformedJsonPackets() {
        val ordinaryIp = ByteArray(20).apply { this[0] = 0x45; this[9] = 17 }
        assertThrows(IllegalArgumentException::class.java) { AlbionCaptureMarker.parse(ordinaryIp) }
        val malformed = legacyPacket("ALBION_TAG_V1\nnot-json")
        assertThrows(org.json.JSONException::class.java) { AlbionCaptureMarker.parse(malformed) }
    }

    private fun legacyPacket(payloadText: String): ByteArray {
        val payload = payloadText.toByteArray(Charsets.UTF_8)
        val packet = ByteArray(20 + payload.size)
        packet[0] = 0x45
        packet[2] = (packet.size ushr 8).toByte()
        packet[3] = packet.size.toByte()
        packet[9] = 253.toByte()
        System.arraycopy(payload, 0, packet, 20, payload.size)
        return packet
    }
}
