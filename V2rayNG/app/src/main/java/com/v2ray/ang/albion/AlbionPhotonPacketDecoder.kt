package com.v2ray.ang.albion

/**
 * Bounds-checked passive parser for raw IPv4/IPv6 UDP packets and the Photon
 * command envelope. It intentionally does not decrypt Photon payloads.
 */
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
        val photonPayload: ByteArray? = null,
        val encrypted: Boolean = false
    )

    fun decodeIpPacket(packet: ByteArray): Result? {
        if (packet.isEmpty()) return null
        return when ((packet[0].toInt() ushr 4) and 15) {
            4 -> decodeIpv4(packet)
            6 -> decodeIpv6(packet)
            else -> null
        }
    }

    private fun decodeIpv4(packet: ByteArray): Result? {
        if (packet.size < 20) return null
        val ihl = (packet[0].toInt() and 15) * 4
        if (ihl < 20 || ihl > packet.size || u8(packet, 9) != IP_UDP) return null
        val totalLength = u16be(packet, 2)
        if (totalLength < ihl + 8 || totalLength > packet.size) return null
        // IP fragments must be reassembled before the UDP/Photon layer.
        val fragment = u16be(packet, 6)
        if ((fragment and 0x3fff) != 0) return null
        return decodeUdp(packet, ihl, totalLength, packet.size)
    }

    private fun decodeIpv6(packet: ByteArray): Result? {
        if (packet.size < 40) return null
        val payloadLength = u16be(packet, 4)
        val end = 40 + payloadLength
        if (end > packet.size) return null
        var next = u8(packet, 6)
        var offset = 40
        var extensions = 0
        while (next != IP_UDP) {
            if (++extensions > 8) return null
            when (next) {
                0, 43, 60 -> { // Hop-by-hop, routing, destination options
                    if (offset + 2 > end) return null
                    val following = u8(packet, offset)
                    val length = (u8(packet, offset + 1) + 1) * 8
                    if (length < 8 || offset + length > end) return null
                    next = following
                    offset += length
                }
                44 -> { // Fragment header: no IP-fragment reassembly here
                    if (offset + 8 > end) return null
                    val fragmentBits = u16be(packet, offset + 2)
                    if ((fragmentBits and 0xfff9) != 0) return null
                    next = u8(packet, offset)
                    offset += 8
                }
                51 -> { // Authentication header
                    if (offset + 2 > end) return null
                    val following = u8(packet, offset)
                    val length = (u8(packet, offset + 1) + 2) * 4
                    if (length < 8 || offset + length > end) return null
                    next = following
                    offset += length
                }
                else -> return null
            }
        }
        return decodeUdp(packet, offset, end, packet.size)
    }

    private fun decodeUdp(packet: ByteArray, offset: Int, ipEnd: Int, packetEnd: Int): Result? {
        if (offset < 0 || offset + 8 > ipEnd || ipEnd > packetEnd) return null
        val src = u16be(packet, offset)
        val dst = u16be(packet, offset + 2)
        val udpLength = u16be(packet, offset + 4)
        if (src != ALBION_PORT && dst != ALBION_PORT) return null
        if (udpLength < 8 || offset + udpLength > ipEnd) {
            return Result(src, dst, false, error = "invalid UDP length")
        }
        val start = offset + 8
        val end = offset + udpLength
        return decodePhoton(packet, start, end, src, dst)
    }

    private fun decodePhoton(data: ByteArray, start: Int, end: Int, src: Int, dst: Int): Result {
        val size = end - start
        if (size < PHOTON_HEADER_LENGTH) return Result(src, dst, false, error = "short Photon datagram")
        val peer = u16be(data, start)
        val flags = u8(data, start + 2)
        val count = u8(data, start + 3)
        if (flags == ENCRYPTED_PACKET_FLAG) {
            return Result(src, dst, false, peer, flags, error = "encrypted Photon packet")
        }
        if (count !in 0..MAX_COMMANDS) {
            return Result(src, dst, false, peer, flags, error = "invalid Photon command count")
        }
        var pos = start + PHOTON_HEADER_LENGTH
        val types = ArrayList<Int>(count)
        repeat(count) {
            if (pos + COMMAND_HEADER_LENGTH > end) {
                return Result(src, dst, false, peer, flags, error = "truncated Photon command header")
            }
            val type = u8(data, pos)
            val commandLength = u32be(data, pos + 4)
            if (commandLength < COMMAND_HEADER_LENGTH || commandLength > end - pos) {
                return Result(src, dst, false, peer, flags, error = "invalid Photon command length")
            }
            types.add(type)
            pos += commandLength
        }
        if (pos != end) {
            return Result(src, dst, false, peer, flags, types.size, types, "trailing bytes after Photon commands")
        }
        return Result(src, dst, true, peer, flags, count, types, photonPayload = data.copyOfRange(start, end))
    }

    private fun u8(b: ByteArray, o: Int) = b[o].toInt() and 255
    private fun u16be(b: ByteArray, o: Int) = (u8(b, o) shl 8) or u8(b, o + 1)
    private fun u32be(b: ByteArray, o: Int): Int =
        (((u8(b, o).toLong() shl 24) or (u8(b, o + 1).toLong() shl 16) or
            (u8(b, o + 2).toLong() shl 8) or u8(b, o + 3).toLong())
            .coerceAtMost(Int.MAX_VALUE.toLong())).toInt()

    const val ALBION_PORT = 5056
    private const val IP_UDP = 17
    private const val ENCRYPTED_PACKET_FLAG = 1
    private const val PHOTON_HEADER_LENGTH = 12
    private const val COMMAND_HEADER_LENGTH = 12
    private const val MAX_COMMANDS = 64
}
