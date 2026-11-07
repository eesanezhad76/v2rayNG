package com.v2ray.ang.ui.albion

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.View
import org.json.JSONObject
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.roundToInt

/** Transparent resource radar using a north-up, east-right world-coordinate projection. */
class AlbionMiniRadarView(context: Context) : View(context) {
    private data class TemporaryMark(val x: Double, val y: Double, val regionId: String, val expiresAtMs: Long)
    private val temporaryMarks = ArrayList<TemporaryMark>()
    private var entities: List<JSONObject> = emptyList()
    private var catalogEntities: List<JSONObject> = emptyList()
    private var enabledFilters: Set<String> = DEFAULT_FILTERS
    private var catalogOverviewMode = false
    private var lastLiveSnapshotJson: String? = null
    // Retain the last good frame for context, but never treat it as current after a bad payload.
    private var liveSnapshotStale = false
    private var previousRegionEntities: List<JSONObject> = emptyList()
    private var showPreviousRegion = false
    // Filtering is independent of player position. Cache it between snapshots/filter changes
    // so onDraw does not repeatedly walk and classify every entity on each invalidation.
    private var visibleCache: List<JSONObject> = emptyList()
    private var catalogVisibleCache: List<JSONObject> = emptyList()
    private var filterCacheDirty = true
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val density = resources.displayMetrics.density
    private val minimumRange = 260.0
    // North-up world projection; movement never rotates the radar.

    fun setCatalogOverviewMode(enabled: Boolean) {
        catalogOverviewMode = enabled
        invalidate()
    }

    fun setFilters(filters: Set<String>) {
        enabledFilters = filters.toSet()
        filterCacheDirty = true
        invalidate()
    }

    fun setSnapshot(json: String): Int? {
        val parsed = AlbionRadarSnapshotParser.parse(json) ?: run {
            entities = emptyList()
            filterCacheDirty = true
            invalidate()
            return null
        }
        entities = parsed.entities
        filterCacheDirty = true
        invalidate()
        return entities.size
    }

    /** Parse static catalog once; live refreshes only reparse the current-session snapshot. */
    fun setCatalogSnapshot(json: String): Int? {
        val parsed = AlbionRadarSnapshotParser.parse(json) ?: return null
        lastLiveSnapshotJson = null
        catalogEntities = parsed.entities
        entities = catalogEntities
        filterCacheDirty = true
        invalidate()
        return catalogEntities.size
    }

    /** Mark currently discovered, useful resource positions for a short revisit window. */
    fun markCurrentResources(selectedFilters: Set<String>, ttlMs: Long): Int {
        if (ttlMs <= 0L || liveSnapshotStale) return 0
        val player = entities.asSequence()
            .filter { it.optString("last_kind") == "own_player_move" && validPoint(it) }
            .maxByOrNull { it.optLong("last_seen_ms") }
        val regionId = player?.optString("region_id").orEmpty()
        if (regionId.isBlank()) return 0
        val oldFilters = enabledFilters
        enabledFilters = selectedFilters.toSet()
        val now = android.os.SystemClock.elapsedRealtime()
        val candidates = try {
            entities.filter { item ->
                val kind = item.optString("last_kind")
                kind in DISPLAY_KINDS &&
                    item.optString("region_id") == regionId &&
                    validPoint(item) && passesFilters(item) &&
                    AlbionSpatialIntelligence.isEligibleForRadarMark(item)
            }
        } finally {
            enabledFilters = oldFilters
        }
        var added = 0
        candidates.forEach { item ->
            val x = item.optDouble("x")
            val y = item.optDouble("y")
            val exists = temporaryMarks.any {
                it.regionId == regionId && hypot(it.x - x, it.y - y) <= 1.5
            }
            if (!exists) {
                temporaryMarks.add(TemporaryMark(x, y, regionId, now + ttlMs))
                added++
            }
        }
        while (temporaryMarks.size > 500) temporaryMarks.removeAt(0)
        invalidate()
        return added
    }

