# ALBION RADAR — PERSISTENT HANDOFF / SOURCE OF TRUTH
Last updated: 2026-11-06
Repository: https://github.com/eesanezhad76/v2rayNG

## 0. Mandatory startup protocol for every new chat
1. Read this file FIRST: `docs/ALBION_HANDOFF.md`.
2. Read `research/README.md`, latest capture inventory under `research/analysis/`, database README, and latest analysis reports.
3. Query GitHub branch heads and Actions runs live. Never assume a SHA, build status, APK artifact, test status, or branch head from an old chat.
4. Inspect the current branch and working tree before editing. Do not write until the active branch and exact HEAD SHA are confirmed.
5. Report separately: code committed, tests run, CI build, APK artifact created, and actual physical-device test. Never conflate them.
6. At the end of every work session, update this handoff and the relevant detailed research records in the same active branch. Include exact commit SHA, changed paths, test/run IDs, confirmed facts, unresolved questions, and next actions.
7. Never treat chat memory as the permanent database. GitHub research files are the durable source of truth.

## 1. Branch discipline — do not violate
- ACTIVE development/research branch: `albion-pattern-mining`.
- `albion-reliability-ux`: integration/target branch referenced by Draft PR #3. Do NOT switch to it unless the user explicitly instructs you.
- Draft PR #3 is conflict-prone and must not be edited, closed, merged, or rebased without explicit instruction.
- Do not delete branches, force-push, reset history, or rewrite commits.
- All commits must be fast-forward updates against the freshly checked HEAD SHA. Use expected-SHA/lease semantics. If rejected, re-fetch branch and reconcile; never force.
- Before any changes, inspect the current branch HEAD and preserve all existing content.
- The user expects direct repository edits, not instructions to edit code manually.

## 2. Research vault — preserve all evidence
Root: `research/`
- `research/inbox/`: original uploaded ZIP/PCAP/JSONL capture evidence. Preserve original archives; do not replace or silently delete.
- `research/database/`: normalized machine-readable datasets, node/entity registry, code dictionaries, state timelines.
- `research/analysis/`: per-capture reports, inventories, correlations, hypotheses, confidence and caveats.
- `research/tests/`: ingest tests and validation evidence. Android/JUnit tests remain in their Gradle-discovered source locations too.
- `research/decisions/`: explicit promotion decisions. Only reviewed/verified facts may be promoted into app code.
- `research/tools/`: ZIP/JSONL ingest and offline analysis tools.
- `.github/workflows/albion-capture-ingest.yml`: capture ingest/test workflow. Inspect current file and latest workflow status before relying on it.

Never mix raw evidence with production code. Unknown values must remain unknown. Preserve all packet/event records, entity IDs, timestamps, coordinates, enchantments, state transitions, markers and unmatched records. Do not silently drop ambiguous evidence.

## 2A. Integration roadmap / current automation status
- Preserve the end-to-end path: passive IP capture → PCAP/JSONL → Photon packet decoder → Protocol18 decoder → typed event mapper → isolated/live knowledge store + protocol evidence store → overlay snapshot; offline replay must exercise the same pipeline without mutating live state.
- Keep inventory candidates separate from confirmed item IDs/stack deltas. Require repeated independent HarvestFinished-correlated observations and retain parameter paths, deltas, sample counts, and uncertainty.
- Keep node identity exact-ID based; resource family, mobile type, tier, enchantment, and runtime entity ID are distinct fields. Do not join entities by proximity alone.
- Mob names/type catalog remains unresolved unless direct packet evidence or independently labelled captures corroborate it. Entity-leave is not proof of kill.
- Current code change: connect ResourcePatternLearner output into persistent live knowledge records as a separate `resource_inference` object with catalog version, value/status/confidence/reason; preserve raw packet fields and suppress inference when evidence conflicts. Add regression coverage and verify the current APK workflow before calling it ready.
- Latest workflow evidence at handoff edit: capture-ingest run 41525742713 succeeded on source SHA b028ee35818201b1b1a525c9b806f7718614fa5a; APK build run 41525742789 was still building passive-tap libv2ray AAR on that SHA. The branch may advance when ingest commits derived research outputs; re-query current HEAD and CI before relying on these values.

