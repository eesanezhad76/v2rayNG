package com.v2ray.ang.albion

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets
import kotlin.math.min

/**
 * Passive Photon/Protocol18 decoder based on the MIT-licensed OpenRadar parser.
 * This class does not decrypt encrypted Photon messages or infer encrypted player positions.
 */
class AlbionProtocol18Decoder {
    data class Message(
        val kind: String,
        val dispatchCode: Int,
        val realCode: Int?,
        val parameters: Map<Int, Any?>,
        val operationCode: Int? = null,
        val returnCode: Int? = null
    )
    data class DecodeStats(val encrypted: Int, val malformed: Int, val fragmentsPending: Int)

    private data class FragmentSet(val bytes: ByteArray, val received: BooleanArray, var written: Int = 0, var lastUpdated: Long = System.currentTimeMillis())
    private val fragments = LinkedHashMap<Long, FragmentSet>()
    private var encryptedCount = 0
    private var malformedCount = 0

    fun receiveDatagram(datagram: ByteArray): List<Message> {
        expireFragments()
        if (datagram.size < 12) { malformedCount++; return emptyList() }
        val flags = u8(datagram, 2)
        val commandCount = u8(datagram, 3)
        if (flags == 1) { encryptedCount++; return emptyList() }
        if (commandCount !in 0..64) { malformedCount++; return emptyList() }
        if (commandCount == 0) return emptyList()
        var offset = 12
        val output = ArrayList<Message>()
        repeat(commandCount) {
            if (offset + 12 > datagram.size) { malformedCount++; return output }
            val type = u8(datagram, offset)
            val lengthLong = u32(datagram, offset + 4)
            if (lengthLong < 12 || lengthLong > (datagram.size - offset).toLong()) {
                malformedCount++; return output
            }
            val end = offset + lengthLong.toInt()
            var body = offset + 12
            var bodyLength = end - body
            when (type) {
                6 -> decodeCommandBody(datagram, body, bodyLength, output)
                7 -> {
                    if (bodyLength < 4) malformedCount++
                    else decodeCommandBody(datagram, body + 4, bodyLength - 4, output)
                }
                8 -> handleFragment(datagram, body, bodyLength, output)
                else -> Unit
            }
            offset = end
        }
        return output
    }

    fun stats() = DecodeStats(encryptedCount, malformedCount, fragments.size)

    private fun decodeCommandBody(src: ByteArray, start: Int, length: Int, out: MutableList<Message>) {
        if (length < 2 || start < 0 || start > src.size || length > src.size - start) { malformedCount++; return }
        val messageType = u8(src, start + 1)
        val payloadStart = start + 2
        val payloadLength = length - 2
        if (messageType == 130 || messageType == 131 || messageType == 132) { encryptedCount++; return }
        try {
            when (messageType) {
                2 -> {
                    val r = Reader(src, payloadStart, payloadLength)
                    val op = r.byte()
                    val params = r.parameterTable()
                    if (!params.containsKey(253)) params[253] = op
                    out += Message("request", 0, params[253].asInt(), params, op)
                }
                3, 7 -> {
                    val r = Reader(src, payloadStart, payloadLength)
                    val op = r.byte()
                    val ret = r.short()
                    var debug: Any? = null
                    if (r.remaining() > 0) debug = r.typedValue()
                    val params = r.parameterTable()
                    if (debug is List<*>) params[0] = debug
                    if (!params.containsKey(253)) params[253] = op
                    out += Message("response", 0, params[253].asInt(), params, op, ret)
                }
                4 -> {
                    val r = Reader(src, payloadStart, payloadLength)
                    val dispatch = r.byte()
                    val params = r.parameterTable().toMutableMap()
                    // Parameter 252 is authoritative when present; dispatch is only fallback.
                    if (!params.containsKey(252)) params[252] = dispatch
                    val real = params[252].asInt()
                    if (real == 3) injectMovePosition(params)
                    out += Message("event", dispatch, real, params)
                }
            }
        } catch (_: Exception) {
            malformedCount++
        }
    }

