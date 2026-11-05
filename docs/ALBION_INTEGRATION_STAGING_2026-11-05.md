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
- New file blob SHA: `1e6ae5529867ba15afd35a1f1443682d1dec1a7a`
- Commit SHA: `ab29239679a2d3ff485b78d68a9cf9befdc696ed`


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
- Resulting commit SHA: `79c55dcaeb1a1b8c2f06675195e47f14c8229786`
- New blob SHA: `6d5c2d391440645aed60832159e3a35886d669fe`


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
- Resulting commit SHA: `29c1dd3ab70de7f267941ff264f1bebf6e52a929`
- New blob SHA: `e47ce69c552bb7aba2627d4b8919683934d569f9`


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
- Resulting commit SHA: `e5774bd251bb4d0d127538e22e55089dc0ba5556`
- New blob SHA: `4974f734dd2b5dee95e9f5ca7a1753e171e3474e`


## 15. CI run 42080576281 — artifact-level verification

The completed run was inspected beyond its green workflow badge.

- Run: `42080576281`; head: `c51481253ff4b5b0bd5bd0223a9e6a2674af680a`
- Overall workflow: `success`; build job: `success`; release-signing job: `skipped` (debug radar build path).
- Gradle unit tests: 282 tests across 47 XML result suites; 0 failures, 0 errors, 0 skipped.
- Python research tests: 40 passed.
- Offline PCAP audit: 13 archives, 32 PCAP members, 31 unique, 1 byte-identical duplicate, 0 errors.
- Android 15 emulator: universal APK installation and launch command completed successfully.
- APKs: arm64-v8a, armeabi-v7a, x86, x86_64 and universal artifacts uploaded; universal APK SHA-256 `1331a58887ccb1f4b7b9862685c8c19ac92e7d0af4fdf122b5b2309eaa2f102b`, 84,735,605 bytes.
- `apksigner`: all five debug APKs report `Verifies`; one V2 signer; certificate SHA-256 `8a3a5bbc02af4f04a06703737b5cb2665cec0e703688630a47874d8d1c2274ca`. This is the Android Debug certificate, not a release-key signature.
- Readiness report: `NOT_READY_FOR_FIELD_TEST`, because deterministic replay is `NOT_VERIFIED` (31 unique captures, 0 stable, 31 review-required), live capture and live overlay rendering are unverified, and Item ID / Stack Delta semantics still require live evidence.
- APK metadata has a separate `signing_block=PRESENT_NOT_CRYPTOGRAPHICALLY_VERIFIED` field even though the dedicated `apksigner` check passes. Keep these as separate checks; do not report the APK signing block parser field as cryptographic verification.

Artifacts retained by GitHub Actions:
- `albion-readiness-report` ID `13442404948`
- `apk-verification` ID `13443545610`
- `unit-test-diagnostics` ID `13443430727`
- APK artifacts: `13443420818` (arm64), `13443510607` (armeabi-v7a), `13442064157` (x86), `13442198967` (x86_64), `13441514444` (universal).

This completes CI validation for the Safe Fixes head at that commit. It does not authorize PR merge and does not establish field readiness.

## 16. Pattern Mining — complete unique commit inventory

GitHub compare result:
- Base/common ancestor: `a4282483d6a4f40eabbd74aabda8a04e4bf61ae5`
- Head: `a453c812483f285dcae97d0ad0bc044c0cec11fa`
- Ahead: 121; behind: 0; 121 commits returned.
- Group counts: 25 generic evidence-upload commits, 8 capture-ingest/derived-data commits, 8 CI/archive-pipeline commits, 1 data-provenance commit, 15 feature commits, 42 runtime/analysis fixes, 21 test commits, 1 refactor commit. Total: 121.
- The generic upload commits mostly add the 45-piece `archiveNew.7z.t0.*` sequence plus `AlbionRadar.zip`. One upload commit `c6d02e12de6b18736a5e94fb0d6f83491bcab667` returned no file list from the commit endpoint and remains to be checked via its raw Git tree/diff; do not infer it is empty.

The following list records every unique commit SHA and subject, grouped by purpose. It is the authoritative commit-level checklist for later transfer review:

### Evidence upload commits (25)

- `0b021c0fe465f5427e9f8c44a5334277b7b659a2` — Add files via upload
- `2f6ea6adb097af8b36780177ffe53b60422b7391` — Add files via upload
- `e7ddae713e69368b745fca13f545969083729eea` — Add files via upload
- `020406f32415a7e7ba95c087961e8b27eb2ebc57` — Add files via upload
- `f732c050b34f6ee592192c2143f884d0cd97d8ec` — Add files via upload
- `37f06969af6a841f1c6f55fbe4b81f528cc2e80c` — Add files via upload
- `1d1c0346058ade7849e29104bb5fb86a05572720` — Add files via upload
- `043072d92226ddc479e1ca940604b8ee2ffb9783` — Add files via upload
- `4ec5f5764cfd2a26eb32f90ad3922814af7cd65d` — Add files via upload
- `5851bc3285a815fd453737fd386c06f41cf7b21e` — Add files via upload
- `b55fac329d9f1cc98692ecc120d9e5344e584526` — Add files via upload
- `de7e06edd09f27febff3ab543c28cc03f5dc1622` — Add files via upload
- `be0ae49dcbd4f867584ec32340f8bb54e11ce8ab` — Add files via upload
- `c6d02e12de6b18736a5e94fb0d6f83491bcab667` — Add files via upload
- `5a804688ed7283104bb49cf398b3f8c3b37611b9` — Add files via upload
- `210e7058852313f20e2fdb1448283bf965c52248` — Add files via upload
- `642d10e2705e2947fe2673eb5a5ead2ea6089520` — Add files via upload
- `68209c46891aa329d08054d2a8afb290a25b5e75` — Add files via upload
- `791c16fbec9a47f1034be66483dbf8c822bbaeb4` — Add files via upload
- `e59e10d114aac757cd9d3f227156539499d00f45` — Add files via upload
- `94dc7e9d40432fedabf831bd8a80c089303c40c8` — Add files via upload
- `86818f315d3ff0740cf215d8dbbb1d35397e1fc8` — Add files via upload
- `430fbd9d05740de854c932a4e75c1345f950290a` — Add files via upload
- `7994092a63dd036f9f8b07c73688827d267336c6` — Add files via upload
- `68fe0a1d66ecac13cf78f053f2b037970280f735` — Add files via upload

### Capture ingest / derived data commits (8)

- `a21780a681ae54d0f487578c363f718ff4959df9` — data(albion): ingest uploaded capture evidence
- `58911e8e565352b906c22578d2c600b7b12e4a20` — data(albion): ingest uploaded capture evidence
- `7a56dd17cd68122cadea0d59ac10ef3e0b9a17c0` — data(albion): ingest uploaded capture evidence
- `a7dc5bc7f95be190dd5fd9cbe1cdfe17caf5ac02` — data(albion): ingest uploaded capture evidence
- `62cc97b4d0f14aa43d6bda85fda7d11965ba90c8` — data(albion): ingest uploaded capture evidence
- `f688d18c2031b223aa99bb30b7986b4ae1f23437` — data(albion): ingest uploaded capture evidence
- `65790ef07e7a5ce0b21318def8afe48975035cf7` — data(albion): ingest uploaded capture evidence
- `6ce9402ab480d02d081ce0a3b17073039f0d20f8` — data(albion): ingest uploaded capture evidence

### CI / archive pipeline commits (8)

- `f6d5996c933b67756da4ab25f7552ce84cf0ceab` — ci(albion): reassemble split 7z captures before ingest
- `6e1209608158707ae101ad7207e0d61e6f8290b4` — fix(ci): zero-pad split archive volume sequence
- `757f1cb1c0d585555d5d696131f5a8c59ce2e790` — fix(ci): route extracted PCAP and JSONL into forensic pipeline
- `c989db1d90d737620b5687d2c90dfd23362c03df` — ci(albion): exclude oversized derived aggregates from git commit
- `e9baa807f7453823cea51ab367971160d83d9aec` — ci(albion): commit oversized derived outputs as verified shards
- `15007df292022f5ef6a8553d8773d8eebc85ad62` — ci(albion): tolerate empty shard sets during ingest
- `2b4ddfc7cf8fde687aacf473848ed529f36c91e7` — ci(albion): stash oversized originals before safe rebase retries
- `20b9151a10bd3247e8cced59509a55eed06b90ff` — ci(albion): stash only tracked originals before rebase

### Data provenance commits (1)

- `34de2fa69d5fe96b5369e2e7773f87274e06706b` — fix(data): align research index provenance with merged CSV blobs

### Feature commits (15)

- `dc7987f99f4276e3d04d6c54b2559c9d3c8fc31f` — feat(albion): retain join and cluster transition parameters
- `69024fce17421db03fa62342b6538868da35e041` — feat(albion): export compact region transition evidence
- `8dc9f3e4f49835da85cdd60456dcb5a6807f01cb` — feat(albion): preserve Event 40 observed resource amounts in catalog
- `e38b331d5bf2890263f847ead354378307e7fb25` — feat(albion): preserve oversized derived outputs as verified shards
- `508d85634bc56950e54525f6766c9bdd1789e02a` — feat(albion): add verified reconstruction for sharded evidence
- `3186925d5fb9448bf0b2cb31800317de3ff3cfef` — feat(albion): expose explicit active-region spatial guard
- `c13cfb4a30fa7c08a5fb06ef25ba6bedc55dac5c` — feat(albion): export exact operation 23 target request evidence
- `b228da225b42b0cb98080d8ebcaa103dceb9b977` — feat(albion): scope offline entity evidence by verified region ID
- `ef41db778333f10b6050e8573894bfd5cfdf2637` — feat(albion): include region IDs in resource and mob evidence exports
- `62aa27e1bd52c63b5a72854e81e3308daae4782c` — feat(albion): keep manual marker lifecycles inside one region
- `e7a42df79f298b4e869e384d0063b2316e2c51cb` — feat(albion): label spatial marker correlations by region
- `01aaeead6c4351d1bdff63d202809681b0536766` — feat(albion): expose parsed region IDs beside raw transition packets
- `ca0238136bbe0aa03221c3e0cf304c92dfc7b1e0` — feat(albion): attach verified region IDs to player movement timeline
- `4665a8361901671be0a7b741566659fa730fe058` — feat(albion): show nearby same-region resources when marker labels do not match
- `a453c812483f285dcae97d0ad0bc044c0cec11fa` — feat(albion): retain uncertain resource candidates without mislabeling markers

### Runtime / analysis fix commits (42)