    fun setPreviousRegionSnapshot(json: String, enabled: Boolean) {
        showPreviousRegion = enabled
        if (enabled) {
            val parsed = AlbionRadarSnapshotParser.parse(json)
            previousRegionEntities = parsed?.entities?.filter {
                it.optBoolean("archived_visit", false) && it.optString("last_kind") in RESOURCE_KINDS
            } ?: emptyList()
        }
        invalidate()
    }

    fun setLiveSnapshot(json: String): Int? {
        // The daemon store returns the same cached snapshot string until an observation
        // changes. A repeated last-good payload also clears a transient stale state.
        if (json == lastLiveSnapshotJson) {
            post {
                if (liveSnapshotStale) {
                    liveSnapshotStale = false
                    invalidate()
                }
            }
            return entities.size
        }
        // Keep the last good entities on parse failure, but mark them stale so they are
        // de-emphasized and cannot drive MARK or navigation.
        val parsed = AlbionRadarSnapshotParser.parse(json)
        if (parsed == null) {
            post {
                liveSnapshotStale = true
                invalidate()
            }
            return null
        }
        lastLiveSnapshotJson = json
        val acceptedCount = parsed.entities.size
        post {
            // Live coordinates belong to the active capture. Never merge historical catalog
            // coordinates into the player-centered map until zone identity is verified.
            entities = parsed.entities
            liveSnapshotStale = false
            filterCacheDirty = true
            invalidate()
        }
        return acceptedCount
    }

    private fun entityMergeKey(item: JSONObject): String {
        val kind = if (item.optString("last_kind") in RESOURCE_KINDS) "resource" else item.optString("last_kind")
        val x = (item.optDouble("x") * 2.0).roundToInt()
        val y = (item.optDouble("y") * 2.0).roundToInt()
        return listOf(kind, item.optInt("family", -1), item.optInt("tier", -1),
            item.optInt("enchant", -1), x, y).joinToString(":")
    }

    private fun validPoint(item: JSONObject): Boolean =
        item.has("x") && item.has("y") &&
            item.optDouble("x").isFinite() && item.optDouble("y").isFinite() &&
            kotlin.math.abs(item.optDouble("x")) <= 10000.0 &&
            kotlin.math.abs(item.optDouble("y")) <= 10000.0

    /** Depleted and departed resource entities are retained in the knowledge store,
     * but are not rendered or offered as navigation targets. A fresh positive
     * harvestable amount clears depletion in the live store and makes the node visible again.
     */
    private fun isUnavailableResource(item: JSONObject): Boolean =
        item.optString("last_kind") in RESOURCE_KINDS &&
            (item.optBoolean("depleted", false) || item.optBoolean("left", false))

    private fun enchantmentOf(item: JSONObject): Int {
        val marker = item.optInt("marker_enchant", -1)
        if (marker in 0..3) return marker
        val inferred = item.optJSONObject("resource_inference")?.optJSONObject("enchantment_name")?.optString("value")
        return when (inferred) {
            "Normal" -> 0
            "E1" -> 1
            "E2" -> 2
            "E3" -> 3
            else -> item.optInt("enchant", -1).takeIf { it in 0..3 } ?: -1
        }
    }

    private fun resourceColor(item: JSONObject): Int = colorForEnchantment(enchantmentOf(item))

    private fun isTroll(item: JSONObject): Boolean =
        AlbionSpatialIntelligence.isTrollCategoryMember(item)

    private fun isOreElemental(item: JSONObject): Boolean =
        item.optString("resource_role") == "ore_elemental" ||
            AlbionSpatialIntelligence.isOreElemental(item)

