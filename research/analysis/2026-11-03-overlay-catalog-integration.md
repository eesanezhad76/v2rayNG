# Albion radar catalog integration — 2026-11-03

## Persisted capture catalog

- Dataset: `albion-1793682590317` from `AlbionRadar.zip`
- Event 40 node records: **83**
- Manual marker lifecycle records: **56**, including **11 manually tagged Troll cycles**
- Attack-target tracks for this PCAP: **27**
- Distinct mobile type IDs resolved from the game mob database: **48**
- The catalog stores node positions, Event 46 state timelines, Mob type/name/tier, attack request timing, health observations, leave/departure evidence and marker correlations.
- Machine-readable files: `research/database/albion-1793682590317-catalog.json`, `research/analysis/albion-1793682590317-overlay-catalog.json`, and Android asset `albion-static-catalog.json`.
- Type dictionary: `research/database/albion-mobile-type-dictionary.json` and `research/analysis/albion-mobile-type-dictionary.json`.

## User-confirmed T5.3 record

Entity **2788**, family 27, T5.3, coordinates **(-379, -24)**, first observed at `1793684963241`.

The user confirmed the first T5.3 target. Its state evidence changes 4 → 3 → 2 → 1 → 0 by `1793684994752`, temporally overlapping the two T5.3 Start/End marker pairs. It is marked `user_confirmed_target=true`.

Entity **2610** (family 14, T5.3, 117.5, 349.5) remains separate and unconfirmed; it is not merged with 2788.

## Mob identity and marker correlation

The PCAP audit extracts outbound Photon operation 23 attack-target requests. These requests identify the exact runtime target entity ID selected by the player. Event 123 / Event 3 provide mobile type and positions; Event 6/7 provide health changes; Leave events provide disappearance evidence.

For this capture, the current timestamp-window matcher links **21 of 24 Mob/Troll marker cycles** to an attack-target track:
- **14 HIGH**, **4 MEDIUM**, **3 LOW** confidence links
- **3 cycles** have no attack-target candidate in the allowed window
- A target ID is exact from the outbound request; the association between a manual Start/Killed cycle and that target remains evidence-ranked, not packet-proven. Duplicate nearby Start markers can point to the same target.

### Important type-label finding

The captured mobile type IDs were resolved with the OpenRadar mob database mapping (server TypeID = mob-array index + 16; `MobsDatabase.OFFSET=16`). This produces actual type names and tiers instead of opaque numeric IDs.

Several cycles manually tagged **Troll** correlate to type IDs **571, 573 and 574**, which the current type dictionary resolves as **Ore Elemental** (T3, T4 and T5 respectively). The manual marker category is preserved as `marker_activity=Troll`; the decoded game type is preserved separately as `name=Ore Elemental`. The radar labels these with a `T!` prefix to make the discrepancy visible rather than silently relabeling the user's marker.

Other captured targets resolve to Keeper mobs, including Keeper Berserk, Keeper Berserk Champion, Keeper Axe Thrower, Keeper Axe Thrower Champion, Keeper Giant Champion and Keeper Druid Champion. Where Event 123 supplies a name, that name takes precedence over the type dictionary.

## Overlay implementation

- Activity controls and radar are separate Android overlay windows.
- Both windows have independent drag handles; their positions are saved and restored across service restarts.
- Controls are compact, with a single-line shortened title, horizontal scrollable activity/tier/enchant chips, and compact Start/End buttons.
- Radar has a collapsed-by-default multi-select filter drawer: normal/enchanted nodes, player-targeted mobs, Ore Elementals, other visible mobs, Troll-tagged and enchanted Troll-tagged targets, T2–T8, E1–E3.
- The radar shows a full saved-catalog overview before live player position is available. Once player coordinates arrive, it becomes player-centered and projects off-range targets to the rim with bearing and distance cues.
- Historical catalog coordinates are capture-scoped. Current-zone identity is not decoded yet, so the live map explicitly warns that saved pins have not been zone-verified.

## Mob health and lifecycle retention

The live knowledge store now preserves Event 123 mob identity/name/type/position when a Leave event arrives, instead of replacing the semantic entity kind with `leave`. Event 6/7 health observations attach to the matching runtime entity record, including health value/delta and source entity ID when present. Departed/killed candidates remain visible in the saved radar catalog.

## Remaining gates

1. Resolve current zone identity before treating historical pins as current-zone navigation targets.
2. Validate the 3 uncorrelated marker cycles and duplicate Start-to-same-target cases against another capture.
3. Confirm the decoded display names against in-game UI for the manually tagged Troll cases.
4. Complete CI APK build, verify artifacts/SHA-256, then test both movable overlay windows and filters on Poco F5.
