# AlbionRadar — Integration Staging Register (2026-11-05)

Repository: `eesanezhad76/v2rayNG`

## Purpose and safety boundary

This register is the current staging map for consolidating the scattered AlbionRadar work. It is an inventory and integration plan, not an authorization to merge, delete, force-push, overwrite, or rewrite history.

**No source files, captures, archives, fixtures, or existing branches were modified by this audit.** A separate staging branch was created from the current Safe Fixes commit so this map can be maintained without changing PR #12.

- Staging branch: `albion-integration-staging-2026-11-05`
- Staging branch starting commit: `c51481253ff4b5b0bd5bd0223a9e6a2674af680a`
- Safe Fixes branch: `albion-safe-fixes-2026-11-05`
- Safe Fixes / PR #12 head at audit: `c51481253ff4b5b0bd5bd0223a9e6a2674af680a`
- Safe Integration base: `albion-safe-integration-2026-11-05` at `a4282483d6a4f40eabbd74aabda8a04e4bf61ae5`
- PR #12 remains open/draft; do not merge without explicit approval.

## 1. Current branch inventory

The following heads were read from GitHub on 2026-11-05. These are point-in-time observations; refresh them before any write.

### Runtime / integration branches

| Branch | Head SHA | Current interpretation |
|---|---|---|
| `albion-safe-fixes-2026-11-05` | `c51481253ff4b5b0bd5bd0223a9e6a2674af680a` | PR #12 safety/runtime fixes; CI still being validated |
| `albion-safe-integration-2026-11-05` | `a4282483d6a4f40eabbd74aabda8a04e4bf61ae5` | integration baseline |
| `albion-reliability-audit-2026-11-05` | `b75011e6ab9d605bd0ae687fea9dfe55353ab930` | reliability/capture audit changes |
| `albion-reliability-runtime-wiring-2026-11-05` | `3e0c139641be7cce12209c5f7c3e5ac0a87ca549` | runtime wiring snapshot; one commit behind reliability audit |
| `albion-zone-forensics-integration-2026-11-05` | `436e7f60eea014977d01c2b570d52d3017691b97` | reliability-audit line plus zone-forensics additions; candidate for detailed review, not yet safe to merge |
| `albion-db-runtime-wiring-2026-11-05` | `a611c8125c91c3d6beb655897dc7e58745ac00fb` | database/runtime wiring snapshot |
| `albion-database-integration-completion` | `2b1097c3cafe2390cdcc522d586c90cab59715fa` | database integration completion snapshot |
| `albion-knowledge-integration-2026-11-04` | `8a5cc3905fc0fb6f621e1d751d9ed3d058eac833` | knowledge integration snapshot |
| `albion-reliability-ux` | `506d24730c51d4debd434ffaf652ad81d482b748` | older large development line; preserve as evidence/source, do not use as a blind merge base |
| `albion-pattern-mining` | `a453c812483f285dcae97d0ad0bc044c0cec11fa` | primary research/data branch |
| `albion-raw-pattern-discovery-2026-11-05` | `c508c2b54eecced896862e401732e2e0ce44ce29` | raw UDP byte-pattern discovery and related additions |

### Zone / decoder / historical branches