    private fun passesFilters(item: JSONObject): Boolean {
        val kind = item.optString("last_kind")
        val node = kind in RESOURCE_KINDS
        val mob = kind == "mob"
        val troll = mob && isTroll(item)
        val targetedMob = mob && item.optBoolean("targeted_by_player", false)
        val oreElemental = mob && isOreElemental(item)
        val enchant = enchantmentOf(item)
        val resourceName = item.optJSONObject("resource_inference")
            ?.optJSONObject("resource_name")?.optString("value")?.lowercase().orEmpty()
        val rawName = (item.optString("name") + " " + resourceName + " " +
            AlbionRadarSnapshotParser.labelForEntity(item)).lowercase()
        val markerActivity = item.optString("marker_activity").ifBlank {
            item.optString("marker_activity_candidate")
        }.lowercase()
        val ambiguousResource = item.optString("classification") == "ambiguous_resource_candidate" ||
            item.optString("evidence_grade") == "conflicting_typed_observation"
        val hasTypedOreEvidence = item.optInt("family", -1) == 29 &&
            item.optString("name").isNotBlank() && item.optString("name") != "Resource"
        val family = item.optInt("family", -1)
        val researchResourceType = item.optString("research_catalog_resource_type").lowercase()
        val researchMappingStatus = item.optString("research_catalog_resource_mapping_status")
        // Audited research evidence outranks family-number proximity and variable display labels.
        val resourceCategory = when {
            ambiguousResource || researchMappingStatus == "UNRESOLVED" || researchResourceType == "unknown" -> "unknown_resource"
            item.optBoolean("user_confirmed_target", false) -> "ore"
            researchResourceType == "ore" -> "ore"
            researchResourceType == "rock" -> "stone"
            researchResourceType == "fiber" -> "fiber"
            researchResourceType == "hide" -> "hide"
            family == 27 || family == 29 -> "ore"
            family == 7 -> "stone"
            family == 14 -> "fiber"
            family == 20 -> "hide"
            hasTypedOreEvidence || (rawName.contains("ore") && !rawName.contains("candidate")) || markerActivity == "ore" -> "ore"
            rawName.contains("wood") || rawName.contains("tree") || rawName.contains("logs") || markerActivity == "wood" -> "wood"
            rawName.contains("stone") || rawName.contains("rock") || markerActivity == "stone" -> "stone"
            rawName.contains("fiber") || rawName.contains("cotton") || rawName.contains("flax") || markerActivity == "fiber" -> "fiber"
            rawName.contains("hide") || rawName.contains("animal") || rawName.contains("beast") || markerActivity == "hide" -> "hide"
            else -> "unknown_resource"
        }
        val mobType = item.optString("mob_category").lowercase()
        val mobCategory = when {
            troll -> "troll"
            oreElemental -> "ore_elemental"
            mobType.contains("animal") || mobType.contains("harvest") ||
                rawName.contains("bird") || rawName.contains("wolf") || rawName.contains("boar") ||
                rawName.contains("bear") || rawName.contains("deer") || rawName.contains("animal") -> "animal"
            else -> "other_mob"
        }
        val selectedCategories = enabledFilters.filter { it.startsWith("category_") }
            .map { it.removePrefix("category_") }.toSet()
        // Unknown Event 39 candidates must never leak into selected resource categories.
        // They can be numerous (and are intentionally drawn grey), so show them only
        // when the user explicitly enables "Unclassified Resources". Once Event 40
        // supplies a type/family/name, the same entity is classified and follows its
        // real category filter normally.
        val categoryEnabled = when {
            node -> "category_$resourceCategory" in enabledFilters
            mob -> mobCategory in selectedCategories ||
                (mobCategory == "other_mob" && "category_mobs" in enabledFilters) ||
                (troll && "category_troll" in enabledFilters) ||
                (oreElemental && "category_ore_elemental" in enabledFilters)
            else -> false
        }
        if (!categoryEnabled) return false
        val tierFilters = enabledFilters.filter { it.startsWith("tier_") }
            .mapNotNull { it.removePrefix("tier_").toIntOrNull() }.toSet()
        val markerTier = item.optInt("marker_tier", -1)
        val effectiveTier = if (markerTier >= 0) markerTier else item.optInt("tier", -1)
        // Exact filtering excludes unknown metadata from a selected Tier/Enchantment subset.
        if (!AlbionSpatialIntelligence.passesTierFilter(effectiveTier, tierFilters)) return false
        val enchantFilters = enabledFilters.filter { it.startsWith("enchant_") }
            .mapNotNull { it.removePrefix("enchant_").toIntOrNull() }.filter { it in 0..3 }.toSet()
        // Enchantment applies only to resources; mobs remain unaffected.
        if (node && enchantFilters.isNotEmpty() && (enchant !in 0..3 || enchant !in enchantFilters)) return false
        return true
    }

