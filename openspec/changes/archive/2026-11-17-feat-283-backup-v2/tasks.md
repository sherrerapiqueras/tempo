# Tasks

## 1. Format

- [x] 1.1 Add `uid`, `createdAt` and `updatedAt` to the six identity-bearing DTOs and both mapper directions, and set `BACKUP_SCHEMA_VERSION = 2`. Verified by `BackupRepositoryImplTest`: the export round trip keeps identities.
- [x] 1.2 Freeze `v2-backup.json`, and test that it restores identities on Replace while `v1-backup.json` still imports. Verified: unit tests pass.

## 2. Import behaviour

- [x] 2.1 Make `MergePlanner` match by identity before natural keys, for all kinds including sections, with identity matches counted as skipped and never cascading. Verified by four new `MergePlannerTest` cases (renamed record, children by identity, same file twice, unknown identity falling back).
- [x] 2.2 Merge inserts keep the incoming identity, and focus sessions are skipped by identity too. Verified by unit tests.
- [x] 2.3 `BackupPayloadValidator` drops malformed and repeated `uid`s. Verified by `BackupPayloadValidatorTest`.

## 3. Docs and integration

- [x] 3.1 Document version 2, identity merge and the version history in `docs/BACKUP_FORMAT.md`. Verified by review.
- [x] 3.2 Run lint, detekt, unit tests, kover, the full instrumented suite and `openspec validate --strict`. Verified green: 406 of 406 instrumented on the Pixel_10 AVD.

## Workflow follow-up

- Archive in the same PR.
