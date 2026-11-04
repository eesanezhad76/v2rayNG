package com.v2ray.ang.ui.albion

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import org.json.JSONObject
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sin

/** Region-wide map of all currently observed coordinates in the active live snapshot. */
class AlbionWorldMapView(context: Context) : View(context) {
    @Volatile private var entities: List<JSONObject> = emptyList()
    @Volatile private var player: JSONObject? = null
    @Volatile private var invalidCount = 0
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val density = resources.displayMetrics.density
    private var pixelsPerUnit = 1f * density
    private var panX = 0f
    private var panY = 0f
    private var lastTouchX = 0f
    private var lastTouchY = 0f
    private var scaling = false
    private var fitted = false
    private var headingRadians = Math.PI / 4.0
    private val scaleDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScaleBegin(detector: ScaleGestureDetector): Boolean { scaling = true; return true }
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            pixelsPerUnit = (pixelsPerUnit * detector.scaleFactor).coerceIn(0.08f * density, 30f * density)
            invalidate()
            return true
        }
        override fun onScaleEnd(detector: ScaleGestureDetector) { scaling = false }
    })

    fun setSnapshot(json: String): Boolean {
        val parsed = AlbionRadarSnapshotParser.parse(json)
        if (parsed == null) {
            entities = emptyList(); player = null; invalidCount = 0; postInvalidate()
            return false
        }
        entities = parsed.entities
        invalidCount = parsed.invalidCoordinateCount
        player = entities.filter { it.optString("last_kind") == "own_player_move" }
            .maxByOrNull { it.optLong("last_seen_ms") }
        updateHeading(player)
        if (!fitted && width > 0 && height > 0) fitRegion()
        postInvalidate()
        return true
    }

    private fun updateHeading(you: JSONObject?) {
        val history = you?.optJSONArray("movement_history") ?: return
        if (history.length() < 2) return
        val cutoff = System.currentTimeMillis() - 5000L
        val points = ArrayList<Pair<Double, Double>>()
        for (i in (history.length() - 8).coerceAtLeast(0) until history.length()) {
            val p = history.optJSONObject(i) ?: continue
            if (p.optLong("time_ms", 0L) < cutoff) continue
            val x = p.optDouble("x", Double.NaN); val y = p.optDouble("y", Double.NaN)
            if (x.isFinite() && y.isFinite()) points.add(x to y)
        }
        if (points.size >= 2) {
            val first = points.first(); val last = points.last()
            if (hypot(last.first - first.first, last.second - first.second) >= 2.0)
                headingRadians = atan2(last.second - first.second, last.first - first.first)
        }
    }

    private fun fitRegion() {
        val pts = entities.filter { it.optString("last_kind") in DISPLAY_KINDS }
        if (pts.isEmpty() || width <= 0 || height <= 0) return
        val you = player
        val originX = you?.optDouble("x") ?: pts.map { it.optDouble("x") }.average()
        val originY = you?.optDouble("y") ?: pts.map { it.optDouble("y") }.average()
        var maxDistance = 1.0
        pts.forEach { maxDistance = max(maxDistance, hypot(it.optDouble("x") - originX, it.optDouble("y") - originY)) }
        val usable = (minOf(width, height) * 0.44f).coerceAtLeast(20f * density)
        pixelsPerUnit = (usable / (maxDistance * 1.08)).toFloat().coerceIn(0.08f * density, 30f * density)
        panX = 0f; panY = 0f; fitted = true
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        fitted = false
        fitRegion()
    }

    fun statusText(): String {
        val resources = entities.count { it.optString("last_kind") in RESOURCE_KINDS && !unavailable(it) }
        val mobs = entities.count { it.optString("last_kind") == "mob" }
        return "Region observations: ${entities.size} | resources=$resources mobs=$mobs | invalid=$invalidCount | heading=movement-aligned"
    }

    private fun unavailable(item: JSONObject) =
        item.optBoolean("depleted", false) || item.optBoolean("left", false)

    private fun enchantment(item: JSONObject): Int {
        val marker = item.optInt("marker_enchant", -1)
        if (marker in 0..3) return marker
        val inferred = item.optJSONObject("resource_inference")?.optJSONObject("enchantment_name")?.optString("value")
        return when (inferred) {
            "Normal" -> 0; "E1" -> 1; "E2" -> 2; "E3" -> 3
            else -> item.optInt("enchant", -1).takeIf { it in 0..3 } ?: -1
        }
    }

    private fun resourceColor(item: JSONObject): Int = when (enchantment(item)) {
        0 -> 0xFFFFC45C.toInt()
        1 -> 0xFF63D471.toInt()
        2 -> 0xFF55B8FF.toInt()
        3 -> 0xFFB58CFF.toInt()
        else -> 0xFF9AA6AF.toInt()
    }

    private fun tier(item: JSONObject): Int =
        item.optInt("marker_tier", -1).takeIf { it in 1..8 }
            ?: item.optInt("tier", -1).takeIf { it in 1..8 } ?: -1

    private fun screenOffset(dx: Double, dy: Double): Pair<Double, Double> {
        val h = headingRadians
        return (dx * sin(h) - dy * cos(h)) to -(dx * cos(h) + dy * sin(h))
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        // 50% map opacity; game remains visible beneath the map.
        canvas.drawColor(0xFF101820.toInt())
        val you = player
        val px = you?.optDouble("x") ?: 0.0
        val py = you?.optDouble("y") ?: 0.0
        val centerX = width / 2f + panX
        val centerY = height / 2f + panY

        // No grid labels, headings, status banners, or overlapping entity names.
        val history = you?.optJSONArray("movement_history")
        if (history != null && history.length() > 1) {
            paint.style = Paint.Style.STROKE; paint.strokeWidth = 1.5f * density
            paint.color = 0x9956D6A0.toInt()
            var oldX: Float? = null; var oldY: Float? = null
            for (i in 0 until history.length()) {
                val p = history.optJSONObject(i) ?: continue
                val (rx, ry) = screenOffset(p.optDouble("x") - px, p.optDouble("y") - py)
                val x = centerX + (rx * pixelsPerUnit).toFloat()
                val y = centerY + (ry * pixelsPerUnit).toFloat()
                if (x !in 0f..width.toFloat() || y !in 0f..height.toFloat()) { oldX = null; oldY = null; continue }
                if (oldX != null && oldY != null) canvas.drawLine(oldX, oldY, x, y, paint)
                oldX = x; oldY = y
            }
        }

        entities.forEach { entity ->
            val kind = entity.optString("last_kind")
            if (kind !in DISPLAY_KINDS || kind == "own_player_move" || (kind in RESOURCE_KINDS && unavailable(entity))) return@forEach
            // Unknown/conflicting static-resource candidates are not painted as false grey nodes.
            if (kind in RESOURCE_KINDS && (entity.optString("classification") == "ambiguous_resource_candidate" ||
                entity.optString("evidence_grade") == "conflicting_typed_observation")) return@forEach
            val (rx, ry) = screenOffset(entity.optDouble("x") - px, entity.optDouble("y") - py)
            val x = centerX + (rx * pixelsPerUnit).toFloat()
            val y = centerY + (ry * pixelsPerUnit).toFloat()
            if (x !in -12f..(width + 12f).toFloat() || y !in -12f..(height + 12f).toFloat()) return@forEach
            paint.style = Paint.Style.FILL
            paint.color = if (kind in RESOURCE_KINDS) resourceColor(entity) else 0xFFFF6666.toInt()
            canvas.drawCircle(x, y, 3.5f * density, paint)
            val t = tier(entity)
            if (kind in RESOURCE_KINDS && t >= 1) {
                paint.color = 0xFFFFFFFF.toInt()
                paint.textSize = 7f * density
                canvas.drawText("T$t", x + 4f * density, y - 3f * density, paint)
            }
        }

        paint.style = Paint.Style.FILL; paint.color = 0xFF56D6A0.toInt()
        canvas.drawCircle(centerX, centerY, 4.5f * density, paint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(event)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> { lastTouchX = event.x; lastTouchY = event.y; return true }
            MotionEvent.ACTION_MOVE -> if (!scaling && event.pointerCount == 1) {
                panX += event.x - lastTouchX; panY += event.y - lastTouchY
                invalidate()
                lastTouchX = event.x; lastTouchY = event.y
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> { scaling = false; return true }
        }
        return true
    }

    companion object {
        private val RESOURCE_KINDS = setOf("static_resource", "harvestable", "resource_state")
        private val DISPLAY_KINDS = RESOURCE_KINDS + setOf("mob", "own_player_move")
    }
}
