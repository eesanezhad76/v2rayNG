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
- A fresh status query on 2026-11-03 showed this run **completed / cancelled**; it produced no confirmed artifacts.
- The branch HEAD has since moved to `07f0a28b5bb10a67247b4c952a8a7e5da4a946ef` through the automated ingest commit. Therefore run 41536147013 does **not** build the current branch HEAD.
- Older Build APK run **41535345732** was re-queried on 2026-11-03 and remains **in_progress** (head `aca1a1959cfa054e8096cb89486e9b6818b25ed8`). It was previously observed at the Gradle step `Build signed isolated radar APK`; last known job ID `125586362344`. Its job steps and artifacts still need inspection.
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
- **Ending HEAD:** `77456ba5d9814b80849b1770945fe1ee677f532a` (this handoff commit).
- **Files changed:** `docs/ALBION_PROJECT_HANDOFF.md` (this file).
- **Implemented:** Persistent project memory, branch guardrails, evidence-level rules, feature inventory, verification ledger, known limitations, historical bug ledger, and resume checklist.
- **Tests run:** No source-code tests run; documentation-only change.
- **CI/build/artifacts:** After the handoff commit, run 41536147013 was confirmed cancelled (no artifacts). Run 41535345732 was still in progress on older commit `aca1a1959cfa054e8096cb89486e9b6818b25ed8`. Ingest run 41536147098 succeeded with 21 Python tests on commit `5ab7482`.
- **Bugs found and fixes:** None in source code during this documentation-only task.
- **Regressions or possible breakage:** This commit may trigger a new APK workflow; monitor it. The current pending APK run targets the parent commit, not this new documentation commit.
- **Not done / remaining:** APK build/signature/artifact verification and all Android field tests remain outstanding.
- **Next exact action:** Inspect job steps/logs/artifacts for run 41535345732; then arrange a fresh APK build for current HEAD `77456ba5d9814b80849b1770945fe1ee677f532a` if no valid current-head build exists.

### 2026-11-03 — Status refresh after handoff commit
- **Starting HEAD:** `77456ba5d9814b80849b1770945fe1ee677f532a`.
- **Ending HEAD:** this status-refresh documentation commit (the authoritative exact SHA is the branch HEAD; this entry intentionally avoids a self-referential hash).
- **Files changed:** `docs/ALBION_PROJECT_HANDOFF.md`.
- **Implemented:** Corrected the initial status snapshot using live GitHub queries: run 41536147013 is cancelled (not pending); run 41535345732 is still in progress on older commit `aca1a1959cfa054e8096cb89486e9b6818b25ed8`.
- **Tests run:** None; status/documentation refresh only.
- **CI/build/artifacts:** No artifacts confirmed for run 41536147013. The branch currently points to the handoff commit; no newer Build APK run appeared in the latest-runs query.
- **Bugs found and fixes:** None.
- **Regressions or possible breakage:** The older build may still be occupying a runner; verify before triggering another build.
- **Not done / remaining:** Inspect run 41535345732 steps/logs/artifacts and build the current branch HEAD.
- **Next exact action:** Query job steps and artifacts for run 41535345732, then trigger/verify a build of the current HEAD if needed.


## 2026-11-03 — Overlay simplification and autonomous-learning boundary

### Implemented in commit `c4171ee269de3a038494402365dbbd720d02d8fa`
- Replaced cyclic activity/tier/enchantment controls in `AlbionOverlayService.kt` with direct horizontal choice rows.
- Row 1 selects activity directly: Ore, Wood, Stone, Fiber, Hide, Mobs, PvP, Move, Other.
- Gathering activities show all tiers T2–T8 in row 2 and Normal/E1/E2/E3 in row 3. Each value is directly tappable; no cycling backward from T8 to T3.
- Gathering actions are reduced to Start/Stop. Start writes the existing `target_found` marker and Stop writes `gathering_completed`; this preserves compatibility with the current PatternMiner's single-target compatibility path. Empty is removed.
- Mobs show only Start/Killed. Movement has its own activity choice with Start movement test/Stop movement test.
- PvP and Other retain simple Start/Stop actions.
- Overlay remains local-only and does not alter game packets.
- This change is source-committed only until the current Android CI build completes. It is not device-tested yet.

