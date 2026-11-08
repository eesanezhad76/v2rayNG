# AlbionRadar Golden Checkpoint — 2026-11-08

## Status
User-tested working state reported on 2026-11-08. Preserve this behavior as the golden baseline. This checkpoint is documentation/backup only; do not trigger or start any APK build.

## Working behavior confirmed by user
- Radar latency is excellent / materially reduced.
- Player position is accurate and stable.
- Target point detection is excellent.
- Movement direction/orientation is correct.
- Remaining resource-node count is displayed correctly; observed T5.4 node showed exactly 4 remaining nodes.
- Resource discovery latency is reduced.
- Do not regress any of the above.

## Preservation invariants
1. Do not alter the verified player-position coordinate pipeline, movement-direction mapping, heading/rotation calibration, or radar transform.
2. Keep player-position frames independent from the full resource catalog.
3. Preserve current resource remaining-count/depletion semantics.
4. No new APK build, workflow dispatch, rebuild, or CI-triggering source change until the user explicitly authorizes the consolidated fixes.
5. Treat all fixes below as deferred requirements; collect/analyze only for now.

## Deferred fixes — resource recognition
### A. Living Ore / Troll / Ore Elemental recognition (root cause)
- Current behavior: living Ore resources are not recognized; Troll/giant-like entities are not recognized at any tier or enchantment.
- Troll and Ore Elemental filters appear nonfunctional because the app does not understand the actual living Ore entity/resource lifecycle.
- Investigate from protocol/data model root cause, not a UI-only filter patch.
- Build a verified mapping across all tiers and enchantments, including entity spawn/transform/death/drop/resource transitions.
- Distinguish actual living Ore entities from ordinary mobs and static Ore nodes using capture-backed evidence.
- Add regression fixtures/tests for each verified lifecycle state before implementation.

## Deferred fixes — map markers and visual hierarchy
### B. Empty Ore nodes
- If Ore is enabled in filters, show depleted/empty Ore nodes too.
- Empty/depleted Ore: small triangle marker, colored by enchantment, with tier label T adjacent.
- Available/non-empty Ore: circular marker, same enchantment color.
- Marker must remain visible at the node's actual map location and must not disappear merely because remaining count is zero.

### C. Marker rendering failure
- User reports that marker rendering currently does not work: no small enchantment-colored triangular marker and no adjacent tier text remains at the point.
- Trace the complete path: detection -> model/state -> filter -> map projection -> draw/render/clipping/z-order.
- Add tests for triangle/circle shape, enchantment color, tier label, and depleted/available transitions.

### D. Remove clutter
- Remove distance text from map markers; it crowds the map and makes tier labels/enchantment colors hard to read.
- Remove blue proximity/target rings around nearby targets; overlapping rings obscure node enchantment colors.
- Keep target/resource points visually unobstructed.

### E. Improve resource marker visibility
- Increase contrast/clarity of enchantment colors (green, blue, purple, gold).
- Ensure markers remain distinguishable when overlapping or close together.
- Do not cover marker colors with rings, halos, labels, or other overlays.
- Keep tier text legible without distance strings.

## Deferred fixes — detection range / coverage
### F. Expand radar discovery range
- Current effective radar range feels roughly equivalent to two phone-screen widths; targets outside that area may be absent.
- User reports green nodes sometimes appear from farther away while blue/purple nodes can require moving closer; determine whether this is real tier/enchantment bias or capture/protocol/visibility variance.
- Audit range by tier, enchantment, entity type, and source event; do not assume the colors themselves cause range differences.
- Compare in-game map visibility and V-Tree/overlay map resource coverage. User sees many gray resources and some T4 blue nodes in the in-game/V-Tree map that are absent from the radar map.
- Determine whether the V-Tree map uses a larger search radius, cached/world-map data, different discovery source, or different filtering/coordinate projection.
- Establish measured detection radius and coverage tests before increasing it. Avoid simply increasing expensive polling if it harms latency/CPU.
- Goal: wider useful detection without regressing low latency, player tracking, or direction.

## Required investigation order
1. Preserve and characterize the exact golden baseline; identify the exact tested APK/build artifact and source commit from GitHub Actions before any future implementation.
2. Root-cause living Ore/Troll/Ore Elemental entity lifecycle using capture-backed protocol evidence and all available internal/external research.
3. Trace/fix marker pipeline and add depleted Ore triangle vs available Ore circle semantics.
4. Remove distance text and blue proximity rings; improve enchantment color contrast and tier labels.
5. Compare radar detection range/data sources against V-Tree/in-game map; quantify tier/enchantment differences and optimize range.
6. Implement all approved fixes together in one consolidated build only after explicit user authorization.
7. Run regression tests proving the golden baseline invariants still hold before producing any APK.

## Build provenance — pending exact confirmation
Prior conversation records identify GitHub Actions Run #54 (run ID 42386619737), source commit 24df5b7a7c9e3de8633338170d8c8b121310809c, as a successful build on 2026-11-07. A later working branch was recorded as movement-direction-root-cause-2026-11-07 at short commit 9b223cd, but its APK was not verified in that record. Current user-tested APK/build number must be matched to the exact installed artifact before treating either identifier as the definitive binary provenance.

## User instruction
Do not start a new build now. Preserve this checkpoint and only collect, compare, and document the fixes until the user authorizes one consolidated implementation/build.