## 2B. Overlay label integration
- The mini radar now consumes provenance-aware resource inference labels for its selected target; conflicting/unknown mappings fall back to numeric family/type labels.
- Mob dots display the directly observed name when present, otherwise a neutral `Mob ID<n>` label. This is a raw type identifier, not a guessed species name.
- Parser label tests cover corroborated Ore inference, conflict fallback, named mobs, and unknown mob type labels.

## 3. Existing capture facts (2026-11-02 capture)
Archive supplied in chat: `Albionsskkakaks111.zip`.
Primary JSONL: `albion-1793620890462.jsonl`.
Secondary JSONL: `albion-1793620143023.jsonl` (no UDP/5056 game packets).

Primary capture analysis:
- 22,093 JSONL rows/IP packet records.
- 14,975 UDP packets involving Albion port 5056.
- Event 40: 77 observations, 51 unique runtime node IDs.
- Event 46: 146 updates across 65 IDs; 13 IDs join exactly to Event 40; 52 IDs have state updates but no Event 40 location snapshot in this capture.
- Event 123: 448 living-entity observations.
- Manual markers: 175 total; 21 target_found, 21 gathering_started, 20 gathering_completed, 20 node_depleted.
- 51 unique Event 40 nodes across multiple resource families, tiers and enchantments — NOT just five nodes.
- Detailed counts, IDs, coordinates, raw values and caveats are in the dated capture report and machine-readable database. Always re-read them; this summary is not a replacement.

Verified code mapping from the aggregate capture corpus and the pinned MIT database-code reference:
- Static resource type_number ranges: WOOD 0–5, ROCK 6–10, FIBER 11–15, HIDE 16–22, ORE 23–27.
- Static Event 40 type_number 27 is the ORE family (175 records); its mobile_type_id is unresolved for the static nodes and must not be borrowed from living Ore Critters.
- Event 40 type_number 29 is a separate ORE_RESOURCE_CREATURE family (229 records). Observed Mobile TypeIDs: T3 570 (Mountain Green) and 571 (Mountain Red Ore Elemental); T4 572 (Mountain Green) and 573 (Mountain Red Ore Elemental); T5 574 (Mountain Red Ore Elemental); T6 576 (Mountain Red Ore Critter).
- Event 40 type_number 28 has 2 unresolved records; type_number 32 has 1 unresolved record. Keep both unknown. TypeID 575 is not observed in the current capture corpus.
- Event 40 `type_number` is a resource-family number; `mobile_type_id` is a separate game mobile code; `entity_id` is the runtime ID of one specific node.
- Event 46 must be joined to Event 40 by the exact composite key archive + PCAP + region_id + entity_id when region is known; never join across captures/regions or by proximity/guessing.
- Three node IDs (39075, 39460, 39464) have clear complete 4→3→2→1→0 sequences in this capture. Check exact database timelines before quoting more detail.
- State-only IDs must be retained and investigated, not discarded.

Markers:
- Multiple Found markers may be recorded before multiple Started markers. Existing single-active-marker logic loses queued Found events.
- Required model: per-activity FIFO/deque of pending targets and explicit Found → Started → Completed → Depleted transitions; preserve unmatched/extra/duplicate markers with uncertainty rather than force-pairing.
- Marker-to-node association requires exact evidence. Temporal/nearest-neighbor guesses must be labeled hypotheses.

## 4. Player location and movement — VERIFIED FINDING RECOVERED
The earlier player-position finding has been recovered from the committed movement captures and preserved in:
- `research/analysis/player-position-evidence.md`
- `research/database/player-position-evidence.json`
- `research/decisions/001-own-player-position-op22.md`

