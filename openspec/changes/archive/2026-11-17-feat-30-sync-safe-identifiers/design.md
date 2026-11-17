# Design

## Context

- `TempoDatabase` is at version 14. Every table except `daily_focus_activity` (keyed by date) and `habit_chain_members` (a composite key) uses `@PrimaryKey(autoGenerate = true) val id: Long`. Foreign keys reference those ids.
- The DAOs contain about 70 insert, update and delete sites across 9 files. Mappers rebuild entities from domain models, for example `TaskMapper` builds a `TaskEntity` from a `Task`. So any column the domain model doesn't carry is reset to its default on every `@Update`.
- Several places create a new record by copying an existing one with `copy(id = 0)`:
  - `SavedSessionDao.duplicate`
  - `TaskAttachmentRepositoryImpl` (reinsert and copy to another task)
  - `BackupRepositoryImpl` (category, section, habit, chain and session import)
  - the recurring-task next instance
- `MergePlanner` compares content with `contentKey()`, which is built from `copy(id = 0, …)`.
- `inboxCallback.onOpen` seeds the Inbox (`id = -1`) on every open. That covers fresh installs, migrations and debug destructive resets. A destructive reset does **not** call `onCreate`.
- Only `DailyFocusActivityDao` and `HabitChainMemberDao` use `OnConflictStrategy.REPLACE`, and neither table gets a `uid`.
- Kotlin is 2.4.21, so `kotlin.uuid.Uuid` is available in pure Kotlin and domain code can use it.

## Goals / Non-Goals

**Goals:**
- No write path, existing or future, can forget to advance `updatedAt`.
- Every copy-as-new site mints a fresh `uid`, and a missed one fails loudly in tests instead of corrupting data quietly.
- The migration is a single additive step that keeps existing data byte-for-byte apart from the new columns.

