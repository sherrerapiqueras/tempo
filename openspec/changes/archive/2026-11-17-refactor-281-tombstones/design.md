# Design

## Context

- #30 gave seven tables a `uid`, `createdAt` and `updatedAt`, maintained by triggers in `EntityIdentitySchema`. Those triggers are dropped and recreated on every open.
- There are 17 delete paths across those tables: `@Delete`, `DELETE … WHERE id`, `IN (…)`, a recursive `task_tree` CTE, category-scoped deletes, `deleteAll…` for backup replace, and `SavedSessionDao.trimTo`.
- Undo works by reinserting snapshot rows with their original ids (`insertOrVerifyRestoredEntity`). Since #30 they keep their `uid` too.

## Goals / Non-Goals

**Goals:** no delete path can skip recording a tombstone, and no read path can show a deleted record.
**Non-Goals:** sync transport, conflict rules between a tombstone and a later edit (#31), and retention policy for shared lists.

## Decisions

### D1. Tombstones go in their own table, not in a `deletedAt` column
`tombstones(uid TEXT PRIMARY KEY, entityType TEXT NOT NULL, deletedAt INTEGER NOT NULL)`, indexed on `deletedAt`.

*Alternative:* a `deletedAt` column on each table, with every query filtering it. That means about 60 queries in 9 DAOs, Room `@Relation`s and `Flow` observers. One missed filter puts a deleted item back on screen, which is exactly the risk #281 names. Undo would also turn into an "un-delete" update everywhere instead of today's reinsert.

A sync engine needs `uid`, kind and time, not the deleted row's content. A separate table gives it exactly that, and live tables stay live.

### D2. SQLite triggers record and clear tombstones
For each identity table:
- **AFTER DELETE:** `INSERT OR REPLACE INTO tombstones (uid, entityType, deletedAt) VALUES (OLD.uid, '<table>', now)`. It's skipped when `OLD.uid = ''`, which can't happen after #30 but is guarded anyway.
- **AFTER INSERT** (`WHEN NEW.uid != ''`): `DELETE FROM tombstones WHERE uid = NEW.uid`. A restore under the original `uid` clears its tombstone. A new row gets its `uid` from the #30 insert trigger, and no tombstone can match it.

`entityType` is the table name. The `uid` alone is unique per table but not across tables in principle, so the primary key is `uid` with the type stored next to it. A clash between v4 and v7 random UUIDs across tables isn't a realistic case.

These triggers sit in `EntityIdentitySchema` next to the #30 ones. They share its drop-and-recreate-on-open lifecycle, so fresh installs, upgrades and debug resets all have them.

Cascades need nothing extra. App-level cascades delete each row through SQL, and SQLite fires row triggers for foreign-key cascade deletes too.

### D3. The purge window is 180 days, and it runs on open
An `onOpen` callback runs `DELETE FROM tombstones WHERE deletedAt < now - 180 days`. It's cheap, and it fits the existing orphan-attachment sweep.

**Why 180 days:** the window has to outlast the longest plausible gap between a member device's syncs. Otherwise that device pushes back a row the others deleted. Phones left unused for a season (travel, a spare device) are common, and half a year is an order of magnitude beyond normal use.

A tombstone costs about 60 bytes, so even heavy use stays in the low thousands of rows. The server side (#275) must treat a device that has been offline longer than the window as needing a full resync, not an incremental one. That rule is recorded in `sync-boundary.md`.

### D4. Retention clean-up records tombstones like any other delete
Completed-task retention deletes rows, so those deletes record tombstones too. Whether they should *propagate* to a shared list's members is a sharing decision. Recording is the safe default: a tombstone can be ignored later, but a missing one can't be recovered.

## Risks / Trade-offs

- **[Risk] A restore that mints a new `uid` leaves a stale tombstone for the old one.** → After #30, Undo restores the original `uid`. Backup "replace" imports under new `uid`s, so the old tombstones correctly record that the old rows were deleted.
- **[Risk] Triggers are invisible to Room.** → Instrumented tests cover recording, cascades, restore and purge on the configured database. The trigger-presence test is extended.
- **[Trade-off] The tombstone table grows with deletions.** → It's bounded by the 180-day purge.

## Migration Plan

`MIGRATION_15_16` creates `tombstones` and its index, then recreates all identity triggers through `EntityIdentitySchema.createTriggers`. There's no backfill: deletions from before this version left nothing to record. Rollback relies on the existing `database-schema-safety` behaviour.
