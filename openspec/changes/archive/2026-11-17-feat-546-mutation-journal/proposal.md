# Proposal

## Why

Identity (#30) and tombstones (#281) say what a record is and that it was deleted. Nothing yet records which records changed since they were last sent anywhere. Without that, the first sync, or any sync after time offline, can only diff everything.

Tracked by [#546](https://github.com/sherrerapiqueras/tempo/issues/546), part of [#272](https://github.com/sherrerapiqueras/tempo/issues/272). This covers the epic's exit criterion "local mutations are journalled so they can be replayed against a server later".

## What Changes

- **A coalesced pending-changes journal** (`pending_changes`): one row per changed record, holding its `uid`, kind, latest operation (`upsert` or `delete`) and a sequence number. Its size is bounded by the number of records, not the number of edits.
- **Every insert, update and delete** of an identity-bearing record updates its row through triggers, from any code path, the same way the #30 and #281 triggers work.
- **Read in order and acknowledge.** Acknowledgement removes a change only if its sequence number hasn't moved since it was read, so an edit made during an upload stays pending.
- Database v16 → v17. Nothing reads the journal yet; the sync engine (#275) will.

**Non-goals:** any sync transport, deciding what may leave the device (`sync-boundary.md` governs that, and the engine filters at push time), and payload snapshots (the engine reads the live row, or the tombstone).

## Capabilities

### New Capabilities

- `mutation-journal`: what gets journalled, how changes coalesce, the order they're read in, and how acknowledgement works.

## Impact

- `PendingChangeEntity`, `PendingChangeDao`, `JournalSchema` (triggers and migration), and its registration in `EntityIdentitySchema`'s trigger lifecycle.
- `app/schemas/17.json`, plus updates to `docs/implementation/MIGRATIONS.md` and `docs/design/sync-boundary.md`.
