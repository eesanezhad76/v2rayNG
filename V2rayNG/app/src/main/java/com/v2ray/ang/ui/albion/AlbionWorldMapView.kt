package com.v2ray.ang.ui.albion

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import org.json.JSONObject
import kotlin.math.hypot
import kotlin.math.max

/** Region-wide map of all currently observed coordinates in the active live snapshot. */
class AlbionWorldMapView(context: Context) : View(context) {
    @Volatile private var entities: List<JSONObject> = emptyList()
    @Volatile private var player: JSONObject? = null
    @Volatile private var invalidCount = 0
    @Volatile private var savedCaptureMode = false
    private var lastLiveSnapshotJson: String? = null
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val density = resources.displayMetrics.density
    private var pixelsPerUnit = 1f * density
    private var panX = 0f
    private var panY = 0f
    private var lastTouchX = 0f
    private var lastTouchY = 0f
    private var scaling = false
    private var fitted = false
    private var mapOriginX = 0.0
    private var mapOriginY = 0.0
    private val scaleDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScaleBegin(detector: ScaleGestureDetector): Boolean { scaling = true; return true }
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            val factor = detector.scaleFactor
            val oldCenterX = width / 2f + panX
            val oldCenterY = height / 2f + panY
            pixelsPerUnit = (pixelsPerUnit * factor).coerceIn(0.005f * density, 30f * density)
            // Keep the world point under the pinch centroid stationary while zooming.
            val newCenterX = detector.focusX - (detector.focusX - oldCenterX) * factor
            val newCenterY = detector.focusY - (detector.focusY - oldCenterY) * factor
            panX = newCenterX - width / 2f
            panY = newCenterY - height / 2f
            invalidate()
            return true
        }
        override fun onScaleEnd(detector: ScaleGestureDetector) { scaling = false }
    })

    fun setSnapshot(json: String): Boolean {
        if (!savedCaptureMode && json == lastLiveSnapshotJson) return true
        savedCaptureMode = false
        val parsed = AlbionRadarSnapshotParser.parse(json)
        if (parsed == null) {
            lastLiveSnapshotJson = null
            entities = emptyList(); player = null; invalidCount = 0; postInvalidate()
            return false
        }
        entities = parsed.entities
        invalidCount = parsed.invalidCoordinateCount
        lastLiveSnapshotJson = json
        player = entities.filter { it.optString("last_kind") == "own_player_move" }
            .maxByOrNull { it.optLong("last_seen_ms") }
        if (!fitted && width > 0 && height > 0) fitRegion()
        postInvalidate()
        return true
    }

    fun setSavedSnapshot(json: String): Boolean {
        val parsed = AlbionRadarSnapshotParser.parse(json) ?: return false
        savedCaptureMode = true
        lastLiveSnapshotJson = null
        entities = parsed.entities
        invalidCount = parsed.invalidCoordinateCount
        player = null
        fitted = false
        fitRegion()
        postInvalidate()
        return true
    }

    private fun fitRegion() {
        val pts = entities.filter { it.optString("last_kind") in DISPLAY_KINDS }
        if (pts.isEmpty() || width <= 0 || height <= 0) return
        val you = player
        val originX = you?.optDouble("x") ?: pts.map { it.optDouble("x") }.average()
        val originY = you?.optDouble("y") ?: pts.map { it.optDouble("y") }.average()
        mapOriginX = originX
        mapOriginY = originY
        var maxDistance = 1.0
        pts.forEach { maxDistance = max(maxDistance, hypot(it.optDouble("x") - originX, it.optDouble("y") - originY)) }
        val usable = (minOf(width, height) * 0.44f).coerceAtLeast(20f * density)
        // Permit a genuinely region-wide fit; the previous 0.08 minimum
        // clipped far-away known nodes on large coordinate spans.
        pixelsPerUnit = (usable / (maxDistance * 1.08)).toFloat().coerceIn(0.005f * density, 30f * density)
        panX = 0f; panY = 0f; fitted = true
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        fitted = false
        fitRegion()
    }

    fun fitToRegion() {
        fitted = false
        fitRegion()
        invalidate()
    }

    fun centerOnPlayer() {
        val you = player
        if (you != null) {
            // Explicit recenter only. During normal movement the world origin never follows the player.
            mapOriginX = you.optDouble("x")
            mapOriginY = you.optDouble("y")
        }
        panX = 0f
        panY = 0f
        invalidate()
    }

    fun statusText(): String {
        val resources = entities.filter { it.optString("last_kind") in RESOURCE_KINDS }
        val active = resources.count { !unavailable(it) }
        val depleted = resources.size - active
        val mobs = entities.count { it.optString("last_kind") == "mob" && !it.optBoolean("left", false) }
        if (savedCaptureMode) {
            return "Saved capture coordinates: ${resources.size} resources, ${entities.count { it.optString("last_kind") == "mob" }} mobs | historical state; zone identity unverified | invalid=$invalidCount"
        }
        val you = player
        val nearest = if (you == null) null else resources.asSequence()
            .filter { !unavailable(it) && validMapPoint(it) }
            .map { it to hypot(it.optDouble("x") - you.optDouble("x"), it.optDouble("y") - you.optDouble("y")) }
            .minByOrNull { it.second }
        val nearestText = nearest?.let { (node, distance) ->
            " | nearest=${AlbionRadarSnapshotParser.labelForEntity(node)} ${"%.0f".format(java.util.Locale.US, distance)}u"
        } ?: ""
        return "Known session nodes: ${resources.size} active=$active depleted=$depleted mobs=$mobs$nearestText | invalid=$invalidCount"
    }

    private fun validMapPoint(item: JSONObject): Boolean =
        item.has("x") && item.has("y") &&
            item.optDouble("x", Double.NaN).isFinite() &&
            item.optDouble("y", Double.NaN).isFinite()

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
        val point = AlbionMapProjection.worldToScreen(dx, dy)
        return point.x to point.y
    }

    private fun drawPlayerMarker(
        canvas: Canvas, cx: Float, cy: Float, direction: AlbionMapProjection.Point?
    ) {
        paint.style = Paint.Style.FILL
        paint.color = 0xFF080B0E.toInt()
        canvas.drawCircle(cx, cy, 6f * density, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.8f * density
        paint.color = 0xFFFFFFFF.toInt()
        canvas.drawCircle(cx, cy, 6f * density, paint)
        if (direction != null) {
            val length = 22f * density
            val tipX = cx + (direction.x * length).toFloat()
            val tipY = cy + (direction.y * length).toFloat()
            val backX = cx - (direction.x * 6f * density).toFloat()
            val backY = cy - (direction.y * 6f * density).toFloat()
            val sideX = (-direction.y * 4.5f * density).toFloat()
            val sideY = (direction.x * 4.5f * density).toFloat()
            // White halo, black direction arrow: player coordinates stay visually distinct.
            paint.color = 0xFFFFFFFF.toInt()
            paint.strokeWidth = 5f * density
            paint.strokeCap = Paint.Cap.ROUND
            canvas.drawLine(cx, cy, tipX, tipY, paint)
            canvas.drawLine(tipX, tipY, backX + sideX, backY + sideY, paint)
            canvas.drawLine(tipX, tipY, backX - sideX, backY - sideY, paint)
            paint.color = 0xFF080B0E.toInt()
            paint.strokeWidth = 2.4f * density
            canvas.drawLine(cx, cy, tipX, tipY, paint)
            canvas.drawLine(tipX, tipY, backX + sideX, backY + sideY, paint)
            canvas.drawLine(tipX, tipY, backX - sideX, backY - sideY, paint)
        }
    }

    private fun drawNearestTargetGuide(canvas: Canvas, centerX: Float, centerY: Float) {
        val you = player ?: return
        // Guide to every valid active resource observation, including uncertain type
        // candidates. Coordinates can be useful even when the resource family is unknown.
        val targetAndDistance = entities.asSequence()
            .filter {
                it.optString("last_kind") in RESOURCE_KINDS &&
                    !unavailable(it) && validMapPoint(it)
            }
            .map { node ->
                node to hypot(node.optDouble("x") - you.optDouble("x"), node.optDouble("y") - you.optDouble("y"))
            }
            .minByOrNull { it.second } ?: return
        val target = targetAndDistance.first
        val distance = targetAndDistance.second
        val playerOffset = screenOffset(you.optDouble("x") - mapOriginX, you.optDouble("y") - mapOriginY)
        val targetOffset = screenOffset(target.optDouble("x") - mapOriginX, target.optDouble("y") - mapOriginY)
        val playerX = centerX + (playerOffset.first * pixelsPerUnit).toFloat()
        val playerY = centerY + (playerOffset.second * pixelsPerUnit).toFloat()
        val rawX = centerX + (targetOffset.first * pixelsPerUnit).toFloat()
        val rawY = centerY + (targetOffset.second * pixelsPerUnit).toFloat()
        val margin = 14f * density
        val targetX = rawX.coerceIn(margin, (width.toFloat() - margin).coerceAtLeast(margin))
        val targetY = rawY.coerceIn(margin, (height.toFloat() - margin).coerceAtLeast(margin))
        val arrived = distance <= ARRIVAL_DISTANCE_UNITS
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = if (arrived) 2.8f * density else 1.6f * density
        paint.color = if (arrived) 0xFFFFD54F.toInt() else 0xCC55E6FF.toInt()
        canvas.drawLine(playerX, playerY, targetX, targetY, paint)
        canvas.drawCircle(targetX, targetY, if (arrived) 13f * density else 8f * density, paint)
        if (arrived) canvas.drawCircle(targetX, targetY, 18f * density, paint)
        paint.style = Paint.Style.FILL
        canvas.drawCircle(targetX, targetY, 3f * density, paint)
        paint.textSize = 10f * density
        paint.textAlign = Paint.Align.CENTER
        paint.color = if (arrived) 0xFFFFE082.toInt() else 0xFFEAFBFF.toInt()
        val label = if (arrived) "ARRIVED" else "NODE • ${distance.toInt()}u"
        canvas.drawText(label, targetX, (targetY - 16f * density).coerceAtLeast(12f * density), paint)
        paint.textAlign = Paint.Align.LEFT
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        // 50% map opacity; game remains visible beneath the map.
        canvas.drawColor(0xFF101820.toInt())
        val you = player
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
                val (rx, ry) = screenOffset(p.optDouble("x") - mapOriginX, p.optDouble("y") - mapOriginY)
                val x = centerX + (rx * pixelsPerUnit).toFloat()
                val y = centerY + (ry * pixelsPerUnit).toFloat()
                if (x !in 0f..width.toFloat() || y !in 0f..height.toFloat()) { oldX = null; oldY = null; continue }
                if (oldX != null && oldY != null) canvas.drawLine(oldX, oldY, x, y, paint)
                oldX = x; oldY = y
            }
        }

        if (you != null) drawNearestTargetGuide(canvas, centerX, centerY)

        entities.forEach { entity ->
            val kind = entity.optString("last_kind")
            if (kind !in DISPLAY_KINDS || kind == "own_player_move") return@forEach
            val (rx, ry) = screenOffset(entity.optDouble("x") - mapOriginX, entity.optDouble("y") - mapOriginY)
            val x = centerX + (rx * pixelsPerUnit).toFloat()
            val y = centerY + (ry * pixelsPerUnit).toFloat()
            if (x !in -12f..(width + 12f).toFloat() || y !in -12f..(height + 12f).toFloat()) return@forEach
            paint.style = Paint.Style.FILL
            val troll = entity.optString("manual_activity").equals("Troll", ignoreCase = true) &&
                entity.optBoolean("manual_marker_identity_proven", false)
            val oreElemental = entity.optString("resource_role") == "ore_elemental" ||
                (entity.optBoolean("is_harvestable_mob", false) &&
                    entity.optString("type_unique_name").contains("CRITTER_ORE", ignoreCase = true))
            val mobState = entity.optString("mob_state")
            val mined = mobState == "mined" || entity.optBoolean("depleted", false)
            val deadNotHarvestable = mobState == "dead" ||
                (entity.optBoolean("mob_dead", false) && mobState != "dead_harvestable")
            paint.color = when {
                mined -> 0xFF424A52.toInt()
                kind in RESOURCE_KINDS && entity.optBoolean("left", false) -> 0xFF78838B.toInt()
                kind in RESOURCE_KINDS && (entity.optString("classification") == "ambiguous_resource_candidate" ||
                    entity.optString("evidence_grade") == "conflicting_typed_observation") -> 0xFFB6A7A0.toInt()
                kind in RESOURCE_KINDS -> resourceColor(entity)
                (troll || oreElemental) && deadNotHarvestable -> 0xFFB0BEC5.toInt()
                (troll || oreElemental) && entity.optBoolean("left", false) -> 0xFF78838B.toInt()
                troll || oreElemental -> resourceColor(entity)
                entity.optBoolean("left", false) -> 0xFF78838B.toInt()
                else -> 0xFFFF6666.toInt()
            }
            canvas.drawCircle(x, y, 3.5f * density, paint)
            if (deadNotHarvestable || mined) {
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 1.2f * density
                paint.color = if (mined) 0xFF20262B.toInt() else 0xFFFFFFFF.toInt()
                canvas.drawCircle(x, y, 5.5f * density, paint)
                paint.style = Paint.Style.FILL
            }
            val t = tier(entity)
            val label = when {
                troll && t >= 1 -> "TG$t"
                kind == "mob" && oreElemental && t >= 1 -> "OE$t"
                kind in RESOURCE_KINDS && t >= 1 -> "T$t"
                else -> null
            }
            if (label != null) {
                paint.color = 0xFFFFFFFF.toInt()
                paint.textSize = 7f * density
                canvas.drawText(label, x + 4f * density, y - 3f * density, paint)
            }
        }

        if (you != null) {
            val playerOffset = screenOffset(you.optDouble("x") - mapOriginX, you.optDouble("y") - mapOriginY)
            val playerScreenX = centerX + (playerOffset.first * pixelsPerUnit).toFloat()
            val playerScreenY = centerY + (playerOffset.second * pixelsPerUnit).toFloat()
            drawPlayerMarker(canvas, playerScreenX, playerScreenY, AlbionMapProjection.movementDirection(history))
        }
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
        private const val ARRIVAL_DISTANCE_UNITS = 4.0
        private val RESOURCE_KINDS = setOf("static_resource", "harvestable", "resource_state")
        private val DISPLAY_KINDS = RESOURCE_KINDS + setOf("mob", "own_player_move")
    }
}
