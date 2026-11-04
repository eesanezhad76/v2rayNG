# AlbionRadar8 manual-marker near-repeat audit

Source of truth: preserved `research/analysis/2026-11-02-overlay-manual-markers.csv`, filtered to `AlbionRadar8.zip`. All 101 marker rows remain in that source. This report does not delete, merge, or rewrite any source event.

Rule: same activity + same event repeated within 10,000 ms is flagged as a **possible** double tap only. It is not proof of an accidental tap; keep both events and review against the gameplay sequence.

| Source row | Activity | Event | First timestamp (ms) | Repeat timestamp (ms) | Gap (ms) | Tier / enchant |
|---:|---|---|---:|---:|---:|---|
| 22 → 24 | Ore | gathering_completed | 1793770006404 | 1793770014917 | 8513 | T4 / E1 |
| 55 → 57 | Ore | target_found | 1793770704710 | 1793770710668 | 5958 | T4 / E2 |
| 56 → 58 | Ore | gathering_completed | 1793770707398 | 1793770712915 | 5517 | T4 / E2 |
| 59 → 60 | Ore | target_found | 1793770777801 | 1793770783111 | 5310 | T4 / E2 |
| 63 → 64 | Ore | target_found | 1793771098339 | 1793771105571 | 7232 | T5 / E1 |
| 67 → 68 | Ore | gathering_completed | 1793771319942 | 1793771320625 | 683 | T4 / E2 |
| 69 → 71 | Ore | target_found | 1793771395065 | 1793771403169 | 8104 | T4 / E1 |
| 70 → 72 | Ore | gathering_completed | 1793771399713 | 1793771406562 | 6849 | T4 / E1 |
| 85 → 86 | Troll | target_found | 1793772028951 | 1793772030445 | 1494 | T5 / E1 |
| 95 → 96 | Troll | mob_killed | 1793772513812 | 1793772515233 | 1421 | T5 / E1 |

## Unmatched STOP event
- Source row 4 is an Ore `gathering_completed` at `1793769694227` with no new Ore `target_found` between it and the previous completed cycle. It is retained as one unmatched STOP marker, not assigned to a second mining cycle.
- Reconciliation: 43 Ore START markers, 42 Ore STOP markers, 41 paired completed cycles, 2 unmatched START markers, and 1 unmatched STOP marker. This matches the 43-row Ore lifecycle table (41 complete + 2 started-not-completed) while retaining the extra STOP separately.

## Interpretation
- `target_found` is emitted by the overlay's **START** button for Ore in this code version; `gathering_completed` is emitted by **END**.
- `mob_killed` is emitted by the mob **KILLED** button.
- A near repeat is retained as an individual marker and only annotated as a possible duplicate.
- The full raw archive and original 101-row marker export remain the authoritative evidence.
