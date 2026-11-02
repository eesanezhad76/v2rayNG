# Albion Radar — Persistent Project Handoff

> **Purpose:** This is the durable working-memory file for future ChatGPT sessions. Read it before making code changes or reporting project status. Update it in the same branch after every meaningful implementation, test, CI, bug-fix, regression, or field-test event. This file is an operational handoff, not proof that an untested feature works.

## 0. Session-start instructions (read first)

1. Confirm the active branch is **`albion-pattern-mining`**. Do not silently switch to `albion-radar`, `albion-reliability-ux`, or another branch.
2. Fetch the branch HEAD and compare it with the last recorded commit below. The branch may have advanced through GitHub Actions automation.
3. Read this file, `docs/ALBION_RADAR_ANDROID.md`, `research/README.md`, and the latest relevant code/workflow files before continuing.
4. Check current GitHub Actions runs, job steps, logs, and artifacts. Never rely on an old chat status when GitHub has newer evidence.
5. Keep code changes, tests, and documentation on `albion-pattern-mining`. Do not claim a build or feature succeeded unless the relevant CI/test/artifact evidence exists.
6. Before ending a work session, update this file with: commit SHA, files changed, what was implemented, tests actually run and their results, failures and fixes, regressions/risks, unfinished work, active workflow run IDs, artifact links/checksums if verified, and the exact next action.
7. Explicitly distinguish **implemented**, **unit-tested**, **CI-built**, **device-tested**, and **verified in live Albion gameplay**. These are different evidence levels.

## 1. Project objective

Build an Android passive packet-capture, evidence-learning, and radar/overlay system for Albion Online, integrated into this V2RayNG fork.

Long-term goals:
- Capture Albion traffic from the VPN TUN path without decrypting the game's encrypted payload.
- Decode observable transport/protocol structure (including Photon framing, fragmentation, and any safely inferable nested numeric/JSON fields).
- Learn recurring packet/event patterns for players, mobs, resources, movement, interactions, and other useful game events.
- Keep evidence provenance, confidence, conflicts, and session boundaries; do not turn uncertain hypotheses into facts.
- Persist learned knowledge locally and export a compact ZIP that can be sent back for central-database ingestion.
- Provide a live overlay, mini-radar, and map presentation with clear confidence/unknown states.
- Iterate from real controlled gameplay captures; do not invent protocol semantics or global coordinates.

## 2. Repository and branch guardrails

- Repository: `eesanezhad76/v2rayNG`
- Required working branch: `albion-pattern-mining`
- Android app application ID: `com.v2ray.ang.radar` (separate install from normal V2RayNG).
- Intended test device: Poco F5, Android 15, arm64-v8a; prefer the arm64 APK for this device.
- Current branch HEAD at the time this file was created: `07f0a28b5bb10a67247b4c952a8a7e5da4a946ef`.
- The current HEAD is an automated ingest commit titled `data(albion): ingest uploaded capture evidence`; its parent is `5ab7482b7bc190a3cdddd3a511e6e799b976a262`.
- The previous implementation commit `5ab7482b7bc190a3cdddd3a511e6e799b976a262` included the incremental central-DB preservation fix.
- PR #3 currently associates this branch with base branch `albion-reliability-ux`. Do not treat that PR base as permission to do development on the base branch; continue work on `albion-pattern-mining`.

## 3. Implemented work (repository-level; not all field-verified)

### Android capture / evidence / UI
Existing implementation includes the following components (inspect current source before modifying):
- `AlbionCaptureWorker.kt`
- `AlbionRadarSnapshotParser.kt`
- `AlbionLiveKnowledgeStore.kt`
- `AlbionEntityEventMapper.kt`
- `AlbionWorldMapView.kt`
- `AlbionMiniRadarView.kt`
- `AlbionKnowledgeBundle.kt`
- `AlbionPatternMiner.kt`
- Related capture, protocol, store, and pattern-miner tests.

Previously integrated capabilities:
- Passive packet-tap pipeline from the VPN/TUN path.
- Protocol/frame decoding and evidence collection without claiming game-payload decryption.
- Entity/event evidence mapping and persistent live-knowledge storage.
- Session-aware entity evidence; runtime IDs must not be merged across independent capture sessions.
- Map and mini-radar UI using locally observed coordinates.
- Overlay and capture UI.
- Manual action markers (e.g. resource/mob labels) for correlating a player's observed action with nearby packet evidence.
- Offline capture replay and pattern mining with confidence/evidence reporting.
- ZIP export of learned data, including `live-knowledge.json`, `protocol-evidence.json`, `learned-patterns.json`, manifest, and SHA-256 metadata (verify exact current schema in source).

