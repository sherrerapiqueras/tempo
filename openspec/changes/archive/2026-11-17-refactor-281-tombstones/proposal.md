# Proposal

## Why

Tempo deletes rows outright. Once sync exists, a row that's gone looks the same as one this device never saw. The next pull would send it back, and deletions would quietly undo themselves. Deletions in a shared list must propagate, so this is a prerequisite for sharing.

Tracked by [#281](https://github.com/sherrerapiqueras/tempo/issues/281), part of [#272](https://github.com/sherrerapiqueras/tempo/issues/272). Builds on the stable identifiers from #30.

## What Changes

- **Every deletion of a record with a stable identity leaves a tombstone.** A tombstone is the record's `uid`, its kind and when it was deleted, kept in a new `tombstones` table. This covers categories, tasks, task sections, habits, habit chains, task attachments and saved focus sessions. It applies whichever code path deletes the record, including cascades and bulk deletes.
- **Live tables keep only live rows.** Deleting still removes the row, so no query anywhere can return a deleted record. That removes the main risk the issue names: a missed `deletedAt IS NULL` filter.
- **Undo clears the tombstone.** Restoring a deleted record under its original `uid` removes its tombstone, and the user sees exactly what they see today.
- **Purge policy:** tombstones older than **180 days** are removed when the database opens. The window is documented, with its reasoning.
- **Cascades:** each row a cascade removes (a category's tasks and sections, a task's subtree) gets its own tombstone.
- Database v15 → v16.

**Non-goals:**
- syncing tombstones, or deciding whether completed-task retention clean-up should propagate to a shared list (both belong to the sharing design)
- a mutation journal
- any change to the backup format (#283)

No UI changes.

## Capabilities

### New Capabilities

- `deletion-tombstones`: what a deletion records, when a tombstone is cleared, how cascades are recorded, and how long tombstones are kept.

### Modified Capabilities

None.

## Impact

- **Data:** a new `TombstoneEntity` and `TombstoneDao` (read and purge only), `MIGRATION_15_16`, delete and re-insert triggers alongside the #30 identity triggers, a purge-on-open callback, and `app/schemas/16.json`.
- **No DAO, repository or UI changes.** Every existing delete path is covered by the triggers.
- **Docs:** `docs/implementation/MIGRATIONS.md` and `docs/design/sync-boundary.md` (the purge window).
