# Tasks

## 1. Remove the sentinel

- [x] 1.1 Replace `DEFAULT_INBOX_CATEGORY_ENTITY` (`id = -1`) with an id-less seed template. Change the `inboxCallback` seed to insert without an explicit id, keeping `INSERT OR IGNORE` on the well-known `uid`. Verify: the seeding instrumented tests pass, looking the Inbox up by `isDefault` or `uid`.
- [x] 1.2 Remove `TaskEntity.categoryId`'s default and fix every caller. Verify: the project compiles and the unit tests pass.
- [x] 1.3 Backup import: map legacy id `-1` to the well-known `uid` in the replace import, and restore a default only when the payload has none. Verify with an updated `BackupRepositoryImplTest` and an instrumented replace import of a legacy payload.
- [x] 1.4 Audited main code for `-1` category references. Only historical migrations (`MIGRATION_3_4`, `4_5`, the 14 → 15 backfill) and the v1-backup mapping remain, all through `LEGACY_INBOX_ID`. The deprecated domain `DEFAULT_INBOX_CATEGORY` has no main-code users and is now only a test fixture.

## 2. Integration

- [x] 2.1 Run lint, detekt, unit tests, kover, the full instrumented suite and `openspec validate --strict`. Verify: green.

## Workflow follow-up

- Archive in the same PR. The PR refers to #326 without closing it (per-space criteria remain for #324).
