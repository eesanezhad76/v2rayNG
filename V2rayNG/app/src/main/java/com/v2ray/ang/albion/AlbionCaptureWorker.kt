package com.v2ray.ang.albion

import android.content.Context
import android.content.ContentValues
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.SystemClock
import android.provider.MediaStore
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
    data class Snapshot(val running: Boolean, val finished: Boolean, val packets: Long, val dropped: Long, val pcap: File?, val log: File?, val error: String?, val publicPcap: Uri? = null, val publicLog: Uri? = null, val mirrorError: String? = null, val markers: Long = 0, val nonAlbion: Long = 0, val report: File? = null)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val running = AtomicBoolean(false)
    private val stopMutex = Mutex()
    private val packetCount = AtomicLong(0)
    private val protocol18 = AlbionProtocol18Decoder()
    @Volatile private var pcapFile: File? = null
    @Volatile private var logFile: File? = null
    @Volatile private var reportFile: File? = null
    @Volatile private var failure: String? = null
    @Volatile private var mirrorFailure: String? = null
    @Volatile private var publicPcapUri: Uri? = null
    @Volatile private var publicLogUri: Uri? = null
    @Volatile private var publicReportUri: Uri? = null
    @Volatile private var appContext: Context? = null
    @Volatile private var knowledgeStore: AlbionLiveKnowledgeStore? = null
    @Volatile private var pcapFileStream: FileOutputStream? = null
    @Volatile private var logFileStream: FileOutputStream? = null
    private val markerCount = AtomicLong(0)
    private val nonAlbionCount = AtomicLong(0)
    private val dropBaseline = AtomicLong(0)
    private val mirroredPcapBytes = AtomicLong(0)
    private val mirroredLogBytes = AtomicLong(0)
    private val mirrorInProgress = AtomicBoolean(false)
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
            appContext = context.applicationContext
            knowledgeStore = AlbionLiveKnowledgeStore(context.applicationContext)
            pcapFileStream = FileOutputStream(raw)
            logFileStream = FileOutputStream(txt)
            pcap = DataOutputStream(BufferedOutputStream(pcapFileStream!!, 262144)).also(::writeHeader)
            log = BufferedWriter(OutputStreamWriter(BufferedOutputStream(logFileStream!!, 65536), Charsets.UTF_8), 65536)
            publicPcapUri = createDownloadsEntry(context, raw.name, "application/vnd.tcpdump.pcap")
            publicLogUri = createDownloadsEntry(context, txt.name, "application/x-ndjson")
            mirrorFailure = null
            markerCount.set(0)
            nonAlbionCount.set(0)
            publicReportUri = null
            pcapFile = raw
            logFile = txt
            packetCount.set(0)
            dropBaseline.set(dropped())
            mirroredPcapBytes.set(0)
            mirroredLogBytes.set(0)
            reportFile = null
            AlbionPhotonPacketDecoder.reset()
            AlbionEntityEventMapper.reset()
            failure = null
            finalized = false
            running.set(true)
            job = scope.launch {
                var lastFlush = System.nanoTime()
                var lastMirror = lastFlush
                try {
                    while (isActive && running.get()) {
                        var consumed = 0
                        while (consumed < 2048) {
                            val packet = readPacket() ?: break
                            val decoded = AlbionPhotonPacketDecoder.decodeIpPacket(packet)
                            writePacket(packet, System.currentTimeMillis(), decoded)
                            consumed++
                        }
                        val now = System.nanoTime()
                        if (now - lastFlush >= 500000000L) {
                            synchronized(this@AlbionCaptureWorker) { flushFiles() }
                            lastFlush = now
                        }
                        if (now - lastMirror >= 2_000_000_000L) {
                            synchronized(this@AlbionCaptureWorker) { flushFiles() }
                            scope.launch(Dispatchers.IO) { mirrorDownloads() }
                            lastMirror = now
                        }
                        if (consumed == 0) delay(10)
                    }
                } catch (cancelled: CancellationException) {
                    // stop() cancels the polling job before draining the native queue.
                    // Do not close the files here: stop() still has to append the queued
                    // tail packets before it flushes and closes both files.
                    if (running.get()) {
                        failure = "capture worker cancelled unexpectedly"
                        running.set(false)
                        try { disableTap() } catch (_: Throwable) {}
                        synchronized(this@AlbionCaptureWorker) { closeFiles() }
                        finalized = true
                    }
                    throw cancelled
                } catch (t: Throwable) {
                    failure = t.message ?: t.javaClass.simpleName
                    running.set(false)
                    try { disableTap() } catch (_: Throwable) {}
                    synchronized(this@AlbionCaptureWorker) { closeFiles() }
                    withContext(Dispatchers.IO) { generateOfflineReport(); while (mirrorInProgress.get()) delay(10); mirrorDownloads() }
                    finalized = true
                } finally {
                    synchronized(this@AlbionCaptureWorker) { flushFiles() }
                }
            }
            appendLifecycleMarker("capture_started")
            true
        } catch (t: Throwable) {
            failure = t.message ?: t.javaClass.simpleName
            running.set(false)
            closeFiles()
            finalized = true
            false
        }
    }

    suspend fun stop() = stopMutex.withLock {
        if (finalized) return@withLock
        appendLifecycleMarker("capture_stopping")
        running.set(false)
        try { disableTap() } catch (_: Throwable) {}
        job?.cancelAndJoin()
        withContext(Dispatchers.IO) {
            while (true) {
                val packet = readPacket() ?: break
                val decoded = AlbionPhotonPacketDecoder.decodeIpPacket(packet)
                writePacket(packet, System.currentTimeMillis(), decoded)
            }
            synchronized(this@AlbionCaptureWorker) { knowledgeStore?.flush(); flushFiles(); closeFiles() }
            withContext(Dispatchers.IO) {
                generateOfflineReport()
                while (mirrorInProgress.get()) delay(10)
                mirrorDownloads()
            }
            finalized = true
        }
    }

    private fun appendLifecycleMarker(event: String) {
        val now = System.currentTimeMillis()
        val json = JSONObject().put("timestamp_ms", now)
            .put("elapsed_realtime_ms", SystemClock.elapsedRealtime())
            .put("event", event).put("activity", "Capture")
            .put("source", "capture_worker").toString()
        try { appendMarker(AlbionCaptureMarker.build(json), now) } catch (t: Throwable) {
            failure = "lifecycle checkpoint failed: ${t.message}"
        }
    }

    /** Writes a synthetic local annotation record; it is never sent to the VPN or game. */
    @Synchronized fun appendMarker(packet: ByteArray, timestampMs: Long): Boolean {
        val out = pcap ?: return false
        val textLog = log ?: return false
        if (!running.get() || packet.size < 20) return false
        return try {
            // Require both sinks before writing; otherwise a tap could appear successful
            // in the overlay while PatternMiner has no JSONL marker to analyze.
            val marker = AlbionCaptureMarker.parse(packet)
            out.writeInt(Integer.reverseBytes((timestampMs / 1000L).toInt()))
            out.writeInt(Integer.reverseBytes(((timestampMs % 1000L) * 1000L).toInt()))
            out.writeInt(Integer.reverseBytes(packet.size))
            out.writeInt(Integer.reverseBytes(packet.size))
            out.write(packet)
            textLog.write(JSONObject().put("time_ms", timestampMs).put("manual_marker", marker).toString())
            textLog.newLine()
            markerCount.incrementAndGet()
            out.flush()
            pcapFileStream?.fd?.sync()
            textLog.flush()
            logFileStream?.fd?.sync()
            scope.launch(Dispatchers.IO) { mirrorDownloads() }
            true
        } catch (t: Throwable) {
            failure = "marker write failed: ${t.message}"
            false
        }
    }

    fun snapshot() = Snapshot(running.get(), finalized, packetCount.get(), (dropped() - dropBaseline.get()).coerceAtLeast(0), pcapFile, logFile, failure, publicPcapUri, publicLogUri, mirrorFailure, markerCount.get(), nonAlbionCount.get(), reportFile)

    fun liveKnowledgeSnapshot(): String = knowledgeStore?.snapshotText()
        ?: "Live knowledge unavailable: capture has not initialized."

    @Synchronized private fun writePacket(packet: ByteArray, timestampMs: Long, decoded: AlbionPhotonPacketDecoder.Result?) {
        val out = pcap ?: return
        out.writeInt(Integer.reverseBytes((timestampMs / 1000L).toInt()))
        out.writeInt(Integer.reverseBytes(((timestampMs % 1000L) * 1000L).toInt()))
        out.writeInt(Integer.reverseBytes(packet.size))
        out.writeInt(Integer.reverseBytes(packet.size))
        out.write(packet)
        packetCount.incrementAndGet()
        if (decoded == null) nonAlbionCount.incrementAndGet()
        val messages = if (decoded?.photon == true && decoded.photonPayload != null) protocol18.receiveDatagram(decoded.photonPayload) else emptyList()
        val messageArray = JSONArray()
        val photonEventAnalysis = JSONArray()
        messages.forEach { message ->
            val item = JSONObject()
            item.put("kind", message.kind)
            item.put("dispatch", message.dispatchCode)
            item.put("code", message.realCode ?: JSONObject.NULL)
            item.put("operation_code", message.operationCode ?: JSONObject.NULL)
            item.put("return_code", message.returnCode ?: JSONObject.NULL)
            item.put("param_keys", JSONArray(message.parameters.keys.sorted()))
            messageArray.put(item)

            if (message.kind == "event" && message.realCode != null) {
                val normalized = AlbionPhotonEventDecoder.decode(message.realCode, message.parameters)
                val eventItem = JSONObject().put("event_code", message.realCode).put("recognized", normalized.recognized)
                val resources = JSONArray()
                normalized.resources.forEach { resource ->
                    val r = JSONObject().put("id", resource.id)
                    resource.typeId?.let { r.put("type_id", it) }
                    resource.tier?.let { r.put("tier", it) }
                    resource.x?.let { r.put("x", it) }
                    resource.y?.let { r.put("y", it) }
                    resource.amount?.let { r.put("amount", it) }
                    resource.enchantmentRaw?.let { r.put("enchantment_raw", it) }
                    resources.put(r)
                }
                val living = JSONArray()
                normalized.livingEntities.forEach { entity ->
                    val e = JSONObject().put("id", entity.id)
                    entity.typeId?.let { e.put("type_id", it) }
                    entity.x?.let { e.put("x", it) }
                    entity.y?.let { e.put("y", it) }
                    living.put(e)
                }
                eventItem.put("resources", resources).put("living_entities", living)
                normalized.stateUpdate?.let { state ->
                    val update = JSONObject().put("id", state.id)
                    state.amountOrState?.let { update.put("amount_or_state", it) }
                    state.enchantmentRaw?.let { update.put("enchantment_raw", it) }
                    eventItem.put("state_update", update)
                }
                photonEventAnalysis.put(eventItem)
            }
        }
        val observations = messages.flatMap { AlbionEntityEventMapper.map(it) }
        knowledgeStore?.observe(timestampMs, observations)
        val entityArray = JSONArray()
        observations.forEach { entity ->
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
            entity.value?.let { item.put("value", it) }
            entity.secondaryValue?.let { item.put("secondary_value", it) }
            if (entity.kind == "resource_state") item.put("removed", entity.removed)
            entityArray.put(item)
        }
        val decoderStats = protocol18.stats()
        log?.write(JSONObject().put("time_ms", timestampMs).put("length", packet.size)
            .put("ip_version", if (packet.isNotEmpty()) (packet[0].toInt() ushr 4) and 15 else JSONObject.NULL)
            .put("src_port", decoded?.srcPort ?: JSONObject.NULL).put("dst_port", decoded?.dstPort ?: JSONObject.NULL)
            .put("albion_candidate", decoded != null).put("photon", decoded?.photon ?: false)
            .put("photon_encrypted", decoded?.encrypted ?: false)
            .put("photon_error", decoded?.error ?: JSONObject.NULL).put("command_count", decoded?.commandCount ?: 0)
            .put("command_types", JSONArray(decoded?.commandTypes ?: emptyList<Int>())).put("messages", messageArray)
            .put("photon_event_analysis", photonEventAnalysis)
            .put("entities", entityArray).put("protocol18_encrypted", decoderStats.encrypted)
            .put("protocol18_malformed", decoderStats.malformed)
            .put("fragments_pending", decoderStats.fragmentsPending).toString())
        log?.newLine()
    }
    @Synchronized private fun flushFiles() {
        try { pcap?.flush(); pcapFileStream?.fd?.sync() } catch (t: Throwable) { failure = "PCAP flush failed: ${t.message}" }
        try { log?.flush(); logFileStream?.fd?.sync() } catch (t: Throwable) { failure = "JSONL flush failed: ${t.message}" }
    }

    /** Create visible Downloads entries on Android 10+ without broad storage permission. */
    private fun createDownloadsEntry(context: Context, name: String, mime: String): Uri? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, mime)
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/AlbionRadar")
            put(MediaStore.MediaColumns.IS_PENDING, 0)
        }
        return context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
    }

    /** Periodically mirrors flushed files so Downloads is updated during capture, not only on Export/Stop. */
    private fun mirrorDownloads() {
        if (!mirrorInProgress.compareAndSet(false, true)) return
        val context = appContext ?: run { mirrorInProgress.set(false); return }
        try {
            pcapFile?.let { file -> publicPcapUri?.let { mirrorOne(context, file, it, mirroredPcapBytes) } }
            logFile?.let { file -> publicLogUri?.let { mirrorOne(context, file, it, mirroredLogBytes) } }
            mirrorFailure = null
        } catch (t: Throwable) {
            mirrorFailure = t.message ?: t.javaClass.simpleName
        } finally {
            mirrorInProgress.set(false)
        }
    }

    private fun mirrorOne(context: Context, source: File, uri: Uri, offset: AtomicLong) {
        val start = offset.get()
        val available = source.length()
        if (available <= start) return
        context.contentResolver.openOutputStream(uri, "wa")?.use { output ->
            FileInputStream(source).use { input ->
                input.channel.position(start)
                val buffer = ByteArray(256 * 1024)
                var copied = 0L
                while (copied < available - start) {
                    val n = input.read(buffer, 0, minOf(buffer.size.toLong(), available - start - copied).toInt())
                    if (n <= 0) break
                    output.write(buffer, 0, n)
                    copied += n
                }
                output.flush()
                offset.addAndGet(copied)
            }
        } ?: error("Cannot append Downloads mirror: ${source.name}")
    }

    private fun generateOfflineReport() {
        val source = pcapFile ?: return
        if (!source.exists()) return
        try {
            knowledgeStore?.flush()
            val report = AlbionCaptureAnalyzer.analyze(source).asText() + AlbionPatternMiner.analyze(logFile).asText() + (knowledgeStore?.summaryText() ?: "")
            val target = File(source.parentFile, source.nameWithoutExtension + ".report.txt")
            target.writeText(buildString {
                append(report)
                appendLine("\nCapture integrity")
                appendLine("PCAP records (TUN packets + checkpoints): ${packetCount.get() + markerCount.get()}")
                appendLine("Native tap dropped copies: ${(dropped() - dropBaseline.get()).coerceAtLeast(0)}")
                appendLine("Manual checkpoints: ${markerCount.get()}")
                appendLine("Non-Albion / unclassified packets: ${nonAlbionCount.get()}")
                appendLine("Downloads mirror error: ${mirrorFailure ?: "none"}")
            }, Charsets.UTF_8)
            reportFile = target
            val context = appContext
            if (context != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val uri = createDownloadsEntry(context, target.name, "text/plain")
                if (uri != null) {
                    publicReportUri = uri
                    context.contentResolver.openOutputStream(uri, "w")?.use { out -> target.inputStream().use { it.copyTo(out) } }
                }
            }
        } catch (t: Throwable) {
            failure = "offline report failed: ${t.message}"
        }
    }

    @Synchronized private fun closeFiles() {
        knowledgeStore?.flush()
        flushFiles()
        try { pcap?.close() } catch (_: Throwable) {}
        try { log?.close() } catch (_: Throwable) {}
        pcap = null
        log = null
        try { pcapFileStream?.close() } catch (_: Throwable) {}
        try { logFileStream?.close() } catch (_: Throwable) {}
        pcapFileStream = null
        logFileStream = null
        mirrorDownloads()
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
