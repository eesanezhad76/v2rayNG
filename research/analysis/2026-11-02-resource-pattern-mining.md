# Resource pattern mining — 2026-11-02

## Scope and evidence

This is a first-pass, evidence-labelled pattern analysis. It uses:
- The 55 file-scoped Event 40 records in `research/analysis/2026-11-02-pcap-harvestable-nodes.csv` from the three inbox ZIPs (19 PCAPs).
- The independent 51-node Event 40 inventory in `research/analysis/2026-11-02-capture-inventory.md` from `Albionsskkakaks111.zip`.
- The manually labelled mining activities and exact entity/state joins documented in `docs/ALBION_CAPTURE_DEEP_ANALYSIS_2026-11-02.md`.

Runtime entity IDs are scoped to archive + PCAP. They are never joined across captures by numeric ID. Event 39 static-resource coordinates are not treated as typed Event 40 nodes unless an exact identity link exists.

## Findings

### 1. Ore family and mobile-type mapping

Family `type_number=29` is consistently the Ore family in the Ore-labelled captures. The mobile-type mapping is independently corroborated by both datasets:

| Family | Tier | Enchantment raw observed | Mobile type | Evidence |
|---:|---:|---:|---:|---|
| 29 | T3 | 0 | 571 | three-inbox data and independent capture |
| 29 | T4 | 0, 1, 2 | 573 | three-inbox data; E1/E2 examples include exact Event 40↔46 joins |
| 29 | T5 | 0, 1, 2 | 574 | three-inbox data; independent capture directly observes T5 Normal and E1 |

The direct invariant supported by the captures is **family 29 + T3/T4/T5 → mobile type 571/573/574**, respectively. Do not extrapolate the sequence to T6+ or other families.

### 2. Enchantment field

Observed raw values are 0, 1, and 2. The working interpretation is:
- 0 = Normal
- 1 = E1
- 2 = E2

This interpretation is consistent with manually named PCAP scenarios (Blue Ore E1/E2, Red Ore E1/E2) and overlay-labelled mining sessions (Normal, E1, E2). It is a strong working mapping, but should remain provenance-tagged until each raw value is confirmed against a same-entity marker or a controlled single-node capture. Do not infer a node's enchantment from colour words in a filename alone.

### 3. Distribution in the 55 three-inbox Event 40 records

| Family | Tier | Raw enchant | Count |
|---:|---:|---:|---:|
| 29 | T3 | 0 | 1 |
| 29 | T4 | 0 | 4 |
| 29 | T4 | 1 | 3 |
| 29 | T4 | 2 | 2 |
| 29 | T5 | 0 | 0 |
| 29 | T5 | 1 | 7 |
| 29 | T5 | 2 | 0 |
| 7 | T4 | 1 | 3 |
| 7 | T5 | 1 | 8 |
| 14 | T4 | 1 | 3 |
| 14 | T4 | 2 | 1 |
| 20 | T1 | 0 | 5 |
| 27 | T4 | 1 | 2 |
| 27 | T4 | 2 | 0 |
| 27 | T5 | 1 | 4 |
| 27 | T5 | 2 | 1 |
| 28 | T5 | 1 | 2 |

Counts above are file-scoped Event 40 rows from the three ZIPs, not globally unique world nodes. Rows with `mobile_type_id=-1` remain unresolved, not missing by assumption.

### 4. Independent capture corroboration

The separate 51-node capture inventory reports:
- Family 29: T3 Normal (3), T4 Normal (6), T5 Normal (16), T5 E1 (1).
- Family 27: T4 E1 (3), T4 E2 (1), T5 E1 (7), T5 E2 (1).
- Family 14: T4 E1 (4), T4 E2 (1), T5 E1 (2).
- Family 7: T4 E1 (1), T5 E1 (5), T5 E2 (1).

This is useful independent support for the family-29 Ore interpretation and for the existence of E1/E2 values on multiple families. It does **not** yet establish the semantic names of families 7, 14, 27, or 28.

### 5. State sequences are not a reliable type classifier

