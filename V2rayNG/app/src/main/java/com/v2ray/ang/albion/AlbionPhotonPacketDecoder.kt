package com.v2ray.ang.albion

/** Strict passive parser for the UDP/IP + Photon transport envelope used by Albion. */
object AlbionPhotonPacketDecoder {
    data class Result(
        val srcPort: Int,
        val dstPort: Int,
        val photon: Boolean,
        val peerId: Int? = null,
        val flags: Int? = null,
        val commandCount: Int = 0,
        val commandTypes: List<Int> = emptyList(),
        val error: String? = null,
        val photonPayload: ByteArray? = null
    )

    fun decodeIpPacket(packet: ByteArray): Result? {
        if (packet.isEmpty()) return null
        val version = (packet[0].toInt() ushr 4) and 15
        val transportOffset: Int
        when (version) {
            4 -> {
                if (packet.size < 20) return null
                val ihl = (packet[0].toInt() and 15) * 4
                if (ihl < 20 || packet.size < ihl || (packet[9].toInt() and 255) != 17) return null
                transportOffset = ihl
            }
            6 -> {
                if (packet.size < 40 || (packet[6].toInt() and 255) != 17) return null
                transportOffset = 40
            }
            else -> return null
        }
        if (packet.size < transportOffset + 8) return null
        val src = u16(packet, transportOffset)
        val dst = u16(packet, transportOffset + 2)
        val udpLength = u16(packet, transportOffset + 4)
        if (udpLength < 8 || transportOffset + udpLength > packet.size) {
            return Result(src, dst, false, error = "invalid UDP length")
        }
        if (src != ALBION_PORT && dst != ALBION_PORT) return null
        val start = transportOffset + 8
        val end = transportOffset + udpLength
        return decodePhoton(packet, start, end, src, dst)
    }

    private fun decodePhoton(data: ByteArray, start: Int, end: Int, src: Int, dst: Int): Result {
        val size = end - start
        if (size < 12) return Result(src, dst, false, error = "short Photon datagram")
        val peer = u16(data, start)
        val flags = data[start + 2].toInt() and 255
        val count = data[start + 3].toInt() and 255
        if (count == 0 || count > 64) return Result(src, dst, false, error = "invalid Photon command count")
        var pos = start + 12
        val types = ArrayList<Int>(count)
        repeat(count) {
            if (pos + 12 > end) return Result(src, dst, false, error = "truncated Photon command header")
            val type = data[pos].toInt() and 255
            val commandLength = u32(data, pos + 4)
            if (commandLength < 12 || commandLength > end - pos) {
                return Result(src, dst, false, error = "invalid Photon command length")
            }
            types.add(type)
            pos += commandLength
        }
        return Result(src, dst, true, peer, flags, count, types, photonPayload = data.copyOfRange(start, end))
    }

    private fun u16(b: ByteArray, o: Int) = ((b[o].toInt() and 255) shl 8) or (b[o + 1].toInt() and 255)
    private fun u32(b: ByteArray, o: Int): Int =
        ((b[o].toLong() and 255L) shl 24 or
         ((b[o + 1].toLong() and 255L) shl 16) or
         ((b[o + 2].toLong() and 255L) shl 8) or
         (b[o + 3].toLong() and 255L)).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()

    const val ALBION_PORT = 5056
}
