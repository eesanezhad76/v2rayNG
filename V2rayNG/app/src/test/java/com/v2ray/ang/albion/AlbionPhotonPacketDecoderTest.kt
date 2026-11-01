package com.v2ray.ang.albion

import org.junit.Assert.*
import org.junit.Test

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
