package com.v2ray.ang.ui.albion

import android.app.Activity
import android.content.Intent
import android.content.BroadcastReceiver
import android.content.IntentFilter
import android.content.ClipboardManager
import android.content.ClipData
import android.content.Context
import android.net.Uri
import android.provider.Settings
import android.os.Bundle
import android.os.Build
import android.os.SystemClock
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.v2ray.ang.core.CoreServiceManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedOutputStream
import java.io.BufferedWriter
import java.io.FileWriter
import java.io.DataOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.InetAddress
import org.json.JSONObject

/** Passive diagnostic capture. Every packet delivered by the native tap is written to PCAP. */
class AlbionRadarActivity : ComponentActivity() {
    private lateinit var status: TextView
    private lateinit var summary: TextView
    private lateinit var packets: TextView
    private lateinit var toggle: Button
    private lateinit var export: Button
    private lateinit var copyAll: Button
    private lateinit var analyze: Button
    private lateinit var overlay: Button
    private lateinit var exportTags: Button
    private var pollJob: Job? = null
    private val captureScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var stopping = false
    private var packetCount = 0L
    private var captureFile: File? = null
    private var captureOutput: DataOutputStream? = null
    private var captureTextFile: File? = null
    private var captureTextOutput: BufferedWriter? = null
    private val recent = ArrayDeque<String>()
    private val protocolCounts = linkedMapOf<String, Long>()
    private var tapping = false

