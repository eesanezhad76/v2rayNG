package com.v2ray.ang.albion

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class AlbionPhotonPacketDecoderTest {
    @Test fun decodesValidIpv4PhotonEnvelope() {
        val packet = ipv4UdpPhotonPacket(commandType = 7, commandLength = 12)
        val result = AlbionPhotonPacketDecoder.decodeIpPacket(packet)
        assertNotNull(result)
        assertTrue(result!!.photon)
        assertEquals(5056, result.srcPort)
        assertEquals(1, result.commandCount)
        assertEquals(listOf(7), result.commandTypes)
    }

    @Test fun rejectsInvalidPhotonCommandLength() {
        val packet = ipv4UdpPhotonPacket(commandType = 7, commandLength = 99)
        val result = AlbionPhotonPacketDecoder.decodeIpPacket(packet)
        assertNotNull(result)
        assertFalse(result!!.photon)
        assertFalse(result.encrypted)
        assertEquals("invalid Photon command length", result.error)
    }

    @Test fun classifiesEncryptedPhotonPacketsSeparatelyFromMalformedPackets() {
        val packet = ipv4UdpPhotonPacket(commandType = 7, commandLength = 12)
        packet[28 + 2] = 1.toByte() // encrypted Photon packet flag
        val result = AlbionPhotonPacketDecoder.decodeIpPacket(packet)
        assertNotNull(result)
        assertFalse(result!!.photon)
        assertTrue(result.encrypted)
        assertEquals("encrypted Photon packet", result.error)
    }

    @Test fun ignoresOtherUdpPorts() {
        val packet = ipv4UdpPhotonPacket(commandType = 7, commandLength = 12)
        packet[20] = 0x13
        packet[21] = 0x89 // source port 5001, not 5056
        val result = AlbionPhotonPacketDecoder.decodeIpPacket(packet)
        assertNull(result)
    }

    @Test fun decodesProtocol18GenericEventAndAlbionCode252() {
        val photon = ByteArray(12 + 12 + 2 + 1 + 1 + 1 + 1 + 2)
        put16(photon, 0, 1)
        photon[2] = 0
        photon[3] = 1
        val command = 12
        photon[command] = 6
        put32(photon, command + 4, photon.size - command)
        val body = command + 12
        photon[body] = 0 // signal byte
        photon[body + 1] = 4 // EventData message
        photon[body + 2] = 1 // generic dispatch
        photon[body + 3] = 1 // one parameter
        photon[body + 4] = 252.toByte()
        photon[body + 5] = 4 // Protocol18 short
        photon[body + 6] = 39
        photon[body + 7] = 0
        val decoder = AlbionProtocol18Decoder()
        val messages = decoder.receiveDatagram(photon)
        assertEquals(1, messages.size)
        assertEquals("event", messages[0].kind)
        assertEquals(1, messages[0].dispatchCode)
        assertEquals(39, messages[0].realCode)
    }

    @Test fun genericDispatchIsNormalizedToAlbionEventCodeWhenParameter252IsAbsent() {
        val photon = ByteArray(12 + 12 + 4)
        photon[3] = 1
        val command = 12
        photon[command] = 6
        put32(photon, command + 4, photon.size - command)
        val body = command + 12
        photon[body + 1] = 4 // EventData
        photon[body + 2] = 39 // dispatch value alone is not Protocol18 event code
        photon[body + 3] = 0 // empty parameter table
        val messages = AlbionProtocol18Decoder().receiveDatagram(photon)
        assertEquals(1, messages.size)
        assertEquals(39, messages[0].realCode)
    }

    @Test fun moveDispatchInjectsOnlyItsDocumentedSpecialEventCode() {
        val photon = ByteArray(12 + 12 + 4)
        photon[3] = 1
        val command = 12
        photon[command] = 6
        put32(photon, command + 4, photon.size - command)
        val body = command + 12
        photon[body + 1] = 4 // EventData
        photon[body + 2] = 3 // Move dispatch
        photon[body + 3] = 0 // params omitted by server
        val messages = AlbionProtocol18Decoder().receiveDatagram(photon)
        assertEquals(1, messages.size)
        assertEquals(3, messages[0].realCode)
    }

    @Test fun operationRequestUsesWireOperationAsFallbackWhenParameter253IsAbsent() {
        val photon = ByteArray(12 + 12 + 2 + 1 + 1)
        photon[3] = 1
        val command = 12
        photon[command] = 6
        put32(photon, command + 4, photon.size - command)
        val body = command + 12
        photon[body + 1] = 2 // OperationRequest
        photon[body + 2] = 1 // wire operation byte
        photon[body + 3] = 0 // empty parameter table
        val messages = AlbionProtocol18Decoder().receiveDatagram(photon)
        assertEquals(1, messages.size)
        assertEquals("request", messages[0].kind)
        assertEquals(1, messages[0].realCode)
        assertEquals(1, messages[0].operationCode)
    }

    @Test fun mapsNewMobPositionAndEnchantToCorrectFields() {
        val message = AlbionProtocol18Decoder.Message(
            kind = "event",
            dispatchCode = 1,
            realCode = 123,
            parameters = mapOf(
                0 to 987654321L,
                1 to 530,
                7 to floatArrayOf(12.5f, 44.25f),
                31 to "MOB_TEST",
                34 to 3
            )
        )
        val observation = AlbionEntityEventMapper.map(message).single()
        assertEquals("mob", observation.kind)
        assertEquals(987654321L, observation.entityId)
        assertEquals(530, observation.typeId)
        assertEquals(12.5f, observation.x)
        assertEquals(44.25f, observation.y)
        assertEquals(3, observation.enchant)
        assertEquals("MOB_TEST", observation.name)
    }

    @Test fun mapsHarvestablePositionAndMetadataToCorrectFields() {
        val message = AlbionProtocol18Decoder.Message(
            kind = "event",
            dispatchCode = 1,
            realCode = 40,
            parameters = mapOf(
                0 to 123L, 5 to 16, 6 to 530, 7 to 5,
                8 to floatArrayOf(8f, 9f), 10 to 3, 11 to 2
            )
        )
        val observation = AlbionEntityEventMapper.map(message).single()
        assertEquals(16, observation.typeNumber)
        assertEquals(530, observation.typeId)
        assertEquals(5, observation.tier)
        assertEquals(2, observation.enchant)
        assertEquals(8f, observation.x)
        assertEquals(9f, observation.y)
        assertEquals(3, observation.size)
    }


    @Test fun mapsPlayerSpawnAndLeaveLifecycle() {
        val spawn = AlbionProtocol18Decoder.Message("event", 1, 29, mapOf(0 to 77L, 1 to "Scout"))
        val leave = AlbionProtocol18Decoder.Message("event", 1, 1, mapOf(0 to 77L))
        assertEquals("player_spawn", AlbionEntityEventMapper.map(spawn).single().kind)
        assertTrue(AlbionEntityEventMapper.map(leave).single().removed)
    }

    @Test fun mapsDungeonExitCoordinates() {
        val message = AlbionProtocol18Decoder.Message("event", 1, 325,
            mapOf(1 to floatArrayOf(10f, 20f), 3 to "Exit", 9 to 2))
        val observation = AlbionEntityEventMapper.map(message).single()
        assertEquals("dungeon_exit", observation.kind)
        assertEquals(10f, observation.x)
        assertEquals(20f, observation.y)
        assertEquals("Exit", observation.name)
    }

    private fun ipv4UdpPhotonPacket(commandType: Int, commandLength: Int): ByteArray {
        val packet = ByteArray(20 + 8 + 12 + 12)
        packet[0] = 0x45
        put16(packet, 2, packet.size)
        packet[8] = 64
        packet[9] = 17
        packet[12] = 10; packet[15] = 2
        packet[16] = 1; packet[19] = 1
        put16(packet, 20, 5056)
        put16(packet, 22, 40000)
        put16(packet, 24, packet.size - 20)
        val photon = 28
        put16(packet, photon, 1)
        packet[photon + 2] = 0
        packet[photon + 3] = 1
        val command = photon + 12
        packet[command] = commandType.toByte()
        put32(packet, command + 4, commandLength)
        return packet
    }

    private fun put16(bytes: ByteArray, offset: Int, value: Int) {
        bytes[offset] = (value ushr 8).toByte()
        bytes[offset + 1] = value.toByte()
    }

    private fun put32(bytes: ByteArray, offset: Int, value: Int) {
        bytes[offset] = (value ushr 24).toByte()
        bytes[offset + 1] = (value ushr 16).toByte()
        bytes[offset + 2] = (value ushr 8).toByte()
        bytes[offset + 3] = value.toByte()
    }
}
