package com.v2ray.ang.albion

import android.content.Context
import kotlinx.coroutines.*
import java.io.*
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/** Daemon-process capture writer; it outlives the Radar Activity. */
class AlbionCaptureWorker(
    private val readPacket: () -> ByteArray?,
    private val dropped: () -> Long,
    private val disableTap: () -> Unit
) {
    data class Snapshot(val running: Boolean, val finished: Boolean, val packets: Long, val dropped: Long, val pcap: File?, val log: File?, val error: String?)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val running = AtomicBoolean(false)
    private val packetCount = AtomicLong(0)
    private val protocol18 = AlbionProtocol18Decoder()
    @Volatile private var pcapFile: File? = null
    @Volatile private var logFile: File? = null
    @Volatile private var failure: String? = null
    @Volatile private var finalized = true
    @Volatile private var pcap: DataOutputStream? = null
    @Volatile private var log: BufferedWriter? = null
    @Volatile private var job: Job? = null

    @Synchronized fun start(context: Context): Boolean {
        if (running.get()) return true
        return try {
            val dir = File(context.getExternalFilesDir(null) ?: context.filesDir, "AlbionCaptures")
            if (!dir.exists() && !dir.mkdirs()) error("Cannot create capture directory")
            val stamp = System.currentTimeMillis()
            val raw = File(dir, "albion-$stamp.pcap")
            val txt = File(dir, "albion-$stamp.jsonl")
            pcap = DataOutputStream(BufferedOutputStream(FileOutputStream(raw), 262144)).also(::writeHeader)
            log = BufferedWriter(OutputStreamWriter(FileOutputStream(txt), Charsets.UTF_8), 65536)
            pcapFile = raw
            logFile = txt
            packetCount.set(0)
            AlbionEntityEventMapper.reset()
            failure = null
            finalized = false
            running.set(true)
            job = scope.launch {
                var lastFlush = System.nanoTime()
                try {
                    while (isActive && running.get()) {
                        var consumed = 0
                        while (consumed < 2048) {
                            val packet = readPacket() ?: break
                            val decoded = AlbionPhotonPacketDecoder.decodeIpPacket(packet)
                            if (decoded != null) writePacket(packet, System.currentTimeMillis(), decoded)
                            consumed++
                        }
                        val now = System.nanoTime()
                        if (now - lastFlush >= 500000000L) {
                            synchronized(this@AlbionCaptureWorker) { flushFiles() }
                            lastFlush = now
                        }
                        if (consumed == 0) delay(10)
                    }
                } catch (t: Throwable) {
                    failure = t.message ?: t.javaClass.simpleName
                    running.set(false)
                    try { disableTap() } catch (_: Throwable) {}
                    synchronized(this@AlbionCaptureWorker) { closeFiles() }
                    finalized = true
                } finally {
                    synchronized(this@AlbionCaptureWorker) { flushFiles() }
                }
            }
            true
        } catch (t: Throwable) {
            failure = t.message ?: t.javaClass.simpleName
            running.set(false)
            closeFiles()
            finalized = true
            false
        }
    }

    suspend fun stop() {
        running.set(false)
        try { disableTap() } catch (_: Throwable) {}
        job?.cancelAndJoin()
        withContext(Dispatchers.IO) {
            var drained = 0
            while (drained < 16384) {
                val packet = readPacket() ?: break
                val decoded = AlbionPhotonPacketDecoder.decodeIpPacket(packet)
                if (decoded != null) writePacket(packet, System.currentTimeMillis(), decoded)
                drained++
            }
            synchronized(this@AlbionCaptureWorker) { flushFiles(); closeFiles() }
            finalized = true
        }
    }

    fun snapshot() = Snapshot(running.get(), finalized, packetCount.get(), dropped(), pcapFile, logFile, failure)

    @Synchronized private fun writePacket(packet: ByteArray, timestampMs: Long, decoded: AlbionPhotonPacketDecoder.Result) {
        val out = pcap ?: return
        out.writeInt(Integer.reverseBytes((timestampMs / 1000L).toInt()))
        out.writeInt(Integer.reverseBytes(((timestampMs % 1000L) * 1000L).toInt()))
        out.writeInt(Integer.reverseBytes(packet.size))
        out.writeInt(Integer.reverseBytes(packet.size))
        out.write(packet)
        packetCount.incrementAndGet()
        val messages = if (decoded.photon && decoded.photonPayload != null) protocol18.receiveDatagram(decoded.photonPayload) else emptyList()
        val messageArray = JSONArray()
        messages.forEach { message ->
            val item = JSONObject()
            item.put("kind", message.kind)
            item.put("dispatch", message.dispatchCode)
            item.put("code", message.realCode ?: JSONObject.NULL)
            item.put("param_keys", JSONArray(message.parameters.keys.sorted()))
            messageArray.put(item)
        }
        val entityArray = JSONArray()
        messages.flatMap { AlbionEntityEventMapper.map(it) }.forEach { entity ->
            val item = JSONObject()
            item.put("kind", entity.kind)
            item.put("event_code", entity.eventCode)
            entity.entityId?.let { item.put("entity_id", it) }
            entity.typeId?.let { item.put("mobile_type_id", it) }
            entity.typeNumber?.let { item.put("type_number", it) }
            entity.tier?.let { item.put("tier", it) }
            entity.enchant?.let { item.put("enchant", it) }
            entity.x?.let { item.put("x", it) }
            entity.y?.let { item.put("y", it) }
            entity.size?.let { item.put("size", it) }
            entity.name?.let { item.put("name", it) }
            entityArray.put(item)
        }
        log?.write(JSONObject().put("time_ms", timestampMs).put("length", packet.size)
            .put("src_port", decoded.srcPort).put("dst_port", decoded.dstPort)
            .put("photon", decoded.photon).put("command_count", decoded.commandCount)
            .put("command_types", JSONArray(decoded.commandTypes)).put("messages", messageArray)
            .put("entities", entityArray).toString())
        log?.newLine()
    }
    @Synchronized private fun flushFiles() {
        try { pcap?.flush() } catch (_: Throwable) {}
        try { log?.flush() } catch (_: Throwable) {}
    }

    @Synchronized private fun closeFiles() {
        flushFiles()
        try { pcap?.close() } catch (_: Throwable) {}
        try { log?.close() } catch (_: Throwable) {}
        pcap = null
        log = null
    }

    private fun writeHeader(out: DataOutputStream) {
        out.writeInt(Integer.reverseBytes(0xa1b2c3d4.toInt()))
        out.writeShort(java.lang.Short.reverseBytes(2.toShort()).toInt())
        out.writeShort(java.lang.Short.reverseBytes(4.toShort()).toInt())
        out.writeInt(0); out.writeInt(0)
        out.writeInt(Integer.reverseBytes(65535))
        out.writeInt(Integer.reverseBytes(101))
    }
}
