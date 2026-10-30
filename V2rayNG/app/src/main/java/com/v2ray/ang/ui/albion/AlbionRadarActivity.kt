package com.v2ray.ang.ui.albion

import androidx.activity.ComponentActivity
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import com.v2ray.ang.core.CoreServiceManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.InetAddress

/** Diagnostic screen for validating passive packet observation on Android TUN. */
class AlbionRadarActivity : ComponentActivity() {
    private lateinit var status: TextView
    private lateinit var summary: TextView
    private lateinit var packets: TextView
    private lateinit var toggle: Button
    private var pollJob: Job? = null
    private var packetCount = 0L
    private val recent = ArrayDeque<String>()
    private var tapping = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "Albion packet monitor"
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 20, 24, 20)
        }
        status = TextView(this).apply { textSize = 18f; text = "Waiting for V2RayNG connection" }
        summary = TextView(this).apply { textSize = 14f; text = "Packets: 0 | dropped copies: 0" }
        toggle = Button(this).apply {
            text = "Start passive capture"
            setOnClickListener { if (tapping) stopCapture() else startCapture() }
        }
        packets = TextView(this).apply {
            textSize = 13f
            typeface = android.graphics.Typeface.MONOSPACE
            text = "No packets captured yet."
        }
        root.addView(status, LinearLayout.LayoutParams(-1, -2))
        root.addView(summary, LinearLayout.LayoutParams(-1, -2).apply { topMargin = 12 })
        root.addView(toggle, LinearLayout.LayoutParams(-1, -2).apply { topMargin = 12 })
        root.addView(ScrollView(this).apply { addView(packets) },
            LinearLayout.LayoutParams(-1, 0, 1f).apply { topMargin = 12 })
        setContentView(root)
    }

    private fun startCapture() {
        if (!CoreServiceManager.isRunning()) {
            status.text = "Start V2RayNG and connect first."
            return
        }
        if (!CoreServiceManager.enableAlbionPacketTap(64)) {
            status.text = "Could not enable packet tap. Check that the custom AAR is installed."
            return
        }
        tapping = true
        toggle.text = "Stop passive capture"
        status.text = "Capturing IP packet copies from TUN. Game payloads are not decrypted."
        pollJob = lifecycleScope.launch {
            while (isActive && tapping) {
                val batch = withContext(Dispatchers.IO) {
                    val result = ArrayList<String>()
                    repeat(64) {
                        val packet = CoreServiceManager.readAlbionPacketTap() ?: return@repeat
                        packetCount++
                        result.add(describePacket(packet))
                    }
                    result
                }
                if (batch.isNotEmpty()) {
                    batch.forEach {
                        recent.addFirst(it)
                        while (recent.size > 40) recent.removeLast()
                    }
                    packets.text = recent.joinToString("\n")
                }
                summary.text = "Packets: $packetCount | dropped copies: " + CoreServiceManager.getAlbionPacketTapDropped()
                delay(60)
            }
        }
    }

    private fun stopCapture() {
        tapping = false
        pollJob?.cancel()
        pollJob = null
        CoreServiceManager.disableAlbionPacketTap()
        toggle.text = "Start passive capture"
        status.text = "Capture stopped."
    }

    private fun describePacket(p: ByteArray): String {
        if (p.isEmpty()) return "Empty packet"
        return try {
            val version = (p[0].toInt() ushr 4) and 0x0f
            if (version == 4 && p.size >= 20) {
                val ihl = (p[0].toInt() and 0x0f) * 4
                if (ihl < 20 || p.size < ihl) return "Malformed IPv4 packet (" + p.size + " bytes)"
                val proto = p[9].toInt() and 0xff
                val src = InetAddress.getByAddress(p.copyOfRange(12, 16)).hostAddress
                val dst = InetAddress.getByAddress(p.copyOfRange(16, 20)).hostAddress
                val transport = when (proto) { 6 -> "TCP"; 17 -> "UDP"; 1 -> "ICMP"; else -> "IP/" + proto }
                val ports = if ((proto == 6 || proto == 17) && p.size >= ihl + 4) {
                    ":" + u16(p, ihl) + " → :" + u16(p, ihl + 2)
                } else ""
                "$transport $src$ports → $dst (" + p.size + " B)"
            } else if (version == 6 && p.size >= 40) {
                val src = InetAddress.getByAddress(p.copyOfRange(8, 24)).hostAddress
                val dst = InetAddress.getByAddress(p.copyOfRange(24, 40)).hostAddress
                val next = p[6].toInt() and 0xff
                val transport = when (next) { 6 -> "TCP"; 17 -> "UDP"; 58 -> "ICMPv6"; else -> "IPv6/" + next }
                "$transport $src → $dst (" + p.size + " B; IPv6 ports not decoded)"
            } else "Unknown IP packet (" + p.size + " B)"
        } catch (_: Exception) {
            "Could not parse packet (" + p.size + " B)"
        }
    }

    private fun u16(p: ByteArray, offset: Int): Int =
        ((p[offset].toInt() and 0xff) shl 8) or (p[offset + 1].toInt() and 0xff)

    override fun onDestroy() {
        stopCapture()
        super.onDestroy()
    }
}
