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
