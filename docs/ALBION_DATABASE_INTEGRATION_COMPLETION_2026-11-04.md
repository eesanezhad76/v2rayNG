# Albion Radar database integration audit — 2026-11-04

## Scope

This audit connects the current research index, the imported four-bundle historical corpus, the external mobile-type dictionary, the saved capture-local catalog, the live entity store, the autonomous pattern learner, and the protocol evidence diagnostics. It deliberately does not treat historical coordinates or unverified protocol shapes as live map facts.

## Evidence inventory

| Dataset | Audited count | Runtime treatment |
|---|---:|---|
| Research resource observations | 331 | Evidence by family; only packet-confirmed family 29 is promoted |
| Research mobile observations | 2,330 | Crosswalk and observed type evidence |
| Observed mobile type IDs | 64 | All 64 must exist in the shipped external dictionary |
| Historical imported bundles | 4 | Provenance source |
| Historical entity observations | 9,522 | Grouped by mobile type and resource family |
| Historical protocol events | 4,599 | Grouped by observed event/parameter shape; semantics remain unassigned |
| Historical learned patterns | 70 | Exact family/tier/enchantment references only |
| Historical autonomous signatures | 296 | Seeded into the online learner as candidate evidence |
| Saved capture-local catalog | 83 resources, 656 mobs, 56 markers | Kept separate from historical evidence; zone identity remains unresolved |

## Runtime links

1. `AlbionResearchKnowledgeIndex` loads the current research index and the observed mobile dictionary.
2. `AlbionHistoricalKnowledgeIndex` loads the generated historical entity, protocol-shape, learned-pattern and manifest assets.
3. `AlbionLiveKnowledgeStore` annotates typed live observations with historical counts and provenance; it does not replace packet-derived names, types, or coordinates.
4. `AlbionCaptureWorker` loads `albion-historical-autonomous-patterns.json` and passes it into `AlbionAutonomousPatternLearner`, which merges historical candidate signatures with live signatures.
5. `AlbionProtocolEvidenceStore` reports the historical corpus and its most frequent parameter shapes alongside current protocol evidence.
6. `AlbionOverlayService` displays separate research, dictionary, historical-memory and saved-map counters.

## Safety invariants

- Family 29 is the only resource family treated as packet-confirmed living Ore Elemental evidence.
- Families 7, 14, 20 and 27 remain provisional; family 28 remains unresolved.
- Mobile Type ID 482 remains Mountain Rabbit T1 and non-harvestable; it is not Hide.
- Historical labels and protocol shapes are provenance/reference evidence, not semantic classification.
- Historical coordinates are never projected onto the live map.
- Runtime entity IDs remain capture-session scoped until a zone identity and coordinate transform are verified.
- Event 1 is entity Leave, not a zone transition. Event 2 remains an unmapped candidate pending repeated controlled captures.

## CI audit and corrections

The last completed build before the latest fixes failed before APK assembly for three independent reasons:

1. The historical seed generator created its output directory relative to the runner working directory instead of the supplied temporary test root. Fixed by creating `root / ASSETS`.
2. The runtime validator incorrectly required the external dictionary to contain exactly the 64 observed IDs. The dictionary is broader; validation now requires complete inclusion of the 64 observed IDs and reports coverage against that observed set.
3. The integration branch was missing from the workflow's debug-build exclusions, causing the release-tag lookup to run against the passive-tap branch checkout, which has no tags. The branch is now routed through the isolated debug APK path and skips the release tag/download steps.

The next CI run must confirm Python tests, Gradle unit tests, APK assembly, offline PCAP replay, and the readiness report. A successful build has not yet been verified as of this document's creation.

## Archive mirror status

The private archive repository is `eesanezhad76/albion-radar-archive`. Its latest inventory records 273 of 356 source research files copied with matching Git blob SHA-1 values; 83 remain outstanding, including large JSON/JSONL/CSV/Markdown datasets and binary ZIP/GZIP files. Six of the twelve original ZIP captures are in `raw/`. The user's encrypted offline archive was reported as passing a 7-Zip integrity check, but a clean-directory restore has not been independently verified in this session.

The source repository `eesanezhad76/v2rayNG` remains Public; the archive repository is Private. Do not claim full privacy migration or a complete archive until the remaining transfer, source-visibility change, and restore verification are completed.

## Release gate

Do not merge PR #4 or claim a release-ready APK until:
- the latest branch CI run succeeds;
- generated historical assets and their source SHA-256 manifest validate;
- the APK and test artifacts are available and checksummed;
- the manual zone-arrival marker is tested in a real capture;
- zone identity is established only from repeatable controlled evidence.
