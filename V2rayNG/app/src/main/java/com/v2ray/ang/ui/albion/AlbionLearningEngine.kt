package com.v2ray.ang.ui.albion

import java.io.BufferedInputStream
import java.io.DataInputStream
import java.io.File
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import kotlin.math.min

/**
 * Offline, passive feature discovery for raw-IP PCAP captures.
 *
 * It deliberately does not assign game meanings to unknown bytes. It groups UDP
 * payloads, compares byte positions within similarly-sized packets, and emits
 * reproducible candidates for later protocol-specific validation.
 */
internal object AlbionLearningEngine {
    data class Packet(
        val seconds: Long,
        val micros: Long,
        val src: String,
        val srcPort: Int,
        val dst: String,
        val dstPort: Int,
        val payload: ByteArray
    ) {
        val direction: String get() = if (srcPort < dstPort) "out?" else "in?"
    }

    private data class Signature(
        val port: Int,
        val lengthBucket: Int,
        val prefix: String
    )

    fun analyze(file: File): String {
        val packets = readPcap(file)
        if (packets.isEmpty()) return "No UDP payloads found in this PCAP."

        val portCounts = packets.flatMap { listOf(it.srcPort, it.dstPort) }.groupingBy { it }.eachCount()
            .entries.sortedByDescending { it.value }
        val candidatePort = portCounts.firstOrNull()?.key
        val selected = if (candidatePort == null) packets else packets.filter {
            it.srcPort == candidatePort || it.dstPort == candidatePort
        }
        val grouped = selected.groupBy { packet ->
            Signature(
                (if (packet.srcPort == candidatePort) packet.srcPort else packet.dstPort),
                packet.payload.size / 16,
                packet.payload.take(8).joinToString("") { "%02x".format(it) }
            )
        }.entries.sortedByDescending { it.value.size }

        val out = StringBuilder()
        out.appendLine("ALBION PASSIVE LEARNING REPORT")
        out.appendLine("PCAP: ${file.name}")
        out.appendLine("UDP payload packets: ${packets.size}")
        out.appendLine("Candidate main UDP port: ${candidatePort ?: "unknown"}")
        out.appendLine("Packets on candidate port: ${selected.size}")
        out.appendLine()
        out.appendLine("UDP PORT COUNTS")
        portCounts.take(12).forEach { out.appendLine("${it.key}: ${it.value}") }
        out.appendLine()
        out.appendLine("REPEATED PAYLOAD SIGNATURES (port + 16-byte length bucket + first 8 bytes)")
        grouped.take(25).forEach { (sig, values) ->
            out.appendLine("port=${sig.port}, lenBucket=${sig.lengthBucket}, prefix=${sig.prefix}, count=${values.size}")
        }
        out.appendLine()
        out.appendLine("BYTE-POSITION VARIABILITY")
        out.appendLine("Stable offsets may contain command/type/IDs; changing offsets may contain counters, IDs or coordinates.")
        val clusters = selected.groupBy { it.payload.size / 8 }.entries
            .filter { it.value.size >= 8 }
            .sortedByDescending { it.value.size }
            .take(12)
        clusters.forEach { (bucket, values) ->
            val minLen = values.minOf { it.payload.size }
            val stable = ArrayList<Int>()
            val variable = ArrayList<Int>()
            for (offset in 0 until min(minLen, 256)) {
                val distinct = values.map { it.payload[offset].toInt() and 0xff }.toSet().size
                if (distinct == 1) stable.add(offset)
                else if (distinct in 2..min(values.size, 16)) variable.add(offset)
            }
            out.appendLine("lengthBucket8=${bucket}, packets=${values.size}, minPayload=${minLen}")
            out.appendLine("  stableOffsets(first 48): ${stable.take(48).joinToString(",")}")
            out.appendLine("  variableOffsets(first 64): ${variable.take(64).joinToString(",")}")
            out.appendLine("  samplePayloadHex: ${hex(values.first().payload, 96)}")
        }
        out.appendLine()
        out.appendLine("TEMPORAL CHANGE CANDIDATES")
        val chronological = selected.sortedWith(compareBy<Packet> { it.seconds }.thenBy { it.micros })
        val changes = chronological.zipWithNext().mapNotNull { (a, b) ->
            if (a.payload.size != b.payload.size) null
            else {
                val offsets = a.payload.indices.filter { a.payload[it] != b.payload[it] }
                if (offsets.isEmpty()) null else offsets
            }
        }
        val changeCounts = changes.flatten().groupingBy { it }.eachCount()
            .entries.sortedByDescending { it.value }
        out.appendLine("Most frequently changing offsets across adjacent equal-length packets:")
        out.appendLine(changeCounts.take(64).joinToString(", ") { "${it.key}(${it.value})" })
        out.appendLine()
        out.appendLine("LIMITATIONS")
        out.appendLine("- This is unsupervised byte-pattern discovery, not a decoded Albion event log.")
        out.appendLine("- Direction labels and packet roles are provisional until tunnel direction is validated.")
        out.appendLine("- No resource, mob, enchantment or coordinate meaning is assigned automatically here.")
        out.appendLine("- Raw packet bytes remain in the PCAP for later protocol-specific decoding.")
        return out.toString()
    }