| Branch | Head SHA | Current interpretation |
|---|---|---|
| `albion-zone-evidence-phase6` | `8d9d536b173b2e7f410b4b4393c8999da62dd5f4` | zone evidence snapshot |
| `albion-zone-forensics` | `a420346a14b0f7a5261fcc8f28489242f9d48c3d` | earlier zone-forensics work |
| `albion-radar` | `2a34a8473643cb0084057ac535a2cf8bdbdfb391` | historical radar line |
| `albion-radar-fix-2026-11-03` | `53efd9d2815f15b36e00b6d2c7094bce863f79fd` | historical remediation snapshot |
| `albion-radar-phase1-remediation` | `d0f4845a3cf740a0e726ad760b3f16fa4dafce03` | historical phase-1 remediation |
| `albion-decoder-lab` | `77a8c3e2c58761d6bd2d40aa8eebef0cb32068a5` | historical decoder lab |
| `albion-decoder-radar-rebuild` | `77a8c3e2c58761d6bd2d40aa8eebef0cb32068a5` | exact same head as decoder-lab |
| `albion-decoder-radar` | `ac7e90cd29a874ccd367cf7ea126372f8920a96a` | earlier decoder/radar implementation |
| `albion-decoder-radar-ci` | `023705a103ccff3ccb6b59d5bd71a5bfc882252c` | historical CI-only change |
| `albion-decoder-lab-failed-merge-backup` | `0a21332de0bfc2f70c4982eb4af7a35d051b5d8e` | preserve as failed-merge backup |
| `backup/albion-decoder-lab-before-2.2.6` | `f31ba8d3318d929eb1b22e0e94297112d08bf193` | preserve as backup |
| `backup/albion-capture-e3f95d9` | `e3f95d9e03b08a9e31a4d0467a6d8cc224773831` | preserve as backup |
| `backup/albion-radar-2026-10-31` | `e3f95d9e03b08a9e31a4d0467a6d8cc224773831` | exact same head as capture backup |
| `albion-integrity-autosave` | `39a125b9de84a9101e610f33deb663095e8c10c7` | historical checkpoint |
| `albion-v2rayng-2.2.6-port` | `d41804beecec085eb5f774009ac47935cfa03b24` | historical port |

### Safety-prefixed snapshots and other refs

| Branch | Head SHA | Relationship / interpretation |
|---|---|---|
| `safety/database-integration-2026-11-05` | `2b1097c3cafe2390cdcc522d586c90cab59715fa` | exact alias of database-integration-completion |
| `safety/db-runtime-wiring-2026-11-05` | `a611c8125c91c3d6beb655897dc7e58745ac00fb` | exact alias of db-runtime-wiring |
| `safety/integration-after-knowledge-2026-11-05` | `6fa9d10555aa2ab5e36f64ca831f0e4869faaeb0` | integration checkpoint |
| `safety/integration-after-master-2026-11-05` | `dba99f7e8c1421b2483c0f0136ed74537c6b197f` | integration checkpoint |
| `safety/knowledge-2026-11-05` | `8a5cc3905fc0fb6f621e1d751d9ed3d058eac833` | exact alias of knowledge-integration branch |
| `safety/master-2026-11-05` | `ec7ed4278896336b24f8c2152345702687acb30a` | master/archive checkpoint |
| `safety/pattern-mining-2026-11-05` | `e5941f67a0cf8b0651298450611a9e25065add45` | earlier pattern-mining checkpoint; not same head as current pattern branch |
| `safety/reliability-audit-2026-11-05` | `b75011e6ab9d605bd0ae687fea9dfe55353ab930` | exact alias of reliability-audit |
| `safety/reliability-runtime-wiring-2026-11-05` | `3e0c139641be7cce12209c5f7c3e5ac0a87ca549` | exact alias of reliability-runtime-wiring |
| `safety/reliability-ux-2026-11-05` | `506d24730c51d4debd434ffaf652ad81d482b748` | exact alias of reliability-ux |
| `safety/remediation-2026-11-05` | `d0f4845a3cf740a0e726ad760b3f16fa4dafce03` | exact alias of radar-phase1-remediation |
| `safety/zone-evidence-2026-11-05` | `8d9d536b173b2e7f410b4b4393c8999da62dd5f4` | exact alias of zone-evidence-phase6 |
| `safety/zone-forensics-integration-2026-11-05` | `c7e8cb21e8878385e4b7b9fa3db8aa2cd1d9e549` | separate snapshot; not same SHA as zone-forensics-integration |
| `audit/detection-engine-2026-11-04` | `10d3a0a0f6caea55f31ad3d61de4e546b36189a0` | isolated detection-engine changes |
| `master` | `d4542fadfee6d3b40ac3a65053d3b7031c602f16` | historical/default archive line |

## 2. Verified divergence and overlap

All comparisons below are GitHub compare results, not assumptions based on branch names.