### Autonomous learning: exact capability boundary
- Packet capture and `AlbionLiveKnowledgeStore.observe(...)` already run automatically while the daemon-owned capture is active; user does not need to tag every resource or mob for the recognized mapper observations to be recorded.
- Current entity mapper still emits only known event mappings. Unknown events are not semantically classified by magic.
- Current `AlbionPatternMiner` is still primarily offline and its robust resource correlation is based on capture markers. The overlay simplification keeps optional Start/Stop labels; it does NOT yet implement a validated fully unsupervised semantic classifier.
- Never auto-promote an unknown packet signature to a named resource/mob. Future autonomous discovery must create an Unknown/Candidate record with evidence and confidence, keep session IDs isolated, and only promote after independent repeated evidence or optional user confirmation.
- Capture Start remains required once per session unless the daemon snapshot says capture is already running. Overlay start is separate and only needed when manual labels are desired. The overlay is not required for automatic packet capture.
- Field workflow after install: connect V2RayNG, start full capture once, play normally; optionally open overlay and tap activity + tier + enchant + Start/Stop for a high-quality labeled sample; stop capture at end and export knowledge ZIP for central ingest.
- Do not claim live semantic recognition of every enchanted resource or mob until verified against controlled Android gameplay captures.

### Verification and next actions
- Commit: `c4171ee269de3a038494402365dbbd720d02d8fa`.
- Source change: `AlbionOverlayService.kt` only.
- No Kotlin test, Android build, APK artifact, checksum, or device test is confirmed by this commit yet.
- Next: inspect Build APK workflow run triggered by this commit; verify Gradle success, signing, package ID, checksums and artifacts. Then inspect overlay runtime on Poco F5 for row clipping/touchability/scroll behavior.
- Next autonomous-learning engineering task: design and test a bounded candidate-signature learner that runs from mapped observations and protocol evidence without marker dependency; keep all unknown semantics as candidates and add tests for false merges, tier/enchantment variation, repeated IDs across sessions, despawn/depletion, and missing coordinates before wiring it into map display.


## 2026-11-03 — Online autonomous signature learner and unified marker feedback

### Implementation commits
- Autonomous learner: `6950f9cb80c43fd181d50daea89ea67534a09540`
- Capture-worker wiring: `71f59da66bd0bb28fc98b2ebe0b756c3f8fe11b1`
- Knowledge ZIP export: `50cbabe1fd91487d84b5b3cc306626c9b008b148`
- Central importer: `bd03f26fa6cae652e4d058f0d28b04e4735bcfaa`
- Importer merge/backward-compatibility fixes: `c55c4d590fe6bf1e0667f6056dfc6e0645dafbad`
- Research test: `7ba33db188ada74d0a6b4f28f805a0ea9eae8740`
- Android learner test: `158deeba39e4580bd51de8f14311f18ab529080e`
- Bundle export test: `3428dbf0a379ad269d0621a7c43dd692037c9660`
- Latest learner safety refinement: `31cf099169254bd46065ae8762ed6ed248888678`

### Files changed
- Added `AlbionAutonomousPatternLearner.kt`.
- Updated `AlbionCaptureWorker.kt` to feed every decoded Photon event (including empty-parameter events) to the learner, feed manual markers only after both PCAP and JSONL writes are flushed/synced, flush persistent learner state, and include learner summaries in live status and offline report.
- Updated `AlbionKnowledgeBundle.kt` to export `autonomous-patterns.json` with manifest checksums and counts.
- Updated `research/tools/ingest_knowledge_bundle.py` to validate/import optional autonomous patterns, retain provenance, union capture-session IDs, and avoid inflating cumulative sighting/sample counts. Old schema-version-1 bundles and old central DBs remain supported.
- Updated research README and added `AlbionAutonomousPatternLearnerTest.kt`; extended `AlbionKnowledgeBundleTest.kt` and `test_knowledge_bundle_import.py`.

### Behavior and safeguards
- Learning now runs continuously during ordinary gameplay while full capture is active; it does not require the user to press activity markers for each event.
- It hashes stable observable event signatures from event code + parameter-key/type shape. It records sightings, capture-session provenance, bounded labels, and heuristic evidence status.
- Unknown signatures remain unknown candidates. It does not infer an Ore name, mob species, Tier, Enchantment, or item semantics from a signature alone.
- Optional manual markers add labels to signatures observed around a user-confirmed activity. Labels are associated by activity/Tier/Enchantment and are promoted only to `label_corroborated_candidate` after at least 3 completed samples and evidence from at least 2 capture sessions. This is still a candidate, not proof of protocol semantics.
- Pre-roll is bounded to 3 seconds; active label windows are bounded to 15 minutes; recent events are capped at 2048, signature records at 5000, exported patterns at 5000, and label signatures at 512 per sample.
- Persistent writes are periodic/atomic and failures are swallowed so the capture loop is not intentionally interrupted.
- This learner is deliberately in-process Kotlin; no external ML runtime/model was added. With no verified labeled dataset, an external ML model would risk false classifications and increase APK size/complexity without justified benefit.
- Manual markers and autonomous learning now share the same capture session, event stream, local persistent cache, ZIP schema and central importer. Markers improve correlation; they are not required for background signature collection.

