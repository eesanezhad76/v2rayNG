# Albion entity discovery — evidence-ranked reconnaissance

Profiles every Event 40 family, Event 123 mobile type, and merged Knowledge schema. Numeric IDs are not assigned names without independent evidence.

## Coverage
- Event 40 node records: **331**
- Event 123 lifecycle records: **2330**
- Knowledge entity records: **9522**
- Learned patterns: **70**; autonomous patterns: **296**; protocol evidence: **4599**
- Mobile TypeID dictionary coverage: **64/64** PCAP types mapped
- Dictionary source: Nouuu/Albion-Online-OpenRadar at 466c70b04e13ed0e8739ba23e6a950f7caf495e5

## Event 40 family profiles
| Family | Records | Mobile Type IDs | Tiers | Enchantment values | Interpretation |
|---:|---:|---|---|---|---|
| 14 | 47 | -1 (47) | 4 (33), 5 (14) | 1 (35), 2 (9), 3 (2), 1|2 (1) | unclassified |
| 20 | 7 | 482 (7) | 1 (7) | 0 (7) | event40_typeid_conflict_with_mobile_dictionary |
| 27 | 88 | -1 (88) | 4 (51), 5 (37) | 1 (78), 2 (8), 3 (2) | unknown_family_with_user_confirmed_exception |
| 28 | 2 | -1 (2) | 5 (2) | 1 (2) | unclassified |
| 29 | 100 | 574 (59), 573 (29), 571 (12) | 5 (59), 4 (29), 3 (12) | 0 (71), 1 (22), 2 (7) | confirmed_ore_family |
| 7 | 87 | -1 (87) | 4 (45), 5 (42) | 1 (71), 2 (15), 3 (1) | unclassified |