### Safe Fixes vs research / feature branches

| Compared head against Safe Fixes | Ahead / behind | Changed files | Added / deleted lines | Interpretation |
|---|---:|---:|---:|---|
| `albion-pattern-mining` | 121 / 36 | 285 | 2,462,182 / 96,542 | major research/data divergence; includes large evidence and asset payloads |
| `albion-raw-pattern-discovery-2026-11-05` | 8 / 46 | 22 | 1,199 / 57 | focused raw-pattern and forensics changes; overlaps reliability/zone files |
| `albion-zone-forensics-integration-2026-11-05` | 7 / 46 | 20 | 919 / 57 | feature line from common ancestor `6fa9d105...`; not fast-forward compatible with Safe Fixes |
| `albion-reliability-ux` | 59 / 1,060 | 15 | 1,208 / 89 | old divergent development line; its historical commits/data are not equivalent to a clean patch set |

### Feature-line relationships

- `albion-reliability-runtime-wiring-2026-11-05` is one commit behind `albion-reliability-audit-2026-11-05` (same ancestry).
- `albion-zone-forensics-integration-2026-11-05` is three commits ahead of reliability-audit and includes nine further changed files in that comparison, including the zone-audit workflow, portal/zone matching analyzer and tests, and related build/knowledge-index adjustments.
- `albion-db-runtime-wiring-2026-11-05` is five commits behind zone-forensics-integration; do not separately replay its commits until ancestry and exact patch equivalence are confirmed.
- `albion-reliability-audit` compared with Safe Fixes has 15 changed files (644 additions, 50 deletions). Shared hot files include build.yml, CaptureAnalyzer, CaptureWorker, LiveKnowledgeStore, ResourcePatternLearner, CoreServiceManager and OverlayService.
- `albion-zone-forensics-integration` compared with Safe Fixes has 20 changed files (919 additions, 57 deletions). Its shared hot files overlap the reliability line and include CaptureAnalyzer, CaptureMarker, CaptureWorker, LiveKnowledgeStore, MarkerQueue, Protocol18Decoder, ResourcePatternLearner, CoreServiceManager and OverlayService.
- `albion-raw-pattern-discovery` compared with Pattern Mining has 8 commits ahead and 131 behind, with 22 changed files (1,199 additions, 57 deletions). It contains the Raw UDP Byte-Pattern Discovery restoration and `AlbionLearningEngine.kt`, plus overlapping reliability/zone files.
- `albion-pattern-mining` compared with Safe Integration has 121 commits ahead and 36 behind, 285 changed files (139 added, 146 modified in the earlier path inventory). Its diff is not a safe copy operation.

### Exact-head aliases found

These pairs have identical commit SHAs at the audit snapshot, so they are duplicate refs, not independent work:
- `albion-decoder-lab` = `albion-decoder-radar-rebuild`
- `backup/albion-capture-e3f95d9` = `backup/albion-radar-2026-10-31`
- `albion-database-integration-completion` = `safety/database-integration-2026-11-05`
- `albion-db-runtime-wiring-2026-11-05` = `safety/db-runtime-wiring-2026-11-05`
- `albion-knowledge-integration-2026-11-04` = `safety/knowledge-2026-11-05`
- `albion-reliability-audit-2026-11-05` = `safety/reliability-audit-2026-11-05`
- `albion-reliability-runtime-wiring-2026-11-05` = `safety/reliability-runtime-wiring-2026-11-05`
- `albion-reliability-ux` = `safety/reliability-ux-2026-11-05`
- `albion-radar-phase1-remediation` = `safety/remediation-2026-11-05`
- `albion-zone-evidence-phase6` = `safety/zone-evidence-2026-11-05`

Identical heads do not imply that the branches can be deleted. Keep every ref until retention and PR dependencies are separately audited.

## 3. Source-of-truth and evidence tiers

Do not treat every file under research as equally authoritative. Preserve source data and separate it from interpretations.

