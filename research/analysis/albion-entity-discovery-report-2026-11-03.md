# Albion entity discovery — evidence-ranked reconnaissance

Profiles every Event 40 Type Number, Event 123 mobile type, and merged Knowledge schema. Numeric IDs are not assigned names without independent evidence.

## Coverage
- Event 40 node records: **725**
- Event 123 lifecycle records: **4373**
- Knowledge entity records: **9522**
- Learned patterns: **70**; autonomous patterns: **296**; protocol evidence: **4599**
- Mobile TypeID dictionary coverage: **124/124** PCAP types mapped
- Dictionary source: Nouuu/Albion-Online-OpenRadar at 466c70b04e13ed0e8739ba23e6a950f7caf495e5

## Event 40 type-number profiles
| Type number | Records | Resource mapping | Mobile Type IDs | Tiers | Interpretation |
|---:|---:|---|---|---|---|
| 14 | 86 | FIBER | -1 (86) | 4 (43), 5 (41), 6 (2) | type_number_resource_mapping_candidate |
| 20 | 9 | HIDE | 482 (9) | 1 (9) | hide_type_number_with_nonharvestable_mobile_fallback |
| 27 | 192 | ORE | -1 (192) | 4 (104), 5 (79), 6 (9) | ore_type_number_candidate |
| 28 | 2 | UNRESOLVED | -1 (2) | 5 (2) | unresolved_type_number_outside_static_range |
| 29 | 259 | ORE_RESOURCE_CREATURE | 574 (116), 573 (79), 572 (34), 571 (21), 570 (6), 576 (3) | 5 (116), 4 (113), 3 (27), 6 (3) | confirmed_ore_resource_creature |
| 32 | 1 | UNRESOLVED | -1 (1) | 4 (1) | unclassified |
| 7 | 176 | ROCK | -1 (176) | 4 (90), 5 (75), 6 (11) | type_number_resource_mapping_candidate |

