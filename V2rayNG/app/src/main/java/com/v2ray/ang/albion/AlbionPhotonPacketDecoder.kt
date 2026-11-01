package com.v2ray.ang.albion

/**
 * Bounds-checked passive parser for raw IPv4/IPv6 UDP packets and the Photon
 * command envelope. It intentionally does not decrypt Photon payloads.
 */
object AlbionPhotonPacketDecoder {
    private data class FragmentKey(val source: String, val destination: String, val id: Int, val protocol: Int)
    private data class FragmentAssembly(
        var header: ByteArray,
        val payload: ByteArray = ByteArray(65535),
        val received: BooleanArray = BooleanArray(65535),
        var totalPayload: Int = -1,
        var touchedAt: Long = System.currentTimeMillis()
    )
    private val ipv4Fragments = Ipv4FragmentCache()

    private class Ipv4FragmentCache {
        private val sets = LinkedHashMap<FragmentKey, FragmentAssembly>()
        fun clear() = synchronized(this) { sets.clear() }
        fun accept(packet: ByteArray, ihl: Int, totalLength: Int, flagsOffset: Int): ByteArray? = synchronized(this) {
            val now = System.currentTimeMillis()
            val iterator = sets.entries.iterator()
            while (iterator.hasNext()) if (now - iterator.next().value.touchedAt > 15000) iterator.remove()
            val offset = (flagsOffset and 0x1fff) * 8
            val more = (flagsOffset and 0x2000) != 0
            val payloadLength = totalLength - ihl
            if (payloadLength <= 0 || offset < 0 || offset + payloadLength > 65515) return@synchronized null
            val key = FragmentKey(
                packet.copyOfRange(12, 16).joinToString("") { "%02x".format(it) },
                packet.copyOfRange(16, 20).joinToString("") { "%02x".format(it) },
                (((packet[4].toInt() and 255) shl 8) or (packet[5].toInt() and 255)), packet[9].toInt() and 255
            )
            if (sets.size >= 64 && !sets.containsKey(key)) sets.remove(sets.keys.first())
            var set = sets[key]
            if (set == null) {
                set = FragmentAssembly(packet.copyOfRange(0, ihl))
                sets[key] = set
            }
            set.touchedAt = now
            if (offset == 0) set.header = packet.copyOfRange(0, ihl)
            for (i in 0 until payloadLength) {
                val at = offset + i
                if (!set.received[at]) {
                    set.received[at] = true
                    set.payload[at] = packet[ihl + i]
                }
            }
            if (!more) set.totalPayload = offset + payloadLength
            val end = set.totalPayload
            if (end <= 0 || set.header.size + end > 65535 || (0 until end).any { !set.received[it] }) return@synchronized null
            val output = ByteArray(set.header.size + end)
            System.arraycopy(set.header, 0, output, 0, set.header.size)
            System.arraycopy(set.payload, 0, output, set.header.size, end)
            val total = output.size
            output[2] = (total ushr 8).toByte()
            output[3] = total.toByte()
            output[6] = 0
            output[7] = 0
            output[10] = 0
            output[11] = 0
            var sum = 0L
            for (i in 0 until set.header.size step 2) {
                sum += ((output[i].toInt() and 255) shl 8) + (output[i + 1].toInt() and 255)
            }
            while ((sum ushr 16) != 0L) sum = (sum and 0xffff) + (sum ushr 16)
            val checksum = sum.inv().toInt() and 0xffff
            output[10] = (checksum ushr 8).toByte()
            output[11] = checksum.toByte()
            sets.remove(key)
            output
        }
    }