- `cd80649060b9c46262cde0cbf8a8bf6a2cd20e0f` — fix(albion): isolate target and Troll markers across regions
- `96544090d51720743f0bb9ebd5954612a918c267` — fix(albion): require exact proof for manual Troll classification
- `ae55141041615496a4c14599ed1fa3679e0e771a` — fix(albion): navigate packet-typed harvestables without mobile type IDs
- `a40e9b03dc2df1a61bfc89022c84a6be81828f88` — fix(albion): prevent candidate pins from hijacking radar guidance
- `253d34973905a9b85de9bd1ef6955640be714c61` — fix(albion): prevent stale marker windows from absorbing later stops
- `deb8e5ebbebc3822c7340528b7727feb5679ded1` — fix(albion): extend mobile crosswalk for newly observed TypeIDs
- `737d6936b773d941f3d911152a26acda008a75a0` — fix(albion): supersede stale unclosed mob marker targets
- `b5f8b687c032a137b8b1045a411a081f9319157f` — fix(albion): count superseded mob starts as incomplete markers
- `e5bee60fa8d37b7f6d424c349948c9b927f00831` — fix(albion): retain legacy multi-stage marker pairing semantics
- `29a19d4cd19e0a7f79ebf88b86596216840b96ef` — fix(albion): preserve provenance of historical cross-branch research index
- `94edfa222a3d0416bddb679cd85acaaafac9d36a` — fix(albion): include superseded troll targets in incomplete marker totals
- `5fbb6f27a31aeef3b33479abd4060181d7434f27` — fix(albion): enrich radar catalogs for every captured PCAP
- `220eea2519506527e8c27a962c4372bb5441895e` — fix(albion): run radar enrichment across all captures
- `f5ff6c92067677d23493349eaade27ab1f73bd57` — fix(albion): correlate overlapping attack windows without pairing superseded markers
- `1bdfb7bff9279eaab0c87b958f8b2e418fdf389d` — fix(albion): scope mob lifecycle joins by source archive and PCAP
- `03474e843f723f3ed49a2a3e34c7c9ea4100fe83` — fix(albion): tolerate unavailable bundled asset in knowledge export
- `42969213f48734c2da5ae7924bee5409f6456e09` — fix(albion): exclude depleted resource candidates from marker alignment
- `4a1e0402d5659cd5ef667daf7ffc8b4fa5de023b` — fix(albion): block cross-region resource navigation on mini radar
- `13c560af2d5f84dde5ecc8b6e8064a34f4cb559e` — fix(albion): label depleted-only spatial candidates explicitly
- `ac7b15b2540e1f1356a7e5e8c7d9a128a85e1d6b` — fix(albion): retain exact target IDs when mob spawn arrives later
- `5b7eccbc9cc60492d4955a6bb043771ba35f844d` — fix(albion): invalidate Troll pairing after ambiguous target request
- `3b0ce0fbb7efd11f4ef095f3c4688d8a94a68273` — fix(albion): scope respawn generation keys to active region
- `de175e2ce532144c195571a5aabdfb8b48eef3a1` — fix(albion): restrict delayed Troll marker joins to mob records
- `4244d6e7257860db1f6ee89b0ef1623d5c5889c9` — fix(albion): use region-scoped identity keys throughout audit
- `8def2478d801bd908b6aaee9696012941809e228` — fix(albion): correlate player positions only within the marker region
- `d149d3e28cc9bdcd3af72263de6638204e7b89a4` — fix(albion): correlate Troll attacks within the same region and attack interval
- `7aa3b85d4da33287ff953a8d51c03bd326b78895` — fix(albion): keep Troll marker enrichment within verified region
- `82afa8a854c989ca66a74eaa7beeb2e3e11923bb` — fix(albion): resolve marker region from its unique source archive
- `aaa3f22d3a7f5fe61e05a55a6c60beb37d6825c5` — fix(albion): enforce region match in spatial resource candidates
- `30efa7d44c5659516987a8dbe9aa8dd0919ec7c1` — fix(albion): keep distinct PCAP counts separate from region counts
- `078b3ba11523c976c2fbc5aee2637439a8c2cd2b` — fix(albion): refresh entity region tags and retain them in target exports
- `c242f7c4784723df3169a49c5a10b8a55fc00572` — fix(albion): align region columns in node and resource-state CSV exports
- `7c9f170e28c0e1fa8c71c8c725d7c668749607b9` — fix(albion): serialize unknown attack-target regions as blank
- `5b41496bea8a62885f4ea0ece5c4656c82bea22b` — fix(albion): preserve region column in ranked spatial correlations
- `85773d439870403ea16ac5aa814f7872e19e262a` — fix(albion): preserve region column in mob marker correlations
- `c350ef4e995f69d7a4b8086d9288e576bc2ba38b` — fix(albion): isolate spatial alignment by confirmed region
- `ddc80cfde036ab207b44625f31cb3456f8221c1b` — fix(albion): parse region ID from operation 2 response parameters
- `8320c8be5ef1ef11f93c00e2b6980e5c29eb0a7b` — fix(albion): accept numeric cluster IDs in initial Join responses
- `2e113ae6631d734517f5c3c276abfd693a826358` — fix(albion): correct operation response region parsing regex
- `c183b7ce0f53226f7b48bbf8d66774ecf33553bd` — fix(albion): reject region-tagged navigation targets until active region is known
- `cff7fc8ecfa2832c2e6722b2868d0da2148135b9` — fix(albion): parse numeric Photon parameter names in region evidence
- `412354a02099cbd57a893c3fd0dc58b4bebd866b` — fix(albion): distinguish confirmed depletion from missing fresh resource evidence

### Test commits (21)

- `10f4f2fc3ede1acd825e4867883c20dc3038fb99` — test(albion): cover manual Troll identity and typed unknown-family navigation
- `1f6709f324196de58d3fd4663b191c5f24101c50` — test(albion): prevent Troll target leakage across region transitions
- `dbca76d9fab2a08ae50db6faaeecfaa7c15c5605` — test(albion): cover superseded starts and duplicate stop markers
- `509a0435e143e307243708582ea4112df1ecb5e1` — test(albion): protect troll marker windows from stale pairing
- `42ab1c845fedf41d3e4f72034fa91eb9c2eccc0f` — test(albion): cover enrichment of newly uploaded captures
- `5031ed074a6d35bb2726b38910a5cc71d4d9f384` — test(albion): prevent superseded troll marker cross-pairing
- `257016c833beef4c399004544ad567190acf0d7f` — test(albion): assert initial Event 40 amount survives catalog export
- `63234b51595e5bab681b1d707f7488df9e48c406` — test(albion): verify lossless sharding of large analysis outputs
- `c54f2e07630a2e54fc7bc956471aa5b7fc6ed7a1` — test(albion): verify exact reconstruction from evidence shards
- `e63220575aed859dc3bebcb0ca7fe52402979fdf` — test(albion): expect preserved marker elapsed realtime timestamp
- `9141cc2cf0ff923dab7fad9f38b2792c5ad02423` — test(albion): count unresolved research family in parsed index
- `1f84e305cb876a525da1c04fc086465a08622948` — test(albion): prevent mini radar cross-region target selection
- `6a6585482cf37670952a7d3374933d9467dcf0d2` — test(albion): cover target requests arriving before mob spawn events
- `258ff2f33d2fe64279da38e46977c9426c7d7a02` — test(albion): verify respawn keys retain region identity
- `b21722b3810a20e85d50668c3d5dfd98b7ead8cc` — test(albion): keep Troll markers off non-mob resources
- `90135da4b5e3c2ec463aa8627d5639dee949e046` — test(albion): prevent cross-region Troll marker enrichment
- `25dd91c106c419e1e44abb25f88f55f35f3abf06` — test(albion): import assertions for delayed target and region cases
- `cf49be7aa09c60a1d7379ede03e8f282830f12a4` — test(albion): cover numeric Join cluster identifiers
- `0baffcc37d6f5ae4cee83af4842a63e2126796c1` — test(albion): reject cross-region spatial alignment
- `02b786fa5908641a0d3114afaab2fd0a2e0b6476` — test(albion): enforce unknown-region navigation safety
- `a3252b73c67af88f8a8145d801cb121c3aacbe3c` — test(albion): cover numeric cluster IDs in ChangeCluster responses

### Refactor commits (1)

- `ea74173f46cd7b263b6f8081d91bab00a55f336c` — refactor(albion): use tested active-region guard in radar

### Other commits (0)



## 17. Raw Pattern Discovery — exact 8-commit file inventory

Raw Pattern Discovery is a strict descendant of Zone Forensics Integration:
- Common ancestor: `6fa9d10555aa2ab5e36f64ca831f0e4869faaeb0`
- Zone Forensics Integration head: `436e7f60eea014977d01c2b570d52d3017691b97`
- Raw Pattern Discovery head: `c508c2b54eecced896862e401732e2e0ce44ce29`
- 8 commits total: the seven zone/reliability commits plus the Raw UDP discovery commit.

Per-commit paths (names shortened only for readability; exact repository paths are represented by the filename suffixes):
- `145ffa105a37e25eddb61db39d51aef5579d4ac2`: .github/workflows/build.yml; AlbionLiveKnowledgeStore.kt; generate_research_knowledge_index.py; validate_runtime_knowledge_assets.py
- `a611c8125c91c3d6beb655897dc7e58745ac00fb`: .github/workflows/build.yml; AlbionLiveKnowledgeStore.kt; ResourcePatternLearner.kt
- `3e0c139641be7cce12209c5f7c3e5ac0a87ca549`: .github/workflows/build.yml; AlbionCaptureWorker.kt; AlbionEventStateTracker.kt; AlbionMarkerQueue.kt; AlbionProtocol18Decoder.kt; CoreServiceManager.kt; AlbionOverlayService.kt
- `b75011e6ab9d605bd0ae687fea9dfe55353ab930`: .github/workflows/build.yml; AlbionCaptureAnalyzer.kt; AlbionCaptureMarker.kt; AlbionCaptureAnalyzerTest.kt; AlbionPhotonPacketDecoderTest.kt
- `c7e8cb21e8878385e4b7b9fa3db8aa2cd1d9e549`: .github/workflows/albion-zone-audit.yml; .github/workflows/build.yml; app/build.gradle.kts; test_portal_zone_node_matches.py; analyze_portal_zone_node_matches.py
- `9c5c3e3ad1c87b523da50feb478f618a2520b8a4`: AlbionResearchKnowledgeIndexTest.kt; generate_research_knowledge_index.py; validate_runtime_knowledge_assets.py
- `436e7f60eea014977d01c2b570d52d3017691b97`: AlbionCaptureAnalyzer.kt
- `c508c2b54eecced896862e401732e2e0ce44ce29`: .github/workflows/build.yml; AlbionLearningEngine.kt; AlbionRadarActivity.kt

The final Raw UDP discovery commit changes:
- `.github/workflows/build.yml`: adds the Raw Pattern Discovery branch to build/test/replay workflow conditions.
- `V2rayNG/app/src/main/java/com/v2ray/ang/ui/albion/AlbionLearningEngine.kt`: adds the raw UDP byte-pattern discovery engine.
- `V2rayNG/app/src/main/java/com/v2ray/ang/ui/albion/AlbionRadarActivity.kt`: wires the learning engine into the radar activity.