    private fun handleFragment(src: ByteArray, start: Int, length: Int, out: MutableList<Message>) {
        expireFragments()
        if (length < 20 || start + length > src.size) { malformedCount++; return }
        val sequence = u32(src, start)
        val totalLength = u32(src, start + 12)
        val fragmentOffset = u32(src, start + 16)
        val dataStart = start + 20
        val dataLength = length - 20
        if (totalLength <= 0 || totalLength > 262_144 || fragmentOffset > totalLength || dataLength.toLong() > totalLength - fragmentOffset) {
            malformedCount++; return
        }
        if (fragments.size >= 16 && !fragments.containsKey(sequence)) {
            val oldest = fragments.keys.firstOrNull()
            if (oldest != null) fragments.remove(oldest)
        }
        var set = fragments[sequence]
        if (set != null && set.bytes.size != totalLength.toInt()) {
            fragments.remove(sequence)
            malformedCount++
            set = null
        }
        if (set == null) {
            set = FragmentSet(ByteArray(totalLength.toInt()), BooleanArray(totalLength.toInt()))
            fragments[sequence] = set
        }
        set.lastUpdated = System.currentTimeMillis()
        val base = fragmentOffset.toInt()
        for (i in 0 until dataLength) {
            val target = base + i
            if (!set.received[target]) {
                set.received[target] = true
                set.bytes[target] = src[dataStart + i]
                set.written++
            }
        }
        if (set.written == set.bytes.size) {
            fragments.remove(sequence)
            decodeCommandBody(set.bytes, 0, set.bytes.size, out)
        }
    }

    private fun expireFragments() {
        val cutoff = System.currentTimeMillis() - FRAGMENT_TTL_MS
        val iterator = fragments.entries.iterator()
        while (iterator.hasNext()) {
            if (iterator.next().value.lastUpdated < cutoff) {
                iterator.remove()
                malformedCount++
            }
        }
    }