## Event 123 mobile TypeID crosswalk
| Type ID | Name | Tier | Category | Loot type | Harvestable | PCAP records |
|---:|---|---:|---|---|---|---:|
| 482 | Mountain Rabbit | 1 | harmless | HIDE | False | 469 |
| 2489 | Keeper Axe Thrower | 5 | roaming | SILVERCOINS_LOOT_ROAMING | False | 221 |
| 2474 | Keeper Berserk | 5 | roaming | SILVERCOINS_LOOT_ROAMING | False | 200 |
| 571 | Ore Elemental | 3 | critter | ORE_CRITTER | True | 167 |
| 573 | Ore Elemental | 4 | critter | ORE_CRITTER | True | 136 |
| 2486 | Keeper Axe Thrower Recruit | 5 | roaming | SILVERCOINS_LOOT_ROAMING | False | 111 |
| 120 | Mists Solo Wisp | 4 | rd_solo | None | False | 107 |
| 2471 | Keeper Berserk Recruit | 5 | roaming | SILVERCOINS_LOOT_ROAMING | False | 95 |
| 2510 | Keeper Druid | 5 | roaming | SILVERCOINS_LOOT_ROAMING | False | 92 |
| 574 | Ore Elemental | 5 | critter | ORE_CRITTER | True | 85 |
| 2712 | Mountain Guard | 5 | factionwarfare | None | False | 68 |
| 2477 | Keeper Berserk Champion | 5 | champion | SILVERCOINS_LOOT_ROAMING | False | 65 |
| 2492 | Keeper Axe Thrower Champion | 5 | champion | SILVERCOINS_LOOT_ROAMING | False | 51 |
| 2549 | Keeper Berserk Champion | 5 | champion | SILVERCOINS_LOOT_VETERAN | False | 51 |
| 2564 | Keeper Axe Thrower Champion | 5 | champion | SILVERCOINS_LOOT_VETERAN | False | 50 |
| 2501 | Keeper Giant Champion | 5 | champion | SILVERCOINS_LOOT_ROAMING | False | 28 |
| 2787 | Mountain Watchtower Crossbow | 5 | factionwarfare | None | False | 23 |
| 2512 | Keeper Druid | 5 | camp | SILVERCOINS_LOOT_VETERAN | False | 21 |
| 2476 | Keeper Berserk | 5 | camp | SILVERCOINS_LOOT_VETERAN | False | 20 |
| 2576 | Keeper Druid | 5 | static | SILVERCOINS_LOOT_VETERAN | False | 20 |
| 2491 | Keeper Axe Thrower | 5 | camp | SILVERCOINS_LOOT_VETERAN | False | 18 |
| 2513 | Keeper Druid Champion | 5 | champion | SILVERCOINS_LOOT_ROAMING | False | 17 |
| 2473 | Keeper Berserk Recruit | 5 | camp | SILVERCOINS_LOOT_VETERAN_TRASH | False | 15 |
| 2535 | Keeper Bear | 5 | trash | SILVERCOINS_LOOT_VETERAN_TRASH | False | 15 |
| 2563 | Keeper Axe Thrower | 5 | static | SILVERCOINS_LOOT_VETERAN | False | 14 |
| 2577 | Keeper Druid Champion | 5 | champion | SILVERCOINS_LOOT_VETERAN | False | 14 |
| 2488 | Keeper Axe Thrower Recruit | 5 | camp | SILVERCOINS_LOOT_VETERAN_TRASH | False | 12 |
| 2590 | Keeper Giant Champion | 5 | champion | SILVERCOINS_LOOT_VETERAN | False | 12 |
| 2802 | Mountain Watchtower Staff | 5 | factionwarfare | None | False | 11 |
| 2548 | Keeper Berserk | 5 | static | SILVERCOINS_LOOT_VETERAN | False | 10 |
| 2539 | Keeper Earthchild Champion | 5 | champion | SILVERCOINS_LOOT_VETERAN | False | 9 |
| 2494 | Keeper Axe Thrower Champion | 5 | champion | SILVERCOINS_LOOT_VETERAN | False | 8 |
| 2715 | Mountain Boss | 5 | boss | None | False | 8 |
| 2490 | Keeper Axe Thrower | 5 | camp | SILVERCOINS_LOOT_ROAMING | False | 7 |
| 121 | Mists Solo Wisp | 4 | rd_solo | None | False | 6 |
| 2511 | Keeper Druid | 5 | camp | SILVERCOINS_LOOT_ROAMING | False | 6 |
| 2772 | Mountain Watchtower Sword | 5 | factionwarfare | None | False | 6 |
| 2503 | Roaming Keeper Camp Giant Veteran Champion | 5 | champion | SILVERCOINS_LOOT_VETERAN | False | 5 |
| 2792 | City Fortress Mountain Crossbow | 6 | factionwarfare | None | False | 5 |
| 2479 | Keeper Berserk Champion | 5 | champion | SILVERCOINS_LOOT_VETERAN | False | 4 |
| 2502 | Keeper Giant Champion | 5 | champion | SILVERCOINS_LOOT_ROAMING | False | 4 |
| 2475 | Keeper Berserk | 5 | camp | SILVERCOINS_LOOT_ROAMING | False | 3 |
| 2480 | Keeper Berserk Mini-Boss | 5 | miniboss | SILVERCOINS_LOOT_ROAMING_MINIBOSS | False | 3 |
| 2516 | Keeper Druid Mini-Boss | 5 | miniboss | SILVERCOINS_LOOT_ROAMING_MINIBOSS | False | 3 |
| 2634 | City Camp Caerleon Guard Male | 5 | factionwarfare | None | False | 3 |
| 2646 | City Camp Caerleon Guard Ranged | 5 | factionwarfare | None | False | 3 |
| 2777 | City Fortress Mountain Sword | 6 | factionwarfare | None | False | 3 |
| 2807 | City Fortress Mountain Staff | 6 | factionwarfare | None | False | 3 |
| 2478 | Keeper Berserk Champion | 5 | champion | SILVERCOINS_LOOT_ROAMING | False | 2 |
| 2493 | Keeper Axe Thrower Champion | 5 | champion | SILVERCOINS_LOOT_ROAMING | False | 2 |
| 2514 | Roaming Keeper Camp Druid Champion | 5 | champion | SILVERCOINS_LOOT_ROAMING | False | 2 |
| 2515 | Roaming Keeper Camp Druid Veteran Champion | 5 | champion | SILVERCOINS_LOOT_VETERAN | False | 2 |
| 2575 | t5 mob keeper druid adept veteran | 5 | static | SILVERCOINS_LOOT_VETERAN_TRASH | False | 2 |
| 2642 | City Camp Caerleon Guard Melee Elite | 5 | factionwarfare | None | False | 2 |
| 830 | Mountain Chest | 4 | chest | SILVERCOINS_LOOT_CHEST_STANDARD | False | 2 |
| 2495 | Roaming Keeper Axe Thrower Miniboss | 5 | miniboss | SILVERCOINS_LOOT_ROAMING_MINIBOSS | False | 1 |
| 2504 | Roaming Keeper Giant Miniboss | 5 | miniboss | SILVERCOINS_LOOT_ROAMING_MINIBOSS | False | 1 |
| 2589 | Keeper Giant Veteran | 5 | static | SILVERCOINS_LOOT_VETERAN | False | 1 |
| 2592 | Keeper Giant Mini-Boss | 5 | miniboss | None | False | 1 |
| 2654 | City Camp Caerleon Guard Support | 5 | factionwarfare | None | False | 1 |
| 2662 | City Camp Caerleon Guard Male Veteran | 5 | factionwarfare | None | False | 1 |
| 2682 | City Camp Caerleon Guard Support Veteran | 5 | factionwarfare | None | False | 1 |
| 835 | Roaming Mountain Chest | 5 | chest | SILVERCOINS_LOOT_CHEST_STANDARD | False | 1 |
| 943 | Avalonian Treasure Drone | 5 | treasuredrones | None | False | 1 |

