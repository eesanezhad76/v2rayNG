# AlbionRadar8.zip — forensic and gameplay-data findings

Source archive: `research/inbox/AlbionRadar8.zip`
ZIP SHA-256: `4620f0806d48c3a5d1d25908b4925846cc9ad360c0c2c233c25bf000601bcfde`

## Preserved source and derived files

The original ZIP remains intact in `research/inbox/`. Its extracted inventory, replay summary, node catalog, mob catalog, marker lifecycle data, and spatial-correlation outputs are already preserved under `research/analysis/` and `research/database/`. This repository is public; these paths are not private storage, so no private-storage claim is made.

## Capture integrity

- JSONL: 112,767 records; 0 malformed JSONL records.
- PCAP: 18,981,978 bytes; link type 101.
- Replay: 77,128 valid Photon packets; 12 malformed Photon packets; 5 malformed Protocol18 messages; 2 pending fragments.
- Replay quality: FAIR (score 80); 18 timestamp regressions, 343 gaps over one second, maximum gap 11,929 ms.
- The replay does not verify delivery into the Android overlay.

## Resource and entity evidence

- Event 39 static-resource observations: 1,898 events; 8,560 mapped static-resource observations in replay inventory.
- Event 40 typed harvestable observations: 320.
- Event 46 resource-state observations: 219.
- Event 123 mob spawn observations: 2,620.
- Capture-local catalog: 75 resource nodes, 626 mob entities, 49 manual markers.
- Of the 75 catalog nodes: 21 marked depleted, 54 not depleted.
- Node tier distribution: T1=1, T3=2, T4=38, T5=34.
- Enchantment distribution: Normal=5, E1=45, E2=20, E3=5.
- Family distribution: 7=21, 14=8, 20=1, 27=35, 29=10.

All 75 resource catalog rows remain unclassified candidates. The 10 family-29 rows are Ore candidates, not packet-confirmed Ore identities. Other family mappings remain unresolved. Candidate family numbers must not be promoted to confirmed resource names.

## Marker and combat correlations

- 49 marker lifecycles were retained.
- 3 mob-to-marker spatial correlations were produced; all are LOW or AMBIGUOUS confidence, and none proves entity identity.
- Several Troll marker events have no spatial candidate.
- The capture contains substantial repeated movement data for future heading/route calibration, but overlay delivery is not verified.

## Implementation issue identified and addressed

Event 46 reports the remaining resource amount. A value of zero means the node is depleted; checking only for a missing/null value incorrectly left zero-count nodes available. The mapper now treats null or zero/negative remaining amount as depleted. The live store retains that state, records the remaining amount, and clears depletion only when a later typed harvestable observation reports a positive amount. Depleted/left nodes render black and are excluded from nearest-node selection.

## Further research opportunities

The replay contains many event families beyond the current typed map, including 324, 360, 376, 304, 308, 311, 312, 544, 558, and 604. Their parameters should be decoded from raw packet evidence before assigning gameplay meaning. The archive is useful for improving resource lifecycle tracking, route calibration, mob lifecycle research, and marker-to-entity correlation. Its coordinates must not be combined with other captures until zone identity is decoded.


## Marker-cycle cross-check and incremental database value

A second pass over the capture-local marker lifecycle export confirms:

- 43 Ore marker cycles: 39 clean completed cycles, 2 completed cycles with tier/enchantment metadata conflicts, and 2 found-but-not-started cycles. This is 41 completed Ore cycles in total; the two conflict rows must remain flagged rather than silently normalized.
- The Ore marker labels comprise 36 T4 and 7 T5 observations; enchantment labels comprise E1=15, E2=26, E3=2. These are marker metadata, not a substitute for packet-confirmed resource identity.
- 6 Troll target-found-to-killed marker cycles: 3 labeled T4 and 3 labeled T5; E1=4 and E3=2.
- The six Troll marker rows correlate to five unique runtime target IDs. Two overlapping T5/E1 marker cycles point to the same runtime entity 253631; they must not be counted as two distinct killed mobs. The capture-local correlation rows resolve to Ore Elemental mobile types 573 (T4) and 574 (T5), but marker-to-runtime identity remains inferred, not proven.
- The lifecycle CSV has no populated started_ms values for Ore rows even though raw marker totals include 21 gathering_started and 111 gathering_completed events. Therefore mining duration cannot safely be computed from the current lifecycle export. This is a parser/join gap to fix in a later pass; raw marker events remain preserved.

These are capture-local additions/quality notes only. The archive's 75 resource records, 626 mobile entities, and Troll correlations were already included in the 2026-11-03 consolidated database; do not duplicate them as new entities or merge their coordinates across zones.
