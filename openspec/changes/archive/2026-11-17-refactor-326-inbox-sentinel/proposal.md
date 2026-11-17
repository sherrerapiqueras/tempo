# Proposal

## Why

The Inbox is still special-cased by a reserved local id: `DEFAULT_INBOX_CATEGORY_ENTITY` has `id = -1`, the seed inserts at `-1`, `TaskEntity` defaults `categoryId` to `-1`, and backup import re-creates a row at `-1` because "other code references it directly". Reserved ids can't survive multiple default categories (Spaces, #324). They also aren't an identity that means anything across devices, which #30 now provides through `uid`.

Tracked by [#326](https://github.com/sherrerapiqueras/tempo/issues/326), part of [#272](https://github.com/sherrerapiqueras/tempo/issues/272). This change is the space-independent part. The per-space criteria (one default per space, seeding on space creation, protecting a space's default) need Spaces itself and stay with #324 (milestone 2.1).

## What Changes

- **No code resolves the Inbox, or any default category, by a reserved id.** "The default category" is the row with `isDefault = 1`. "The seeded Inbox" is the row with the well-known `uid` from #30.
- **Fresh installs seed the Inbox with an ordinary autoincrement id** and the well-known `uid`. Existing installs keep their `-1` row unchanged, and nothing depends on that value any more.
- **`TaskEntity.categoryId` has no default.** Every task is created in a category the caller names, as the spec already requires.
- **Backup import:**
  - Version-1 files identify the seeded Inbox by its legacy id `-1`. On import, that category gets the well-known `uid`, so a restored Inbox stays the same record.
  - Import no longer inserts an extra Inbox when the file already brings a default category. It only restores a default when none is present.
- No database version change and no migration.

**Non-goals:** anything per space (#324), and backup v2 (#283).

## Capabilities

### Modified Capabilities

- `default-category-persistence`: a requirement is added: the default category isn't identified by a reserved local id.

## Impact

- `CategoryEntity.kt` (the seed template replaces the `-1` constant), `TaskEntity.kt`, the `TempoDatabase` Inbox seed, and `BackupRepositoryImpl.ensureDefaultCategory` plus the replace import.
- Tests that relied on the Inbox being `-1` look it up by `isDefault` or `uid` instead.
