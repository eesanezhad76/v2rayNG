# ALBION RADAR — PERSISTENT HANDOFF / SOURCE OF TRUTH
Last updated: 2026-11-02
Repository: https://github.com/eesanezhad76/v2rayNG

## 0. Mandatory startup protocol for every new chat
1. Read this file FIRST: `docs/ALBION_HANDOFF.md`.
2. Read `research/README.md`, latest capture inventory under `research/analysis/`, database README, and latest analysis reports.
3. Query GitHub branch heads and Actions runs live. Never assume a SHA, build status, APK artifact, test status, or branch head from an old chat.
4. Inspect the current branch and working tree before editing. Do not write until the active branch and exact HEAD SHA are confirmed.
5. Report separately: code committed, tests run, CI build, APK artifact created, and actual physical-device test. Never conflate them.
6. At the end of every work session, update this handoff and the relevant detailed research records in the same active branch. Include exact commit SHA, changed paths, test/run IDs, confirmed facts, unresolved questions, and next actions.
7. Never treat chat memory as the permanent database. GitHub research files are the durable source of truth.

## 1. Branch discipline — do not violate
- ACTIVE development/research branch: `albion-pattern-mining`.
- `albion-reliability-ux`: integration/target branch referenced by Draft PR #3. Do NOT switch to it unless the user explicitly instructs you.
- Draft PR #3 is conflict-prone and must not be edited, closed, merged, or rebased without explicit instruction.
- Do not delete branches, force-push, reset history, or rewrite commits.
- All commits must be fast-forward updates against the freshly checked HEAD SHA. Use expected-SHA/lease semantics. If rejected, re-fetch branch and reconcile; never force.
- Before any changes, inspect the current branch HEAD and preserve all existing content.
- The user expects direct repository edits, not instructions to edit code manually.

## 2. Research vault — preserve all evidence
Root: `research/`
- `research/inbox/`: original uploaded ZIP/PCAP/JSONL capture evidence. Preserve original archives; do not replace or silently delete.
- `research/database/`: normalized machine-readable datasets, node/entity registry, code dictionaries, state timelines.
- `research/analysis/`: per-capture reports, inventories, correlations, hypotheses, confidence and caveats.
- `research/tests/`: ingest tests and validation evidence. Android/JUnit tests remain in their Gradle-discovered source locations too.
- `research/decisions/`: explicit promotion decisions. Only reviewed/verified facts may be promoted into app code.
- `research/tools/`: ZIP/JSONL ingest and offline analysis tools.
- `.github/workflows/albion-capture-ingest.yml`: capture ingest/test workflow. Inspect current file and latest workflow status before relying on it.

Never mix raw evidence with production code. Unknown values must remain unknown. Preserve all packet/event records, entity IDs, timestamps, coordinates, enchantments, state transitions, markers and unmatched records. Do not silently drop ambiguous evidence.

## 3. Existing capture facts (2026-11-02 capture)
Archive supplied in chat: `Albionsskkakaks111.zip`.
Primary JSONL: `albion-1793620890462.jsonl`.
Secondary JSONL: `albion-1793620143023.jsonl` (no UDP/5056 game packets).

Primary capture analysis:
- 22,093 JSONL rows/IP packet records.
- 14,975 UDP packets involving Albion port 5056.
- Event 40: 77 observations, 51 unique runtime node IDs.
- Event 46: 146 updates across 65 IDs; 13 IDs join exactly to Event 40; 52 IDs have state updates but no Event 40 location snapshot in this capture.
- Event 123: 448 living-entity observations.
- Manual markers: 175 total; 21 target_found, 21 gathering_started, 20 gathering_completed, 20 node_depleted.
- 51 unique Event 40 nodes across multiple resource families, tiers and enchantments — NOT just five nodes.
- Detailed counts, IDs, coordinates, raw values and caveats are in the dated capture report and machine-readable database. Always re-read them; this summary is not a replacement.

Verified code mapping from this capture/previous cross-check:
- Resource family/type_number 29 (Ore): T3 mobile_type_id 571; T4 573; T5 574.
- Other families 7, 14, 27: mobile_type_id unresolved (-1 in this capture). Do not invent names/codes.
- Event 40 `type_number` is resource family; `mobile_type_id` is a separate game mobile code; `entity_id` is the runtime ID of one specific node.
- Event 46 must be joined to Event 40 by exact runtime entity ID only, never by proximity or guessed matching.
- Three node IDs (39075, 39460, 39464) have clear complete 4→3→2→1→0 sequences in this capture. Check exact database timelines before quoting more detail.
- State-only IDs must be retained and investigated, not discarded.

Markers:
- Multiple Found markers may be recorded before multiple Started markers. Existing single-active-marker logic loses queued Found events.
- Required model: per-activity FIFO/deque of pending targets and explicit Found → Started → Completed → Depleted transitions; preserve unmatched/extra/duplicate markers with uncertainty rather than force-pairing.
- Marker-to-node association requires exact evidence. Temporal/nearest-neighbor guesses must be labeled hypotheses.

