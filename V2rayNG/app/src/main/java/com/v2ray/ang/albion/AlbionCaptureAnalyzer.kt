package com.v2ray.ang.albion

import java.io.BufferedInputStream
import java.io.DataInputStream
import java.io.File
import java.io.FileInputStream
import org.json.JSONObject

/** Offline PCAP validation using the same Photon transport parser as the live writer. */
object AlbionCaptureAnalyzer {
    data class Report(
        val ipPackets: Int,
        val albionUdpPackets: Int,
        val validPhotonPackets: Int,
        val inboundPackets: Int,
        val outboundPackets: Int,
        val photonCommandTypes: Map<Int, Int>,
        val eventCodes: Map<Int, Int>,
        val operationRequests: Map<Int, Int>,
        val operationResponses: Map<Int, Int>,
        val entityKinds: Map<String, Int>,
        val typedEventKinds: Map<String, Int>,
        val malformedPhotonPackets: Int,
        val encryptedPhotonPackets: Int,
        val encryptedProtocol18Messages: Int,
        val malformedProtocol18Messages: Int,
        val pendingFragments: Int,
        val warnings: List<String>,
        val checkpoints: List<String>,
        val detailedObservations: List<String>
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
            appendLine("Protocol18 Albion event codes:")
            eventCodes.toSortedMap().forEach { (code, count) -> appendLine("  event $code: $count") }
            appendLine("Operation requests:")
            operationRequests.toSortedMap().forEach { (code, count) -> appendLine("  operation $code: $count") }
            appendLine("Operation responses:")
            operationResponses.toSortedMap().forEach { (code, count) -> appendLine("  operation $code: $count") }
            appendLine("Mapped entity observations:")
            entityKinds.toSortedMap().forEach { (kind, count) -> appendLine("  $kind: $count") }
            appendLine("Typed Photon event observations:")
            typedEventKinds.toSortedMap().forEach { (kind, count) -> appendLine("  $kind: $count") }
            appendLine("Malformed / unrecognized Photon envelopes: $malformedPhotonPackets")
            appendLine("Encrypted Photon packets: $encryptedPhotonPackets")
            appendLine("Encrypted Protocol18 messages: $encryptedProtocol18Messages")
            appendLine("Malformed Protocol18 messages: $malformedProtocol18Messages")
            appendLine("Pending fragmented messages: $pendingFragments")
            appendLine("Manual checkpoints: ${checkpoints.size}")
            checkpoints.forEach { appendLine("  $it") }
            appendLine("Detailed entity/player observations: ${detailedObservations.size}")
            detailedObservations.forEach { appendLine("  $it") }
            warnings.forEach { appendLine("Warning: $it") }
        }
    }

    fun analyze(file: File): Report {
        var packets = 0
        var albion = 0
        var valid = 0
        var inbound = 0
        var outbound = 0
        var malformed = 0
        var encryptedPhoton = 0
        val commands = linkedMapOf<Int, Int>()
        val events = linkedMapOf<Int, Int>()
        val requests = linkedMapOf<Int, Int>()
        val responses = linkedMapOf<Int, Int>()
        val entities = linkedMapOf<String, Int>()
        val typedEvents = linkedMapOf<String, Int>()
        val checkpoints = mutableListOf<String>()
        val detailed = mutableListOf<String>()
        val protocol18 = AlbionProtocol18Decoder()
        AlbionPhotonPacketDecoder.reset()
        AlbionEntityEventMapper.reset()
        DataInputStream(BufferedInputStream(FileInputStream(file))).use { input ->
            val magic = input.readInt()
            val little = when (magic) {
                0xd4c3b2a1.toInt(), 0x4d3cb2a1 -> true
                0xa1b2c3d4.toInt(), 0xa1b23c4d.toInt() -> false
                else -> throw IllegalArgumentException("Unsupported PCAP magic: %08x".format(magic))
            }
            // Four 32-bit words precede the network/link-type word:
            // version pair, thiszone, sigfigs, snaplen.
            repeat(4) { readInt(input, little) }
            val linkType = readInt(input, little)
            while (true) {
                try {
                    val packetSeconds = readInt(input, little)
                    val packetMicros = readInt(input, little)
                    val packetTimeMs = packetSeconds.toLong() * 1000L + packetMicros.toLong() / 1000L
                    val captured = readInt(input, little)
                    readInt(input, little)
                    if (captured !in 1..2_000_000) break
                    val frame = ByteArray(captured)
                    input.readFully(frame)
                    packets++
                    // Local overlay checkpoints are deliberately stored as raw IPv4 records.
                    // Recognize them before link-layer decoding so a PCAP link-type mismatch
                    // cannot silently discard our own annotation packets.
                    if (isCheckpointPacket(frame)) {
                        parseCheckpoint(frame)?.let { checkpoints += it }
                        continue
                    }
                    val ip = extractIpPacket(frame, linkType) ?: continue
                    if (isCheckpointPacket(ip)) {
                        parseCheckpoint(ip)?.let { checkpoints += it }
                        continue
                    }
                    val decoded = AlbionPhotonPacketDecoder.decodeIpPacket(ip) ?: continue
                    albion++
                    if (decoded.photon) {
                        valid++
                        if (decoded.dstPort == AlbionPhotonPacketDecoder.ALBION_PORT) inbound++ else outbound++
                        decoded.commandTypes.forEach { type -> commands[type] = (commands[type] ?: 0) + 1 }
                        decoded.photonPayload?.let { payload ->
                            protocol18.receiveDatagram(payload).forEach { message ->
                                message.realCode?.let { code ->
                                    when (message.kind) {
                                        "event" -> events[code] = (events[code] ?: 0) + 1
                                        "request" -> requests[code] = (requests[code] ?: 0) + 1
                                        "response" -> responses[code] = (responses[code] ?: 0) + 1
                                    }
                                }
                                if (message.kind == "event" && message.realCode == 3) {
                                    val raw = message.parameters[1] as? ByteArray
                                    val hex = raw?.take(128)?.joinToString("") { "%02x".format(it) } ?: "null"
                                    detailed += "t=" + packetTimeMs + " event3 id=" + message.parameters[0] + " raw=" + hex + " p4=" + message.parameters[4] + " p5=" + message.parameters[5]
                                }
                                if (message.kind == "request" && message.realCode in setOf(22, 23, 24, 54, 199, 554)) {
                                    val params = message.parameters.entries.sortedBy { it.key }.joinToString(" ") { (key, value) -> key.toString() + "=" + describeValue(value) }
                                    detailed += "t=" + packetTimeMs + " outbound_request op=" + message.realCode + " params={" + params + "}"
                                }
                                AlbionEntityEventMapper.map(message).forEach { observation ->
                                    entities[observation.kind] = (entities[observation.kind] ?: 0) + 1
                                    if (observation.kind in setOf("player_spawn", "player_move", "static_resource", "harvestable", "resource_state", "mob", "mob_state", "player_mounted", "move")) {
                                        detailed += buildString {
                                            append("t=").append(packetTimeMs).append(" kind=").append(observation.kind)
                                            observation.entityId?.let { append(" id=").append(it) }
                                            observation.name?.let { append(" name=").append(it) }
                                            observation.x?.let { append(" x=").append(it) }
                                            observation.y?.let { append(" y=").append(it) }
                                            observation.typeId?.let { append(" mobileType=").append(it) }
                                            observation.typeNumber?.let { append(" family=").append(it) }
                                            observation.tier?.let { append(" tier=").append(it) }
                                            observation.enchant?.let { append(" enchant=").append(it) }
                                            observation.size?.let { append(" amount=").append(it) }
                                            if (observation.removed) append(" removed=true")
                                            if (observation.kind == "player_move") {
                                                val raw = message.parameters[1] as? ByteArray
                                                if (raw != null) append(" raw_hex=").append(raw.take(96).joinToString("") { "%02x".format(it) })
                                                append(" coord_source=unvalidated_event3_payload")
                                            }
                                        }
                                    }
                                }
                                if (message.kind == "event" && message.realCode != null) {
                                    val typed = AlbionPhotonEventDecoder.decode(message.realCode, message.parameters)
                                    if (typed.resources.isNotEmpty()) typedEvents["resource"] = (typedEvents["resource"] ?: 0) + typed.resources.size
                                    if (typed.livingEntities.isNotEmpty()) typedEvents["living_entity"] = (typedEvents["living_entity"] ?: 0) + typed.livingEntities.size
                                    if (typed.stateUpdate != null) typedEvents["resource_state"] = (typedEvents["resource_state"] ?: 0) + 1
                                }
                            }
                        }
                    } else if (decoded.encrypted) encryptedPhoton++ else malformed++
                } catch (_: java.io.EOFException) {
                    break
                }
            }
        }
        val protocolStats = protocol18.stats()
        val warnings = mutableListOf<String>()
        if (albion == 0) warnings += "No UDP datagrams involving port 5056 were found."
        if (malformed > 0) warnings += "Some datagrams on port 5056 did not pass Photon envelope validation."
        return Report(
            packets, albion, valid, inbound, outbound, commands, events, requests, responses, entities, typedEvents, malformed,
            encryptedPhoton, protocolStats.encrypted, protocolStats.malformed, protocolStats.fragmentsPending, warnings, checkpoints, detailed
        )
    }


    private fun describeValue(value: Any?): String = when (value) {
        null -> "null"
        is ByteArray -> "bytes[" + value.size + "]=" + value.take(96).joinToString("") { "%02x".format(it) } + if (value.size > 96) "..." else ""
        is FloatArray -> value.joinToString(prefix = "[", postfix = "]")
        is DoubleArray -> value.joinToString(prefix = "[", postfix = "]")
        is IntArray -> value.joinToString(prefix = "[", postfix = "]")
        is LongArray -> value.joinToString(prefix = "[", postfix = "]")
        is ShortArray -> value.joinToString(prefix = "[", postfix = "]")
        is Map<*, *> -> value.entries.take(32).joinToString(prefix = "{", postfix = "}") { describeValue(it.key) + ":" + describeValue(it.value) }
        is Iterable<*> -> value.take(32).joinToString(prefix = "[", postfix = "]") { describeValue(it) }
        else -> value.toString()
    }

    private fun isCheckpointPacket(ip: ByteArray): Boolean =
        ip.size >= 20 && (ip[0].toInt() ushr 4) == 4 && (ip[9].toInt() and 255) == 253

    private fun parseCheckpoint(ip: ByteArray): String? {
        return try {
            val marker = AlbionCaptureMarker.parse(ip)
            marker.optString("activity", "Unknown") + ": " +
                marker.optString("event", "event") + " @ " + marker.optLong("timestamp_ms")
        } catch (_: Exception) { null }
    }

    private fun extractIpPacket(frame: ByteArray, linkType: Int): ByteArray? {
        when (linkType) {
            101, 12 -> return frame // DLT_RAW / LINKTYPE_RAW
            228 -> return frame.takeIf { it.isNotEmpty() && (it[0].toInt() ushr 4) == 4 }
            229 -> return frame.takeIf { it.isNotEmpty() && (it[0].toInt() ushr 4) == 6 }
            113 -> { // Linux cooked capture v1
                if (frame.size < 16) return null
                val protocol = u16(frame, 14)
                if (protocol == 0x0800 || protocol == 0x86dd) return frame.copyOfRange(16, frame.size)
                return null
            }
            276 -> { // Linux cooked capture v2
                if (frame.size < 20) return null
                val protocol = u16(frame, 0)
                if (protocol == 0x0800 || protocol == 0x86dd) return frame.copyOfRange(20, frame.size)
                return null
            }
            1 -> {
                if (frame.size < 14) return null
                var offset = 14
                var protocol = u16(frame, 12)
                var tags = 0
                while (protocol == 0x8100 || protocol == 0x88a8 || protocol == 0x9100) {
                    if (++tags > 4 || offset + 4 > frame.size) return null
                    protocol = u16(frame, offset + 2)
                    offset += 4
                }
                if (protocol == 0x0800 || protocol == 0x86dd) return frame.copyOfRange(offset, frame.size)
                return null
            }
            else -> return null
        }
    }

    private fun readInt(input: DataInputStream, little: Boolean): Int =
        if (little) Integer.reverseBytes(input.readInt()) else input.readInt()

    private fun u16(bytes: ByteArray, offset: Int): Int =
        ((bytes[offset].toInt() and 255) shl 8) or (bytes[offset + 1].toInt() and 255)
}
