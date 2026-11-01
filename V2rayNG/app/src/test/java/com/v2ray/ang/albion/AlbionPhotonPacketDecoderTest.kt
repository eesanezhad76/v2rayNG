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
        assertEquals("invalid Photon command length", result.error)
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

    @Test fun doesNotTreatPhotonDispatchAsAlbionEventCode() {
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
        assertNull(messages[0].realCode)
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

    @Test fun operationRequestDoesNotUseWireOperationByteAsAlbionOperationCode() {
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
        assertNull(messages[0].realCode)
        assertEquals(1, messages[0].operationCode)
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
