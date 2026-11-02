# Albion capture deep analysis — 2026-11-02

Source: user-uploaded `Albionsskkakaks111.zip`.
Analyzed files:
- `albion-1793620890462.jsonl` and matching PCAP: primary game capture.
- `albion-1793620143023.jsonl` and matching PCAP: short overlay-only capture; it contains no UDP/5056 game traffic and is not useful for entity decoding.

## Primary capture inventory
- 22,093 packet rows / IP packets.
- 14,975 UDP packets involving Albion port 5056; all 14,975 passed the Photon envelope parser.
- 5,588 inbound and 9,387 outbound packets.
- Event 39 (harvestable list): 408 occurrences.
- Event 40 (single harvestable): 77 observations.
- Event 46 (harvestable state): 146 occurrences.
- Event 123 (living entity): 448 observations.
- Mapped observations in report: 9,162 movement events, 448 mob/living-entity observations, 77 harvestables, 307 health events, 101 health-regeneration events, 46 player-spawn observations, 1 mob-state observation, plus other event classes.
- 175 manual markers in JSONL (includes capture lifecycle and selection markers).

## Critical schema distinction
The existing mapper exposes two different numeric identifiers for a harvestable:
- `mobile_type_id`: the game mobile/type code. The five-node T5 Normal cluster in this capture is **574**.
- `type_number`: resource family number (for example, 29).
Do not conflate either with the runtime entity ID (`entity_id`), which identifies one individual node instance.
Event 40 carries the node's runtime ID, mobile type, resource family, tier, position, and size/state. Event 46 updates that same runtime ID's resource state. These exact IDs should be joined; proximity-only joins are unsafe.

## Resource family evidence
In the Ore-labeled capture, resource-family number 29 repeatedly appears in harvestable observations. The five-node cluster around the user's five consecutive T5 Normal Found markers has these exact nearby runtime entities/positions:

| Runtime entity ID | Mobile type ID | Family number | Tier | Enchant raw | X | Y | Observed size/state |
|---:|---:|---:|---:|---:|---:|---:|
| 36602 | **574** | 29 | 5 | 0 | -57.2258 | 382.6596 | 0 |
| 36601 | **574** | 29 | 5 | 0 | -59.3886 | 379.9530 | 0 |
| 36607 | **574** | 29 | 5 | 0 | -54.5323 | 381.6981 | 0 |
| 39460 | **574** | 29 | 5 | 0 | -57.8493 | 380.3329 | 5 → 0 |
| 39464 | **574** | 29 | 5 | 0 | -56.4080 | 382.8032 | 5 → 0 |

These five IDs form a tight cluster (roughly 5.5 game units across). All five have the same **mobile_type_id=574**, family number 29, T5, enchant raw 0. The three nodes 36602/36601/36607 were observed at size 0. The other two have stronger depletion evidence: event 46 updated ID 39460 at 1793621336592, 1337138, 1337674, 1338205, 1338742 with size 4,3,2,1,0; ID 39464 at 1793621339723, 1340285, 1340827, 1341321, 1341879 with size 4,3,2,1,0. The exact runtime ID remains constant through each five-step depletion sequence. This proves these two individual nodes were harvested down to zero during the capture. It does not yet prove which exact Found marker belongs to each ID, because the server snapshots for these five entities occur after the manual marker sequence.

Ore mobile-type codes directly observed in this capture (same family number 29):

| Tier | Enchant observed | mobile_type_id | Evidence |
|---:|---:|---:|---|
| T3 | Normal | **571** | Event 40 harvestable observations |
| T4 | Normal | **573** | Event 40 harvestable observations |
| T5 | Normal and E1 | **574** | Event 40 harvestable observations; the five-node cluster is T5 Normal |

Thus the code progression for Ore in this capture is T3=571, T4=573, T5=574. These are mobile type codes, not runtime node IDs. Other resource families had `mobile_type_id=-1` in this capture, so their exact mobile type codes were not decoded reliably.

Additional resource family/tier combinations were decoded in the same capture:
- Family 29: T3, T4, T5 (Ore-labeled context).
- Family 14: T4 and T5.
- Family 27: T4 and T5.
- Family 7: T4 and T5.
The overlay was manually switched through Wood/Ore/Other/Travel/PvP/Mobs/Hide/Fiber/Stone during the session. Family-to-name mappings beyond family 29 should remain provisional until cross-checked against a capture with a single known resource activity and/or game data tables. Never hard-code a semantic name solely from the integer.

