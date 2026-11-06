# Complete capture database and map-coverage audit — 2026-11-06

Repository: `eesanezhad76/v2rayNG`  
Active branch: `albion-pattern-mining`  
Audit base HEAD: `a453c812483f285dcae97d0ad0bc044c0cec11fa`

This is the current authoritative summary of the existing capture corpus and its remaining gaps. It does not claim that a licensed global world-map database has been completed.

## 1. Capture intake and integrity

The aggregate manifest records 14 source archive inventories (including the extracted `archiveNew-extracted.zip` dataset), 35 PCAP members, 34 unique PCAP SHA-256 hashes, one byte-identical duplicate, 20 JSONL members, and zero PCAP analyzer errors.

- Normalized observation rows: **761,082**
- Own-player operation-22 movement observations: **81,881**
- All source archives and extracted evidence remain preserved; no raw PCAP or original archive was replaced.

## 2. Current evidence database coverage

| Evidence family | Current records | Interpretation |
|---|---:|---|
| Event 39 static-resource observations | 47,108 | Untyped static-resource candidates; not all are confirmed nodes |
| Event 40 resource-node observations | 652 | File-scoped node records |
| Event 46 state IDs | 629 | 161 exact Event 40 joins; 468 state-only IDs |
| Event 123 living/mobile lifecycle records | 4,172 | Not every record is independently proven to be a hostile mob |
| Distinct observed Event 123 Mobile TypeIDs | 116 | 116/116 matched to the current type dictionary |
| Operation 23 attack-target tracks | 37 | 3,007 Event 3 target-position samples |
| Manual marker events | 369 | 109 gathering_completed + node_depleted events |
| Manual marker-target aggregate rows | 13 | 12 Troll labels + 1 Mobs label |

Event 40 family/type-number counts:

| type_number | Records | Current interpretation |
|---:|---:|---|
| 7 | 157 | ROCK |
| 14 | 79 | FIBER |
| 20 | 9 | HIDE |
| 27 | 175 | Static ORE family candidate |
| 28 | 2 | UNRESOLVED |
| 29 | 229 | Living ORE_RESOURCE_CREATURE |
| 32 | 1 | UNRESOLVED |

The imported MIT OpenRadar source-code references document static resource ranges: WOOD 0–5, ROCK 6–10, FIBER 11–15, HIDE 16–22, ORE 23–27. Family 29 is a separate living Ore Critter classification; do not conflate it with static Event 40 type_number 27. The legacy `MobsDatabase.OFFSET=16` remains Protocol16-era reference only and is not approved for Protocol18 production.

## 3. Ore Critter TypeID database — expanded

The complete crosswalk and node catalog already contain six observed Ore Critter Mobile TypeIDs:

| Tier | Mobile TypeID | Dictionary name | Event 40 node records | Event 123 lifecycle records |
|---:|---:|---|---:|---:|
| T3 | 570 | Critter Ore Mountain Green | 1 | 34 |
| T3 | 571 | Ore Elemental (Mountain Red) | 21 | 227 |
| T4 | 572 | Critter Ore Mountain Green | 29 | 42 |
| T4 | 573 | Ore Elemental (Mountain Red) | 68 | 210 |
| T5 | 574 | Ore Elemental (Mountain Red) | 107 | 122 |
| T6 | 576 | Critter Ore Mountain Red | 3 | 2 |

Totals: **229 Event 40 nodes** and **637 Event 123 lifecycle records** across these six TypeIDs.

The prior three-ID summary (571/573/574) was incomplete. TypeID 575 is not present in this capture corpus and must remain unassigned. Event 40 type_number 28 (2 records) and 32 (1 record) also remain unresolved. Do not fill these gaps by interpolation.

The machine-readable manifest now records the full tiered Ore Critter crosswalk and per-type Event 40 / Event 123 counts. Event 46 has 1,523 updates across 629 region-scoped IDs; 161 join to Event 40 by exact archive + PCAP + region + entity ID, and 468 remain state-only. The earlier 297/213 summary collapsed IDs across regions/sessions and is superseded.

## 4. Troll / Mobs marker-to-target evidence — region join repaired

