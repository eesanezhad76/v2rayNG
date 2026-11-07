# Complete Albion inbox database — 2026-11-06

Consolidated index for all original ZIP archives. Raw ZIPs remain unchanged in research/inbox. Counts are capture-scoped unless stated otherwise.

## Intake and integrity
- ZIP archives: **15**
- PCAP members: **36**; unique PCAP hashes: **35**; byte-identical duplicates: **1**
- PCAP analyzer errors: **0**
- JSONL members: **21**
- Normalized observations: **806,306**; own-player Move(22) observations: **85,843**

## Resource nodes and static resources
- Event 40 harvestable-node records: **725**
- Family counts: 14: 86, 20: 9, 27: 192, 28: 2, 29: 259, 32: 1, 7: 176
- Event 46 state IDs: **709**; exact Event 40 joins: **175**; state-only: **534** (join key: archive + PCAP + region + entity ID)
- Event 39 static-resource observations: **49,551** (untyped candidates, not automatically confirmed nodes)
- Ore critter mobile TypeIDs by tier: T3: 570 Critter Ore Mountain Green, 571 Ore Elemental; T4: 572 Critter Ore Mountain Green, 573 Ore Elemental; T5: 574 Ore Elemental; T6: 576 Critter Ore Mountain Red
- Unresolved Event 40 type-number counts: 28: 2, 32: 1
- Static Ore type_number family is 23–27; living Ore Critters are separately identified by mobile TypeID and Loot.Harvestable semantics.
- User-confirmed T5.3 exception: entity 2788, family 27, coordinates (-379, -24); this does not classify all family-27 nodes.

## Mobs and Troll marker cycles
- Event 123 living/mobile lifecycle records: **4,373**; distinct Mobile TypeIDs: **124**; dictionary mapped: **124/124**
- Operation 23 attack-target tracks: **38**; Event 3 target-position samples: **3007**
- Manual marker records: **394**; completed lifecycle event rows (gathering_completed + node_depleted): **109**
- Aggregate marker-target rows: **13**; by activity/confidence: Mobs|LOW: 1, Troll|HIGH: 7, Troll|LOW: 1, Troll|MEDIUM: 4
- Curated Troll marker candidates: **12**; candidate target names: Ore Elemental: 11, Roaming Morgana Crossbowman: 1
- Troll identity-proven rows: **0**; distinct candidate target IDs: **10**; unlinked candidates: **0**
- Main-table Troll candidates: **12**; names: Ore Elemental: 12; main-vs-curated disagreements: **1**
- Mobs marker candidates: **1**; target-name candidates: Ore Elemental: 1; identity-proven: **0**
- Marker rows with exact region transition: **12/13**
- Marker label Troll is retained separately from decoded game name Ore Elemental; all marker-to-target links remain evidence-ranked, not identity-proven.
- Attack-track interpretations: attack_target_with_plausible_mode3_movement_kill_not_proven: 1, health_at_or_below_5_then_leave_after_attack_strong_defeat_candidate: 5, health_updates_then_leave_after_attack_defeat_likely_not_confirmed: 32. Leave is not proof of death.

## Knowledge bundles
- Imported bundles: **4**
- Source entity records across manifests: **10,767**; deduplicated session-scoped records: **9,522**
- Deduplicated protocol events: **4,599**
- Learned patterns: **70**; autonomous signatures: **296**
- Troll-labeled autonomous signatures: **69** (signatures, not individual mobs)

## Spatial evidence
- Captures checked: **59**; plausible same-local-coordinate-space: **20**; not verified: **39**
- Captured region-transition evidence: **42** records across **9** region IDs: 4000, 4001, 4002, 4206, 4207, 4209, 4213, 4217, 4219
- Zone names and global map coordinates remain unverified/unlicensed; a local coordinate alignment is not a world-map transform.

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
- Event 40/46 entity IDs are scoped by archive + PCAP + region_id when known + entity_id; never merge numeric IDs alone or across regions.
- Event 39 static-resource observations are not automatically typed Event 40 nodes.
- Event 123 records are not all independently proven hostile mobs.
- Leave is not kill confirmation.
- ZIP text keyword-hit counts are not counts of nodes, Trolls, or mobs.
- Unknown family/type evidence remains preserved as unknown.