## Event 123 mobile TypeID crosswalk
| Type ID | Name | Tier | Category | Loot type | Harvestable | PCAP records |
|---:|---|---:|---|---|---|---:|
| 482 | Mountain Rabbit | 1 | harmless | HIDE | False | 1230 |
| 2489 | Keeper Axe Thrower | 5 | roaming | SILVERCOINS_LOOT_ROAMING | False | 252 |
| 2474 | Keeper Berserk | 5 | roaming | SILVERCOINS_LOOT_ROAMING | False | 247 |
| 571 | Ore Elemental | 3 | critter | ORE_CRITTER | True | 227 |
| 573 | Ore Elemental | 4 | critter | ORE_CRITTER | True | 223 |
| 120 | Mists Solo Wisp | 4 | rd_solo | None | False | 168 |
| 2486 | Keeper Axe Thrower Recruit | 5 | roaming | SILVERCOINS_LOOT_ROAMING | False | 152 |
| 574 | Ore Elemental | 5 | critter | ORE_CRITTER | True | 125 |
| 2471 | Keeper Berserk Recruit | 5 | roaming | SILVERCOINS_LOOT_ROAMING | False | 123 |
| 2712 | Mountain Guard | 5 | factionwarfare | None | False | 104 |
| 2510 | Keeper Druid | 5 | roaming | SILVERCOINS_LOOT_ROAMING | False | 101 |
| 2477 | Keeper Berserk Champion | 5 | champion | SILVERCOINS_LOOT_ROAMING | False | 77 |
| 2492 | Keeper Axe Thrower Champion | 5 | champion | SILVERCOINS_LOOT_ROAMING | False | 68 |
| 1381 | Roaming Morgana Soldier | 6 | roaming | SILVERCOINS_LOOT_ROAMING | False | 63 |
| 1888 | Roaming Undead Ghoul | 4 | standard | SILVERCOINS_LOOT_ROAMING | False | 63 |
| 1471 | Roaming Morgana Crossbowman | 6 | standard | SILVERCOINS_LOOT_ROAMING | False | 59 |
| 1908 | Roaming Undead Soldier | 4 | standard | SILVERCOINS_LOOT_ROAMING | False | 57 |
| 2048 | Roaming Undead Mage | 4 | standard | SILVERCOINS_LOOT_ROAMING | False | 57 |
| 1968 | Roaming Undead Archer | 4 | standard | SILVERCOINS_LOOT_ROAMING | False | 53 |
| 1561 | Roaming Morgana Cultist | 6 | standard | SILVERCOINS_LOOT_ROAMING | False | 47 |
| 572 | Critter Ore Mountain Green | 4 | critter | ORE_CRITTER | True | 44 |
| 570 | Critter Ore Mountain Green | 3 | critter | ORE_CRITTER | True | 43 |
| 1453 | Roaming Morgana Recruit | 6 | trash | SILVERCOINS_LOOT_ROAMING | False | 40 |
| 1543 | Roaming Morgana Acolyte | 6 | trash | SILVERCOINS_LOOT_ROAMING | False | 33 |
| 2475 | Keeper Berserk | 5 | camp | SILVERCOINS_LOOT_ROAMING | False | 32 |
| 1363 | Roaming Morgana Recruit Melee | 6 | roaming | SILVERCOINS_LOOT_ROAMING | False | 30 |
| 2501 | Keeper Giant Champion | 5 | champion | SILVERCOINS_LOOT_ROAMING | False | 30 |
| 2549 | Keeper Berserk Champion | 5 | champion | SILVERCOINS_LOOT_VETERAN | False | 29 |
| 2512 | Keeper Druid | 5 | camp | SILVERCOINS_LOOT_VETERAN | False | 26 |
| 2513 | Keeper Druid Champion | 5 | champion | SILVERCOINS_LOOT_ROAMING | False | 22 |
| 2491 | Keeper Axe Thrower | 5 | camp | SILVERCOINS_LOOT_VETERAN | False | 21 |
| 1830 | Morgana Imp Summon | 6 | summon | None | False | 20 |
| 2490 | Keeper Axe Thrower | 5 | camp | SILVERCOINS_LOOT_ROAMING | False | 20 |
| 2511 | Keeper Druid | 5 | camp | SILVERCOINS_LOOT_ROAMING | False | 20 |
| 2564 | Keeper Axe Thrower Champion | 5 | champion | SILVERCOINS_LOOT_VETERAN | False | 20 |
| 1868 | Roaming Undead Skeleton | 4 | trash | SILVERCOINS_LOOT_ROAMING | False | 19 |
| 1917 | Roaming Undead Camp Soldier | 4 | standard | SILVERCOINS_LOOT_ROAMING | False | 19 |
| 2476 | Keeper Berserk | 5 | camp | SILVERCOINS_LOOT_VETERAN | False | 19 |
| 2473 | Keeper Berserk Recruit | 5 | camp | SILVERCOINS_LOOT_VETERAN_TRASH | False | 18 |
| 2723 | City Swamp Guard | 6 | factionwarfare | None | False | 18 |
| 2787 | Mountain Watchtower Crossbow | 5 | factionwarfare | None | False | 17 |
| 1977 | Roaming Undead Camp Archer | 4 | standard | SILVERCOINS_LOOT_ROAMING | False | 15 |
| 1848 | Roaming Undead Skeleton Melee | 4 | trash | SILVERCOINS_LOOT_ROAMING | False | 14 |
| 2478 | Keeper Berserk Champion | 5 | champion | SILVERCOINS_LOOT_ROAMING | False | 14 |
| 2488 | Keeper Axe Thrower Recruit | 5 | camp | SILVERCOINS_LOOT_VETERAN_TRASH | False | 12 |
| 2715 | Mountain Boss | 5 | boss | None | False | 12 |
| 2057 | Roaming Undead Camp Mage | 4 | standard | SILVERCOINS_LOOT_ROAMING | False | 11 |
| 2502 | Keeper Giant Champion | 5 | champion | SILVERCOINS_LOOT_ROAMING | False | 11 |
| 2493 | Keeper Axe Thrower Champion | 5 | champion | SILVERCOINS_LOOT_ROAMING | False | 10 |
| 830 | Mountain Chest | 4 | chest | SILVERCOINS_LOOT_CHEST_STANDARD | False | 10 |
| 2563 | Keeper Axe Thrower | 5 | static | SILVERCOINS_LOOT_VETERAN | False | 9 |
| 1489 | Roaming Morgana Crossbowman Champion | 6 | champion | SILVERCOINS_LOOT_ROAMING | False | 8 |
| 1579 | Roaming Morgana Cultist Champion | 6 | champion | SILVERCOINS_LOOT_ROAMING | False | 8 |
| 2802 | Mountain Watchtower Staff | 5 | factionwarfare | None | False | 8 |
| 121 | Mists Solo Wisp | 4 | rd_solo | None | False | 7 |
| 1633 | Roaming Morgana Knight Champion | 6 | champion | SILVERCOINS_LOOT_ROAMING | False | 7 |
| 2514 | Roaming Keeper Camp Druid Champion | 5 | champion | SILVERCOINS_LOOT_ROAMING | False | 7 |
| 2535 | Keeper Bear | 5 | trash | SILVERCOINS_LOOT_VETERAN_TRASH | False | 7 |
| 2576 | Keeper Druid | 5 | static | SILVERCOINS_LOOT_VETERAN | False | 7 |
| 835 | Roaming Mountain Chest | 5 | chest | SILVERCOINS_LOOT_CHEST_STANDARD | False | 7 |
| 2494 | Keeper Axe Thrower Champion | 5 | champion | SILVERCOINS_LOOT_VETERAN | False | 6 |
| 2503 | Roaming Keeper Camp Giant Veteran Champion | 5 | champion | SILVERCOINS_LOOT_VETERAN | False | 6 |
| 2714 | City Mountain Knight Elite | 4 | factionwarfare | None | False | 6 |
| 2757 | Guard Tower | 6 | factionwarfare | None | False | 6 |
| 2792 | City Fortress Mountain Crossbow | 6 | factionwarfare | None | False | 5 |
| 1674 | Morgana Bloodhound | 6 | unknown | SILVERCOINS_LOOT_STANDARD_TRASH | False | 4 |
| 1928 | Roaming Undead Soldier Champion | 4 | champion | SILVERCOINS_LOOT_ROAMING | False | 4 |
| 1957 | Roaming Undead Camp Soldier Miniboss | 4 | miniboss | SILVERCOINS_LOOT_ROAMING_MINIBOSS | False | 4 |
| 2515 | Roaming Keeper Camp Druid Veteran Champion | 5 | champion | SILVERCOINS_LOOT_VETERAN | False | 4 |
| 2539 | Keeper Earthchild Champion | 5 | champion | SILVERCOINS_LOOT_VETERAN | False | 4 |
| 2590 | Keeper Giant Champion | 5 | champion | SILVERCOINS_LOOT_VETERAN | False | 4 |
| 2675 | City Camp Caerleon Guard Ranged Veteran | 6 | factionwarfare | None | False | 4 |
| 2772 | Mountain Watchtower Sword | 5 | factionwarfare | None | False | 4 |
| 1396 | Roaming Morgana Camp Soldier Veteran | 6 | camp | SILVERCOINS_LOOT_VETERAN | False | 3 |
| 1399 | Roaming Morgana Soldier Champion | 6 | champion | SILVERCOINS_LOOT_ROAMING | False | 3 |
| 1468 | Roaming Morgana Camp Recruit Veteran | 6 | trash | SILVERCOINS_LOOT_VETERAN_TRASH | False | 3 |
| 2479 | Keeper Berserk Champion | 5 | champion | SILVERCOINS_LOOT_VETERAN | False | 3 |
| 2480 | Keeper Berserk Mini-Boss | 5 | miniboss | SILVERCOINS_LOOT_ROAMING_MINIBOSS | False | 3 |
| 2548 | Keeper Berserk | 5 | static | SILVERCOINS_LOOT_VETERAN | False | 3 |
| 2577 | Keeper Druid Champion | 5 | champion | SILVERCOINS_LOOT_VETERAN | False | 3 |
| 2634 | City Camp Caerleon Guard Male | 5 | factionwarfare | None | False | 3 |
| 2635 | City Camp Caerleon Guard Male | 6 | factionwarfare | None | False | 3 |
| 2639 | City Camp Caerleon Guard Female | 6 | factionwarfare | None | False | 3 |
| 2646 | City Camp Caerleon Guard Ranged | 5 | factionwarfare | None | False | 3 |
| 2663 | City Camp Caerleon Guard Male Veteran | 6 | factionwarfare | None | False | 3 |
| 2777 | City Fortress Mountain Sword | 6 | factionwarfare | None | False | 3 |
| 2807 | City Fortress Mountain Staff | 6 | factionwarfare | None | False | 3 |
| 1378 | Roaming Morgana Camp Recruit Melee Veteran | 6 | camp | SILVERCOINS_LOOT_VETERAN_TRASH | False | 2 |
| 1486 | Roaming Morgana Camp Crossbowman Veteran | 6 | standard | SILVERCOINS_LOOT_VETERAN | False | 2 |
| 1558 | Roaming Morgana Camp Acolyte Veteran | 6 | trash | SILVERCOINS_LOOT_VETERAN_TRASH | False | 2 |
| 2068 | Roaming Undead Mage Champion | 4 | champion | SILVERCOINS_LOOT_ROAMING | False | 2 |
| 2495 | Roaming Keeper Axe Thrower Miniboss | 5 | miniboss | SILVERCOINS_LOOT_ROAMING_MINIBOSS | False | 2 |
| 2642 | City Camp Caerleon Guard Melee Elite | 5 | factionwarfare | None | False | 2 |
| 2647 | City Camp Caerleon Guard Ranged | 6 | factionwarfare | None | False | 2 |
| 2651 | City Camp Caerleon Guard Ranged Elite | 6 | factionwarfare | None | False | 2 |
| 2667 | City Camp Caerleon Guard Female Veteran | 6 | factionwarfare | None | False | 2 |
| 2671 | City Camp Caerleon Guard Melee Elite Veteran | 6 | factionwarfare | None | False | 2 |
| 2726 | City Swamp Boss | 6 | boss | None | False | 2 |
| 576 | Critter Ore Mountain Red | 6 | critter | ORE_CRITTER | True | 2 |
| 840 | Roaming Mountain Chest | 6 | chest | SILVERCOINS_LOOT_CHEST_STANDARD | False | 2 |
| 1417 | Roaming Morgana Soldier Miniboss | 6 | miniboss | SILVERCOINS_LOOT_ROAMING_MINIBOSS | False | 1 |
| 1576 | Roaming Morgana Camp Cultist Veteran | 6 | standard | SILVERCOINS_LOOT_VETERAN | False | 1 |
| 1594 | Roaming Morgana Camp Cultist Veteran Champion | 6 | champion | SILVERCOINS_LOOT_VETERAN | False | 1 |
| 1648 | Roaming Morgana Camp Knight Veteran Champion | 6 | champion | SILVERCOINS_LOOT_VETERAN | False | 1 |
| 1937 | Roaming Undead Camp Soldier Champion | 4 | champion | SILVERCOINS_LOOT_ROAMING | False | 1 |
| 1988 | Roaming Undead Archer Champion | 4 | champion | SILVERCOINS_LOOT_ROAMING | False | 1 |
| 1997 | Roaming Undead Camp Archer Champion | 4 | champion | SILVERCOINS_LOOT_ROAMING | False | 1 |
| 2017 | Roaming Undead Camp Archer Miniboss | 4 | miniboss | SILVERCOINS_LOOT_ROAMING_MINIBOSS | False | 1 |
| 2088 | Roaming Undead Mage Miniboss | 4 | miniboss | SILVERCOINS_LOOT_ROAMING_MINIBOSS | False | 1 |
| 2483 | Roaming Keeper Berserk Boss | 5 | boss | SILVERCOINS_LOOT_ROAMING_BOSS | False | 1 |
| 2496 | Roaming Keeper Camp Axe Thrower Miniboss | 5 | miniboss | SILVERCOINS_LOOT_ROAMING_MINIBOSS | False | 1 |
| 2498 | Roaming Keeper Axe Thrower Boss | 5 | boss | SILVERCOINS_LOOT_ROAMING_BOSS | False | 1 |
| 2504 | Roaming Keeper Giant Miniboss | 5 | miniboss | SILVERCOINS_LOOT_ROAMING_MINIBOSS | False | 1 |
| 2516 | Keeper Druid Mini-Boss | 5 | miniboss | SILVERCOINS_LOOT_ROAMING_MINIBOSS | False | 1 |
| 2517 | Roaming Keeper Camp Druid Miniboss | 5 | miniboss | SILVERCOINS_LOOT_ROAMING_MINIBOSS | False | 1 |
| 2589 | Keeper Giant Veteran | 5 | static | SILVERCOINS_LOOT_VETERAN | False | 1 |
| 2654 | City Camp Caerleon Guard Support | 5 | factionwarfare | None | False | 1 |
| 2655 | City Camp Caerleon Guard Support | 6 | factionwarfare | None | False | 1 |
| 2659 | City Camp Caerleon Guard Support Elite | 6 | factionwarfare | None | False | 1 |
| 2662 | City Camp Caerleon Guard Male Veteran | 5 | factionwarfare | None | False | 1 |
| 2679 | City Camp Caerleon Guard Ranged Elite Veteran | 6 | factionwarfare | None | False | 1 |
| 2682 | City Camp Caerleon Guard Support Veteran | 5 | factionwarfare | None | False | 1 |
| 2683 | City Camp Caerleon Guard Support Veteran | 6 | factionwarfare | None | False | 1 |
| 2687 | City Camp Caerleon Guard Support Elite Veteran | 6 | factionwarfare | None | False | 1 |

