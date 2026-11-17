# Design

## Context

See proposal.md. #304 chose `drive.appdata` (a hidden per-app folder, non-sensitive scope), with `AuthorizationClient` and silent re-authorisation from a worker. #305 chose a stored backup passphrase feeding the existing envelope v1. `BackupRepository.exportEncrypted(passphrase)` already produces the file.

## Decisions

### D1. Layering
- **Domain** (`features/backup/domain`, pure Kotlin): `DriveBackupSettings`, `DriveBackupStatus`, `DriveBackupFailure`, `CloudBackupFile`; the `BackupPassphraseStore`, `DriveBackupPreferences`, `CloudBackupStore`, `CloudAuthorization` and `DriveBackupScheduler` interfaces; and the `EnableDriveBackupUseCase`, `DisableDriveBackupUseCase`, `ChangeBackupPassphraseUseCase` and `RunDriveBackupUseCase` use cases.
- **Infrastructure** (`infrastructure/backup/drive`): implementations backed by Keystore, SharedPreferences, Drive REST and Play services, plus the worker, its scheduler and the notifier.
- **Presentation:** `SettingsDriveBackupDelegate` owns a `driveBackup` slice of Settings state. The interactive consent (a `PendingIntent` from `AuthorizationClient`) is launched from the screen, never from the ViewModel. The ViewModel only learns the outcome.

### D2. Drive REST over `HttpURLConnection`
Four calls in Drive v3: list (`spaces=appDataFolder`), multipart upload, delete, and download (for #307). This doesn't justify the Google API client (several MB plus Guava) or a new HTTP stack. The transport sits behind a small `HttpTransport` interface, so request building and response parsing are unit-tested with a fake. HTTP 401 maps to `AuthorizationNeeded`, 403 with `storageQuotaExceeded` to `StorageFull`, and an `IOException` to `Network`.

### D3. Authorisation
`GoogleDriveAuthorization.authorize()` calls `AuthorizationClient.authorize` with the `drive.appdata` scope. If the result needs no resolution, it returns the access token (valid about an hour, so one is fetched per run). If it needs one, it returns `NeedsUserConsent`. Refresh tokens are never stored. A 401 during a run counts as `AuthorizationNeeded` (no retry loop).

### D4. Passphrase storage
`KeystoreBackupPassphraseStore` does AES-256-GCM under a non-exportable Keystore key (`tempo_backup_passphrase_key`), with the blob stored as `iv.ciphertext` in `tempo_backup_secret_prefs`. It mirrors `KeystoreDbPassphraseProvider`. Both the file and the key are excluded from Auto Backup and transfer. An unwrap failure returns null, which the run reports as `PassphraseUnavailable`.

### D5. Scheduling
Unique periodic work (`DriveBackupWorker`) runs every 1 day or 7 days with `NetworkType.UNMETERED` or `CONNECTED` plus `requiresBatteryNotLow`. Settings changes use `ExistingPeriodicWorkPolicy.UPDATE`. "Back up now" is unique one-time work. A `Network` failure returns `Result.retry()` with exponential backoff, and every other failure returns `Result.success()` after recording it, so WorkManager doesn't spin on a cause a retry can't fix.

### D6. Status
`DriveBackupPreferences` holds the settings plus `lastSuccessAt`, `lastFailureAt` and `lastFailure` as a `StateFlow`. A failure is shown only if it's newer than the last success. A notification is raised for `AuthorizationNeeded`, `StorageFull` and `PassphraseUnavailable`, through a dedicated channel that opens Settings.

### D7. File naming and retention
Files are named `tempo-backup-<epochMillis>.tempo`. Retention keeps the 5 newest by `createdTime` and deletes the rest after a successful upload. A failure to prune doesn't fail the backup.

## Risks / Trade-offs

- **[Risk] No real Drive in CI.** → The transport and authorisation are behind interfaces with fakes. The real-Drive check waits for the user's OAuth client (#12 PR notes), and the acceptance criteria that need it stay unticked until then.
- **[Trade-off] The whole dataset uploads each time.** → Daily on Wi-Fi by default keeps metered usage at zero. Payloads are small.
- **Transactions vs. side effects:** the export reads in one database transaction. Upload, prune and status writes happen after it, outside the database.
