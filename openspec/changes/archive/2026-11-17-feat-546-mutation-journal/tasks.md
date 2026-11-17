# Tasks

## 1. Journal

- [x] 1.1 Add `PendingChangeEntity` (`uid` primary key, `entityType`, `operation`, `seq`, `changedAt`, index on `seq`) and `PendingChangeDao` (`getAll` ordered by `seq`, `acknowledge(uid, seq)`). Bump the database to 17 with `MIGRATION_16_17`. Verify: `17.json` is generated.
- [x] 1.2 Add `JournalSchema` with insert, update and delete triggers that upsert the record's row with a new `seq`, recreated on every open with the other identity triggers. Verify with the trigger-presence tests.
- [x] 1.3 Add an instrumented `MutationJournalTest` covering: create, edit and delete through repositories; coalescing; category cascade; ordering; acknowledge with and without a later edit; Undo turning a delete back into an upsert. Add a 16 → 17 migration test. Verify: all pass.

## 2. Docs and integration

- [x] 2.1 Document version 17 and the journal triggers in `MIGRATIONS.md`, and how the sync engine uses the journal in `sync-boundary.md`. Verify by review.
- [x] 2.2 Run lint, detekt, unit tests, kover, the full instrumented suite and `openspec validate --strict`. Verified green: 412 of 412 instrumented on the Pixel_10 AVD.

## Workflow follow-up

- Archive in the same PR.