    private fun visibleEntities(): List<JSONObject> {
        if (filterCacheDirty) {
            // Keep depleted/departed nodes visible as grey historical hints. They remain
            // excluded from nearest-target guidance, but must not silently disappear.
            visibleCache = entities.filter {
                it.optString("last_kind") in DISPLAY_KINDS &&
                    validPoint(it) && passesFilters(it)
            }
            catalogVisibleCache = catalogEntities.filter {
                it.optString("last_kind") in DISPLAY_KINDS &&
                    it.optString("last_kind") != "own_player_move" &&
                    validPoint(it) && passesFilters(it)
            }
            filterCacheDirty = false
        }
        return visibleCache
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f
        val cy = height / 2f
        val radius = (min(width, height) * 0.48f).coerceAtLeast(12f * density)
        val clip = android.graphics.Path().apply { addCircle(cx, cy, radius, android.graphics.Path.Direction.CW) }
        val save = canvas.save()
        canvas.clipPath(clip)

        // The overlay window is translucent; draw no panel, grid, rings, crosshair, or trail.
        if (catalogOverviewMode) {
            drawCatalogOverview(canvas, cx, cy, radius)
            canvas.restoreToCount(save)
            return
        }

        val you = entities.asSequence()
            .filter { it.optString("last_kind") == "own_player_move" && validPoint(it) }
            .maxByOrNull { it.optLong("last_seen_ms") }
        if (you != null) {
            val currentRegionId = you.optString("region_id").takeIf { it.isNotBlank() }
            val history = you.optJSONArray("movement_history")
            // Use the newest authoritative player coordinates. Median smoothing over
            // the last 3 seconds displaced the player origin behind actual movement,
            // making every resource vector lag and swing while the player was walking.
            val px = you.optDouble("x")
            val py = you.optDouble("y")
            val trailPoints = ArrayList<Pair<Double, Double>>()
            if (history != null) {
                val first = (history.length() - 240).coerceAtLeast(0)
                for (i in first until history.length()) {
                    val point = history.optJSONObject(i) ?: continue
                    val tx = point.optDouble("x", Double.NaN)
                    val ty = point.optDouble("y", Double.NaN)
                    if (tx.isFinite() && ty.isFinite()) trailPoints.add(tx to ty)
                }
            }
            val resources = visibleEntities().filter {
                it.optString("last_kind") in RESOURCE_KINDS &&
                    AlbionSpatialIntelligence.isInActiveRegion(currentRegionId, it.optString("region_id"))
            }
            // Mobs obey the same active-region boundary as resources. Other mob categories
            // remain opt-in through the existing category filters.
            val visibleMobs = visibleEntities().filter {
                it.optString("last_kind") == "mob" &&
                    !it.has("superseded_by_harvestable_entity_id") && validPoint(it) &&
                    AlbionSpatialIntelligence.isInActiveRegion(currentRegionId, it.optString("region_id"))
            }
            // Candidate-only Event 39 pins remain visible for investigation but must never
            // drive navigation. Only typed resources or eligible living targets can be
            // highlighted. Keep only three best targets in one O(n) pass.
            val targetCandidates = if (liveSnapshotStale) emptyList() else
                resources + visibleMobs.filter { AlbionSpatialIntelligence.isNavigableResource(it) }
            val nearestTargets = ArrayList<Pair<JSONObject, Double>>(3)
            targetCandidates.forEach { item ->
                if (!isUnavailableResource(item) && validPoint(item) &&
                    AlbionSpatialIntelligence.isNavigableResource(item)) {
                    val distance = hypot(item.optDouble("x") - px, item.optDouble("y") - py)
                    val insertAt = nearestTargets.indexOfFirst { distance < it.second }
                    if (insertAt >= 0) nearestTargets.add(insertAt, item to distance)
                    else if (nearestTargets.size < 3) nearestTargets.add(item to distance)
                    if (nearestTargets.size > 3) nearestTargets.removeAt(3)
                }
            }
            val nearestTarget = nearestTargets.firstOrNull()?.first
            val nearestDistance = nearestTargets.firstOrNull()?.second
            // Fit both nearby resources and the retained movement path so the trail
            // remains a useful route, not a tiny 16-point trace around the center.
            // Keep the local radar at a stable scale. A single remote catalog node must
            // not zoom the entire local view out and make nearby nodes unreadable.
            // Remote targets are clamped to the rim; use the full map for long-range search.
            val displayRange = minimumRange

            // Keep a short live breadcrumb trail visible without adding a panel or grid.
            // Coordinates are translated to the current player origin and use the same
            // fixed north-up transform as resource markers.
            if (trailPoints.size > 1) {
                val trail = android.graphics.Path()
                var started = false
                trailPoints.forEach { (tx, ty) ->
                    val (trX, trY) = rotateOffset(tx - px, ty - py)
                    val sx = cx + (trX / displayRange * radius).toFloat()
                    val sy = cy + (trY / displayRange * radius).toFloat()
                    if (!started) { trail.moveTo(sx, sy); started = true } else trail.lineTo(sx, sy)
                }
                if (started) {
                    paint.style = Paint.Style.STROKE
                    paint.strokeWidth = 2.0f * density
                    paint.strokeCap = Paint.Cap.ROUND
                    paint.strokeJoin = Paint.Join.ROUND
                    paint.color = 0xCC56D6A0.toInt()
                    canvas.drawPath(trail, paint)
                }
            }

            resources.forEach { entity ->
                val dx = entity.optDouble("x") - px
                val dy = entity.optDouble("y") - py
                val distance = hypot(dx, dy)
                val factor = if (distance > displayRange) displayRange / distance else 1.0
                val (rx, ry) = rotateOffset(dx * factor, dy * factor)
                val x = cx + (rx / displayRange * radius).toFloat()
                val y = cy + (ry / displayRange * radius).toFloat()
                paint.style = Paint.Style.FILL
                val trollTarget = entity.optString("manual_activity").equals("Troll", ignoreCase = true) &&
                    entity.optBoolean("manual_marker_identity_proven", false)
                paint.color = when {
                    liveSnapshotStale -> 0xFF78838B.toInt()
                    entity.optString("mob_state") == "mined" || entity.optBoolean("depleted", false) -> 0xFF424A52.toInt()
                    isUnavailableResource(entity) -> 0xFF78838B.toInt()
                    else -> resourceColor(entity)
                }
                canvas.drawCircle(x, y, 3.3f * density, paint)
                if (entity.optString("mob_state") == "dead_harvestable") {
                    paint.style = Paint.Style.STROKE
                    paint.strokeWidth = 1.1f * density
                    paint.color = 0xFFFFFFFF.toInt()
                    canvas.drawCircle(x, y, 5.2f * density, paint)
                    paint.style = Paint.Style.FILL
                }
                val targetRank = nearestTargets.indexOfFirst { it.first === entity }
                if (targetRank in 1..2) {
                    paint.style = Paint.Style.STROKE
                    paint.strokeWidth = 1.4f * density
                    paint.color = if (targetRank == 1) 0xFF9BE7FF.toInt() else 0xFFB8C7D1.toInt()
                    canvas.drawCircle(x, y, (6.0f - targetRank) * density, paint)
                    paint.style = Paint.Style.FILL
                    paint.textSize = 6.5f * density
                    paint.color = 0xFFFFFFFF.toInt()
                    canvas.drawText((targetRank + 1).toString(), x + 5f * density, y + 6f * density, paint)
                }
                if (entity === nearestTarget) {
                    val arrived = (nearestDistance ?: Double.MAX_VALUE) <= 4.0
                    paint.style = Paint.Style.STROKE
                    paint.strokeWidth = 2f * density
                    paint.color = if (arrived) 0xFFFFD54F.toInt() else 0xFF55E6FF.toInt()
                    canvas.drawCircle(x, y, if (arrived) 9f * density else 7f * density, paint)
                    if (arrived) {
                        paint.style = Paint.Style.FILL
                        paint.textSize = 7f * density
                        paint.textAlign = Paint.Align.CENTER
                        paint.color = 0xFFFFE082.toInt()
                        canvas.drawText("HERE", x, y - 11f * density, paint)
                        paint.textAlign = Paint.Align.LEFT
                    }
                }
                // Distant nodes are clamped to the rim to preserve the local radar scale.
                // Show their actual game-coordinate distance so multiple remote bearings
                // are not mistaken for equally distant targets.
                if (distance > displayRange) {
                    paint.color = 0xFFE8EDF2.toInt()
                    paint.textSize = 7f * density
                    paint.textAlign = Paint.Align.CENTER
                    val labelRadius = (radius - 13f * density).coerceAtLeast(0f)
                    val labelX = cx + (rx / displayRange * labelRadius).toFloat()
                    val labelY = cy + (ry / displayRange * labelRadius).toFloat()
                    canvas.drawText(distance.roundToInt().toString(), labelX, labelY, paint)
                    paint.textAlign = Paint.Align.LEFT
                }
                val t = entity.optInt("marker_tier", -1).takeIf { it in 1..8 }
                    ?: entity.optInt("tier", -1).takeIf { it in 1..8 }
                if (t != null) {
                    paint.color = 0xFFFFFFFF.toInt()
                    paint.textSize = 7f * density
                    canvas.drawText(if (trollTarget) "TG$t" else "T$t", x + 4f * density, y - 3f * density, paint)
                }
                if (entity.optBoolean("user_confirmed_target", false)) {
                    paint.style = Paint.Style.STROKE
                    paint.strokeWidth = 1.8f * density
                    paint.color = 0xFF55E6FF.toInt()
                    canvas.drawCircle(x, y, 7f * density, paint)
                }
            }
            // Temporary sweep marks are small triangles. They disappear when a real,
            // typed node is observed at the same position; the real node keeps its enchant color.
            val markNow = android.os.SystemClock.elapsedRealtime()
            temporaryMarks.removeAll { it.expiresAtMs <= markNow }
            if (!liveSnapshotStale) temporaryMarks.filter { it.regionId == currentRegionId }.forEach { mark ->
                val actualNodeAppeared = resources.any { node ->
                    val dxNode = node.optDouble("x") - mark.x
                    val dyNode = node.optDouble("y") - mark.y
                    hypot(dxNode, dyNode) <= 2.5 &&
                        (node.optString("last_kind") in setOf("harvestable", "resource_state") ||
                            (node.optInt("tier", -1) in 2..8 && node.optInt("family", -1) >= 0 &&
                                node.optString("evidence_grade") != "candidate"))
                } || visibleMobs.any { mob ->
                    hypot(mob.optDouble("x") - mark.x, mob.optDouble("y") - mark.y) <= 2.5 &&
                        AlbionSpatialIntelligence.isEligibleForRadarMark(mob)
                }
                if (!actualNodeAppeared) {
                    val dx = mark.x - px
                    val dy = mark.y - py
                    val distance = hypot(dx, dy)
                    val factor = if (distance > displayRange) displayRange / distance else 1.0
                    val (rx, ry) = rotateOffset(dx * factor, dy * factor)
                    val mx = cx + (rx / displayRange * radius).toFloat()
                    val my = cy + (ry / displayRange * radius).toFloat()
                    val size = 3.6f * density
                    val triangle = android.graphics.Path().apply {
                        moveTo(mx, my - size)
                        lineTo(mx - size * 0.86f, my + size * 0.65f)
                        lineTo(mx + size * 0.86f, my + size * 0.65f)
                        close()
                    }
                    paint.style = Paint.Style.FILL
                    paint.color = 0xFFFFD166.toInt()
                    canvas.drawPath(triangle, paint)
                    paint.style = Paint.Style.STROKE
                    paint.strokeWidth = 0.8f * density
                    paint.color = 0xFF302414.toInt()
                    canvas.drawPath(triangle, paint)
                }
            }

            // Previous visit nodes are shown only on demand as grey, non-actionable hints.
            if (showPreviousRegion) {
                previousRegionEntities.forEach { archived ->
                    if (!validPoint(archived)) return@forEach
                    val dx = archived.optDouble("x") - px
                    val dy = archived.optDouble("y") - py
                    val distance = hypot(dx, dy)
                    val factor = if (distance > displayRange) displayRange / distance else 1.0
                    val (rx, ry) = rotateOffset(dx * factor, dy * factor)
                    val x = cx + (rx / displayRange * radius).toFloat()
                    val y = cy + (ry / displayRange * radius).toFloat()
                    paint.style = Paint.Style.STROKE
                    paint.strokeWidth = 1.1f * density
                    paint.color = 0x997F8C96.toInt()
                    canvas.drawCircle(x, y, 4.8f * density, paint)
                    paint.style = Paint.Style.FILL
                    paint.textSize = 5.5f * density
                    paint.color = 0xBBCCD5DC.toInt()
                    canvas.drawText("P", x + 4f * density, y - 3f * density, paint)
                }
            }

            // Show only identity-backed Troll markers and database-confirmed Ore Elementals.
            visibleMobs.forEach { mob ->
                val dx = mob.optDouble("x") - px
                val dy = mob.optDouble("y") - py
                val distance = hypot(dx, dy)
                val factor = if (distance > displayRange) displayRange / distance else 1.0
                val (rx, ry) = rotateOffset(dx * factor, dy * factor)
                val x = cx + (rx / displayRange * radius).toFloat()
                val y = cy + (ry / displayRange * radius).toFloat()
                paint.style = Paint.Style.FILL
                val state = mob.optString("mob_state")
                val dead = mob.optBoolean("mob_dead", false) || mob.optBoolean("left", false) ||
                    state in setOf("dead", "dead_manual", "dead_harvestable", "mined")
                paint.color = when {
                    liveSnapshotStale -> 0xFF78838B.toInt()
                    state == "mined" -> 0xFF424A52.toInt()
                    state in setOf("dead", "dead_manual") -> 0xFFB0BEC5.toInt()
                    dead -> 0xFF78838B.toInt()
                    isTroll(mob) || isOreElemental(mob) -> resourceColor(mob)
                    else -> 0xFFB0BEC5.toInt()
                }
                canvas.drawCircle(x, y, 3.8f * density, paint)
                val targetRank = nearestTargets.indexOfFirst { it.first === mob }
                if (targetRank == 0) {
                    paint.style = Paint.Style.STROKE
                    paint.strokeWidth = 2f * density
                    paint.color = if (distance <= 4.0) 0xFFFFD54F.toInt() else 0xFF55E6FF.toInt()
                    canvas.drawCircle(x, y, if (distance <= 4.0) 9f * density else 7f * density, paint)
                    paint.style = Paint.Style.FILL
                    if (distance <= 4.0) {
                        paint.textSize = 7f * density
                        paint.textAlign = Paint.Align.CENTER
                        paint.color = 0xFFFFE082.toInt()
                        canvas.drawText("HERE", x, y - 11f * density, paint)
                        paint.textAlign = Paint.Align.LEFT
                    }
                } else if (targetRank in 1..2) {
                    paint.style = Paint.Style.STROKE
                    paint.strokeWidth = 1.4f * density
                    paint.color = if (targetRank == 1) 0xFF9BE7FF.toInt() else 0xFFB8C7D1.toInt()
                    canvas.drawCircle(x, y, (6.0f - targetRank) * density, paint)
                    paint.style = Paint.Style.FILL
                    paint.textSize = 6.5f * density
                    paint.color = 0xFFFFFFFF.toInt()
                    canvas.drawText((targetRank + 1).toString(), x + 5f * density, y + 6f * density, paint)
                }
                val t = mob.optInt("marker_tier", -1).takeIf { it in 1..8 }
                    ?: mob.optInt("tier", -1).takeIf { it in 1..8 } ?: -1
                val label = when {
                    isTroll(mob) && t >= 1 -> "TG$t"
                    isOreElemental(mob) && t >= 1 -> "OE$t"
                    else -> null
                }
                if (label != null) {
                    paint.color = 0xFFFFFFFF.toInt()
                    paint.textSize = 7f * density
                    canvas.drawText(label, x + 4f * density, y - 3f * density, paint)
                }
            }
            // Small black center marker remains the authoritative player coordinate.
            drawPlayerMarker(canvas, cx, cy, AlbionMapProjection.movementDirection(history))
        }
        canvas.restoreToCount(save)
    }