    companion object {
        private const val CREATE_PCAP_REQUEST = 7301
        private const val CREATE_TAGS_REQUEST = 7302
        private const val TAP_CAPACITY = 4096
        private const val MAX_READ_BATCH = 8192
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "Albion packet monitor"
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 20, 24, 20)
        }
        status = TextView(this).apply {
            textSize = 18f
            text = "Waiting for V2RayNG connection"
        }
        summary = TextView(this).apply {
            textSize = 14f
            text = "Packets saved: 0 | dropped copies: 0"
        }
        toggle = Button(this).apply {
            text = "Start full capture"
            setOnClickListener { if (tapping) stopCapture() else startCapture() }
        }
        export = Button(this).apply {
            text = "Export PCAP"
            isEnabled = false
            setOnClickListener { exportCapture() }
        }
        copyAll = Button(this).apply {
            text = "Copy packet summary"
            isEnabled = false
            setOnClickListener { copyAllPackets() }
        }
        analyze = Button(this).apply {
            text = "Analyze mining session"
            isEnabled = false
            setOnClickListener { analyzeCapture() }
        }
        overlay = Button(this).apply {
            text = "Launch activity tagging overlay"
            setOnClickListener { launchActivityOverlay() }
        }
        exportTags = Button(this).apply {
            text = "Export activity tags (JSONL)"
            setOnClickListener { exportActivityTags() }
        }
        packets = TextView(this).apply {
            textSize = 13f
            typeface = android.graphics.Typeface.MONOSPACE
            text = "No packets captured yet."
        }
        root.addView(status, LinearLayout.LayoutParams(-1, -2))
        root.addView(summary, LinearLayout.LayoutParams(-1, -2).apply { topMargin = 12 })
        root.addView(toggle, LinearLayout.LayoutParams(-1, -2).apply { topMargin = 12 })
        val actions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(copyAll, LinearLayout.LayoutParams(0, -2, 1f))
            addView(export, LinearLayout.LayoutParams(0, -2, 1f).apply { leftMargin = 8 })
        }
        root.addView(actions, LinearLayout.LayoutParams(-1, -2).apply { topMargin = 6 })
        root.addView(analyze, LinearLayout.LayoutParams(-1, -2).apply { topMargin = 6 })
        root.addView(overlay, LinearLayout.LayoutParams(-1, -2).apply { topMargin = 6 })
        root.addView(exportTags, LinearLayout.LayoutParams(-1, -2).apply { topMargin = 6 })
        root.addView(
            ScrollView(this).apply { addView(packets) },
            LinearLayout.LayoutParams(-1, 0, 1f).apply { topMargin = 12 }
        )
        setContentView(root)
        val markerFilter = IntentFilter(AlbionOverlayService.ACTION_MARKER)
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(markerReceiver, markerFilter, Context.RECEIVER_NOT_EXPORTED)
        else registerReceiver(markerReceiver, markerFilter)
    }

    private val markerReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val marker = intent?.getStringExtra(AlbionOverlayService.EXTRA_MARKER) ?: return
            writeMarkerToPcap(marker)
        }
    }

    private fun writeMarkerToPcap(markerJson: String) {
        val out = captureOutput
        if (!tapping || out == null) {
            status.text = "Marker not written: start PCAP capture first."
            return
        }
        try {
            val packet = buildMarkerIpPacket(markerJson.toByteArray(Charsets.UTF_8))
            writePcapRecord(out, packet, System.currentTimeMillis())
            out.flush()
            status.text = "Activity marker embedded in current PCAP (capture-only; not sent to Albion)."
        } catch (e: Exception) {
            status.text = "Could not write activity marker: ${e.message}"
        }
    }

    // Synthetic private protocol 253 record; this never enters the VPN or game socket.
    private fun buildMarkerIpPacket(json: ByteArray): ByteArray {
        val payload = "ALBION_TAG_V1\n".toByteArray(Charsets.US_ASCII) + json
        require(payload.size <= 65515) { "Marker too large" }
        val packet = ByteArray(20 + payload.size)
        packet[0] = 0x45
        putU16(packet, 2, packet.size)
        putU16(packet, 4, 0xA17B)
        putU16(packet, 6, 0x4000)
        packet[8] = 64
        packet[9] = 253.toByte()
        packet[12] = 192.toByte(); packet[13] = 0; packet[14] = 2; packet[15] = 1
        packet[16] = 192.toByte(); packet[17] = 0; packet[18] = 2; packet[19] = 2
        System.arraycopy(payload, 0, packet, 20, payload.size)
        var sum = 0L
        for (i in 0 until 20 step 2) sum += ((packet[i].toInt() and 255) shl 8) + (packet[i + 1].toInt() and 255)
        while ((sum shr 16) != 0L) sum = (sum and 0xffff) + (sum shr 16)
        putU16(packet, 10, sum.inv().toInt() and 0xffff)
        return packet
    }

    private fun putU16(target: ByteArray, offset: Int, value: Int) {
        target[offset] = (value ushr 8).toByte()
        target[offset + 1] = value.toByte()
    }

    private fun launchActivityOverlay() {
        if (Build.VERSION.SDK_INT >= 23 && !Settings.canDrawOverlays(this)) {
            status.text = "Allow Display over other apps for V2RayNG, then return and tap the overlay button again."
            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
            startActivity(intent)
            return
        }
        try {
            val intent = Intent(this, AlbionOverlayService::class.java)
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(intent) else startService(intent)
            status.text = "Activity tagging overlay started. It writes local timestamps only; packet capture and VPN routing are unchanged."
        } catch (e: Exception) {
            status.text = "Could not start activity overlay: ${e.message}"
        }
    }

    private fun startCapture() {
        if (!CoreServiceManager.isRunning()) {
            status.text = "Start V2RayNG and connect first."
            return
        }
        try {
            val stamp = System.currentTimeMillis()
            val file = File(cacheDir, "albion-capture-$stamp.pcap")
            val textFile = File(cacheDir, "albion-capture-$stamp.txt")
                captureOutput = DataOutputStream(BufferedOutputStream(FileOutputStream(file, false), 256 * 1024))
            writePcapHeader(captureOutput!!)
            captureTextOutput = BufferedWriter(FileWriter(textFile, false), 64 * 1024)
            captureFile = file
            captureTextFile = textFile
            packetCount = 0L
            recent.clear()
            protocolCounts.clear()
        } catch (e: Exception) {
            status.text = "Could not create capture file: ${e.message}"
            closeCaptureFile()
            closeTextCaptureFile()
            return
        }

        if (!CoreServiceManager.enableAlbionPacketTap(TAP_CAPACITY)) {
            status.text = "Could not enable packet tap. Check the custom AAR."
            closeCaptureFile()
            return
        }
        tapping = true
        toggle.text = "Stop capture"
        export.isEnabled = false
        copyAll.isEnabled = false
        analyze.isEnabled = false
        status.text = "Continuous capture active. Play normally; no event buttons needed. Packet timestamps are recorded automatically."
        pollJob = captureScope.launch {
            while (isActive && tapping) {
                val batch = withContext(Dispatchers.IO) {
                    val result = ArrayList<ByteArray>()
                    while (result.size < MAX_READ_BATCH) {
                        val packet = CoreServiceManager.readAlbionPacketTap() ?: break
                        result.add(packet)
                    }
                    result
                }
                if (batch.isNotEmpty()) {
                    try {
                        val out = captureOutput
                        if (out != null) {
                            batch.forEach { packet ->
                                val packetTime = System.currentTimeMillis()
                                writePcapRecord(out, packet, packetTime)
                                packetCount++
                                val description = describePacket(packet)
                                captureTextOutput?.apply {
                                    write(description)
                                    newLine()
                                }
                                recent.addFirst(description)
                                while (recent.size > 40) recent.removeLast()
                                val protocol = description.substringBefore(' ')
                                protocolCounts[protocol] = (protocolCounts[protocol] ?: 0L) + 1L
                            }
                            out.flush()
                            captureTextOutput?.flush()
                        }
                    } catch (e: Exception) {
                        status.text = "File write error: ${e.message}. Capture stopped."
                        stopCapture()
                        return@launch
                    }
                    packets.text = buildString {
                        append("Recent packets (last 40 only; full capture is saved):\n")
                        append(recent.joinToString("\n"))
                        append("\n\nProtocol totals:\n")
                        protocolCounts.entries.sortedByDescending { it.value }.forEach {
                            append(it.key).append(": ").append(it.value).append('\n')
                        }
                    }
                }
                summary.text = "Packets saved: $packetCount | dropped copies: " +
                    CoreServiceManager.getAlbionPacketTapDropped() +
                    " | file: " + (captureFile?.length() ?: 0L) / 1024 + " KB"
                delay(40)
            }
        }
    }

    private fun stopCapture() {
        if (stopping || !tapping) return
        tapping = false
        stopping = true
        // Stop accepting new packets, but leave the native queue readable.
        CoreServiceManager.disableAlbionPacketTap()
        val finishingJob = pollJob
        pollJob = null

        val finalize = {
            captureScope.launch {
                try {
                    withContext(Dispatchers.IO) {
                        // The polling job has completed, so every packet it already removed
                        // has been written. Drain the remaining native queue before closing.
                        while (true) {
                            val packet = CoreServiceManager.readAlbionPacketTap() ?: break
                            val out = captureOutput ?: break
                            val packetTime = System.currentTimeMillis()
                            writePcapRecord(out, packet, packetTime)
                            packetCount++
                            val description = describePacket(packet)
                            captureTextOutput?.apply { write(description); newLine() }
                            recent.addFirst(description)
                            while (recent.size > 40) recent.removeLast()
                        }
                        captureOutput?.flush()
                        captureTextOutput?.flush()
                    }
                } catch (e: Exception) {
                    status.text = "Final drain warning: ${e.message}"
                } finally {
                    closeCaptureFile()
                    closeTextCaptureFile()
                    toggle.text = "Start full capture"
                    export.isEnabled = captureFile?.exists() == true
                    copyAll.isEnabled = captureTextFile?.exists() == true
                    analyze.isEnabled = captureFile?.exists() == true
                    status.text = "Capture stopped. One continuous PCAP and packet summary are saved."
                    summary.text = "Packets saved: $packetCount | dropped copies: " +
                        CoreServiceManager.getAlbionPacketTapDropped() +
                        " | file: " + (captureFile?.length() ?: 0L) / 1024 + " KB"
                    stopping = false
                }
            }
        }

        if (finishingJob != null) {
            finishingJob.invokeOnCompletion { finalize() }
        } else {
            finalize()
        }
    }

    private fun closeTextCaptureFile() {
        try {
            captureTextOutput?.flush()
            captureTextOutput?.close()
        } catch (_: Exception) {
        }
        captureTextOutput = null
    }

    private fun analyzeCapture() {
        val file = captureFile
        if (tapping || file == null || !file.exists()) {
            status.text = "Stop the capture before analyzing it."
            return
        }
        analyze.isEnabled = false
        status.text = "Analyzing UDP payload patterns..."
        lifecycleScope.launch {
            try {
                val report = withContext(Dispatchers.IO) {
                    AlbionLearningEngine.analyze(file)
                }
                packets.text = report
                status.text = "Analysis complete. This is a pattern-discovery report, not a decoded game map."
            } catch (e: Exception) {
                status.text = "Analysis failed: ${e.message}"
            } finally {
                analyze.isEnabled = captureFile?.exists() == true
            }
        }
    }

    private fun copyAllPackets() {
        val file = captureTextFile
        if (file == null || !file.exists()) {
            status.text = "No packet text is available to copy."
            return
        }
        lifecycleScope.launch {
            try {
                val text = withContext(Dispatchers.IO) {
                    captureTextOutput?.flush()
                    file.readText()
                }
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("Albion captured packets", text))
                status.text = "Copied ${text.lineSequence().count().coerceAtLeast(1) - 1} packet lines to clipboard."
            } catch (e: Exception) {
                status.text = "Copy failed: ${e.message}"
            }
        }
    }

    private fun closeCaptureFile() {
        try {
            captureOutput?.flush()
            captureOutput?.close()
        } catch (_: Exception) {
        }
        captureOutput = null
    }

    private fun exportActivityTags() {
        val file = File(filesDir, "AlbionRadar/activity-events.jsonl")
        if (!file.exists()) {
            status.text = "No activity tags recorded yet. Start the overlay and record an event first."
            return
        }
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/json"
            putExtra(Intent.EXTRA_TITLE, "albion-activity-events.jsonl")
        }
        startActivityForResult(intent, CREATE_TAGS_REQUEST)
    }

    private fun exportCapture() {
        val file = captureFile
        if (file == null || !file.exists()) {
            status.text = "No capture file is available to export."
            return
        }
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/vnd.tcpdump.pcap"
            putExtra(Intent.EXTRA_TITLE, file.name)
        }
        startActivityForResult(intent, CREATE_PCAP_REQUEST)
    }

    @Deprecated("Use Activity Result APIs; retained for compatibility with this diagnostic activity.")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != Activity.RESULT_OK) return
        val uri: Uri = data?.data ?: return
        if (requestCode == CREATE_TAGS_REQUEST) {
            val tagsFile = File(filesDir, "AlbionRadar/activity-events.jsonl")
            if (!tagsFile.exists()) return
            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    contentResolver.openOutputStream(uri, "w")?.use { output ->
                        FileInputStream(tagsFile).use { input -> input.copyTo(output) }
                    } ?: throw IllegalStateException("Could not open destination")
                    withContext(Dispatchers.Main) { status.text = "Activity tags exported successfully." }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) { status.text = "Tag export failed: ${e.message}" }
                }
            }
            return
        }
        if (requestCode != CREATE_PCAP_REQUEST) return
        val file = captureFile
        if (file == null) return
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                contentResolver.openOutputStream(uri, "w")?.use { output ->
                    FileInputStream(file).use { input -> input.copyTo(output, 256 * 1024) }
                } ?: throw IllegalStateException("Could not open destination")
                withContext(Dispatchers.Main) {
                    status.text = "PCAP exported successfully."
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    status.text = "Export failed: ${e.message}"
                }
            }
        }
    }

    private fun writePcapHeader(out: DataOutputStream) {
        out.writeInt(Integer.reverseBytes(0xa1b2c3d4L.toInt()))
        out.writeShort(java.lang.Short.reverseBytes(2.toShort()).toInt())
        out.writeShort(java.lang.Short.reverseBytes(4.toShort()).toInt())
        out.writeInt(0)
        out.writeInt(0)
        out.writeInt(Integer.reverseBytes(65535))
        out.writeInt(Integer.reverseBytes(101)) // LINKTYPE_RAW: raw IP, IPv4/IPv6
    }

    private fun writePcapRecord(out: DataOutputStream, packet: ByteArray, timestampMs: Long) {
        val seconds = (timestampMs / 1000L).toInt()
        val micros = ((timestampMs % 1000L) * 1000L).toInt()
        out.writeInt(Integer.reverseBytes(seconds))
        out.writeInt(Integer.reverseBytes(micros))
        out.writeInt(Integer.reverseBytes(packet.size))
        out.writeInt(Integer.reverseBytes(packet.size))
        out.write(packet)
    }

    private fun describePacket(p: ByteArray): String {
        if (p.isEmpty()) return "Empty packet"
        return try {
            val version = (p[0].toInt() ushr 4) and 0x0f
            if (version == 4 && p.size >= 20) {
                val ihl = (p[0].toInt() and 0x0f) * 4
                if (ihl < 20 || p.size < ihl) return "Malformed IPv4 packet (${p.size} bytes)"
                val proto = p[9].toInt() and 0xff
                val src = InetAddress.getByAddress(p.copyOfRange(12, 16)).hostAddress
                val dst = InetAddress.getByAddress(p.copyOfRange(16, 20)).hostAddress
                val transport = protocolName(proto)
                val endpoints = if ((proto == 6 || proto == 17) && p.size >= ihl + 4) {
                    "$src:${u16(p, ihl)} → $dst:${u16(p, ihl + 2)}"
                } else "$src → $dst"
                "$transport $endpoints (${p.size} B)"
            } else if (version == 6 && p.size >= 40) {
                val src = InetAddress.getByAddress(p.copyOfRange(8, 24)).hostAddress
                val dst = InetAddress.getByAddress(p.copyOfRange(24, 40)).hostAddress
                val next = p[6].toInt() and 0xff
                "${protocolName(next)} $src → $dst (${p.size} B)"
            } else "Unknown IP packet (${p.size} B)"
        } catch (_: Exception) {
            "Could not parse packet (${p.size} B)"
        }
    }

    private fun protocolName(proto: Int): String = when (proto) {
        6 -> "TCP"
        17 -> "UDP"
        1 -> "ICMP"
        58 -> "ICMPv6"
        else -> "IP/$proto"
    }

    private fun u16(p: ByteArray, offset: Int): Int =
        ((p[offset].toInt() and 0xff) shl 8) or (p[offset + 1].toInt() and 0xff)

    override fun onDestroy() {
        try { unregisterReceiver(markerReceiver) } catch (_: Exception) {}
        if (isFinishing && tapping) stopCapture()
        super.onDestroy()
    }
}