### Pattern-mining safeguards
- Byte-identical capture files are deduplicated by SHA-256; copied files must not count as independent captures.
- Mining scans a bounded recent-capture window.
- Reports skipped duplicate files.
- Entity/runtime identifiers are scoped to capture sessions; identical numeric IDs in different sessions are not automatically the same entity.

### Central knowledge ZIP importer
Added:
- `research/tools/ingest_knowledge_bundle.py`
- `research/tests/test_knowledge_bundle_import.py`
- Workflow integration in `.github/workflows/albion-capture-ingest.yml`
- Documentation in `research/README.md`
- Field-test guidance in `docs/ALBION_RADAR_ANDROID.md`

Importer behavior implemented:
- Recognizes ZIP bundles with manifest format `albion-radar-knowledge`, schema version 1.
- Validates manifest, required members, byte counts, and SHA-256 checksums.
- Reads only expected ZIP members; does not extract arbitrary archive paths.
- Streams whole-ZIP hashing to avoid loading large raw capture ZIPs into memory.
- Stores central records in `research/database/albion-knowledge-bundle-import.json`.
- Preserves session-scoped entity records, protocol evidence, learned pattern signatures, and bundle provenance.
- Deduplicates repeated cumulative exports so repeated imports do not inflate sightings or pattern sample counts.
- Keeps same runtime IDs from different sessions separate.
- Skips ordinary raw PCAP ZIPs in this importer; the existing raw-capture ingest path remains responsible for those.
- A bug in custom `--output` handling was fixed: incremental imports now load the existing database instead of overwriting it.
- A checksum-test fixture bug was fixed by rewriting the ZIP manifest rather than appending a duplicate ZIP member.

## 4. Verification ledger

### Confirmed from the last observed CI evidence
- The native passive-tap `libv2ray.aar` build step succeeded in an earlier APK workflow attempt.
- The knowledge-ingest workflow run **41536147098** completed with conclusion **success** on commit `5ab7482b7bc190a3cdddd3a511e6e799b976a262`.
- That ingest CI run reported **21 Python tests passed**, including importer tests.
- Existing eight ZIP archives in the repository at the time were ordinary raw capture archives, not Android knowledge bundles. The central knowledge DB therefore had no genuine Android-exported knowledge bundle from those files.
- Do not assume Kotlin tests or APK packaging passed merely because the native AAR or Python ingest tests passed.

### APK build status at handoff creation (2026-11-03)
- Build workflow run **41536147013** (`Build APK`) targets commit `5ab7482b7bc190a3cdddd3a511e6e799b976a262`.
- Its last observed state was **pending**, with no job rows returned yet and no artifacts published.
- The branch HEAD has since moved to `07f0a28b5bb10a67247b4c952a8a7e5da4a946ef` through the automated ingest commit. Therefore run 41536147013 does **not** build the current branch HEAD.
- An older Build APK run **41535345732** was previously observed in progress at the Gradle step `Build signed isolated radar APK`; the last known job ID was `125586362344`. Its current status must be re-queried; do not repeat the old in-progress state as current fact.
- No APK artifact or verified SHA-256 manifest had been confirmed at the time of this handoff.
- The newer build workflow commit `5ab7482...` added checks intended to verify five APK outputs (four ABI splits plus universal), signatures with `apksigner`, package ID with `aapt`, SHA-256 manifest generation, a separate x86_64 artifact, and a verification/checksum artifact. Confirm the actual workflow file and run output before relying on this list.
- A push that updates this handoff may trigger another APK workflow. Track the newest run rather than assuming an older run represents the new HEAD.

## 5. Known limitations / unfinished functionality

These are explicit gaps, not bugs to conceal:
- The current map is a **local-coordinate map plus mini-radar**, not a complete Albion world map with verified terrain, roads, zone names, or global coordinate transforms.
- Zone IDs and global coordinate conversion are not confirmed.
- Inventory item-ID recognition and stack-delta mapping remain **NOT VERIFIED** and require controlled gameplay evidence.
- Player, mob, and resource classification may have candidate patterns, but live in-game recognition is not proven until field-tested.
- Network visibility is limited by encryption and by what the passive tap can observe. Do not promise decryption or hidden server-state extraction.
- The first controlled Android field test has not been confirmed completed.
- No genuine Android-exported knowledge ZIP has yet been confirmed imported into the central database.
- APK availability, installation, overlay behavior, capture stability, packet-loss rate, marker writing, and actual game-event correlation remain to be validated on the user's device.
- Do not state that all requested radar/inventory/map features are complete.

