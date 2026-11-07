# Passive radar and region-visit isolation — 2026-11-07

Branch: `albion-pattern-mining`
Implementation commits: region identity isolation, previous-visit display, passive target ranking.

## Files changed

- `AlbionEntityEventMapper.kt`: ChangeCluster candidate arrays and numeric candidates are no longer accepted as confirmed region IDs. Join response map ID and stable MAP_/Mists tokens remain accepted.
- `AlbionLiveKnowledgeStore.kt`: fresh visit identity on each confirmed region transition; visit-scoped record keys, live snapshots, mob/resource correlation, and region scans; retain the latest prior visit per region for explicit retrieval.
- `AlbionCaptureWorker.kt`, `CoreServiceManager.kt`: expose previous-visit snapshot on demand.
- `AlbionMiniRadarView.kt`: rank the three nearest actionable resources and harvestable mobs in one linear pass; previous-visit markers are grey, outlined, and excluded from navigation.
- `AlbionOverlayService.kt`: show the decoded region identifier and add PREV/LIVE toggle. Archived snapshot generation runs only after a user tap, not in the 200 ms refresh loop.
- Tests: reject ambiguous ChangeCluster candidate arrays; verify that returning to a region starts a clean live visit while preserving its previous visit for manual review.

## Performance and data-integrity

No new network calls, active probes, packet polling, or background scan loops. The top-three ranking is passive O(n) work on the already-filtered snapshot. Archived nodes are explicitly stale hints, not live availability. Numeric region IDs display as `ZONE <id>`; MAP_/Mists IDs are prettified. This is not yet a verified numeric-ID-to-human-zone-name dictionary.


## Follow-up validation

- Updated projection unit tests to assert the existing capture-calibrated map rotation (49.819°) and independent movement transform (45°). The previous cardinal-axis expectations predated those calibrated transforms and caused the pre-change baseline CI run to fail.


## Region-name lookup

- Added `albion-region-name-index.json`, a compact index-to-name table generated from the public `ao-data/ao-bin-dumps/formatted/world.json` game-data snapshot (tree `0483dd97f1581dfaf02eed42f25f5ea2a3cb75e5`). Only numeric map indexes and their display names are included; no world coordinates or inferred resource locations are imported.
- Region names are loaded once when the overlay initializes. Unknown MAP_/Mists IDs retain their readable identifier fallback.
