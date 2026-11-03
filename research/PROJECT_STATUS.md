# Albion Radar — authoritative project status

## Latest full inbox verification — 2026-11-03

Repository: eesanezhad76/v2rayNG  
Branch: albion-pattern-mining  
Verified head before this maintenance pass: e8e79c1c6380a418fadf409201eb771c82b6324d  
Latest completed ingest run: 41681834068 / run 248 — SUCCESS

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
- Python tests: 29 passed; PCAP analyzer Gradle audit: successful. This was a research ingest, not an APK build or device test.

The earlier three-ZIP forensic report is a historical subset, not the current complete inbox status. The consolidated report is generated at research/analysis/2026-11-03-complete-inbox-database-report.md; the machine-readable SHA-256 manifest is research/database/albion-complete-database-manifest-2026-11-03.json.

Preservation defect found for correction: the previous forensic ZIP inventory omitted text members larger than 2 MB and truncated smaller note text at 50,000 characters. The original ZIPs were not modified, and the knowledge-bundle importer had already read the full four knowledge archives. The updated forensic audit retains full text members and SHA-256 values in each JSON inventory; the workflow rebuilds the knowledge database from source ZIPs instead of relying on an old merge state.


Last verified against GitHub: 2026-11-02 (UTC session date)
Repository: `eesanezhad76/v2rayNG`
Working branch: `albion-pattern-mining`
Verified branch HEAD before this status commit: `6a74db31af845a31a458ea9091734010bfd22c2e`

> This file is the operational status snapshot. Update it after every meaningful analysis, implementation, test, or build. The Git history and raw captures remain the evidence of record.

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
