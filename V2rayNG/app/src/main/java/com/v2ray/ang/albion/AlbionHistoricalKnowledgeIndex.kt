package com.v2ray.ang.albion

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Read-only bridge to the historical knowledge-bundle corpus.
 *
 * Counts and pattern labels are attached as provenance only. This index never
 * changes packet facts, promotes a resource type, or supplies historical map coordinates.
 */
object AlbionHistoricalKnowledgeIndex {
    private const val ENTITY_ASSET = "albion-historical-entity-evidence.json"
    private const val PROTOCOL_ASSET = "albion-historical-protocol-shapes.json"
    private const val LEARNED_ASSET = "albion-historical-learned-patterns.json"
    private const val MANIFEST_ASSET = "albion-historical-knowledge-manifest.json"
    private val RESOURCE_KINDS = setOf("static_resource", "harvestable", "resource_state")

    data class Snapshot internal constructor(
        val entityRecordCount: Int,
        val protocolEventCount: Int,
        val learnedPatternCount: Int,
        val autonomousSignatureCount: Int,
        val protocolShapeCount: Int,
        private val mobileTypes: JSONObject,
        private val resourceFamilies: JSONObject,
        private val learnedPatterns: JSONArray
    ) {
        fun summaryText(): String =
            "Historical memory: $entityRecordCount entities / $protocolEventCount protocol events / " +
                "$learnedPatternCount learned patterns / $autonomousSignatureCount autonomous signatures " +
                "($protocolShapeCount protocol shapes). Evidence only."

        fun annotate(entity: JSONObject) {
            when (entity.optString("last_kind")) {
                "mob" -> annotateMobile(entity)
                in RESOURCE_KINDS -> annotateResource(entity)
            }
        }

        private fun annotateMobile(entity: JSONObject) {
            val typeId = entity.optInt("mobile_type_id", Int.MIN_VALUE)
            if (typeId == Int.MIN_VALUE) return
            val evidence = mobileTypes.optJSONObject(typeId.toString()) ?: return
            entity.put("historical_bundle_mobile_records", evidence.optInt("record_count"))
            entity.put("historical_bundle_mobile_sessions", evidence.optInt("capture_session_count"))
            entity.put("historical_bundle_mobile_sources", evidence.optInt("source_bundle_count"))
            entity.put("historical_bundle_mobile_names", evidence.optJSONObject("names")?.names() ?: JSONArray())
            entity.put("historical_knowledge_source", "four_imported_knowledge_bundles")
        }

        private fun annotateResource(entity: JSONObject) {
            val family = entity.optInt("family", Int.MIN_VALUE)
            if (family == Int.MIN_VALUE) return
            val evidence = resourceFamilies.optJSONObject(family.toString()) ?: return
            entity.put("historical_bundle_family_records", evidence.optInt("record_count"))
            entity.put("historical_bundle_family_sessions", evidence.optInt("capture_session_count"))
            entity.put("historical_bundle_family_sources", evidence.optInt("source_bundle_count"))
            entity.put("historical_bundle_family_tiers", evidence.optJSONArray("tiers") ?: JSONArray())
            entity.put("historical_bundle_family_enchantments", evidence.optJSONArray("enchantments") ?: JSONArray())
            entity.put("historical_knowledge_source", "four_imported_knowledge_bundles")

            val tier = entity.optInt("tier", Int.MIN_VALUE)
            val enchant = entity.optInt("enchant", Int.MIN_VALUE)
            if (tier == Int.MIN_VALUE || enchant == Int.MIN_VALUE) return
            val matches = JSONArray()
            for (i in 0 until learnedPatterns.length()) {
                val pattern = learnedPatterns.optJSONObject(i) ?: continue
                if (pattern.optInt("family", Int.MIN_VALUE) == family &&
                    pattern.optInt("tier", Int.MIN_VALUE) == tier &&
                    pattern.optInt("enchant", Int.MIN_VALUE) == enchant) {
                    matches.put(JSONObject()
                        .put("activity", pattern.optString("activity"))
                        .put("overlay_enchant_label", pattern.optString("overlay_enchant_label"))
                        .put("samples", pattern.optInt("samples"))
                        .put("complete_pairs", pattern.optInt("complete_pairs"))
                        .put("heuristic_score", pattern.optInt("heuristic_score"))
                        .put("source_bundle_count", pattern.optJSONArray("source_bundles")?.length() ?: 0))
                }
            }
            if (matches.length() > 0) {
                entity.put("historical_learned_pattern_matches", matches)
                entity.put("historical_learned_pattern_count", matches.length())
                entity.put("historical_learned_pattern_semantics", "reference evidence only; not used to infer resource or enchantment")
            }
        }
    }

    fun load(context: Context): Snapshot? = try {
        val assets = context.assets
        parse(
            assets.open(ENTITY_ASSET).bufferedReader(Charsets.UTF_8).use { it.readText() },
            assets.open(PROTOCOL_ASSET).bufferedReader(Charsets.UTF_8).use { it.readText() },
            assets.open(LEARNED_ASSET).bufferedReader(Charsets.UTF_8).use { it.readText() },
            assets.open(MANIFEST_ASSET).bufferedReader(Charsets.UTF_8).use { it.readText() }
        )
    } catch (_: Exception) {
        null
    }

    fun parse(entityJson: String, protocolJson: String, learnedJson: String, manifestJson: String): Snapshot? {
        return try {
            val entities = JSONObject(entityJson)
            val protocols = JSONArray(protocolJson)
            val learned = JSONArray(learnedJson)
            val manifest = JSONObject(manifestJson)
            val mobile = entities.optJSONObject("mobile_types")
            val families = entities.optJSONObject("resource_families")
            val entityCount = manifest.optInt("entity_records", -1)
            val protocolCount = manifest.optInt("protocol_events", -1)
            val learnedCount = manifest.optInt("learned_patterns", -1)
            val autonomousCount = manifest.optInt("autonomous_signatures", -1)
            if (mobile == null || families == null ||
                entities.optInt("schema_version", -1) != 1 ||
                manifest.optInt("schema_version", -1) != 1 ||
                entityCount < 0 || protocolCount < 0 || learnedCount < 0 || autonomousCount < 0 ||
                entities.optInt("entity_record_count", -1) != entityCount ||
                learned.length() != learnedCount) null
            else Snapshot(entityCount, protocolCount, learnedCount, autonomousCount, protocols.length(), mobile, families, learned)
        } catch (_: Exception) {
            null
        }
    }
}
