# Albion Zone Evidence Phase 6 — Dual-clock correlation

Status: instrumentation change in progress; no automatic zone-transition semantics are enabled.

## Purpose

Preserve enough timing information to compare decoded protocol events with manually observed zone arrivals. This phase is deliberately an evidence collector, not a zone detector.

## Clock model

Each manual overlay marker can contain:
- `timestamp_ms`: wall-clock epoch milliseconds, comparable to classic PCAP packet timestamps.
- `elapsed_realtime_ms`: Android monotonic milliseconds, useful for ordering and detecting wall-clock adjustments.
- `marker_type=manual_zone_transition`: explicit user-observed arrival checkpoint.

The offline analyzer now retains both clock values in checkpoint text when the monotonic value is present. Older marker records without that field remain valid.

## Evidence protocol

For each controlled travel capture:
1. Start capture before leaving the origin zone.
2. Keep capture active during loading and after arrival.
3. Tap **ZONE ARRIVAL • MARK** only after the destination zone is visibly loaded.
4. Wait for a short stable interval, then stop capture.
5. If practical, repeat a same-zone control capture with the same marker procedure but without changing zones.

## Candidate evaluation

For every Event 2 observation, retain the exact decoded parameter map and PCAP timestamp. Compare its temporal offset to the nearest manual arrival marker and inspect surrounding:
- outbound movement operation 22,
- player movement/update observations,
- entity/resource population churn,
- any repeated Event 2 parameter signature.

Do not infer a zone ID from coordinates or entity churn. Do not treat proximity to one marker as proof.

## Minimum promotion gate

Event 2 may be described as a *zone-transition candidate* only after the same signature appears around at least two independent, manually marked zone arrivals and is absent from a same-zone control. It must remain *unconfirmed* until the payload semantics or a stable zone identity field are independently established.

No historic coordinates are eligible for live map projection from this analysis.