| Tier | Content | Handling |
|---|---|---|
| A — raw evidence | original PCAPs, original ZIP/7z pieces, uploaded capture bundles, original exports | immutable; retain original path, byte size, SHA-256, source/session identity |
| B — normalized evidence | parsed packet/event JSONL, extracted records, forensic inventories | reproducible where possible; retain parent raw hash, parser/tool version and extraction parameters |
| C — derived analysis | CSVs, correlations, timelines, spatial alignment, inferred catalogs, replay outputs | never overwrite raw evidence; regenerate only into versioned outputs and record input hashes |
| D — runtime knowledge | Android assets, learned signatures, TypeID dictionaries, resource indexes | generated from explicitly identified sources; carry manifest, provenance and confidence/unknown labels |
| E — tests and fixtures | unit tests, synthetic fixtures, hand-curated examples, replay baselines | label as synthetic / curated / observed; do not count fixtures as independent field captures |
| F — documentation | handoffs, audit reports, branch registers, status snapshots | date and identify the commit/data snapshot; old reports remain historical, not current truth |

### Weak or early test data

Weak early samples must not be deleted just because later data is stronger. They must be retained and reclassified:
- preserve the original sample and its original hash;
- mark sample quality, capture completeness, labeling confidence and known limitations;
- distinguish a negative result from missing/low-quality evidence;
- do not count byte-identical captures as independent samples;
- do not merge runtime/entity IDs across independent sessions without a verified identity key;
- keep unresolved or conflicting observations visible;
- only promote a decoder/rule/runtime fact after a stated evidence threshold and regression test;
- if a derived output is superseded, retain the old output and record the replacement's input hashes and generator version.

The existing Pattern Mining guide explicitly says its 0–100 evidence score is a heuristic, not a calibrated probability or proof of protocol semantics. It also requires inspection of underlying PCAP/JSONL before promoting a candidate to a production decoder rule. Preserve that constraint.

## 4. Data layout to normalize

Current Pattern Mining tree has distinct areas that should remain logically separate:
- `research/inbox/`: source bundles and archive pieces.
- `research/database/`: consolidated databases and manifests.
- `research/analysis/`: derived reports, CSVs, JSON inventories and replay baseline.
- `research/analysis/replay/`: replay artifacts.
- `research/tools/`: deterministic generators, ingest and validation tools.
- `research/tests/`: tests for the research pipeline and generated outputs.
- `V2rayNG/app/src/main/assets/`: runtime-consumed assets.
- `docs/`: narrative analysis, handoff, operational procedures and historical phase reports.

Do not move or rename these directories during the current staging phase. First produce a path/hash inventory and dependency graph.

## 5. Documentation conflicts requiring reconciliation

The following documents in the zone-forensics integration snapshot still encode an older workflow:
- `research/BRANCH_REGISTER.md` says `albion-pattern-mining` is the only active working branch and records a 2026-11-02 branch snapshot.
- `docs/ALBION_PROJECT_HANDOFF.md` instructs future sessions to always work on `albion-pattern-mining`.
- `research/PROJECT_STATUS.md` contains dated 2026-11-03 data/test/build results; these are historical evidence, not current CI status.
- `docs/ALBION_PATTERN_MINING.md` correctly cautions that the evidence score is heuristic and candidates need PCAP/JSONL review before production promotion.
- `docs/ALBION_ZONE_EVIDENCE_PHASE6_2026-11-04.md` explicitly says no automatic zone-transition semantics are enabled and requires independent marked arrivals plus a same-zone control before describing a candidate.

Do not silently edit historical status reports. Add a new current handoff/status record and mark old instructions as superseded only after verifying their content and dependencies.

## 6. Proposed safe integration sequence