Verified evidence:
- `Albions--run_and_stop-18-pcap-report.md`: 233 outbound operation-22 requests; example at t=1793399097905 ms, current position [-78.86469, 46.23921], heading 172.43239, target [-75.69194, 44.742764], speed 11.
- `Albions--2_times_90degred_change_direction_-14-pcap-report.md`: 203 outbound operation-22 requests; example at t=1793399203561 ms, current position [-61.95968, 241.66254], heading 181.79382, target [-60.418728, 240.38028], speed 11.
- Outbound Photon operation 22 parameter key 1 is the local player's current [x,y] pair. Key 2 is heading, key 3 is target pair, and key 4 is speed.
- This is independent of the player_spawn observation EDmars / entity ID 23433. The local mapper uses entity_id=-1 only as a YOU sentinel; it is not a server runtime ID.
- Do not use arbitrary Event 3 payloads as own-player coordinates; the reports show implausible Event 3 decoded values.
- Two captures establish real position fixes and movement sequences, but do NOT establish zone identity, a global coordinate transform, or cross-zone coordinate continuity.
- Code maps operation 22 and now stores a bounded 240-point trail. Mini radar and pan/zoom local-coordinate map render it.

## 5. Current architecture / app capabilities and limits
Implemented in previous work (verify current code before claiming):
- Android VPN/TUN packet capture to PCAP/JSONL.
- Overlay/manual capture markers.
- Shared marker builder/parser and validation.
- Photon packet/event decoding and entity mapping.
- Offline capture analyzer and heuristic pattern miner.
- Event 40 harvestable and Event 46 resource-state observations; Event 123 living entity observations.
- Marker error visibility in overlay.

Implemented in current branch (must still pass the current CI run before claiming build validation):
- Mini radar plus a pan/zoom local-coordinate map screen, both fed from the daemon live-knowledge snapshot.
- Own-player current position from outbound operation 22 and bounded persistent movement history.
- Learned database ZIP export with a versioned manifest, entity/protocol counts and SHA-256 digests; excludes VPN profiles, credentials and raw PCAP payloads.
- Regression tests for database bundle export and movement-trail persistence.

Not yet proven/complete:
- Physical-device rendering and live snapshot delivery on the user's Poco F5.
- Same-capture player-position / typed-resource coordinate alignment has been analyzed across 34 captures: 15 are PLAUSIBLE_SAME_LOCAL_COORDINATE_SPACE and 19 are NOT_VERIFIED. This supports local alignment only, not zone identity or a global/world-map transform.
- Zone identity and global/world-map coordinate transform; current map is a local coordinate plane, not a complete world map with terrain or roads. 33 region-transition records cover nine numeric IDs; external names are reference-only and no unlicensed zone dataset was imported.
- Full mob Seen→Killed linkage and exact T4 golem runtime entity identification.
- Calibrated statistical confidence (current confidence is heuristic).
- Production-grade mappings for unknown resource families and inventory Item IDs/stack deltas.
- Safe import/merge of a centrally revised knowledge database into the phone; current implemented exchange is a privacy-minimal export bundle for sending the phone database to the central research vault.
- Validated other-player position decoding; Event 3 coordinates remain withheld because the current decoder has not been approved.
- The capture-ingest workflow must be checked against actual ZIP and Actions logs; never assume it processed a particular archive just because the workflow exists.

## 6. Promotion policy: research → production APK
- Research vault is the evidence source; app code consumes only reviewed records.
- A mapping may be promoted only when: (a) raw packet/event evidence is preserved, (b) semantics and exact field meanings are understood, (c) corroborated across independent captures where feasible, (d) regression tests cover it, (e) decision record is added under `research/decisions/`.
- Keep experimental dictionaries/data out of APK. Promote the smallest verified subset required by production.
- Unknown/encrypted data must not be fabricated or decoded by guesswork.
- Before build, inspect diffs and tests. Build only after the user-requested implementation scope is clear.
- After build, verify workflow run SHA, conclusion, artifact name/download availability, test totals. State that device behavior remains unverified until user tests on Poco F5.