Exact Event 40↔46 joins include:
- Family 29 / T4 / raw E1 / mobile 573: two nodes with 2→1→0.
- Family 29 / T4 / raw E2 / mobile 573: one node with 2→1→0.
- Family 29 / T5 / raw E1 / mobile 574: two nodes with 4→3→2→1→0.

These examples suggest different initial resource sizes in these samples, but sample count is too small to infer Tier or Enchantment from a state sequence. State should be used for depletion tracking and as a secondary feature only.

## What can be predicted now

1. If family 29 and Tier are directly decoded but mobile type is missing, predict 571/573/574 for T3/T4/T5, respectively. Label it **predicted**, not packet-confirmed.
2. If raw enchant is directly decoded, map 0/1/2 to Normal/E1/E2 as a **provisional semantic decode** with evidence provenance.
3. If family is 7, 14, 27, or 28, do not assign a resource name yet. Keep the family numeric and semantic class unknown.
4. If Event 40 lacks enchant raw value, do not fill it from nearby nodes, coordinates, state count, or a filename. Return Unknown unless an exact same-entity marker or another authoritative field supplies it.
5. Do not predict resource identity from Event 39 coordinates alone. Event 39 observations are currently an untyped static-resource stream.


## Overlay marker correlation status

The offline analyzer now preserves marker fields (`activity`, `event`, `tier`, and `enchantment`) and exports:
- `2026-11-02-overlay-manual-markers.csv`
- `2026-11-02-overlay-family-correlation.csv`
- `2026-11-02-overlay-family-candidate-summary.csv`

The current three ZIPs in `research/inbox` contain **zero manual checkpoint packets**, so these correlation exports currently contain headers only. The separate `Albionsskkakaks111.zip` capture described in `2026-11-02-capture-inventory.md` is not present in the current inbox; only its aggregate Markdown inventory is available. Consequently, family 7/14/27 cannot yet be assigned semantic names from marker evidence. This is a missing-source limitation, not a negative mapping result.

FIFO lifecycle pairing is now implemented in `research/tools/ingest_capture.py`. It uses per-activity queues to pair Found → Started → Completed → Depleted in order, preserves unmatched/orphan events and incomplete cycles, and emits `marker_lifecycles`, `unmatched_marker_events`, and a marker-lifecycle CSV. Synthetic tests cover multiple queued targets, orphan starts, and mob Found→Killed pairs. The current inbox still has no usable manual checkpoint stream, so real marker-to-node association remains unverified. Candidate matches remain explicitly non-identity evidence unless a runtime entity ID is shared.

## Why a trained classifier is not yet validated

The three-inbox file has only 55 labelled Event 40 records and the second capture is currently represented in the repository by an aggregate inventory rather than a row-level node CSV. A random row split would leak nearly identical capture context and overstate accuracy. A meaningful evaluation must hold out an entire PCAP or archive, and must measure family, tier, enchant, and mobile-type separately.

## Next implementation steps

1. Export row-level Event 40 nodes and marker records from the independent capture, retaining archive, PCAP, timestamp, runtime ID, and exact marker type.
2. Repair marker association with FIFO queues per activity; do not overwrite multiple pending Found targets.
3. Match markers to Event 40 entities only through exact IDs or an explicitly justified time/space window with ambiguity retained.
4. Add a provenance-aware resource catalog: field, value, interpretation, evidence refs, capture count, confidence, and status (confirmed/provisional/unknown).
5. Add deterministic rule inference for family-29 mobile types and raw enchant mapping.
6. Add a baseline classifier only after row-level labels exist; evaluate leave-one-PCAP-out and leave-one-archive-out. Report per-field accuracy and abstention rate.
7. Keep predictions separate from packet facts in the overlay and database. Never display a prediction as confirmed.

## Current confidence summary

| Inference | Status |
|---|---|
| Family 29 is Ore in the labelled Ore captures | Strong / independently corroborated |
| Family 29 T3→571, T4→573, T5→574 | Strong within observed tiers |
| Raw enchant 0/1/2 → Normal/E1/E2 | Provisional semantic mapping, repeatedly consistent |
| Family 7/14/27/28 semantic names | Unknown |
| Inferring enchant from location, state count, or neighbouring nodes | Unsupported |
| General ML accuracy on unseen captures | Not measured |
