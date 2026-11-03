# Albion Radar — authoritative project status

## Latest full inbox verification — 2026-11-03

Repository: eesanezhad76/v2rayNG  
Branch: albion-pattern-mining  
Latest verified data commit: 21c6d40fcc03772db559c2b08b93b893e33726f8  
Latest completed full rebuild: 41684119321 — SUCCESS

- All 12 ZIP archives in research/inbox were inventoried.
- 31 PCAP members were audited; one byte-identical duplicate was excluded from aggregate observations; 30 unique captures were analyzed.
- PCAP analyzer errors: 0.
- Normalized observations: 340,892; outbound own-player Move(22) observations: 38,132.
- Event 40 harvestable-node records: 331; Event 46 state IDs: 297, including 213 state-only IDs without an Event 40 location.
- Event 39 static-resource observations: 16,944.
- Event 123 living/mobile entity lifecycle records: 2,330.
- Operation 23 attack-target tracks: 44, with 1,514 tracked target-position samples.
- Manual markers: 358; completed marker lifecycle samples: 109.
- Mob/Troll marker correlations: 29 — 16 HIGH, 6 MEDIUM, 4 LOW, 3 without a candidate.
- Troll-labelled cycles: 15; 10 HIGH, 3 MEDIUM, 2 unmatched. Thirteen rows resolve to Ore Elemental types 571/573/574; there are 10 distinct target IDs because some marker windows point to the same target. None of the 29 marker-to-target associations is marked identity-proven.
- Knowledge bundles: 4; merged database contains 9,522 session-scoped entity records, 4,599 deduplicated protocol events, 70 learned pattern signatures and 296 autonomous signatures.
- Spatial alignment: 30 captures checked, 11 plausible same-local-coordinate-space; this does not prove zone identity or a global map transform.
- Python tests: 31 passed; PCAP analyzer Gradle audit: successful. The research ingest itself did not build an APK or perform a device test.


## APK workflow guardrail

A separate Build APK workflow run (41683439058) was unintentionally triggered by the maintenance commit because the Build APK workflow had not excluded changes to .github/workflows/albion-capture-ingest.yml. It had already progressed through APK build steps and was at offline PCAP replay/audit at the last status check. The paths-ignore list was corrected in commit e585b614cb672d0d6f25a06c1f4bea4b5085d71b to exclude the ingest workflow and research/**/*.md. Future research-only changes should not trigger APK builds. The already-started run cannot be cancelled through the available GitHub integration.

The earlier three-ZIP forensic report is a historical subset, not the current complete inbox status. The consolidated report is generated at research/analysis/2026-11-03-complete-inbox-database-report.md; the machine-readable SHA-256 manifest is research/database/albion-complete-database-manifest-2026-11-03.json.

Preservation defect fixed and re-tested: the previous forensic ZIP inventory omitted text members larger than 2 MB and truncated smaller note text at 50,000 characters. Original ZIPs were never modified. The new forensic audit preserves full text members and SHA-256 values in each JSON inventory, including the 3.84 MB file2 pattern report and multi-megabyte live-knowledge files. The knowledge database was rebuilt from all four source bundles: 9,522 session-scoped entity records, 4,599 protocol events, 70 learned patterns and 296 autonomous signatures. A dedicated test verifies that a text member larger than 2 MB is retained without truncation.


Last verified against GitHub: 2026-11-03 (UTC session date)
Repository: `eesanezhad76/v2rayNG`
Working branch: `albion-pattern-mining`
Verified data commit before this status update: `21c6d40fcc03772db559c2b08b93b893e33726f8`

> This file is the operational status snapshot. Update it after every meaningful analysis, implementation, test, or build. The Git history and raw captures remain the evidence of record.


## Entity discovery and resource resolver — 2026-11-03

Latest successful full inbox ingest: run 41690046009 — SUCCESS.  
Current branch data head at report generation: 37b4c4f1d8333b0c5bac121811788ad8c2835de3.

