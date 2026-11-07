# Albion radar risk remediation — 2026-11-07

## Baseline and preservation
- Baseline: `c6ae901df3675c2689e4bd0a96a0b455c11ca6a6` on `albion-pattern-mining`.
- Work is isolated on `albion-radar-risk-remediation-2026-11-07`; no existing branch was reset, rebased, or force-updated.
- No capture, catalog, dictionary, PCAP, JSONL, database, or entity-record data files were modified.
- No entity IDs, runtime identity keys, or source observations were rewritten or regenerated.

## Changes
- `AlbionMiniRadarView.kt`
  - MARK now supports eligible, region-matched Troll targets and harvestable mobs as well as navigable resource nodes.
  - Superseded/dead/unclassified mobs are not promoted to marks; live mob observations resolve nearby temporary marks.
  - Unknown tier metadata no longer hides otherwise valid targets when a tier subset is selected; known tiers remain exact.
  - Mobs are constrained to the active region for drawing/navigation.
  - Invalid JSON retains the last good frame but marks it stale; stale frames are dimmed and cannot drive MARK or navigation. Reappearance of the last-good payload clears stale state.
  - Opt-in Other Animals/Other Mobs categories are now actually rendered.
- `AlbionSpatialIntelligence.kt`
  - Added shared tier-filter and MARK-eligibility rules.
  - Dead mob states are excluded from navigation.
- `AlbionOverlayService.kt`
  - Touchable bounds are recalculated from the actual side-panel and drag-handle descendant geometry, including the current scale transform.
  - Invalid live snapshots are reported explicitly as stale while retaining the last good frame.
- `AlbionRadarSnapshotParserTest.kt`
  - Added regression tests for unknown/known tier filtering, eligible MARK targets, superseded/dead mobs, and dead-mob navigation exclusion.

## Verification
- Source files were fetched again from the repair branch after writes.
- Unit tests and Android build have not yet been confirmed; CI/device verification is required before calling this game-ready.
