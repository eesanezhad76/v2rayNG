# AlbionRadar11 movement-direction calibration

Date: 2026-11-07  
Repository: `eesanezhad76/v2rayNG`  
Branch at analysis: `albion-pattern-mining`  
Source commit: `461ec1472553458e2e4070d03fe0ece364575ccd`

## Source integrity and scope

- Capture: `research/inbox/AlbionRadar11.zip`
- ZIP SHA-256: `59319bac63b6703f24b3bf621ae5dd5da78282d162ff2b737db9842c3252a164`
- PCAP: `albion-1794058383031.pcap`
- PCAP SHA-256: `c3d38d582146f5b5f9fcc08aa3c8d46aa7d756e597b76042b3c1522b4e76582e`
- PCAP report: `research/analysis/AlbionRadar11--albion-1794058383031-13-pcap-report.md`
- PCAP report blob SHA: `d0ecb584024bcad0dfecad8ecc287979902aebb2`
- Manual-marker source: `research/analysis/2026-11-02-overlay-manual-markers.csv`
- Manual-marker blob SHA: `f72e26724a0f39215d38af9bf488045a80b59e76`

The report contains 3,962 decoded local-client Move(22) position samples and 154 `player_mounted` observations. It also contains 25 unique manual app markers. Analysis below uses only the four explicitly marked UP/DOWN intervals from this same PCAP and does not join other captures.

## UP/DOWN interval measurements

Positions are decoded from outbound operation 22 parameter 1. Direction is computed from the first-to-last position delta in each marked interval; the heading field is not used as the movement vector.

| Segment | Marker interval (ms) | Move samples | Net dx | Net dy | Net angle |
|---|---:|---:|---:|---:|---:|
| UP 1 | 1794059632143–1794059638117 | 50 | -42.009 | +36.280 | 139.185° |
| DOWN 1 | 1794059641055–1794059650323 | 79 | +63.178 | -53.591 | 319.693° |
| UP 2 | 1794059683400–1794059700434 | 82 | -47.995 | +40.209 | 140.045° |
| DOWN 2 | 1794059705538–1794059714305 | 66 | +60.345 | -50.117 | 320.290° |

For the combined axis estimate, the two UP angles and the two DOWN angles rotated by 180° were circular-averaged. Result:

- Measured UI-UP world-coordinate axis: **139.803°** from +X.
- Opposite-axis mismatch between UP and DOWN: **0.377°** from exact 180°.
- UP run-to-run spread: **0.860°**; DOWN run-to-run spread: **0.597°**.
- Relative to the current +Y-up world basis, the measured axis is **49.803°** away from +Y.
- The earlier 45° assumption differs from the measured axis correction by **4.803°**.

The current `AlbionMapProjection.worldToScreen(dx, dy) = (dx, -dy)` maps this measured UP vector to an up-left screen vector. If the marked UP input is treated as the intended screen-vertical reference, the candidate screen-space correction is **+49.8° in Android canvas coordinates** (positive canvas rotation is visually clockwise). This is a measured UI-input-axis calibration, not proof of the game's absolute cardinal north: a systematic thumb/joystick bias cannot be separated from the map transform using PCAP coordinates alone. Do not label this as an absolute north calibration without an independent screen/cardinal reference.

## Heading field check

Across the marked intervals, the median Move(22) heading is roughly 169–176° away from the coordinate-derived path direction. Therefore, the heading field must not replace the coordinate-history delta as the radar movement arrow direction for this capture. Continue deriving the arrow from sequential player coordinates.

## Mount / Dismount markers retained

All four app markers are present in the capture and in the manual-marker dataset:

| Event | Timestamp (ms) |
|---|---:|
| `radar_test_mount_start` | 1794059660855 |
| `radar_test_mount_end` | 1794059664816 |
| `radar_test_dismount_start` | 1794059669540 |
| `radar_test_dismount_end` | 1794059672903 |

There are no Move(22) position samples inside either Mount or Dismount marker interval. The nearest surrounding Move(22) samples are at 1794059650053 before the Mount test and 1794059677334 after the Dismount test. These markers are preserved as separate time anchors; they are not treated as movement vectors and are not discarded. The 154 `player_mounted` observations remain a separate evidence stream and are not conflated with the four manual test markers.

## Capture quality and limitations

Replay quality is **FAIR**: 22,894 valid Photon packets, 12 malformed Photon packets, 5 malformed Protocol18 messages, 6 timestamp regressions, 102 gaps over one second, and a maximum gap of 12,049 ms. The marked movement intervals contain enough Move(22) samples to estimate their net directions, but the second UP interval has local direction outliers; its first-to-last displacement agrees with the first UP run and is used only as a whole-interval axis estimate.

No app source or runtime transform was changed in this analysis commit. The report records the measured candidate correction and its systematic-bias limitation; a production rotation should be applied only after deciding that the user's marked UP input is an acceptable screen-vertical reference or obtaining an independent visual/cardinal reference.


## Regression-based refinement and UI-input error

The first-to-last displacement table above is retained for traceability. A second calculation fits X(t) and Y(t) independently across all samples in each marker interval, which reduces sensitivity to the user's non-vertical and locally irregular touch/joystick path.

| Segment | Samples | OLS bearing | Median perpendicular residual | P90 perpendicular residual |
|---|---:|---:|---:|---:|
| UP 1 | 50 | 139.072° | 1.595u | 3.633u |
| DOWN 1 | 79 | 320.011° | 3.260u | 7.246u |
| UP 2 | 82 | 139.954° | 5.046u | 15.090u |
| DOWN 2 | 66 | 320.238° | 2.393u | 5.325u |

After reversing DOWN bearings by 180°, the four fitted UP-axis bearings have a mean of **139.819°** and sample standard deviation **0.513°** (range 1.166°). The resulting candidate clockwise screen correction is **49.819°**, about 4.819° away from the earlier 45° assumption. This is the measured UI-input axis estimate; the 0.513° spread describes repeatability between these four windows, not systematic thumb/joystick bias.

The UP 2 window has a larger P90 path residual (15.090 game units) than the other windows, but its fitted bearing remains within 0.9° of UP 1. It is included with that lower straightness noted rather than discarded.

### Mounted-state observations around the app markers

The PCAP report also records these `player_mounted` observations near the manual test markers:
- 1794059658875, runtime id 90376 (before the Mount marker)
- 1794059662839, runtime id 90376 (inside the Mount marker interval)
- 1794059669109, runtime id 93378 (immediately before the Dismount marker interval)
- 1794059673069, runtime id 90376 (immediately after the Dismount marker interval)

These are preserved as separate mounted-state observations. The replay has no inferred local-player entity ID, so the runtime IDs are not asserted to be the local player. There are no Move(22) coordinate samples inside either Mount or Dismount marker interval.

### Interpretation boundary

The projection change deliberately aligns the user's **marked UI-UP input axis** with screen-up. It does not prove that this axis is true game-world north: a systematic bias in the manual joystick/touch input cannot be distinguished from a map-orientation offset using these PCAP coordinates alone. An independent visual/cardinal reference would be required to claim absolute north.