**Non-Goals:**
- Switching foreign keys or primary keys to UUIDs.
- Exposing `uid` in any UI.
- Changing the backup file (#283).

## Decisions

### D1. Add `uid` alongside the `Long` primary key instead of replacing it
Local `id` stays the primary key and the foreign-key target. `uid TEXT NOT NULL` gets a `UNIQUE` index on each covered table.

*Alternative:* make `uid` the primary key and rewrite every foreign key. That means rebuilding every table with foreign keys, plus every DAO query, the navigation arguments and the widgets that pass `Long` ids. That's a large risk for no gain, because ids never leave the device. A sync layer translates `uid` to and from `id` at its boundary.

### D2. The database assigns `uid`s, as random v4 UUIDs in SQL
An insert that arrives with `uid = ''` gets one from the insert trigger (D4). The same SQL expression backfills existing rows in the migration: `randomblob`, with the version nibble set to `4` and the variant bits set to `10xx`. The result is a lowercase canonical RFC 9562 UUID. Nothing relies on ordering.

*Considered and dropped:* minting UUIDv7 in Kotlin as a default parameter. A random default makes every `Task(...)` and `Habit(...)` non-deterministic. That breaks equality in hundreds of unit and ViewModel tests and `coVerify` matchers, and it scatters identity minting across creation paths. With one assigner, identity works like the autoincrement `id`. v7's index locality doesn't matter at Tempo's table sizes.

### D3. Domain models carry the metadata, with defaults that mean "not yet stored"
`uid = UNSET_UID` (`""`), `createdAt = UNSET_TIMESTAMP` (`0`) and `updatedAt = UNSET_TIMESTAMP` are added to `Category`, `Task`, `TaskSection`, `Habit`, `HabitChain`, `TaskAttachment` and `SavedSession`. Mappers copy them both ways.

The domain does need to carry them. Undo snapshots are domain objects, so without the metadata an Undo would restore a record under a *new* `uid`. The integration tests caught exactly that, and it breaks "identifiers never change". Because the defaults are constants, domain equality in tests stays deterministic.

Each model has `asNew()`, which resets `id`, `uid` and both timestamps. Every copy-as-new site goes through it:
- the next occurrence of a recurring task (`Task.asNextOccurrence`)
- subtask clones in the toggle and roll-over use cases
- attaching attachments to a task
- every backup import insert

Two places reset at the entity level instead: copying attachment rows to another task, and `SavedSessionDao.remember`, which matches the row by title rather than by identity.

### D4. SQLite triggers own the metadata
For each covered table there are four triggers:

- **Insert** (AFTER INSERT, `WHEN NEW.uid = '' OR NEW.createdAt = 0`): fills in a random `uid` and the current time, and sets `updatedAt` from `createdAt` unless it was supplied. Explicit values from a restore, import or sync are kept.
- **Update stamp** (AFTER UPDATE, `WHEN OLD.uid != '' AND (NEW.updatedAt IS OLD.updatedAt OR NEW.updatedAt = 0)`): sets `updatedAt = MAX(now, OLD.updatedAt + 1)`. A write that supplies its own `updatedAt` is left alone.
- **Protect** (AFTER UPDATE): a write that sends `uid` or `createdAt` unset (`''`/`0`) gets the stored values back. That makes code paths that don't carry the metadata harmless.
- **Immutable** (BEFORE UPDATE, `RAISE(ABORT)`): changing a stored `uid` or `createdAt` to a *different* value is a bug, so it fails loudly.

"Now" is `CAST(ROUND((julianday('now') - 2440587.5) * 86400000) AS INTEGER)`. `unixepoch('subsec')` would read better, but it needs SQLite 3.42. Test helpers and unencrypted paths can run on the framework SQLite, which is 3.9 on API 24.

Android's SQLite runs with **recursive triggers on**, which the first test run showed. So every trigger's own UPDATE must fail its WHEN clause:
- the stamp is strictly greater than the old value, even within one millisecond
- the fill-in and the restore are excluded by `OLD.uid != ''` and `OLD.createdAt != 0`

The triggers are dropped and recreated in `MIGRATION_14_15` and in an `onOpen` callback (`TriggersCallback`, registered in `configure` before the Inbox seed). This covers fresh installs, upgrades and debug destructive resets, which skip `onCreate`, and a changed trigger body always replaces the old one. Room's schema JSON doesn't record triggers, so tests assert they exist and behave correctly. Test databases built without `configure` add the callback.

*Alternatives:*
- Stamp in each repository. That's about 70 sites, and a future one will forget.
- A Room `@TypeConverter` or `@Query` wrapper. Neither covers `@Update` with custom `@Query UPDATE` statements.

The trigger-based clock means `updatedAt` reflects device wall-clock time. That's acceptable for last-writer-wins at this stage. #31 can add a logical counter if needed. Any write to a row counts as a change, including a no-op `@Update`.

### D5. Fixed `uid` for the default Inbox
The well-known value is `00000000-0000-4000-8000-000000000001`, a v4-shaped constant, defined once next to `DEFAULT_INBOX_CATEGORY_ENTITY`.

The migration sets it on the seeded Inbox row (`id = -1`), not on "whichever row has `isDefault = 1`". The default can be reassigned (`setDefaultCategory`), and a `uid` can't move with it. `inboxCallback`'s `INSERT OR IGNORE` includes it. The unique index guarantees it's held by at most one row.

When #326 removes the `-1` sentinel, this `uid` becomes the stable handle on the Inbox.

### D6. `MergePlanner` content keys ignore metadata
`contentKey()` starts from `asNew()`, so it neutralises `uid`, `createdAt` and `updatedAt` as well as `id`. That way v1 import's duplicate detection behaves exactly as it does today. Matching by identity is #283.

### D7. Backfill `createdAt` from existing dates
- `habits.createdDate` and `habit_chains.createdDate` are local `LocalDateTime` text. The migration converts them using the device's current UTC offset, through a Kotlin-side read and update in the migration, because SQLite has no time-zone data. This is approximate across DST changes, which is acceptable for historical rows.
- `focus_sessions` has no creation date, so it uses `lastUsedAt` when it's nonzero.
- Everything else uses the migration time.
- `updatedAt` is set equal to `createdAt`.

## Risks / Trade-offs

- **A missed copy-as-new site reuses a `uid`.** → The `UNIQUE` index makes the insert throw, so it fails loudly instead of corrupting data. Every site found by grepping `id = 0` goes through `asNew()`.
- **A mapper drops the metadata.** → The protect trigger restores `uid` and `createdAt`, and the stamp still advances `updatedAt`. Mapper round-trip unit tests cover all seven models.
- **Triggers are invisible to Room.** → Instrumented tests check `sqlite_master` after a fresh open and after migrating from 1 and from 14, and exercise every trigger path.
- **Clock skew across devices affects later LWW merges.** → This is accepted for now and recorded for #31. It has no effect in 1.1, since nothing merges by timestamp yet.
- **Data transactions vs. scheduler side effects:** the migration and triggers live entirely within SQLite transactions. No reminder or AlarmManager work is touched, and the recurrence and rollover logic doesn't change apart from the next instance going through `asNew()`, so the insert trigger gives it a fresh `uid` within the same statement.

## Migration Plan

1. `MIGRATION_14_15`, run in a single transaction:
   - `ALTER TABLE … ADD COLUMN uid TEXT NOT NULL DEFAULT ''`, `createdAt INTEGER NOT NULL DEFAULT 0` and `updatedAt INTEGER NOT NULL DEFAULT 0` on the seven tables
   - backfill `uid` (D2 and D5) and timestamps (D7)
   - create the unique indexes, then the triggers
2. Regenerate `app/schemas/15.json`. Room's expected schema must match, so the column defaults are declared on the entities with `@ColumnInfo(defaultValue = …)`.
3. **Rollback:** `database-schema-safety` already fails safely if an older build opens a v15 database in release. No down-migration is provided, which is consistent with the existing policy.