No commit from these branches has been cherry-picked, merged or copied to the staging branch in this audit.

## 18. Data-preservation classification for Pattern Mining commits

- The 25 generic upload commits are source/evidence inputs. Preserve original blobs byte-for-byte and verify all 45 archive parts plus the separately uploaded `AlbionRadar.zip`.
- The 8 `data(albion)` commits add or revise generated outputs in `research/analysis/`, `research/database/` and historical runtime assets. Treat these as derived data until their source archive/PCAP hashes and generator versions are reconciled; do not blindly accept later CSV/JSON outputs as canonical.
- The 8 CI/archive commits change reconstruction, ingest, sharding, and retry behavior. These are executable pipeline changes and require tests against immutable raw inputs.
- The 42 fix and 15 feature commits include production behavior and offline evidence logic. Review each against Safe Fixes, especially region identity, marker pairing, stale-target invalidation, resource depletion, and provenance guards.
- The 21 test commits are not automatically trustworthy simply because they pass. Separate synthetic fixtures from observed captures; tests must not promote low-confidence or single-session evidence into global protocol semantics.
- The 1 data-provenance commit and 1 refactor commit must be evaluated with their affected manifests/generators.

## 19. Change log — CI and commit inventory extension

- Operation: append CI artifact verification, full 121-commit Pattern Mining inventory, and exact Raw Pattern Discovery ancestry/file inventory.
- Repository: `eesanezhad76/v2rayNG`
- Source file blob SHA before update: `7c5257d6910a3f387352adc5a0c8454093a95e10`
- Destination branch: `albion-integration-staging-2026-11-05`
- No application source, tests, raw evidence, archive parts, generated research data, or pre-existing branch changed.
- Resulting commit SHA: a69c1030c3e3eb51057cb4b2dcfe5291a4822782
- New blob SHA: 978ed95f52b3c6b8a333a7ea9c56756bea625c21


## 20. Pattern Mining — exact 285-path diff inventory against Safe Integration

The following is the full file-path list returned by GitHub compare for base `albion-safe-integration-2026-11-05` and head `albion-pattern-mining`. Status is relative to that base. Binary-file line counts from the compare API may be zero and must not be interpreted as empty content.

### .github (1)
- `.github/workflows/albion-capture-ingest.yml` — modified; +63/-1

### V2rayNG/app (19)
- `V2rayNG/app/src/main/assets/albion-historical-autonomous-patterns.json` — added; +1/-0
- `V2rayNG/app/src/main/assets/albion-historical-entity-evidence.json` — added; +1/-0
- `V2rayNG/app/src/main/assets/albion-historical-knowledge-manifest.json` — added; +13/-0
- `V2rayNG/app/src/main/assets/albion-historical-learned-patterns.json` — added; +1/-0
- `V2rayNG/app/src/main/assets/albion-historical-protocol-shapes.json` — added; +1/-0
- `V2rayNG/app/src/main/assets/albion-mobile-type-dictionary.json` — modified; +521/-320
- `V2rayNG/app/src/main/assets/albion-research-knowledge-index.json` — modified; +1/-1
- `V2rayNG/app/src/main/assets/albion-static-catalog.json` — modified; +67/-1505
- `V2rayNG/app/src/main/java/com/v2ray/ang/albion/AlbionCaptureAnalyzer.kt` — modified; +3/-3
- `V2rayNG/app/src/main/java/com/v2ray/ang/albion/AlbionEntityEventMapper.kt` — modified; +6/-3
- `V2rayNG/app/src/main/java/com/v2ray/ang/albion/AlbionKnowledgeBundle.kt` — modified; +8/-3
- `V2rayNG/app/src/main/java/com/v2ray/ang/albion/AlbionLiveKnowledgeStore.kt` — modified; +39/-6
- `V2rayNG/app/src/main/java/com/v2ray/ang/ui/albion/AlbionMiniRadarView.kt` — modified; +17/-9
- `V2rayNG/app/src/main/java/com/v2ray/ang/ui/albion/AlbionSpatialIntelligence.kt` — modified; +22/-2
- `V2rayNG/app/src/test/java/com/v2ray/ang/albion/AlbionCaptureAnalyzerTest.kt` — modified; +1/-1
- `V2rayNG/app/src/test/java/com/v2ray/ang/albion/AlbionEntityEventMapperTest.kt` — modified; +12/-0
- `V2rayNG/app/src/test/java/com/v2ray/ang/albion/AlbionLiveKnowledgeStoreTest.kt` — modified; +148/-0
- `V2rayNG/app/src/test/java/com/v2ray/ang/albion/AlbionResearchKnowledgeIndexTest.kt` — modified; +1/-1
- `V2rayNG/app/src/test/java/com/v2ray/ang/ui/albion/AlbionSpatialIntelligenceTest.kt` — modified; +40/-0