    private fun readPcap(file: File): List<Packet> {
        val result = ArrayList<Packet>()
        DataInputStream(BufferedInputStream(FileInputStream(file))).use { input ->
            val magic = input.readInt()
            val littleEndian = when (magic) {
                0xa1b2c3d4.toInt(), 0xa1b23c4d.toInt() -> false
                0xd4c3b2a1.toInt(), 0x4d3cb2a1 -> true
                else -> throw IllegalArgumentException("Unsupported PCAP magic: %08x".format(magic))
            }
            val major = readInt(input, littleEndian)
            readInt(input, littleEndian) // minor
            readInt(input, littleEndian); readInt(input, littleEndian)
            readInt(input, littleEndian) // snaplen
            val linkType = readInt(input, littleEndian)
            if (major != 2) throw IllegalArgumentException("Unsupported PCAP version: $major")
            while (true) {
                val sec = try { readInt(input, littleEndian) } catch (_: Exception) { break }
                val usec = readInt(input, littleEndian)
                val incl = readInt(input, littleEndian)
                readInt(input, littleEndian) // original length
                if (incl < 0 || incl > 2_000_000) break
                val frame = ByteArray(incl)
                input.readFully(frame)
                val ip = when (linkType) {
                    101 -> frame
                    228, 229 -> frame
                    1 -> ethernetPayload(frame)
                    else -> null
                } ?: continue
                parseUdp(ip, sec.toLong(), usec.toLong())?.let(result::add)
            }
        }
        return result
    }

    private fun ethernetPayload(frame: ByteArray): ByteArray? {
        if (frame.size < 14) return null
        var offset = 14
        var etherType = u16be(frame, 12)
        while (etherType == 0x8100 || etherType == 0x88a8) {
            if (frame.size < offset + 4) return null
            etherType = u16be(frame, offset + 2)
            offset += 4
        }
        return if (etherType == 0x0800 || etherType == 0x86dd) frame.copyOfRange(offset, frame.size) else null
    }

    private fun parseUdp(ip: ByteArray, sec: Long, usec: Long): Packet? {
        if (ip.isEmpty()) return null
        return when ((ip[0].toInt() ushr 4) and 15) {
            4 -> {
                if (ip.size < 20 || (ip[9].toInt() and 255) != 17) return null
                val ihl = (ip[0].toInt() and 15) * 4
                if (ihl < 20 || ip.size < ihl + 8) return null
                val total = u16be(ip, 2).coerceAtMost(ip.size)
                val udpLen = u16be(ip, ihl + 4)
                val end = min(total, ihl + udpLen)
                if (end < ihl + 8) return null
                Packet(sec, usec, ipv4(ip, 12), u16be(ip, ihl), ipv4(ip, 16), u16be(ip, ihl + 2),
                    ip.copyOfRange(ihl + 8, end))
            }
            6 -> {
                if (ip.size < 48 || (ip[6].toInt() and 255) != 17) return null
                val udpLen = u16be(ip, 44)
                val end = min(ip.size, 40 + udpLen)
                if (end < 48) return null
                Packet(sec, usec, ipv6(ip, 8), u16be(ip, 40), ipv6(ip, 24), u16be(ip, 42),
                    ip.copyOfRange(48, end))
            }
            else -> null
        }
    }

    private fun readInt(input: DataInputStream, little: Boolean): Int {
        val value = input.readInt()
        return if (little) Integer.reverseBytes(value) else value
    }
    private fun u16be(b: ByteArray, i: Int): Int = ((b[i].toInt() and 255) shl 8) or (b[i + 1].toInt() and 255)
    private fun ipv4(b: ByteArray, i: Int): String = (0..3).joinToString(".") { (b[i + it].toInt() and 255).toString() }
    private fun ipv6(b: ByteArray, i: Int): String = (0..7).joinToString(":") {
        "%x".format(((b[i + it * 2].toInt() and 255) shl 8) or (b[i + it * 2 + 1].toInt() and 255))
    }
    private fun hex(bytes: ByteArray, limit: Int): String =
        bytes.take(limit).joinToString(" ") { "%02x".format(it) }
}
