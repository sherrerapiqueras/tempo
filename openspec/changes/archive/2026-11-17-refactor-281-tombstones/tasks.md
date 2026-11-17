# Tasks

## 1. Schema and triggers

- [x] 1.1 Add `TombstoneEntity` (`uid` primary key, `entityType`, `deletedAt`, index on `deletedAt`) and a `TombstoneDao` (`getAll`, `get(uid)`, `purgeOlderThan`). Register them in `TempoDatabase`. Verify: `kspDebugKotlin` generates `16.json`.
- [x] 1.2 Add the delete and re-insert tombstone triggers to `EntityIdentitySchema.createTriggers`. Write `MIGRATION_15_16` (create the table and index, recreate the triggers) and bump the version to 16. Verify with a populated 15 → 16 migration test and the 1 → 16 path.
- [x] 1.3 Add the purge-on-open callback with a 180-day window, registered in `configure`. Verify with an instrumented test: a 181-day-old tombstone is purged and a 10-day-old one is kept.

## 2. Behaviour

- [x] 2.1 Add an instrumented `TombstoneTest` on the configured database, covering: delete a task records a tombstone; bulk-clearing completed tasks records each; deleting a category through the repository records the category, its tasks and its sections; deleting a task tree records every node; Undo (restore snapshot) clears the tombstones and keeps the `uid`; a new insert doesn't touch other tombstones. Verify: all pass.
- [x] 2.2 Run the full instrumented suite to confirm existing delete and undo behaviour is unchanged. Verified on the Pixel_10 AVD: 405 of 406. The one failure, `MainActivityTabWarmupTest`, passes on its own rerun (cold-boot timing), and all 65 data tests pass on the final build.

## 3. Docs and integration

- [x] 3.1 Document version 16 and the tombstone triggers in `docs/implementation/MIGRATIONS.md`, and the purge window plus full-resync rule in `docs/design/sync-boundary.md`. Verify by review.
- [x] 3.2 Run `./gradlew ktlintFormat ktlintCheck :app:detekt testDebugUnitTest koverVerifyDebug` and `openspec validate refactor-281-tombstones --strict`. Verify: green.

## Workflow follow-up

- Archive the change in the same PR.