### research/analysis (130)
- `research/analysis/2026-11-02-operation23-target-requests.csv` — added; +1/-0
- `research/analysis/2026-11-02-overlay-family-candidate-summary.csv` — modified; +3/-1
- `research/analysis/2026-11-02-overlay-family-correlation.csv` — modified; +107/-159
- `research/analysis/2026-11-02-overlay-manual-markers.csv` — modified; +370/-460
- `research/analysis/2026-11-02-overlay-mob-attack-correlation.csv` — modified; +14/-36
- `research/analysis/2026-11-02-overlay-spatial-correlation.csv` — modified; +159/-508
- `research/analysis/2026-11-02-own-player-movement-timeline.csv` — modified; +81882/-54607
- `research/analysis/2026-11-02-pcap-attack-target-tracks.csv` — modified; +38/-52
- `research/analysis/2026-11-02-pcap-harvestable-nodes.csv` — modified; +653/-407
- `research/analysis/2026-11-02-pcap-health-energy-events.csv` — modified; +2230/-2261
- `research/analysis/2026-11-02-pcap-mob-lifecycle.csv` — modified; +4173/-2957
- `research/analysis/2026-11-02-pcap-replay-index.json` — modified; +83/-50
- `research/analysis/2026-11-02-pcap-resource-state-timelines.csv` — modified; +630/-357
- `research/analysis/2026-11-02-pcap-static-resources.csv` — modified; +47109/-25505
- `research/analysis/2026-11-02-region-transition-evidence.csv` — added; +34/-0
- `research/analysis/2026-11-02-replay-regression-report.md` — modified; +15/-5
- `research/analysis/2026-11-02-resource-pattern-frequency.csv` — modified; +30/-21
- `research/analysis/2026-11-02-spatial-alignment.json` — modified; +138/-52
- `research/analysis/2026-11-02-spatial-alignment.md` — modified; +44/-11
- `research/analysis/2026-11-03-complete-inbox-database-report.md` — modified; +15/-15
- `research/analysis/Albion2--2_blue_gholem_ench_2_killed_together_and_mined_besides_each_other_-04-pcap-report.md` — modified; +14/-9
- `research/analysis/Albion2--6red_ores_each_one_mined-05-pcap-report.md` — modified; +14/-12
- `research/analysis/Albion2--blue_enchanted_2_gholem_killed_and_mined_-06-pcap-report.md` — modified; +10/-6
- `research/analysis/Albion2--blue_ore_ench1_mined_-07-pcap-report.md` — modified; +6/-4
- `research/analysis/Albion2--blue_ore_ench2_while_mining_a_mob_attacked_me_and_I_killed_him_but_the_ore_lost_its_enchantment_after-08-pcap-report.md` — modified; +13/-7
- `research/analysis/Albion2--red_gholem_lvl2_ench_killed_and_mines-09-pcap-report.md` — modified; +13/-7
- `research/analysis/Albion2--red_ore_ench_1_mined_mount_dismount_3_times_accidentally_before_mine-10-pcap-report.md` — modified; +20/-10
- `research/analysis/Albion2--red_ore_lvl2-11-pcap-report.md` — modified; +10/-6
- `research/analysis/Albion2--went_from_yellow_5_region_to_fort_sterling_and_then_to_bank_and_inspectoed_a_few_of_my_items_and_equi-12-pcap-report.md` — modified; +91/-9
- `research/analysis/AlbionRadar-forensic-inventory.json` — modified; +658/-158
- `research/analysis/AlbionRadar-forensic-inventory.md` — modified; +14/-10
- `research/analysis/AlbionRadar2--albion-1793699111286-13-pcap-report.md` — added; +43/-0
- `research/analysis/AlbionRadar2--albion-1793699145258-14-pcap-report.md` — added; +43/-0
- `research/analysis/AlbionRadar2--albion-1793699283778-15-pcap-report.md` — added; +35359/-0
- `research/analysis/AlbionRadar5--albion-1793712506879-16-pcap-report.md` — added; +37052/-0
- `research/analysis/AlbionRadar6json--albion-1793718210678-17-pcap-report.md` — added; +43/-0
- `research/analysis/AlbionRadar6json--albion-1793718347142-18-pcap-report.md` — added; +43/-0
- `research/analysis/AlbionRadar6json--albion-1793718370435-19-pcap-report.md` — added; +43/-0
- `research/analysis/AlbionRadar6json--albion-1793718384341-20-pcap-report.md` — added; +88424/-0
- `research/analysis/AlbionRadar8--albion-1793769140776-21-pcap-report.md` — added; +181739/-0
- `research/analysis/Albion_--_-01-pcap-report.md` — modified; +6/-5
- `research/analysis/Albion_--_-02-pcap-report.md` — modified; +14/-4
- `research/analysis/Albion_--_-03-pcap-report.md` — modified; +14/-11
- `research/analysis/Albions--1_mob_dismount_and_mount_-22-pcap-report.md` — modified; +4/-1
- `research/analysis/Albions--2_times_90degred_change_direction_-23-pcap-report.md` — modified; +13/-8
- `research/analysis/Albions--2mobs_dismount_and_Mount_-24-pcap-report.md` — modified; +9/-4
- `research/analysis/Albions--gholem_dismount_kill_mine_mount-25-pcap-report.md` — modified; +15/-11
- `research/analysis/Albions--ore_4_3_red_2_blue_-26-pcap-report.md` — modified; +54/-47
- `research/analysis/Albions--run_and_stop-27-pcap-report.md` — modified; +14/-10
- `research/analysis/Albions--stopping-28-pcap-report.md` — modified; +5/-2
- `research/analysis/Albionsskkakaks111--albion-1793620143023-29-pcap-report.md` — modified; +6/-6
- `research/analysis/Albionsskkakaks111--albion-1793620890462-30-pcap-report.md` — modified; +609/-531
- `research/analysis/albion-1793620890462-attack-targets.csv` — added; +2/-0
- `research/analysis/albion-1793620890462-overlay-catalog.json` — modified; +2742/-499
- `research/analysis/albion-1793682590317-attack-targets.csv` — modified; +0/-27
- `research/analysis/albion-1793682590317-overlay-catalog.json` — modified; +67/-1505
- `research/analysis/albion-1793682590317.md` — modified; +9/-9
- `research/analysis/albion-1793699283778-attack-targets.csv` — added; +2/-0
- `research/analysis/albion-1793699283778-overlay-catalog.json` — modified; +2632/-482
- `research/analysis/albion-1793699283778.md` — modified; +1/-1
- `research/analysis/albion-1793707992278-overlay-catalog.json` — modified; +50/-0
- `research/analysis/albion-1793712506879-attack-targets.csv` — added; +3/-0
- `research/analysis/albion-1793712506879-overlay-catalog.json` — modified; +2941/-516
- `research/analysis/albion-1793712506879.md` — modified; +1/-1
- `research/analysis/albion-1793718384341-attack-targets.csv` — added; +3/-0
- `research/analysis/albion-1793718384341-overlay-catalog.json` — modified; +9665/-1779
- `research/analysis/albion-1793718384341.md` — modified; +4/-4
- `research/analysis/albion-1793769140776-attack-targets.csv` — added; +8/-0
- `research/analysis/albion-1793769140776-overlay-catalog.json` — modified; +7836/-1368
- `research/analysis/albion-1793769140776.md` — modified; +7/-7
- `research/analysis/albion-1793779572842-overlay-catalog.json` — added; +2232/-0
- `research/analysis/albion-1793779572842.md` — added; +29/-0
- `research/analysis/albion-1793782308821-attack-targets.csv` — added; +7/-0
- `research/analysis/albion-1793782308821-overlay-catalog.json` — added; +30092/-0
- `research/analysis/albion-1793782308821.md` — added; +29/-0
- `research/analysis/albion-1793786223835-attack-targets.csv` — added; +3/-0
- `research/analysis/albion-1793786223835-overlay-catalog.json` — added; +3388/-0
- `research/analysis/albion-1793786223835.md` — added; +29/-0
- `research/analysis/albion-1793789871115-attack-targets.csv` — added; +6/-0
- `research/analysis/albion-1793789871115-overlay-catalog.json` — added; +25651/-0
- `research/analysis/albion-1793789871115.md` — added; +29/-0
- `research/analysis/albion-entity-discovery-report-2026-11-03.md` — modified; +114/-64
- `research/analysis/albion-knowledge-bundle-import.md` — modified; +1/-1
- `research/analysis/archiveNew-extracted--albion-1793779572842-32-pcap-report.md` — added; +11161/-0
- `research/analysis/archiveNew-extracted--albion-1793782308821-33-pcap-report.md` — added; +199039/-0
- `research/analysis/archiveNew-extracted--albion-1793786223835-34-pcap-report.md` — added; +9471/-0
- `research/analysis/archiveNew-extracted--albion-1793789871115-35-pcap-report.md` — added; +142584/-0
- `research/analysis/archiveNew-extracted-forensic-inventory.json` — added; +789/-0
- `research/analysis/archiveNew-extracted-forensic-inventory.md` — added; +36/-0
- `research/analysis/derived-output-shards-manifest.json` — added; +82/-0
- `research/analysis/derived-shards/915d2c1cc679da6ec0380b30c1f3784e774affa8a31afbe53b48f32ae701194b/part-0001.bin` — added; +429516/-0
- `research/analysis/derived-shards/915d2c1cc679da6ec0380b30c1f3784e774affa8a31afbe53b48f32ae701194b/part-0002.bin` — added; +335914/-0
- `research/analysis/derived-shards/f2b13bebd4292355e9460d1bf6cb353e8f06392aeed4e49b097d2e2163d01883/part-0001.bin` — added; +249797/-0
- `research/analysis/derived-shards/f2b13bebd4292355e9460d1bf6cb353e8f06392aeed4e49b097d2e2163d01883/part-0002.bin` — added; +248012/-0
- `research/analysis/derived-shards/f2b13bebd4292355e9460d1bf6cb353e8f06392aeed4e49b097d2e2163d01883/part-0003.bin` — added; +237957/-0
- `research/analysis/derived-shards/f2b13bebd4292355e9460d1bf6cb353e8f06392aeed4e49b097d2e2163d01883/part-0004.bin` — added; +25319/-0
- `research/analysis/replay/Albion2--2_blue_gholem_ench_2_killed_together_and_mined_besides_each_other_-04-replay.json` — modified; +7/-4
- `research/analysis/replay/Albion2--6red_ores_each_one_mined-05-replay.json` — modified; +5/-4
- `research/analysis/replay/Albion2--blue_enchanted_2_gholem_killed_and_mined_-06-replay.json` — modified; +7/-4
- `research/analysis/replay/Albion2--blue_ore_ench1_mined_-07-replay.json` — modified; +2/-1
- `research/analysis/replay/Albion2--blue_ore_ench2_while_mining_a_mob_attacked_me_and_I_killed_him_but_the_ore_lost_its_enchantment_after-08-replay.json` — modified; +7/-4
- `research/analysis/replay/Albion2--red_gholem_lvl2_ench_killed_and_mines-09-replay.json` — modified; +5/-2
- `research/analysis/replay/Albion2--red_ore_ench_1_mined_mount_dismount_3_times_accidentally_before_mine-10-replay.json` — modified; +2/-2
- `research/analysis/replay/Albion2--red_ore_lvl2-11-replay.json` — modified; +3/-1
- `research/analysis/replay/Albion2--went_from_yellow_5_region_to_fort_sterling_and_then_to_bank_and_inspectoed_a_few_of_my_items_and_equi-12-replay.json` — modified; +2/-2
- `research/analysis/replay/AlbionRadar2--albion-1793699111286-13-replay.json` — added; +33/-0
- `research/analysis/replay/AlbionRadar2--albion-1793699145258-14-replay.json` — added; +33/-0
- `research/analysis/replay/AlbionRadar2--albion-1793699283778-15-replay.json` — added; +173/-0
- `research/analysis/replay/AlbionRadar5--albion-1793712506879-16-replay.json` — added; +173/-0
- `research/analysis/replay/AlbionRadar6json--albion-1793718210678-17-replay.json` — added; +33/-0
- `research/analysis/replay/AlbionRadar6json--albion-1793718347142-18-replay.json` — added; +33/-0
- `research/analysis/replay/AlbionRadar6json--albion-1793718370435-19-replay.json` — added; +33/-0
- `research/analysis/replay/AlbionRadar6json--albion-1793718384341-20-replay.json` — added; +213/-0
- `research/analysis/replay/AlbionRadar8--albion-1793769140776-21-replay.json` — added; +243/-0
- `research/analysis/replay/Albion_--_-01-replay.json` — modified; +2/-2
- `research/analysis/replay/Albion_--_-02-replay.json` — modified; +5/-3
- `research/analysis/replay/Albion_--_-03-replay.json` — modified; +2/-2
- `research/analysis/replay/Albions--1_mob_dismount_and_mount_-22-replay.json` — modified; +2/-1
- `research/analysis/replay/Albions--2_times_90degred_change_direction_-23-replay.json` — modified; +3/-1
- `research/analysis/replay/Albions--2mobs_dismount_and_Mount_-24-replay.json` — modified; +4/-2
- `research/analysis/replay/Albions--gholem_dismount_kill_mine_mount-25-replay.json` — modified; +4/-2
- `research/analysis/replay/Albions--ore_4_3_red_2_blue_-26-replay.json` — modified; +3/-2
- `research/analysis/replay/Albions--run_and_stop-27-replay.json` — modified; +3/-2
- `research/analysis/replay/Albions--stopping-28-replay.json` — modified; +2/-1
- `research/analysis/replay/Albionsskkakaks111--albion-1793620143023-29-replay.json` — modified; +1/-1
- `research/analysis/replay/Albionsskkakaks111--albion-1793620890462-30-replay.json` — modified; +5/-3
- `research/analysis/replay/archiveNew-extracted--albion-1793779572842-32-replay.json` — added; +154/-0
- `research/analysis/replay/archiveNew-extracted--albion-1793782308821-33-replay.json` — added; +206/-0
- `research/analysis/replay/archiveNew-extracted--albion-1793786223835-34-replay.json` — added; +162/-0
- `research/analysis/replay/archiveNew-extracted--albion-1793789871115-35-replay.json` — added; +233/-0

