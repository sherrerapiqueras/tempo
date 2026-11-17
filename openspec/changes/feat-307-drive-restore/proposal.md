# Proposal

## Why

A backup nobody can restore is a guess, not a safety net. #306 writes the backups. This is the half that matters: getting the data back on a new phone in a couple of taps, or from Settings later.

Tracked by [#307](https://github.com/sherrerapiqueras/tempo/issues/307), part of [#12](https://github.com/sherrerapiqueras/tempo/issues/12). Key model: [#305](https://github.com/sherrerapiqueras/tempo/issues/305).

## What Changes

- **Onboarding opens with the Welcome page, which offers "Restore from Google Drive"** ("Coming from another phone?"), so someone who knows Tempo can restore before skipping the rest; and the Google Drive backup page in Settings has a Restore section, for anyone who skipped it.
- **One restore flow behind both:**
  1. connect Drive (consent if needed);
  2. find this account's backups and offer the newest, with its date and size;
  3. ask **merge or replace** only when the device already holds the user's data (a fresh install restores by replacing);
  4. ask the backup passphrase;
  5. restore with a progress indicator.
- **Failures are specific:**
  - wrong passphrase (asks again, saying so)
  - damaged backup
  - backup from a newer Tempo version (says to update)
  - inconsistent contents
  - no network
  - no Drive access
  - Drive unavailable

  Every failure leaves local data untouched.
- **After restoring**, if Drive backup isn't on yet, the flow offers to keep backing up with the passphrase just typed (#305).
- English and Spanish strings.

**Non-goals:** choosing among older backups (the newest is offered), and Block Store or account-based restore (#291).

## Capabilities

### New Capabilities

- `drive-restore`: offering, choosing the mode for, running and reporting a restore from Google Drive.

## Impact

- **Domain:** `ListDriveBackupsUseCase`, `RestoreDriveBackupUseCase` (download, then `ImportBackupUseCase`, so validation, the single transaction and reminder rescheduling all apply), `HasLocalDataUseCase`, and `BackupRepository.hasUserData()`.
- **Presentation:** a shared `DriveRestoreFlow` with `DriveRestoreDialogs`, embedded by `SettingsDriveBackupDelegate` and `OnboardingViewModel`, each with its own consent launcher.