1. Finish and record PR #12 / Safe Integration validation. No merge without explicit approval.
2. Freeze a snapshot of every branch head and create a commit-level register.
3. Complete a path-level diff inventory for Pattern Mining and Raw Pattern Discovery, including binary files, archive hashes, manifests, and generated assets.
4. Reconcile the reliability/DB/zone feature line by commit ancestry. Start with the zone-forensics integration line because it is three commits beyond reliability-audit, but review its patch against Safe Fixes file by file; do not merge it wholesale.
5. Extract independent changes from Raw Pattern Discovery and Pattern Mining. Keep raw evidence, derived research, runtime code, and test fixtures as separate integration batches.
6. Validate all archive parts and manifests before any archive relocation or deduplication.
7. Prepare a new authoritative handoff and branch register with links to preserved historical documents.
8. Integrate in small logical commits on a dedicated staging branch; after each batch run relevant tests and record old/new blob SHA, source/destination branch, commit SHA and CI evidence.
9. Only after staging validation, propose a PR to the intended integration base. No destructive cleanup until the integrated result and backups are verified.

## 7. Immediate next tasks

- [ ] Poll CI run `42080576281` for commit `c51481253ff4b5b0bd5bd0223a9e6a2674af680a`; record final unit tests, emulator, PCAP replay, Python checks, APK artifacts and readiness report separately.
- [ ] Record exact unique commit SHAs/messages for Pattern Mining and Raw Pattern Discovery.
- [ ] Produce file-by-file overlap matrix for the hot runtime files across Safe Fixes, reliability-audit, zone-forensics integration, DB runtime wiring, Raw Pattern Discovery and Pattern Mining.
- [ ] Hash and classify every raw archive piece and compare against all manifests; validate archive integrity before interpreting extracted contents.
- [ ] Audit the older decoder/backup branches for unique changes before classifying them as archival-only.

## 8. Change log for this staging document

- Operation: create new integration staging branch and add this register; no existing branch or file changed.
- Repository: `eesanezhad76/v2rayNG`
- Source branch / commit: `albion-safe-fixes-2026-11-05` / `c51481253ff4b5b0bd5bd0223a9e6a2674af680a`
- Destination branch: `albion-integration-staging-2026-11-05`
- File: `docs/ALBION_INTEGRATION_STAGING_2026-11-05.md`
- Reason: create a dated, evidence-backed branch/data integration map without touching PR #12 or source evidence.
- Blob SHA and resulting commit SHA: fill from the successful GitHub create-file response and verify against branch HEAD before claiming completion.


## 9. Hot-file blob comparison (exact Git blob identity)

The following blob SHAs were read from recursive Git trees on 2026-11-05. Equal SHA means byte-identical file content; different SHA means the file needs a semantic diff, not a blind copy.

| File | Safe Fixes | Reliability Audit | Zone Forensics Integration | DB Runtime Wiring | Raw Pattern Discovery | Pattern Mining |
|---|---|---|---|---|---|---|
| `build.yml` | `ab23d4ab...` | `5bdc09bc...` | `c1434256...` | `43ff15fd...` | `1c5d8103...` | `0fb8f09f...` |
| `CaptureAnalyzer.kt` | `5f082357...` | `9abfd959...` | `f40cf7ac...` | `cae34340...` | `f40cf7ac...` | `24cd47a5...` |
| `CaptureMarker.kt` | `8677f41f...` | `d68dae44...` | `d68dae44...` | `8677f41f...` | `d68dae44...` | `8677f41f...` |
| `CaptureWorker.kt` | `246cfed2...` | `d708bc35...` | `d708bc35...` | `4fa3e003...` | `d708bc35...` | `4fa3e003...` |
| `EventStateTracker.kt` | absent | `9ca2eb10...` | `9ca2eb10...` | absent | `9ca2eb10...` | absent |
| `LiveKnowledgeStore.kt` | `28103291...` | `6f1091c8...` | `6f1091c8...` | `6f1091c8...` | `6f1091c8...` | `acdba321...` |
| `MarkerQueue.kt` | absent | `0cfdb6e9...` | `0cfdb6e9...` | absent | `0cfdb6e9...` | absent |
| `Protocol18Decoder.kt` | `be8f7450...` | `63cb6d47...` | `63cb6d47...` | `be8f7450...` | `63cb6d47...` | `be8f7450...` |
| `ResourcePatternLearner.kt` | `7ca47966...` | `3b4dff71...` | `3b4dff71...` | `3b4dff71...` | `3b4dff71...` | `7ca47966...` |
| `CoreServiceManager.kt` | `9a8a42ac...` | `c2c90474...` | `c2c90474...` | `9a8a42ac...` | `c2c90474...` | `9a8a42ac...` |
| `OverlayService.kt` | `264a12e5...` | `536cad5a...` | `536cad5a...` | `a2d9b8c6...` | `536cad5a...` | `6677464d...` |
| `generate_research_knowledge_index.py` | absent | `5c7e43cd...` | `cbd15b74...` | `5c7e43cd...` | `cbd15b74...` | absent |
| `validate_runtime_knowledge_assets.py` | `2b10a440...` | `53d0f7d3...` | `a1eff654...` | `53d0f7d3...` | `a1eff654...` | `6f983e2b...` |

