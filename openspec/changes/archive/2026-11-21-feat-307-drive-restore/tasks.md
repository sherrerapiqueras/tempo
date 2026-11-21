# Tasks

## 1. Domain

- [x] 1.1 Add `ListDriveBackupsUseCase`, `RestoreDriveBackupUseCase` and `HasLocalDataUseCase`, plus `BackupRepository.hasUserData()`. Verified by `DriveRestoreUseCasesTest` (ordering, each failure, outcome mapping) and the instrumented `HasUserDataTest` (a fresh install's seeded Inbox counts as no data).

## 2. Flow and UI

- [x] 2.1 Add the `DriveRestoreFlow` state machine and `DriveRestoreDialogs`. Verified by `DriveRestoreFlowTest`: consent, offer, none, mode only with data, wrong passphrase, specific failures, keep or decline backing up, already-on, dismiss. Also by the instrumented `DriveRestoreDialogsTest`.
- [x] 2.2 Wire the flow into Settings (a Restore section on the Google Drive backup page, shown whether backup is on or off, and its consent launcher) and onboarding (the Welcome page moved first, with a tonal restore button, the consent launcher and dialogs). Verified by delegate and ViewModel tests and `OnboardingContentTest`.
- [x] 2.3 Add strings in English and Spanish. Verified: `lintDebug` passes.

## 3. Integration

- [x] 3.1 Run lint, detekt, unit tests, kover, the instrumented suite and `openspec validate --strict`. Verified green.
- [x] 3.2 Restore end to end on a wiped device with real Drive. Verified on a Pixel 7 (Android 16), debug build, 2026-11-21: a backup taken with two tasks (one with a subtask), a second list and a habit; the app uninstalled and reinstalled on a user where it had never run; onboarding offered the backup, and the restore brought back every item. A wrong passphrase showed "That passphrase doesn't open this backup"; restoring again from Settings onto a device with a new local task asked for the mode, and merge kept the local task with no duplicates. Backup resumed and uploaded afterwards.