    private data class Ipv6FragmentKey(val source: String, val destination: String, val id: Long, val nextHeader: Int)
    private data class Ipv6FragmentAssembly(
        var prefix: ByteArray,
        val payload: ByteArray = ByteArray(65535),
        val received: BooleanArray = BooleanArray(65535),
        var totalPayload: Int = -1,
        var touchedAt: Long = System.currentTimeMillis()
    )
    private class Ipv6FragmentCache {
        private val sets = LinkedHashMap<Ipv6FragmentKey, Ipv6FragmentAssembly>()
        fun clear() = synchronized(this) { sets.clear() }
        fun accept(packet: ByteArray, fragmentOffset: Int, packetEnd: Int, previousNextHeaderOffset: Int): ByteArray? = synchronized(this) {
            val now = System.currentTimeMillis()
            val iterator = sets.entries.iterator()
            while (iterator.hasNext()) if (now - iterator.next().value.touchedAt > 15000) iterator.remove()
            val bits = AlbionPhotonPacketDecoder.u16be(packet, fragmentOffset + 2)
            val offset = bits and 0xfff8
            val more = (bits and 1) != 0
            val following = AlbionPhotonPacketDecoder.u8(packet, fragmentOffset)
            val dataStart = fragmentOffset + 8
            val dataLength = packetEnd - dataStart
            if (dataLength <= 0 || offset + dataLength > 65535) return@synchronized null
            val key = Ipv6FragmentKey(
                packet.copyOfRange(8, 24).joinToString("") { "%02x".format(it) },
                packet.copyOfRange(24, 40).joinToString("") { "%02x".format(it) },
                ((AlbionPhotonPacketDecoder.u8(packet, fragmentOffset + 4).toLong() shl 24) or (AlbionPhotonPacketDecoder.u8(packet, fragmentOffset + 5).toLong() shl 16) or
                    (AlbionPhotonPacketDecoder.u8(packet, fragmentOffset + 6).toLong() shl 8) or AlbionPhotonPacketDecoder.u8(packet, fragmentOffset + 7).toLong()),
                following
            )
            if (sets.size >= 64 && !sets.containsKey(key)) sets.remove(sets.keys.first())
            var set = sets[key]
            if (set == null) {
                set = Ipv6FragmentAssembly(packet.copyOfRange(0, fragmentOffset))
                sets[key] = set
            }
            set.touchedAt = now
            if (offset == 0) {
                set.prefix = packet.copyOfRange(0, fragmentOffset)
                set.prefix[previousNextHeaderOffset] = following.toByte()
            }
            for (i in 0 until dataLength) {
                val at = offset + i
                if (!set.received[at]) {
                    set.received[at] = true
                    set.payload[at] = packet[dataStart + i]
                }
            }
            if (!more) set.totalPayload = offset + dataLength
            val total = set.totalPayload
            if (total <= 0 || set.prefix.size - 40 + total > 65535 || (0 until total).any { !set.received[it] }) return@synchronized null
            val output = ByteArray(set.prefix.size + total)
            System.arraycopy(set.prefix, 0, output, 0, set.prefix.size)
            System.arraycopy(set.payload, 0, output, set.prefix.size, total)
            val ipv6PayloadLength = output.size - 40
            output[4] = (ipv6PayloadLength ushr 8).toByte()
            output[5] = ipv6PayloadLength.toByte()
            sets.remove(key)
            output
        }
    }

    private val ipv6Fragments = Ipv6FragmentCache()

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

    fun reset() {
        ipv4Fragments.clear()
        ipv6Fragments.clear()
    }

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
        if ((fragment and 0x3fff) != 0) {
            val assembled = ipv4Fragments.accept(packet, ihl, totalLength, fragment) ?: return null
            val assembledHeader = (assembled[0].toInt() and 15) * 4
            val assembledLength = u16be(assembled, 2)
            return decodeUdp(assembled, assembledHeader, assembledLength, assembled.size)
        }
        return decodeUdp(packet, ihl, totalLength, packet.size)
    }

    private fun decodeIpv6(packet: ByteArray): Result? {
        if (packet.size < 40) return null
        val payloadLength = u16be(packet, 4)
        val end = 40 + payloadLength
        if (end > packet.size) return null
        var next = u8(packet, 6)
        var nextHeaderOffset = 6
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
                    nextHeaderOffset = offset
                    next = following
                    offset += length
                }
                44 -> { // IPv6 fragment header
                    if (offset + 8 > end) return null
                    val following = u8(packet, offset)
                    val fragmentBits = u16be(packet, offset + 2)
                    if ((fragmentBits and 0xfff8) != 0 || (fragmentBits and 1) != 0) {
                        val assembled = ipv6Fragments.accept(packet, offset, end, nextHeaderOffset) ?: return null
                        return decodeIpv6(assembled)
                    }
                    nextHeaderOffset = offset
                    next = following
                    offset += 8
                }
                51 -> { // Authentication header
                    if (offset + 2 > end) return null
                    val following = u8(packet, offset)
                    val length = (u8(packet, offset + 1) + 2) * 4
                    if (length < 8 || offset + length > end) return null
                    nextHeaderOffset = offset
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
            return Result(src, dst, false, peer, flags, error = "encrypted Photon packet", encrypted = true)
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
