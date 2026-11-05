import com.v2ray.ang.albion.AlbionCaptureReplayEngine
import java.io.File
import java.security.MessageDigest
import java.util.zip.ZipInputStream
import org.json.JSONArray
import org.json.JSONObject

fun main() {
    val repo = File(System.getProperty("user.dir")).canonicalFile
    val inbox = File(repo, "research/inbox")
    val out = File(repo, "research/analysis").apply { mkdirs() }
    require(inbox.isDirectory) { "Missing research/inbox at " + inbox }
    val archives = inbox.walkTopDown().filter { it.isFile && it.extension.equals("zip", true) }.sortedBy { it.name }.toList()
    require(archives.isNotEmpty()) { "No ZIP archives found in research/inbox" }
    val temp = kotlin.io.path.createTempDirectory("albion-pcap-audit-").toFile()
    val index = mutableListOf<String>()
    val allDetails = mutableListOf<Triple<String, String, String>>()
    var total = 0
    var uniquePcapTotal = 0
    var duplicatePcapTotal = 0
    var failed = 0
    val duplicatePcapRows = mutableListOf<String>()
    val replayDir = File(out, "replay").apply { mkdirs() }
    val baselineFile = File(out, "2026-11-02-pcap-replay-baseline.json")
    val baselineByKey = linkedMapOf<String, JSONObject>()
    val baselineExists = baselineFile.isFile
    if (baselineExists) {
        val baselineRoot = JSONObject(baselineFile.readText(Charsets.UTF_8))
        val baselineCaptures = baselineRoot.optJSONArray("captures") ?: JSONArray()
        for (i in 0 until baselineCaptures.length()) {
            val row = baselineCaptures.optJSONObject(i) ?: continue
            val key = row.optString("key")
            if (key.isNotBlank()) baselineByKey[key] = row
        }
    }
    val replayRows = JSONArray()
    val baselineRows = JSONArray()
    try {
        for (archive in archives) {
            val dir = File(temp, archive.nameWithoutExtension).apply { mkdirs() }
            var expanded = 0L
            ZipInputStream(archive.inputStream().buffered()).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    if (!entry.isDirectory && entry.name.lowercase().endsWith(".pcap")) {
                        val target = File(dir, File(entry.name).name)
                        target.outputStream().buffered().use { stream ->
                            val buffer = ByteArray(65536)
                            while (true) {
                                val n = zip.read(buffer)
                                if (n < 0) break
                                expanded += n
                                require(expanded <= 1500000000L) { "Archive expands beyond 1.5 GB: " + archive.name }
                                stream.write(buffer, 0, n)
                            }
                        }
                    }
                    zip.closeEntry()
                }
            }
            val pcaps = dir.walkTopDown().filter { it.isFile && it.extension.equals("pcap", true) }.sortedBy { it.name }.toList()
            println("ARCHIVE " + archive.name + " PCAPS " + pcaps.size)
            index += "## " + archive.name + " (" + pcaps.size + " discovered PCAP files)"
            val seenHashes = linkedMapOf<String, String>()
            for (pcap in pcaps) {
                total++
                val digest = MessageDigest.getInstance("SHA-256")
                pcap.inputStream().buffered().use { input ->
                    val buffer = ByteArray(65536)
                    while (true) {
                        val n = input.read(buffer)
                        if (n < 0) break
                        digest.update(buffer, 0, n)
                    }
                }
                val contentHash = digest.digest().joinToString("") { "%02x".format(it.toInt() and 0xff) }
                val duplicateOf = seenHashes.putIfAbsent(contentHash, pcap.name)
                var replayResult: AlbionCaptureReplayEngine.ReplayResult? = null
                val text = if (duplicateOf != null) {
                    duplicatePcapTotal++
                    duplicatePcapRows += listOf(archive.name, pcap.name, contentHash, duplicateOf)
                        .joinToString(",") { "\"" + it.replace("\"", "\"\"") + "\"" }
                    "DUPLICATE_PCAP: byte-identical to " + duplicateOf + "; excluded from aggregate observations to prevent double counting."
                } else try {
                    uniquePcapTotal++
                    val replay = AlbionCaptureReplayEngine.replay(pcap)
                    replayResult = replay
                    val report = replay.report
                    report.detailedObservations.forEach { allDetails += Triple(archive.name, pcap.name, it) }
                    report.asText()
                } catch (e: Throwable) {
                    failed++
                    "ANALYSIS_ERROR: " + e.javaClass.simpleName + ": " + e.message
                }
                val safe = (archive.nameWithoutExtension + "--" + pcap.nameWithoutExtension)
                    .replace(Regex("[^A-Za-z0-9._-]+"), "_").take(110) + "-" + total.toString().padStart(2, '0')
                val replayKey = archive.name + "::" + pcap.name
                when {
                    duplicateOf != null -> replayRows.put(JSONObject()
                        .put("key", replayKey).put("source_sha256", contentHash)
                        .put("status", "DUPLICATE_SKIPPED").put("duplicate_of", duplicateOf))
                    replayResult == null -> replayRows.put(JSONObject()
                        .put("key", replayKey).put("source_sha256", contentHash)
                        .put("status", "ANALYSIS_ERROR"))
                    else -> {
                        val replay = replayResult!!
                        val report = replay.report
                        val previous = baselineByKey[replayKey]
                        val baselineStatus = when {
                            !baselineExists -> "BASELINE_INITIALIZED"
                            previous == null -> "NEW_CAPTURE_REVIEW"
                            previous.optString("source_sha256") != replay.sourceSha256 -> "SOURCE_CHANGED_REVIEW_REQUIRED"
                            previous.optString("fingerprint") == replay.fingerprint -> "STABLE"
                            else -> "OUTPUT_CHANGED_REVIEW_REQUIRED"
                        }
                        val row = JSONObject()
                            .put("key", replayKey)
                            .put("source_sha256", replay.sourceSha256)
                            .put("fingerprint", replay.fingerprint)
                            .put("status", baselineStatus)
                            .put("ip_packets", report.ipPackets)
                            .put("valid_photon_packets", report.validPhotonPackets)
                            .put("malformed_photon_packets", report.malformedPhotonPackets)
                            .put("quality_score", report.quality.score)
                            .put("quality_grade", report.quality.grade)
                        replayRows.put(row)
                        if (!baselineExists) baselineRows.put(JSONObject()
                            .put("key", replayKey)
                            .put("source_sha256", replay.sourceSha256)
                            .put("fingerprint", replay.fingerprint)
                            .put("ip_packets", report.ipPackets)
                            .put("valid_photon_packets", report.validPhotonPackets)
                            .put("malformed_photon_packets", report.malformedPhotonPackets)
                            .put("quality_score", report.quality.score))
                        AlbionCaptureReplayEngine.writeJson(replay, File(replayDir, safe + "-replay.json"))
                    }
                }
                File(out, safe + "-pcap-report.md").writeText(
                    "# PCAP analysis: " + archive.name + " / " + pcap.name + "\n\n" + text,
                    Charsets.UTF_8
                )
                index += "### " + pcap.name + "\n" + text
                println("PCAP " + archive.name + " :: " + pcap.name + " :: " + text.lineSequence().take(9).joinToString(" | "))
            }
        }
        File(out, "2026-11-02-all-inbox-pcap-analysis.md").writeText(
            "# Full inbox PCAP analysis\n\nArchives: " + archives.size + "\nDiscovered PCAP files: " + total + "\nUnique PCAP files analyzed: " + uniquePcapTotal + "\nByte-identical duplicates skipped: " + duplicatePcapTotal + "\nAnalysis errors: " + failed + "\n\n" + index.joinToString("\n\n") + "\n\nPCAP_AUDIT_COMPLETE archives=" + archives.size + " pcaps=" + total + " unique=" + uniquePcapTotal + " duplicates=" + duplicatePcapTotal + " errors=" + failed,
            Charsets.UTF_8
        )
        File(out, "2026-11-02-pcap-duplicate-inventory.csv").writeText(
            (listOf("archive,duplicate_pcap,sha256,canonical_pcap") + duplicatePcapRows).joinToString("\n") + "\n",
            Charsets.UTF_8
        )
        val replayIndex = JSONObject()
            .put("schema_version", 1)
            .put("generated_from_sha", System.getenv("GITHUB_SHA") ?: "local")
            .put("baseline_file", baselineFile.name)
            .put("captures", replayRows)
        File(out, "2026-11-02-pcap-replay-index.json").writeText(replayIndex.toString(2), Charsets.UTF_8)
        if (!baselineExists && failed == 0 && baselineRows.length() == uniquePcapTotal) {
            baselineFile.writeText(JSONObject()
                .put("schema_version", 1)
                .put("initialized_from_sha", System.getenv("GITHUB_SHA") ?: "local")
                .put("captures", baselineRows).toString(2), Charsets.UTF_8)
        }
        val replayStatuses = linkedMapOf<String, Int>()
        for (i in 0 until replayRows.length()) {
            val status = replayRows.optJSONObject(i)?.optString("status") ?: "UNKNOWN"
            replayStatuses[status] = (replayStatuses[status] ?: 0) + 1
        }
        val changedStatuses = setOf("OUTPUT_CHANGED_REVIEW_REQUIRED", "SOURCE_CHANGED_REVIEW_REQUIRED", "NEW_CAPTURE_REVIEW", "ANALYSIS_ERROR")
        val currentReplayKeys = (0 until replayRows.length()).mapNotNull { replayRows.optJSONObject(it)?.optString("key") }.toSet()
        val changedRows = (0 until replayRows.length()).mapNotNull { replayRows.optJSONObject(it) }
            .filter { it.optString("status") in changedStatuses }
        val missingBaselineKeys = baselineByKey.keys.filterNot { it in currentReplayKeys }
        val regressionText = buildString {
            appendLine("# Offline replay baseline comparison")
            appendLine()
            appendLine("Unique PCAPs analyzed: $uniquePcapTotal")
            appendLine("Byte-identical duplicates skipped: $duplicatePcapTotal")
            appendLine("Analysis errors: $failed")
            appendLine("Baseline: " + if (baselineExists) "existing baseline; unchanged" else if (baselineFile.isFile) "initialized" else "not initialized because analysis had errors")
            replayStatuses.toSortedMap().forEach { (status, count) -> appendLine("- $status: $count") }
            appendLine()
            appendLine("Captures requiring review:")
            if (changedRows.isEmpty()) appendLine("- none")
            else changedRows.forEach { appendLine("- ${it.optString("key")}: ${it.optString("status")}") }
            appendLine("Baseline captures missing from this inbox: ${missingBaselineKeys.size}")
            missingBaselineKeys.forEach { appendLine("- $it") }
            appendLine()
            appendLine("Changed fingerprints are review-required signals, not automatic proof of an algorithm defect.")
            appendLine("Different source hashes are never treated as algorithm regressions.")
        }
        File(out, "2026-11-02-replay-regression-report.md").writeText(regressionText, Charsets.UTF_8)
        File(out, "2026-11-02-all-inbox-observations.jsonl").writeText(
            allDetails.joinToString("\n") { (archive, pcap, detail) ->
                JSONObject().put("archive", archive).put("pcap", pcap).put("detail", detail).toString()
            } + "\n",
            Charsets.UTF_8
        )
        val movements = allDetails.filter { it.third.contains("kind=own_player_move ") }
        val csv = buildString {
            appendLine("archive,pcap,timestamp_ms,x,y,heading,target_x,target_y,speed,source")
            for ((archive, pcap, detail) in movements) {
                val fields = Regex("([A-Za-z_]+)=([^\\s]+)").findAll(detail).associate { it.groupValues[1] to it.groupValues[2] }
                val values = listOf(archive, pcap, fields["t"] ?: "", fields["x"] ?: "", fields["y"] ?: "",
                    fields["heading"] ?: "", fields["target_x"] ?: "", fields["target_y"] ?: "",
                    fields["speed"] ?: "", fields["source"] ?: "")
                appendLine(values.joinToString(",") { "\"" + it.replace("\"", "\"\"") + "\"" })
            }
        }
        File(out, "2026-11-02-own-player-movement-timeline.csv").writeText(csv, Charsets.UTF_8)

        data class NodeAgg(
            val archive: String, val pcap: String, val regionId: String?, val id: String,
            val mobileTypes: MutableSet<String> = sortedSetOf(),
            val families: MutableSet<String> = sortedSetOf(),
            val tiers: MutableSet<String> = sortedSetOf(),
            val enchants: MutableSet<String> = sortedSetOf(),
            val coordinates: MutableSet<String> = sortedSetOf(),
            val event40Amounts: MutableSet<String> = sortedSetOf(),
            val observedAt: MutableList<Long> = mutableListOf(),
            var first: Long = Long.MAX_VALUE,
            var last: Long = Long.MIN_VALUE,
            var observations: Int = 0
        )
        fun field(detail: String, key: String): String? =
            Regex("(?:^| )" + Regex.escape(key) + "=([^ ]+)").find(detail)?.groupValues?.get(1)
        fun csvCell(value: String): String = "\"" + value.replace("\"", "\"\"") + "\""
        val regionTimelineByCapture = linkedMapOf<String, List<Pair<Long, String>>>()
        for ((captureKey, rows) in allDetails.groupBy { it.first + "\u001f" + it.second }) {
            val transitions = rows.mapNotNull { (_, _, detail) ->
                val t = field(detail, "t")?.toLongOrNull() ?: return@mapNotNull null
                val regionId = when {
                    detail.contains("kind=operation_response op=41 ") -> field(detail, "param0_value")
                    detail.contains("kind=operation_response op=2 ") ->
                        Regex("(?:^| )8=([^ ]+)").find(detail)?.groupValues?.get(1)
                    else -> null
                }?.takeIf { it.isNotBlank() && it != "null" } ?: return@mapNotNull null
                t to regionId
            }.sortedBy { it.first }
            regionTimelineByCapture[captureKey] = transitions
        }
        fun regionAt(archive: String, pcap: String, timestampMs: Long): String? =
            regionTimelineByCapture[archive + "\u001f" + pcap].orEmpty()
                .lastOrNull { it.first <= timestampMs }?.second
        fun nodesKey(archive: String, pcap: String, regionId: String?, id: String) =
            archive + "\u001f" + pcap + "\u001f" + (regionId ?: "<unknown-region>") + "\u001f" + id
        val nodes = linkedMapOf<String, NodeAgg>()
        val resourceStates = linkedMapOf<String, MutableList<Pair<Long, String>>>()
        val staticResources = mutableListOf<String>()
        val rawHealthEnergyEvents = mutableListOf<String>()
        data class MobAgg(
            val archive: String, val pcap: String, val regionId: String?, val id: String,
            var mobileType: String = "", var enchant: String = "",
            var name: String = "", var x: String = "", var y: String = "",
            var first: Long = Long.MAX_VALUE, var last: Long = Long.MIN_VALUE,
            var spawnObservations: Int = 0, var movementObservations: Int = 0,
            var healthObservations: Int = 0, var energyObservations: Int = 0,
            var healthRegenerationObservations: Int = 0, var energyRegenerationObservations: Int = 0,
            var stateObservations: Int = 0,
            var minObservedHealth: Double? = null, var maxObservedHealth: Double? = null,
            var lastObservedHealth: Double? = null,
            var minObservedEnergy: Double? = null, var maxObservedEnergy: Double? = null,
            var lastObservedEnergy: Double? = null,
            var lastHealthRegeneration: Double? = null, var lastEnergyRegeneration: Double? = null,
            var leaveAfterSeen: Boolean = false
        )
        val mobs = linkedMapOf<String, MobAgg>()
        for ((archive, pcap, detail) in allDetails) {
            val kind = field(detail, "kind") ?: continue
            val t = field(detail, "t")?.toLongOrNull() ?: 0L
            val regionId = regionAt(archive, pcap, t)
            if (kind == "raw_health_energy_event") {
                val code = Regex("code=(\\d+)").find(detail)?.groupValues?.get(1) ?: ""
                rawHealthEnergyEvents += listOf(archive, pcap, t.toString(), code, detail)
                    .joinToString(",") { csvCell(it) }
                continue
            }
            val id = field(detail, "id") ?: continue
            val key = nodesKey(archive, pcap, regionId, id)
            when (kind) {
                "harvestable" -> {
                    val node = nodes.getOrPut(key) { NodeAgg(archive, pcap, regionId, id) }
                    node.observations++
                    node.first = minOf(node.first, t)
                    node.last = maxOf(node.last, t)
                    node.observedAt += t
                    field(detail, "amount")?.let { node.event40Amounts += it }
                    field(detail, "mobileType")?.let { node.mobileTypes += it }
                    field(detail, "family")?.let { node.families += it }
                    field(detail, "tier")?.let { node.tiers += it }
                    field(detail, "enchant")?.let { node.enchants += it }
                    val x = field(detail, "x"); val y = field(detail, "y")
                    if (x != null && y != null) node.coordinates += "$x,$y"
                }
                "resource_state" -> {
                    val amount = field(detail, "amount") ?: "null"
                    val removed = detail.contains("removed=true")
                    val value = amount + "|removed=" + removed
                    resourceStates.getOrPut(key) { mutableListOf() }.add(t to value)
                }
                "static_resource" -> {
                    staticResources += listOf(archive, pcap, regionId ?: "", t.toString(), id,
                        field(detail, "x") ?: "", field(detail, "y") ?: "").joinToString(",") { csvCell(it) }
                }
                "mob" -> {
                    val mob = mobs.getOrPut(key) { MobAgg(archive, pcap, regionId, id) }
                    mob.spawnObservations++
                    mob.first = minOf(mob.first, t); mob.last = maxOf(mob.last, t)
                    mob.mobileType = field(detail, "mobileType") ?: mob.mobileType
                    mob.enchant = field(detail, "enchant") ?: mob.enchant
                    mob.name = field(detail, "name") ?: mob.name
                    mob.x = field(detail, "x") ?: mob.x; mob.y = field(detail, "y") ?: mob.y
                }
                "move" -> mobs[key]?.let { it.movementObservations++; it.last = maxOf(it.last, t) }
                "health" -> mobs[key]?.let { mob ->
                    mob.healthObservations++; mob.last = maxOf(mob.last, t)
                    field(detail, "secondary_value")?.toDoubleOrNull()?.let { hp ->
                        mob.minObservedHealth = mob.minObservedHealth?.let { minOf(it, hp) } ?: hp
                        mob.maxObservedHealth = mob.maxObservedHealth?.let { maxOf(it, hp) } ?: hp
                        mob.lastObservedHealth = hp
                    }
                }
                "energy" -> mobs[key]?.let { mob ->
                    mob.energyObservations++; mob.last = maxOf(mob.last, t)
                    field(detail, "secondary_value")?.toDoubleOrNull()?.let { energy ->
                        mob.minObservedEnergy = mob.minObservedEnergy?.let { minOf(it, energy) } ?: energy
                        mob.maxObservedEnergy = mob.maxObservedEnergy?.let { maxOf(it, energy) } ?: energy
                        mob.lastObservedEnergy = energy
                    }
                }
                "health_regeneration" -> mobs[key]?.let { mob ->
                    mob.healthRegenerationObservations++; mob.last = maxOf(mob.last, t)
                    mob.lastHealthRegeneration = field(detail, "secondary_value")?.toDoubleOrNull() ?: mob.lastHealthRegeneration
                }
                "energy_regeneration" -> mobs[key]?.let { mob ->
                    mob.energyRegenerationObservations++; mob.last = maxOf(mob.last, t)
                    mob.lastEnergyRegeneration = field(detail, "secondary_value")?.toDoubleOrNull() ?: mob.lastEnergyRegeneration
                }
                "mob_state" -> mobs[key]?.let { it.stateObservations++; it.last = maxOf(it.last, t) }
                "leave" -> mobs[key]?.let { it.leaveAfterSeen = true; it.last = maxOf(it.last, t) }
            }
        }
        val nodeCsv = buildString {
            appendLine("archive,pcap,region_id,entity_id,mobile_type_ids,type_numbers,tiers,enchantment_raw_values,coordinates,event40_amount_values,first_seen_ms,last_seen_ms,observation_count,state_event_count,state_values,identity_scope")
            for ((key, node) in nodes.toSortedMap()) {
                val states = resourceStates[key].orEmpty().sortedBy { it.first }
                val values = listOf(node.archive, node.pcap, node.id,
                    node.mobileTypes.joinToString("|"), node.families.joinToString("|"),
                    node.tiers.joinToString("|"), node.enchants.joinToString("|"),
                    node.coordinates.joinToString("|"), node.event40Amounts.joinToString("|"),
                    node.first.toString(), node.last.toString(), node.observations.toString(), states.size.toString(),
                    states.joinToString(">") { it.second }, "archive+pcap+region+runtime_entity_id")
                appendLine(values.joinToString(",") { csvCell(it) })
            }
        }
        File(out, "2026-11-02-pcap-harvestable-nodes.csv").writeText(nodeCsv, Charsets.UTF_8)

        data class ResourcePatternAgg(
            val family: String, val tier: String, val enchant: String, val mobileType: String,
            val entities: MutableSet<String> = linkedSetOf(),
            val captures: MutableSet<String> = linkedSetOf(),
            var coordinateEntities: Int = 0
        )
        val resourcePatterns = linkedMapOf<String, ResourcePatternAgg>()
        for ((nodeKey, node) in nodes) {
            val families = node.families.filter { it.isNotBlank() && it != "-1" }.ifEmpty { listOf("unknown") }
            val tiers = node.tiers.ifEmpty { listOf("unknown") }
            val enchants = node.enchants.ifEmpty { listOf("unknown") }
            val mobileTypes = node.mobileTypes.ifEmpty { listOf("unknown") }
            for (family in families) for (tier in tiers) for (enchant in enchants) for (mobileType in mobileTypes) {
                val patternKey = listOf(family, tier, enchant, mobileType).joinToString("|")
                val pattern = resourcePatterns.getOrPut(patternKey) {
                    ResourcePatternAgg(family, tier, enchant, mobileType)
                }
                pattern.entities += nodeKey
                pattern.captures += node.archive + "/" + node.pcap + "/" + (node.regionId ?: "<unknown-region>")
                if (node.coordinates.isNotEmpty()) pattern.coordinateEntities++
            }
        }
        val patternCsv = buildString {
            appendLine("family,tier,enchantment_raw,mobile_type,unique_entities,distinct_pcaps,entities_with_coordinates,evidence_strength")
            for (pattern in resourcePatterns.values.sortedWith(
                compareBy<ResourcePatternAgg> { it.family }.thenBy { it.tier }.thenBy { it.enchant }.thenBy { it.mobileType }
            )) {
                val strength = when {
                    pattern.entities.size >= 5 && pattern.captures.size >= 2 -> "repeated_multi_capture"
                    pattern.entities.size >= 2 && pattern.captures.size >= 2 -> "multi_capture_provisional"
                    pattern.entities.size >= 2 -> "single_capture_repeated"
                    else -> "single_observation"
                }
                appendLine(listOf(pattern.family, pattern.tier, pattern.enchant, pattern.mobileType,
                    pattern.entities.size.toString(), pattern.captures.size.toString(),
                    pattern.coordinateEntities.toString(), strength).joinToString(",") { csvCell(it) })
            }
        }
        File(out, "2026-11-02-resource-pattern-frequency.csv").writeText(patternCsv, Charsets.UTF_8)

        // Preserve and correlate manually labelled overlay lifecycle markers with Event 40 nodes.
        // This is candidate evidence only: time/tier/enchantment overlap does not prove entity identity.
        data class OverlayMiningSample(
            val archive: String, val pcap: String, val regionId: String?, val activity: String, val foundMs: Long,
            val tierLabel: String?, val enchantLabel: String?,
            var startedMs: Long? = null, var completedMs: Long? = null, var depletedMs: Long? = null
        )
        val pendingMarkers = linkedMapOf<String, MutableList<OverlayMiningSample>>()
        val markerSamples = mutableListOf<OverlayMiningSample>()
        var unmatchedStarts = 0
        var unmatchedCompletions = 0
        var unmatchedDepletions = 0
        val markerDetails = allDetails.filter { field(it.third, "kind") == "manual_marker" }
            .sortedBy { field(it.third, "t")?.toLongOrNull() ?: 0L }
        fun markerKey(archive: String, pcap: String, regionId: String?, activity: String) =
            archive + "\u001f" + pcap + "\u001f" + (regionId ?: "<unknown-region>") + "\u001f" + activity
        for ((archive, pcap, detail) in markerDetails) {
            val t = field(detail, "t")?.toLongOrNull() ?: continue
            val activity = field(detail, "activity") ?: "Unknown"
            val regionId = regionAt(archive, pcap, t)
            val key = markerKey(archive, pcap, regionId, activity)
            when (field(detail, "event")) {
                "target_found" -> pendingMarkers.getOrPut(key) { mutableListOf() }.add(
                    OverlayMiningSample(archive, pcap, regionId, activity, t, field(detail, "tier"), field(detail, "enchantment"))
                )
                "gathering_started" -> {
                    val sample = pendingMarkers[key]?.firstOrNull { it.startedMs == null && it.completedMs == null }
                    if (sample == null) unmatchedStarts++ else sample.startedMs = t
                }
                "gathering_completed", "mob_killed" -> {
                    // Overlay uses mob_killed for Mob/Mobs/Troll and gathering_completed
                    // for gatherables. Reject cross-event pairing even inside one activity key.
                    val isMobActivity = activity in setOf("Mob", "Mobs", "Troll")
                    val isMatchingCompletion = if (isMobActivity) field(detail, "event") == "mob_killed"
                        else field(detail, "event") == "gathering_completed"
                    if (!isMatchingCompletion) continue
                    val queue = pendingMarkers[key]
                    val sample = if (queue == null) null else
                        queue.firstOrNull { it.startedMs != null && it.completedMs == null }
                            ?: queue.lastOrNull { it.startedMs == null && it.completedMs == null }
                    if (sample == null) unmatchedCompletions++ else {
                        sample.completedMs = t
                        queue?.remove(sample)
                        markerSamples += sample
                    }
                }
                "node_depleted" -> {
                    val sample = markerSamples.firstOrNull {
                        it.archive == archive && it.pcap == pcap && it.regionId == regionId && it.activity == activity &&
                            it.completedMs != null && it.depletedMs == null && it.completedMs!! <= t
                    }
                    if (sample == null) unmatchedDepletions++ else sample.depletedMs = t
                }
            }
        }
        // Keep unpaired Start markers in spatial/family analysis too. A missing End
        // must never erase a node the user explicitly marked as visited.
        val unmatchedFoundMarkers = pendingMarkers.values.sumOf { it.size }
        markerSamples += pendingMarkers.values.flatten()

        fun stateAmount(value: String): Int? = value.substringBefore("|").toIntOrNull()
        fun stateRemoved(value: String): Boolean =
            value.substringAfter("removed=", "false").equals("true", ignoreCase = true)

        fun hasActiveResourceEvidence(
            node: NodeAgg, sample: OverlayMiningSample, windowStart: Long, windowEnd: Long
        ): Boolean {
            val states = resourceStates[nodesKey(node.archive, node.pcap, node.regionId, node.id)].orEmpty().sortedBy { it.first }
            val referenceMs = sample.startedMs ?: sample.foundMs
            val before = states.lastOrNull { it.first <= referenceMs }
            val later = states.filter { it.first > referenceMs && it.first <= windowEnd }
            val beforeDepleted = before?.let {
                stateRemoved(it.second) || (stateAmount(it.second)?.let { amount -> amount <= 0 } == true)
            } == true
            val positiveBefore = before?.let {
                !stateRemoved(it.second) && (stateAmount(it.second)?.let { amount -> amount > 0 } == true)
            } == true
            val positiveLater = later.any {
                !stateRemoved(it.second) && (stateAmount(it.second)?.let { amount -> amount > 0 } == true)
            }
            val event40InWindow = node.observedAt.any { it in windowStart..windowEnd }
            val event40AfterDepletion = beforeDepleted && before != null &&
                node.observedAt.any { it > before.first && it in windowStart..windowEnd }
            if (beforeDepleted && !positiveLater && !event40AfterDepletion) return false
            return event40InWindow || positiveBefore || positiveLater
        }

        val markerCsv = buildString {
            appendLine("archive,pcap,region_id,timestamp_ms,activity,event,tier_label,enchantment_label")
            for ((archive, pcap, detail) in markerDetails) {
                appendLine(listOf(archive, pcap, regionAt(archive, pcap, field(detail, "t")?.toLongOrNull() ?: 0L) ?: "", field(detail, "t") ?: "", field(detail, "activity") ?: "",
                    field(detail, "event") ?: "", field(detail, "tier") ?: "", field(detail, "enchantment") ?: "")
                    .joinToString(",") { csvCell(it) })
            }
        }
        File(out, "2026-11-02-overlay-manual-markers.csv").writeText(markerCsv, Charsets.UTF_8)

        data class FamilyEvidence(
            val activity: String, val family: String,
            val samples: MutableSet<String> = linkedSetOf(),
            val captures: MutableSet<String> = linkedSetOf(),
            var candidateNodes: Int = 0
        )
        val familyEvidence = linkedMapOf<String, FamilyEvidence>()
        val correlationCsv = buildString {
            appendLine("archive,pcap,region_id,activity,found_ms,started_ms,completed_ms,depleted_ms,tier_label,enchantment_label,candidate_node_count,candidate_families,candidate_entity_ids,candidate_evidence_details,inactive_candidate_count,inactive_candidate_entity_ids,match_status")
            for (sample in markerSamples) {
                val tierNumber = sample.tierLabel?.removePrefix("T")?.toIntOrNull()
                val enchantNumber = when (sample.enchantLabel?.uppercase()?.replace(" ", "")) {
                    "NORMAL", "E0", "0" -> "0"
                    "E1", "ENCH1", "ENCHANT1", "ENCHANTMENT1", "1" -> "1"
                    "E2", "ENCH2", "ENCHANT2", "ENCHANTMENT2", "2" -> "2"
                    else -> null
                }
                val windowStart = sample.foundMs - 10_000L
                val windowEnd = (sample.depletedMs ?: sample.completedMs ?: sample.foundMs) + 10_000L
                // Resource-family inference is valid only for resource activities.
                // Mob/Troll markers must never inherit Ore/Wood/Stone/Fiber/Hide families
                // merely because a harvestable node was visible in the same time window.
                val isResourceActivity = sample.activity in setOf("Ore", "Wood", "Stone", "Fiber", "Hide")
                val isMobActivity = sample.activity in setOf("Mob", "Mobs", "Troll")
                // The pattern catalog has a strong, multi-capture mapping for Ore -> family 29.
                // Unknown family IDs remain in the raw node database but are not promoted to Ore.
                val expectedResourceFamily = when (sample.activity) {
                    "Ore" -> "29"
                    else -> null
                }
                val allTemporalResourceCandidates = if (!isResourceActivity) emptyList() else nodes.values.filter { node ->
                    node.archive == sample.archive && node.pcap == sample.pcap && node.regionId == sample.regionId &&
                        node.first <= windowEnd && node.last >= windowStart &&
                        (tierNumber == null || node.tiers.contains(tierNumber.toString())) &&
                        (enchantNumber == null || node.enchants.contains(enchantNumber))
                }
                val temporalResourceCandidates = allTemporalResourceCandidates.filter { node ->
                    hasActiveResourceEvidence(node, sample, windowStart, windowEnd)
                }
                val inactiveResourceCandidates = allTemporalResourceCandidates.filterNot { node ->
                    hasActiveResourceEvidence(node, sample, windowStart, windowEnd)
                }
                val resourceCandidates = if (expectedResourceFamily == null) temporalResourceCandidates
                    else temporalResourceCandidates.filter { it.families.contains(expectedResourceFamily) }
                val reportedResourceCandidates = if (resourceCandidates.isNotEmpty()) resourceCandidates else temporalResourceCandidates
                // Event 123 mob observations are kept in their own entity namespace.
                // They are temporal candidates only, never resource-family evidence.
                val mobCandidates = if (!isMobActivity) emptyList() else mobs.values.filter { mob ->
                    mob.archive == sample.archive && mob.pcap == sample.pcap && mob.regionId == sample.regionId &&
                        mob.first <= windowEnd && mob.last >= windowStart &&
                        (enchantNumber == null || mob.enchant == enchantNumber)
                }
                val candidateCount = if (isMobActivity) mobCandidates.size else reportedResourceCandidates.size
                val familyCounts = linkedMapOf<String, Int>()
                resourceCandidates.forEach { node ->
                    node.families.filter { it.isNotBlank() && it != "-1" }.forEach { family ->
                        familyCounts[family] = (familyCounts[family] ?: 0) + 1
                    }
                }
                val singleFamily = familyCounts.keys.singleOrNull()
                if (singleFamily != null) {
                    val evidenceKey = (sample.regionId ?: "<unknown-region>") + "\u001f" + sample.activity + "\u001f" + singleFamily
                    val evidence = familyEvidence.getOrPut(evidenceKey) { FamilyEvidence(sample.activity, singleFamily) }
                    evidence.samples += sample.archive + "\u001f" + sample.pcap + "\u001f" + (sample.regionId ?: "<unknown-region>") + "\u001f" + sample.foundMs
                    evidence.captures += sample.archive + "/" + sample.pcap + "/" + (sample.regionId ?: "<unknown-region>")
                    evidence.candidateNodes += familyCounts[singleFamily] ?: 0
                }
                val status = when {
                    isMobActivity && mobCandidates.isEmpty() -> "no_event123_mob_candidates"
                    isMobActivity && mobCandidates.size == 1 -> "single_event123_mob_candidate_only"
                    isMobActivity -> "ambiguous_event123_mob_candidates"
                    isResourceActivity && temporalResourceCandidates.isEmpty() && inactiveResourceCandidates.isNotEmpty() -> "only_depleted_or_inactive_candidates"
                    resourceCandidates.isEmpty() && temporalResourceCandidates.isNotEmpty() -> "unverified_family_candidates"
                    resourceCandidates.isEmpty() -> "no_candidates"
                    tierNumber == null || enchantNumber == null -> "incomplete_overlay_labels"
                    familyCounts.isEmpty() -> "family_unavailable"
                    familyCounts.size == 1 -> "single_family_candidate_only"
                    else -> "ambiguous_families"
                }
                val candidateDetails = if (isMobActivity) {
                    mobCandidates.sortedBy { mob ->
                        when {
                            sample.foundMs < mob.first -> mob.first - sample.foundMs
                            sample.foundMs > mob.last -> sample.foundMs - mob.last
                            else -> 0L
                        }
                    }.joinToString(";") { mob ->
                        listOf(mob.id, mob.mobileType, mob.enchant, mob.name, mob.x, mob.y,
                            mob.first.toString(), mob.last.toString()).joinToString(":")
                    }
                } else {
                    reportedResourceCandidates.sortedBy { node ->
                        when {
                            sample.foundMs < node.first -> node.first - sample.foundMs
                            sample.foundMs > node.last -> sample.foundMs - node.last
                            else -> 0L
                        }
                    }.joinToString(";") { node ->
                        val gap = when {
                            sample.foundMs < node.first -> node.first - sample.foundMs
                            sample.foundMs > node.last -> sample.foundMs - node.last
                            else -> 0L
                        }
                        listOf(node.id, node.families.joinToString("|"), node.tiers.joinToString("|"),
                            node.enchants.joinToString("|"), node.mobileTypes.joinToString("|"),
                            node.coordinates.joinToString("|"), node.first.toString(), node.last.toString(), gap.toString())
                            .joinToString(":")
                    }
                }
                appendLine(listOf(sample.archive, sample.pcap, sample.regionId ?: "", sample.activity, sample.foundMs.toString(),
                    sample.startedMs?.toString() ?: "", sample.completedMs?.toString() ?: "", sample.depletedMs?.toString() ?: "",
                    sample.tierLabel ?: "", sample.enchantLabel ?: "", candidateCount.toString(),
                    familyCounts.entries.sortedBy { it.key }.joinToString("|") { it.key + ":" + it.value },
                    (if (isMobActivity) mobCandidates.map { it.id } else reportedResourceCandidates.map { it.id }).joinToString("|"),
                    candidateDetails, inactiveResourceCandidates.size.toString(),
                    inactiveResourceCandidates.map { it.id }.joinToString("|"), status).joinToString(",") { csvCell(it) })
            }
        }
        File(out, "2026-11-02-overlay-family-correlation.csv").writeText(correlationCsv, Charsets.UTF_8)

        // Rank compatible Ore candidates by proximity to the nearest valid player position.
        // This is spatial evidence only and does not prove entity identity.
        data class PlayerPosition(val regionId: String?, val t: Long, val x: Double, val y: Double, val source: String)
        val playerPositions = allDetails.mapNotNull { (archive, pcap, detail) ->
            if (field(detail, "kind") != "own_player_move") return@mapNotNull null
            val t = field(detail, "t")?.toLongOrNull() ?: return@mapNotNull null
            val x = field(detail, "x")?.toDoubleOrNull() ?: return@mapNotNull null
            val y = field(detail, "y")?.toDoubleOrNull() ?: return@mapNotNull null
            if (!x.isFinite() || !y.isFinite() || kotlin.math.abs(x) > 10000.0 || kotlin.math.abs(y) > 10000.0) return@mapNotNull null
            val regionId = regionAt(archive, pcap, t)
            Triple(archive, pcap, regionId to PlayerPosition(regionId, t, x, y, field(detail, "source") ?: "unknown"))
        }.groupBy { it.first + "::" + it.second + "::" + (it.third.first ?: "<unknown-region>") }
        val spatialCsv = buildString {
            appendLine("archive,pcap,region_id,activity,found_ms,reference_ms,player_position_ms,player_position_gap_ms,player_x,player_y,player_position_source,candidate_rank,entity_id,family,tier,enchantment,mobile_type,ore_x,ore_y,distance_game_units,spatial_evidence")
            for (sample in markerSamples) {
                val referenceMs = sample.startedMs ?: sample.foundMs
                if (sample.activity !in setOf("Ore", "Wood", "Stone", "Fiber", "Hide")) {
                    appendLine(listOf(sample.archive, sample.pcap, sample.regionId ?: "", sample.activity, sample.foundMs.toString(),
                        referenceMs.toString(), "", "", "", "", "", "", "", "", "", "", "", "", "", "",
                        "not_a_resource_spatial_candidate").joinToString(",") { csvCell(it) })
                    continue
                }
                val scoped = sample.archive + "::" + sample.pcap + "::" + (sample.regionId ?: "<unknown-region>")
                val nearestPlayer = playerPositions[scoped].orEmpty().map { it.third.second }
                    .minByOrNull { kotlin.math.abs(it.t - referenceMs) }
                    ?.takeIf { kotlin.math.abs(it.t - referenceMs) <= 5000L }
                if (nearestPlayer == null) {
                    appendLine(listOf(sample.archive, sample.pcap, sample.regionId ?: "", sample.activity, sample.foundMs.toString(),
                        referenceMs.toString(), "", "", "", "", "", "", "", "", "", "", "", "", "", "",
                        "no_valid_player_position_within_5000ms").joinToString(",") { csvCell(it) })
                    continue
                }
                val tierNumber = sample.tierLabel?.removePrefix("T")?.toIntOrNull()
                val enchantNumber = when (sample.enchantLabel?.uppercase()?.replace(" ", "")) {
                    "NORMAL", "E0", "0" -> "0"
                    "E1", "ENCH1", "ENCHANT1", "ENCHANTMENT1", "1" -> "1"
                    "E2", "ENCH2", "ENCHANT2", "ENCHANTMENT2", "2" -> "2"
                    else -> null
                }
                val windowStart = sample.foundMs - 10000L
                val windowEnd = (sample.depletedMs ?: sample.completedMs ?: sample.foundMs) + 10000L
                val spatialTemporalNodes = nodes.values.filter { node ->
                    node.archive == sample.archive && node.pcap == sample.pcap &&
                        node.first <= windowEnd && node.last >= windowStart &&
                        (tierNumber == null || node.tiers.contains(tierNumber.toString())) &&
                        (enchantNumber == null || node.enchants.contains(enchantNumber))
                }
                val inactiveSpatialIds = spatialTemporalNodes.filterNot { node ->
                    hasActiveResourceEvidence(node, sample, windowStart, windowEnd)
                }.map { it.id }
                val ranked = spatialTemporalNodes.asSequence()
                    .filter { node -> hasActiveResourceEvidence(node, sample, windowStart, windowEnd) }
                    .flatMap { node ->
                        node.coordinates.asSequence().mapNotNull coordinateLoop@ { coordinate ->
                            val pair = coordinate.split(",")
                            if (pair.size != 2) return@coordinateLoop null
                            val ox = pair[0].toDoubleOrNull() ?: return@coordinateLoop null
                            val oy = pair[1].toDoubleOrNull() ?: return@coordinateLoop null
                            if (!ox.isFinite() || !oy.isFinite() || kotlin.math.abs(ox) > 10000.0 || kotlin.math.abs(oy) > 10000.0) return@coordinateLoop null
                            Triple(node, coordinate, kotlin.math.hypot(nearestPlayer.x - ox, nearestPlayer.y - oy))
                        }
                    }.sortedBy { it.third }.toList()
                if (ranked.isEmpty()) {
                    appendLine(listOf(sample.archive, sample.pcap, sample.regionId ?: "", sample.activity, sample.foundMs.toString(),
                        referenceMs.toString(), nearestPlayer.t.toString(), kotlin.math.abs(nearestPlayer.t-referenceMs).toString(),
                        nearestPlayer.x.toString(), nearestPlayer.y.toString(), nearestPlayer.source, "", "", "", "", "", "",
                        "", "", "", if (inactiveSpatialIds.isNotEmpty())
                            "only_depleted_or_inactive_candidates:" + inactiveSpatialIds.joinToString("|")
                        else "no_coordinate_bearing_time_tier_candidate").joinToString(",") { csvCell(it) })
                } else {
                    ranked.forEachIndexed { index, item ->
                        val node = item.first
                        val coords = item.second.split(",")
                        appendLine(listOf(sample.archive, sample.pcap, sample.activity, sample.foundMs.toString(),
                            referenceMs.toString(), nearestPlayer.t.toString(), kotlin.math.abs(nearestPlayer.t-referenceMs).toString(),
                            nearestPlayer.x.toString(), nearestPlayer.y.toString(), nearestPlayer.source, (index + 1).toString(),
                            node.id, node.families.joinToString("|"), node.tiers.joinToString("|"), node.enchants.joinToString("|"),
                            node.mobileTypes.joinToString("|"), coords[0], coords[1], item.third.toString(),
                            "nearest_player_position_rank_only_not_identity_proof").joinToString(",") { csvCell(it) })
                    }
                }
            }
        }
        File(out, "2026-11-02-overlay-spatial-correlation.csv").writeText(spatialCsv, Charsets.UTF_8)

        val familySummaryCsv = buildString {
            appendLine("overlay_activity,candidate_family,samples_with_single_family,distinct_pcaps,candidate_nodes,evidence_status")
            for (evidence in familyEvidence.values.sortedWith(compareBy<FamilyEvidence> { it.activity }.thenBy { it.family })) {
                val status = if (evidence.samples.size >= 3 && evidence.captures.size >= 2) "repeated_candidate_mapping" else "provisional_candidate_mapping"
                appendLine(listOf(evidence.activity, evidence.family, evidence.samples.size.toString(),
                    evidence.captures.size.toString(), evidence.candidateNodes.toString(), status)
                    .joinToString(",") { csvCell(it) })
            }
        }
        File(out, "2026-11-02-overlay-family-candidate-summary.csv").writeText(familySummaryCsv, Charsets.UTF_8)
        println("OVERLAY_CORRELATION markers=${markerDetails.size} completed_samples=${markerSamples.size} single_family_candidates=${familyEvidence.size} unmatched_start=$unmatchedStarts unmatched_found=$unmatchedFoundMarkers unmatched_completed=$unmatchedCompletions unmatched_depleted=$unmatchedDepletions")

        val unmatchedStates = resourceStates.filterKeys { !nodes.containsKey(it) }
        val stateCsv = buildString {
            appendLine("archive,pcap,entity_id,first_state_ms,last_state_ms,state_event_count,state_values,has_event40_location")
            for ((key, states) in resourceStates.toSortedMap()) {
                val parts = key.split("\u001f")
                val ordered = states.sortedBy { it.first }
                val vals = listOf(parts[0], parts[1], parts[2], parts[3], ordered.firstOrNull()?.first?.toString() ?: "",
                    ordered.lastOrNull()?.first?.toString() ?: "", ordered.size.toString(),
                    ordered.joinToString(">") { it.second }, if (nodes.containsKey(key)) "true" else "false")
                appendLine(vals.joinToString(",") { csvCell(it) })
            }
        }
        File(out, "2026-11-02-pcap-resource-state-timelines.csv").writeText(stateCsv, Charsets.UTF_8)
        File(out, "2026-11-02-pcap-static-resources.csv").writeText(
            (listOf("archive,pcap,region_id,timestamp_ms,entity_id,x,y") + staticResources).joinToString("\n") + "\n",
            Charsets.UTF_8
        )
        val mobCsv = buildString {
            appendLine("archive,pcap,region_id,entity_id,mobile_type_id,enchantment,name,x,y,first_seen_ms,last_seen_ms,spawn_observations,movement_observations,health_observations,energy_observations,health_regeneration_observations,energy_regeneration_observations,min_observed_health,max_observed_health,last_observed_health,min_observed_energy,max_observed_energy,last_observed_energy,last_health_regeneration,last_energy_regeneration,state_observations,left_after_seen,interpretation")
            for ((_, mob) in mobs.toSortedMap()) {
                val values = listOf(mob.archive, mob.pcap, mob.regionId ?: "", mob.id, mob.mobileType, mob.enchant,
                    mob.name, mob.x, mob.y, mob.first.toString(), mob.last.toString(),
                    mob.spawnObservations.toString(), mob.movementObservations.toString(),
                    mob.healthObservations.toString(), mob.energyObservations.toString(),
                    mob.healthRegenerationObservations.toString(), mob.energyRegenerationObservations.toString(),
                    mob.minObservedHealth?.toString() ?: "", mob.maxObservedHealth?.toString() ?: "",
                    mob.lastObservedHealth?.toString() ?: "", mob.minObservedEnergy?.toString() ?: "",
                    mob.maxObservedEnergy?.toString() ?: "", mob.lastObservedEnergy?.toString() ?: "",
                    mob.lastHealthRegeneration?.toString() ?: "", mob.lastEnergyRegeneration?.toString() ?: "",
                    mob.stateObservations.toString(), mob.leaveAfterSeen.toString(),
                    if (mob.leaveAfterSeen) "entity_leave_observed_not_proof_of_kill" else "no_leave_observed_in_capture")
                appendLine(values.joinToString(",") { csvCell(it) })
            }
        }
        File(out, "2026-11-02-pcap-mob-lifecycle.csv").writeText(mobCsv, Charsets.UTF_8)
        File(out, "2026-11-02-pcap-health-energy-events.csv").writeText(
            (listOf("archive,pcap,timestamp_ms,event_code,raw_detail") + rawHealthEnergyEvents).joinToString("\n") + "\n",
            Charsets.UTF_8
        )

        data class AttackSample(val t: Long, val flag: String, val requestId: String)
        data class PositionSample(val t: Long, val x: Double, val y: Double)
        val attacks = linkedMapOf<String, MutableList<AttackSample>>()
        val targetPositions = linkedMapOf<String, MutableList<PositionSample>>()
        val playerIds = mutableSetOf<String>()
        val event123Types = mutableMapOf<String, String>()
        val event123Names = mutableMapOf<String, String>()
        val leaveKeys = mutableSetOf<String>()
        val leaveTimes = mutableMapOf<String, Long>()
        val healthCounts = mutableMapOf<String, Int>()
        val energyCounts = mutableMapOf<String, Int>()
        val healthCurrentSamples = mutableMapOf<String, MutableList<Double>>()
        val energyCurrentSamples = mutableMapOf<String, MutableList<Double>>()
        val healthDeltaSamples = mutableMapOf<String, MutableList<Double>>()
        val healthSourceIds = mutableMapOf<String, MutableSet<String>>()
        fun littleFloat(hex: String, byteOffset: Int): Double? {
            val start = byteOffset * 2
            if (start + 8 > hex.length) return null
            return try {
                val b0 = hex.substring(start, start + 2).toInt(16)
                val b1 = hex.substring(start + 2, start + 4).toInt(16)
                val b2 = hex.substring(start + 4, start + 6).toInt(16)
                val b3 = hex.substring(start + 6, start + 8).toInt(16)
                val bits = b0 or (b1 shl 8) or (b2 shl 16) or (b3 shl 24)
                java.lang.Float.intBitsToFloat(bits).toDouble().takeIf { it.isFinite() && kotlin.math.abs(it) <= 10000.0 }
            } catch (_: Exception) { null }
        }
        for ((archive, pcap, detail) in allDetails) {
            val t = field(detail, "t")?.toLongOrNull() ?: 0L
            val regionId = regionAt(archive, pcap, t)
            val keyFor = { id: String -> nodesKey(archive, pcap, regionId, id) }
            if (detail.contains("kind=player_spawn")) {
                field(detail, "id")?.let { playerIds += keyFor(it) }
            }
            if (detail.contains("kind=mob")) {
                val id = field(detail, "id")
                if (id != null) {
                    val key = keyFor(id)
                    field(detail, "mobileType")?.let { event123Types[key] = it }
                    field(detail, "name")?.let { event123Names[key] = it }
                }
            }
            if (detail.contains("kind=leave")) field(detail, "id")?.let {
                val key = keyFor(it); leaveKeys += key; leaveTimes[key] = t
            }
            if (detail.contains("kind=health")) field(detail, "id")?.let { id ->
                val key = keyFor(id)
                healthCounts[key] = (healthCounts[key] ?: 0) + 1
                field(detail, "secondary_value")?.toDoubleOrNull()?.let { healthCurrentSamples.getOrPut(key) { mutableListOf() }.add(it) }
                field(detail, "value")?.toDoubleOrNull()?.let { healthDeltaSamples.getOrPut(key) { mutableListOf() }.add(it) }
                field(detail, "source_entity_id")?.let { healthSourceIds.getOrPut(key) { sortedSetOf() }.add(it) }
            }
            if (detail.contains("kind=energy")) field(detail, "id")?.let { id ->
                val key = keyFor(id)
                energyCounts[key] = (energyCounts[key] ?: 0) + 1
                field(detail, "secondary_value")?.toDoubleOrNull()?.let { energyCurrentSamples.getOrPut(key) { mutableListOf() }.add(it) }
            }
            if (detail.contains("outbound_request op=23")) {
                val target = field(detail, "1")
                if (target != null) {
                    val requestId = field(detail, "0") ?: ""
                    val flag = field(detail, "2") ?: ""
                    attacks.getOrPut(keyFor(target)) { mutableListOf() }.add(AttackSample(t, flag, requestId))
                }
            }
            if (detail.contains(" event3 id=")) {
                val id = Regex("event3 id=(\\d+)").find(detail)?.groupValues?.get(1)
                val raw = Regex("raw=([0-9a-fA-F]+)").find(detail)?.groupValues?.get(1)?.lowercase()
                if (id != null && raw != null && raw.length >= 34 && raw.take(2) == "03") {
                    val x = littleFloat(raw, 9); val y = littleFloat(raw, 13)
                    if (x != null && y != null) {
                        targetPositions.getOrPut(keyFor(id)) { mutableListOf() }.add(PositionSample(t, x, y))
                    }
                }
            }
        }
        val attackCsv = buildString {
            appendLine("archive,pcap,region_id,target_entity_id,attack_action_count,attack_flag_values,first_attack_ms,last_attack_ms,event3_position_samples,first_position_x,first_position_y,last_position_x,last_position_y,min_x,max_x,min_y,max_y,event123_mobile_type,event123_name,known_player_spawn,leave_observed,leave_after_last_attack,health_observations,health_delta_samples,sum_health_delta,health_source_entity_ids,min_current_health,max_current_health,last_current_health,energy_observations,min_current_energy,max_current_energy,last_current_energy,interpretation")
            for ((key, values) in attacks.toSortedMap()) {
                val parts = key.split("\u001f")
                val ordered = values.sortedBy { it.t }
                val positions = targetPositions[key].orEmpty().sortedBy { it.t }
                val xs = positions.map { it.x }; val ys = positions.map { it.y }
                val vals = listOf(
                    parts[0], parts[1], parts[2], parts[3], ordered.size.toString(),
                    ordered.map { it.flag }.distinct().joinToString("|"),
                    ordered.firstOrNull()?.t?.toString() ?: "", ordered.lastOrNull()?.t?.toString() ?: "",
                    positions.size.toString(), positions.firstOrNull()?.x?.toString() ?: "",
                    positions.firstOrNull()?.y?.toString() ?: "", positions.lastOrNull()?.x?.toString() ?: "",
                    positions.lastOrNull()?.y?.toString() ?: "",
                    xs.minOrNull()?.toString() ?: "", xs.maxOrNull()?.toString() ?: "",
                    ys.minOrNull()?.toString() ?: "", ys.maxOrNull()?.toString() ?: "",
                    event123Types[key] ?: "", event123Names[key] ?: "",
                    (key in playerIds).toString(), (key in leaveKeys).toString(),
                    ((leaveTimes[key] ?: Long.MIN_VALUE) > (ordered.lastOrNull()?.t ?: Long.MAX_VALUE)).toString(),
                    (healthCounts[key] ?: 0).toString(),
                    (healthDeltaSamples[key]?.size ?: 0).toString(),
                    healthDeltaSamples[key]?.sum()?.toString() ?: "",
                    healthSourceIds[key]?.joinToString("|") ?: "",
                    healthCurrentSamples[key]?.minOrNull()?.toString() ?: "",
                    healthCurrentSamples[key]?.maxOrNull()?.toString() ?: "",
                    healthCurrentSamples[key]?.lastOrNull()?.toString() ?: "",
                    (energyCounts[key] ?: 0).toString(),
                    energyCurrentSamples[key]?.minOrNull()?.toString() ?: "",
                    energyCurrentSamples[key]?.maxOrNull()?.toString() ?: "",
                    energyCurrentSamples[key]?.lastOrNull()?.toString() ?: "",
                    when {
                        key in playerIds -> "player_spawn_known_do_not_classify_as_mob"
                        (leaveTimes[key] ?: Long.MIN_VALUE) > (ordered.lastOrNull()?.t ?: Long.MAX_VALUE) &&
                            (healthCurrentSamples[key]?.minOrNull() ?: Double.POSITIVE_INFINITY) <= 5.0 ->
                            "health_at_or_below_5_then_leave_after_attack_strong_defeat_candidate"
                        (leaveTimes[key] ?: Long.MIN_VALUE) > (ordered.lastOrNull()?.t ?: Long.MAX_VALUE) &&
                            (healthCurrentSamples[key]?.isNotEmpty() == true) ->
                            "health_updates_then_leave_after_attack_defeat_likely_not_confirmed"
                        (leaveTimes[key] ?: Long.MIN_VALUE) > (ordered.lastOrNull()?.t ?: Long.MAX_VALUE) ->
                            "leave_after_attack_but_no_target_health_samples_defeat_unconfirmed"
                        event123Types.containsKey(key) -> "event123_mobile_entity_attack_target_kill_not_proven"
                        positions.isNotEmpty() -> "attack_target_with_plausible_mode3_movement_kill_not_proven"
                        else -> "attack_target_only_entity_type_unresolved_kill_not_proven"
                    }
                )
                appendLine(vals.joinToString(",") { csvCell(it) })
            }
        }
        File(out, "2026-11-02-pcap-attack-target-tracks.csv").writeText(attackCsv, Charsets.UTF_8)

        // Correlate manually tagged Mob/Troll cycles with exact outbound operation-23
        // target IDs. This is ranked evidence, not proof that a particular marker names
        // a particular entity; retain all candidates and ambiguity.
        val mobileTypeDictionary = runCatching {
            JSONObject(File(repo, "research/database/albion-mobile-type-dictionary.json").readText(Charsets.UTF_8))
                .optJSONObject("entries") ?: JSONObject()
        }.getOrDefault(JSONObject())
        fun resolvedMobName(typeId: String, eventName: String): String {
            if (eventName.isNotBlank()) return eventName
            return mobileTypeDictionary.optJSONObject(typeId)?.optString("display_name", "") ?: ""
        }
        val mobMarkerCorrelationCsv = buildString {
            appendLine("archive,pcap,region_id,activity,found_ms,mob_killed_ms,candidate_count,best_target_entity_id,mobile_type_id,resolved_mob_name,mob_tier,mob_category,first_attack_ms,last_attack_ms,start_to_attack_delta_ms,attack_interval_gap_ms,health_observations,leave_after_last_attack,confidence,identity_proven,candidate_evidence")
            for (sample in markerSamples.filter { it.activity in setOf("Mob", "Mobs", "Troll") }) {
                val prefix = sample.archive + "\u001f" + sample.pcap + "\u001f" + (sample.regionId ?: "<unknown-region>") + "\u001f"
                val finishMs = sample.completedMs ?: (sample.foundMs + 15_000L)
                data class MobAttackCandidate(
                    val id: String, val first: Long, val last: Long, val actions: Int,
                    val health: Int, val leaveAfter: Boolean, val type: String, val name: String,
                    val delta: Long, val intervalGap: Long
                )
                val candidates = attacks.entries.mapNotNull { (key, values) ->
                    if (!key.startsWith(prefix) || key in playerIds || !event123Types.containsKey(key)) return@mapNotNull null
                    val ordered = values.sortedBy { it.t }
                    val first = ordered.firstOrNull()?.t ?: return@mapNotNull null
                    val last = ordered.lastOrNull()?.t ?: return@mapNotNull null
                    val delta = first - sample.foundMs
                    if (last < sample.foundMs - 1500L || first > finishMs + 2500L) return@mapNotNull null
                    val intervalGap = if (first <= sample.foundMs && sample.foundMs <= last) 0L
                        else minOf(kotlin.math.abs(first - sample.foundMs), kotlin.math.abs(last - sample.foundMs))
                    val health = healthCounts[key] ?: 0
                    val leaveAfter = (leaveTimes[key] ?: Long.MIN_VALUE) > last
                    if (health <= 0 && !leaveAfter) return@mapNotNull null
                    MobAttackCandidate(
                        key.substringAfterLast("\u001f"), first, last, ordered.size, health, leaveAfter,
                        event123Types[key] ?: "", event123Names[key] ?: "", delta, intervalGap
                    )
                }.sortedWith(compareBy<MobAttackCandidate> { it.intervalGap }
                    .thenByDescending { it.health }.thenByDescending { it.actions })
                val best = candidates.firstOrNull()
                val nearby = if (best == null) emptyList() else candidates.filter {
                    it.intervalGap <= best.intervalGap + 1500L
                }
                val confidence = when {
                    best == null -> "NONE"
                    nearby.size > 1 -> "AMBIGUOUS"
                    best.intervalGap <= 3000L && best.health > 0 && best.leaveAfter -> "HIGH"
                    best.intervalGap <= 8000L && best.health > 0 -> "MEDIUM"
                    else -> "LOW"
                }
                val candidateEvidence = candidates.joinToString(";") { c ->
                    listOf(c.id, c.type, c.name, c.first.toString(), c.last.toString(),
                        c.delta.toString(), c.intervalGap.toString(), c.health.toString(), c.leaveAfter.toString(), c.actions.toString())
                        .joinToString(":")
                }
                appendLine(listOf(sample.archive, sample.pcap, sample.activity, sample.foundMs.toString(),
                    sample.completedMs?.toString() ?: "", candidates.size.toString(), best?.id ?: "",
                    best?.type ?: "", best?.let { resolvedMobName(it.type, it.name) } ?: "",
                    best?.let { mobileTypeDictionary.optJSONObject(it.type)?.opt("tier")?.toString() ?: "" } ?: "",
                    best?.let { mobileTypeDictionary.optJSONObject(it.type)?.optString("category", "") ?: "" } ?: "",
                    best?.first?.toString() ?: "", best?.last?.toString() ?: "", best?.delta?.toString() ?: "",
                    best?.intervalGap?.toString() ?: "", best?.health?.toString() ?: "", best?.leaveAfter?.toString() ?: "",
                    confidence, "false", candidateEvidence).joinToString(",") { csvCell(it) })
            }
        }
        File(out, "2026-11-02-overlay-mob-attack-correlation.csv").writeText(mobMarkerCorrelationCsv, Charsets.UTF_8)

        val databaseDir = File(repo, "research/database").apply { mkdirs() }
        // Keep a compact, searchable record of join/change-cluster parameters.
        // These are candidate region identifiers only; their meaning is not inferred here.
        val regionTransitionDetails = allDetails.filter { (_, _, detail) ->
            detail.contains("kind=operation_response op=2 ") ||
                detail.contains("kind=operation_response op=41 ") ||
                detail.contains("outbound_request op=2 ")
        }
        val regionTransitionCsv = buildString {
            appendLine("archive,pcap,detail")
            for ((archive, pcap, detail) in regionTransitionDetails) {
                appendLine(listOf(archive, pcap, detail).joinToString(",") { csvCell(it) })
            }
        }
        File(out, "2026-11-02-region-transition-evidence.csv").writeText(regionTransitionCsv, Charsets.UTF_8)

        val targetRequestDetails = allDetails.filter { (_, _, detail) ->
            detail.contains("kind=outbound_request op=23 ")
        }
        val targetRequestCsv = buildString {
            appendLine("archive,pcap,timestamp_ms,detail")
            for ((archive, pcap, detail) in targetRequestDetails) {
                appendLine(listOf(archive, pcap, field(detail, "t") ?: "", detail)
                    .joinToString(",") { csvCell(it) })
            }
        }
        File(out, "2026-11-02-operation23-target-requests.csv").writeText(targetRequestCsv, Charsets.UTF_8)

        val databaseArtifacts = listOf(
            "2026-11-02-all-inbox-observations.jsonl",
            "2026-11-02-own-player-movement-timeline.csv",
            "2026-11-02-pcap-harvestable-nodes.csv",
            "2026-11-02-pcap-resource-state-timelines.csv",
            "2026-11-02-pcap-static-resources.csv",
            "2026-11-02-pcap-mob-lifecycle.csv",
            "2026-11-02-pcap-health-energy-events.csv",
            "2026-11-02-pcap-attack-target-tracks.csv",
            "2026-11-02-overlay-mob-attack-correlation.csv",
            "2026-11-02-region-transition-evidence.csv",
            "2026-11-02-operation23-target-requests.csv"
        )
        val exportedDatabaseArtifacts = databaseArtifacts.filter { name ->
            val source = File(out, name)
            source.isFile && runCatching { source.copyTo(File(databaseDir, name), overwrite = true) }.isSuccess
        }
        println("DATABASE_EXPORT files=" + exportedDatabaseArtifacts.size + "/" + databaseArtifacts.size)
        println("RESOURCE_AUDIT harvestable_nodes=" + nodes.size +
            " state_ids=" + resourceStates.size + " state_only_ids=" + unmatchedStates.size +
            " static_resources=" + staticResources.size + " event123_entities=" + mobs.size +
            " attack_targets=" + attacks.size + " tracked_attack_target_positions=" + targetPositions.size)
        println("OBSERVATIONS total=" + allDetails.size + " own_player_move=" + movements.size)
        println("PCAP_AUDIT_COMPLETE archives=" + archives.size + " pcaps=" + total + " errors=" + failed)
        require(total > 0) { "No PCAP files were analyzed" }
        require(failed == 0) { "PCAP replay/analysis failed for $failed files; refusing to mark audit ready." }
    } finally {
        temp.deleteRecursively()
    }
}