## 6. Historical issues and regression watchlist

### Fixed issues (verify they remain fixed)
- Marker writer error previously observed: `ALBION_TAG_V1 of type java.lang.String cannot be converted to JSONObject`; marker count was zero in that capture. A serialization/marker-writing correction was made later. Re-run marker-write tests and verify non-zero marker output in a field capture before declaring this fixed end-to-end.
- Knowledge importer custom-output path previously risked overwriting the existing central DB; incremental-load behavior was corrected and tested.
- Checksum-mismatch test fixture previously appended a duplicate manifest member; fixture was corrected to rewrite the archive.
- Large ZIP hashing was changed to streaming SHA-256 to avoid memory blowups.
- x86 APK artifact matching was tightened to `*_x86.apk` to avoid accidentally matching x86_64.

### Failures / risks still requiring observation
- Earlier build runs queued or remained pending/in-progress for a long time. This is a CI scheduling/build-duration observation, not proof of a source-code defect.
- Earlier user capture attempts had packet/marker drops and a marker-writer failure; the corrected path still needs a real field retest.
- Never label a historical issue "fixed" solely because code changed; require an appropriate test or field evidence.

## 7. Exact next actions (resume from here)

1. Re-query branch HEAD and recent workflow runs for `albion-pattern-mining`.
2. Inspect current state, jobs, step statuses, logs, and artifacts for runs **41536147013** and **41535345732**. Then inspect the newest Build APK run created after this handoff commit.
3. If the latest APK build fails, capture the failing step and actual log excerpt, diagnose the root cause, make the smallest fix on `albion-pattern-mining`, add/adjust a regression test, and update this file.
4. If it succeeds, verify every expected APK artifact exists; verify package ID and signatures; obtain SHA-256 values from the workflow's verification artifact. Only then provide an APK artifact link.
5. Clearly label the APK as a **field-test build**, not as proof of correct live Albion entity recognition.
6. Follow the controlled procedure in `docs/ALBION_RADAR_ANDROID.md`: install arm64 APK; connect the VPN; open Albion Radar; grant overlay permission; start full capture; check whether the YOU marker follows movement; label at least three ore nodes using the app's Found → Start → Done → Empty flow; label at least two mobs as Seen/Killed; stop capture; inspect packet/drop/writer/marker counts; export the learned-knowledge ZIP; bring that ZIP back for central ingestion; repeat in another zone.
7. After the first real bundle import, inspect the central DB for correct session separation, provenance, checksum validation, and deduplication. Record bundle/session IDs and test results here.
8. Only after real captures support it, expand protocol mappings for player/mob/resource/inventory events and coordinate/zone interpretation.

## 8. Mandatory work-log format for future updates

Append a dated entry below whenever meaningful work is performed. Keep older entries; do not erase failure history. Correct old entries with a note rather than silently rewriting history.

Each entry must include:
- **Starting HEAD / ending HEAD**
- **Files changed**
- **Implemented**
- **Tests run** (exact commands or workflow IDs and pass/fail counts)
- **CI/build/artifacts** (run IDs, status, exact artifact names, SHA-256 if available)
- **Bugs found and fixes**
- **Regressions or possible breakage**
- **Not done / remaining**
- **Next exact action**

If nothing was changed, do not create a fake implementation entry; record only a verified status refresh when useful.

---

## 9. Work log

### 2026-11-03 — Initial durable handoff created
- **Starting HEAD:** `07f0a28b5bb10a67247b4c952a8a7e5da4a946ef` (branch query at start of this documentation change).
- **Ending HEAD:** to be filled after this documentation commit is confirmed.
- **Files changed:** `docs/ALBION_PROJECT_HANDOFF.md` (this file).
- **Implemented:** Persistent project memory, branch guardrails, evidence-level rules, feature inventory, verification ledger, known limitations, historical bug ledger, and resume checklist.
- **Tests run:** No source-code tests run; documentation-only change.
- **CI/build/artifacts:** At the time of inspection, run 41536147013 was pending and had no artifacts; run 41535345732 requires a fresh status check. Ingest run 41536147098 had succeeded with 21 Python tests on parent commit 5ab7482.
- **Bugs found and fixes:** None in source code during this documentation-only task.
- **Regressions or possible breakage:** This commit may trigger a new APK workflow; monitor it. The current pending APK run targets the parent commit, not this new documentation commit.
- **Not done / remaining:** APK build/signature/artifact verification and all Android field tests remain outstanding.
- **Next exact action:** Query the new branch HEAD and latest APK workflow run, then inspect the APK build steps and artifacts.
