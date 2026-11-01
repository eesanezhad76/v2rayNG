package com.v2ray.ang.albion

import java.io.BufferedInputStream
import java.io.DataInputStream
import java.io.File
import java.io.FileInputStream

/** Offline PCAP validation using the same Photon transport parser as the live writer. */
object AlbionCaptureAnalyzer {
    data class Report(
        val ipPackets: Int,
        val albionUdpPackets: Int,
        val validPhotonPackets: Int,
        val inboundPackets: Int,
        val outboundPackets: Int,
        val photonCommandTypes: Map<Int, Int>,
        val malformedPhotonPackets: Int,
        val warnings: List<String>
    ) {
        fun asText(): String = buildString {
            appendLine("Albion / Photon capture report")
            appendLine("IP packets in file: $ipPackets")
            appendLine("UDP packets involving port 5056: $albionUdpPackets")
            appendLine("Valid Photon envelopes: $validPhotonPackets")
            appendLine("Inbound to local device: $inboundPackets")
            appendLine("Outbound from local device: $outboundPackets")
            appendLine("Photon command types (transport commands, not Albion events):")
            photonCommandTypes.toSortedMap().forEach { (type, count) -> appendLine("  command $type: $count") }
            appendLine("Malformed / unrecognized Photon envelopes: $malformedPhotonPackets")
            warnings.forEach { appendLine("Warning: $it") }
            appendLine("Protocol18 event/entity decoding is not yet implemented; no mob/resource/coordinate counts are claimed.")
        }
    }

    fun analyze(file: File): Report {
        var packets = 0
        var albion = 0
        var valid = 0
        var inbound = 0
        var outbound = 0
        var malformed = 0
        val commands = linkedMapOf<Int, Int>()
        DataInputStream(BufferedInputStream(FileInputStream(file))).use { input ->
            val magic = input.readInt()
            val little = when (magic) {
                0xd4c3b2a1.toInt(), 0x4d3cb2a1 -> true
                0xa1b2c3d4.toInt() -> false
                else -> throw IllegalArgumentException("Unsupported PCAP magic: %08x".format(magic))
            }
            // Four 32-bit words precede the network/link-type word:
            // version pair, thiszone, sigfigs, snaplen.
            repeat(4) { readInt(input, little) }
            val linkType = readInt(input, little)
            while (true) {
                try {
                    readInt(input, little)
                    readInt(input, little)
                    val captured = readInt(input, little)
                    readInt(input, little)
                    if (captured !in 1..2_000_000) break
                    val frame = ByteArray(captured)
                    input.readFully(frame)
                    packets++
                    val ip = when (linkType) {
                        101 -> frame
                        1 -> if (frame.size >= 14 && (u16(frame, 12) == 0x0800 || u16(frame, 12) == 0x86dd)) frame.copyOfRange(14, frame.size) else null
                        else -> null
                    } ?: continue
                    val decoded = AlbionPhotonPacketDecoder.decodeIpPacket(ip) ?: continue
                    albion++
                    if (decoded.photon) {
                        valid++
                        if (decoded.dstPort == AlbionPhotonPacketDecoder.ALBION_PORT) inbound++ else outbound++
                        decoded.commandTypes.forEach { type -> commands[type] = (commands[type] ?: 0) + 1 }
                    } else malformed++
                } catch (_: java.io.EOFException) {
                    break
                }
            }
        }
        val warnings = mutableListOf<String>()
        if (albion == 0) warnings += "No UDP datagrams involving port 5056 were found."
        if (malformed > 0) warnings += "Some datagrams on port 5056 did not pass Photon envelope validation."
        return Report(packets, albion, valid, inbound, outbound, commands, malformed, warnings)
    }

    private fun readInt(input: DataInputStream, little: Boolean): Int =
        if (little) Integer.reverseBytes(input.readInt()) else input.readInt()

    private fun u16(bytes: ByteArray, offset: Int): Int =
        ((bytes[offset].toInt() and 255) shl 8) or (bytes[offset + 1].toInt() and 255)
}