The table intentionally abbreviates blob SHAs for readability. Re-fetch full blob SHAs from the Git tree immediately before any Git operation or change log; do not use abbreviations as identifiers.

### Immediate implications

1. Safe Fixes has its own newer safety versions of CaptureWorker, LiveKnowledgeStore, Protocol18Decoder, CoreServiceManager and OverlayService; it does **not** contain EventStateTracker.kt, MarkerQueue.kt or generate_research_knowledge_index.py.
2. Reliability Audit and Zone Forensics Integration have byte-identical blobs for the core reliability files listed above. Zone Forensics Integration adds later zone-specific changes and a different knowledge-index generator/validator pair.
3. Raw Pattern Discovery shares the same blobs as Zone Forensics Integration for CaptureAnalyzer, CaptureMarker, CaptureWorker, EventStateTracker, LiveKnowledgeStore, MarkerQueue, Protocol18Decoder, ResourcePatternLearner, CoreServiceManager and OverlayService. Its build workflow and knowledge-index generator/validator differ, and it adds its own `AlbionLearningEngine.kt` / raw pattern work.
4. DB Runtime Wiring is a mixed snapshot: several blobs match Safe Fixes, while LiveKnowledgeStore and ResourcePatternLearner match the reliability/zone line. Its CaptureWorker and OverlayService have separate blobs. It must not be assumed to be a complete superset of either line.
5. Pattern Mining has older/different CaptureAnalyzer, LiveKnowledgeStore and OverlayService blobs, while several other files match Safe Fixes. Its research payload is substantial and must be handled as a separate evidence/data integration batch.

These identities show why a single branch merge or file-copy operation would be unsafe. The next step is a semantic three-way diff per hot file, with the common ancestor and intended behavior recorded before any transfer.

## 10. Additional change log

- Operation: append exact hot-file blob identity matrix and integration implications.
- Repository: `eesanezhad76/v2rayNG`
- Source file blob SHA before update: `1e6ae5529867ba15afd35a1f1443682d1dec1a7a`
- Destination branch: `albion-integration-staging-2026-11-05`
- No application source, test, capture, archive or pre-existing branch changed.
- Resulting commit and new blob SHA: verify from the update response and branch tree.


## 11. Confirmed Raw Pattern Discovery ancestry

A direct GitHub comparison confirms:

- Base: `albion-zone-forensics-integration-2026-11-05` at `436e7f60eea014977d01c2b570d52d3017691b97`
- Head: `albion-raw-pattern-discovery-2026-11-05` at `c508c2b54eecced896862e401732e2e0ce44ce29`
- Status: `ahead`; 1 commit ahead, 0 behind; merge base is exactly the zone-forensics-integration head.
- The only additional commit is `c508c2b54eecced896862e401732e2e0ce44ce29`: `feat(albion): restore raw UDP byte-pattern discovery alongside session pattern mining`.
- That one commit changes exactly 3 files:
  - `.github/workflows/build.yml` (15 additions, 14 deletions)
  - `V2rayNG/app/src/main/java/com/v2ray/ang/ui/albion/AlbionLearningEngine.kt` (new, 248 lines)
  - `V2rayNG/app/src/main/java/com/v2ray/ang/ui/albion/AlbionRadarActivity.kt` (31 additions)

