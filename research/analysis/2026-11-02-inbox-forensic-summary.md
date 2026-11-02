# Forensic summary of the three inbox ZIPs

Analysis date: 2026-11-02  
Branch: albion-pattern-mining  
Decoder: the same Kotlin Photon/Protocol18 decoder classes used by the Android app, run offline by GitHub Actions.

## Overall result

- ZIP archives processed: **3**
- PCAP files decoded: **19**
- Analyzer errors: **0**
- Normalized detailed observations in the latest run: **23,020**
- Outbound local Move (operation 22) coordinate samples: **1,704**
- File-scoped Event 40 harvestable IDs: **55**
- File-scoped Event 46 resource-state IDs: **53**
- Event 46 state-only IDs without a same-PCAP Event 40 coordinate record: **48**
- Exact Event 40 ↔ Event 46 identity joins: **5**
- Event 39 static-resource coordinate observations: **507**
- Event 123 living/mobile-entity records: **126** (not all necessarily mobs)
- AttackStart target IDs: **11**
- Raw health/energy event messages preserved: **757** rows (Event 6/7/8/91/92)

The original ZIP files remain unchanged in research/inbox/. Identity is scoped by **archive + PCAP filename + runtime entity ID**. Runtime IDs are not globally unique across independent captures, so do not merge them by numeric ID alone.

## Archive and test-action manifest

The ZIPs do not contain separate README/test-note documents. Descriptions below are taken from PCAP filenames; filenames indicate the intended test action, not independent proof that every step completed.

### Albion اااستسمنسمنش.zip — 3 PCAPs

1. **بیکار سوار مونت موندم.pcap** — idle/mounted baseline.
2. **فقط ماب کشتم.pcap** — combat/mob-only test.
3. **پیاده شدم اور ماین کردم دوتا قرمز و سوار .pcap** — dismount, mine two red ore nodes, remount.

### Albion2.zip — 9 PCAPs

1. **2 blue gholem ench 2 killed together and mined besides each other .pcap** — two blue enchanted golems attacked/killed, then mined nearby nodes.
2. **6red ores ,each one mined.pcap** — six red ore nodes, mined individually.
3. **blue enchanted 2 gholem killed and mined .pcap** — blue enchanted golem combat and mining.
4. **blue ore ench1 mined .pcap** — blue E1 ore gathering.
5. **blue ore ench2 , while mining a mob attacked me,, and I killed him, but the ore lost its enchantment after it.pcap** — blue E2 ore gathering interrupted by a mob attack; filename records that the attacker was killed and the ore lost its enchantment.
6. **red gholem lvl2 ench killed and mines.pcap** — red level-2 enchanted golem combat and mining.
7. **red ore ench 1 mined , mount dismount 3 times accidentally before mine.pcap** — red E1 ore test with three accidental mount/dismount actions before gathering.
8. **red,ore lvl2.pcap** — red level-2 ore test.
9. **went from yellow 5 region to fort sterling, and then to bank and inspectoed a few of my items and equipmentz.pcap** — zone/city/bank travel and inventory/equipment inspection.

### Albions.zip — 7 PCAPs

1. **1 mob dismount and mount .pcap** — dismount, engage one mob, remount.
2. **2 times 90degred change direction .pcap** — movement/heading experiment with two major turns.
3. **2mobs dismount and Mount .pcap** — dismount, engage two mobs, remount.
4. **gholem dismount kill mine mount.pcap** — dismount, golem combat, ore gathering, remount.
5. **ore 4 3 red 2 blue .pcap** — ore-tier/type observation test; filename is retained literally rather than guessing its shorthand.
6. **run and stop.pcap** — straight movement then stop.
7. **stopping.pcap** — separate stopping capture; it contains no decoded Move(22) requests and cannot independently supply a position trace.

## Resource-node registry

### Family/tier/enchantment counts

The 55 Event 40 records are file-scoped node identities.

| Resource family (type_number) | File-scoped IDs |
|---:|---:|
| 7 | 11 |
| 14 | 4 |
| 20 | 5 |
| 27 | 7 |
| 28 | 2 |
| 29 | 26 |

Known Ore family 29 mobile-type mappings retained from packet evidence:
- T3 normal: mobile type 571.
- T4 normal: mobile type 573.
- T5 normal: mobile type 574.

Other observed combinations include family 29 T4 E1/E2 and T5 E1; family 7 T4/T5 E1; family 27 T4/T5 E1/E2; family 14 T4 E1/E2; family 28 T5 E1; and family 20 T1 E0. Families 7/14/27/28 report mobile type -1 in these Event 40 observations; their mobile-type mapping remains unresolved. Family 20 uses raw mobile type 482 and is not automatically classified as ore.

### Five exact Event 40 ↔ Event 46 joins

| Capture | Runtime ID | Mobile type | Family / tier / enchant | Coordinates (X,Y) | State sequence |
|---|---:|---:|---|---|---|
| 2 blue gholem... | 642433 | 573 | 29 / T4 / E1 | (-261.5, 109.0) | 2→1→0 |
| 2 blue gholem... | 642510 | 573 | 29 / T4 / E1 | (-260.56326, 110.63506) | 2→1→0 |
| blue enchanted 2 gholem... | 639684 | 573 | 29 / T4 / E2 | (-262.5214, 314.02997) | 2→1→0 |
| red gholem lvl2... | 641585 | 574 | 29 / T5 / E1 | (-308.4258, 83.123505) | 4→3→2→1→0 |
| gho lem dismount kill mine mount... | 601922 | 574 | 29 / T5 / E1 | (-382.81464, 253.60684) | 4→3→2→1→0 |

