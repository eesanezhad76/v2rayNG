package com.v2ray.ang.ui.albion

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.widget.FrameLayout
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.CheckBox
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.NotificationCompat
import org.json.JSONArray
import org.json.JSONObject
import com.v2ray.ang.albion.AlbionCaptureMarker
import com.v2ray.ang.core.CoreServiceManager
import java.util.concurrent.atomic.AtomicBoolean

/** Two independently movable, compact overlay windows: activity controls and navigation radar. */
class AlbionOverlayService : Service() {
    private lateinit var wm: WindowManager
    private var controlsRoot: FrameLayout? = null
    private var radarRoot: FrameLayout? = null
    private var controlsContent: FrameLayout? = null
    private var radarContent: FrameLayout? = null
    private lateinit var radarView: AlbionMiniRadarView
    private lateinit var body: LinearLayout
    private lateinit var liveStatus: TextView
    private lateinit var title: TextView
    private lateinit var filterPanel: LinearLayout
    private lateinit var filterScroll: ScrollView
    private lateinit var filterButton: Button
    private lateinit var catalogButton: Button
    private lateinit var nextCatalogButton: Button
    private lateinit var catalogInfo: TextView
    private var catalogOverviewMode = false
    private var controlsParams: WindowManager.LayoutParams? = null
    private var radarParams: WindowManager.LayoutParams? = null
    private var expanded = true
    private var activity = "Ore"
    private var tier = "T3"
    private var enchant = "Normal"
    private var enabledFilters = AlbionMiniRadarView.DEFAULT_FILTERS.toMutableSet()
    private var catalogEntities = JSONArray()
    private var catalogCaptures = JSONArray()
    private var catalogIndex = 0
    private var mobileTypeDictionary = JSONObject()
    private var catalogNodeCount = 0
    private var catalogMobCount = 0
    private var catalogMarkerCount = 0
    private val refreshHandler = Handler(Looper.getMainLooper())
    private val snapshotThread = HandlerThread("AlbionRadarSnapshot").apply { start() }
    private val snapshotHandler = Handler(snapshotThread.looper)
    private val snapshotRefreshInFlight = AtomicBoolean(false)
    private val activities = listOf("Ore", "Wood", "Stone", "Fiber", "Hide", "Mob", "Troll", "PvP", "Move", "Other")
    private val tiers = (2..8).map { "T$it" }
    private val enchants = listOf("Normal", "E1", "E2", "E3")
    private val filterOptions = listOf(
        "category_ore" to "Ore",
        "category_wood" to "Wood / Trees",
        "category_stone" to "Stone",
        "category_fiber" to "Fiber",
        "category_hide" to "Hide / Animals",
        "category_troll" to "Trolls",
        "category_ore_elemental" to "Ore Elementals",
        "category_animal" to "Other Animals",
        "category_mobs" to "Other Mobs",
        "category_unknown_resource" to "Unclassified Resources"
    ) + (2..8).map { "tier_$it" to "T$it" } +
        listOf("enchant_0" to "Normal", "enchant_1" to "E1", "enchant_2" to "E2", "enchant_3" to "E3")