The main aggregate correlation table contains 13 rows: 12 `Troll` activity markers and one `Mobs` activity marker. Its header previously had 21 columns while rows contained only 20 values, shifting `Troll`/`Mobs` into the `region_id` column. The original malformed file was preserved at:

`research/analysis/2026-11-02-overlay-mob-attack-correlation.pre-region-join.csv`

The current main analysis and database copies now have a correctly aligned 21-column schema. Exact `region_id` values were joined only from the same archive + PCAP's transition timeline and only using the latest transition timestamp at or before the marker timestamp:

- Main aggregate: 13 rows; 12 have an exact region ID; one old Mobs row remains unjoined.
- Troll rows: 12/12 region-joined.
- Mobs row: 0/1 region-joined; no transition record exists in that capture.

### Conservative dedicated Troll candidate table

The dedicated table is intentionally preserved separately from the broader aggregate table:

| Candidate target name | Mobile TypeID | Rows |
|---|---:|---:|
| Ore Elemental | 573 | 6 |
| Ore Elemental | 574 | 5 |
| Roaming Morgana Crossbowman | 1471 | 1 |

Confidence in the dedicated table: HIGH 6, MEDIUM 4, LOW 2. **Identity-proven rows: 0.** These are marker-to-target candidates, not verified species identities or kill confirmations. The 116-row observed Mobile TypeID crosswalk contains no display_name or unique_name with “Troll”; the pinned OpenRadar source search likewise found no Troll data entry. “Troll” therefore remains a user activity-marker label, not a decoded game Mob name.

### Important unresolved disagreement

For the final Troll marker at `found_ms=1793792784792`, in captured region ID `4219`:

- Main aggregate table: two candidates; selects target entity 28922, TypeID 573 Ore Elemental, HIGH confidence. Its candidate evidence also includes entity 7480 / TypeID 1471.
- Dedicated conservative table: selects target entity 7480, TypeID 1471 Roaming Morgana Crossbowman, LOW confidence.
- Both retain `identity_proven=false`.

This disagreement is explicitly stored in the manifest. Neither interpretation is promoted to a confirmed Troll species. The label `Troll` remains the user's activity marker, not a decoded Mob name.

The separate Mobs marker has one Ore Elemental candidate (TypeID 573), LOW confidence, and no proven identity.

## 5. Region and map evidence

The user's capture-derived region-transition table contains **33 records across 9 numeric region IDs**:

`4000, 4001, 4002, 4206, 4207, 4209, 4213, 4217, 4219`.

Troll marker rows occur in:
- Region 4206: 4 rows
- Region 4209: 6 rows
- Region 4219: 2 rows

Cross-reference names from the pinned OpenRadar `zones.json` reference are:
- 4206 — Tharcal Fissure
- 4209 — Cairn Darg
- 4219 — Caulder Fissure

The other captured IDs also cross-reference to Fort Sterling, its bank/market, Pen Gent, Cairn Camain and Pen Fenair. These names are **analysis-only candidates** from game-derived data with no verified redistribution license. No zone-name dataset, world JSON, map tiles, global coordinates, exits or terrain geometry was copied into the app or research database.

### Additional protocol/map schema reference\n\nThe existing MIT `ao-data/albiondata-client@c6edd9456bec4657755c25f173e8ebc7a31a17f7` snapshot was expanded with five exact upstream blobs: event enum/string mapping, red-zone world-map event model, `GetClusterMapInfo` response and `MapDataUpload` coordinate/building schema. This provides an independent EventType and zone/building-coordinate schema reference, but no actual world-map data. The Photon parser/deserializer was excluded due separately restrictive JPCodeCraft/AlbionDataAvalonia provenance. No captures, `zones.json`, map images, tiles or game data were copied. See `analysis/2026-11-06-albiondata-client-event-map-expansion.md` in the external quarantine.\n\n### AODP atlas discovery

The separate external quarantine branch now contains the AODP `boxes.json` metadata (327 unlabeled rectangles; pixel extent x=1..2047 and y=850..2047) plus its bundled MIT notice. The matching 2.88 MB `MiniMapAtlas.png` was not imported. The boxes have no zone IDs/names and cannot yet be joined to the capture region IDs. The atlas remains quarantined pending license-scope and mapping verification.

