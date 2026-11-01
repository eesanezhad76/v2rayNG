package com.v2ray.ang.albion

import java.util.concurrent.atomic.AtomicInteger

/** Builds a local-only synthetic IPv4 record for PCAP annotations. Never routed to the VPN/game. */
object AlbionCaptureMarker {
    private val nextId = AtomicInteger(1)

    fun build(markerJson: String): ByteArray {
        val payload = ("ALBION_TAG_V1\\n" + markerJson).toByteArray(Charsets.UTF_8)
        require(payload.size <= 65515) { "Marker too large" }
        val packet = ByteArray(20 + payload.size)
        packet[0] = 0x45
        packet[2] = (packet.size ushr 8).toByte()
        packet[3] = packet.size.toByte()
        val id = nextId.getAndIncrement() and 0xffff
        packet[4] = (id ushr 8).toByte()
        packet[5] = id.toByte()
        packet[6] = 0x40
        packet[8] = 64
        packet[9] = 253.toByte()
        packet[12] = 192.toByte(); packet[13] = 0; packet[14] = 2; packet[15] = 1
        packet[16] = 192.toByte(); packet[17] = 0; packet[18] = 2; packet[19] = 2
        System.arraycopy(payload, 0, packet, 20, payload.size)
        var sum = 0L
        for (i in 0 until 20 step 2) sum += ((packet[i].toInt() and 255) shl 8) + (packet[i + 1].toInt() and 255)
        while ((sum ushr 16) != 0L) sum = (sum and 0xffff) + (sum ushr 16)
        val checksum = sum.inv().toInt() and 0xffff
        packet[10] = (checksum ushr 8).toByte()
        packet[11] = checksum.toByte()
        return packet
    }
}