### research/database (78)
- `research/database/2026-11-02-operation23-target-requests.csv` — added; +1/-0
- `research/database/2026-11-02-overlay-mob-attack-correlation.csv` — modified; +14/-36
- `research/database/2026-11-02-own-player-movement-timeline.csv` — modified; +0/-0
- `research/database/2026-11-02-pcap-attack-target-tracks.csv` — modified; +0/-0
- `research/database/2026-11-02-pcap-harvestable-nodes.csv` — modified; +0/-0
- `research/database/2026-11-02-pcap-health-energy-events.csv` — modified; +0/-0
- `research/database/2026-11-02-pcap-mob-lifecycle.csv` — modified; +0/-0
- `research/database/2026-11-02-pcap-resource-state-timelines.csv` — modified; +0/-0
- `research/database/2026-11-02-pcap-static-resources.csv` — modified; +0/-0
- `research/database/2026-11-02-region-transition-evidence.csv` — added; +0/-0
- `research/database/albion-1793620143023-marker-lifecycles.csv` — modified; +0/-0
- `research/database/albion-1793620890462-attack-targets.csv` — added; +0/-0
- `research/database/albion-1793620890462-catalog.json` — modified; +0/-0
- `research/database/albion-1793620890462-marker-lifecycles.csv` — modified; +0/-0
- `research/database/albion-1793620890462.json` — modified; +0/-0
- `research/database/albion-1793682590317-attack-targets.csv` — modified; +0/-0
- `research/database/albion-1793682590317-catalog.json` — modified; +0/-0
- `research/database/albion-1793682590317-marker-lifecycles.csv` — modified; +0/-0
- `research/database/albion-1793682590317.json` — modified; +0/-0
- `research/database/albion-1793699111286-marker-lifecycles.csv` — modified; +0/-0
- `research/database/albion-1793699145258-marker-lifecycles.csv` — modified; +0/-0
- `research/database/albion-1793699283778-attack-targets.csv` — added; +0/-0
- `research/database/albion-1793699283778-catalog.json` — modified; +0/-0
- `research/database/albion-1793699283778-marker-lifecycles.csv` — modified; +0/-0
- `research/database/albion-1793699283778.json` — modified; +0/-0
- `research/database/albion-1793707992278-catalog.json` — modified; +0/-0
- `research/database/albion-1793707992278-marker-lifecycles.csv` — modified; +0/-0
- `research/database/albion-1793712506879-attack-targets.csv` — added; +0/-0
- `research/database/albion-1793712506879-catalog.json` — modified; +0/-0
- `research/database/albion-1793712506879-marker-lifecycles.csv` — modified; +0/-0
- `research/database/albion-1793712506879-mob-marker-spatial-correlation.csv` — modified; +0/-0
- `research/database/albion-1793712506879.json` — modified; +0/-0
- `research/database/albion-1793718210678-marker-lifecycles.csv` — modified; +0/-0
- `research/database/albion-1793718347142-marker-lifecycles.csv` — modified; +0/-0
- `research/database/albion-1793718370435-marker-lifecycles.csv` — modified; +0/-0
- `research/database/albion-1793718384341-attack-targets.csv` — added; +0/-0
- `research/database/albion-1793718384341-catalog.json` — modified; +0/-0
- `research/database/albion-1793718384341-marker-lifecycles.csv` — modified; +0/-0
- `research/database/albion-1793718384341.json` — modified; +0/-0
- `research/database/albion-1793769140776-attack-targets.csv` — added; +0/-0
- `research/database/albion-1793769140776-catalog.json` — modified; +0/-0
- `research/database/albion-1793769140776-marker-lifecycles.csv` — modified; +0/-0
- `research/database/albion-1793769140776-mob-marker-spatial-correlation.csv` — modified; +0/-0
- `research/database/albion-1793769140776.json` — modified; +0/-0
- `research/database/albion-1793779572842-catalog.json` — added; +0/-0
- `research/database/albion-1793779572842-marker-lifecycles.csv` — added; +0/-0
- `research/database/albion-1793779572842-mob-entities.csv` — added; +0/-0
- `research/database/albion-1793779572842-mob-marker-spatial-correlation.csv` — added; +0/-0
- `research/database/albion-1793779572842.csv` — added; +0/-0
- `research/database/albion-1793779572842.json` — added; +0/-0
- `research/database/albion-1793782308821-attack-targets.csv` — added; +0/-0
- `research/database/albion-1793782308821-catalog.json` — added; +0/-0
- `research/database/albion-1793782308821-marker-lifecycles.csv` — added; +0/-0
- `research/database/albion-1793782308821-mob-entities.csv` — added; +0/-0
- `research/database/albion-1793782308821-mob-marker-spatial-correlation.csv` — added; +0/-0
- `research/database/albion-1793782308821.csv` — added; +0/-0
- `research/database/albion-1793782308821.json` — added; +0/-0
- `research/database/albion-1793786223835-attack-targets.csv` — added; +0/-0
- `research/database/albion-1793786223835-catalog.json` — added; +0/-0
- `research/database/albion-1793786223835-marker-lifecycles.csv` — added; +0/-0
- `research/database/albion-1793786223835-mob-entities.csv` — added; +0/-0
- `research/database/albion-1793786223835-mob-marker-spatial-correlation.csv` — added; +0/-0
- `research/database/albion-1793786223835.csv` — added; +0/-0
- `research/database/albion-1793786223835.json` — added; +0/-0
- `research/database/albion-1793789871115-attack-targets.csv` — added; +0/-0
- `research/database/albion-1793789871115-catalog.json` — added; +0/-0
- `research/database/albion-1793789871115-marker-lifecycles.csv` — added; +0/-0
- `research/database/albion-1793789871115-mob-entities.csv` — added; +0/-0
- `research/database/albion-1793789871115-mob-marker-spatial-correlation.csv` — added; +0/-0
- `research/database/albion-1793789871115.csv` — added; +0/-0
- `research/database/albion-1793789871115.json` — added; +0/-0
- `research/database/albion-complete-database-manifest-2026-11-03.json` — modified; +0/-0
- `research/database/albion-entity-discovery-report-2026-11-03.json` — modified; +0/-0
- `research/database/albion-mob-target-correlation-2026-11-03.csv` — modified; +0/-0
- `research/database/albion-mobile-entity-catalog-2026-11-03.csv` — modified; +0/-0
- `research/database/albion-mobile-type-crosswalk-2026-11-03.csv` — modified; +0/-0
- `research/database/albion-resource-node-catalog-2026-11-03.csv` — modified; +0/-0
- `research/database/albion-troll-target-correlation-2026-11-03.csv` — modified; +0/-0

### research/inbox (46)
- `research/inbox/AlbionRadar.zip` — modified; +0/-0
- `research/inbox/archiveNew.7z.t0.001` — added; +0/-0
- `research/inbox/archiveNew.7z.t0.002` — added; +0/-0
- `research/inbox/archiveNew.7z.t0.003` — added; +0/-0
- `research/inbox/archiveNew.7z.t0.004` — added; +0/-0
- `research/inbox/archiveNew.7z.t0.005` — added; +0/-0
- `research/inbox/archiveNew.7z.t0.006` — added; +0/-0
- `research/inbox/archiveNew.7z.t0.007` — added; +0/-0
- `research/inbox/archiveNew.7z.t0.008` — added; +0/-0
- `research/inbox/archiveNew.7z.t0.009` — added; +0/-0
- `research/inbox/archiveNew.7z.t0.010` — added; +0/-0
- `research/inbox/archiveNew.7z.t0.011` — added; +0/-0
- `research/inbox/archiveNew.7z.t0.012` — added; +0/-0
- `research/inbox/archiveNew.7z.t0.013` — added; +0/-0
- `research/inbox/archiveNew.7z.t0.014` — added; +0/-0
- `research/inbox/archiveNew.7z.t0.015` — added; +0/-0
- `research/inbox/archiveNew.7z.t0.016` — added; +0/-0
- `research/inbox/archiveNew.7z.t0.017` — added; +0/-0
- `research/inbox/archiveNew.7z.t0.018` — added; +0/-0
- `research/inbox/archiveNew.7z.t0.019` — added; +0/-0
- `research/inbox/archiveNew.7z.t0.020` — added; +0/-0
- `research/inbox/archiveNew.7z.t0.021` — added; +0/-0
- `research/inbox/archiveNew.7z.t0.022` — added; +0/-0
- `research/inbox/archiveNew.7z.t0.023` — added; +0/-0
- `research/inbox/archiveNew.7z.t0.024` — added; +0/-0
- `research/inbox/archiveNew.7z.t0.025` — added; +0/-0
- `research/inbox/archiveNew.7z.t0.026` — added; +0/-0
- `research/inbox/archiveNew.7z.t0.027` — added; +0/-0
- `research/inbox/archiveNew.7z.t0.028` — added; +0/-0
- `research/inbox/archiveNew.7z.t0.029` — added; +0/-0
- `research/inbox/archiveNew.7z.t0.030` — added; +0/-0
- `research/inbox/archiveNew.7z.t0.031` — added; +0/-0
- `research/inbox/archiveNew.7z.t0.032` — added; +0/-0
- `research/inbox/archiveNew.7z.t0.033` — added; +0/-0
- `research/inbox/archiveNew.7z.t0.034` — added; +0/-0
- `research/inbox/archiveNew.7z.t0.035` — added; +0/-0
- `research/inbox/archiveNew.7z.t0.036` — added; +0/-0
- `research/inbox/archiveNew.7z.t0.037` — added; +0/-0
- `research/inbox/archiveNew.7z.t0.038` — added; +0/-0
- `research/inbox/archiveNew.7z.t0.039` — added; +0/-0
- `research/inbox/archiveNew.7z.t0.040` — added; +0/-0
- `research/inbox/archiveNew.7z.t0.041` — added; +0/-0
- `research/inbox/archiveNew.7z.t0.042` — added; +0/-0
- `research/inbox/archiveNew.7z.t0.043` — added; +0/-0
- `research/inbox/archiveNew.7z.t0.044` — added; +0/-0
- `research/inbox/archiveNew.7z.t0.045` — added; +0/-0

### research/other (1)
- `research/pcap-analyzer/src/main/kotlin/AlbionPcapAudit.kt` — modified; +0/-0

### research/tests (4)
- `research/tests/test_enrich_radar_catalog.py` — modified; +0/-0
- `research/tests/test_ingest_capture.py` — modified; +0/-0
- `research/tests/test_spatial_alignment.py` — modified; +0/-0
- `research/tests/test_split_large_outputs.py` — added; +0/-0

### research/tools (6)
- `research/tools/analyze_spatial_alignment.py` — modified; +0/-0
- `research/tools/enrich_radar_catalog.py` — modified; +0/-0
- `research/tools/generate_mobile_type_dictionary.py` — modified; +0/-0
- `research/tools/ingest_capture.py` — modified; +0/-0
- `research/tools/split_large_outputs.py` — added; +0/-0
- `research/tools/validate_runtime_knowledge_assets.py` — modified; +0/-0


## 21. Upload commit anomaly resolved

Commit `c6d02e12de6b18736a5e94fb0d6f83491bcab667` has no file list in the GitHub commit response. A direct parent-tree vs commit-tree comparison confirms **zero changed paths and zero removed paths**: it is an empty/no-op commit, not a missing archive part. The 25 upload-labelled commits therefore consist of 24 actual file-changing commits plus this one empty commit.

The actual upload path set is 45 split archive parts (`.001`–`.045`) plus a modified `research/inbox/AlbionRadar.zip`. The ZIP is marked `modified`, not `added`; preserve both its prior and current blob identities until the source/version change is explained.

## 22. Change log — exact Pattern Mining path inventory

- Operation: append full 285-path diff inventory and resolve no-op upload commit by comparing parent and commit trees.
- Repository: `eesanezhad76/v2rayNG`
- Source document blob SHA: `978ed95f52b3c6b8a333a7ea9c56756bea625c21`
- Destination branch: `albion-integration-staging-2026-11-05`
- No source branch or file changed; no data copied or moved.
- Resulting commit SHA: da5f9f160c2e1ff3ada971193bcc63b74c171f67
- New blob SHA: 5fc53a40e5d64084858aad04a8fff9652e00a965


## 23. Safe-transfer assessment — semantic review, not merge approval

Comparison target:
- Safe Fixes: c51481253ff4b5b0bd5bd0223a9e6a2674af680a
- Pattern Mining: a453c812483f285dcae97d0ad0bc044c0cec11fa
- Raw Pattern Discovery: c508c2b54eecced896862e401732e2e0ce44ce29

### Candidate transfer matrix

