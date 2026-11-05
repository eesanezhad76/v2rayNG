# Complete Albion inbox database — 2026-11-03

Consolidated index for all original ZIP archives. Raw ZIPs remain unchanged in research/inbox. Counts are capture-scoped unless stated otherwise.

## Intake and integrity
- ZIP archives: **14**
- PCAP members: **35**; unique PCAP hashes: **34**; byte-identical duplicates: **1**
- PCAP analyzer errors: **0**
- JSONL members: **20**
- Normalized observations: **761,082**; own-player Move(22) observations: **81,881**

## Resource nodes and static resources
- Event 40 harvestable-node records: **649**
- Family counts: 14: 78, 14|27: 1, 20: 9, 27: 174, 28: 2, 29: 227, 32: 1, 7: 157
- Event 46 state IDs: **297**; state-only IDs without Event 40 location: **213**
- Event 39 static-resource observations: **47,108** (untyped candidates, not automatically confirmed nodes)
- Verified Ore mapping: family 29, T3→571, T4→573, T5→574
- User-confirmed T5.3 exception: entity 2788, family 27, coordinates (-379, -24); this does not classify all family-27 nodes.

## Mobs and Troll marker cycles
- Event 123 living/mobile lifecycle records: **4,098**
- Operation 23 attack-target tracks: **37**; tracked target-position samples: **1514**
- Manual marker records: **369**; completed lifecycle samples: **109**
- Mob/Troll correlations: **13** (16 HIGH, 6 MEDIUM, 4 LOW, 3 with no candidate)
- Troll-labelled cycles: **12**; confidence: HIGH: 6, LOW: 2, MEDIUM: 4
- Troll cycles resolving to Ore Elemental: **12**; mobile types: 1471: 1, 573: 6, 574: 5
- Distinct target IDs among correlated Troll cycles: **10**; unlinked cycles: **0**
- Marker label Troll is retained separately from decoded game name Ore Elemental; all marker-to-target links remain evidence-ranked, not identity-proven.
- Attack tracks: 5 strong defeat candidates, 38 likely defeats not confirmed, 1 movement-supported candidate with kill unproven.

## Knowledge bundles
- Imported bundles: **4**
- Source entity records across manifests: **10,767**; deduplicated session-scoped records: **9,522**
- Deduplicated protocol events: **4,599**
- Learned patterns: **70**; autonomous signatures: **296**
- Troll-labeled autonomous signatures: **69** (signatures, not individual mobs)

## Spatial evidence
- Captures checked: **34**; plausible same-local-coordinate-space: **11**; not verified: **19**
- This does not prove zone identity or a global map transform.

## Complete source tables
- 2026-11-02-all-inbox-observations.jsonl — all normalized timestamped observations
- 2026-11-02-pcap-harvestable-nodes.csv — all Event 40 node records
- 2026-11-02-pcap-mob-lifecycle.csv — Event 123 lifecycle records
- 2026-11-02-pcap-static-resources.csv — Event 39 static-resource observations
- 2026-11-02-pcap-attack-target-tracks.csv — operation-23 target tracks
- 2026-11-02-overlay-manual-markers.csv — all manual markers
- 2026-11-02-overlay-mob-attack-correlation.csv — all mob/Troll correlation rows
- albion-knowledge-bundle-import.json — merged knowledge-bundle database
- albion-troll-target-correlation-2026-11-03.csv — dedicated Troll subset
- albion-mob-target-correlation-2026-11-03.csv — dedicated Mobs subset
- Machine-readable file SHA-256 manifest: albion-complete-database-manifest-2026-11-03.json

## Preservation rules
- Runtime IDs are scoped by archive + PCAP/session; never merge numeric IDs alone.
- Event 39 static-resource observations are not automatically typed Event 40 nodes.
- Event 123 records are not all independently proven hostile mobs.
- Leave is not kill confirmation.
- ZIP text keyword-hit counts are not counts of nodes, Trolls, or mobs.
- Unknown family/type evidence remains preserved as unknown.
