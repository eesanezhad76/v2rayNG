# Player movement reconstruction from outbound Photon Move requests

Analysis date: 2026-11-02  
Branch: `albion-pattern-mining`  
Source archive: `Albions.zip`  
Method: decode locally-originated Photon operation requests. Upstream OpenRadar's operation-code dictionary names operation 22 as `Move`; the parameter meanings below were established empirically from the decoded values and their continuous movement patterns.

## Why the previous event-3 method was rejected

The earlier Kotlin decoder read two fixed float offsets from every event-3 movement payload. That assumption is not valid for player entities: player movement arrays are XOR-encrypted, and the result produced impossible values (for example magnitudes around 10^21). That path was removed from coordinate output. Event-3 mode-3 positions are still retained as evidence for non-player entities when the mode and coordinate bounds are valid; they are not used as the player's position.

The replacement uses locally-originated operation 22 requests:
- parameter 1: current movement coordinate pair (X,Y), empirically validated by smooth sequential changes;
- parameter 2: heading in degrees;
- parameter 3: destination/target coordinate pair;
- parameter 4: speed field.

These are Albion game-world coordinates, not GPS latitude/longitude or map-image pixels.

## Straight-line movement and stop

Capture: `run and stop.pcap`

- 233 decoded Move(22) requests.
- Time span: 25.66 seconds.
- First sample: (t=1793399097905), current position ((-78.86469, 46.23921)), destination ((-75.69194, 44.74276)), heading (172.43^circ).
- Final sample: (t=1793399123565), current position ((-60.75448, 319.42041)), destination exactly ((-60.75448, 319.42041)).
- The destination/current equality in the final Move request is the stop-state evidence. The speed field in that same sample is 11; it must not be interpreted as a measured movement velocity or as zero.

The path proceeds predominantly northward through the capture, with initial heading changes while the movement settles. The final request is the strongest packet-level indication that the character was told to stop at the current point.

Capture: `stopping.pcap`

- 1,578 IP packets and 1,011 valid Photon envelopes.
- No decoded Move(22) request was present in this file, so this file alone does not yield a movement coordinate series. It is retained as a separate capture and is not merged with `run and stop.pcap`.

## Two-turn movement experiment

Capture: `2 times 90degred change direction .pcap`

- 203 decoded Move(22) requests.
- Time span: 22.14 seconds.
- First sample: (t=1793399203561), current position ((-61.95968, 241.66254)), heading (181.79^circ).
- Final sample: (t=1793399225703), current position ((-144.36446, 363.26904)), destination exactly the same coordinate.

Largest observed heading transitions:
1. At (t=1793399213698), around ((-54.54021, 348.59915)), heading changed from approximately (354.9^circ) to (277.4^circ), a signed change of about (-77.5^circ).
2. Around (t=1793399222679)–(1793399222803), near ((-149.24,331.87)) to ((-148.52,333.07)), heading changed (264.2^circ 	o 311.3^circ 	o 29.4^circ). These two successive changes are approximately (+47.0^circ) and (+78.1^circ), for a net normalized change of about (+125.1^circ).

The filename says two 90-degree turns, but the values decoded from the current heading field do not show two mathematically exact 90-degree transitions. The observations above are reported without rounding them into exact 90-degree turns. Early heading changes at the start of the capture are also retained in the raw timeline and may reflect initial direction settling.

## Dataset generated

`research/analysis/2026-11-02-own-player-movement-timeline.csv` contains 1,704 Move(22) samples from the 19 PCAPs in the three inbox archives. The per-capture and per-sample timestamps, current coordinate, heading, destination and speed field are kept separately.

The same CSV is exported to `research/database/` by the ingest runner. The data remains file-scoped: timestamps and runtime identifiers from separate captures must not be treated as one continuous session unless a continuity test establishes it.

## Confidence and limits

- **High confidence** that the movement coordinate series is sourced from the local client's outbound Move(22) requests: values form continuous paths, the destination evolves with movement, and stop samples show destination equal to current position.
- **High confidence** in the two exact coordinate pairs listed for the initial and final samples of each controlled trace.
- **Moderate confidence** in interpreting the parameter-2 field as heading degrees; it changes consistently with path direction but the current captures do not establish an authoritative coordinate transform or the exact in-game compass convention.
- The current dataset does not yet transform game-world X/Y into screen/map pixels, zone map coordinates, or geographic coordinates.
- No claim is made that these CSVs prove a device-tested live map overlay. They are offline PCAP reconstructions.
