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
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.NotificationCompat
import org.json.JSONObject
import com.v2ray.ang.ui.albion.AlbionMiniRadarView
import com.v2ray.ang.albion.AlbionCaptureMarker
import com.v2ray.ang.core.CoreServiceManager

/** Small local-only activity tagger; game packets are never modified or transmitted. */
class AlbionOverlayService : Service() {
    private lateinit var wm: WindowManager
    private var root: LinearLayout? = null
    private var expanded = false
    private var activityIndex = 0
    private var tierIndex = 2
    private var enchantIndex = 0
    private lateinit var title: TextView
    private lateinit var body: LinearLayout
    private lateinit var liveStatus: TextView
    private lateinit var radarView: AlbionMiniRadarView
    private val refreshHandler = Handler(Looper.getMainLooper())
    private val refreshLiveSnapshot = object : Runnable {
        override fun run() {
            if (root == null) return
            if (::liveStatus.isInitialized) liveStatus.text = CoreServiceManager.getAlbionLiveKnowledgeSnapshot()
            if (::radarView.isInitialized) radarView.setSnapshot(CoreServiceManager.getAlbionLiveKnowledgeJson())
            refreshHandler.postDelayed(this, 1500L)
        }
    }
    private val activities = listOf("Ore", "Wood", "Stone", "Fiber", "Hide", "Mobs", "PvP", "Travel", "Other")
    private val tiers = listOf("T2","T3","T4","T5","T6","T7","T8")
    private val enchants = listOf("Normal","E1","E2","E3")