Other map/pathfinder/dungeon repositories were audited. Unlicensed map JSON, coordinate grids and map images remain held. A binary-only radar updater with contradictory license claims and advertised stealth functionality was rejected and never executed. Full source decisions are in the external repository report:
`AlbionRadar-External-Research/analysis/2026-11-06-deep-map-zone-radar-source-sweep.md`.

## 6. Spatial alignment correction

The authoritative per-capture spatial report contains 34 captures:
- PLAUSIBLE_SAME_LOCAL_COORDINATE_SPACE: **15**
- NOT_VERIFIED: **19**

A bug in `build_complete_database_index.py` used a nonexistent `plausible_captures` key and defaulted to 11. The builder now derives this value from the 34 per-capture status rows, and the aggregate manifest is corrected to 15. This remains local coordinate alignment only; it does not prove zone identity or a global world-map transform.

## 7. Database builder and manifest repairs

The index builder now:
- Derives all `ORE_CRITTER` Mobile TypeIDs by tier from the current 116-row crosswalk and includes Event 40 node counts.
- Records unresolved type-number counts without inventing mappings.
- Derives spatial plausible counts from the actual status rows.
- Reads the dedicated conservative Troll/Mobs candidate tables rather than overwriting them from the broader aggregate table.
- Separates candidate target names from identity-proven names and records main-vs-curated disagreements.
- Preserves exact region joins and reports how many marker rows remain unjoined.

The manifest and builder were reconciled with the evidence. CI run #365 (run ID 42250739007) completed 44 tests successfully, including the corrected Spatial test, but the builder step failed on accidental JavaScript syntax in Python report formatting. That syntax was corrected in commit `d3dc5b81d577c02dea34a680c440494977bfa7a2`. CI run #369 (run ID 42252090655) is currently in progress and includes the new `test_capture_database_integrity.py` regression suite plus the corrected builder. No success is claimed until that run completes.

## 8. Exact file changes in this audit

| Operation | Path | Before | After |
|---|---|---|---|
| Create preservation copy | `research/analysis/2026-11-02-overlay-mob-attack-correlation.pre-region-join.csv` | ABSENT | Original malformed CSV preserved; create commit `aeda1971370c6bb08ff952b52d264c0516a4e624` |
| Repair | `research/analysis/2026-11-02-overlay-mob-attack-correlation.csv` | `10b6b10eb0971575393adbcaeb6d1cb7699f2907` | `f461c3473715784804ac0cc0c21d9807c80adb3d` |
| Repair | `research/database/2026-11-02-overlay-mob-attack-correlation.csv` | `10b6b10eb0971575393adbcaeb6d1cb7699f2907` | `f461c3473715784804ac0cc0c21d9807c80adb3d` |
| Enrich | `research/database/albion-troll-target-correlation-2026-11-03.csv` | `0983fe06501bce0c85b084d35fa6215d48e3bf74` | `59f400f1c95fc70a3d8d4080bcb03871e28619ec` |
| Enrich | `research/database/albion-mob-target-correlation-2026-11-03.csv` | `4daad39f7cbb2fe6e469b38ff752a07025c2f2dc` | `cc52648f12f3795e1a0491b4846c327987a196aa` |
| Update | `research/tools/build_complete_database_index.py` | `c7cb4da59621b8da21875b2a14e59eee99fa9909` | `5645a1b043b78b0753f6ac8216c2bd7efbd036bf` |
| Update | `research/database/albion-complete-database-manifest-2026-11-03.json` | `afca48704cd2d4a3919a9c7a4697060255ce2329` | `b6886bc153908a0e1611f4ab8b19e78e753f7228` |

No production app source, runtime asset, raw PCAP, original archive, user data or third-party upstream repository was modified. The latest completed re-ingest working-tree output (not yet committed because the builder step failed) reported 54 spatial captures with 18 plausible local alignments, 3,015 Event 3 target-position samples and 106 completed lifecycle marker events. The committed manifest remains on the previous snapshot until run #369 completes.
