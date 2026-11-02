# Albion research database

This directory is the durable, versioned knowledge base. Raw captures belong in `research/inbox/`; derived normalized datasets belong here. Never treat an unverified decode as a build constant.

Current dataset: [nodes.json.gz](nodes-2026-11-02.json.gz) and [nodes.csv](nodes-2026-11-02.csv), extracted from `Albionsskkakaks111.zip`.

Identity rules:
- `entity_id`: runtime identity of one world node.
- `type_number`: Event 40 resource-family number.
- `mobile_type_id`: separate game mobile code; null when not established.
- Event 46 `amount_or_state` is preserved raw. Do not assume its semantics are identical for every family.

Only promote a mapping into app code after independent captures and tests confirm it.