## 7. Previous build references — historical, not current
- Build #112, run ID 41426701143, SHA 6f741c621a0a91bc5e688b93c9769bfc534c5085: success, APK artifacts and diagnostics; 150 tests, 0 failures/errors/skips. This predates later overlay fix.
- Build #113, run ID 41433972907, SHA 37cce77c6371aec5eab54b538a81715c83179f26: previously reported successful; re-verify live if needed.
- Do not call these the latest build unless verified against current branch.
- No claim of physical installation/device testing unless the user explicitly confirms it.

## 8. Immediate next actions
1. Verify the latest build run for the current HEAD after the map, movement trail, and database-export changes; inspect Gradle tests, offline replay, Python tests, readiness report, APK signatures and all ABI artifacts.
2. Keep the APK withheld until that current run passes and its artifacts are verified.
3. Test database ZIP contents in CI and ensure export can run both during capture and after capture.
4. Implement safe, provenance-preserving import/merge of a central knowledge bundle; do not join runtime node IDs across captures unless a zone/session scope is known.
5. Investigate zone identity and map coordinate transforms without inventing map terrain or cross-zone continuity.
6. Improve mob lifecycle/Seen→Killed linkage and inventory Item ID/stack-delta semantics only from direct evidence.
7. Confirm the capture-ingest workflow processed the original ZIP archives and that all original raw captures remain preserved.
8. On the first controlled device test, verify operation-22 player location, resource pins, Mob pins, overlay delivery, VPN forwarding, storage and long-session stability.
9. Add new captures to the research vault without asking the user to resend evidence already present.

## 9. Required reporting format at each session end
- Active branch + current HEAD SHA:
- Commits made (SHA and message):
- Files added/changed:
- Research data extracted (counts, IDs, exact joins):
- Confirmed findings:
- Hypotheses / unknowns:
- Tests run + exact result:
- Actions workflow run ID/status/SHA:
- APK artifact status:
- Device test status:
- Next concrete actions:

## 10. Instructions to the assistant in the next chat
Treat this file as mandatory project continuity context. Do not restart analysis from scratch, do not overwrite prior findings, do not claim a feature is complete without evidence, and do not confuse absence in the latest capture with absence in all historical captures. The user values exact coordinates, entity IDs, resource codes, enchantment levels, state timelines, marker correlations and permanent records. Update this handoff after every meaningful discovery or implementation.


## 11. Aggregate database and map audit — 2026-11-06

### Active branch and current research baseline
- Active branch remains `albion-pattern-mining`; do not merge into `albion-safe-integration-2026-11-05`, recovery, master, or Draft PR #3 without explicit instruction.
- This audit began at app HEAD `a453c812483f285dcae97d0ad0bc044c0cec11fa`. Database/script commits in this audit include `91694e790b0007e2cb58eecba29eec5c5b9985d8`, `86e48e06634589d660611bd21b16b374ac814a18`, `c176b707ed921a4ee4f66c9d93098528f986f9b1`, and manifest reconciliation `1d7d9565cb8f2b6543d8d2ab84c84cba1f907ee4`. Later report/README/handoff commits advance the branch; query live HEAD next session.
- External source quarantine: `eesanezhad76/AlbionRadar-External-Research`, branch `external-data-quarantine-2026-11-06`, last verified HEAD before handoff update `8b720dc856b0fda28a33c67b1dd3caba1d421021`.

### Capture corpus coverage
- 14 source archive inventories; 35 PCAP members; 34 unique PCAP SHA-256; one byte-identical duplicate; 20 JSONL members; zero analyzer errors.
- 761,082 normalized observations; 81,881 own-player Move(22) observations.
- Event 39: 47,108 untyped static-resource candidates.
- Event 40: 652 node records; Event 46: 629 region-scoped state IDs, 161 exact Event 40 joins and 468 state-only IDs (1,523 state updates).
- Event 123: 4,172 lifecycle records; 116 distinct observed Mobile TypeIDs, all 116 matched to the current dictionary.
- Operation 23: 37 attack-target tracks with 3,007 Event 3 target-position samples.
- Manual marker log: 369 events; 109 gathering_completed + node_depleted events.
- The machine-readable source of truth is `research/database/albion-complete-database-manifest-2026-11-03.json`; the latest human audit is `research/analysis/2026-11-06-complete-database-audit.md`. `research/database/README.md` now lists the current aggregate files.

