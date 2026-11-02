# Decision 001 — own-player position from Photon operation 22

Status: APPROVED for local coordinate rendering  
Date: 2026-11-03

Evidence: research/analysis/player-position-evidence.md and its JSON record.

The outbound operation-22 request parameter key 1 is accepted as the local client's current [x, y] position because two independent captures contain hundreds of operation-22 requests with coherent changing coordinates. Keys 2, 3 and 4 are retained as heading, target pair and speed respectively.

Constraints:
- This does not identify a zone or global coordinate transform.
- The app sentinel entity ID -1 is not a server-side entity ID.
- Event 3 payload decoding remains unapproved as a player-position source.
- Map rendering must stay in the current observed coordinate space and must not fabricate terrain or cross-zone continuity.

Regression coverage: AlbionEntityEventMapperTest.mapsOwnPlayerPositionFromOutboundMoveOperation22 and AlbionLiveKnowledgeStoreTest.retainsBoundedOwnPlayerMovementTrailForRadarRendering.
