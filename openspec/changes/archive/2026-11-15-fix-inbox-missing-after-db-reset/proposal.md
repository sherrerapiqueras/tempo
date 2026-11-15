# Proposal

## Why

On an emulator that ran a newer-schema build and then an older-schema one (`main` at schema 14, then a branch at 13), the Tasks screen showed no categories at all (only the "+" chip) and the task editor's category row was empty. The default Inbox row was gone from the `categories` table and nothing ever put it back.

Two things combine:

- Inbox is only inserted in the database's `onCreate`. Room does not call `onCreate` after a destructive migration (it drops and recreates the tables, then opens), so a reset database has no Inbox.
- The database builder has `fallbackToDestructiveMigration(false)` in every build, so any schema downgrade, or any upgrade with a missing migration, silently wipes the user's data. This is what produced the empty database in the first place.

Without a category the editor falls back to `categoryId = 0L`, which is never a persisted id, so a task saved that way would exist but show up nowhere.

## What Changes

- The default category is ensured every time the database opens, not only when the file is first created. It is inserted only when the database has no default category, and an existing row is never rewritten (its name stays user-owned).
- This covers a fresh database, a database that lost its categories, and a database that was just reset by a destructive migration. (Room's `onDestructiveMigration` callback fires between "drop all tables" and "create all tables", so the table does not exist yet and it cannot be the place to seed.)
- **BREAKING (data safety, release builds)**: release builds no longer wipe the database on a schema downgrade or on a missing migration path. Room throws instead and leaves the file untouched, so reinstalling the matching version recovers the data. Debug builds keep a wipe on downgrade only (switching branches with different schema versions on one emulator is a normal workflow there), log a warning when it happens, and still fail loudly on a missing upgrade path.
- Task creation can no longer end up with `categoryId = 0L`: the Tasks screen stops inventing it as a category selection when no category exists, the add-task entry point refuses to open the editor when there is no category to put the task in, and saving refuses a task without a real category. Both say so in a snackbar instead of failing silently.

### Non-goals

- No change to the editor sheet footer or any other editor layout (separate work).
- No change to how users create, rename, delete or reorder categories. Deleting a non-default Inbox stays possible; it is not resurrected while another category is the default.
- No Room schema change and no new migration (schema stays at 14).
- No recovery UI for a release build that hits a schema mismatch: it fails loudly, as an unhandled Room error.

## Capabilities

### New Capabilities

- `database-schema-safety`: what the app does when the database file's schema version does not match the build (downgrade or missing migration), per build type.

### Modified Capabilities

- `default-category-persistence`: the default category is also restored when it is missing, including after a database reset, and task creation requires an existing category.

## Impact

- Data: `core/data/local/TempoDatabase.kt` (seeding moves to `onOpen`, fallback policy per build type, shared builder configuration used by the app and by tests).
- UI/ViewModel: `TasksViewModelDataLoading.kt`, `TasksViewModelTaskActions.kt`, `TaskBottomSheetContent.kt`, plus one new snackbar string in `values` and `values-es`.
- Tests: instrumented database tests (fresh create, missing Inbox, renamed Inbox, destructive reset, release downgrade), unit tests for the ViewModel guards.
- Release behaviour: users on a release build can no longer lose data to a schema mismatch (see **BREAKING** above). Called out explicitly in the PR.
