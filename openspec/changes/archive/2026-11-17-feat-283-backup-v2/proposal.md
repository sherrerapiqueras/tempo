# Proposal

## Why

The backup format is keyed on local row ids, which mean nothing outside the device that wrote them. Merging a backup onto a device in use can't tell "this is the task I already have" from "a new task with the same title". Merge quality is capped by the format. Since #30 every record has a stable `uid`, so the file can carry it.

Tracked by [#283](https://github.com/sherrerapiqueras/tempo/issues/283), part of [#272](https://github.com/sherrerapiqueras/tempo/issues/272).

## What Changes

- **Backup schema version 2:** categories, sections, tasks, habits, chains and focus sessions carry `uid`, `createdAt` and `updatedAt`. Export writes version 2.
- **Replace** restores identities and timestamps exactly.
- **Merge matches by identity first.** A known `uid` maps onto the local record, the device keeps its own version, and it isn't a conflict. Merged-in records keep their `uid`, so merging the same file twice adds nothing.
- **Version-1 files still import.** Natural-key merge applies as before, records get fresh `uid`s, and the legacy Inbox (`id = -1`) gets the well-known `uid`.
- **Malformed or repeated `uid`s in a file are dropped** (that record imports as new), never fatal.
- The encryption envelope version is unchanged and stays independent.

**Non-goals:**
- last-writer-wins merging of differing content (that's sync, #31)
- carrying tombstones or attachments in backups

## Capabilities

### New Capabilities

- `backup-identity`: how backups carry record identity, and how import uses it.

## Impact

- `BackupFileDto` and `BackupMapper`, `BACKUP_SCHEMA_VERSION = 2`, `BackupPayloadValidator` (identity sanitising), `MergePlanner` (identity-first), and `BackupRepositoryImpl` (merge inserts keep identity).
- A new frozen fixture, `app/src/test/resources/backup/v2-backup.json`.
- `docs/BACKUP_FORMAT.md` documents v2 and adds a version history.