- All 12 ZIP archives preserved and inventoried; 31 PCAP members audited, 30 unique captures after excluding one byte-identical duplicate; PCAP analyzer errors: 0.
- Normalized observations: 340,892; Event 40 node records: 331; Event 39 static-resource observations: 16,944; Event 123 mobile lifecycle records: 2,330.
- Operation 23 attack tracks: 44 with 1,514 target-position samples; manual markers: 358; completed marker cycles: 109.
- Knowledge database: 9,522 session-scoped entity records; 4,599 deduplicated protocol events; 70 learned patterns; 296 autonomous signatures; 69 Troll-labelled signatures.
- Mobile TypeID dictionary resolves all 64/64 distinct PCAP TypeIDs. Enriched lifecycle catalog: 2,330/2,330 records mapped with display name, unique name, tier, category, loot metadata and harvestable flag.
- Event 40 type-number mapping: 7=Rock, 14=Fiber, 20=Hide, 27=Ore (reference mappings, provisional); 29=Ore Elemental living resource (capture-corroborated); 28 remains unresolved.
- Independent type-number cross-check against albion-1793682590317.csv: 83/83 matching entity IDs, zero type-number mismatches.
- Enriched node catalog: 331 rows; 329 mapped, 2 unresolved. Display counts: Rock 87, Fiber 47, Hide 7, static Ore 88, Ore Elemental 100, unresolved 2.
- ResourcePatternLearner upgraded to version 2026-11-03.2. Static resource type numbers now map provisionally; known harvestable mobile TypeIDs 571/573/574 override static type numbers. Non-harvestable TypeID 482 falls back to type number 20 (Hide). Type number 28 remains unknown.
- The radar snapshot parser already consumes resource_inference; inferred categories therefore flow into Rock/Fiber/Hide/Ore filters. Provisional labels retain a question mark; Ore Elemental remains capture-corroborated.
- New database outputs: research/database/albion-resource-node-catalog-2026-11-03.csv, research/database/albion-mobile-entity-catalog-2026-11-03.csv, research/database/albion-mobile-type-crosswalk-2026-11-03.csv, research/database/albion-entity-discovery-report-2026-11-03.json.

## APK verification status

- Build APK run 41687848623 failed one unit-test assertion: the test expected CAPTURE_CORROBORATED while the resolver returned PACKET_CONFIRMED for the resource-name inference. This was a provenance-calibration mismatch, not a parsing or compilation error.
- Corrected ResourcePatternLearner so the resource name remains CAPTURE_CORROBORATED while the directly decoded Mobile TypeID remains PACKET_CONFIRMED. Updated the corresponding unit-test expectation.
- Replacement Build APK run 41689099098 is currently in progress. Do not mark the APK build successful until this run completes and its APK verification/SHA-256 steps pass.
- Research ingest run 41690046009 completed successfully after adding the enriched 2,330-row mobile entity catalog.


## Where the user uploads material

**All new Albion capture ZIPs go to `research/inbox/` on branch `albion-pattern-mining`.**

Examples: resource gathering, movement/position, mobs/golems, idle baseline, map tests, PvP observations. Keep each original ZIP intact; use descriptive filenames. Do not upload to a different branch.

- Upload guide: [UPLOAD_GUIDE.md](UPLOAD_GUIDE.md)
- Inbox: [research/inbox](inbox/)
- After upload, the `Albion capture ingest` GitHub Actions workflow should run automatically.
- The workflow extracts JSONL, creates normalized JSON/CSV and reports, runs research tests, and commits derived files.
- Raw ZIP files stay in the inbox and are not included in the APK.
- If the workflow fails, do not re-upload repeatedly; send the failed run link or its logs.

**Research test code / fixtures** belong in `research/tests/`. Existing Android/JUnit source tests stay in their Gradle source-set paths (for example `V2rayNG/app/src/test`) and are indexed from this research folder; do not move them out of Gradle source sets.

## Branch policy and branch inventory

### Working branches
| Branch | Verified HEAD | Use |
|---|---|---|
| `albion-pattern-mining` | `6a74db31af845a31a458ea9091734010bfd22c2e` (before this status commit) | **Only active development and capture-ingest branch** |
| `albion-reliability-ux` | `dda77733e893e2fb87865d8300dfab09e5295a1b` | PR #3 target; do not modify unless explicitly instructed |