| Change family | Source evidence | Current Safe Fixes state | Assessment | Required guard before transfer |
|---|---|---|---|---|
| Numeric region ID parsing (Join / ChangeCluster) | Pattern Mining mapper and tests; AlbionEntityEventMapper.kt | accepts String IDs only; rejects unsuccessful operation responses (returnCode != 0) | Candidate — low/medium risk | Preserve Safe Fixes return-code gate and map-local hint reset; convert only validated scalar IDs to trimmed strings; test String/Long/Int, null, blank, failed response |
| Active-region navigation guard | Pattern commits 3186925..., 4a1e040..., c183b7...; AlbionSpatialIntelligence.kt, AlbionMiniRadarView.kt | active snapshot is isolated, but UI target selection does not explicitly compare each entity's region to current player region | Candidate — high value safety guard | Add exact-match tests for same/different/missing region; when active region unknown, never navigate to a tagged entity; derive current player region only from authoritative player record |
| Strict Troll marker identity | Pattern commit 965440...; AlbionSpatialIntelligence.kt and spatial tests | label substring or high marker confidence can classify Troll without explicit identity proof | Candidate — high value safety guard | Require exact target/entity link for manual marker classification; preserve Ore Elemental game identity; test stale/ambiguous marker and exact-ID positive cases |
| Family + tier resource navigation without mobile TypeID | Pattern branch AlbionSpatialIntelligence.kt and test event40FamilyAndTierAreEnoughToNavigateWhenMobileTypeIdIsUnknown | currently requires mobile_type_id > 0 for typed resources | Candidate — evidence-backed | Allow only Event 40 harvestable/resource_state with valid positive family and tier 1–8; do not make Event 39 candidates navigable; add depletion/conflict/unknown-family guards |
| Exact target request before mob spawn | Pattern commits ac7b15... and related tests; AlbionLiveKnowledgeStore.kt | currently accepts only candidates matching an already-known mob record | Blocked pending wire-schema proof | Mapper collects integral values from all parameter values; generic numeric fields (including protocol metadata such as key 253 in test fixtures) can pollute candidates. The store's single-candidate fallback can bind the wrong ID. Identify the exact Operation 23 parameter key from raw captures, then test mapper→store end-to-end with unrelated integers and delayed spawn |
| Region-scoped target / marker / respawn lifecycle | Pattern fixes in AlbionLiveKnowledgeStore.kt and research pipeline | Safe Fixes already scopes runtime IDs and generations by active region and clears pending Troll target/marker on region change | Mostly already covered; compare behavior, do not copy | Verify each exact behavior against Safe Fixes tests; port only demonstrable missing cases |
| Offline PCAP region-scoped joins and evidence exports | Pattern commits 4244d6..., aaa3f22..., 412354..., etc.; AlbionPcapAudit.kt, ingest_capture.py, enrichment tools | Safe Fixes includes the audited capture pipeline but not all later region-aware analysis rules | Separate research-pipeline batch | Run on immutable raw PCAPs; compare source-archive/PCAP-scoped IDs, row counts and output hashes; never overwrite existing CSVs |
| Durable marker FIFO and network-handover recovery | Raw commit 3e0c139...; AlbionMarkerQueue.kt, CoreServiceManager.kt, AlbionCaptureWorker.kt | Safe Fixes worker syncs PCAP and JSONL file descriptors before acknowledging a marker, but has no durable queue / event-state tracker | Candidate architecture; not ready to port | Add tests for FIFO ordering, process restart, duplicate ID, partial PCAP/JSONL write, full queue, corrupt preferences, acknowledgement failure and network handover. Current pending() silently treats malformed JSON as an empty queue; this could hide queued data and must be fixed before adoption |
| Event-state snapshots for unknown Photon schemas | Raw commit 3e0c139...; AlbionEventStateTracker.kt | no equivalent state tracker | Research-only candidate | Preserve raw values and explicit unknown_schema; test bounded arrays/maps, reset between sessions, repeated values, nulls and memory limits; never infer inventory semantics from deltas alone |
| Raw UDP byte-pattern discovery UI | Raw commit c508c2...; AlbionLearningEngine.kt, AlbionRadarActivity.kt | absent from Safe Fixes | Optional experimental feature | Keep output explicitly heuristic; test PCAP endianness, IPv4/IPv6, fragments, UDP direction and empty/malformed captures. Candidate-port selection currently picks the most frequent observed port and can select a non-Albion service; never use it as a production decoder |
| Capture analyzer truncation / unknown-schema audit | Raw commit b75011...; analyzer + tests | Safe Fixes has different analyzer changes and return-code handling | Candidate after focused tests | Add tests for short PCAP headers, truncated packet records, captured-length overflow, both packet directions, unknown event counts, and reset across captures; preserve Safe Fixes operation response returnCode==0 logic |
| Workflow branch gates | Pattern/Raw workflow commits | Safe Fixes CI already passes | Do not copy workflow wholesale | Consolidate branch conditions only after all target branches are decided; avoid another long nested boolean expression and ensure source/data-only commits do not trigger unnecessary APK builds |
| Historical runtime assets / generated catalogs | Pattern data-ingest commits | Safe Fixes already regenerates selected runtime assets and validates the research index | Do not overwrite | Rebuild deterministically from immutable inputs; compare source hashes, generator version, schema, counts and unresolved labels before promotion |

### Verified test evidence and gaps

Pattern Mining has explicit tests for:
- Numeric Join / ChangeCluster IDs (Long and Int in addition to strings).
- Cross-region snapshot isolation and active-region scan.
- Same-region-only navigation and unknown-region blocking.
- Strict Troll identity proof and Ore Elemental precedence.
- Family 32 / T4 Event 40 navigation when mobile TypeID is absent.
- Target request before and after mob spawn, using a manually constructed single candidate ID.

Important limitation: target-before-spawn tests exercise the store with a pre-filtered candidateEntityIds = listOf(9901L). They do not prove that the live mapper extracts the correct single ID from the real Operation 23 parameter map. The mapper test itself includes additional integer parameters; because the mapper scans all numeric values, this is not sufficient evidence to safely enable the fallback.

Raw Pattern Discovery test coverage is incomplete for its newly introduced components:
- No test source file was found for AlbionMarkerQueue.
- No test source file was found for AlbionEventStateTracker.
- AlbionCaptureAnalyzerTest covers inbound/outbound port-side classification, but not truncated PCAP detection or unknown event schema counting.
- No test source file was found for AlbionLearningEngine raw UDP candidate discovery.

### Proposed transfer order

1. Port the active-region UI guard and strict Troll identity rule as one small safety batch, with Pattern tests adapted to Safe Fixes APIs.
2. Port numeric region ID scalar normalization while retaining the Safe Fixes successful-response gate.
3. Port family+valid-tier navigability for Event 40 only, retaining conflict/depletion safeguards. The Pattern test uses family 32 / T4; the user's previously verified Type 32, T4 node observation is consistent with testing this case, but does not justify generalizing beyond valid typed Event 40 evidence.
4. Re-evaluate target-before-spawn only after exact Operation 23 wire parameter identification.
5. Add marker FIFO / event tracker / raw UDP discovery as separate research or reliability PRs only after test coverage and corruption handling.
6. Regenerate research outputs in a new versioned output directory; compare manifests and row-level deltas before considering promotion.

No source commit was cherry-picked, merged, or copied by this assessment. The transfer plan preserves all original branch histories and file versions.

## 24. Change log — safe-transfer assessment

- Operation: append semantic transfer assessment, test-coverage review, and ordered integration plan.
- Repository: eesanezhad76/v2rayNG
- Source document blob SHA: 5fc53a40e5d64084858aad04a8fff9652e00a965
- Destination branch: albion-integration-staging-2026-11-05
- No application source, tests, runtime assets, research outputs, raw evidence, or pre-existing branches changed.
- Resulting commit SHA: d75c12484a7646cb36ef5e09bf28dfbfb19e8eaf
- New blob SHA: 2a1685ef1a884c8495eca1a0dde72ad08757f607


## 25. Change log — reference correction and verification

- Operation: correct the recorded commit/blob identifiers in Sections 19, 22 and 24; replace accidental literal newline escape sequences with normal line breaks.
- Repository: eesanezhad76/v2rayNG
- Source document blob SHA: 77066f4737b57df7a51923d093f1190eab1c9a8f
- Destination branch: albion-integration-staging-2026-11-05
- Resulting commit SHA: 8709a3d8bf783c944676f44412375f388dce414b
- New blob SHA: 6a7c3f364badecda60039c7cd65d594bf182a1d2
- No application source, tests, runtime assets, research outputs, raw evidence, archive parts, or pre-existing branches changed.


## 26. Complete repository branch snapshot — refreshed after audit branches

A fresh GitHub branch enumeration returned **44 branches**. This section supersedes the earlier partial branch inventory in Section 1. Every branch is preserved; equal head SHAs identify aliases/checkpoints, not deletion candidates.

| Branch | Head commit SHA |
|---|---|
| `albion-archive-audit-2026-11-05` | `cb38229cfa41f60af89cb3f325347b9785778cfb` |
| `albion-database-integration-completion` | `2b1097c3cafe2390cdcc522d586c90cab59715fa` |
| `albion-db-runtime-wiring-2026-11-05` | `a611c8125c91c3d6beb655897dc7e58745ac00fb` |
| `albion-decoder-lab` | `77a8c3e2c58761d6bd2d40aa8eebef0cb32068a5` |
| `albion-decoder-lab-failed-merge-backup` | `0a21332de0bfc2f70c4982eb4af7a35d051b5d8e` |
| `albion-decoder-radar` | `ac7e90cd29a874ccd367cf7ea126372f8920a96a` |
| `albion-decoder-radar-ci` | `023705a103ccff3ccb6b59d5bd71a5bfc882252c` |
| `albion-decoder-radar-rebuild` | `77a8c3e2c58761d6bd2d40aa8eebef0cb32068a5` |
| `albion-integration-staging-2026-11-05` | `76284e75f7bfacb585003390d725cdedcdc11481` |
| `albion-integrity-autosave` | `39a125b9de84a9101e610f33deb663095e8c10c7` |
| `albion-knowledge-integration-2026-11-04` | `8a5cc3905fc0fb6f621e1d751d9ed3d058eac833` |
| `albion-pattern-mining` | `a453c812483f285dcae97d0ad0bc044c0cec11fa` |
| `albion-radar` | `2a34a8473643cb0084057ac535a2cf8bdbdfb391` |
| `albion-radar-fix-2026-11-03` | `53efd9d2815f15b36e00b6d2c7094bce863f79fd` |
| `albion-radar-phase1-remediation` | `d0f4845a3cf740a0e726ad760b3f16fa4dafce03` |
| `albion-raw-pattern-discovery-2026-11-05` | `c508c2b54eecced896862e401732e2e0ce44ce29` |
| `albion-reliability-audit-2026-11-05` | `b75011e6ab9d605bd0ae687fea9dfe55353ab930` |
| `albion-reliability-runtime-wiring-2026-11-05` | `3e0c139641be7cce12209c5f7c3e5ac0a87ca549` |
| `albion-reliability-ux` | `506d24730c51d4debd434ffaf652ad81d482b748` |
| `albion-safe-fixes-2026-11-05` | `c51481253ff4b5b0bd5bd0223a9e6a2674af680a` |
| `albion-safe-integration-2026-11-05` | `a4282483d6a4f40eabbd74aabda8a04e4bf61ae5` |
| `albion-v2rayng-2.2.6-port` | `d41804beecec085eb5f774009ac47935cfa03b24` |
| `albion-zone-evidence-phase6` | `8d9d536b173b2e7f410b4b4393c8999da62dd5f4` |
| `albion-zone-forensics` | `a420346a14b0f7a5261fcc8f28489242f9d48c3d` |
| `albion-zone-forensics-integration-2026-11-05` | `436e7f60eea014977d01c2b570d52d3017691b97` |
| `audit/detection-engine-2026-11-04` | `10d3a0a0f6caea55f31ad3d61de4e546b36189a0` |
| `backup/albion-capture-e3f95d9` | `e3f95d9e03b08a9e31a4d0467a6d8cc224773831` |
| `backup/albion-decoder-lab-before-2.2.6` | `f31ba8d3318d929eb1b22e0e94297112d08bf193` |
| `backup/albion-radar-2026-10-31` | `e3f95d9e03b08a9e31a4d0467a6d8cc224773831` |
| `checkpoint/albion-radar-stable-2026-11-04` | `9db20f3334dad17b86883b20061e2f2ace637f51` |
| `master` | `d4542fadfee6d3b40ac3a65053d3b7031c602f16` |
| `safety/database-integration-2026-11-05` | `2b1097c3cafe2390cdcc522d586c90cab59715fa` |
| `safety/db-runtime-wiring-2026-11-05` | `a611c8125c91c3d6beb655897dc7e58745ac00fb` |
| `safety/integration-after-knowledge-2026-11-05` | `6fa9d10555aa2ab5e36f64ca831f0e4869faaeb0` |
| `safety/integration-after-master-2026-11-05` | `dba99f7e8c1421b2483c0f0136ed74537c6b197f` |
| `safety/knowledge-2026-11-05` | `8a5cc3905fc0fb6f621e1d751d9ed3d058eac833` |
| `safety/master-2026-11-05` | `ec7ed4278896336b24f8c2152345702687acb30a` |
| `safety/pattern-mining-2026-11-05` | `e5941f67a0cf8b0651298450611a9e25065add45` |
| `safety/reliability-audit-2026-11-05` | `b75011e6ab9d605bd0ae687fea9dfe55353ab930` |
| `safety/reliability-runtime-wiring-2026-11-05` | `3e0c139641be7cce12209c5f7c3e5ac0a87ca549` |
| `safety/reliability-ux-2026-11-05` | `506d24730c51d4debd434ffaf652ad81d482b748` |
| `safety/remediation-2026-11-05` | `d0f4845a3cf740a0e726ad760b3f16fa4dafce03` |
| `safety/zone-evidence-2026-11-05` | `8d9d536b173b2e7f410b4b4393c8999da62dd5f4` |
| `safety/zone-forensics-integration-2026-11-05` | `c7e8cb21e8878385e4b7b9fa3db8aa2cd1d9e549` |

