package com.v2ray.ang.albion

import java.util.concurrent.atomic.AtomicInteger

/** Local-only synthetic IPv4 annotation. It is written to the capture, never routed to the game. */
object AlbionCaptureMarker {
    const val PREFIX = "ALBION_TAG_V1\n"
    private val nextId = AtomicInteger(1)

    fun payload(markerJson: String): ByteArray {
        val json = org.json.JSONObject(markerJson).toString()
        return (PREFIX + json).toByteArray(Charsets.UTF_8)
    }

    fun parsePayload(payload: ByteArray, offset: Int = 0, length: Int = payload.size - offset): org.json.JSONObject? {
        if (offset < 0 || length < PREFIX.toByteArray(Charsets.UTF_8).size || offset + length > payload.size) return null
        return try {
            val text = String(payload, offset, length, Charsets.UTF_8)
            if (!text.startsWith(PREFIX)) null else org.json.JSONObject(text.substring(PREFIX.length))
        } catch (_: Exception) { null }
    }

    fun build(markerJson: String): ByteArray {
        val body = payload(markerJson)
        require(body.size <= 65515) { "Marker too large" }
        val packet = ByteArray(20 + body.size)
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
        System.arraycopy(body, 0, packet, 20, body.size)
        var sum = 0L
        for (i in 0 until 20 step 2) sum += ((packet[i].toInt() and 255) shl 8) + (packet[i + 1].toInt() and 255)
        while ((sum ushr 16) != 0L) sum = (sum and 0xffff) + (sum ushr 16)
        val checksum = sum.inv().toInt() and 0xffff
        packet[10] = (checksum ushr 8).toByte()
        packet[11] = checksum.toByte()
        return packet
    }
}
