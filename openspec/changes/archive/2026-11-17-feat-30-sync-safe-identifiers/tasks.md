# Tasks

## 1. Groundwork

- [x] 1.1 Pick the "now" expression for the triggers. Resolved: use the portable `julianday('now')` form, because `MigrationTestHelper` and older framework SQLite (API 24 ships 3.9) lack `unixepoch('subsec')`. Verified by the trigger test in 2.5.
- [x] 1.2 Add the `UNSET_UID`, `UNSET_TIMESTAMP` and `DEFAULT_INBOX_UID` constants in the domain core (the database assigns `uid`s, see design D2). Verified by `EntityIdentityTest`.

## 2. Schema and migration

- [x] 2.1 Add `uid`, `createdAt` and `updatedAt` to the seven entities, with column defaults through `@ColumnInfo(defaultValue)` and a unique `uid` index. The Inbox entity gets `DEFAULT_INBOX_UID`. Verified: `./gradlew kspDebugKotlin` succeeds.
- [x] 2.2 Write `MIGRATION_14_15` in `EntityIdentitySchema`: add the columns, backfill `uid` in SQL (the seeded Inbox `id = -1` gets the fixed value), backfill `createdAt`/`updatedAt` per D7, then create the indexes and triggers. Bump to version 15. Verified: `app/schemas/.../15.json` is generated.
- [x] 2.3 Recreate the triggers in an `onOpen` callback registered in `configure` before the Inbox seed, and add the fixed `uid` to the seed insert. Verified by `EntityIdentityTriggersTest.everyIdentityTableHasItsTriggersAfterOpen` and `MigrationTest.migrateAll_fromVersion1To15`.
- [x] 2.4 Extend `MigrationTest` with a populated v14 → 15 migration (all seven tables plus chain members and the Inbox) and the full 1 → 15 path. Verified on the Pixel_10 AVD with `am instrument`.
- [x] 2.5 Add `EntityIdentityTriggersTest`, covering: insert stamping; explicit times kept; raw insert without `uid`; update stamping; explicit `updatedAt` kept; an update from a row without metadata keeps its identity; overwriting a `uid` aborts; rapid updates strictly advance without recursing; bulk `@Query` updates stamp. Verified: all pass.
- [x] 2.6 Update `docs/implementation/MIGRATIONS.md`: version rows 9–15 and an "Identity triggers" section. Verified by review.

## 3. Domain and mappers

- [x] 3.1 Add `uid`, `createdAt` and `updatedAt` with unset defaults to the seven domain models, and copy them both ways in every mapper. Verified by `IdentityMetadataMappingTest` (seven round trips).
- [x] 3.2 Add `asNew()` per model and route every copy-as-new site through it: `Task.asNextOccurrence`, the subtask clones in `ToggleTaskCompletionUseCase` and `RollOverduePeriodicTaskUseCase`, `TaskAttachmentRepositoryImpl.attach`, and all `BackupRepositoryImpl` import inserts. Entity-level resets cover `copyAttachments` and `SavedSessionDao.remember`. Verified: `grep -rn "id = 0" app/src/main` leaves only intentional matches, and `IdentityMetadataMappingTest` asserts `asNew()` and next occurrence clear the identity.
- [x] 3.3 Build `MergePlanner.contentKey()` from `asNew()` so it ignores the metadata. Verified: the existing `MergePlanner` tests pass unchanged (whole unit suite green).
- [x] 3.4 Assert that Undo keeps a task's `uid`, `createdAt` and `updatedAt` in `TaskRepositoryRoomIntegrationTest.deleteTaskWithSnapshot_restorePreservesIdsAndHierarchy`. Verified: the full instrumented suite passes, 399 out of 399.

## 4. Integration

- [x] 4.1 Run `./gradlew ktlintFormat ktlintCheck :app:detekt testDebugUnitTest koverVerifyDebug`. Verified green (1779 unit tests).
- [x] 4.2 Run `openspec validate feat-30-sync-safe-identifiers --strict`. Verify that it passes.
- [x] 4.3 Fresh install on the Pixel_10 AVD launches to onboarding with no crash in the crash buffer. Create, edit, complete and Undo were exercised against the real database by the full instrumented suite (399 out of 399), not by hand in the UI. An upgrade of a device that already has data is covered by `MigrationTest` rather than a manual upgrade.

## Workflow follow-up

- Archive the change in the same PR before the final CI run, according to AGENTS.md.
- PR title: `feat(#30): add sync-safe identifiers and merge metadata to core entities`, with `Closes #30`.
