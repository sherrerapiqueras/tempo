# Design

## Context

See `proposal.md` for the problem. Constraints that shape the fix, all read from Room 2.8.5's `RoomOpenHelper` and the existing code:

- On a schema mismatch with no migration path, Room runs `dropAllTables`, then every callback's `onDestructiveMigration`, then `createAllTables`. `onCreate` is **not** called. `onOpen` is called afterwards, always, for every way a database can come up (create, migrate, destructive reset, plain open).
- `TempoDatabase` already uses an `onOpen` callback for another invariant (`OrphanedAttachmentRowsCallback`), so a write there is an established pattern.
- The Inbox is id `-1`. `TaskEntity.categoryId` defaults to it, and `BackupRepositoryImpl.ensureDefaultCategory` already states the invariant "the Inbox row always exists, and some category is the default" for imports.
- A non-default Inbox can be deleted from the UI (the delete use case only refuses the default category and the last category). So "the Inbox exists" is not an invariant that the rest of the app keeps; "a default category exists" is.
- `core/data/local/*` is excluded from Kover, so the database code is covered by instrumented tests, not by the 80/70 unit gate. The ViewModel code is not excluded.

## Goals / Non-Goals

**Goals:**
- A database that has been opened by this build always has a default category.
- A schema mismatch cannot silently delete data in a release build.
- The same builder configuration is used by the app and by the tests, so the tests exercise what ships.

**Non-Goals:**
- Reconstructing user data after a reset. A reset database is empty; only the default category is restored.
- Handling a failed open in the UI.

## Decisions

### 1. Seed in `onOpen`, with one conditional `INSERT OR IGNORE`

`onOpen` runs one statement:

```sql
INSERT OR IGNORE INTO categories (id, name, icon, isDefault, sortOrder)
SELECT -1, ?, 'inbox', 1, -1
WHERE NOT EXISTS (SELECT 1 FROM categories WHERE isDefault = 1)
```

- **Idempotent**: when a default category exists, the `SELECT` yields no row and nothing is written. When an Inbox row exists but is not the default and no other default exists, `OR IGNORE` keeps the existing row untouched. Either way an existing row is never rewritten, which keeps the name user-owned (`default-category-persistence`).
- **Why the `WHERE NOT EXISTS`** rather than a plain `INSERT OR IGNORE` on id -1: a user who made "Work" the default and then deleted the Inbox would otherwise get the Inbox back on every launch, as a second default. Guarding on "no default exists" restores the thing the app depends on (a default category) and leaves a deliberate deletion alone.
- The localized name is read from `category_inbox` at the time of the insert; it is only used when a row is actually created.
- **`onCreate` keeps only the `DbKdfIterMarker.write(...)` call.** `onOpen` follows `onCreate` in the same open, so the fresh-install seed still happens, through the same path as every other case. One code path, not two.

Alternatives considered:
- *Seed in `onDestructiveMigration`* (the original idea): impossible, the table is dropped and not yet recreated when it runs. A write there fails. The callback is still overridden, but only to log.
- *Seed in the repository on first read (`getAllCategories`)*: runs on every query path, races the first emission, and leaves the data layer holding a database that is invalid until someone reads it.
- *Plain `INSERT OR IGNORE` on id -1*: see above.

### 2. Schema mismatch policy per build type

| Situation | Release | Debug |
|---|---|---|
| Database newer than the build (downgrade) | Room throws, file untouched | Reset: drop all tables, recreate, warn in the log |
| Older database, no migration path | Room throws, file untouched | Room throws, file untouched |

Implemented with `fallbackToDestructiveMigrationOnDowngrade(dropAllTables = true)` applied only when `BuildConfig.DEBUG`, and no `fallbackToDestructiveMigration(...)` at all.

- `dropAllTables = true` (Room's recommended value) so a reset really is one: with `false`, tables that only the newer schema has (like `focus_sessions` after a v14 → v13 switch) survive with stale rows and come back when the schema is upgraded again.
- Release never wipes. A silent wipe is irreversible; a failed open is loud and recoverable (install the matching version, the file is intact). Play Store never downgrades, so only sideloading or a missing migration can trigger it.
- Debug keeps the downgrade reset because switching between branches with different schema versions on one device is exactly how this bug appeared, and test data is disposable. It does not keep the missing-migration reset: that is a developer mistake that should crash in development, not be hidden by a wipe.

Alternatives considered:
- *Keep the wipe in all builds, only add the re-seed*: leaves silent data loss in release.
- *Fail loudly in debug too*: forces `pm clear` on every branch switch, which wipes the data anyway, just manually.
- *Opt-in switch (build config flag / setting)*: more surface for the same outcome. `BuildConfig.DEBUG` is already the project's distinction.

### 3. One shared builder configuration

Migrations, both callbacks and the fallback policy move into one `internal` function on `TempoDatabase`'s companion that takes the build-type decision as a parameter (`allowDestructiveDowngrade`). `getDatabase` passes `BuildConfig.DEBUG`; instrumented tests pass both values and can add their own open helper. Otherwise tests would rebuild the configuration by hand and drift from it.

### 4. The `0L` category

`0L` is not a valid persisted category id (Room autogenerates from 1, the Inbox is -1), so it becomes a named constant, `NO_CATEGORY_ID`, instead of a literal that looks like data.

- `loadData` no longer substitutes it for "no category": with an empty list it keeps the current selection and does not persist anything.
- `TaskBottomSheetContent` uses the constant for its fallback (the editor can still render while categories load, and re-keys when they arrive).
- The ViewModel is the choke point and guards twice: opening the new-task editor when categories have loaded and are empty (snackbar, no sheet), and `createOrUpdateTask` rejecting `NO_CATEGORY_ID` before it validates or closes the editor (snackbar, the editor and the user's input stay).
- The "loaded" condition matters: before the first emission `categories` is also empty, and the editor already handles that by re-keying on the default category.

## Risks / Trade-offs

- [A release build with a forgotten migration now crashes at startup instead of wiping data] → Intended; the existing `MigrationTest` is the safety net, and a crash with intact data is fixable by a hotfix, a wipe is not.
- [A sideloaded downgrade on a release build crashes] → Intended and documented; reinstall the newer build.
- [`onOpen` runs one extra statement on every launch] → It is a primary-key/`EXISTS` lookup on a table of a handful of rows; `OrphanedAttachmentRowsCallback` already does comparable work there.
- [Seeding a database whose categories were never lost does nothing, so a tampered file with no default but other categories gets an Inbox added as default] → Acceptable: the app requires a default category, and the existing code already treats this as a repair (`BackupRepositoryImpl.ensureDefaultCategory`).
- [Existing orphan tasks with `categoryId = 0` from earlier occurrences stay invisible] → Out of scope; they cannot be reached today either.

## Migration Plan

No schema change and no data migration. Ship as a normal release. Rollback is reverting the commit.
