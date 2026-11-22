# Tasks

- [x] 1.1 `DRIVE_BACKUP_ENABLED` build config field: true in debug, false in release, with a pointer to #565.
- [x] 1.2 `FeatureFlags` domain model, provided from `BuildConfig`.
- [x] 1.3 Settings: hide the Drive entry, ignore requests to open its page, skip the Drive delegate when disabled.
- [x] 1.4 Onboarding: hide the restore offer and ignore the restore event when disabled.
- [x] 1.5 Unit tests for both view models with the flag off.
- [x] 1.6 `openspec validate feat-565-drive-backup-release-flag`; ktlint, detekt, unit tests, Kover, lint, release build.
- [x] 1.7 Release build on the emulator: no Drive entry in Settings, no restore offer on the welcome page.