The two audit branches created during this work are:
- `albion-integration-staging-2026-11-05`: isolated documentation/transfer plan based on Safe Fixes.
- `albion-archive-audit-2026-11-05`: read-only archive verification branch based on Pattern Mining.

The complete snapshot also includes `checkpoint/albion-radar-stable-2026-11-04`, which was absent from the initial branch summary and is now explicitly recorded. No branch was removed, renamed, force-pushed, or merged.

## 27. Change log — complete branch snapshot

- Operation: append complete branch inventory and current head SHAs for all 44 branches.
- Repository: eesanezhad76/v2rayNG
- Source document blob SHA: e113cb37785e832fc40758032975b7f1b384c2a5
- Destination branch: albion-integration-staging-2026-11-05
- No pre-existing branch or application/data file changed.
- Resulting commit SHA: 2bc49f3edd500da355a23ee9a79d866eae2d820d
- New blob SHA: 3ab9b8353be4e2d0a6521d975337d84b5cc65645


## 28. Change log — final hash-reference correction

- Operation: correct Section 25's source/result identifiers after verifying the preceding GitHub update response.
- Repository: eesanezhad76/v2rayNG
- Source document blob SHA: 6a7c3f364badecda60039c7cd65d594bf182a1d2
- Destination branch: albion-integration-staging-2026-11-05
- Resulting commit SHA: 76284e75f7bfacb585003390d725cdedcdc11481
- New blob SHA: e113cb37785e832fc40758032975b7f1b384c2a5
- No application source, tests, runtime assets, research outputs, raw evidence, archive parts, or pre-existing branches changed.


## 29. Raw 45-part archive — completed 7z and member-integrity verification

Read-only audit branch:
- Branch: `albion-archive-audit-2026-11-05`
- Source snapshot: Pattern Mining commit `a453c812483f285dcae97d0ad0bc044c0cec11fa`
- Audit workflow file: `.github/workflows/albion-archive-audit.yml`
- Workflow run: `42087590613` (completed, success)
- Job: `127456762723` (success)
- Artifact: `albion-archive-integrity-audit`, ID `13444361039`

Verified results from the uploaded artifact:
- All 45 source parts present, sequence complete, total 465,164,657 bytes.
- 7-Zip 23.01 recognized the archive as a 45-volume multi-volume 7z and reported `Everything is Ok`.
- Archive contained 9 files, total uncompressed member bytes 797,745,828.
- Extracted members: 4 PCAP, 4 JSONL, 1 report.
- All 9 extracted files matched the exact byte size and SHA-256 recorded in `research/analysis/archiveNew-extracted-forensic-inventory.md`.
- The six Master files (026–031) were compared byte-for-byte against the corresponding Pattern Mining split parts; all six matched. Their computed SHA-256 values are recorded in the artifact.
- A SHA-256 list for all 45 raw split parts is retained in the same artifact.
- No raw source part or Master file was rewritten, moved, or deleted.

Manifest boundary:
- The complete database manifest lists 14 ZIP archives, including the derived `archiveNew-extracted.zip`; it does **not** define SHA-256 values for the 45 raw 7z split parts.
- Therefore the raw split archive was validated by 7z multi-volume CRC/integrity testing, extracted-member SHA-256 comparison, and byte-for-byte Master duplicate checks.
- The manifest's outer ZIP record is 797,747,472 bytes with SHA-256 `8c93960e9a036f63d2d822d91c6d5288bb1cb565c0acdfb70add61ef79e2e1c7`. The nine extracted members match, but the outer ZIP container hash was not recomputed by this audit.

The enhanced read-only audit run `42087978698` completed successfully (head `cb38229cfa41f60af89cb3f325347b9785778cfb`). It verified **all 13 original ZIP inputs** against the complete database manifest by exact byte size and SHA-256, including the modified `AlbionRadar.zip`. Final artifact ID: `13443078504` (6,908 bytes). The workflow keeps temporary Master copies outside the uploaded artifact. Thus 13 of the manifest's 14 ZIP entries were verified as source ZIP containers; the remaining derived `archiveNew-extracted.zip` was verified at member level (9/9 files) but its outer ZIP container hash was not recomputed.

## 30. Change log — archive audit branch and verified evidence

- Operation: create isolated archive-audit branch from Pattern Mining; add and refine a read-only workflow; run 7z and member-integrity verification.
- Repository: `eesanezhad76/v2rayNG`
- New branch: `albion-archive-audit-2026-11-05`, source commit `a453c812483f285dcae97d0ad0bc044c0cec11fa`
- Workflow file creation commit: `27d212a5c28350ac9c5ca82e72c9c679365bb422`
- Workflow correction commit: `27857cf19fc3541030b3addba9af8cd1527758ef`
- ZIP-manifest validation commit: `aedc0f55ae3929aaf869295d66a0ae5e39dcc3ff`
- Artifact-size cleanup commit: `cb38229cfa41f60af89cb3f325347b9785778cfb`
- Latest audit branch head at this record: `cb38229cfa41f60af89cb3f325347b9785778cfb`
- Latest workflow blob SHA: `9dc3f1c5dab26b8bf2f4f16c94824f57aa3d5628`
- Initial successful 7z/member audit run: `42087590613`; artifact ID `13444361039`.
- Final successful full audit run: `42087978698`; artifact ID `13443078504`; 13/13 original ZIPs match the manifest.
- The audit workflow uses `contents: read`; it writes no repository data and commits no extracted output.


## 31. Change log — final archive manifest result

- Operation: update Section 29 with completed full audit results after inspecting the final Actions artifact.
- Repository: eesanezhad76/v2rayNG
- Source document blob SHA: e3cda59fa88788b21aeba289fceb8c2686fe78cc
- Destination branch: albion-integration-staging-2026-11-05
- Archive audit run: 42087978698 (success); artifact: 13443078504
- Verified: 45/45 split parts, 7z CRC/integrity, 9/9 extracted member hashes, 6/6 Master duplicate comparisons, 13/13 original ZIP hashes/sizes against the complete database manifest.
- Resulting commit SHA: 427d4a36cdce2bd600ebcb6de003c3a9cc92a82e
- New blob SHA: bd6c70c53ee3ad5287db196ca18ccf2ac3513907
- No source data or pre-existing branch changed.


## 32. Reliability UX / PR #7 and stacked PR #8–#9 audit

### PR #7 — historical reliability UX source

- PR: `#7`, “Integrate Albion reliability, marker queue and capture recovery”.
- State: open, draft, not merged, `mergeable=false`.
- Head: `albion-reliability-ux` / `506d24730c51d4debd434ffaf652ad81d482b748`.
- Base snapshot recorded by PR: `albion-safe-integration-2026-11-05` / `dba99f7e8c1421b2483c0f0136ed74537c6b197f`.
- Current Safe Integration head: `a4282483d6a4f40eabbd74aabda8a04e4bf61ae5`, which is 163 commits ahead of that old PR base.
- PR #7 has 59 commits, 15 changed files, +1,208 / -89 lines. Compared with current Safe Integration, it diverges at `39a125b9de84a9101e610f33deb663095e8c10c7` and is 59 commits ahead / 1,024 behind; it is not a safe merge candidate.
- Its 15 changed paths are:
  - `.github/workflows/build.yml`
  - `.github/workflows/validate-albion-sources.yml`
  - `AlbionCaptureAnalyzer.kt`
  - `AlbionCaptureMarker.kt`
  - `AlbionCaptureWorker.kt`
  - `AlbionEventStateTracker.kt`
  - `AlbionMarkerQueue.kt`
  - `AlbionPatternMiner.kt`
  - `AlbionProtocol18Decoder.kt`
  - `CoreServiceManager.kt`
  - `AlbionOverlayService.kt`
  - `AlbionCaptureAnalyzerTest.kt`
  - `AlbionPatternMinerTest.kt`
  - `AlbionPhotonPacketDecoderTest.kt`
  - `docs/ALBION_RADAR_ANDROID.md`

PR #7 contains two distinct research approaches that must not be conflated:
- `AlbionPatternMiner`: cross-capture numeric JSONL field-path recurrence discovery; its test requires recurrence across at least three independent capture files.
- Raw Pattern Discovery's `AlbionLearningEngine`: unsupervised UDP payload byte-pattern analysis directly from PCAP bytes.
These are different inputs, algorithms and outputs. Keep both experimental and separate until independently validated.

### PR #8 — research index / runtime provenance

