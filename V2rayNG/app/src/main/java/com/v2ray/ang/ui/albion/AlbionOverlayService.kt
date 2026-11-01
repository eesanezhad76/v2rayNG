package com.v2ray.ang.ui.albion

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.NotificationCompat
import org.json.JSONObject

/**
 * Local-only activity tagger. It never reads, changes, redirects or sends game traffic.
 * Activity tags are broadcast only to the active PCAP capture activity.
 */
class AlbionOverlayService : Service() {
    private lateinit var windowManager: WindowManager
    private var overlayView: android.view.View? = null
    private var expanded = true
    private lateinit var activityButton: Button
    private lateinit var tierButton: Button
    private lateinit var enchantButton: Button
    private var activityIndex = 0
    private var tierIndex = 2
    private var enchantIndex = 0

    private val activities = listOf("Ore mining", "Wood", "Stone", "Fiber", "Hide", "Mob combat", "PvP", "Travel", "Unknown")
    private val tiers = listOf("T2", "T3", "T4", "T5", "T6", "T7", "T8")
    private val enchants = listOf("Normal", "Enchanted I", "Enchanted II", "Enchanted III")

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startAsForeground()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        if (Settings.canDrawOverlays(this)) {
            showOverlay()
            logEvent("overlay_started")
        } else {
            stopSelf()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            logEvent("overlay_stopped")
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    private fun startAsForeground() {
        val channelId = "albion_activity_tags"
        val manager = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) {
            manager.createNotificationChannel(
                NotificationChannel(channelId, "Albion activity logger", NotificationManager.IMPORTANCE_LOW)
            )
        }
        val notification: Notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(com.v2ray.ang.R.drawable.ic_stat_name)
            .setContentTitle("Albion activity logger")
            .setContentText("Local activity tags are being saved")
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIFICATION_ID, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun showOverlay() {
        if (overlayView != null) return
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(10), dp(12), dp(10))
            background = GradientDrawable().apply {
                setColor(0xEE18232D.toInt())
                cornerRadius = dp(14).toFloat()
                setStroke(dp(1), 0xFF668092.toInt())
            }
        }
        val header = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val title = TextView(this).apply {
            text = "ALBION TAGGER"
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
        }
        header.addView(title, LinearLayout.LayoutParams(0, dp(38), 1f))
        header.addView(makeButton("—") { toggleExpanded() }, LinearLayout.LayoutParams(dp(44), dp(38)))
        header.addView(makeButton("×") { stopSelf() }, LinearLayout.LayoutParams(dp(44), dp(38)))
        panel.addView(header)

        val body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        activityButton = makeButton("") { activityIndex = (activityIndex + 1) % activities.size; refreshSelectors() }
        tierButton = makeButton("") { tierIndex = (tierIndex + 1) % tiers.size; refreshSelectors() }
        enchantButton = makeButton("") { enchantIndex = (enchantIndex + 1) % enchants.size; refreshSelectors() }
        body.addView(activityButton)
        body.addView(tierButton)
        body.addView(enchantButton)
        body.addView(makeButton("START selected activity") { logEvent("activity_start") })
        body.addView(makeButton("Found node / target") { logEvent("target_found") })
        body.addView(makeButton("Gathering started") { logEvent("gathering_started") })
        body.addView(makeButton("Gathering completed") { logEvent("gathering_completed") })
        body.addView(makeButton("Node depleted") { logEvent("node_depleted") })
        body.addView(makeButton("Mob killed") { logEvent("mob_killed") })
        body.addView(makeButton("Player spotted") { logEvent("player_spotted") })
        body.addView(makeButton("END selected activity") { logEvent("activity_end") })
        panel.addView(body)
        panel.addView(TextView(this).apply {
            text = "Tags saved locally; game traffic is untouched."
            setTextColor(0xFFB9C7D1.toInt())
            textSize = 10f
            setPadding(0, dp(6), 0, 0)
        })
        overlayView = panel
        val type = if (Build.VERSION.SDK_INT >= 26) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE
        val params = WindowManager.LayoutParams(
            dp(270), WindowManager.LayoutParams.WRAP_CONTENT, type,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.START; x = dp(8); y = dp(120) }
        windowManager.addView(panel, params)
        refreshSelectors()
    }

    private fun makeButton(label: String, action: () -> Unit): Button = Button(this).apply {
        text = label
        textSize = 11f
        isAllCaps = false
        minHeight = dp(38)
        setPadding(dp(4), 0, dp(4), 0)
        setOnClickListener { action() }
    }

    private fun refreshSelectors() {
        if (!::activityButton.isInitialized) return
        activityButton.text = "Activity: ${activities[activityIndex]}  ›"
        tierButton.text = "Tier: ${tiers[tierIndex]}  ›"
        enchantButton.text = "Enchant: ${enchants[enchantIndex]}  ›"
    }

    private fun toggleExpanded() {
        val view = overlayView ?: return
        val parent = view as LinearLayout
        expanded = !expanded
        for (i in 1 until parent.childCount) parent.getChildAt(i).visibility = if (expanded) android.view.View.VISIBLE else android.view.View.GONE
    }

    private fun logEvent(event: String) {
        try {
            val record = JSONObject().apply {
                put("timestamp_ms", System.currentTimeMillis())
                put("elapsed_realtime_ms", android.os.SystemClock.elapsedRealtime())
                put("event", event)
                put("activity", activities[activityIndex])
                put("tier", tiers[tierIndex])
                put("enchantment", enchants[enchantIndex])
                put("source", "manual_overlay")
            }.toString()
            // Explicit app-local broadcast to the capture activity; never touches the VPN socket.
            sendBroadcast(Intent(ACTION_MARKER).setPackage(packageName).putExtra(EXTRA_MARKER, record))
        } catch (_: Exception) {
            // A failed annotation must never affect the VPN or game connection.
        }
    }

    override fun onDestroy() {
        try { overlayView?.let { windowManager.removeView(it) } } catch (_: Exception) {}
        overlayView = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    companion object {
        const val ACTION_STOP = "com.v2ray.ang.albion.STOP_OVERLAY"
        const val ACTION_MARKER = "com.v2ray.ang.albion.PCAP_MARKER"
        const val EXTRA_MARKER = "marker_json"
        private const val NOTIFICATION_ID = 7331
    }
}
