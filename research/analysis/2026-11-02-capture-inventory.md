# Capture inventory — 2026-11-02

Source archive: `Albionsskkakaks111.zip`; primary JSONL: `albion-1793620890462.jsonl`. Secondary JSONL `albion-1793620143023.jsonl` contains no UDP/5056 game packets.

## Primary capture
- 22,093 JSONL rows / IP packet records.
- 14,975 UDP packets involving Albion port 5056.
- Event 40: 77 observations, 51 unique runtime node IDs.
- Event 46: 146 updates across 65 unique runtime IDs; 13 IDs join exactly to Event 40 nodes; 52 state IDs have no Event 40 location snapshot in this capture.
- Event 123: 448 living-entity observations.
- Manual markers: 175 total; 21 target_found, 21 gathering_started, 20 gathering_completed, 20 node_depleted.

## 51 unique Event 40 nodes by family/tier/enchantment

| family | tier | enchant raw | unique nodes |
|---:|---:|---:|---:|
| 29 | 3 | 0 | 3 |
| 29 | 4 | 0 | 6 |
| 29 | 5 | 0 | 16 |
| 29 | 5 | 1 | 1 |
| 27 | 4 | 1 | 3 |
| 27 | 4 | 2 | 1 |
| 27 | 5 | 1 | 7 |
| 14 | 4 | 1 | 4 |
| 14 | 4 | 2 | 1 |
| 14 | 5 | 1 | 2 |
| 7 | 4 | 1 | 1 |
| 7 | 5 | 1 | 5 |
| 7 | 5 | 2 | 1 |

The database file stores each unique node's runtime ID, mobile code, family, tier, enchantment, coordinates, first/last timestamps, observation count, sizes and exact-ID state timeline.

## Interpretation
- Counts are unique Event 40 runtime IDs, not packet occurrences.
- Event 40 `type_number` is the resource family; it is not the mobile code.
- Ore family 29 codes cross-checked in earlier work: T3=571, T4=573, T5=574. Other families remain unknown in this capture.
- Event 46 timelines are joined only by exact runtime ID. The 52 state-only IDs are retained separately; no coordinates or resource names are guessed.
- Three located nodes have complete 4→3→2→1→0 transitions: IDs 39075, 39460 and 39464. Other transitions, including resets/reappearance, are preserved verbatim.

## Follow-up
1. Correlate all marker cycles to IDs without forcing ambiguous matches.
2. Resolve family 7/14/27 mobile codes from further captures.
3. Investigate 52 state-only IDs.
4. Extract own-player movement independently from arbitrary Event 3 movement.
5. Promote mappings only after independent capture corroboration and regression tests.