## Key conflict requiring raw-event re-decode
- Family 20 has 7 Event 40 rows with TypeID 482, but the game TypeID dictionary maps 482 to T1 Mountain Rabbit, with is_harvestable=false. This is a protocol-field/semantic conflict, not a confirmed resource family.
- Family 29 is consistent: TypeIDs 571/573/574 resolve to Ore Elemental T3/T4/T5, loot ORE_CRITTER, harvestable=true.
- Families 7, 14, 27 and 28 have no decoded TypeID in Event 40; their names remain unresolved.
- Family 27 has only one user-confirmed exception: entity 2788, T5.3 at (-379,-24).

## Knowledge schema reconnaissance
The JSON report records exact fields and common values for type, family, tier, enchantment, classification, resource, name, labels and IDs. Field-name similarity alone is not proof.

## Weak filename hints
- Family 14 — Albion2.zip / 6red ores ,each one mined.pcap (1 rows; filename hint only)
- Family 14 — Albion2.zip / red ore ench 1 mined , mount dismount 3 times accidentally before mine.pcap (1 rows; filename hint only)
- Family 14 — Albion2.zip / red,ore lvl2.pcap (1 rows; filename hint only)
- Family 20 — Albions.zip / ore 4 3 red 2 blue .pcap (1 rows; filename hint only)
- Family 27 — Albions.zip / 2mobs dismount and Mount .pcap (1 rows; filename hint only)
- Family 29 — Albion2.zip / red ore ench 1 mined , mount dismount 3 times accidentally before mine.pcap (1 rows; filename hint only)
- Family 29 — Albions.zip / 2mobs dismount and Mount .pcap (1 rows; filename hint only)
- Family 29 — Albions.zip / ore 4 3 red 2 blue .pcap (1 rows; filename hint only)
- Family 7 — Albion2.zip / blue ore ench2 , while mining a mob attacked me,, and I killed him, but the ore lost its enchantment after it.pcap (1 rows; filename hint only)
- Family 7 — Albion2.zip / red ore ench 1 mined , mount dismount 3 times accidentally before mine.pcap (2 rows; filename hint only)
- Family 7 — Albions.zip / 2mobs dismount and Mount .pcap (1 rows; filename hint only)

## Safety rules
- Runtime IDs are scoped by archive and PCAP/session.
- Event 39 static observations are not Event 40 harvestable nodes.
- Event 123 lifecycle rows are not all proven hostile mobs.
- Do not infer a family name from numeric family ID alone.
- Do not join by numeric ID alone; require same capture/session and time/coordinate evidence.
- Do not convert filename hints into confirmed labels.
- Unknown values are retained rather than dropped.