### Ore Critter coverage
- Six observed variants now appear in the mobile crosswalk and resource-node catalog: 570 T3 green, 571 T3 red, 572 T4 green, 573 T4 red, 574 T5 red, 576 T6 red.
- Together they account for 229 Event 40 type_number-29 nodes and 637 Event 123 lifecycle records.
- Static ORE is type_number 23–27; type_number 27 has 175 records. Do not conflate static type_number 27 with living type_number 29.
- type_number 28 (2 records), type_number 32 (1 record), and TypeID 575 (not observed in this corpus) remain unassigned.

### Troll and Mob target correlation repair
- The main aggregate marker correlation CSV had a malformed header: 21 columns but only 20 values per row. The original file is preserved as `research/analysis/2026-11-02-overlay-mob-attack-correlation.pre-region-join.csv`.
- Main analysis and database copies were repaired by joining exact region IDs from the same archive + PCAP transition timeline. 12/13 aggregate marker rows now have region IDs; one older Mobs row has no transition evidence.
- Dedicated Troll table: 12 candidate rows (11 Ore Elemental, 1 Roaming Morgana Crossbowman), confidence HIGH 6 / MEDIUM 4 / LOW 2; identity_proven remains false for all.
- Dedicated Mobs table: one Ore Elemental candidate, LOW confidence, no region ID, identity_proven false.
- The final Troll marker at 1793792784792 in region 4219 has a main-vs-curated disagreement: aggregate candidate table chooses TypeID 573 / Ore Elemental / target 28922 (two candidates, HIGH); dedicated conservative table chooses TypeID 1471 / Roaming Morgana Crossbowman / target 7480 (one candidate, LOW). Preserve both; do not promote either as confirmed. The user marker label `Troll` is an activity label, not a verified species. The 116-row observed Mobile TypeID crosswalk contains no display_name or unique_name with “Troll”; the pinned OpenRadar source search also found no Troll data entry.
- Troll region IDs: 4206 (4), 4209 (6), 4219 (2). Candidate zone names from an unlicensed reference are Tharcal Fissure, Cairn Darg and Caulder Fissure; names remain analysis-only.

### Map / zone research
- 33 captured region-transition records cover nine numeric IDs: 4000, 4001, 4002, 4206, 4207, 4209, 4213, 4217, 4219.
- Spatial report: 34 captures, 15 plausible local coordinate alignments, 19 not verified. A generator default bug that reported 11 was corrected; future builds derive the count from the per-capture status rows.
- The external quarantine inventory now records 124 source entries / 123 unique IDs. Two MIT OpenRadar database parser references were added (HarvestablesDatabase.js and MobsDatabase.js); game-derived JSON payloads were not copied.
- AODP `boxes.json` (327 unlabeled rectangles) and its bundled MIT notice were imported into external quarantine only. The 2.88 MB atlas image was not imported; license scope and atlas-to-zone mapping remain unverified.
- Multiple map/pathfinder/dungeon sources were reviewed. Unlicensed map JSON, grid coordinates, tiles and images remain held. A binary-only radar updater with contradictory license claims was rejected and never executed. Full source review: `analysis/2026-11-06-deep-map-zone-radar-source-sweep.md` in the external quarantine repository.

### Validation and outstanding work
- No app source or runtime asset was changed in this audit.
- The index-builder script and aggregate manifest were updated, but the generator was not executed; no Python tests, Gradle tests, CI build, APK creation, live capture or physical-device test was performed.
- Next: run the database generator in a controlled checkout; verify Python syntax and output diffs; ensure it preserves the curated Troll/Mobs tables; add regression tests for the 21-column marker CSV and region join; investigate the one unresolved Troll disagreement; obtain a licensed zone/world map dataset and validate a global coordinate transform; leave unknown IDs unresolved until direct evidence appears.
