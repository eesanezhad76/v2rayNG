package com.v2ray.ang.ui.albion

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import org.json.JSONObject

/** Pan/zoom coordinate map for observed game coordinates; no terrain is fabricated. */
class AlbionWorldMapView(context: Context) : View(context) {
    private var entities: List<JSONObject> = emptyList()
    private var player: JSONObject? = null
    private var invalidCount = 0
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val density = resources.displayMetrics.density
    private var pixelsPerUnit = 2.4f * density
    private var panX = 0f
    private var panY = 0f
    private var lastTouchX = 0f
    private var lastTouchY = 0f
    private var scaling = false
    private val scaleDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
            scaling = true
            return true
        }
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            pixelsPerUnit = (pixelsPerUnit * detector.scaleFactor).coerceIn(0.25f * density, 18f * density)
            invalidate()
            return true
        }
        override fun onScaleEnd(detector: ScaleGestureDetector) { scaling = false }
    })

    fun setSnapshot(json: String): Boolean {
        val parsed = AlbionRadarSnapshotParser.parse(json)
        if (parsed == null) {
            entities = emptyList()
            player = null
            invalidCount = 0
            invalidate()
            return false
        }
        entities = parsed.entities
        invalidCount = parsed.invalidCoordinateCount
        player = entities.filter { it.optString("last_kind") == "own_player_move" }
            .maxByOrNull { it.optLong("last_seen_ms") }
        invalidate()
        return true
    }

    fun statusText(): String {
        val you = player
        val resources = entities.count { it.optString("last_kind") in RESOURCE_KINDS }
        val mobs = entities.count { it.optString("last_kind") == "mob" }
        val position = if (you == null) "Player position: waiting for movement" else
            "Player position: %.1f, %.1f".format(java.util.Locale.US, you.optDouble("x"), you.optDouble("y"))
        return "$position | entities=${entities.size} resources=$resources mobs=$mobs invalid=$invalidCount | zone/terrain and player-resource alignment: not verified | other-player coordinates withheld (Event 3 unvalidated)"
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(0xFF101820.toInt())
        val you = player
        val px = you?.optDouble("x") ?: 0.0
        val py = you?.optDouble("y") ?: 0.0
        val centerX = width / 2f + panX
        val centerY = height / 2f + panY

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = density
        paint.color = 0x334E7185
        val spacing = 20f * pixelsPerUnit
        if (spacing > 8f) {
            var x = ((centerX % spacing) + spacing) % spacing
            while (x < width) { canvas.drawLine(x, 0f, x, height.toFloat(), paint); x += spacing }
            var y = ((centerY % spacing) + spacing) % spacing
            while (y < height) { canvas.drawLine(0f, y, width.toFloat(), y, paint); y += spacing }
        }
        paint.style = Paint.Style.FILL
        paint.color = 0xFFB8C8D5.toInt()
        paint.textSize = 10f * density
        canvas.drawText("LOCAL MAP  •  terrain/zone and player-resource alignment unverified", 10f * density, 18f * density, paint)

        if (you == null) {
            paint.textSize = 13f * density
            canvas.drawText("Move in Albion to acquire your position", 12f * density, height / 2f, paint)
            return
        }

        val history = you.optJSONArray("movement_history")
        if (history != null && history.length() > 1) {
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 2f * density
            paint.color = 0xCC56D6A0.toInt()
            var oldX: Float? = null
            var oldY: Float? = null
            for (i in 0 until history.length()) {
                val point = history.optJSONObject(i) ?: continue
                val x = centerX + ((point.optDouble("x") - px) * pixelsPerUnit).toFloat()
                val y = centerY - ((point.optDouble("y") - py) * pixelsPerUnit).toFloat()
                if (x !in 0f..width.toFloat() || y !in 0f..height.toFloat()) {
                    oldX = null
                    oldY = null
                    continue
                }
                if (oldX != null && oldY != null) canvas.drawLine(oldX, oldY, x, y, paint)
                oldX = x
                oldY = y
            }
        }

        entities.forEach { entity ->
            val kind = entity.optString("last_kind")
            if (kind !in DISPLAY_KINDS || kind == "own_player_move") return@forEach
            val x = centerX + ((entity.optDouble("x") - px) * pixelsPerUnit).toFloat()
            val y = centerY - ((entity.optDouble("y") - py) * pixelsPerUnit).toFloat()
            if (x !in -20f..(width + 20f).toFloat() || y !in -20f..(height + 20f).toFloat()) return@forEach
            val candidate = kind == "static_resource" ||
                entity.optString("classification") == "ambiguous_resource_candidate" ||
                entity.optString("evidence_grade") == "conflicting_typed_observation"
            paint.style = Paint.Style.FILL
            paint.color = when {
                kind == "mob" -> 0xFFFF6666.toInt()
                candidate -> 0xFF9AA6AF.toInt()
                else -> 0xFFFFC45C.toInt()
            }
            canvas.drawCircle(x, y, 4.2f * density, paint)
            paint.color = 0xFFEAF1F5.toInt()
            paint.textSize = 9f * density
            val label = AlbionRadarSnapshotParser.labelForEntity(entity)
            canvas.drawText(label, x + 6f * density, y - 5f * density, paint)
        }

        paint.style = Paint.Style.FILL
        paint.color = 0xFF56D6A0.toInt()
        canvas.drawCircle(centerX, centerY, 6f * density, paint)
        paint.color = 0xFFFFFFFF.toInt()
        paint.textSize = 10f * density
        canvas.drawText("YOU  ${"%.1f".format(java.util.Locale.US, px)}, ${"%.1f".format(java.util.Locale.US, py)}", centerX + 9f * density, centerY + 4f * density, paint)
        paint.color = 0xFFB8C8D5.toInt()
        paint.textSize = 9f * density
        canvas.drawText("YOU=green  resource=gold  candidate=grey  mob=red  player=blue", 10f * density, height - 24f * density, paint)
        canvas.drawText("Pinch: zoom  •  Drag: pan  •  1 grid = 20 coordinate units", 10f * density, height - 10f * density, paint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(event)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastTouchX = event.x
                lastTouchY = event.y
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (!scaling && event.pointerCount == 1) {
                    panX += event.x - lastTouchX
                    panY += event.y - lastTouchY
                    invalidate()
                }
                lastTouchX = event.x
                lastTouchY = event.y
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                scaling = false
                return true
            }
        }
        return true
    }

    companion object {
        private val RESOURCE_KINDS = setOf("static_resource", "harvestable", "resource_state")
        private val DISPLAY_KINDS = RESOURCE_KINDS + setOf("mob", "own_player_move")
    }
}