The 48 state-only IDs are preserved with their full ordered state values and timestamps; no coordinates are assigned to them by proximity. Five exact joins are not evidence that the remaining IDs were absent from the zone—only that these files did not provide a same-PCAP Event 40 location snapshot for them.

Event 39 contributes 507 static-resource observations. Those IDs/coordinates are retained separately and are not automatically equated to Event 40 harvestable IDs.

## Mob/combat correlation

The mapper now distinguishes:
- Event 6: HealthUpdate
- Event 7: HealthUpdates (batched)
- Event 8: EnergyUpdate
- Event 91: RegenerationHealthChanged
- Event 92: RegenerationEnergyChanged

For Event 7, key 0 is the entity whose health changes; key 6 carries the aligned source/attacker IDs. Treating key 6 as the health target would attribute the wrong entity's HP. Raw parameter sets remain preserved in the health/energy event CSV.

AttackStart is operation 23. The current 11 target records are:

| Capture | Target ID | AttackStart requests | Min observed HP | Leave after last attack | Interpretation |
|---|---:|---:|---:|---|---|
| فقط ماب کشتم.pcap | 450979 | 3 | 30 | No | No confirmed defeat |
| فقط ماب کشتم.pcap | 454770 | 10 | 15 | Yes | Defeat likely, not confirmed |
| فقط ماب کشتم.pcap | 455074 | 17 | 5 | Yes | Strong defeat candidate |
| 2 blue gholem... | 630243 | 11 | 4 | Yes | Strong defeat candidate |
| 2 blue gholem... | 640358 | 12 | 126 | Yes | Defeat likely, not confirmed |
| blue enchanted 2 gholem... | 638588 | 8 | 119 | Yes | Defeat likely, not confirmed |
| blue ore ench2... | 645014 | 5 | 2 | Yes | Strong defeat candidate |
| red gholem lvl2... | 633091 | 17 | 13 | Yes | Defeat likely, not confirmed |
| 2mobs dismount... | 586309 | 7 | 12 | Yes | Defeat likely, not confirmed |
| 2mobs dismount... | 589427 | 2 | 17 | Yes | Defeat likely, not confirmed |
| gho lem dismount... | 600258 | 14 | 64 | Yes | Defeat likely, not confirmed |

In the two-blue-golem file, target IDs 630243 and 640358 receive repeated health updates from source ID 636743; their HP falls to 4 and 126 respectively, and each receives a Leave event after its last attack request. Event 7 also records both target IDs as damage sources in health changes to entity 636743. This is consistent with reciprocal combat involving both targets and supports the filename's two-golem scenario. It does not independently prove that entity 636743 is the user's character.

Across all 11 attack targets, 10 show Leave after the last AttackStart. Leave means the server removed the entity from the client's local visibility/interest stream; it is not a formal kill confirmation. Three targets reach observed HP values at or below 5 before leaving. The other seven have health updates and later Leave but retain a positive last observed HP. These remain candidates/likely defeats, not asserted confirmed kills.

The Event 123 table has 126 file-scoped living/mobile entity records; it is intentionally not labelled “126 mobs”, because it includes mobile entities that are resources or otherwise not proven to be mobs.

## Persistent output files

The GitHub ingest runner writes:
- 2026-11-02-all-inbox-pcap-analysis.md — all 19 PCAP reports and event inventories.
- 2026-11-02-all-inbox-observations.jsonl — detailed timestamped observations.
- 2026-11-02-own-player-movement-timeline.csv — outbound local Move(22) coordinate timeline.
- 2026-11-02-pcap-harvestable-nodes.csv — Event 40 node registry.
- 2026-11-02-pcap-resource-state-timelines.csv — Event 46 state sequences and exact-join flags.
- 2026-11-02-pcap-static-resources.csv — Event 39 static-resource positions.
- 2026-11-02-pcap-mob-lifecycle.csv — Event 123 mobile/living entities plus movement, health/energy, regeneration and Leave observations.
- 2026-11-02-pcap-health-energy-events.csv — raw Event 6/7/8/91/92 parameter sets.
- 2026-11-02-pcap-attack-target-tracks.csv — AttackStart targets joined to health changes, damage-source IDs, movement and Leave timing.

Normalized datasets are exported both to research/analysis/ and research/database/; per-PCAP forensic reports stay under research/analysis/.

## Important limitations

- No README/test-description files were found inside the three archives; the action mapping above is based on filenames.
- Runtime IDs are file-scoped and can repeat across files. Do not globally join on numeric ID alone.
- Unknown family/mobile mappings remain unknown.
- Leave events are not conclusive proof of death.
- Coordinates are game-world coordinates; no map-pixel or GPS transform is implemented here.
- The automation ran successfully on GitHub Actions; this does not claim the current APK has been installed or physically tested on the user's phone.
