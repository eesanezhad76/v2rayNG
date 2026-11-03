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
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.NotificationCompat
import org.json.JSONObject
import com.v2ray.ang.albion.AlbionCaptureMarker
import com.v2ray.ang.core.CoreServiceManager

/** Compact, direct-choice local activity tagger. It never modifies game traffic. */
class AlbionOverlayService : Service() {
    private lateinit var wm: WindowManager
    private var root: LinearLayout? = null
    private lateinit var radarView: AlbionMiniRadarView
    private var windowParams: WindowManager.LayoutParams? = null
    private var expanded = true
    private var activity = "Ore"
    private var tier = "T3"
    private var enchant = "Normal"
    private lateinit var title: TextView
    private lateinit var body: LinearLayout
    private lateinit var liveStatus: TextView
    private val refreshHandler = Handler(Looper.getMainLooper())
    private var dragStartX = 0f
    private var dragStartY = 0f
    private var dragWindowX = 0
    private var dragWindowY = 0
    private val refreshLiveSnapshot = object : Runnable {
        override fun run() {
            if (root == null) return
            val snapshot = CoreServiceManager.getAlbionLiveKnowledgeJson()
            if (::radarView.isInitialized) radarView.setSnapshot(snapshot)
            if (::liveStatus.isInitialized) liveStatus.text = CoreServiceManager.getAlbionLiveKnowledgeSnapshot()
            refreshHandler.postDelayed(this, 1200L)
        }
    }
    private val activities = listOf("Ore", "Wood", "Stone", "Fiber", "Hide", "Mobs", "Troll", "PvP", "Move", "Other")
    private val tiers = (2..8).map { "T$it" }
    private val enchants = listOf("Normal", "E1", "E2", "E3")

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
        if (Settings.canDrawOverlays(this)) show() else stopSelf()
    }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) stopSelf()
        return START_STICKY
    }
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun button(label: String, selected: Boolean = false, action: () -> Unit) = Button(this).apply {
        text = label
        textSize = 10f
        isAllCaps = false
        minWidth = 0; minimumWidth = 0; minHeight = 0; minimumHeight = 0
        setPadding(dp(3), 0, dp(3), 0)
        if (selected) setTextColor(0xFF80D8FF.toInt())
        setOnClickListener { action() }
    }
    private fun choiceRow(values: List<String>, selected: String, onPick: (String) -> Unit): HorizontalScrollView {
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        values.forEach { value ->
            row.addView(button(value, value == selected) { onPick(value) },
                LinearLayout.LayoutParams(-2, dp(34)).apply { rightMargin = dp(3) })
        }
        return HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            addView(row)
        }
    }
    private fun show() {
        if (root != null) return
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(5), dp(4), dp(5), dp(4))
            background = GradientDrawable().apply {
                setColor(0xE9162029.toInt()); cornerRadius = dp(9).toFloat()
                setStroke(dp(1), 0x885F7788.toInt())
            }
        }
        val head = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        title = TextView(this).apply {
            text = "Albion • $activity"; setTextColor(-1); textSize = 12f
            typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER_VERTICAL
        }
        head.addView(title, LinearLayout.LayoutParams(0, dp(32), 1f))
        head.addView(button("−") { expanded = !expanded; renderBody() }, LinearLayout.LayoutParams(dp(32), dp(32)))
        head.addView(button("×") { stopSelf() }, LinearLayout.LayoutParams(dp(32), dp(32)))
        panel.addView(head)
        radarView = AlbionMiniRadarView(this)
        panel.addView(radarView, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(176)))
        body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        liveStatus = TextView(this).apply {
            text = "Automatic capture learning: waiting for capture"
            setTextColor(0xFFE3EDF5.toInt()); textSize = 9f
            typeface = Typeface.MONOSPACE; setPadding(dp(3), dp(3), dp(3), dp(3))
            maxLines = 3
        }
        panel.addView(body)
        root = panel
        val type = if (Build.VERSION.SDK_INT >= 26) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE
        val params = WindowManager.LayoutParams(dp(290), WindowManager.LayoutParams.WRAP_CONTENT, type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT).apply { gravity = Gravity.TOP or Gravity.START; x = dp(4); y = dp(105) }
        windowParams = params
        wm.addView(panel, params)
        title.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> { dragStartX = event.rawX; dragStartY = event.rawY; dragWindowX = params.x; dragWindowY = params.y; true }
                MotionEvent.ACTION_MOVE -> {
                    params.x = dragWindowX + (event.rawX - dragStartX).toInt()
                    params.y = dragWindowY + (event.rawY - dragStartY).toInt()
                    try { wm.updateViewLayout(panel, params) } catch (_: Exception) { }
                    true
                }
                else -> false
            }
        }
        renderBody()
        refreshHandler.removeCallbacks(refreshLiveSnapshot)
        refreshHandler.post(refreshLiveSnapshot)
    }
    private fun renderBody() {
        if (!::body.isInitialized) return
        body.removeAllViews()
        if (!expanded) {
            body.addView(liveStatus)
            return
        }
        // Row 1: choose an activity directly; no cycling through categories.
        body.addView(label("1 • Activity"))
        body.addView(choiceRow(activities, activity) { activity = it; renderBody() })
        title.text = "Albion • $activity"
        if (activity != "Mobs" && activity != "PvP" && activity != "Move" && activity != "Other") {
            body.addView(label("2 • Tier"))
            body.addView(choiceRow(tiers, tier) { tier = it; renderBody() })
            body.addView(label("3 • Enchantment"))
            body.addView(choiceRow(enchants, enchant) { enchant = it; renderBody() })
        }
        val actions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        when (activity) {
            "Ore", "Wood", "Stone", "Fiber", "Hide" -> {
                actions.addView(button("Start") { tag("target_found") }, LinearLayout.LayoutParams(0, dp(38), 1f))
                actions.addView(button("Stop") { tag("gathering_completed") }, LinearLayout.LayoutParams(0, dp(38), 1f))
            }
            "Mobs", "Troll" -> {
                actions.addView(button("Start") { tag("target_found") }, LinearLayout.LayoutParams(0, dp(38), 1f))
                actions.addView(button("Killed") { tag("mob_killed") }, LinearLayout.LayoutParams(0, dp(38), 1f))
            }
            "Move" -> {
                actions.addView(button("Start movement test") { tag("movement_started") }, LinearLayout.LayoutParams(0, dp(38), 1f))
                actions.addView(button("Stop movement test") { tag("movement_completed") }, LinearLayout.LayoutParams(0, dp(38), 1f))
            }
            "PvP" -> {
                actions.addView(button("Start") { tag("pvp_started") }, LinearLayout.LayoutParams(0, dp(38), 1f))
                actions.addView(button("Stop") { tag("pvp_ended") }, LinearLayout.LayoutParams(0, dp(38), 1f))
            }
            else -> {
                actions.addView(button("Start") { tag("activity_start") }, LinearLayout.LayoutParams(0, dp(38), 1f))
                actions.addView(button("Stop") { tag("activity_end") }, LinearLayout.LayoutParams(0, dp(38), 1f))
            }
        }
        body.addView(actions)
        body.addView(liveStatus)
    }
    private fun label(value: String) = TextView(this).apply {
        text = value; setTextColor(0xFFB8CAD8.toInt()); textSize = 9f
        setPadding(dp(2), dp(3), 0, 0)
    }
    private fun tag(event: String) {
        val now = System.currentTimeMillis()
        try {
            val json = JSONObject().apply {
                put("timestamp_ms", now)
                put("elapsed_realtime_ms", android.os.SystemClock.elapsedRealtime())
                put("event", event); put("activity", activity)
                if (activity in setOf("Ore", "Wood", "Stone", "Fiber", "Hide", "Troll")) {
                    put("tier", tier); put("enchantment", enchant)
                }
                put("source", "manual_overlay")
            }.toString()
            val saved = CoreServiceManager.appendAlbionCaptureMarker(AlbionCaptureMarker.build(json), now)
            title.text = if (saved) "Albion • $activity ✓" else "Albion • $activity !"
            if (!saved) Toast.makeText(this, "Marker not saved. Capture must be running.", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            title.text = "Albion • $activity !"
            Toast.makeText(this, "Marker error: ${e.message ?: "unknown"}", Toast.LENGTH_LONG).show()
        }
    }
    override fun onDestroy() {
        refreshHandler.removeCallbacks(refreshLiveSnapshot)
        try { root?.let { wm.removeView(it) } } catch (_: Exception) { }
        root = null
        windowParams = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }
    companion object {
        const val ACTION_MARKER = "com.v2ray.ang.albion.PCAP_MARKER"
        const val EXTRA_MARKER = "marker_json"
        const val ACTION_STOP = "com.v2ray.ang.albion.STOP_OVERLAY"
    }
}