### Tests and CI
- Added tests for repeated labeled signatures across two sessions, unknown empty-parameter events, autonomous ZIP member/checksum manifest presence, and cumulative importer deduplication.
- Tests have been added but have **not yet been confirmed passing** in CI.
- Build APK workflow run `41541531789` was pending on older HEAD `3e24f34ce7d8d506c28180c526fb3aa33a25fbe4` when last checked; later source/test commits may supersede it. Re-query the newest run after this handoff update.
- Albion capture ingest workflow run `41541531883` was pending on `3e24f34...`; another ingest run `41541520142` was in progress on `592d5f9...`. Re-query jobs and results; the ingest workflow may commit derived research files.
- No APK, checksum, successful Gradle test, successful Python test run, or device test is confirmed yet.

### Remaining risks / exact next actions
1. Re-query branch HEAD, latest Build APK and Albion capture ingest runs; inspect job steps/logs/artifacts.
2. Confirm Kotlin compilation and all `testPlaystoreDebugUnitTest` tests, especially the new learner and bundle tests.
3. Confirm Python `unittest discover -s research/tests -p 'test_*.py' -v` passes, including the new cumulative autonomous-signature test.
4. If CI fails, fix the smallest root cause and add regression coverage; do not declare this APK ready until CI packaging, signature, package ID and SHA-256 checks pass.
5. After a successful build, field-test on Poco F5: start capture once, play without markers for a while, inspect automatic signature counts, then use occasional Start/Stop labels for the same activity/Tier/Enchantment across at least three samples and two capture sessions.
6. Export the learned database ZIP and verify that `autonomous-patterns.json` is present, checksummed, and ingested into `research/database/albion-knowledge-bundle-import.json`.
7. Compare candidate signatures against controlled PCAP evidence before adding semantic event mappings or promoting map labels. No full automatic recognition of every resource/mob is verified.

## 2026-11-03 continuation — current verified work

Branch: `albion-pattern-mining`  
Latest observed HEAD: `a74b3bea3cc04241209a18bc0ea0a39a8063d906`.

Changes made after the previous handoff snapshot:
- `3bdff0dad0cfb9f83bac4c6bb7e015582886c45d`: repaired malformed Kotlin knowledge-bundle test source.
- `a8d5a5c3603cd8a7509975eb21578f92ebbbfe2b`: added a dedicated `Troll` activity choice to the overlay. Troll shares the mob Start/Killed actions, while the existing tier (T2–T8) and enchantment (Normal/E1/E2/E3) selectors remain visible because only Mobs/PvP/Move/Other hide those rows. This is UI tagging only; it does not prove Troll event semantics.
- `a74b3bea3cc04241209a18bc0ea0a39a8063d906`: removed a duplicate union operation for autonomous capture-session provenance in the central knowledge importer. No intended data behavior change.

Verification at this update:
- The current branch head is `a74b3bea3cc04241209a18bc0ea0a39a8063d906`.
- Build APK run `41578331030` targets the older commit `70f0485a6389bbf32c040f113ce14fc4a8c9fc21`; it is still running and had reached “Run offline PCAP replay and audit” at the last status query. It is NOT a build of current HEAD.
- Run `41578346841` targets `3bdff0dad0cfb9f83bac4c6bb7e015582886c45d` and was queued/pending at the last status query; it does not include the later Troll/importer commits.
- No APK artifacts, SHA-256 manifest, final readiness report, or successful latest-HEAD CI result have been confirmed yet.
- The importer test file `research/tests/test_knowledge_bundle_import.py` includes tests for cumulative export deduplication, cross-session entity separation, autonomous signature/session provenance merge without count inflation, and checksum rejection. Test execution for the latest HEAD is still pending CI evidence.
- The overlay currently offers T2–T8, not T1. Albion gathering resource tiers start at T2 in this UI design. Verify the actual game/UI usability on Poco F5; source inspection is not a device test.
- The structural autonomous learner still cannot assign a semantic resource/mob name by itself. Do not dynamically promote an unknown event signature into Ore/Mob/Troll without independently validated field evidence and a mapping test.
- The central importer only merges ZIPs after a user exports and submits them; it is not a live server/database update during gameplay.

Next required work:
1. Query the new Build APK run triggered by latest HEAD and verify every step, artifacts, package ID, signatures, ABI and SHA-256 manifest.
2. Inspect readiness report and offline replay audit; fix any failures and rerun.
3. Add/verify regression coverage for overlay marker labels and learner correlation (including Troll|tier|enchantment).
4. Review dynamic mapping architecture. Keep unknown structural signatures as candidates until validated against controlled PCAPs; then add explicit mapping + fixture tests.
5. Confirm central-ingest workflow handles the latest knowledge-bundle schema and never inflates cumulative counters.
6. Device test on Poco F5: VPN routing, capture start/stop, overlay permissions/layout, marker FIFO, export ZIP, app restart persistence, battery/performance, and real gameplay correlation.
7. Only after successful latest-HEAD CI and device test, label APK “ready for field testing”; only after controlled live captures should semantic detection be marked verified.

