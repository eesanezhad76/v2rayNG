# Albion Radar — Overlay Data Wiring Audit (2026-11-04)

## Runtime data paths

The saved capture catalog, live protocol observations, research crosswalk, external mob dictionary, imported historical bundles, and archived activity markers now feed the overlay through distinct, explicit paths.

- Saved spatial catalog: 83 resource nodes and 656 mob/entity pins from one capture-local coordinate frame.
- Archived activity markers: 56 manual activity records; 20 have a same-capture player-position checkpoint and are added to the Saved view as an opt-in Activity History layer. These are player positions at activity time, never target/node coordinates. The remaining 36 stay non-spatial records.
- External mobile dictionary: 5,479 TypeIDs from `Nouuu/Albion-Online-OpenRadar`, source commit `466c70b04e13ed0e8739ba23e6a950f7caf495e5`; server TypeID mapping uses the documented array-index offset +16.
- Research mobile crosswalk: all 64 observed TypeIDs are present in the external dictionary.
- Imported historical bundles: 4 bundles, 9,522 entity observations, 4,599 protocol events, 70 learned patterns, and 296 autonomous signatures. All 54 distinct historical mobile TypeIDs are present in the external dictionary.
- Historical resource families observed in the imported bundles (7, 14, 20, 27, 29) are covered by the runtime research index.

## Runtime integration

1. `AlbionOverlayService` loads the saved capture catalog, external TypeID dictionary, research index, and generated historical evidence.
2. Research metadata fills any missing observed TypeID entries without deleting the external dictionary's wider 5,479-entry catalog.
3. Live and saved entities receive TypeID names/categories/harvestability, research family evidence, and historical provenance before reaching the radar view.
4. `AlbionMiniRadarView` uses the research family mapping for resource filters. Provisional family mappings and unresolved resources remain grey; confirmed packet/user evidence retains its distinct status.
5. External dictionary harvestable mobs can be navigation targets when the research index has no explicit value. An explicit research `false` overrides external metadata (notably Mountain Rabbit TypeID 482).
6. Ore Elemental TypeIDs 571, 573, and 574 are classified before Troll labels, preventing a conflicting display/marker label from turning them into Trolls.
7. Activity history is a separate opt-in Saved-map filter and is labeled as player position, not a resource/mob target.

## Resource family policy

| Family | Research mapping | Overlay filter | Evidence |
|---:|---|---|---|
| 7 | Rock | Stone | Provisional |
| 14 | Fiber | Fiber | Provisional |
| 20 | Hide | Hide | Provisional |
| 27 | Ore | Ore | Provisional |
| 28 | Unknown | Unclassified Resources | Unresolved |
| 29 | Ore | Ore | Packet-confirmed living-resource family |

The resource classifier consumes the attached research type/status first. It no longer assumes every nearby numeric family ID is Ore.

## Coordinate safety

Only the saved capture-local catalog and same-capture activity player-position checkpoints are plotted in Saved mode. Historical entity observations and protocol events enrich live/saved entities by TypeID/family and provenance only. Cross-capture coordinates are not merged into the player-centered live radar because a stable zone identity and coordinate transform have not been verified.

## Validation boundary

Static source wiring and data crosswalks were audited. The workflow now regenerates/merges the runtime TypeID dictionary before asset validation and APK packaging. Android build, JVM test suite, and real-device overlay behavior remain unverified until a successful GitHub Actions run and device capture are available.