## Enriched Event 123 mobile entity catalog
- Rows: 4373; mapped: 4373; unmapped: 0
- File: albion-mobile-entity-catalog-2026-11-03.csv

## Enriched Event 40 node catalog
- Rows: 725; mapped: 722; unresolved: 3
- File: albion-resource-node-catalog-2026-11-03.csv
- Resource display counts: Critter Ore Mountain Green=40, Critter Ore Mountain Red=3, Fiber=86, Hide=9, Ore=192, Ore Elemental=216, Rock=176
- Mapping status counts: PACKET_CONFIRMED_LIVING_RESOURCE=259, REFERENCE_STATIC_TYPE_NUMBER_PROVISIONAL=463, UNRESOLVED=3

## Mapping caveats and unresolved Type Number
- Type number 20 maps to HIDE. Its mobile TypeID 482 is Mountain Rabbit and is not harvestable, so the reference OpenRadar handler falls back to type number 20 rather than overriding it.
- Type number 29 is a separate living Ore Elemental resource mapping: TypeIDs 571/573/574, loot ORE_CRITTER, harvestable=true.
- Type numbers 7/14/20/27 map to ROCK/FIBER/HIDE/ORE through the public HarvestablesDatabase mapping; 28 remains unresolved.
- Type-number cross-validation against the independent per-capture ingest for AlbionRadar.zip/albion-1793682590317.pcap: 0 matched rows; 0 mismatches.
- Entity 2788, T5.3 at (-379,-24), remains a user-confirmed individual target; do not generalize its confirmation.

