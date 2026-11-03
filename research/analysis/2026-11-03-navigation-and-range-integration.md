# Albion Radar: evidence integration and navigation update

Date: 2026-11-03
Branch: `albion-pattern-mining`

## Confirmed T5.3 Ore target

The previously user-confirmed node is retained as an explicit exception:
- source entity ID: `2788`
- family: `27` (still unresolved as a general family mapping)
- tier: `5`
- enchant: `3`
- coordinates: `(-379, -24)`
- catalog entity: `catalog:node:2788`
- classification: `unclassified_resource_candidate`
- evidence grade: `typed_resource_family_unresolved`
- user confirmation: `true`

This is sufficient to preserve and display this particular node as the user's confirmed T5.3 Ore target. It is NOT evidence that every family-27 node is Ore. The user-confirmed flag is the reason the radar's Ore filter includes this one record.

## Movement evidence now present in the application

The PCAP audit dataset contains 1,704 outbound Move(22) samples across 19 captures from the three inbox archives. The samples are preserved per capture in:
- `research/analysis/2026-11-02-own-player-movement-timeline.csv`
- `research/database/2026-11-02-own-player-movement-timeline.csv`

The timeline fields are capture-scoped timestamp, current world X/Y, heading, destination X/Y and speed field. Do not merge runtime coordinates or timestamps across separate captures without proving zone/session continuity.

The live code already maps outbound Photon operation 22 to `own_player_move` in `AlbionEntityEventMapper`. The live knowledge store persists current X/Y, heading, destination, and a bounded movement history. The mini radar consumes the player's recent movement history for coordinate smoothing. This integration is based on the actual outbound Move request, not the invalid XOR-obfuscated player Event 3 float offsets.

Evidence confidence:
- High: current position X/Y comes from operation 22 parameter 1; sequential values form coherent paths.
- High: operation 22 parameter 3 is the movement destination pair in the tested captures.
- Moderate: parameter 2 behaves as heading degrees; the exact compass convention and map transform remain uncalibrated.
- The speed field is not a measured velocity and must not be used as one.

## Resource lifecycle and entity correlation

The three inbox archives produced 23,020 extracted observations, including 507 Event 39 resource-list observations, 55 Event 40 identified records, 53 Event 46 resource-state IDs, 1,704 Move(22) samples, 126 Event 123 moving-entity observations and 11 attack-target records.

Event 46 state transitions such as 2 -> 1 -> 0 or 4 -> 3 -> 2 -> 1 -> 0 are useful for marking depleted resources. Join state changes to a node only by exact runtime entity ID within the same capture/session. Only five exact Event 40/Event 46 ID joins were found in the three new archives, so do not proximity-join the remaining records.

Operation 23 attack target IDs can be correlated with Event 123 and health events. A Leave event is not proof of a kill; kill confirmation needs explicit lifecycle evidence.

## Radar behavior changes in this revision

- Live radar world-coordinate range increased from 130 to 260 game units. This changes visualization scale and the displayed area; it does not force the server to send entities that the client has not received.
- Radar canvas increased from 190 x 142 dp to 224 x 190 dp; radar window width increased from 202 dp to 238 dp.
- Target selection continues to choose the nearest non-depleted resource matching the active filters. It keeps the current target stable while approaching, but switches if another matching resource becomes at least 12 world units closer.
- The nearest-resource list is sorted by actual player-to-node distance, rather than depending on an unavailable precomputed list.
- The target line is drawn from the latest smoothed player world coordinate to the selected resource coordinate. The display is north-up/world-coordinate relative; it does not require heading to calculate the target vector.

## Remaining validation

1. Build and run unit/replay tests after these changes.
2. Verify that the live packet capture emits Move(22) observations on the user's device and that X/Y updates are continuous.
3. Field-test the 260-unit radar range; packet delivery determines actual visibility.
4. Calibrate map orientation and coordinate-to-screen transform with known landmarks or deliberate movement experiments before claiming compass-grade bearing accuracy.
5. Compare nearest-target switching and depletion handling during a real gathering session.
