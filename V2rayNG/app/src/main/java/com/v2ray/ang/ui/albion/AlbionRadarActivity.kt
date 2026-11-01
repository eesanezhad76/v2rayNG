package com.v2ray.ang.ui.albion

import android.app.Activity
import android.content.Intent
import android.content.ClipboardManager
import android.content.ClipData
import android.content.Context
import android.content.ContentValues
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import android.os.Environment
import android.provider.MediaStore
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
import kotlinx.coroutines.cancel
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

/** Passive diagnostic capture. Every packet delivered by the native tap is written to PCAP. */
class AlbionRadarActivity : ComponentActivity() {
    private lateinit var status: TextView
    private lateinit var summary: TextView
    private lateinit var packets: TextView
    private lateinit var analysis: TextView
    private lateinit var toggle: Button
    private lateinit var export: Button
    private lateinit var copyAll: Button
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
            text = "Copy all packets"
            isEnabled = false
            setOnClickListener { copyAllPackets() }
        }
        analysis = TextView(this).apply {
            textSize = 13f
            typeface = android.graphics.Typeface.MONOSPACE
            text = "After stopping capture, transport analysis appears here."
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
        root.addView(analysis, LinearLayout.LayoutParams(-1, -2).apply { topMargin = 12 })
        root.addView(
            ScrollView(this).apply { addView(packets) },
            LinearLayout.LayoutParams(-1, 0, 1f).apply { topMargin = 12 }
        )
        setContentView(root)
        val existingCapture = CoreServiceManager.getAlbionCaptureSnapshot()
        if (existingCapture?.running == true) {
            tapping = true
            toggle.text = "Stop capture"
            captureFile = existingCapture.pcap
            captureTextFile = existingCapture.log
            packetCount = existingCapture.packets
            summary.text = "Packets saved: ${existingCapture.packets} | dropped copies: ${existingCapture.dropped}"
            status.text = "Capture is already running in the VPN daemon."
            observeCapture()
        }
    }

    private fun observeCapture() {
        pollJob?.cancel()
        pollJob = captureScope.launch {
            while (isActive && tapping) {
                val s = CoreServiceManager.getAlbionCaptureSnapshot()
                if (s != null) {
                    captureFile = s.pcap
                    captureTextFile = s.log
                    packetCount = s.packets
                    summary.text = "Packets saved: ${s.packets} | dropped copies: ${s.dropped} | file: ${(s.pcap?.length() ?: 0L) / 1024} KB"
                    if (!s.running && s.finished && tapping) {
                        tapping = false
                        toggle.text = "Start full capture"
                        export.isEnabled = s.pcap?.exists() == true
                        copyAll.isEnabled = s.log?.exists() == true
                        status.text = if (s.error != null)
                            "Capture stopped with an error: ${s.error}. Existing files were finalized."
                        else
                            "Capture stopped unexpectedly. Existing files were finalized."
                    } else if (s.error != null) {
                        status.text = "Capture writer error: ${s.error}"
                    }
                }
                delay(500)
            }
        }
    }

    private fun startCapture() {
        if (!CoreServiceManager.isRunning()) {
            status.text = "Start V2RayNG and connect first."
            return
        }
        if (!CoreServiceManager.startAlbionCapture()) {
            status.text = "Could not start daemon-owned capture. Check VPN state and packet tap."
            return
        }
        tapping = true
        toggle.text = "Stop capture"
        export.isEnabled = false
        copyAll.isEnabled = false
        status.text = "Capture runs in the VPN daemon. It continues when this screen is closed."
        observeCapture()
    }

    private fun stopCapture() {
        if (!tapping || stopping) return
        tapping = false
        stopping = true
        pollJob?.cancel()
        pollJob = null
        CoreServiceManager.stopAlbionCapture()
        captureScope.launch {
            var waited = 0
            var s = CoreServiceManager.getAlbionCaptureSnapshot()
            while (s != null && !s.finished && waited < 30000) {
                delay(250)
                waited += 250
                s = CoreServiceManager.getAlbionCaptureSnapshot()
            }
            captureFile = s?.pcap ?: captureFile
            captureTextFile = s?.log ?: captureTextFile
            packetCount = s?.packets ?: packetCount
            toggle.text = "Start full capture"
            export.isEnabled = captureFile?.exists() == true
            copyAll.isEnabled = captureTextFile?.exists() == true
            status.text = "Capture stopped. Files are saved in the app's AlbionCaptures directory."
            publishCaptureToDownloads()
            captureFile?.takeIf { it.exists() }?.let { file ->
                try {
                    analysis.text = withContext(Dispatchers.IO) {
                        com.v2ray.ang.albion.AlbionCaptureAnalyzer.analyze(file).asText()
                    }
                } catch (e: Exception) {
                    analysis.text = "Analysis failed: ${e.message}"
                }
            }
            summary.text = "Packets saved: $packetCount | dropped copies: ${s?.dropped ?: 0} | file: ${(captureFile?.length() ?: 0L) / 1024} KB"
            stopping = false
        }
    }

    /**
     * Copies completed captures into public Downloads using MediaStore. This works
     * on Android 10+ without requesting broad storage permissions.
     */
    private fun publishCaptureToDownloads() {
        val pcap = captureFile
        val log = captureTextFile
        captureScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI
                    fun publish(source: File, mime: String) {
                        val values = ContentValues().apply {
                            put(MediaStore.MediaColumns.DISPLAY_NAME, source.name)
                            put(MediaStore.MediaColumns.MIME_TYPE, mime)
                            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/AlbionCaptures")
                            put(MediaStore.MediaColumns.IS_PENDING, 1)
                        }
                        val uri = contentResolver.insert(collection, values)
                            ?: throw IllegalStateException("MediaStore insert failed for ${source.name}")
                        try {
                            contentResolver.openOutputStream(uri, "w")?.use { output ->
                                FileInputStream(source).use { input -> input.copyTo(output, 256 * 1024) }
                            } ?: throw IllegalStateException("Cannot open Downloads output for ${source.name}")
                            val done = ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }
                            contentResolver.update(uri, done, null, null)
                        } catch (e: Exception) {
                            contentResolver.delete(uri, null, null)
                            throw e
                        }
                    }
                    pcap?.takeIf { it.exists() }?.let { publish(it, "application/vnd.tcpdump.pcap") }
                    log?.takeIf { it.exists() }?.let { publish(it, "text/plain") }
                }
                status.text = "Saved to Downloads/AlbionCaptures. PCAP and packet log are available in Files."
            } catch (e: Exception) {
                status.text = "Private capture saved, but publishing to Downloads failed: ${e.message}"
            }
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
        if (requestCode != CREATE_PCAP_REQUEST || resultCode != Activity.RESULT_OK) return
        val uri: Uri = data?.data ?: return
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
        // Stop only this screen's observer; daemon-owned capture remains active.
        pollJob?.cancel()
        captureScope.cancel()
        super.onDestroy()
    }
}