- PR: `#8`, “Regenerate research index and wire historical knowledge into live snapshots”.
- State: open, draft, not merged, `mergeable=false`.
- Head: `albion-db-runtime-wiring-2026-11-05` / `a611c8125c91c3d6beb655897dc7e58745ac00fb`.
- Base snapshot: `6fa9d10555aa2ab5e36f64ca831f0e4869faaeb0`; current Safe Integration is 10 commits ahead of that base.
- 2 commits, 5 changed files:
  - `.github/workflows/build.yml`
  - `AlbionLiveKnowledgeStore.kt`
  - `ResourcePatternLearner.kt`
  - `research/tools/generate_research_knowledge_index.py`
  - `research/tools/validate_runtime_knowledge_assets.py`
- It regenerates the runtime research index from 406 resource rows, 2,956 mobile rows and 67 TypeIDs, and adds read-only research provenance to current-session snapshots. It explicitly does not project historical coordinates onto the live map.
- This branch is not a replacement for Pattern Mining's raw evidence/database tree; it is a runtime knowledge wiring subset.

### PR #9 — selective marker queue / capture recovery stack

- PR: `#9`, “Integrate durable marker queue and capture recovery into live radar”.
- State: open, draft, not merged, `mergeable=false`.
- Head: `albion-reliability-runtime-wiring-2026-11-05` / `3e0c139641be7cce12209c5f7c3e5ac0a87ca549`.
- Base snapshot: `6fa9d10555aa2ab5e36f64ca831f0e4869faaeb0`.
- 3 PR commits: the 2 PR #8 commits plus one reliability-wiring commit. Compared with PR #8 head, it is exactly 1 commit ahead / 0 behind and adds the seven runtime reliability paths listed below:
  - `AlbionCaptureWorker.kt`
  - `AlbionEventStateTracker.kt`
  - `AlbionMarkerQueue.kt`
  - `AlbionProtocol18Decoder.kt`
  - `CoreServiceManager.kt`
  - `AlbionOverlayService.kt`
  - `.github/workflows/build.yml`
- PR #9's total changed-file set is 11 because it retains the five PR #8 research-index files as part of the stack.
- Zone Forensics Integration is 4 commits beyond PR #9, and Raw Pattern Discovery is 1 commit beyond Zone Forensics Integration. This is the same 7-commit reliability/DB/zone chain plus one raw UDP discovery commit documented in Sections 11 and 17.
- No successful CI run for the PR #9 head was found in the latest Actions run inventory inspected during this audit. Do not mark PR #9 validated from PR #12's CI; PR #12 builds a different head.

### Integration decision

- PR #7 remains a historical source branch; do not merge it wholesale or rebase it in place.
- PR #8 and PR #9 are stacked drafts based on an older Safe Integration snapshot; do not merge them directly into the current Safe Integration branch.
- Raw Pattern Discovery is the latest descendant of the reliability/DB/zone stack, but it also must not be merged wholesale into Safe Fixes.
- Reuse only the semantically reviewed candidate changes from Section 23, with fresh tests against the current Safe Fixes head. Preserve PR #7, #8, #9 and all source branches as immutable references until a separately validated integration PR is approved.

## 33. Change log — reliability PR stack audit

- Operation: inspect PR #7, #8 and #9 metadata, changed-file lists, ancestry and current-base divergence; append integration disposition.
- Repository: eesanezhad76/v2rayNG
- Source document blob SHA: fc7343cf5df57364b79e688dbb9d4d4ea5b8ca54
- Destination branch: albion-integration-staging-2026-11-05
- No PR was merged or modified; no source branch or application/data file changed.
- Resulting commit SHA: 56bee732f24cf455fdffb8f6c60ea64cc9d0bd18
- New blob SHA: d48446b3b9e1c792150e15eca1609433a4e15652


## 34. Change log — archive and PR audit log formatting

- Operation: replace accidental literal newline escapes in Sections 30–31 and record verified Section 33 commit/blob identifiers.
- Repository: eesanezhad76/v2rayNG
- Source document blob SHA: d48446b3b9e1c792150e15eca1609433a4e15652
- Destination branch: albion-integration-staging-2026-11-05
- Resulting commit SHA: 0bb3ab9cdc27b621a14d69416ce965de321e77ed
- New blob SHA: ddf380e46242c882197f6689fba357fe8b5d4fb3
- No source branch, PR, application code, tests, runtime assets, raw evidence, archive or derived data changed.


## 35. Change log — verified formatting correction

- Operation: replace the remaining literal newline escapes and finalize Section 34 identifiers.
- Repository: eesanezhad76/v2rayNG
- Source document blob SHA: ddf380e46242c882197f6689fba357fe8b5d4fb3
- Destination branch: albion-integration-staging-2026-11-05
- Resulting commit SHA: ceed59bb34915ea0380ff2086aee911213b95f91
- New blob SHA: 284d4e03532b90a2fbd4b09a48a2c39b7695e148
- No source branch, PR, application code, tests, runtime assets, raw evidence, archive or derived data changed.


## 36. Change log — global line-break normalization

- Operation: replace all remaining literal backslash-n sequences in the staging register with actual line breaks; finalize Section 35 identifiers.
- Repository: eesanezhad76/v2rayNG
- Source document blob SHA: 284d4e03532b90a2fbd4b09a48a2c39b7695e148
- Destination branch: albion-integration-staging-2026-11-05
- Resulting commit SHA: 1500b389c48de16289732bb55486d0936ffb4a35
- New blob SHA: d43895946f3535725092318863ffa661e3673f75
- No source branch, PR, application code, tests, runtime assets, raw evidence, archive or derived data changed.


## 37. Complete open-PR inventory and CI failure evidence

A fresh repository query returned eight open PRs. No PR was merged during this audit.

| PR | Head → base | State | Commits / files | Mergeable | Current disposition |
|---|---|---|---:|---|---|
| #2 | `albion-decoder-radar-ci` → `albion-decoder-lab` | open, not draft | 1 / 1 | true | isolated CI-trigger-only change; old run failed because Actions secret `encodedString` was unset, not because of source compilation |
| #3 | `albion-pattern-mining` → `albion-reliability-ux` | open, draft | 1,145 / 632 | false | enormous stale-base integration; +6,300,508 / -182 lines; do not merge |
| #7 | `albion-reliability-ux` → Safe Integration | open, draft | 59 / 15 | false | historical reliability UX source; compile failure in duplicate declarations in `AlbionPatternMiner.kt` (lines 81 and 143) |
| #8 | `albion-db-runtime-wiring` → Safe Integration | open, draft | 2 / 5 | false | stacked research-index/runtime-provenance draft; CI failure |
| #9 | `albion-reliability-runtime-wiring` → Safe Integration | open, draft | 3 / 11 | false | stacked marker queue/capture recovery draft; CI failure |
| #10 | `albion-reliability-audit` → Safe Integration | open, draft | 4 / 15 | false | reliability/PCAP audit draft; CI failure |
| #11 | `albion-zone-forensics-integration` → Safe Integration | open, draft | 7 / 20 | false | zone forensics draft; CI failure |
| #12 | `albion-safe-fixes-2026-11-05` → Safe Integration | open, draft | 36 / 17 | true | only currently validated runtime integration PR; CI success, but field readiness still blocked |

### Historical CI results by PR head

- PR #2 / run `41302637938`: stopped at `Decode Keystore`; log says `encodedString value is not set`. This is missing Actions secret/configuration, not evidence of a decoder-code failure.
- PR #3 / run `42044414509`: capture-ingest workflow failed in `SpatialAlignmentTests.test_same_pcap_regions_are_never_spatially_joined` with `KeyError: 'MAP_A'`. The test fixture/map inventory relationship is unresolved; do not treat the branch's ingest pipeline as passing.
- PR #7 / run `41530238126`: Kotlin compilation failed in `AlbionPatternMiner.kt` due conflicting declarations at lines 81 and 143. Unit tests were not reached.
- PR #8 / run `41944493624`: 276 tests, 1 failed — `AlbionResearchKnowledgeIndexTest.parsesCompleteResearchIndexAndReportsSourceCounts` (line 76); Python runtime asset validation also failed with `family 28 type mapping mismatch`.
- PR #9 / run `41944965587`: same knowledge-index count failure and Family 28 type-mapping mismatch.
- PR #10 / run `41945615354`: 278 tests, 4 failed — `AlbionCaptureAnalyzerTest.reportsLocalOverlayCheckpointsFromPcap`, `AlbionCaptureReplayEngineTest.replaysCommittedInboxCapturesThroughKnowledgeEvidenceAndOverlayParser`, `AlbionPhotonPacketDecoderTest.mapsDungeonExitCoordinates`, and the research-index count test. Python validation also reported Family 28 mapping mismatch.
- PR #11 / run `41946500963`: 278 tests, 2 failed — the capture analyzer checkpoint test and dungeon-exit coordinate test. Python validation reported Mountain Rabbit ID 482 was not explicitly non-harvestable and the zone matcher returned `null` instead of `cluster_A`.
- PR #11 zone-audit workflow / run `41946500978`: failed the same portal/zone matching expectation (`cluster_A` vs `None`).
- Raw Pattern Discovery / run `41946883759`: 278 tests, 2 failed — capture analyzer checkpoint and dungeon-exit coordinate tests; Python validation repeated the Mountain Rabbit and `cluster_A` failures.
- PR #12 / run `42080576281`: 282 tests passed, 40 Python tests passed, APK verification and emulator installation passed. Readiness remains `NOT_READY_FOR_FIELD_TEST` because replay/live evidence is not verified.

### What this means for integration

1. PR #2 is technically mergeable but belongs to the isolated decoder-lab line; it is not part of the Safe Integration plan.
2. PR #3 is not a reasonable merge unit. Its 632-file, 6.3-million-line diff is largely the consequence of a stale base and includes source, tests, assets, docs and research payload. Use the 121-commit / 285-path Pattern Mining vs Safe Integration inventory in Sections 16 and 20 instead.
3. PR #7–#11 are stacked historical drafts, not five independent merge requests. Their ancestry is PR8 → PR9 → PR10 → PR11 → Raw Pattern Discovery. The failed CI runs show that their current snapshots cannot be promoted as-is.
4. PR #12 is the only head with a green full build in this audit. Its green status must not be extended to PR #7–#11 or to the Pattern Mining/Raw branches.
5. Do not merge PR #2 or #12 automatically; do not merge PR #3 or #7–#11 wholesale. Preserve every branch and PR as an audit source.

## 38. Change log — open PR and CI inventory

- Operation: enumerate all open PRs; inspect head/base snapshots, file/commit counts, mergeability and historical Actions failures; append evidence-backed disposition.
- Repository: eesanezhad76/v2rayNG
- Source document blob SHA: 60290db0451965a1b1816254263790731d39814d
- Destination branch: albion-integration-staging-2026-11-05
- No PR was merged or edited; no source branch or application/data file changed.
- Resulting commit SHA: 86ee1119f4e60d141b517c32f37855137b82baa4
- New blob SHA: 557f2277e5da97fb9df069d2da6bbb424bac41cd
