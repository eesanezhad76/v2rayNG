# Albion research database

This directory is the durable, versioned knowledge base. Raw captures belong in `research/inbox/`; derived normalized datasets belong here. Never treat an unverified decode as a build constant.

## Current authoritative aggregate

- [Complete database manifest](albion-complete-database-manifest-2026-11-03.json) — capture inventory, table hashes, Event 39/40/46/123 counts, Ore Critter variants, marker candidate evidence, zone IDs and spatial status.
- [2026-11-06 complete database audit](../analysis/2026-11-06-complete-database-audit.md) — latest coverage and unresolved gaps.
- [All normalized observations](2026-11-02-all-inbox-observations.jsonl)
- [Event 40 harvestable nodes](2026-11-02-pcap-harvestable-nodes.csv)
- [Resource-node catalog](albion-resource-node-catalog-2026-11-03.csv)
- [Event 123 mob lifecycle](2026-11-02-pcap-mob-lifecycle.csv)
- [Mobile TypeID crosswalk](albion-mobile-type-crosswalk-2026-11-03.csv)
- [Event 39 static-resource candidates](2026-11-02-pcap-static-resources.csv)
- [Event 46 resource-state timelines](2026-11-02-pcap-resource-state-timelines.csv)
- [Region transition evidence](2026-11-02-region-transition-evidence.csv)
- [Main mob/marker/target correlation](2026-11-02-overlay-mob-attack-correlation.csv)
- [Conservative Troll target candidates](albion-troll-target-correlation-2026-11-03.csv)
- [Conservative Mobs target candidates](albion-mob-target-correlation-2026-11-03.csv)
- [Attack-target tracks](2026-11-02-pcap-attack-target-tracks.csv)
- [Merged knowledge bundles](albion-knowledge-bundle-import.json)

## Current capture coverage

| Dataset | Count |
|---|---:|
| Source archive inventories | 14 |
| PCAP members | 35 |
| Unique PCAP SHA-256 | 34 |
| Byte-identical duplicate PCAPs | 1 |
| JSONL members | 20 |
| Normalized observations | 761,082 |
| Own-player Move(22) observations | 81,881 |
| Event 39 static-resource candidates | 47,108 |
| Event 40 resource-node records | 652 |
| Event 46 state IDs | 629 (161 exact Event 40 joins; 468 state-only; 1,523 updates) |
| Event 123 lifecycle records | 4,172 |
| Distinct observed Mobile TypeIDs mapped | 116/116 |
| Region-transition records | 33 across 9 numeric IDs |
| Operation 23 attack-target tracks | 37 (3,007 Event 3 position samples) |
| Manual marker events | 369 (109 gathering_completed + node_depleted) |

## Ore Critter variants observed

| Tier | Mobile TypeID | Display name | Event 40 nodes | Event 123 records |
|---:|---:|---|---:|---:|
| T3 | 570 | Critter Ore Mountain Green | 1 | 34 |
| T3 | 571 | Ore Elemental (Mountain Red) | 21 | 227 |
| T4 | 572 | Critter Ore Mountain Green | 29 | 42 |
| T4 | 573 | Ore Elemental (Mountain Red) | 68 | 210 |
| T5 | 574 | Ore Elemental (Mountain Red) | 107 | 122 |
| T6 | 576 | Critter Ore Mountain Red | 3 | 2 |

These six variants account for 229 Event 40 nodes and 637 Event 123 lifecycle records. TypeID 575 is not observed in the current corpus. Event 40 type_number 28 (2 records) and 32 (1 record) remain unresolved.

## Identity and join rules

- `entity_id`: runtime identity of one world node; scope it by archive + PCAP/session + region ID when available.
- `type_number`: Event 40 resource-family number.
- `mobile_type_id`: separate game mobile code; null or unknown when not established.
- Static resource type_number ranges from the imported MIT code reference: WOOD 0–5, ROCK 6–10, FIBER 11–15, HIDE 16–22, ORE 23–27.
- Living Ore Critters are a separate family (29) and are identified by Mobile TypeID / harvestable mob metadata, not by static type_number 27.
- Event 46 `amount_or_state` is preserved raw. Do not assume its semantics are identical for every family.
- Event 39 is untyped static-resource evidence; do not automatically merge it with Event 40 nodes.
- Event 123 records are not all independently proven hostile mobs.
- Marker-to-target matches are candidates. `identity_proven=false` means no confirmed species or kill identity.
- Event 46 must be joined to Event 40 by the exact composite key archive + PCAP + region_id + entity_id when region is known; never join across captures/regions or by proximity/guessing.
- Zone names and world-map geometry remain reference-only until a reusable, licensed dataset and a validated coordinate transform are available.

## Spatial and map limitations

The 34-capture spatial report currently has 15 `PLAUSIBLE_SAME_LOCAL_COORDINATE_SPACE` and 19 `NOT_VERIFIED` results. This only supports local coordinate alignment; it does not establish a zone or global world-map transform.

Event 46 state IDs are scoped by archive + PCAP + region + entity ID: 629 total, 161 exact Event 40 joins and 468 state-only. The earlier 297/213 counts collapsed IDs across sessions/regions and are superseded.

The region-transition database contains numeric IDs from user captures. Candidate names were cross-referenced for analysis only against an external game-derived dataset whose redistribution license is unverified; no world-zone names, map tiles, terrain, roads or coordinate database were copied into this repository or the APK.

Only promote a mapping into app code after independent captures and tests confirm it. Keep the complete evidence and uncertainty in the research vault.
