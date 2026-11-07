# Temporary radar marks and touch-through controls — 2026-11-07

## Changes
- Added a MARK side control with a selection panel parallel to FILTER: resource/target categories, tier and enchantment subsets.
- Added 5, 10, 15 and 30 minute mark lifetimes (10 minutes default).
- MARK VISIBLE RESOURCES creates temporary in-memory location hints for useful, currently available resource observations in the confirmed active region. It does not trigger scanning, network calls, or persistence writes.
- Hints render as small amber triangles. When a typed/harvestable live node is observed within 2.5 world units, the triangle is hidden and the normal live node marker remains visible with its own enchantment color.
- Mark locations are scoped to the active region, expire using elapsed realtime, and are capped at 500 entries.
- Replaced the text drag label with a filled red circular drag handle.
- Restricted the radar overlay touchable region to the side controls and drag handle; transparent radar pixels pass touches to the game beneath.

## Files
- `V2rayNG/app/src/main/java/com/v2ray/ang/ui/albion/AlbionOverlayService.kt`
- `V2rayNG/app/src/main/java/com/v2ray/ang/ui/albion/AlbionMiniRadarView.kt`

## Verification notes
- This change is source-committed only. Android compilation, unit tests, APK signing/artifact generation, and on-device Android overlay behavior must be confirmed by CI/device validation.
- Android touch-region behavior is platform/OEM dependent; the app-level touchable region is configured to leave the circular radar canvas pass-through while retaining side-button interaction.