### Other existing branches (inventory only; do not assume active)
- `albion-decoder-lab` — `77a8c3e2c58761d6bd2d40aa8eebef0cb32068a5`
- `albion-decoder-lab-failed-merge-backup` — `0a21332de0bfc2f70c4982eb4af7a35d051b5d8e`
- `albion-decoder-radar` — `ac7e90cd29a874ccd367cf7ea126372f8920a96a`
- `albion-decoder-radar-ci` — `023705a103ccff3ccb6b59d5bd71a5bfc882252c`
- `albion-decoder-radar-rebuild` — `77a8c3e2c58761d6bd2d40aa8eebef0cb32068a5`
- `albion-integrity-autosave` — `39a125b9de84a9101e610f33deb663095e8c10c7`
- `albion-radar` — `2a34a8473643cb0084057ac535a2cf8bdbdfb391`
- `albion-v2rayng-2.2.6-port` — `d41804beecec085eb5f774009ac47935cfa03b24`
- `backup/albion-capture-e3f95d9` — `e3f95d9e03b08a9e31a4d0467a6d8cc224773831`
- `backup/albion-decoder-lab-before-2.2.6` — `f31ba8d3318d929eb1b22e0e94297112d08bf193`
- `backup/albion-radar-2026-10-31` — `e3f95d9e03b08a9e31a4d0467a6d8cc224773831`
- `master` — `aa4b05503a504a3f2e43818e6ee8ebf1caaa3df0`

Branch SHAs are a snapshot, not permanent constants. Re-fetch current heads before every write. Never delete or force-push branches.

## Pull requests

- PR #3: draft, open; head `albion-pattern-mining`, base `albion-reliability-ux`. **Do not alter.**
- PR #2: open, non-draft; head `albion-decoder-radar-ci`, base `albion-decoder-lab`. Inventory only; do not retarget or merge without instruction.
- PR #1: closed; head `albion-decoder-radar`, base `albion-decoder-lab`.

## Permanent research layout

- `research/inbox/`: immutable original ZIP captures.
- `research/database/`: normalized data, node registry, code dictionaries, exact-ID timelines.
- `research/analysis/`: per-capture analysis, reports, correlations, uncertainty.
- `research/tests/`: offline ingest tests, fixtures and validation notes.
- `research/decisions/`: explicit evidence-based promotion decisions for APK.
- `research/tools/`: offline analyzers and ingestion scripts.
- `research/archive/`: historical reports/snapshots that should not be discarded.

## Capture already analyzed (2026-11-02)

Source archive: `Albionsskkakaks111.zip`.
Primary JSONL: `albion-1793620890462.jsonl`.
Secondary JSONL: `albion-1793620143023.jsonl`.

Primary capture inventory:
- 22,093 JSONL rows.
- 14,975 UDP packets involving Albion port 5056.
- Event 40: 77 observations, 51 unique runtime node IDs.
- Event 46: 146 updates across 65 IDs; 13 exact joins to Event 40, 52 state-only IDs.
- Event 123: 448 living-entity observations.
- 175 manual markers: 21 target_found, 21 gathering_started, 20 gathering_completed, 20 node_depleted.

51 Event 40 nodes by family/tier/enchant:
| family | tier | enchant raw | unique |
|---:|---:|---:|---:|
| 29 | 3 | 0 | 3 |
| 29 | 4 | 0 | 6 |
| 29 | 5 | 0 | 16 |
| 29 | 5 | 1 | 1 |
| 27 | 4 | 1 | 3 |
| 27 | 4 | 2 | 1 |
| 27 | 5 | 1 | 7 |
| 14 | 4 | 1 | 4 |
| 14 | 4 | 2 | 1 |
| 14 | 5 | 1 | 2 |
| 7 | 4 | 1 | 1 |
| 7 | 5 | 1 | 5 |
| 7 | 5 | 2 | 1 |

Known Ore family (`type_number=29`) mobile codes: T3 normal=571, T4 normal=573, T5 normal=574. Families 7, 14 and 27 remain unresolved in the current capture. Three exact located-node state sequences show 4→3→2→1→0: IDs 39075, 39460, 39464. Keep other Event 46 state IDs separate unless exact identity/location evidence is obtained.

## Historical player-position finding — priority recovery

The user reports that an earlier movement test had identified their own player coordinates/position. The currently verified project summary does **not** preserve a reproducible coordinate sequence or exact source reference. Do not dismiss this as nonexistent and do not claim it is recovered.

Known lead: player name `EDmars`, reported player entity ID `23433` in player_spawn observations. This is a lead, not proof of a full position track. Revisit historical captures, packet logs, previous reports and chat context. Identify outbound movement/own-player signals and record exact X/Y, timestamps, entity ID, source packet/event, extraction method and confidence. Never substitute another entity's movement.

Status:
- historical coordinate claim: **needs evidence recovery**
- own-player identity/trajectory: **not currently verified from this status snapshot**
- live map/player pin: **not complete**

## Implementation status (last documented)

