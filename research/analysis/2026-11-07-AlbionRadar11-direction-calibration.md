# AlbionRadar11 movement-direction calibration

Date: 2026-11-07  
Repository: `eesanezhad76/v2rayNG`  
Source branch/head audited: `albion-pattern-mining` / `461ec1472553458e2e4070d03fe0ece364575ccd`  
Calibration branch: `movement-direction-calibration-2026-11-07`  
Capture: `AlbionRadar11.zip::albion-1794058383031.pcap`  
Capture SHA-256: `c3d38d582146f5b5f9fcc08aa3c8d46aa7d756e597b76042b3c1522b4e76582e`  
Region for the manual direction trials: `4207`

## Source and method

The source timeline `research/analysis/2026-11-02-own-player-movement-timeline.csv` contains 3,962 rows for this capture. Each row is sourced from an outbound Photon Operation 22 (Move) request and contains timestamp, current world X/Y, heading, target X/Y and speed. The replay summary independently reports 3,962 `own_player_move` observations and 154 `player_mounted` observations.

The 25 manual checkpoints are preserved in `research/analysis/2026-11-02-overlay-manual-markers.csv`. Direction calculations use only the UP/DOWN start/end windows explicitly marked by the app, all in region 4207. For each window, X(t) and Y(t) were independently fit by ordinary least-squares linear regression; the resulting slope vector determines the path bearing. This uses the full timestamped trajectory rather than assuming the user's joystick/touch path is perfectly vertical or using only the first/last point. DOWN bearings were reversed by 180 degrees before averaging into the common UP axis.

The heading field was not used to set the calibration. It is retained as a separate diagnostic because its relationship to the fitted coordinate path is not a stable enough independent compass reference in these short UI-controlled trials.

## Direction windows

Bearings are mathematical world-coordinate angles measured counter-clockwise from +X. The deviation from world north is bearing minus 90 degrees for UP; DOWN is first reversed by 180 degrees.

| Trial | Manual interval (ms) | Samples | Fitted bearing | Median residual | P90 residual |
|---|---:|---:|---:|---:|---:|
| UP 1 | 1794059632143–1794059638117 | 50 | 139.072° | 1.595u | 3.633u |
| DOWN 1 | 1794059641055–1794059650323 | 79 | 320.011° | 3.260u | 7.246u |
| UP 2 | 1794059683400–1794059700434 | 82 | 139.954° | 5.046u | 15.090u |
| DOWN 2 | 1794059705538–1794059714305 | 66 | 320.238° | 2.393u | 5.325u |

UP-axis estimates after reversing DOWN:
- UP 1: 139.072°
- DOWN 1 reversed: 140.011°
- UP 2: 139.954°
- DOWN 2 reversed: 140.238°

Mean calibrated UP bearing: **139.819°**.  
Sample standard deviation across the four segment estimates: **0.513°**.  
Observed range: **1.166°**.  
Deviation from world north: **49.819°**.

The measured user-UP axis is therefore about 49.8 degrees to the left of world north in screen-space before calibration. This empirically accounts for the non-vertical UI path; it is not a guessed 45-degree correction.

## Mount / dismount markers and mounted observations

The app's manual marker windows were retained and checked separately:
- Mount: `radar_test_mount_start` 1794059660855; `radar_test_mount_end` 1794059664816.
- Dismount: `radar_test_dismount_start` 1794059669540; `radar_test_dismount_end` 1794059672903.

There are zero `own_player_move` coordinate samples inside either of those two marked intervals, so neither interval is misrepresented as a movement-direction trial. The capture contains 154 `player_mounted` observations overall. Four observations are near the manual mount/dismount markers:
- 1794059658875, id 90376 (before mount marker)
- 1794059662839, id 90376 (inside mount marker interval)
- 1794059669109, id 93378 (immediately before dismount marker interval)
- 1794059673069, id 90376 (immediately after dismount marker interval)

These IDs are retained as mounted-state observations only; they are not assumed to identify the local player. The replay reports zero inferred local-player entity IDs. Mount/dismount events are therefore not used as coordinate vectors or to bias the direction angle.

## Applied projection

`AlbionMapProjection.worldToScreen` now:
1. converts world offsets to the existing screen basis `(dx, -dy)`;
2. rotates that screen-space vector **clockwise by 49.819°**;
3. supplies the same result to the mini radar, full map, trails, resource/mob pins and movement arrow.

The same shared projection is used by the movement arrow, so the arrow and displayed world entities cannot silently use different rotations. The calibration is stored as a named constant and documented here. The tests now assert the calibrated axes and that the empirical UP vector maps to screen-up.

## Quality and limits

The capture is graded FAIR (80/100), with 12 malformed Photon envelopes, 5 malformed Protocol18 messages, 6 timestamp regressions, 55 duplicate payloads, 102 gaps over one second and a maximum gap of 12,049 ms. These quality limitations are why the calibration uses only four manually bounded windows and reports their repeatability and fit residuals rather than claiming device-perfect compass truth.

This is an offline evidence-based calibration. It does not claim a live Poco F5 visual acceptance test. Build/test status must be read from the CI run after the calibrated commit is advanced to `albion-pattern-mining`.
