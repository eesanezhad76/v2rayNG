package com.v2ray.ang.albion

import java.io.BufferedInputStream
import java.io.DataInputStream
import java.io.File
import java.io.FileInputStream

/**
 * Offline analyzer for PCAP files exported by the passive TUN tap.
 * It deliberately reports transport and Photon framing separately: ENet command
 * types are not Photon event codes.
 */
object AlbionCaptureAnalyzer {
    data class Report(
        val packets: Int,
        val udpPackets: Int,
        val gamePortPackets: Int,
        val gameInbound: Int,
        val gameOutbound: Int,
        val enetCommands: Map<Int, Int>,
        val photonEvents: Map<Int, Int>,
        val warnings: List<String>
    ) {
        fun asText(): String = buildString {
            appendLine("Albion capture analysis")
            appendLine("IP packets: $packets")
            appendLine("UDP packets: $udpPackets")
            appendLine("UDP involving port 5056: $gamePortPackets")
            appendLine("Inbound to device: $gameInbound")
            appendLine("Outbound from device: $gameOutbound")
            appendLine("ENet command types (not game events):")
            enetCommands.toSortedMap().forEach { (k, v) -> appendLine("  $k: $v") }
            appendLine("Photon event candidates (validated framing only):")
            if (photonEvents.isEmpty()) appendLine("  none confidently decoded")
            photonEvents.toSortedMap().forEach { (k, v) -> appendLine("  Event $k: $v") }
            warnings.forEach { appendLine("Warning: $it") }
        }
    }

    fun analyze(file: File): Report {
        var packets = 0
        var udp = 0
        var game = 0
        var inbound = 0
        var outbound = 0
        val enet = linkedMapOf<Int, Int>()
        val events = linkedMapOf<Int, Int>()
        val warnings = mutableListOf<String>()
        DataInputStream(BufferedInputStream(FileInputStream(file))).use { input ->
            val magic = input.readInt()
            val little = when (magic) {
                0xd4c3b2a1.toInt(), 0x4d3cb2a1 -> true
                0xa1b2c3d4.toInt() -> false
                else -> throw IllegalArgumentException("Unsupported PCAP magic: %08x".format(magic))
            }
            readShort(input, little); readShort(input, little)
            readInt(input, little); readInt(input, little); readInt(input, little)
            val link = readInt(input, little)
            if (link != 101 && link != 1) warnings += "Link type $link is not raw IP/Ethernet; some packets may be skipped."
            while (true) {
                val sec = try { readInt(input, little) } catch (_: Exception) { break }
                readInt(input, little)
                val incl = readInt(input, little)
                readInt(input, little)
                if (incl <= 0 || incl > 2_000_000) break
                val b = ByteArray(incl)
                input.readFully(b)
                packets++
                val ip = ipPayload(b, link) ?: continue
                val p = ip.first
                val off = ip.second
                if (p != 17 || off + 8 > b.size) continue
                udp++
                val src = u16(b, off); val dst = u16(b, off + 2)
                val len = u16(b, off + 4)
                if (src != 5056 && dst != 5056) continue
                game++
                val payloadStart = off + 8
                val payloadEnd = minOf(b.size, off + len)
                if (payloadEnd <= payloadStart) continue
                if (dst == 5056) inbound++ else outbound++
                val payload = b.copyOfRange(payloadStart, payloadEnd)
            }
        }
        if (game == 0) warnings += "No UDP packets on port 5056 found."
        warnings += "Photon event decoding is not enabled in this build yet; transport statistics are automatic, but entity IDs and coordinates are not inferred."
        return Report(packets, udp, game, inbound, outbound, enet, events, warnings)
    }

    private fun parseEnet(data: ByteArray): List<Pair<Int, ByteArray>> {
        if (data.size < 12) return emptyList()
        // ENet protocol header: peer ID (2), sent time (2), flags/command count (1), channel (1), sent time (2), checksum (4).
        val count = data[4].toInt() and 0x0f
        if (count == 0 || count > 15) return emptyList()
        var pos = 12
        val result = mutableListOf<Pair<Int, ByteArray>>()
        repeat(count) {
            if (pos + 4 > data.size) return@repeat
            val start = pos
            val command = data[pos].toInt() and 0xff
            val channel = data[pos + 1].toInt() and 0xff
            val length = u16(data, pos + 2)
            if (length < 4 || pos + length > data.size) return@repeat
            val bodyStart = pos + 4
            val body = data.copyOfRange(bodyStart, pos + length)
            result += command to body
            pos += length
        }
        return result
    }

    private fun photonEventCode(commandBody: ByteArray): Int? {
        // Photon event payloads vary by command subtype/version. Do not guess an event
        // from arbitrary bytes: accept only the known Protocol18 event envelope marker.
        if (commandBody.size < 5) return null
        val type = commandBody[0].toInt() and 0xff
        if (type != 0x04) return null
        // Photon event code is not safely extractable without fully decoding the parameter table.
        return null
    }

    private fun ipPayload(bytes: ByteArray, link: Int): Pair<Int, Int>? {
        val start = when (link) {
            101 -> 0
            1 -> {
                if (bytes.size < 14) return null
                val ether = u16(bytes, 12)
                if (ether == 0x0800) 14 else if (ether == 0x86dd) 14 else return null
            }
            else -> 0
        }
        if (start >= bytes.size) return null
        val version = (bytes[start].toInt() ushr 4) and 15
        return when (version) {
            4 -> {
                if (start + 20 > bytes.size) null
                else (bytes[start + 9].toInt() and 255) to (start + (bytes[start].toInt() and 15) * 4)
            }
            6 -> if (start + 40 <= bytes.size) (bytes[start + 6].toInt() and 255) to (start + 40) else null
            else -> null
        }
    }

    private fun readInt(i: DataInputStream, le: Boolean) = if (le) Integer.reverseBytes(i.readInt()) else i.readInt()
    private fun readShort(i: DataInputStream, le: Boolean) = if (le) java.lang.Short.reverseBytes(i.readShort()) else i.readShort()
    private fun u16(b: ByteArray, o: Int) = ((b[o].toInt() and 255) shl 8) or (b[o + 1].toInt() and 255)
}