    override fun onBind(intent: Intent?): IBinder? = null
    override fun onCreate() {
        super.onCreate()
        val channel = "albion_activity_tags"
        val nm = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) nm.createNotificationChannel(NotificationChannel(channel, "Albion tags", NotificationManager.IMPORTANCE_LOW))
        val notification: Notification = NotificationCompat.Builder(this, channel)
            .setSmallIcon(com.v2ray.ang.R.drawable.ic_stat_name)
            .setContentTitle("Albion activity tags").setContentText("Compact overlay active").setOngoing(true).build()
        if (Build.VERSION.SDK_INT >= 34) startForeground(7331, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        else startForeground(7331, notification)
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        if (Settings.canDrawOverlays(this)) { show(); tag("overlay_started") } else stopSelf()
    }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) stopSelf()
        return START_STICKY
    }
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun button(label: String, action: () -> Unit) = Button(this).apply {
        text = label; textSize = 10f; isAllCaps = false
        minWidth = 0; minimumWidth = 0; minHeight = 0; minimumHeight = 0
        setPadding(dp(2), 0, dp(2), 0); setOnClickListener { action() }
    }
    private fun show() {
        if (root != null) return
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(5), dp(4), dp(5), dp(4))
            background = GradientDrawable().apply { setColor(0xE9162029.toInt()); cornerRadius = dp(9).toFloat(); setStroke(dp(1), 0x885F7788.toInt()) }
        }
        val head = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        head.addView(button("‹") { stepActivity(-1) }, LinearLayout.LayoutParams(dp(28), dp(32)))
        title = TextView(this).apply { text = "Ore"; setTextColor(-1); textSize = 12f; typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER_VERTICAL }
        head.addView(title, LinearLayout.LayoutParams(0, dp(32), 1f))
        head.addView(button("›") { stepActivity(1) }, LinearLayout.LayoutParams(dp(28), dp(32)))
        head.addView(button(if (expanded) "−" else "+") { expanded = !expanded; tag(if (expanded) "overlay_expanded" else "overlay_collapsed"); renderBody() }, LinearLayout.LayoutParams(dp(28), dp(32)))
        head.addView(button("×") { tag("overlay_closed"); stopSelf() }, LinearLayout.LayoutParams(dp(28), dp(32)))
        panel.addView(head)
        body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        radarView = AlbionMiniRadarView(this)
        liveStatus = TextView(this).apply {
            text = "Live entities: waiting for capture"
            setTextColor(0xFFE3EDF5.toInt())
            textSize = 10f
            typeface = Typeface.MONOSPACE
            setPadding(dp(3), dp(3), dp(3), dp(3))
        }
        panel.addView(body)
        root = panel
        val type = if (Build.VERSION.SDK_INT >= 26) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE
        val params = WindowManager.LayoutParams(dp(176), WindowManager.LayoutParams.WRAP_CONTENT, type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT).apply { gravity = Gravity.TOP or Gravity.START; x = dp(4); y = dp(105) }
        wm.addView(panel, params)
        renderBody()
        refreshHandler.removeCallbacks(refreshLiveSnapshot)
        refreshHandler.post(refreshLiveSnapshot)
    }
    private fun stepActivity(delta: Int) { activityIndex = (activityIndex + delta + activities.size) % activities.size; title.text = activities[activityIndex]; tag("activity_selected"); renderBody() }
    private fun renderBody() {
        if (!::body.isInitialized) return
        body.removeAllViews()
        if (!expanded) {
            body.visibility = android.view.View.VISIBLE
            liveStatus.maxLines = 2
            body.addView(radarView, LinearLayout.LayoutParams(-1, dp(128)))
            body.addView(liveStatus)
            return
        }
        body.visibility = android.view.View.VISIBLE
        body.addView(radarView, LinearLayout.LayoutParams(-1, dp(128)))
        val selectors = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        selectors.addView(button(tiers[tierIndex]) { tierIndex = (tierIndex + 1) % tiers.size; tag("tier_selected"); renderBody() }, LinearLayout.LayoutParams(0, dp(30), 1f))
        selectors.addView(button(enchants[enchantIndex]) { enchantIndex = (enchantIndex + 1) % enchants.size; tag("enchantment_selected"); renderBody() }, LinearLayout.LayoutParams(0, dp(30), 1f))
        body.addView(selectors)
        val events = when (activities[activityIndex]) {
            "Ore","Wood","Stone","Fiber","Hide" -> listOf("Found" to "target_found", "Start" to "gathering_started", "Done" to "gathering_completed", "Empty" to "node_depleted")
            "Mobs" -> listOf("Seen" to "target_found", "Killed" to "mob_killed")
            "PvP" -> listOf("Player" to "player_spotted", "Fight" to "pvp_started", "End" to "pvp_ended")
            else -> listOf("Start" to "activity_start", "Event" to "activity_event", "End" to "activity_end")
        }
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        events.forEach { (label, event) -> row.addView(button(label) { tag(event) }, LinearLayout.LayoutParams(0, dp(32), 1f)) }
        body.addView(row)
        liveStatus.maxLines = 8
        body.addView(liveStatus)
    }
    private fun tag(event: String) {
        val selectedActivity = activities[activityIndex]
        try {
            val now = System.currentTimeMillis()
            val json = JSONObject().apply {
                put("timestamp_ms", now); put("elapsed_realtime_ms", android.os.SystemClock.elapsedRealtime())
                put("event", event); put("activity", selectedActivity); put("tier", tiers[tierIndex])
                put("enchantment", enchants[enchantIndex]); put("source", "manual_overlay")
            }.toString()
            val saved = CoreServiceManager.appendAlbionCaptureMarker(
                AlbionCaptureMarker.build(json), now
            )
            title.text = if (saved) "$selectedActivity ✓" else "$selectedActivity !"
            if (!saved) Toast.makeText(this, "Marker NOT saved. Start capture and check VPN connection.", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            title.text = "$selectedActivity !"
            Toast.makeText(this, "Marker NOT saved: ${e.message ?: "unknown error"}", Toast.LENGTH_LONG).show()
        }
    }
    override fun onDestroy() {
        refreshHandler.removeCallbacks(refreshLiveSnapshot)
        try { root?.let { wm.removeView(it) } } catch (_: Exception) { }
        root = null; stopForeground(STOP_FOREGROUND_REMOVE); super.onDestroy()
    }
    companion object {
        const val ACTION_MARKER = "com.v2ray.ang.albion.PCAP_MARKER"
        const val EXTRA_MARKER = "marker_json"
        const val ACTION_STOP = "com.v2ray.ang.albion.STOP_OVERLAY"
    }
}