## Verification update — 2026-11-03 04:37 UTC

Latest source branch HEAD at this update: `c3abb80b63af56b511dd0508796609de27046a8e`.

Additional UI correctness fix:
- `c3abb80b63af56b511dd0508796609de27046a8e`: manual markers now include tier/enchantment only for gathering activities (Ore/Wood/Stone/Fiber/Hide) and Troll. Mobs, PvP, Move, and Other no longer inherit the default T3/Normal metadata. This prevents false labels such as `Mobs|T3|Normal` in the autonomous learner.
- Troll remains a separate activity with tier/enchantment selectors and mob-style Start/Killed markers.

CI evidence:
- Build run `41578331030` completed successfully on older source commit `70f0485a6389bbf32c040f113ce14fc4a8c9fc21`.
- Confirmed successful steps on that run: isolated radar APK build, APK verification/SHA manifest generation, offline PCAP replay audit, Python analysis/marker FIFO/readiness tests, readiness report generation, and uploads of arm64-v8a, armeabi-v7a, x86, x86_64, universal APK, diagnostics, readiness report, and APK verification artifact.
- Artifact metadata includes arm64-v8a artifact ID `13204703625`, universal `13205192170`, x86 `13205192160`, armeabi-v7a `13204648646`, x86_64 `13204409291`, and verification `apk-verification`. These artifacts belong to the older commit above and must not be described as the final APK for current HEAD.
- Albion ingest run `41579031315` completed successfully on `a74b3bea3cc04241209a18bc0ea0a39a8063d906`.
- Build run `41579066893` is in progress on `ebbacc3028c2a1c2b516da3ed9c4606e2f8d80e7`; it does not include the later marker-metadata fix at `c3abb80...`.
- A generated-data-only ingest commit `5c0f72aa773c4462b1ecbf8c912e668136d5c52a` was subsequently observed on the branch. It changes derived evidence/report metadata, not the Android source.
- No CI run has yet been confirmed for the latest source commit `c3abb80...`; check Actions after the next push/build trigger.

Remaining verification:
- Inspect the successful build's readiness report and APK verification manifest; artifact metadata alone does not disclose the actual APK SHA-256 values or report contents.
- Obtain a successful APK workflow for source commit `c3abb80...` or later.
- Add automated marker-payload regression tests if a suitable test seam exists; otherwise validate on device.
- Device and real Albion gameplay verification remain outstanding. No semantic mapping should be called live-verified without controlled PCAP evidence.

## 2026-11-03 — continued completion work

Latest Android/source test commit before this handoff update:
- `cf76ff7899bd28b5ea2ed97b89bd7a9adba07fbf`: added regression coverage in `AlbionEntityEventMapperTest` for typed harvestable event 40 (entity/type/family/tier/enchant/coordinates/size), mob event 123 (entity/type/coordinates/name/enchantment), and the invariant that unknown event 999 remains unmapped.
- This test reinforces the conservative boundary: known typed events flow into the existing live knowledge store; unknown signatures are not automatically given semantic entity names.

Current verified architecture:
- `AlbionCaptureWorker` feeds decoded event messages to the autonomous structural learner and separately maps known messages through `AlbionEntityEventMapper` into `AlbionLiveKnowledgeStore`.
- The live store keeps raw evidence, evidence conflicts, classification and evidence grade; resource catalog conflicts downgrade classification rather than overwriting raw packet fields.
- The autonomous learner is currently an evidence inventory and marker-correlation mechanism. It is intentionally NOT allowed to rewrite the typed mapper's semantic classifications automatically. Its marker-correlated labels are candidate evidence only; field validation is required before adding a new semantic mapping.
- This is deliberate, not an omitted “AI” step: blindly promoting co-occurring packet signatures would risk false live labels.

Latest CI status observed:
- Build APK run `41579562706` targets `cf76ff7899bd28b5ea2ed97b89bd7a9adba07fbf` and was pending at the last query.
- Build run `41579211562` on `d37fae697749aa4ec620ee23b952d6568cdecb7b` was cancelled when newer commits arrived.
- Build run `41579066893` on `ebbacc3028c2a1c2b516da3ed9c4606e2f8d80e7` remained in progress but is stale and does not include subsequent source/test changes.
- Successful older build run `41578331030` and its APK artifacts are documented above; do not use them as proof of the current source APK.
- No successful Build APK run for `cf76ff...` or later has yet been confirmed.

Next:
1. Let run `41579562706` finish and inspect Kotlin test results, offline replay, APK signature/package/ABI/SHA verification and all artifacts.
2. If it fails, fix the exact failing step and rerun.
3. Inspect the readiness report and APK verification artifact contents, not only their artifact names.
4. Perform the on-device checklist and controlled live-game captures; only then promote new mappings.