    private fun injectMovePosition(params: MutableMap<Int, Any?>) {
        val raw = params[1] as? ByteArray ?: return
        if (raw.size < 17 || (raw[0].toInt() and 255) != 3) return
        val b = ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN)
        b.position(9)
        val x = b.float
        val y = b.float
        if (x.isFinite() && y.isFinite()) { params[4] = x; params[5] = y }
    }

    private class Reader(private val bytes: ByteArray, start: Int, length: Int) {
        private var pos = start
        private val end = start + length
        fun remaining() = end - pos
        private fun require(n: Int) { require(n >= 0 && pos >= 0 && n <= end - pos) { "truncated Protocol18 payload" } }
        fun byte(): Int { require(1); return u8(bytes, pos++) }
        fun short(): Int { require(2); val v = u16le(bytes, pos); pos += 2; return v.toShort().toInt() }
        private fun ushort(): Int { require(2); val v = u16le(bytes, pos); pos += 2; return v }
        private fun float(): Float { require(4); val v = ByteBuffer.wrap(bytes, pos, 4).order(ByteOrder.LITTLE_ENDIAN).float; pos += 4; return v }
        private fun double(): Double { require(8); val v = ByteBuffer.wrap(bytes, pos, 8).order(ByteOrder.LITTLE_ENDIAN).double; pos += 8; return v }
        private fun varUInt(): Long {
            var value = 0L; var shift = 0
            while (shift < 35) {
                val b = byte(); value = value or ((b and 127).toLong() shl shift)
                if ((b and 128) == 0) return value
                shift += 7
            }
            error("invalid compressed integer")
        }
        private fun varULong(): Long {
            var value = 0L; var shift = 0
            while (shift < 70) {
                val b = byte(); value = value or ((b and 127).toLong() shl shift)
                if ((b and 128) == 0) return value
                shift += 7
            }
            error("invalid compressed long")
        }
        private fun varInt(): Int { val v = varUInt(); return ((v ushr 1) xor -(v and 1).toLong()).toInt() }
        private fun varLong(): Long { val v = varULong(); return (v ushr 1) xor -(v and 1L) }
        private fun string(): String {
            val n = varUInt().toInt()
            require(n in 0..remaining())
            val s = String(bytes, pos, n, StandardCharsets.UTF_8); pos += n; return s
        }
        fun parameterTable(): MutableMap<Int, Any?> {
            val count = varUInt().toInt()
            require(count in 0..65536)
            val result = LinkedHashMap<Int, Any?>(count)
            repeat(count) { val key = byte(); val tc = byte(); result[key] = value(tc) }
            return result
        }
        fun typedValue(): Any? = value(byte())
        private fun value(tc: Int): Any? {
            if (tc >= 128) return custom(slim = true)
            return when (tc) {
                0, 8 -> null
                2 -> byte() != 0
                3 -> byte()
                4 -> short()
                5 -> float()
                6 -> double()
                7 -> string()
                9 -> varInt()
                10 -> varLong()
                11 -> byte()
                12 -> -byte()
                13 -> ushort()
                14 -> -ushort()
                15 -> byte().toLong()
                16 -> -byte().toLong()
                17 -> ushort().toLong()
                18 -> -ushort().toLong()
                19 -> custom(slim = false)
                20, 21 -> dictionary()
                23 -> objectArray()
                24 -> mapOf("operationCode" to byte(), "parameters" to parameterTable())
                25 -> {
                    val op = byte(); val ret = short()
                    val debug = if (remaining() > 0) typedValue() else null
                    mapOf("operationCode" to op, "returnCode" to ret, "debugMessage" to debug, "parameters" to parameterTable())
                }
                26 -> {
                    val code = byte()
                    val params = parameterTable()
                    if (!params.containsKey(252)) params[252] = code
                    mapOf("code" to code, "parameters" to params)
                }
                27 -> false
                28 -> true
                29 -> 0.toShort()
                30 -> 0
                31 -> 0L
                32 -> 0f
                33 -> 0.0
                34 -> 0.toByte()
                64 -> nestedArray()
                else -> if ((tc and 64) != 0) typedArray(tc and 191) else null
            }
        }
        private fun custom(slim: Boolean): ByteArray {
            if (!slim) byte()
            val n = varUInt().toInt()
            require(n in 0..min(65536, remaining()))
            val result = bytes.copyOfRange(pos, pos + n); pos += n; return result
        }
        private fun dictionary(): Map<Any?, Any?> {
            val keyType = byte(); val valueType = byte(); val count = varUInt().toInt()
            require(count in 0..65536)
            val map = LinkedHashMap<Any?, Any?>(count)
            repeat(count) {
                val kt = if (keyType == 0) byte() else keyType
                val vt = if (valueType == 0) byte() else valueType
                val key = value(kt); val value = value(vt)
                if (key == null || key is String || key is Number || key is Boolean) map[key] = value
            }
            return map
        }
        private fun objectArray(): List<Any?> {
            val n = varUInt().toInt(); require(n in 0..65536)
            return List(n) { value(byte()) }
        }
        private fun nestedArray(): List<Any?> {
            val n = varUInt().toInt(); require(n in 0..65536)
            val tc = byte()
            return List(n) { value(tc) }
        }
        private fun typedArray(tc: Int): Any {
            val n = varUInt().toInt(); require(n in 0..65536)
            return when (tc) {
                2 -> {
                    val packed = ByteArray((n + 7) / 8) { byte().toByte() }
                    List(n) { index -> (packed[index / 8].toInt() and (1 shl (index % 8))) != 0 }
                }
                3 -> ByteArray(n) { byte().toByte() }
                4 -> ShortArray(n) { short().toShort() }
                5 -> FloatArray(n) { float() }
                6 -> DoubleArray(n) { double() }
                7 -> List(n) { string() }
                9 -> IntArray(n) { varInt() }
                10 -> LongArray(n) { varLong() }
                20, 21 -> List(n) { dictionary() }
                19 -> { byte(); List(n) { custom(slim = true) } }
                else -> List(n) { value(tc) }
            }
        }
    }

    companion object {
        private const val FRAGMENT_TTL_MS = 30_000L
        private fun u8(b: ByteArray, o: Int) = b[o].toInt() and 255
        private fun u16le(b: ByteArray, o: Int) = (b[o].toInt() and 255) or ((b[o + 1].toInt() and 255) shl 8)
        private fun u32(b: ByteArray, o: Int) =
            ((b[o].toLong() and 255L) shl 24) or ((b[o + 1].toLong() and 255L) shl 16) or
            ((b[o + 2].toLong() and 255L) shl 8) or (b[o + 3].toLong() and 255L)
        private fun Any?.asInt(): Int? = when (this) {
            is Number -> toInt()
            else -> null
        }
    }
}
