# Room Migration Strategy

> **Status:** Active · **Last reviewed:** 2026-06-17 · **Database version:** 8

## Current State

| Version | Migration | Description |
|:--------|:----------|:------------|
| 1 | — | Initial schema (5 tables: tasks, categories, habits, habit_chains, habit_chain_members) |
| 2 | `MIGRATION_1_2` | Adds `completedAt TEXT` column to `tasks` table |
| 3 | `MIGRATION_2_3` | Adds `color TEXT`, `icon TEXT`, and `isDefault INTEGER NOT NULL DEFAULT 0` columns to `categories` |
| 4 | `MIGRATION_3_4` | Seeds the default **Inbox** category (`id = -1`) via `INSERT OR IGNORE` |
| 5 | `MIGRATION_4_5` | Backfills `icon = 'inbox'` for the Inbox category when it is null |
| 6 | `MIGRATION_5_6` | Adds task recurrence columns: `periodicityInterval INTEGER NOT NULL DEFAULT 1`, `repeatDays TEXT`, `monthDayOption TEXT` |
| 7 | `MIGRATION_6_7` | Replaces the unused `isInverted` column with `habitType TEXT` via table rebuild (`isInverted = 1` → `QUIT`, else `BUILD`); prunes quit habits from chains and removes emptied chains |
| 8 | `MIGRATION_7_8` | Adds `nextInstanceId INTEGER` to `tasks` to support periodic-completion rollback |
| 9 | `MIGRATION_8_9` | Adds indices on `tasks.categoryId` and `tasks.parentTaskId` |
| 10 | `MIGRATION_9_10` | Adds the `daily_focus_activity` table (per-day Focus statistics) |
| 11 | `MIGRATION_10_11` | Adds the `task_attachments` table and its `taskId` / `storedName` indices |
| 12 | `MIGRATION_11_12` | Adds the `task_sections` table and `categories.sectionsEnabled`, and `tasks.sectionId` (#61) |
| 13 | `MIGRATION_12_13` | Adds `periodicityEndDate TEXT` to `tasks` (#509) |
| 14 | `MIGRATION_13_14` | Adds the `focus_sessions` table for saved focus sessions (#517) |
| 15 | `MIGRATION_14_15` | Stable record identity (#30): `uid`, `createdAt` and `updatedAt` on seven tables, unique `uid` indexes, backfill, and identity triggers (see [Identity triggers](#identity-triggers)) |
| 16 | `MIGRATION_15_16` | Adds the `tombstones` table (#281): every deletion of an identity row records its `uid`, table and time |

### Identity triggers

Since version 15, `categories`, `tasks`, `task_sections`, `habits`, `habit_chains`, `task_attachments` and `focus_sessions` each carry `uid`, `createdAt` and `updatedAt`. SQLite triggers maintain them, so no DAO has to:

- **Insert:** an empty `uid` gets a random UUID, and `createdAt = 0` gets the current time. Explicit values are kept.
- **Update:** `updatedAt` advances (strictly, even within one millisecond) unless the write supplies its own.
- **Protect:** a write that sends `uid` / `createdAt` unset gets the stored values back.
- **Immutable:** a write that changes them to a different value aborts.

Room's schema JSON does not record triggers. `EntityIdentitySchema` drops and recreates them on every open (`TriggersCallback`, registered in `TempoDatabase.configure`), so fresh installs, upgrades and debug destructive resets all run this build's triggers. A test database built without `configure` must add `EntityIdentitySchema.TriggersCallback`, or rows inserted with an empty `uid` collide on its unique index.

Code that saves a copy of a stored record as a new row must call the domain model's `asNew()`.

Since version 16, two more triggers per table maintain **tombstones** (#281). An `AFTER DELETE` trigger records `(uid, entityType, deletedAt)` in `tombstones` for every delete, from any code path or cascade. An `AFTER INSERT` trigger removes the tombstone when a row comes back under the same `uid` (Undo). Live tables keep only live rows, so no query filters on deletion. `TombstonePurgeCallback` drops tombstones older than 180 days on every open.

Migrations never create triggers. A migration chain passes through intermediate schemas that this build's triggers don't fit, so only the open callback creates them, and Room runs it right after the last migration.

### Schema Exports

Exported schemas live in `app/schemas/` and are **checked into source control**. CI verifies that committed schemas match the compiler output — schema drift will fail the build.

### Legacy Cleanup

A legacy schema directory from a previous package name (`com.mandredotdev.tempo`) was removed. Only the current package (`com.mandrecode.tempo`) schemas are maintained.

---

## Beta Migration Policy

During the Beta phase, Tempo preserves **all upgrade paths from version 1 onward**. This means:

- Every schema version has a corresponding exported JSON file.
- Every version gap has an explicit `Migration` object registered in `TempoDatabase`.
- Migration tests validate each step and the full path from v1 → latest.
- Destructive fallback is **disabled** (`fallbackToDestructiveMigration(false)`).

### Rationale

Beta users have real data. A destructive migration would silently erase it. Even during Beta, we treat user data with production-level care.

---

## Guidelines for Future Schema Changes

### 1. Write the migration

Add a `Migration` object in `TempoDatabase.companion`:

```kotlin
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // SQL statements here
    }
}
```

Register it in the builder: `.addMigrations(MIGRATION_1_2, MIGRATION_2_3)`

### 2. Bump the version

Update `@Database(version = N)` in `TempoDatabase.kt`.

### 3. Export and commit the schema

Build the project so KSP generates `app/schemas/.../N.json`, then commit it. CI will fail if you forget.

### 4. Add a migration test

In `MigrationTest.kt`, add a test that:
- Creates a DB at version N-1 with representative data.
- Runs the migration.
- Asserts the data survived and the new schema is correct.

Update the full-path test to chain all migrations.

### 5. Update this document

Add the new version to the table at the top.

---

## Post-GA Considerations

After General Availability, if migration debt becomes excessive, the team may choose to:

- **Squash migrations** into a single baseline (requires a fresh-install path for new users and a legacy path for upgraders).
- **Reset migrations** by bumping to a new major version with `fallbackToDestructiveMigrationFrom()` for very old versions, with clear user communication.

Any such decision must be documented here before implementation.
