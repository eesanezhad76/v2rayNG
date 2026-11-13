# AlbionRadar — TH.GL full-zone map bridge (2026-11-13)

## Purpose

Provide a full-zone static reference map without increasing the live radar's data load or attempting to force the game server to transmit the entire world map.

## How it works

The existing production pipeline already decodes Operation 2 (Join) response parameter 8 as the confirmed region/cluster ID:

`Join response p8 → AlbionEntityEventMapper → AlbionLiveKnowledgeStore.activeRegionId → CoreServiceManager.getAlbionCurrentRegionId()`

The existing `albion-region-name-index.json` asset maps 542 region IDs to names. The live coordinate map now uses that ID/name and offers **OPEN FULL ZONE MAP • TH.GL**. When tapped, Android opens:

`https://albiononline.th.gl/maps/<encoded-zone-name>`

in the external browser.

This avoids adding a WebView, local 353,074-marker database, map renderer, new parser, background network requests, or new decoder. The radar remains responsible for live, locally observed entities; TH.GL supplies the separate full-zone static reference layer.

## Current verified zone names

| Region ID | Name |
|---:|---|
| 4000 | Fort Sterling |
| 4001 | Bank of Fort Sterling |
| 4002 | Fort Sterling Market |
| 4206 | Tharcal Fissure |
| 4207 | Pen Gent |
| 4209 | Cairn Darg |
| 4213 | Cairn Camain |
| 4217 | Pen Fenair |
| 4219 | Caulder Fissure |

## External map source

TH.GL lists 1,059 interactive maps and 353,074 locations. Its developer documentation offers free map embeds without an API key and requires a visible TH.GL link beneath an embedded map. This implementation opens the normal map page externally instead of embedding it, keeping map rendering and marker data outside the radar process.

Six currently relevant map pages:
- [Tharcal Fissure](https://albiononline.th.gl/maps/Tharcal%20Fissure)
- [Pen Gent](https://albiononline.th.gl/maps/Pen%20Gent)
- [Cairn Darg](https://albiononline.th.gl/maps/Cairn%20Darg)
- [Cairn Camain](https://albiononline.th.gl/maps/Cairn%20Camain)
- [Pen Fenair](https://albiononline.th.gl/maps/Pen%20Fenair)
- [Caulder Fissure](https://albiononline.th.gl/maps/Caulder%20Fissure)

These are static/community map references, not current server state. They do not guarantee that a node is currently spawned, enchanted, full, or undepleted. Their coordinates have not been merged into the radar because the external map coordinate transform has not been independently validated against the game's local coordinates.

## Existing data analysis

The expanded preserved dataset contains 725 harvestable observations:
- 610 direct region assignments.
- 9 additional high-confidence pre-Join candidates (5 for region 4213 and 4 for 4219), kept separate from direct assignments.
- 106 observations remain unassigned and are not guessed.

The 9 `type_number=20` observations are all Mobile TypeID 482 (Mountain Rabbit), not harvestable Hide nodes. Two TypeNumber-28 observations and one TypeNumber-32 observation remain unresolved.

Full analysis, checksums and evidence breakdown:
[AlbionRadar-External-Research analysis](https://github.com/eesanezhad76/AlbionRadar-External-Research/blob/external-data-quarantine-2026-11-06/analysis/2026-11-13-full-zone-map-source-and-observation-coverage.md)

## Change log

- Edited only `V2rayNG/app/src/main/java/com/v2ray/ang/ui/albion/AlbionWorldMapActivity.kt`.
- Added cached region-name lookup from the existing asset.
- Added the current zone ID/name to the live map status.
- Added one external-browser launcher for the active zone's TH.GL map.
- No capture, decoder, packet handling, resource classification, map projection, heading calibration, overlay drawing, or #147 movement transform was changed.
- No new assets or dependencies were added.
- No build, automated test, or Poco F5 device validation was run for this commit.