| Component | Status |
|---|---|
| VPN/TUN packet capture, PCAP/JSONL | Implemented; real-device validation still matters |
| Manual overlay markers | Implemented |
| Shared marker builder/parser | Implemented |
| Photon event decoder / entity mapper | Partial; extend with evidence |
| Offline capture analyzer | Implemented, still evolving |
| Pattern miner | Initial implementation; multi-target FIFO/deque correlation needs completion/verification |
| Event 40 resource observations | Extracted |
| Event 46 resource-state observations | Extracted; exact-ID joins only |
| Event 123 living entities | Extracted |
| Resource mobile-code dictionary | Partial; Ore 29 codes known, others unresolved |
| Marker-to-node identity | Partial; ambiguous matches must remain ambiguous |
| Mob seen/health/killed correlation | Incomplete |
| Own-player coordinates/trajectory | Historical claim; needs recovery/reproduction |
| Live interactive map / live resource pins | Not complete |
| Live radar overlay | Not complete |

## Build/test status snapshot

Known historical:
- Build #112 / run `41426701143`, commit `6f741c621a0a91bc5e688b93c9769bfc534c5085`; 150 diagnostics tests passed, APK artifacts reported.
- Build #113 / run `41433972907`, commit `37cce77c6371aec5eab54b538a81715c83179f26`; historical build result reported, re-verify before relying on it.
- Research ingest workflow run `41450135395` succeeded on SHA `ee38addedf2457ebe86a9c001c6f2e86b2abe502`.
- Latest APK build seen while writing this status: run `41450948155`, SHA `6a74db31af845a31a458ea9091734010bfd22c2e`, status **in progress** at time of check.

Never conflate commit, successful CI, APK artifact creation, installation, and physical device test. User's device is Poco F5 / Android 15 / arm64. No device test should be claimed without user confirmation.

## Rules before every development session

1. Fetch live branch heads and confirm current SHA.
2. Confirm branch is `albion-pattern-mining`; do not switch silently.
3. Read this file and inspect latest inbox/database/analysis/test outputs.
4. Check workflow runs, logs, artifacts, and latest APK build.
5. Review historical player movement evidence before asking for a repeat capture.
6. Preserve all raw captures and unknown fields.
7. Add regression tests for each new decoder/correlation rule.
8. Update this status file and a dated analysis report after each substantial milestone.
9. Only promote proven data into APK after a documented decision under `research/decisions/`.
10. Report exact commit SHA, tests, workflow/build IDs, artifacts and remaining limitations.

This status is a navigation and continuity document; raw captures, normalized database, test output and Git history are the underlying evidence.


## Verified APK build — 2026-11-03

- Replacement Build APK workflow run 41689099098 completed successfully.
- Build, isolated radar APK signing, SHA-256 manifest generation, offline PCAP replay/audit, Python analysis/marker FIFO/readiness tests, and readiness report generation all completed successfully.
- Five debug APK variants were uploaded: arm64-v8a, armeabi-v7a, x86, x86_64 and universal. APK verification confirms Android APK Signature Scheme v2 for all five; SHA-256 values are in workflow artifact `apk-verification` (artifact ID 13257098641).
- This is CI/build verification only. It does not establish successful installation or live capture/overlay behavior on the user's Poco F5; field validation remains pending.

## Data preservation and repository security — 2026-11-03

- The GitHub repository is currently PUBLIC. The current `research/inbox/` tree contains 12 original capture ZIP files. Treat these as potentially sensitive because packet captures can contain session identifiers or other private network metadata; do not add further raw captures, credentials, or private user data to this public repository.
- Do not claim the raw archive is securely/private backed up yet. A separate PRIVATE repository or encrypted offline/cloud archive is required for raw ZIP/PCAP originals and any third-party source files whose redistribution is permitted. Preserve SHA-256 manifests and original filenames; do not modify originals.
- Keep a separate derived-knowledge archive for normalized observations, entity/type dictionaries, correlations, protocol-decoding notes, analysis reports, test outputs, and provenance. Third-party datasets/code should be copied only where their license permits redistribution; otherwise preserve source URL, commit/version, license, and SHA-256.
- Recommended immediate security action: change this repository to PRIVATE in GitHub settings before adding any more capture data. Making it private does not erase prior public exposure or remove data from Git history; if captures are sensitive, review history and rotate any credentials/session tokens that may have been exposed.
- Archive migration is NOT complete until a private destination is created, raw files are copied and hash-verified, and a restore test succeeds.