    private fun drawPlayerMarker(
        canvas: Canvas, cx: Float, cy: Float, direction: AlbionMapProjection.Point?
    ) {
        paint.style = Paint.Style.FILL
        paint.color = 0xFF080B0E.toInt()
        canvas.drawCircle(cx, cy, 5.0f * density, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.5f * density
        paint.color = 0xFFFFFFFF.toInt()
        canvas.drawCircle(cx, cy, 5.0f * density, paint)
        if (direction != null) {
            val length = 15f * density
            val tipX = cx + (direction.x * length).toFloat()
            val tipY = cy + (direction.y * length).toFloat()
            paint.color = 0xFF55E6FF.toInt()
            paint.strokeWidth = 2.2f * density
            paint.strokeCap = Paint.Cap.ROUND
            canvas.drawLine(cx, cy, tipX, tipY, paint)
            val backX = cx - (direction.x * 4.5f * density).toFloat()
            val backY = cy - (direction.y * 4.5f * density).toFloat()
            val sideX = (-direction.y * 3.5f * density).toFloat()
            val sideY = (direction.x * 3.5f * density).toFloat()
            canvas.drawLine(tipX, tipY, backX + sideX, backY + sideY, paint)
            canvas.drawLine(tipX, tipY, backX - sideX, backY - sideY, paint)
        }
    }

    private fun drawCatalogOverview(canvas: Canvas, cx: Float, cy: Float, radius: Float) {
        visibleEntities()
        val points = catalogVisibleCache.filter { it.optString("last_kind") in RESOURCE_KINDS }
        if (points.isEmpty()) return
        val minX = points.minOf { it.optDouble("x") }
        val maxX = points.maxOf { it.optDouble("x") }
        val minY = points.minOf { it.optDouble("y") }
        val maxY = points.maxOf { it.optDouble("y") }
        val centerX = (minX + maxX) / 2.0
        val centerY = (minY + maxY) / 2.0
        val span = maxOf(maxX - minX, maxY - minY, 1.0)
        val scale = (radius * 1.65 / span).toFloat()
        points.forEach { entity ->
            val (rx, ry) = rotateOffset(entity.optDouble("x") - centerX, entity.optDouble("y") - centerY)
            val x = cx + (rx * scale).toFloat()
            val y = cy + (ry * scale).toFloat()
            paint.style = Paint.Style.FILL
            paint.color = if (isUnavailableResource(entity)) 0xFF78838B.toInt() else resourceColor(entity)
            canvas.drawCircle(x, y, 2.8f * density, paint)
            val t = entity.optInt("marker_tier", -1).takeIf { it in 1..8 }
                ?: entity.optInt("tier", -1).takeIf { it in 1..8 }
            if (t != null) {
                paint.color = 0xFFFFFFFF.toInt()
                paint.textSize = 6.5f * density
                canvas.drawText("T$t", x + 3f * density, y - 2f * density, paint)
            }
        }
    }

    companion object {
        val DEFAULT_FILTERS = setOf(
            "category_ore", "category_wood", "category_stone", "category_fiber",
            "category_hide", "category_unknown_resource", "category_troll", "category_ore_elemental"
        )
        internal fun rotateOffset(dx: Double, dy: Double): Pair<Double, Double> {
            val point = AlbionMapProjection.worldToScreen(dx, dy)
            return point.x to point.y
        }
        internal fun colorForEnchantment(enchant: Int): Int = when (enchant) {
            0 -> 0xFFFFC45C.toInt()
            1 -> 0xFF63D471.toInt()
            2 -> 0xFF55B8FF.toInt()
            3 -> 0xFFB58CFF.toInt()
            else -> 0xFF9AA6AF.toInt()
        }
        private val RESOURCE_KINDS = setOf("static_resource", "harvestable", "resource_state")
        private val DISPLAY_KINDS = RESOURCE_KINDS + setOf("mob")
    }
}