## Knowledge schema reconnaissance
The JSON report records exact fields and common values for type, family, tier, enchantment, classification, resource, name, labels and IDs. Field-name similarity alone is not proof.

## Weak filename hints
- Type number 14 — Albion2.zip / 6red ores ,each one mined.pcap (1 rows; filename hint only)
- Type number 14 — Albion2.zip / red ore ench 1 mined , mount dismount 3 times accidentally before mine.pcap (1 rows; filename hint only)
- Type number 14 — Albion2.zip / red,ore lvl2.pcap (1 rows; filename hint only)
- Type number 20 — Albions.zip / ore 4 3 red 2 blue .pcap (1 rows; filename hint only)
- Type number 27 — Albions.zip / 2mobs dismount and Mount .pcap (1 rows; filename hint only)
- Type number 29 — Albion2.zip / red ore ench 1 mined , mount dismount 3 times accidentally before mine.pcap (1 rows; filename hint only)
- Type number 29 — Albions.zip / 2mobs dismount and Mount .pcap (1 rows; filename hint only)
- Type number 29 — Albions.zip / ore 4 3 red 2 blue .pcap (1 rows; filename hint only)
- Type number 7 — Albion2.zip / blue ore ench2 , while mining a mob attacked me,, and I killed him, but the ore lost its enchantment after it.pcap (1 rows; filename hint only)
- Type number 7 — Albion2.zip / red ore ench 1 mined , mount dismount 3 times accidentally before mine.pcap (2 rows; filename hint only)
- Type number 7 — Albions.zip / 2mobs dismount and Mount .pcap (1 rows; filename hint only)

## Safety rules
- Runtime IDs are scoped by archive and PCAP/session.
- Event 39 static observations are not Event 40 harvestable nodes.
- Event 123 lifecycle rows are not all proven hostile mobs.
- Do not confuse Event 40 Type Number with Mobile Type ID; unknown numbers remain unresolved.
- Do not join by numeric ID alone; require same capture/session and time/coordinate evidence.
- Do not convert filename hints into confirmed labels.
- Unknown values are retained rather than dropped.