## 4. Player location and movement — HIGH PRIORITY, DO NOT FORGET
The user explicitly recalls that an earlier movement test/capture allowed the assistant to determine the user's in-game coordinates. This is an important prior finding and must NOT be erased or dismissed merely because the latest capture analysis did not recover a validated trajectory.
- Search prior commits, reports, chat handoff files, capture datasets, and earlier test results for the exact coordinate(s), capture filename, entity ID, event code, timestamp and method that previously identified the player's position.
- Preserve the historical coordinate finding with its original provenance and confidence as soon as recovered.
- Distinguish: one-time position fix, own-player entity ID, and a continuous player trajectory. They are not the same.
- A player_spawn record showed player name `EDmars`, entity ID `23433`; this does not by itself prove movement coordinates.
- Do not infer the player's location from arbitrary Event 3 movement records.
- Investigate outbound movement requests, own-player entity updates, known player entity IDs, and packet timing. Record exact source packet/event, direction, entity ID, coordinate system, timestamps, and confidence.
- If old coordinate evidence cannot be found in accessible repo/history, explicitly state that it is not yet recovered and keep the user's recollection as an unresolved lead. Never state categorically that it never existed.

## 5. Current architecture / app capabilities and limits
Implemented in previous work (verify current code before claiming):
- Android VPN/TUN packet capture to PCAP/JSONL.
- Overlay/manual capture markers.
- Shared marker builder/parser and validation.
- Photon packet/event decoding and entity mapping.
- Offline capture analyzer and heuristic pattern miner.
- Event 40 harvestable and Event 46 resource-state observations; Event 123 living entity observations.
- Marker error visibility in overlay.

Not yet proven/complete:
- Live interactive map with player/resource pins.
- Reliable own-player coordinate/trajectory reconstruction.
- Full mob Seen→Killed linkage and exact T4 golem runtime entity identification.
- Calibrated statistical confidence (current confidence is heuristic).
- Production-grade mappings for unknown resource families.
- The capture-ingest workflow must be checked against actual ZIP and Actions logs; never assume it processed a particular archive just because the workflow exists.

## 6. Promotion policy: research → production APK
- Research vault is the evidence source; app code consumes only reviewed records.
- A mapping may be promoted only when: (a) raw packet/event evidence is preserved, (b) semantics and exact field meanings are understood, (c) corroborated across independent captures where feasible, (d) regression tests cover it, (e) decision record is added under `research/decisions/`.
- Keep experimental dictionaries/data out of APK. Promote the smallest verified subset required by production.
- Unknown/encrypted data must not be fabricated or decoded by guesswork.
- Before build, inspect diffs and tests. Build only after the user-requested implementation scope is clear.
- After build, verify workflow run SHA, conclusion, artifact name/download availability, test totals. State that device behavior remains unverified until user tests on Poco F5.

## 7. Previous build references — historical, not current
- Build #112, run ID 41426701143, SHA 6f741c621a0a91bc5e688b93c9769bfc534c5085: success, APK artifacts and diagnostics; 150 tests, 0 failures/errors/skips. This predates later overlay fix.
- Build #113, run ID 41433972907, SHA 37cce77c6371aec5eab54b538a81715c83179f26: previously reported successful; re-verify live if needed.
- Do not call these the latest build unless verified against current branch.
- No claim of physical installation/device testing unless the user explicitly confirms it.

## 8. Immediate next actions
1. Verify current branch HEAD and latest Actions runs.
2. Audit the research tree and current ingestion workflow; ensure original ZIPs are actually preserved in inbox (the chat-uploaded ZIP was analyzed, but do not claim raw ZIP was committed unless verified).
3. Audit generated node database against raw JSONL. Confirm all 51 unique Event 40 nodes and all 65 Event 46 IDs are retained, with exact ID joins only.
4. Retrieve the earlier user-position/movement test and exact coordinates from prior GitHub/chat/capture evidence; preserve it as a first-class report and test fixture.
5. Implement marker FIFO/deque lifecycle and tests without losing unmatched markers.
6. Improve mob/entity lifecycle and own-player movement correlation.
7. Add a new capture whenever the user provides one; never ask for an already available capture again.
8. Promote only verified findings to app code; build and verify CI afterward.

## 9. Required reporting format at each session end
- Active branch + current HEAD SHA:
- Commits made (SHA and message):
- Files added/changed:
- Research data extracted (counts, IDs, exact joins):
- Confirmed findings:
- Hypotheses / unknowns:
- Tests run + exact result:
- Actions workflow run ID/status/SHA:
- APK artifact status:
- Device test status:
- Next concrete actions:

## 10. Instructions to the assistant in the next chat
Treat this file as mandatory project continuity context. Do not restart analysis from scratch, do not overwrite prior findings, do not claim a feature is complete without evidence, and do not confuse absence in the latest capture with absence in all historical captures. The user values exact coordinates, entity IDs, resource codes, enchantment levels, state timelines, marker correlations and permanent records. Update this handoff after every meaningful discovery or implementation.
