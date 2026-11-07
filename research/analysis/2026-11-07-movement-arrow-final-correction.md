# AlbionRadar movement-arrow final correction — 2026-11-07

## Applied change
The movement arrow now uses an independently named `movementToScreen` transform rather than calling the map/node `worldToScreen` function. Both transforms currently use the same capture-supported calibration, but the movement arrow is no longer architecturally coupled to future map/node bearing changes.

## Calibration retained
- Capture: `AlbionRadar11.zip::albion-1794058383031.pcap`
- Region: 4207
- The four timestamp-regression path fits give UP-axis bearings 139.072°, 140.011° (DOWN reversed), 139.954° and 140.238°.
- Mean marked UI-UP bearing: 139.819°; standard deviation 0.513°.
- Retained clockwise correction: 49.819°. This aligns the marked UI-UP input axis with screen-up. It is not an independently verified cardinal-north calibration because PCAP alone cannot separate systematic joystick/touch bias from map-orientation bias.
- Do not replace this measured 49.819° with the earlier guessed 45° formula.

## Mount / Dismount handling
- Mount: 1794059660855–1794059664816 (3,961 ms).
- Dismount: 1794059669540–1794059672903 (3,363 ms).
- Both are in region 4207 and contain zero Move(22) position samples.
- They are state-transition windows only; they are not stationary samples, turn vectors or movement-direction calibration data.
- Four nearby Event 210 `player_mounted` observations include runtime IDs 90376 and 93378. The replay inferred no local-player entity ID, so those IDs cannot be attributed to the local player.
- Therefore no runtime mount-state filter was added to the local movement arrow. Doing so would risk suppressing or segmenting the local player's history based on another entity's mounted event.
- Runtime arrow remains based on outbound own-player Operation 22 position history only.

## Regression coverage
- Existing four world-axis projection assertions retained.
- Added one test with exact first/last Move(22) coordinates from all four marked UP1/DOWN1/UP2/DOWN2 intervals. It asserts both UP intervals map to screen-up and both DOWN intervals map to screen-down within 2° tolerance.
- Existing stale-history (3 seconds) and left/right direction checks retained.
- Mount/Dismount markers are not synthesized into the movement history.

## Files changed
- Modified: `V2rayNG/app/src/main/java/com/v2ray/ang/ui/albion/AlbionMapProjection.kt`
- Modified: `V2rayNG/app/src/test/java/com/v2ray/ang/ui/albion/AlbionMapProjectionTest.kt`
- Added: `research/analysis/2026-11-07-movement-arrow-final-correction.md`
- Modified: `docs/ALBION_HANDOFF.md`

## Validation boundary
- Source/test changes are committed only after a fast-forward lease update is confirmed.
- Kotlin unit tests and CI have not yet been run for this change.
- No APK was built or installed.
- No Poco F5 visual acceptance test has been performed.
- The capture is graded FAIR; the four UI-marked direction segments are repeatable, but absolute north remains unverified.
