# Albion overlay capture-index integration — 2026-11-04

## Verified source data

The app's existing `albion-mobile-type-dictionary.json` is not empty. Its Git blob is 2,457,806 bytes and contains 5,479 entries. The discovery report lists 64 distinct captured mobile TypeIDs; all 64 are present in the full dictionary.

The previous blank response from the GitHub file-content reader was a retrieval limitation for the large file, not evidence that the repository blob was empty. The external OpenRadar dictionary was retained unchanged.

## New app asset

Added `V2rayNG/app/src/main/assets/albion-research-catalog-index.json` as a capture-scoped supplement to the original static catalog.

- 20 capture groups with spatial entities
- 331 Event 40 resource-node records
- 2,330 Event 123 mobile-entity lifecycle records
- 2,661 entity records total
- 358 manual marker events retained
- 109 resource-marker candidate correlations retained
- 29 mob/attack-target correlations retained
- 6 additional capture groups with marker/correlation evidence but no spatial entity records retained separately
- The original `AlbionRadar.zip/albion-1793682590317.pcap` catalog retains its 56 marker records, 27 attack-target records, and user-confirmed target evidence.

Every entity receives a capture-scoped ID and retains its source archive and PCAP. The index does not flatten coordinates across captures.

## Overlay behavior

The saved-catalog view now loads one capture group at a time. The capture counter button advances to the next saved capture; the SAVED/LIVE control switches between that selected historical capture and the live snapshot. The selected saved capture is explicitly labelled `ZONE UNVERIFIED` with its source archive and PCAP.

The full external mobile dictionary remains the shared mob enrichment source for both saved and live entities. Historical capture coordinates are never merged into the live player-centered snapshot. Candidate marker correlations remain evidence metadata and are not promoted to confirmed entity identity.

## Validation

Added `research/tests/test_overlay_research_catalog_index.py` to verify dictionary coverage, record counts, finite coordinates, capture-scoped IDs, retained marker evidence, and the unverified-zone guardrail.

A GitHub Actions APK build and the Albion ingest/validation workflow were started for this integration. Their final outcomes must be checked before treating the build as complete.

The snapshot label resolver also consumes the research catalog's source-derived resource names. Static reference mappings are marked provisional with a trailing `?`; packet-confirmed living-resource names remain unmarked. This prevents the new catalog records from appearing as anonymous family-only labels in the overlay.
