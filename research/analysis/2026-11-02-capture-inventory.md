# Capture inventory — 2026-11-02

Source archive: `Albionsskkakaks111.zip` (provided in chat; raw ZIP has not yet been committed to GitHub). Primary JSONL: `albion-1793620890462.jsonl`. Secondary capture `albion-1793620143023.jsonl` contains no UDP/5056 game packets and is not useful for entity inventory.

## Primary capture
- 22,093 JSONL rows / IP packet records.
- 14,975 UDP packets involving Albion port 5056; all observed packets have valid Photon envelopes.
- Event 40 resource snapshots: 77 observations, 51 unique runtime node IDs.
- Event 46 state updates: 146 observations; 13 IDs overlap Event 40 nodes, and 44 IDs have state updates but no Event 40 location snapshot in this capture.
- Event 123 living-entity observations: 448.
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

## Important interpretation
- The 51 count is distinct Event 40 runtime IDs, not packet observations.
- Event 40 `type_id` in this decoder is the resource family number; it is not the mobile code.
- Previously cross-checked Ore family 29 mobile codes: T3=571, T4=573, T5=574. These are recorded in the machine dataset with explicit cross-capture provenance. Other families remain unknown.
- Event 46 updates are linked only when runtime IDs match exactly. A state-only ID is preserved separately; it is not assigned a guessed location or resource family.
- Raw state transitions may include resets/reappearance. They are preserved as timelines and are not flattened into a simplistic depleted/not-depleted flag.

## Known state timelines for located nodes
13 of 51 Event 40 IDs have matching Event 46 updates. Their complete ordered timelines are in the JSON database. Five nodes have clear 4→3→2→1→0 sequences (including IDs 39075, 39460, 39464 and two more in the same capture); all other sequences are preserved without assuming every value has the same meaning.

## Next analysis tasks
1. Correlate all manual marker cycles with runtime node IDs using time windows plus exact family/tier/enchantment and state evidence; preserve ambiguity.
2. Resolve mobile codes for families 7, 14 and 27 from additional captures or protocol evidence.
3. Investigate why 44 Event 46 IDs lack Event 40 snapshots; these may represent previously known nodes outside the snapshot window.
4. Continue own-player movement extraction separately from arbitrary Event 3 movement.
5. Use repeated captures before promoting any new resource-code mapping to production.
