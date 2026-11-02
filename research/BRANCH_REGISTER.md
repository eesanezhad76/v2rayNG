# Branch and pull-request register

Repository: `eesanezhad76/v2rayNG`

## Operational rule
Only `albion-pattern-mining` is the working branch for Albion Radar research, capture ingestion and current app development. Upload capture ZIPs to `research/inbox/` on this branch.

Do not delete, force-push, retarget or merge branches without explicit instruction. Always refresh branch SHAs before writing.

## Branch snapshot (2026-11-02)
| Branch | SHA at snapshot | Role |
|---|---|---|
| albion-pattern-mining | 6a74db31af845a31a458ea9091734010bfd22c2e | Active work branch |
| albion-reliability-ux | dda77733e893e2fb87865d8300dfab09e5295a1b | PR #3 target; protected from edits |
| albion-decoder-lab | 77a8c3e2c58761d6bd2d40aa8eebef0cb32068a5 | Historical decoder work |
| albion-decoder-lab-failed-merge-backup | 0a21332de0bfc2f70c4982eb4af7a35d051b5d8e | Backup |
| albion-decoder-radar | ac7e90cd29a874ccd367cf7ea126372f8920a96a | Historical |
| albion-decoder-radar-ci | 023705a103ccff3ccb6b59d5bd71a5bfc882252c | Historical / PR #2 head |
| albion-decoder-radar-rebuild | 77a8c3e2c58761d6bd2d40aa8eebef0cb32068a5 | Historical rebuild |
| albion-integrity-autosave | 39a125b9de84a9101e610f33deb663095e8c10c7 | Historical |
| albion-radar | 2a34a8473643cb0084057ac535a2cf8bdbdfb391 | Historical |
| albion-v2rayng-2.2.6-port | d41804beecec085eb5f774009ac47935cfa03b24 | Historical port |
| backup/albion-capture-e3f95d9 | e3f95d9e03b08a9e31a4d0467a6d8cc224773831 | Backup |
| backup/albion-decoder-lab-before-2.2.6 | f31ba8d3318d929eb1b22e0e94297112d08bf193 | Backup |
| backup/albion-radar-2026-10-31 | e3f95d9e03b08a9e31a4d0467a6d8cc224773831 | Backup |
| master | aa4b05503a504a3f2e43818e6ee8ebf1caaa3df0 | Default/historical baseline |

SHAs above are historical snapshots. Query GitHub for current SHAs before making changes.

## Pull requests at snapshot
- PR #3: open, draft; `albion-pattern-mining` → `albion-reliability-ux`. Do not modify.
- PR #2: open, non-draft; `albion-decoder-radar-ci` → `albion-decoder-lab`. Do not merge or retarget without approval.
- PR #1: closed; `albion-decoder-radar` → `albion-decoder-lab`.

Update this register when branch/PR state changes.
