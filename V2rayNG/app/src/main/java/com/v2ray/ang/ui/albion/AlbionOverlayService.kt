package com.v2ray.ang.ui.albion

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.app.NotificationCompat
import com.v2ray.ang.core.CoreServiceManager
import com.v2ray.ang.albion.AlbionMarkerQueue
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.max
import kotlin.math.min

/** Compact local-only tagger. A tap/gesture is persisted through the daemon marker queue. */
class AlbionOverlayService : Service() {
    private lateinit var wm: WindowManager
    private var root: LinearLayout? = null
    private var expanded = false
    private var activityIndex = 0
    private var tierIndex = 2
    private var enchantIndex = 0
    private var nodeId: String? = null
    private lateinit var title: TextView
    private lateinit var body: LinearLayout
    private val activities = listOf("Ore", "Wood", "Stone", "Fiber", "Hide", "Mobs", "PvP", "Travel", "Other")
    private val tiers = listOf("T2", "T3", "T4", "T5", "T6", "T7", "T8")
    private val enchants = listOf("Normal", "E1", "E2", "E3")
    private val eventSequence = AtomicLong(0)

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
        if (Settings.canDrawOverlays(this)) { show(); record("overlay_started") } else stopSelf()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) stopSelf()
        return START_STICKY
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    // All hit targets are enlarged by approximately 20% while retaining the compact panel width.
    private fun button(label: String, action: () -> Unit) = Button(this).apply {
        text = label
        textSize = 12f
        isAllCaps = false
        minWidth = 0; minimumWidth = 0; minHeight = 0; minimumHeight = 0
        setPadding(dp(3), 0, dp(3), 0)
        setOnClickListener { action() }
    }

    private fun pickerButton(label: String, values: List<String>, selected: () -> Int, commit: (Int) -> Unit) =
        Button(this).apply {
            text = label
            textSize = 12f
            isAllCaps = false
            minWidth = 0; minimumWidth = 0; minHeight = 0; minimumHeight = 0
            setPadding(dp(2), 0, dp(2), 0)
            setOnTouchListener { view, event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        showSwipePicker(view, values, selected(), commit, event.rawY)
                        true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        updateSwipePicker(event.rawY)
                        true
                    }
                    MotionEvent.ACTION_UP -> {
                        finishSwipePicker(event.rawY, commit)
                        true
                    }
                    MotionEvent.ACTION_CANCEL -> {
                        dismissSwipePicker()
                        true
                    }
                    else -> true
                }
            }
        }

    private var activePopup: PopupWindow? = null
    private var popupRows: List<TextView> = emptyList()
    private var popupTop = 0
    private var popupRowHeight = 0
    private var popupIndex = -1
    private var popupCommit: ((Int) -> Unit)? = null

    private fun showSwipePicker(anchor: View, values: List<String>, initial: Int, commit: (Int) -> Unit, rawY: Float) {
        dismissSwipePicker()
        if (values.isEmpty()) return
        popupCommit = commit
        popupRowHeight = dp(38)
        popupIndex = initial.coerceIn(values.indices)
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                setColor(0xF21A2631.toInt()); cornerRadius = dp(10).toFloat()
                setStroke(dp(1), 0xCC7892A5.toInt())
            }
            setPadding(dp(3), dp(3), dp(3), dp(3))
        }
        popupRows = values.mapIndexed { index, value ->
            TextView(this).apply {
                text = value
                textSize = 14f
                gravity = Gravity.CENTER
                setTextColor(Color.WHITE)
                layoutParams = LinearLayout.LayoutParams(dp(124), popupRowHeight)
                content.addView(this)
            }
        }
        activePopup = PopupWindow(content, dp(130), popupRowHeight * values.size + dp(6), false).apply {
            isTouchable = false
            isOutsideTouchable = false
            elevation = dp(8).toFloat()
        }
        val location = IntArray(2)
        anchor.getLocationOnScreen(location)
        val screenHeight = resources.displayMetrics.heightPixels
        val desiredTop = rawY.toInt() - popupIndex * popupRowHeight - popupRowHeight / 2
        popupTop = desiredTop.coerceIn(dp(4), max(dp(4), screenHeight - popupRowHeight * values.size - dp(10)))
        val popupParent = root ?: return
        activePopup?.showAtLocation(popupParent, Gravity.TOP or Gravity.START, location[0], popupTop)
        updatePopupHighlight()
        updateSwipePicker(rawY)
    }

    private fun updateSwipePicker(rawY: Float) {
        val count = popupRows.size
        if (count == 0) return
        popupIndex = ((rawY.toInt() - popupTop - dp(3)) / popupRowHeight).coerceIn(0, count - 1)
        updatePopupHighlight()
    }

    private fun updatePopupHighlight() {
        popupRows.forEachIndexed { index, row ->
            row.setBackgroundColor(if (index == popupIndex) 0xFF42647A.toInt() else Color.TRANSPARENT)
            row.setTypeface(null, if (index == popupIndex) Typeface.BOLD else Typeface.NORMAL)
        }
    }

    private fun finishSwipePicker(rawY: Float, commit: (Int) -> Unit) {
        updateSwipePicker(rawY)
        val selected = popupIndex
        dismissSwipePicker()
        if (selected >= 0) commit(selected)
    }

    private fun dismissSwipePicker() {
        try { activePopup?.dismiss() } catch (_: Exception) { }
        activePopup = null
        popupRows = emptyList()
        popupIndex = -1
        popupCommit = null
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
        head.addView(button("‹") { stepActivity(-1) }, LinearLayout.LayoutParams(dp(34), dp(38)))
        title = TextView(this).apply {
            text = activities[activityIndex]; setTextColor(-1); textSize = 12f
            typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER_VERTICAL
        }
        head.addView(title, LinearLayout.LayoutParams(0, dp(38), 1f))
        head.addView(button("›") { stepActivity(1) }, LinearLayout.LayoutParams(dp(34), dp(38)))
        head.addView(button(if (expanded) "−" else "+") {
            expanded = !expanded
            record(if (expanded) "overlay_expanded" else "overlay_collapsed")
            renderBody()
        }, LinearLayout.LayoutParams(dp(34), dp(38)))
        head.addView(button("×") { record("overlay_closed"); stopSelf() }, LinearLayout.LayoutParams(dp(34), dp(38)))
        panel.addView(head)
        body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        panel.addView(body)
        root = panel
        val type = if (Build.VERSION.SDK_INT >= 26) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE
        val params = WindowManager.LayoutParams(
            dp(176), WindowManager.LayoutParams.WRAP_CONTENT, type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.START; x = dp(4); y = dp(105) }
        wm.addView(panel, params)
        renderBody()
    }

    private fun stepActivity(delta: Int) {
        activityIndex = (activityIndex + delta + activities.size) % activities.size
        title.text = activities[activityIndex]
        nodeId = null
        record("activity_selected")
        renderBody()
    }

    private fun selectActivity(index: Int) {
        if (index == activityIndex) return
        activityIndex = index
        title.text = activities[activityIndex]
        nodeId = null
        record("activity_selected")
        renderBody()
    }

    private fun renderBody() {
        if (!::body.isInitialized) return
        body.removeAllViews()
        if (!expanded) { body.visibility = View.GONE; return }
        body.visibility = View.VISIBLE
        val selectors = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val tierPicker = pickerButton(tiers[tierIndex], tiers, { tierIndex }) { index ->
            tierIndex = index
            record("tier_selected", JSONObject().put("selection_field", "tier"))
            renderBody()
        }
        val enchantPicker = pickerButton(enchants[enchantIndex], enchants, { enchantIndex }) { index ->
            enchantIndex = index
            record("enchantment_selected", JSONObject().put("selection_field", "enchantment"))
            renderBody()
        }
        selectors.addView(tierPicker, LinearLayout.LayoutParams(0, dp(36), 1f))
        selectors.addView(enchantPicker, LinearLayout.LayoutParams(0, dp(36), 1f))
        body.addView(selectors)

        val activityPicker = pickerButton(activities[activityIndex], activities, { activityIndex }, ::selectActivity)
        body.addView(activityPicker, LinearLayout.LayoutParams(-1, dp(36)))

        val events = when (activities[activityIndex]) {
            "Ore", "Wood", "Stone", "Fiber", "Hide" -> listOf("Found" to "target_found", "Start" to "gathering_started", "Done" to "gathering_completed", "Empty" to "node_depleted")
            "Mobs" -> listOf("Seen" to "target_found", "Killed" to "mob_killed")
            "PvP" -> listOf("Player" to "player_spotted", "Fight" to "pvp_started", "End" to "pvp_ended")
            else -> listOf("Start" to "activity_start", "Event" to "activity_event", "End" to "activity_end")
        }
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        events.forEach { (label, event) ->
            row.addView(button(label) {
                if (event == "target_found" && activities[activityIndex] in listOf("Ore", "Wood", "Stone", "Fiber", "Hide")) {
                    nodeId = UUID.randomUUID().toString()
                    record(event, JSONObject().put("node_created", true))
                } else {
                    if (nodeId == null && activities[activityIndex] in listOf("Ore", "Wood", "Stone", "Fiber", "Hide")) {
                        nodeId = UUID.randomUUID().toString()
                    }
                    record(event)
                    if (event == "node_depleted" || event == "gathering_completed" || event == "mob_killed") nodeId = null
                }
            }, LinearLayout.LayoutParams(0, dp(38), 1f))
        }
        body.addView(row)
    }

    private fun record(event: String, extra: JSONObject = JSONObject()) {
        try {
            val now = System.currentTimeMillis()
            val json = JSONObject()
                .put("timestamp_ms", now)
                .put("elapsed_realtime_ms", android.os.SystemClock.elapsedRealtime())
                .put("event", event)
                .put("event_id", UUID.randomUUID().toString())
                .put("sequence", eventSequence.incrementAndGet())
                .put("activity", activities[activityIndex])
                .put("tier", tiers[tierIndex])
                .put("enchantment", enchants[enchantIndex])
                .put("source", "manual_overlay")
            nodeId?.let { json.put("node_id", it) }
            val keys = extra.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                json.put(key, extra.get(key))
            }
            var result = CoreServiceManager.recordAlbionCaptureMarker(json.toString())
            if (result == "error:no_service") {
                val queued = AlbionMarkerQueue.enqueue(this, json.toString())
                result = if (queued != null) "queued:$queued" else "error:local_queue_failed"
            }
            title.text = when {
                result.startsWith("saved:") -> "${activities[activityIndex]} ✓"
                result.startsWith("queued:") -> "${activities[activityIndex]} …"
                else -> "${activities[activityIndex]} !"
            }
        } catch (_: Exception) {
            title.text = "${activities[activityIndex]} !"
        }
    }

    override fun onDestroy() {
        dismissSwipePicker()
        try { root?.let { wm.removeView(it) } } catch (_: Exception) { }
        root = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    companion object {
        const val ACTION_MARKER = "com.v2ray.ang.albion.PCAP_MARKER"
        const val EXTRA_MARKER = "marker_json"
        const val ACTION_STOP = "com.v2ray.ang.albion.STOP_OVERLAY"
    }
}
