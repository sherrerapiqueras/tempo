# Proposal

## Why

Every core entity is identified only by a local autoincrement `Long`, which means nothing outside the device that assigned it. Two installs will both happily call different tasks `7`. Backup import (#283), tombstones (#281) and any later sync can't reliably tell "the same thing" from "a new thing" until rows carry an identity that is globally unique and stable. They also need to know when each row was created and last changed.

Tracked by [#30](https://github.com/sherrerapiqueras/tempo/issues/30) and part of the local sync foundations epic [#272](https://github.com/sherrerapiqueras/tempo/issues/272) (milestone 1.1). It goes first in that epic because every other issue there builds on it.

## What Changes

- **Stable identifier on every core row.** Categories, tasks, task sections, habits, habit chains, task attachments and saved focus sessions gain a `uid`: a lowercase UUID string that is unique across all devices. It's assigned when the row is created and never changes afterwards.
- **Merge metadata on the same rows:** `createdAt` and `updatedAt`, as UTC epoch milliseconds. `updatedAt` advances on every change automatically, without each write path having to remember to set it.
- **The default Inbox has a fixed, well-known `uid`**, so the Inboxes from two installs are recognised as the same category.
- **A database migration (v14 → v15)** assigns a `uid` to every existing row and backfills timestamps. No existing data changes and nothing the user can see changes.
- Domain models carry the metadata, so an edit or an Undo round-trips it instead of losing it.
- Local `Long` ids and all foreign keys stay as they are. They remain the local join keys.

**Non-goals:**
- tombstones and soft delete (#281)
- a mutation journal
- backup format v2 (#283)
- removing the Inbox `id = -1` sentinel (#326)
- making `completionHistory` mergeable (3.0)
- any network sync or server

No UI changes.

## Capabilities

### New Capabilities

- `entity-identity`: which persisted entities carry a stable global identifier and merge metadata, the identifier's format and immutability, how `createdAt` and `updatedAt` behave, and which tables are deliberately excluded.

### Modified Capabilities

- `default-category-persistence`: the seeded Inbox category also has a fixed, well-known identifier, shared by every install.

## Impact

- **Data:** the seven entities above gain `uid`, `createdAt` and `updatedAt`, with a unique index on `uid` and `updatedAt` triggers. `TempoDatabase` moves to version 15 with `MIGRATION_14_15`, and the triggers are created again for fresh installs. A new `app/schemas/15.json` is added.
- **Domain and mappers:** `uid`, `createdAt` and `updatedAt` are added to the matching domain models (unset by default) and to their `toEntity()` and `toDomain()` mappers. Copy-as-new paths go through a per-model `asNew()`. The database assigns the values.
- **Backup:** the v1 export and import are unchanged. Imported rows get fresh `uid`s. Carrying them in the file is #283.
- **Tests:** a migration test against populated v14 data, trigger tests, and mapper round-trip tests.
- **Docs:** `docs/implementation/MIGRATIONS.md`.
