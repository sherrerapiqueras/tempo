# Tasks

## 1. Domain

- [x] 1.1 Add `ListDriveBackupsUseCase`, `RestoreDriveBackupUseCase` and `HasLocalDataUseCase`, plus `BackupRepository.hasUserData()`. Verified by `DriveRestoreUseCasesTest` (ordering, each failure, outcome mapping) and the instrumented `HasUserDataTest` (a fresh install's seeded Inbox counts as no data).

## 2. Flow and UI

- [x] 2.1 Add the `DriveRestoreFlow` state machine and `DriveRestoreDialogs`. Verified by `DriveRestoreFlowTest`: consent, offer, none, mode only with data, wrong passphrase, specific failures, keep or decline backing up, already-on, dismiss. Also by the instrumented `DriveRestoreDialogsTest`.
- [x] 2.2 Wire the flow into Settings (a restore row and its consent launcher) and onboarding (a Welcome-page button, the consent launcher and dialogs). Verified by delegate and ViewModel tests and `OnboardingContentTest`.
- [x] 2.3 Add strings in English and Spanish. Verified: `lintDebug` passes.

## 3. Integration

- [x] 3.1 Run lint, detekt, unit tests, kover, the instrumented suite and `openspec validate --strict`. Verified green.
- [ ] 3.2 Restore end to end on a wiped device with real Drive. This needs the user's OAuth client; it stays open until done.

## Workflow follow-up

- Archive once 3.2 is done.
