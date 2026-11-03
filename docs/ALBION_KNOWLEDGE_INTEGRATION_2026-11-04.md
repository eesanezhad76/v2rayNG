# Albion Radar — Knowledge Integration Phase 1
Date: 2026-11-04

## Objective

Connect the already-completed research corpus to the Android runtime without repeating entity discovery and without mixing historical capture coordinates into the active player-centered map.

## Audit findings

The existing APK asset `V2rayNG/app/src/main/assets/albion-static-catalog.json` is a single-capture spatial snapshot, not the complete research database:

- 739 spatial entities in that snapshot
- 83 static resource records
- 656 mob records
- 56 markers
- 27 attack-target records
- coordinate semantics explicitly state capture-local coordinates and unknown zone identity

The research CSV catalogs are broader:

- 331 resource observations across six resource families
- 2,330 mobile entity observations
- 64 distinct observed mobile Type IDs

The original 331-row resource CSV and 2,330-row mobile entity CSV remain the provenance sources. No historical coordinates are copied into the new runtime knowledge index.

## Changes in this branch

### 1. New coordinate-free runtime knowledge asset

Added `V2rayNG/app/src/main/assets/albion-research-knowledge-index.json`.

It aggregates the full research CSVs into:

- resource-family evidence, observation counts, capture counts, tiers, and mapping-status distributions
- mobile Type ID evidence, observed names, tiers, categories, harvestability values, and capture counts
- source blob SHAs and an explicit rule prohibiting cross-capture coordinate projection without a verified zone/session transform

Verified generated totals:

| Dataset | Rows / IDs |
|---|---:|
| Resource observations | 331 |
| Resource families | 6 |
| Mobile entity observations | 2,330 |
| Distinct observed mobile Type IDs | 64 |

Family observation counts: 7=87, 14=47, 20=7, 27=88, 28=2, 29=100.

### 2. Runtime evidence enrichment

Added `AlbionResearchKnowledgeIndex.kt`.

The loader validates schema and cardinalities. It annotates live resource and mob observations with cross-capture evidence counts and provenance fields. It does not:

- overwrite packet facts
- assign historical coordinates to live entities
- promote Mountain Rabbit (Type ID 482) to a harvestable creature
- turn provisional resource mappings into packet-confirmed facts

`AlbionOverlayService` now loads the index once and applies its annotations to both the saved snapshot and current-session live observations. The overlay status reports the research knowledge totals separately from the spatial saved-map counts.

### 3. Tests

Added `AlbionResearchKnowledgeIndexTest.kt` for:

- schema and cardinality validation
- resource-family evidence annotation
- preserving coordinates
- Mountain Rabbit non-harvestable handling
- Ore Elemental harvestability evidence
- preserving packet-supplied names

### 4. CI

Added this integration branch to the APK build workflow and enabled workflow-file changes to trigger validation. The run must complete before this branch is considered build-verified.

## Important design boundary

The saved map remains the 83-node / 656-mob single-capture snapshot. The new 331-resource / 2,330-mobile knowledge index is evidence-only and coordinate-free. This is intentional: capture-local coordinates from different zones must not be merged into the live player-centered map until a reliable zone identity and coordinate transform are available.

## Not yet complete

- CI/APK build and unit-test result for this branch
- verify runtime index load on an installed device
- test current-session resource enrichment in a real capture
- confirm the live map's filtering and density behavior on the user's device
- later phase: validated zone identity and safe spatial catalog matching

## Branch

`albion-knowledge-integration-2026-11-04`

This is a first integration increment, not a claim that all historical nodes are already navigable on the live map.


## Phase 2 — Resolver integration (2026-11-04)

The coordinate-free research index is now consumed by the persistent live knowledge resolver:

- `AlbionLiveKnowledgeStore` loads the research index once at construction, passes it to `ResourcePatternLearner` for every resource inference, and annotates known mobile observations with the 2,330-row entity corpus.
- `AlbionResearchKnowledgeIndex.Snapshot.resourceTypeEvidence` exposes a family mapping only when the index has exactly one non-unknown resource type and one recognized mapping status.
- Provisional reference mappings remain `PROVISIONAL_MAPPING` at 0.90 confidence; the observation/capture counts are attached to the explanation, not treated as proof.
- The packet-corroborated living-resource family remains at the higher confidence tier.
- Family 28 stays unresolved. Family 20 remains Hide; Mobile Type ID 482 (Mountain Rabbit) does not override it or become harvestable.
- Known mobile Type IDs receive a separate `catalogued_mobile_entity` classification and provenance-based evidence grade; this does not relabel them as resources.
- If the research asset is unavailable, the resolver retains its prior conservative fallback behavior.

A regression test verifies that family evidence reaches the resolver and that provisional evidence is not promoted. CI must pass before this phase is build-verified.


## Phase 3 — Spatial intelligence and safe gathering targets (2026-11-04)

Implemented on the isolated integration branch:

- Added `AlbionSpatialIntelligence` for live-snapshot spatial safeguards.
- Deduplicates Event 39 candidate pins at the same half-unit coordinate; when a typed Event 40 resource exists at that point, the typed record supersedes the untyped candidate.
- All distinct typed runtime IDs at the same coordinate are preserved; coordinates alone are never used to merge two independently identified typed entities.
- Gathering navigation now accepts only typed `harvestable`/`resource_state` observations with a valid mobile Type ID and tier, or an explicit user-confirmed target. Untyped Event 39 candidates, conflicted records, depleted nodes, and departed entities cannot become the nearest gathering target.
- When the user explicitly enables unclassified resources, the mini-radar displays at most the 40 nearest untyped candidates, recalculated against the current player position. This limits visual clutter without deleting evidence from the live knowledge store.
- The radar continues to keep historical saved-catalog coordinates separate from active-session coordinates. No cross-zone coordinate transform is assumed.

Regression tests cover candidate/typed deduplication, preservation of distinct typed resources, navigation eligibility, conflict/depletion exclusion, and the 40-candidate display cap.

Remaining spatial limitations:
- The protocol pipeline still does not establish a verified zone identity/coordinate transform. A same-session zone transition cannot be safely inferred from coordinates alone; cross-zone historical projection remains disabled.
- This phase does not claim automatic pathfinding or a tested in-game route. It improves which live node may be selected and prevents untyped candidates from being presented as gathering destinations.
- CI and APK verification are still required before device testing.
