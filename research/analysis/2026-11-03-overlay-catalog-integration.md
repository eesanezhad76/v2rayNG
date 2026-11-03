# Albion radar catalog integration — 2026-11-03

## Persisted capture catalog

- Dataset: `albion-1793682590317` from `AlbionRadar.zip`
- Event 40 node records: **83**
- Manual marker lifecycle records: **56**
- Troll lifecycle records: **11**
- All 83 node records retain runtime entity ID, protocol family, tier, enchantment, X/Y, first/last observation timestamps, observation count, size history, and Event 46 state timeline.
- The complete machine-readable catalog is stored in `research/database/albion-1793682590317-catalog.json` and bundled into Android assets as `albion-static-catalog.json`.

## User-confirmed T5.3 record

Entity **2788**, family 27, T5.3, coordinates **(-379, -24)**, first observed at `1793684963241`.

The user's confirmation applies to the first T5.3 target. The node's state evidence changes 4 → 3 → 2 → 1 → 0 by `1793684994752`, temporally overlapping the two T5.3 Start/End marker pairs. It is marked `user_confirmed_target=true` in the catalog.

Entity **2610** (family 14, T5.3, 117.5, 349.5) remains a separate, unconfirmed candidate and is not merged with 2788.

## Mob and Troll evidence

All 56 marker lifecycle records are retained, including 11 Troll records and all generic Mob records. Manual marker rows do not contain runtime entity IDs or coordinates. Therefore they are stored as activity evidence but are not falsely plotted as exact Troll pins until correlated with Event 123 mob observations and the player's Event 22 position timeline.

The live knowledge store now retains Event 123 mob identity/name/type/position when a Leave event arrives, instead of overwriting the semantic entity kind with `leave`. Event 6/7 health observations are attached to the matching runtime entity record (health value/delta/source ID). This preserves health evidence and allows the radar to keep departed/killed mob pins visible.

## Overlay implementation in this revision

- Activity controls and radar are separate Android overlay windows.
- Both windows have independent drag handles and can be moved independently.
- Controls are narrower and use single-line truncated title, smaller but readable chips, and compact Start/End buttons.
- Radar is compact; the FILTER drawer exposes multi-select node, enchanted node, mob, Troll, enchanted Troll, T2–T8, and E1–E3 filters.
- Off-range nodes are projected to the radar rim with distance labels; they remain discoverable outside the immediate view.
- The map uses observed game coordinates only; no terrain, roads, or unverified zone alignment is fabricated.

## Important limitations

1. The 83-node catalog is capture-scoped. Zone identity is not decoded yet; do not assume historical pins belong to the player's current zone until zone matching is implemented.
2. The first T5.3 is user-confirmed and temporal/state-correlated, but the PCAP marker itself has no runtime entity ID. Its map point is the Event 40 entity 2788 position.
3. Troll marker cycles are preserved, but exact per-marker Troll coordinates still require timestamp-to-Event123/health/player-position correlation. Live Event123 mobs with coordinates are displayed independently.
4. APK build and device testing are separate gates; this source change is not declared installed or field-verified until CI and Poco F5 testing pass.
