import com.v2ray.ang.albion.AlbionCaptureAnalyzer
import java.io.File
import java.security.MessageDigest
import java.util.zip.ZipInputStream
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
                val text = if (duplicateOf != null) {
                    duplicatePcapTotal++
                    duplicatePcapRows += listOf(archive.name, pcap.name, contentHash, duplicateOf)
                        .joinToString(",") { "\"" + it.replace("\"", "\"\"") + "\"" }
                    "DUPLICATE_PCAP: byte-identical to " + duplicateOf + "; excluded from aggregate observations to prevent double counting."
                } else try {
                    uniquePcapTotal++
                    val report = AlbionCaptureAnalyzer.analyze(pcap)
                    report.detailedObservations.forEach { allDetails += Triple(archive.name, pcap.name, it) }
                    report.asText()
                } catch (e: Throwable) {
                    failed++
                    "ANALYSIS_ERROR: " + e.javaClass.simpleName + ": " + e.message
                }
                val safe = (archive.nameWithoutExtension + "--" + pcap.nameWithoutExtension)
                    .replace(Regex("[^A-Za-z0-9._-]+"), "_").take(110) + "-" + total.toString().padStart(2, '0')
                File(out, safe + "-pcap-report.md").writeText(
                    "# PCAP analysis: " + archive.name + " / " + pcap.name + "\n\n" + text,
                    Charsets.UTF_8
                )
                index += "### " + pcap.name + "\n" + text
                println("PCAP " + archive.name + " :: " + pcap.name + " :: " + text.lineSequence().take(9).joinToString(" | "))
            }
        }
        File(out, "2026-11-02-all-inbox-pcap-analysis.md").writeText(
            "# Full inbox PCAP analysis\n\nArchives: " + archives.size + "\nPCAP files: " + total + "\nAnalysis errors: " + failed + "\n\n" + index.joinToString("\n\n"),
            Charsets.UTF_8
        )
        File(out, "2026-11-02-pcap-duplicate-inventory.csv").writeText(
            (listOf("archive,duplicate_pcap,sha256,canonical_pcap") + duplicatePcapRows).joinToString("\\n") + "\\n",
            Charsets.UTF_8
        )
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
            val archive: String, val pcap: String, val id: String,
            val mobileTypes: MutableSet<String> = sortedSetOf(),
            val families: MutableSet<String> = sortedSetOf(),
            val tiers: MutableSet<String> = sortedSetOf(),
            val enchants: MutableSet<String> = sortedSetOf(),
            val coordinates: MutableSet<String> = sortedSetOf(),
            var first: Long = Long.MAX_VALUE,
            var last: Long = Long.MIN_VALUE,
            var observations: Int = 0
        )
        fun field(detail: String, key: String): String? =
            Regex("(?:^| )" + Regex.escape(key) + "=([^ ]+)").find(detail)?.groupValues?.get(1)
        fun csvCell(value: String): String = "\"" + value.replace("\"", "\"\"") + "\""
        val nodes = linkedMapOf<String, NodeAgg>()
        val resourceStates = linkedMapOf<String, MutableList<Pair<Long, String>>>()
        val staticResources = mutableListOf<String>()
        val rawHealthEnergyEvents = mutableListOf<String>()
        data class MobAgg(
            val archive: String, val pcap: String, val id: String,
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
        fun scopedKey(archive: String, pcap: String, id: String) = archive + "\u001f" + pcap + "\u001f" + id
        for ((archive, pcap, detail) in allDetails) {
            val kind = field(detail, "kind") ?: continue
            val t = field(detail, "t")?.toLongOrNull() ?: 0L
            if (kind == "raw_health_energy_event") {
                val code = Regex("code=(\\d+)").find(detail)?.groupValues?.get(1) ?: ""
                rawHealthEnergyEvents += listOf(archive, pcap, t.toString(), code, detail)
                    .joinToString(",") { csvCell(it) }
                continue
            }
            val id = field(detail, "id") ?: continue
            val key = scopedKey(archive, pcap, id)
            when (kind) {
                "harvestable" -> {
                    val node = nodes.getOrPut(key) { NodeAgg(archive, pcap, id) }
                    node.observations++
                    node.first = minOf(node.first, t)
                    node.last = maxOf(node.last, t)
                    field(detail, "mobileType")?.let { node.mobileTypes += it }
                    field(detail, "family")?.let { node.families += it }
                    field(detail, "tier")?.let { node.tiers += it }
                    field(detail, "enchant")?.let { node.enchants += it }
                    val x = field(detail, "x"); val y = field(detail, "y")
                    if (x != null && y != null) node.coordinates += "$x,$y"
                }
                "resource_state" -> {
                    val value = field(detail, "amount") ?: if (detail.contains("removed=true")) "removed" else "null"
                    resourceStates.getOrPut(key) { mutableListOf() }.add(t to value)
                }
                "static_resource" -> {
                    staticResources += listOf(archive, pcap, t.toString(), id,
                        field(detail, "x") ?: "", field(detail, "y") ?: "").joinToString(",") { csvCell(it) }
                }
                "mob" -> {
                    val mob = mobs.getOrPut(key) { MobAgg(archive, pcap, id) }
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
            appendLine("archive,pcap,entity_id,mobile_type_ids,type_numbers,tiers,enchantment_raw_values,coordinates,first_seen_ms,last_seen_ms,observation_count,state_event_count,state_values,identity_scope")
            for ((key, node) in nodes.toSortedMap()) {
                val states = resourceStates[key].orEmpty().sortedBy { it.first }
                val values = listOf(node.archive, node.pcap, node.id,
                    node.mobileTypes.joinToString("|"), node.families.joinToString("|"),
                    node.tiers.joinToString("|"), node.enchants.joinToString("|"),
                    node.coordinates.joinToString("|"), node.first.toString(), node.last.toString(),
                    node.observations.toString(), states.size.toString(),
                    states.joinToString(">") { it.second }, "archive+pcap+runtime_entity_id")
                appendLine(values.joinToString(",") { csvCell(it) })
            }
        }
        File(out, "2026-11-02-pcap-harvestable-nodes.csv").writeText(nodeCsv, Charsets.UTF_8)

        // Preserve and correlate manually labelled overlay lifecycle markers with Event 40 nodes.
        // This is candidate evidence only: time/tier/enchantment overlap does not prove entity identity.
        data class OverlayMiningSample(
            val archive: String, val pcap: String, val activity: String, val foundMs: Long,
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
        fun markerKey(archive: String, pcap: String, activity: String) = archive + "\u001f" + pcap + "\u001f" + activity
        for ((archive, pcap, detail) in markerDetails) {
            val t = field(detail, "t")?.toLongOrNull() ?: continue
            val activity = field(detail, "activity") ?: "Unknown"
            val key = markerKey(archive, pcap, activity)
            when (field(detail, "event")) {
                "target_found" -> pendingMarkers.getOrPut(key) { mutableListOf() }.add(
                    OverlayMiningSample(archive, pcap, activity, t, field(detail, "tier"), field(detail, "enchantment"))
                )
                "gathering_started" -> {
                    val sample = pendingMarkers[key]?.firstOrNull { it.startedMs == null && it.completedMs == null }
                    if (sample == null) unmatchedStarts++ else sample.startedMs = t
                }
                "gathering_completed" -> {
                    val queue = pendingMarkers[key]
                    val sample = if (queue == null) null else
                        queue.firstOrNull { it.startedMs != null } ?: queue.singleOrNull()
                    if (sample == null) unmatchedCompletions++ else {
                        sample.completedMs = t
                        queue?.remove(sample)
                        markerSamples += sample
                    }
                }
                "node_depleted" -> {
                    val sample = markerSamples.firstOrNull {
                        it.archive == archive && it.pcap == pcap && it.activity == activity &&
                            it.completedMs != null && it.depletedMs == null && it.completedMs!! <= t
                    }
                    if (sample == null) unmatchedDepletions++ else sample.depletedMs = t
                }
            }
        }
        val markerCsv = buildString {
            appendLine("archive,pcap,timestamp_ms,activity,event,tier_label,enchantment_label")
            for ((archive, pcap, detail) in markerDetails) {
                appendLine(listOf(archive, pcap, field(detail, "t") ?: "", field(detail, "activity") ?: "",
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
            appendLine("archive,pcap,activity,found_ms,started_ms,completed_ms,depleted_ms,tier_label,enchantment_label,candidate_node_count,candidate_families,candidate_entity_ids,match_status")
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
                val candidates = nodes.values.filter { node ->
                    node.archive == sample.archive && node.pcap == sample.pcap &&
                        node.first <= windowEnd && node.last >= windowStart &&
                        (tierNumber == null || node.tiers.contains(tierNumber.toString())) &&
                        (enchantNumber == null || node.enchants.contains(enchantNumber))
                }
                val familyCounts = linkedMapOf<String, Int>()
                candidates.forEach { node ->
                    node.families.filter { it.isNotBlank() && it != "-1" }.forEach { family ->
                        familyCounts[family] = (familyCounts[family] ?: 0) + 1
                    }
                }
                val singleFamily = familyCounts.keys.singleOrNull()
                if (singleFamily != null) {
                    val evidenceKey = sample.activity + "\u001f" + singleFamily
                    val evidence = familyEvidence.getOrPut(evidenceKey) { FamilyEvidence(sample.activity, singleFamily) }
                    evidence.samples += sample.archive + "\u001f" + sample.pcap + "\u001f" + sample.foundMs
                    evidence.captures += sample.archive + "/" + sample.pcap
                    evidence.candidateNodes += familyCounts[singleFamily] ?: 0
                }
                val status = when {
                    candidates.isEmpty() -> "no_candidates"
                    tierNumber == null || enchantNumber == null -> "incomplete_overlay_labels"
                    familyCounts.isEmpty() -> "family_unavailable"
                    familyCounts.size == 1 -> "single_family_candidate_only"
                    else -> "ambiguous_families"
                }
                appendLine(listOf(sample.archive, sample.pcap, sample.activity, sample.foundMs.toString(),
                    sample.startedMs?.toString() ?: "", sample.completedMs?.toString() ?: "", sample.depletedMs?.toString() ?: "",
                    sample.tierLabel ?: "", sample.enchantLabel ?: "", candidates.size.toString(),
                    familyCounts.entries.sortedBy { it.key }.joinToString("|") { it.key + ":" + it.value },
                    candidates.joinToString("|") { it.id }, status).joinToString(",") { csvCell(it) })
            }
        }
        File(out, "2026-11-02-overlay-family-correlation.csv").writeText(correlationCsv, Charsets.UTF_8)
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
        println("OVERLAY_CORRELATION markers=${markerDetails.size} completed_samples=${markerSamples.size} single_family_candidates=${familyEvidence.size} unmatched_start=$unmatchedStarts unmatched_completed=$unmatchedCompletions unmatched_depleted=$unmatchedDepletions")

        val unmatchedStates = resourceStates.filterKeys { !nodes.containsKey(it) }
        val stateCsv = buildString {
            appendLine("archive,pcap,entity_id,first_state_ms,last_state_ms,state_event_count,state_values,has_event40_location")
            for ((key, states) in resourceStates.toSortedMap()) {
                val parts = key.split("\u001f")
                val ordered = states.sortedBy { it.first }
                val vals = listOf(parts[0], parts[1], parts[2], ordered.firstOrNull()?.first?.toString() ?: "",
                    ordered.lastOrNull()?.first?.toString() ?: "", ordered.size.toString(),
                    ordered.joinToString(">") { it.second }, if (nodes.containsKey(key)) "true" else "false")
                appendLine(vals.joinToString(",") { csvCell(it) })
            }
        }
        File(out, "2026-11-02-pcap-resource-state-timelines.csv").writeText(stateCsv, Charsets.UTF_8)
        File(out, "2026-11-02-pcap-static-resources.csv").writeText(
            (listOf("archive,pcap,timestamp_ms,entity_id,x,y") + staticResources).joinToString("\n") + "\n",
            Charsets.UTF_8
        )
        val mobCsv = buildString {
            appendLine("archive,pcap,entity_id,mobile_type_id,enchantment,name,x,y,first_seen_ms,last_seen_ms,spawn_observations,movement_observations,health_observations,energy_observations,health_regeneration_observations,energy_regeneration_observations,min_observed_health,max_observed_health,last_observed_health,min_observed_energy,max_observed_energy,last_observed_energy,last_health_regeneration,last_energy_regeneration,state_observations,left_after_seen,interpretation")
            for ((_, mob) in mobs.toSortedMap()) {
                val values = listOf(mob.archive, mob.pcap, mob.id, mob.mobileType, mob.enchant,
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
            val keyFor = { id: String -> scopedKey(archive, pcap, id) }
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
            appendLine("archive,pcap,target_entity_id,attack_action_count,attack_flag_values,first_attack_ms,last_attack_ms,event3_position_samples,first_position_x,first_position_y,last_position_x,last_position_y,min_x,max_x,min_y,max_y,event123_mobile_type,event123_name,known_player_spawn,leave_observed,leave_after_last_attack,health_observations,health_delta_samples,sum_health_delta,health_source_entity_ids,min_current_health,max_current_health,last_current_health,energy_observations,min_current_energy,max_current_energy,last_current_energy,interpretation")
            for ((key, values) in attacks.toSortedMap()) {
                val parts = key.split("\u001f")
                val ordered = values.sortedBy { it.t }
                val positions = targetPositions[key].orEmpty().sortedBy { it.t }
                val xs = positions.map { it.x }; val ys = positions.map { it.y }
                val vals = listOf(
                    parts[0], parts[1], parts[2], ordered.size.toString(),
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
        val databaseDir = File(repo, "research/database").apply { mkdirs() }
        val databaseArtifacts = listOf(
            "2026-11-02-all-inbox-observations.jsonl",
            "2026-11-02-own-player-movement-timeline.csv",
            "2026-11-02-pcap-harvestable-nodes.csv",
            "2026-11-02-pcap-resource-state-timelines.csv",
            "2026-11-02-pcap-static-resources.csv",
            "2026-11-02-pcap-mob-lifecycle.csv",
            "2026-11-02-pcap-health-energy-events.csv",
            "2026-11-02-pcap-attack-target-tracks.csv"
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
    } finally {
        temp.deleteRecursively()
    }
}
