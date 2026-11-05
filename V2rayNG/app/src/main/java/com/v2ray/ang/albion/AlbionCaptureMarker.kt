package com.v2ray.ang.albion

import org.json.JSONObject
import java.util.concurrent.atomic.AtomicInteger

/** Builds and validates a local-only synthetic IPv4 record for PCAP annotations. */
object AlbionCaptureMarker {
    private const val PREFIX = "ALBION_TAG_V1\n"
    private const val ESCAPED_PREFIX = "ALBION_TAG_V1\\n"
    private val nextId = AtomicInteger(1)

    fun payload(markerJson: String): ByteArray {
        val canonicalJson = JSONObject(markerJson).toString()
        return (PREFIX + canonicalJson).toByteArray(Charsets.UTF_8)
    }

    fun parsePayload(payload: ByteArray, offset: Int = 0, length: Int = payload.size - offset): JSONObject? {
        if (offset < 0 || length < 0 || offset + length > payload.size) return null
        return try {
            val text = String(payload, offset, length, Charsets.UTF_8)
            val json = when {
                text.startsWith(PREFIX) -> text.removePrefix(PREFIX)
                text.startsWith(ESCAPED_PREFIX) -> text.removePrefix(ESCAPED_PREFIX)
                else -> return null
            }
            JSONObject(json)
        } catch (_: Exception) { null }
    }

    fun build(markerJson: String): ByteArray {
        JSONObject(markerJson)
        val payload = (PREFIX + markerJson).toByteArray(Charsets.UTF_8)
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

    /** Accepts canonical newline framing and escaped framing emitted by older overlays. */
    fun parse(packet: ByteArray): JSONObject {
        require(packet.size >= 20) { "Marker packet is shorter than an IPv4 header" }
        require((packet[0].toInt() and 0xf0) == 0x40) { "Marker is not IPv4" }
        val headerLength = (packet[0].toInt() and 0x0f) * 4
        require(headerLength >= 20 && headerLength < packet.size) { "Invalid marker IPv4 header length" }
        require((packet[9].toInt() and 0xff) == 253) { "Not an Albion marker protocol record" }
        val payload = String(packet, headerLength, packet.size - headerLength, Charsets.UTF_8)
        val json = when {
            payload.startsWith(PREFIX) -> payload.removePrefix(PREFIX)
            payload.startsWith(ESCAPED_PREFIX) -> payload.removePrefix(ESCAPED_PREFIX)
            else -> throw IllegalArgumentException("Unknown Albion marker prefix")
        }
        return JSONObject(json)
    }
}