## Five consecutive T5 Normal node sequence
Markers:
- Five `target_found` markers occurred at 1793621260195, 1793621261187, 1793621262149, 1793621263165, and 1793621264543.
- Five `gathering_completed` markers occurred at 1793621270797, 1793621280507, 1793621298035, 1793621304788, and 1793621313017.
- Five `node_depleted` markers followed at 1793621271537, 1793621281038, 1793621298456, 1793621305212, and 1793621313553.
- There are six `gathering_started` markers in this interval (1793621266974, 1273431, 1283104, 1293472, 1299976, 1307145). This mismatch is real and must be preserved in analysis, not silently forced into five pairs. It may represent a repeated/extra start tap or one start without a matching completion.
- The five family-29 / mobile-type-574 T5 Normal node entities listed above are observed in the same local resource cluster shortly after this sequence. Two of them (39460 and 39464) have complete event-46 size countdowns from 5 to 0.

## Earlier labeled mining samples
The marker stream also contains complete labeled sessions:
- T4 Normal: two early complete Found/Start/Completed/Depleted cycles.
- T3 Normal: one complete cycle.
- T5 Normal: one complete cycle.
- T5 E1: two complete cycles.
- T4 E1: one complete cycle.
- T5 E1: one complete cycle.
- T4 E1: one complete cycle.
- T5 Normal: five completions/depletions after five Found taps (with six Starts).
- T5 E1 and T4 E2/E1 later cycles are also present.
The current miner's old one-active-sample-per-activity design overwrites earlier Found samples when several Found markers are queued before any Start. This is the direct reason the previous report undercounted multi-target sessions. The next miner revision must use a FIFO/deque of pending targets per activity, then bind Start/Completed/Depleted by ordered lifecycle, preserving unmatched/ambiguous events and emitting confidence/reason codes.

## Mob and Troll annotations
- A manual `mob_killed` marker exists at 1793621470263, following a `target_found` marker at 1793621458722.
- Living-entity/movement observations surround this interval, but current mapping does not prove which moving entity is the killed T4 golem. Candidate selection must use event 123 entity IDs, health/state transitions, and exact timing; don't assign a mob ID from nearest position alone.
- A Travel activity_start/activity_end pair exists at 1793621532147 and 1793621547304 (about 15.16 seconds). The manual activity was labelled Travel in the overlay; it is not a semantic Troll event. The capture contains many movement observations for many entities, but own-player route/turn extraction is not established.

## Player position
The name `EDmars` appears in player-spawn observations, but the existing mapper currently suppresses movement events for entities not classified as known non-player entities. No validated own-player entity ID/trajectory was recovered in this pass. The next decoder investigation should correlate player-spawn IDs with outbound movement/operation requests and distinguish local-player movement from remote entities.

## Required implementation changes
1. Replace the miner's `active[activity]` single sample with pending FIFO queues per activity; Found markers must not overwrite one another.
2. Track lifecycle states separately: Found, Started, Completed, Depleted. Preserve unmatched and duplicate transitions; do not count a cycle complete unless its own lifecycle closes.
3. Join event 40 and event 46 by exact runtime entity ID. Retain `mobile_type_id`, `type_number`, tier, enchant, X/Y, and size/state in each sample.
4. Add a resource family/type catalog with provenance and confidence. Keep unknown IDs unknown; do not guess names.
5. Add tests for five Found markers followed by sequential Starts/Completed/Depleted, delayed Found, one extra Start, and event-46 state updates.
6. Extend mob samples with event 123 IDs, health and mob-state events, but avoid proximity-only attribution.
7. Add a separate local-player movement decoder path; do not treat every event-3 move as the player's movement.

## Integrity / limitations
- The second short capture contains only 2,138 IP records, zero UDP/5056 packets, and three overlay checkpoints; it is not a game-data capture.
- All numeric positions are game-world coordinates, not latitude/longitude and not yet transformed to a map image.
- The current confidence score is heuristic, not a calibrated probability.
- This document is an analysis record from the supplied capture, not a claim that the next APK already implements these fixes.
