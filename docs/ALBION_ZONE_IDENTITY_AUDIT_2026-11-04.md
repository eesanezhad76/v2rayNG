# Albion Radar — Zone Identity and Session Boundary Audit
Date: 2026-11-04
Branch: `albion-knowledge-integration-2026-11-04`
Starting HEAD: `8a8d8835cd2770924ba4d053627936ec1b388cd0`

## Objective

Determine whether the existing Photon/Protocol18 observations contain a reliable zone/map identity or an explicit zone-transition signal that can safely scope spatial coordinates. This audit uses the existing parser, mapper, capture reports, and research corpus; it does not repeat entity discovery.

## Findings from the current runtime parser

The current `AlbionEntityEventMapper` recognizes these server event codes: 1, 3, 6, 7, 8, 29, 39, 40, 46, 47, 90, 91, 92, 123, 211, and 325. It also maps outbound request 22 to the local player's movement coordinates.

Relevant semantics already implemented:

- Event 1 is an entity `leave` record keyed by one entity ID. The mapper removes that ID from its in-memory player/non-player sets. It is not evidence that the player left a zone.
- Event 3 is an entity movement/update. The current parser's player movement payload is explicitly marked unvalidated in diagnostics.
- Request 22 provides the local player's current/target coordinate pair and movement metadata. It contains no decoded zone ID or map identity.
- Events 39/40/46 provide resource candidates, typed resource observations, and resource-state updates. Their mapped records contain entity IDs and local X/Y values, but no zone identity.
- Event 123 provides living-entity Type ID and position, but no zone identity.
- Unknown event codes are not promoted to named zone semantics by the entity mapper.

The passive Protocol18 decoder does not decrypt encrypted Photon messages. No encrypted payload is treated as a source of zone identity.

## Findings from existing research

- The 83-node forensic catalog is explicitly capture-local and has unknown zone identity.
- The 331 resource observations and 2,330 mobile-entity observations are useful for type/evidence enrichment, but their coordinates are not a verified common world coordinate frame.
- The existing research reports do not establish a stable zone/map ID or a coordinate transform shared across captures.
- Capture/session IDs are already retained in the knowledge pipeline. This prevents accidental cross-capture entity-ID merges, but it does not prove that one long-running capture remains inside one zone.

## Newly identified candidate signal

The existing archive `Albion2.zip` contains nine distinct PCAPs. The report for the capture whose user-provided description says it traveled from a yellow-zone region to Fort Sterling contains **three Event 2 observations**. The other eight PCAPs in the same archive contain **zero Event 2 observations**.

This is a useful correlation, but not proof:
- the report currently stores only the event-code count, not Event 2 parameter values;
- there is only one known transition capture in this comparison;
- the event could be associated with another activity that happened during that capture.

A targeted diagnostic was added to the offline analyzer to preserve the timestamp and decoded parameter values for Event 2 without assigning semantics. The PCAP audit now writes:
- `research/analysis/2026-11-04-zone-event-candidates.md`
- `research/analysis/2026-11-04-zone-event-candidates.jsonl`

The APK workflow uploads these as the `zone-event-candidates` artifact after its offline PCAP replay step. The next successful workflow run can reveal the exact three parameter shapes/values for comparison. Event 2 remains an **unmapped candidate**, not a zone detector.

## Decision

**Do not infer a zone transition from Event 1, coordinate jumps, entity churn, or a quiet period.** Each can occur without a zone change, and treating any of them as a boundary would split valid data or merge unrelated maps unpredictably.

No guessed zone parser, teleport-distance threshold, or cross-capture coordinate merge has been added. The current safeguards remain active:

1. Runtime entity IDs stay capture-session scoped.
2. Historical coordinates remain excluded from the live player-centered map.
3. Cross-zone projection remains disabled until a verified zone identity and coordinate frame are available.
4. Unknown event signatures remain candidates; they are not named as map/zone events.

## What is required to unlock the next implementation

A new controlled capture should include:
- a continuous capture before, during, and after a known zone transition;
- the same capture session kept running across the transition;
- outbound movement request 22 and all observable server event codes/parameter shapes retained;
- a second capture that revisits one of the same zones, if practical, to test whether any candidate identity is stable;
- an exported knowledge bundle with capture/session provenance.

The analysis must identify a candidate signal that changes at the transition, remains stable within a zone, and repeats on a revisit. It must be independently corroborated before being used as a spatial key. If no such signal is observable because of encryption or capture limitations, the app must continue using session-local maps rather than inventing a world map.

## Validation status

- Source audit: completed by inspection of the current branch.
- Existing capture corpus: reviewed; no verified zone identity found in the current reports.
- Android build / Kotlin tests for this branch: **not verified**. Workflow run `41716615223` remains stuck at `Checkout code`; Android SDK setup and Gradle have not started.
- Real-device zone-transition capture: not yet available.

## Phase 5 — Manual zone-transition ground-truth marker

Added a compact `ZONE ARRIVAL • MARK` control to the Albion overlay. The user should tap it immediately after the game has completed loading into a different zone while PCAP capture is running.

The marker records:
- wall-clock timestamp and Android elapsed-realtime timestamp;
- `event=zone_transition_observed`;
- `activity=Zone`;
- `marker_type=manual_zone_transition`;
- `source=manual_overlay`.

The offline analyzer now preserves `marker_type` in the checkpoint report. A JVM test was added to verify that a zone-arrival marker survives PCAP marker parsing and analysis.

This is a manual ground-truth timestamp only. It does not infer a zone ID, does not change runtime entity handling, and does not enable cross-zone coordinate merging. For a useful capture, keep capture running before, during, and after travel; tap once after arrival in each new zone; optionally tap again on return to a previously visited zone.