    private val refreshLiveSnapshot = object : Runnable {
        override fun run() {
            if (controlsRoot == null && radarRoot == null) return
            if (!snapshotRefreshInFlight.compareAndSet(false, true)) {
                refreshHandler.postDelayed(this, 1200L)
                return
            }
            snapshotHandler.post {
                var acceptedCount: Int? = null
                var statusText = "Live snapshot unavailable"
                try {
                    val live = enrichLiveSnapshot(CoreServiceManager.getAlbionLiveKnowledgeJson())
                    if (::radarView.isInitialized) acceptedCount = radarView.setLiveSnapshot(live)
                    statusText = CoreServiceManager.getAlbionLiveKnowledgeSnapshot()
                        .replace('\n', ' ').replace('\r', ' ')
                } catch (error: Exception) {
                    statusText = "Snapshot error: ${error.message ?: error.javaClass.simpleName}"
                }
                refreshHandler.post {
                    acceptedCount?.let(CoreServiceManager::acknowledgeAlbionOverlaySnapshot)
                    if (::liveStatus.isInitialized) {
                        liveStatus.text = "DB ${catalogNodeCount}N/${catalogMobCount}M • " + statusText.take(210)
                    }
                    snapshotRefreshInFlight.set(false)
                    if (controlsRoot != null || radarRoot != null) refreshHandler.postDelayed(this, 1200L)
                }
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val channel = "albion_activity_tags"
        val nm = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) nm.createNotificationChannel(
            NotificationChannel(channel, "Albion tags", NotificationManager.IMPORTANCE_LOW)
        )
        val notification: Notification = NotificationCompat.Builder(this, channel)
            .setSmallIcon(com.v2ray.ang.R.drawable.ic_stat_name)
            .setContentTitle("Albion radar").setContentText("Compact controls and map active").setOngoing(true).build()
        if (Build.VERSION.SDK_INT >= 34) startForeground(
            7331, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        ) else startForeground(7331, notification)
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        loadStaticCatalog()
        if (Settings.canDrawOverlays(this)) show() else stopSelf()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) stopSelf()
        return START_STICKY
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun dpScaled(v: Int, scale: Float) = (dp(v) * scale).toInt().coerceAtLeast(1)

    private fun resizeHandle(): TextView = TextView(this).apply {
        text = "◢"; textSize = 10f; gravity = Gravity.CENTER
        setTextColor(0xFFEAF1F5.toInt())
        contentDescription = getString(com.v2ray.ang.R.string.albion_resize_overlay)
        background = GradientDrawable().apply {
            setColor(0xDD263E50.toInt()); cornerRadius = dp(4).toFloat()
            setStroke(dp(1), 0xFF80D8FF.toInt())
        }
    }

    private fun compactButton(label: String, action: () -> Unit) = Button(this).apply {
        text = label; textSize = 6.5f; isAllCaps = false
        minWidth = 0; minimumWidth = 0; minHeight = 0; minimumHeight = dp(20)
        setPadding(dp(1), 0, dp(1), 0); includeFontPadding = false
        maxLines = 1; isSingleLine = true; gravity = Gravity.CENTER
        setTextColor(0xFFEAF1F5.toInt())
        background = GradientDrawable().apply {
            setColor(0xCC202D38.toInt()); cornerRadius = dp(4).toFloat()
            setStroke(dp(1), 0x88536777.toInt())
        }
        stateListAnimator = null
        setOnClickListener { action() }
    }

    private fun attachResize(
        handle: View, content: View, window: FrameLayout, params: WindowManager.LayoutParams,
        preferenceKey: String, baseWidthDp: Int
    ) {
        val prefs = getSharedPreferences("albion_overlay_layout", MODE_PRIVATE)
        var downX = 0f; var downY = 0f; var startScale = 1f
        handle.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.rawX; downY = event.rawY
                    startScale = content.scaleX.coerceIn(0.65f, 1.5f)
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val denominator = maxOf(dp(baseWidthDp), content.height, dp(100)).toFloat()
                    val delta = ((event.rawX - downX) + (event.rawY - downY)) / (2f * denominator)
                    val scale = (startScale + delta).coerceIn(0.65f, 1.5f)
                    content.scaleX = scale; content.scaleY = scale
                    params.width = dpScaled(baseWidthDp, scale)
                    params.height = dpScaled((content.height / resources.displayMetrics.density).toInt().coerceAtLeast(100), scale)
                    try { wm.updateViewLayout(window, params) } catch (_: Exception) { }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    prefs.edit().putFloat(preferenceKey, content.scaleX.coerceIn(0.65f, 1.5f)).apply()
                    true
                }
                else -> false
            }
        }
    }
    private fun panelBackground() = GradientDrawable().apply {
        setColor(0xE9162029.toInt()); cornerRadius = dp(9).toFloat()
        setStroke(dp(1), 0x885F7788.toInt())
    }

    private fun button(label: String, selected: Boolean = false, action: () -> Unit) =
        Button(this).apply {
            text = label; textSize = if (label.length > 5) 7f else 8f; isAllCaps = false
            minWidth = 0; minimumWidth = dp(30); minHeight = 0; minimumHeight = dp(28)
            setPadding(dp(4), 0, dp(4), 0); includeFontPadding = false
            maxLines = 1; isSingleLine = true; gravity = Gravity.CENTER; textScaleX = 0.90f
            ellipsize = android.text.TextUtils.TruncateAt.END
            setTextColor(if (selected) 0xFF80D8FF.toInt() else 0xFFEAF1F5.toInt())
            background = GradientDrawable().apply {
                setColor(if (selected) 0xFF263E50.toInt() else 0xFF202D38.toInt())
                cornerRadius = dp(5).toFloat()
                setStroke(dp(1), if (selected) 0xFF62BCE5.toInt() else 0xFF536777.toInt())
            }
            stateListAnimator = null
            setOnClickListener { action() }
        }

    private fun choiceRow(values: List<String>, selected: String, onPick: (String) -> Unit): HorizontalScrollView {
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        values.forEach { value ->
            row.addView(button(value, value == selected || selected.split("|").contains(value)) { onPick(value) },
                LinearLayout.LayoutParams(-2, dp(30)).apply { rightMargin = dp(2) })
        }
        return HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false; addView(row) }
    }

    private fun show() {
        if (controlsRoot != null) return
        buildControlsWindow()
        buildRadarWindow()
        refreshHandler.removeCallbacks(refreshLiveSnapshot)
        refreshHandler.post(refreshLiveSnapshot)
    }

    private fun buildControlsWindow() {
        val prefs = getSharedPreferences("albion_overlay_layout", MODE_PRIVATE)
        val scale = prefs.getFloat("controls_scale", 1f).coerceIn(0.65f, 1.5f)
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(3), dp(4), dp(3))
            background = panelBackground()
        }
        val head = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        title = TextView(this).apply {
            text = "ALBION • $activity"; setTextColor(-1); textSize = 11f
            typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER_VERTICAL
            maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END; includeFontPadding = false
        }
        head.addView(title, LinearLayout.LayoutParams(0, dp(27), 1f))
        head.addView(button("MAP") { if (radarRoot == null) buildRadarWindow() }, LinearLayout.LayoutParams(dp(36), dp(28)))
        head.addView(button("−") { expanded = !expanded; renderBody() }, LinearLayout.LayoutParams(dp(27), dp(28)))
        head.addView(button("×") { stopSelf() }, LinearLayout.LayoutParams(dp(27), dp(27)))
        panel.addView(head)
        body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        liveStatus = TextView(this).apply {
            text = "Capture waiting"; setTextColor(0xFFE3EDF5.toInt()); textSize = 8f
            typeface = Typeface.MONOSPACE; setPadding(dp(2), dp(2), dp(2), dp(1))
            maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END; includeFontPadding = false
        }
        panel.addView(body)
        val content = FrameLayout(this).apply {
            pivotX = 0f; pivotY = 0f; scaleX = scale; scaleY = scale
            addView(panel, FrameLayout.LayoutParams(dp(218), WindowManager.LayoutParams.WRAP_CONTENT))
        }
        val grip = resizeHandle()
        content.addView(grip, FrameLayout.LayoutParams(dp(20), dp(20), Gravity.TOP or Gravity.LEFT))
        val root = FrameLayout(this).apply {
            tag = "controls"
            addView(content, FrameLayout.LayoutParams(dp(218), WindowManager.LayoutParams.WRAP_CONTENT))
        }
        controlsContent = content
        controlsRoot = root
        val type = if (Build.VERSION.SDK_INT >= 26) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE
        val params = WindowManager.LayoutParams(
            dpScaled(218, scale), WindowManager.LayoutParams.WRAP_CONTENT, type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = prefs.getInt("controls_x", dp(8))
            y = prefs.getInt("controls_y", dp(90))
        }
        controlsParams = params
        wm.addView(root, params)
        root.post {
            if (controlsRoot === root && content.height > 0) {
                params.height = dpScaled((content.height / resources.displayMetrics.density).toInt(), scale)
                try { wm.updateViewLayout(root, params) } catch (_: Exception) { }
            }
        }
        attachDrag(title, root, params)
        attachResize(grip, content, root, params, "controls_scale", 218)
        renderBody()
    }

    private fun buildRadarWindow() {
        val prefs = getSharedPreferences("albion_overlay_layout", MODE_PRIVATE)
        val scale = prefs.getFloat("radar_scale", 1f).coerceIn(0.65f, 1.5f)
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 0, 0, 0)
            background = null
        }
        radarView = AlbionMiniRadarView(this)
        for (i in 0 until catalogEntities.length()) {
            catalogEntities.optJSONObject(i)?.let { applyMobTypeInfo(it) }
        }
        radarView.setCatalogSnapshot(catalogEntities.toString())
        panel.addView(radarView, LinearLayout.LayoutParams(dp(176), dp(176)))

        val side = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.TOP
            setPadding(dp(2), 0, 0, 0)
        }
        val dragTitle = TextView(this).apply {
            text = "DRAG"; setTextColor(0xFFEAF1F5.toInt()); textSize = 7f
            typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER
            includeFontPadding = false
        }
        side.addView(dragTitle, LinearLayout.LayoutParams(-1, dp(17)))
        catalogButton = compactButton("SAVED") {
            catalogOverviewMode = !catalogOverviewMode
            radarView.setCatalogOverviewMode(catalogOverviewMode)
            catalogButton.text = if (catalogOverviewMode) "LIVE" else "SAVED"
            updateCatalogInfo()
        }
        side.addView(catalogButton, LinearLayout.LayoutParams(-1, dp(21)).apply { bottomMargin = dp(2) })
        nextCatalogButton = compactButton("${catalogIndex + 1}/${catalogCaptures.length().coerceAtLeast(1)}") { showNextCatalog() }
        side.addView(nextCatalogButton, LinearLayout.LayoutParams(-1, dp(21)).apply { bottomMargin = dp(2) })
        filterButton = compactButton("FILTER") { toggleFilters() }
        side.addView(filterButton, LinearLayout.LayoutParams(-1, dp(21)).apply { bottomMargin = dp(2) })
        side.addView(compactButton("×") { removeRadarWindow() }, LinearLayout.LayoutParams(-1, dp(21)).apply { bottomMargin = dp(2) })
        catalogInfo = label("")
        catalogInfo.maxLines = 2
        catalogInfo.textSize = 6f
        side.addView(catalogInfo, LinearLayout.LayoutParams(-1, dp(24)))
        filterPanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(2), dp(1), dp(2), dp(1))
        }
        filterScroll = ScrollView(this).apply {
            isFillViewport = true; visibility = View.GONE; addView(filterPanel)
        }
        side.addView(filterScroll, LinearLayout.LayoutParams(-1, dp(100)))
        panel.addView(side, LinearLayout.LayoutParams(dp(60), dp(176)))

        val content = FrameLayout(this).apply {
            pivotX = 0f; pivotY = 0f; scaleX = scale; scaleY = scale
            addView(panel, FrameLayout.LayoutParams(dp(236), WindowManager.LayoutParams.WRAP_CONTENT))
        }
        val grip = resizeHandle()
        content.addView(grip, FrameLayout.LayoutParams(dp(18), dp(18), Gravity.TOP or Gravity.LEFT))
        val root = FrameLayout(this).apply {
            tag = "radar"
            addView(content, FrameLayout.LayoutParams(dp(236), WindowManager.LayoutParams.WRAP_CONTENT))
        }
        radarContent = content
        radarRoot = root
        val type = if (Build.VERSION.SDK_INT >= 26) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE
        val params = WindowManager.LayoutParams(
            dpScaled(236, scale), WindowManager.LayoutParams.WRAP_CONTENT, type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = prefs.getInt("radar_x", dp(8))
            y = prefs.getInt("radar_y", dp(350))
        }
        radarParams = params
        wm.addView(root, params)
        root.post {
            if (radarRoot === root && content.height > 0) {
                params.height = dpScaled((content.height / resources.displayMetrics.density).toInt(), scale)
                try { wm.updateViewLayout(root, params) } catch (_: Exception) { }
            }
        }
        attachDrag(dragTitle, root, params)
        attachResize(grip, content, root, params, "radar_scale", 236)
        renderFilters()
        radarView.setFilters(enabledFilters)
        updateCatalogInfo()
    }

    private fun attachDrag(handle: View, target: View, params: WindowManager.LayoutParams) {
        var startX = 0f; var startY = 0f; var originX = 0; var originY = 0
        handle.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> { startX = event.rawX; startY = event.rawY; originX = params.x; originY = params.y; true }
                MotionEvent.ACTION_MOVE -> {
                    val maxX = (resources.displayMetrics.widthPixels - target.width).coerceAtLeast(0)
                    val maxY = (resources.displayMetrics.heightPixels - target.height).coerceAtLeast(0)
                    params.x = (originX + (event.rawX - startX).toInt()).coerceIn(0, maxX)
                    params.y = (originY + (event.rawY - startY).toInt()).coerceIn(0, maxY)
                    try { wm.updateViewLayout(target, params) } catch (_: Exception) { }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    val key = target.tag?.toString() ?: "overlay"
                    getSharedPreferences("albion_overlay_layout", MODE_PRIVATE).edit()
                        .putInt("${key}_x", params.x).putInt("${key}_y", params.y).apply()
                    true
                }
                else -> false
            }
        }
    }

    private fun renderBody() {
        body.removeAllViews()
        if (expanded) {
            body.addView(label("ACTIVITY"))
            body.addView(choiceRow(activities, activity) { activity = it; renderBody() })
            title.text = "ALBION • $activity"
            if (activity !in setOf("Mob", "Mobs", "PvP", "Move", "Other")) {
                body.addView(label("TIER"))
                body.addView(choiceRow(tiers, tier) { tier = it; renderBody() })
                body.addView(label("ENCHANT"))
                body.addView(choiceRow(enchants, enchant) { enchant = it; renderBody() })
            }
            val actions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            val first = if (activity == "Move") "MOVE +" else "START"
            val second = if (activity in setOf("Mob", "Mobs", "Troll")) "KILLED" else "END"
            val firstEvent = when (activity) {
                "Move" -> "movement_started"; "PvP" -> "pvp_started"; "Other" -> "activity_start"
                else -> "target_found"
            }
            val secondEvent = when (activity) {
                "Move" -> "movement_completed"; "PvP" -> "pvp_ended"; "Other" -> "activity_end"
                "Mob", "Mobs", "Troll" -> "mob_killed"; else -> "gathering_completed"
            }
            actions.addView(button(first) { tag(firstEvent) },
                LinearLayout.LayoutParams(0, dp(31), 1f).apply { rightMargin = dp(3) })
            actions.addView(button(second) { tag(secondEvent) }, LinearLayout.LayoutParams(0, dp(31), 1f))
            body.addView(actions)
        } else {
            body.addView(button("OPEN CONTROLS") { expanded = true; renderBody() }, LinearLayout.LayoutParams(-1, dp(28)))
        }
        body.addView(liveStatus)
    }

    private fun renderFilters() {
        filterPanel.removeAllViews()
        filterPanel.addView(label("1 • RESOURCE / TARGET TYPE"))
        filterOptions.take(10).forEach { (key, label) ->
            val check = CheckBox(this).apply {
                text = label; textSize = 9f; setTextColor(0xFFE3EDF5.toInt())
                buttonTintList = ColorStateList.valueOf(0xFF80D8FF.toInt())
                isChecked = key in enabledFilters; minHeight = dp(24); setPadding(dp(2), 0, 0, 0)
                includeFontPadding = false
                setOnCheckedChangeListener { _, checked ->
                    if (checked) enabledFilters.add(key) else enabledFilters.remove(key)
                    radarView.setFilters(enabledFilters)
                }
            }
            filterPanel.addView(check, LinearLayout.LayoutParams(-1, dp(24)))
        }
        filterPanel.addView(label("2 • TIER (NONE = ALL)"))
        filterOptions.drop(10).take(7).forEach { (key, label) ->
            val check = CheckBox(this).apply {
                text = label; textSize = 9f; setTextColor(0xFFE3EDF5.toInt())
                buttonTintList = ColorStateList.valueOf(0xFF80D8FF.toInt())
                isChecked = key in enabledFilters; minHeight = dp(24); includeFontPadding = false
                setOnCheckedChangeListener { _, checked ->
                    if (checked) enabledFilters.add(key) else enabledFilters.remove(key)
                    radarView.setFilters(enabledFilters)
                }
            }
            filterPanel.addView(check, LinearLayout.LayoutParams(-1, dp(24)))
        }
        filterPanel.addView(label("3 • ENCHANTMENT (NONE = ALL)"))
        filterOptions.drop(17).forEach { (key, label) ->
            val check = CheckBox(this).apply {
                text = label; textSize = 9f; setTextColor(0xFFE3EDF5.toInt())
                buttonTintList = ColorStateList.valueOf(0xFF80D8FF.toInt())
                isChecked = key in enabledFilters; minHeight = dp(24); includeFontPadding = false
                setOnCheckedChangeListener { _, checked ->
                    if (checked) enabledFilters.add(key) else enabledFilters.remove(key)
                    radarView.setFilters(enabledFilters)
                }
            }
            filterPanel.addView(check, LinearLayout.LayoutParams(-1, dp(24)))
        }
        filterPanel.addView(button("RESET FILTERS") {
            enabledFilters = AlbionMiniRadarView.DEFAULT_FILTERS.toMutableSet()
            renderFilters(); radarView.setFilters(enabledFilters)
        }, LinearLayout.LayoutParams(-1, dp(28)))
    }

    private fun toggleFilters() {
        filterScroll.visibility = if (filterScroll.visibility == View.VISIBLE) View.GONE else View.VISIBLE
        filterButton.text = if (filterScroll.visibility == View.VISIBLE) "HIDE" else "FILTER"
        radarParams?.let { params -> try { radarRoot?.let { root ->
            radarContent?.let { content ->
                params.height = dpScaled((content.height / resources.displayMetrics.density).toInt().coerceAtLeast(176), content.scaleY)
            }
            wm.updateViewLayout(root, params)
        } } catch (_: Exception) { } }
    }

    private fun loadStaticCatalog() {
        // Keep the full external dictionary; the research index is only a capture-scoped supplement.
        try {
            val typeData = assets.open("albion-mobile-type-dictionary.json").bufferedReader().use { JSONObject(it.readText()) }
            mobileTypeDictionary = typeData.optJSONObject("entries") ?: JSONObject()
        } catch (_: Exception) { mobileTypeDictionary = JSONObject() }

        try {
            val index = assets.open("albion-research-catalog-index.json").bufferedReader().use { JSONObject(it.readText()) }
            catalogCaptures = index.optJSONArray("captures") ?: JSONArray()
            if (catalogCaptures.length() > 0) {
                catalogIndex = 0
                loadCatalogCapture(catalogIndex)
                return
            }
        } catch (_: Exception) { catalogCaptures = JSONArray() }

        // Backward-compatible fallback for APKs or branches without the grouped index.
        try {
            val data = assets.open("albion-static-catalog.json").bufferedReader().use { JSONObject(it.readText()) }
            catalogEntities = data.optJSONArray("entities") ?: JSONArray()
            catalogNodeCount = data.optInt("node_count", catalogEntities.length())
            catalogMobCount = data.optInt("mob_count", 0)
            catalogMarkerCount = data.optInt("marker_count", 0)
        } catch (_: Exception) { catalogEntities = JSONArray() }
    }

    private fun loadCatalogCapture(index: Int) {
        val capture = catalogCaptures.optJSONObject(index) ?: return
        catalogEntities = capture.optJSONArray("entities") ?: JSONArray()
        catalogNodeCount = capture.optInt("node_count", 0)
        catalogMobCount = capture.optInt("mob_count", 0)
        catalogMarkerCount = capture.optInt("marker_count", 0)
        for (i in 0 until catalogEntities.length()) {
            catalogEntities.optJSONObject(i)?.let { applyMobTypeInfo(it) }
        }
        if (::radarView.isInitialized) radarView.setCatalogSnapshot(catalogEntities.toString())
        if (::nextCatalogButton.isInitialized) nextCatalogButton.text = "${index + 1}/${catalogCaptures.length()}"
        updateCatalogInfo()
    }

    private fun showNextCatalog() {
        if (catalogCaptures.length() == 0) return
        catalogIndex = (catalogIndex + 1) % catalogCaptures.length()
        loadCatalogCapture(catalogIndex)
        catalogOverviewMode = true
        if (::radarView.isInitialized) radarView.setCatalogOverviewMode(true)
        if (::catalogButton.isInitialized) catalogButton.text = "LIVE"
        updateCatalogInfo()
    }

    private fun updateCatalogInfo() {
        if (!::catalogInfo.isInitialized) return
        val capture = catalogCaptures.optJSONObject(catalogIndex)
        catalogInfo.text = if (capture != null) {
            "ZONE UNVERIFIED • ${capture.optString("archive")} / ${capture.optString("pcap")}"
        } else "Saved capture coordinates are local to their source session"
        catalogInfo.visibility = if (catalogOverviewMode) View.VISIBLE else View.GONE
    }

    private fun mergeCatalogAndLive(liveJson: String): String {
        val merged = JSONArray()
        val live = try { JSONArray(liveJson) } catch (_: Exception) { JSONArray() }
        for (i in 0 until catalogEntities.length()) {
            val source = catalogEntities.optJSONObject(i) ?: continue
            val node = JSONObject(source.toString())
            if (!node.has("x") || !node.has("y")) continue
            node.put("entity_id", "catalog:${source.optString("entity_id")}")
            applyMobTypeInfo(node)
            if (node.optString("last_kind").isBlank()) node.put("last_kind", "static_resource")
            if (node.optString("last_kind") == "static_resource") {
                val timeline = node.optJSONArray("state_timeline")
                val latestSize = if (timeline != null && timeline.length() > 0)
                    timeline.optJSONObject(timeline.length() - 1)?.optInt("size", -1) ?: -1 else -1
                // Do not erase a previously established depleted flag when the static
                // catalog has no state timeline. A later positive live harvestable
                // observation is the only live-session signal that clears depletion.
                val depleted = if (latestSize >= 0) latestSize == 0
                    else node.optBoolean("depleted", false)
                node.put("depleted", depleted)
            }
            merged.put(node)
        }
        for (i in 0 until live.length()) live.optJSONObject(i)?.let {
            val liveEntity = JSONObject(it.toString())
            applyMobTypeInfo(liveEntity)
            merged.put(liveEntity)
        }
        return merged.toString()
    }

    private fun enrichLiveSnapshot(json: String): String {
        val array = try { JSONArray(json) } catch (_: Exception) { return "[]" }
        for (i in 0 until array.length()) {
            array.optJSONObject(i)?.let { applyMobTypeInfo(it) }
        }
        return array.toString()
    }

    private fun applyMobTypeInfo(entity: JSONObject) {
        if (entity.optString("last_kind") != "mob") return
        val info = mobileTypeDictionary.optJSONObject(entity.optString("mobile_type_id")) ?: return
        if (entity.optString("name").isBlank() || entity.optString("name") == "null")
            entity.put("name", info.optString("display_name"))
        if (!entity.has("tier")) entity.put("tier", info.optInt("tier", -1))
        entity.put("mob_category", info.optString("category"))
        entity.put("type_unique_name", info.optString("unique_name"))
        entity.put("localization_tag", info.optString("localization_tag"))
        entity.put("mob_danger", info.optString("danger"))
        entity.put("max_health", info.optInt("max_health", 0))
        entity.put("is_harvestable_mob", info.optBoolean("is_harvestable", false))
    }

    private fun label(value: String) = TextView(this).apply {
        text = value; setTextColor(0xFFB8CAD8.toInt()); textSize = 7.5f
        setPadding(dp(2), dp(1), 0, 0); includeFontPadding = false
    }

    private fun tag(event: String) {
        val now = System.currentTimeMillis()
        try {
            // Preserve every tap, but annotate a repeated same-action tap within 10 s.
            // This is deliberately not a debounce: a possible duplicate is never dropped.
            val repeatPrefs = getSharedPreferences("albion_marker_repeat_detection", MODE_PRIVATE)
            val repeatKey = "$activity|$event"
            val previousAt = repeatPrefs.getLong(repeatKey, 0L)
            val repeatGap = if (previousAt > 0L && now >= previousAt) now - previousAt else Long.MAX_VALUE
            val possibleRepeat = repeatGap <= MARKER_REPEAT_WINDOW_MS
            repeatPrefs.edit().putLong(repeatKey, now).apply()
            val elapsed = android.os.SystemClock.elapsedRealtime()
            val json = JSONObject().apply {
                put("timestamp_ms", now); put("elapsed_realtime_ms", elapsed)
                put("marker_id", "manual:$now:$elapsed")
                put("event", event); put("activity", activity)
                if (activity in setOf("Ore", "Wood", "Stone", "Fiber", "Hide", "Troll")) {
                    put("tier", tier); put("enchantment", enchant)
                }
                put("possible_duplicate", possibleRepeat)
                if (possibleRepeat) {
                    put("duplicate_of_timestamp_ms", previousAt)
                    put("duplicate_gap_ms", repeatGap)
                    put("duplicate_window_ms", MARKER_REPEAT_WINDOW_MS)
                }
                put("source", "manual_overlay")
            }.toString()
            val saved = CoreServiceManager.appendAlbionCaptureMarker(AlbionCaptureMarker.build(json), now)
            title.text = if (saved) "ALBION • $activity ✓" else "ALBION • $activity !"
            if (!saved) Toast.makeText(this, "Marker not saved. Capture must be running.", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            title.text = "ALBION • $activity !"
            Toast.makeText(this, "Marker error: ${e.message ?: "unknown"}", Toast.LENGTH_LONG).show()
        }
    }

    private fun removeRadarWindow() {
        try { radarRoot?.let { wm.removeView(it) } } catch (_: Exception) { }
        radarRoot = null; radarContent = null; radarParams = null
    }

    override fun onDestroy() {
        refreshHandler.removeCallbacks(refreshLiveSnapshot)
        snapshotHandler.removeCallbacksAndMessages(null)
        snapshotThread.quitSafely()
        try { controlsRoot?.let { wm.removeView(it) } } catch (_: Exception) { }
        try { radarRoot?.let { wm.removeView(it) } } catch (_: Exception) { }
        controlsRoot = null; radarRoot = null; controlsContent = null; radarContent = null; controlsParams = null; radarParams = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    companion object {
        private const val MARKER_REPEAT_WINDOW_MS = 10_000L
        const val ACTION_MARKER = "com.v2ray.ang.albion.PCAP_MARKER"
        const val EXTRA_MARKER = "marker_json"
        const val ACTION_STOP = "com.v2ray.ang.albion.STOP_OVERLAY"
    }
}
