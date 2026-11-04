# Albion Radar — Phase 1 remediation status

Date: 2026-11-04

Branch: `albion-radar-phase1-remediation`

Base: `albion-pattern-mining` at `e5941f67a0cf8b0651298450611a9e25065add45`

## Changes made

1. Added `AlbionMapProjection` as the shared world-to-screen transform for the mini radar and full map.
2. Added a movement-direction vector derived from the latest distinct observed player coordinates. The vector is rendered as a cyan arrow on the black player marker. This deliberately avoids assuming the game's heading-angle convention.
3. Kept the mini radar at a stable local range so a single distant node cannot zoom out nearby resources. Distant known nodes are clamped to the radar rim; long-range inspection belongs on the full map.
4. Removed the 2,000-entity truncation from the current-session coordinate snapshot. The snapshot remains bounded by the store's existing 25,000-record cap and remains restricted to the current capture session.
5. Added focal-point-preserving pinch zoom, Fit All Known and Center Player controls to the full map.
6. Added nearest-known-active-resource guidance: a direct bearing line and edge indicator remain visible when the target is outside the current viewport. This is a straight-line bearing, not a pathfinding route.
7. Made depleted/left resources visible in the full map in grey rather than silently omitting them.
8. Added a separate Saved Capture view using the bundled `albion-static-catalog.json`. It is never merged into live player coordinates. The bundled dataset contains 83 resource rows and 656 mob rows (739 rows total).
9. Added tests for movement direction in all four coordinate directions and for snapshots containing more than 2,000 current-session entities.

## Evidence and limitations

- The bundled static catalog declares its coordinates capture-local and explicitly says zone identity is not decoded.
- Its 83 resource rows and 656 mob rows therefore cannot be asserted to belong to the player's current zone. The UI labels this as a saved capture with unverified zone identity.
- The current-session live snapshot is not a cross-session region database. Runtime entity IDs are session-scoped; merging sessions without a validated zone key could put nodes from different zones on top of one another.
- No code can reveal a node that was never observed or whose data was never delivered to the client. This implementation exposes all stored, coordinate-valid records in the active session and the separately selectable bundled saved capture.
- World +Y up / world +X right preserves the existing north-up transform. The new arrow follows coordinate deltas, so it stays consistent with the same transform. The physical in-game orientation still requires a four-direction device test.

## Tests

Added:
- `movementDirectionUsesLatestDistinctWorldCoordinatesInScreenSpace`
- `returnsNoMovementDirectionForStationaryOrInsufficientHistory`
- `coordinateSnapshotIncludesMoreThanTwoThousandKnownNodesFromTheCurrentSession`

**Execution status:** Not yet run. GitHub Actions returned zero workflow runs for this branch in the available API view. A successful build, unit-test run, APK installation, and physical-device test must not be inferred from the commits alone.

## Acceptance tests still required

1. Build `testPlaystoreDebugUnitTest` and `assemblePlaystoreDebug`.
2. Verify the movement arrow while walking north, east, south, and west in one known zone.
3. Verify node positions remain fixed relative to world coordinates during movement.
4. Verify pinch zoom preserves the map point under the pinch centroid.
5. Verify Fit All Known shows every current-session coordinate-valid entity beyond row 2,000.
6. Verify the saved-capture view displays 83 resource rows and 656 mob rows without merging them into live coordinates.
7. Verify depleted resources are grey and active resources retain their enchantment colors.
8. Confirm nearest-target bearing with an actual known node and compare the displayed distance against captured world coordinates.

## Release policy

No merge to `albion-pattern-mining`, no replacement of the existing APK, and no Stable declaration until CI and the physical-device acceptance tests pass.