The Raw Pattern Discovery branch therefore contains the complete seven-commit feature chain present in Zone Forensics Integration (research-index wiring, DB/runtime wiring, marker FIFO/network handover, capture audit, package/zone identity, unresolved Family 28 handling, and offline analyzer separation) plus the single raw UDP byte-pattern discovery commit. This means those seven commits should be reviewed as one ancestry chain; do not separately replay the same reliability, DB, or zone commits from their alias/checkpoint branches.

This does **not** mean Raw Pattern Discovery can be merged wholesale into Safe Fixes: the two lines diverged at `6fa9d10555aa2ab5e36f64ca831f0e4869faaeb0`, while Safe Fixes has 46 commits beyond that common ancestor. Resolve the shared runtime files semantically first.

## 12. Change log — ancestry clarification

- Operation: append confirmed ancestry and exact one-commit delta for Raw Pattern Discovery.
- Repository: `eesanezhad76/v2rayNG`
- Source file blob SHA before update: `6d5c2d391440645aed60832159e3a35886d669fe`
- Destination branch: `albion-integration-staging-2026-11-05`
- No application source, test, capture, archive or pre-existing branch changed.
- Resulting commit and new blob SHA: verify from the update response and branch tree.


## 13. Pattern Mining evidence-tree inventory

A recursive Git tree inventory of `albion-pattern-mining` at `a453c812483f285dcae97d0ad0bc044c0cec11fa` returned the following blob counts and stored byte totals:

| Area | Files | Stored bytes | Interpretation |
|---|---:|---:|---|
| `research/inbox/` | 59 | 527,340,479 | raw uploads and archive pieces |
| `research/database/` | 136 | 155,060,739 | consolidated databases, catalogs and manifests |
| `research/analysis/` | 320 | 634,561,104 | derived analysis and replay artifacts |
| `V2rayNG/app/src/main/assets/` | 19 | 5,847,824 | runtime-consumed assets |

Within `research/inbox/`:
- 45 split archive parts named `archiveNew.7z.t0.001` through `.045` exist with no missing sequence number.
- Parts `.001`–`.044` are each 10,485,760 bytes; part `.045` is 3,791,217 bytes.
- Total split archive bytes: 465,164,657.
- Parts `.026`–`.031` have exact Git blob SHA matches with the six root-level `master/archiveNew.7z.t0.026`–`.031` files. These six Master files are duplicates of the corresponding Pattern Mining parts, not six additional unique archive parts.
- The inbox contains 14 additional files beyond those 45 split parts.
- A dedicated raw split-7z SHA-256 manifest has not yet been verified. The known complete-database manifest describes consolidated ZIP/PCAP/JSONL inputs, not necessarily the raw 45-part 7z set.
- Archive byte continuity and sequence completeness are established from the Git tree; **7z integrity and exact extracted-content/manifest equivalence are not yet verified**.

The 285-file Pattern Mining vs Safe Integration diff groups exactly as follows:
- `research/analysis/`: 130 files (53 added, 77 modified)
- `research/database/`: 78 files (34 added, 44 modified)
- `research/inbox/`: 46 files (45 added, 1 modified)
- `V2rayNG/app/`: 19 files (5 added, 14 modified)
- `research/tools/`: 6 files (1 added, 5 modified)
- `research/tests/`: 4 files (1 added, 3 modified)
- `.github/`: 1 modified file
- `research/other/`: 1 modified file

This confirms that the Pattern Mining delta is not just source code: it is primarily a large research/evidence and derived-output payload. Keep these data layers out of runtime-only PRs unless there is a specific, documented dependency.

## 14. Change log — evidence tree inventory

- Operation: append recursive tree inventory and archive-sequence findings.
- Repository: `eesanezhad76/v2rayNG`
- Source file blob SHA before update: `e47ce69c552bb7aba2627d4b8919683934d569f9`
- Destination branch: `albion-integration-staging-2026-11-05`
- No raw archive, database, analysis, runtime asset, or pre-existing branch changed.
- Resulting commit and new blob SHA: verify from the update response and branch tree.
