# Verified own-player position evidence — 2026-11-03

## Finding

The player's current in-game coordinate is directly observable in outbound Photon operation 22, parameter key 1, as a two-number position pair [x, y]. This is not an inference from nearby entities or Event 3. Operation 22 also carries heading at key 2, target position at key 3, and speed at key 4.

## Independent capture evidence

### Capture A — run and stop

Source report: research/analysis/Albions--run_and_stop-18-pcap-report.md  
Original capture: Albions.zip / run and stop.pcap

- 1,944 IP packets; 1,264 UDP packets involving port 5056; 1,264 valid Photon envelopes.
- 233 operation-22 requests and 233 mapped own_player_move observations.
- At timestamp 1793399097905 ms:
  - current position: [-78.86469, 46.23921]
  - heading: 172.43239 degrees
  - target: [-75.69194, 44.742764]
  - speed: 11.0
- At timestamp 1793399098025 ms, current position advanced to [-77.37234, 45.535328].

### Capture B — two 90-degree direction changes

Source report: research/analysis/Albions--2_times_90degred_change_direction_-14-pcap-report.md  
Original capture: Albions.zip / 2 times 90degred change direction .pcap

- 1,046 IP packets; 733 UDP packets involving port 5056; 733 valid Photon envelopes.
- 203 operation-22 requests and 203 mapped own_player_move observations.
- At timestamp 1793399203561 ms:
  - current position: [-61.95968, 241.66254]
  - heading: 181.79382 degrees
  - target: [-60.418728, 240.38028]
  - speed: 11.0
- Subsequent requests show the coordinate pair changing over time and heading changing during turns.

## Production mapping decision

- Treat outbound operation 22, key 1 as the strongest currently verified own-player position source.
- Treat key 2 as heading, key 3 as target/destination, and key 4 as speed, consistent with the captured request shapes.
- Keep the client-origin player observation distinct from player entity IDs observed in spawn events. The local entity_id=-1 value is an app sentinel for YOU, not an Albion runtime entity ID.
- Do not interpret arbitrary Event 3 payload bytes as player coordinates. The same reports show implausible decoded Event 3 coordinates and label them unvalidated_event3_payload.
- These captures establish real coordinate fixes and movement sequences, but do not establish a zone ID or a global-world coordinate transform.
- Coordinates are valid only within the currently observed game coordinate space until zone identity is decoded.

## Implementation coverage

- AlbionEntityEventMapper maps operation 22 key 1 to own_player_move.
- AlbionLiveKnowledgeStore persists a bounded movement trail (maximum 240 points).
- Mini radar and live coordinate map render the observed player position and recent trail.
- Automated tests cover operation-22 coordinate extraction and movement-trail persistence.

## Confidence

High for the direct mapping of operation 22 key 1 to the player's current coordinate pair and for the observed movement sequences in these two captures. Unknown: how to associate those coordinates with a named zone or a global map.
