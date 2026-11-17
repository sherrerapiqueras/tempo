# Proposal

## Why

Manual export protects a user only if they remember to export and keep the file somewhere safe. The backup that actually saves people is the one that runs by itself and is there when the phone is gone. #305 settled how such a backup can be encrypted unattended (a stored backup passphrase); this builds it.

Tracked by [#306](https://github.com/sherrerapiqueras/tempo/issues/306), part of [#12](https://github.com/sherrerapiqueras/tempo/issues/12). Scope decision: [#304](https://github.com/sherrerapiqueras/tempo/issues/304) (`drive.appdata`). Key model: [#305](https://github.com/sherrerapiqueras/tempo/issues/305).

## What Changes

- **Settings → Backup gains a Google Drive section.** The user turns it on, authorises Drive (`drive.appdata` only), and sets a backup passphrase. The setup screen states what they'll need to restore.
- **Backups run on a schedule** (daily by default, or weekly) through WorkManager, on Wi-Fi only by default or on any network. "Back up now" runs one immediately.
- **Each backup is the existing encrypted `.tempo` file** (schema v2, encryption envelope v1), written with the stored backup passphrase to Drive's hidden app data folder.
- **Retention:** the newest 5 backups are kept, and older ones are deleted after each successful upload.
- **The status line is the most prominent thing in the section.** It shows the last successful backup ("Never" if there hasn't been one) and the last failure with a specific cause and an action:
  - authorisation expired → *Reconnect*
  - Drive full → *Free up space*
  - passphrase lost on this device → *Set passphrase*
  - no network → retried automatically
- **A notification** reports a failure the user has to fix, since a stopped backup is otherwise silent.
- The passphrase can be changed. Turning backup off stops the schedule and forgets the passphrase on this device, and leaves the existing backups in Drive.
- English and Spanish strings.

**Non-goals:** restore (#307), Block Store (#291), other storage providers (#308), and any Tempo account (none is needed).

## Capabilities

### New Capabilities

- `drive-backup`: turning scheduled Drive backup on and off, its schedule and constraints, encryption, retention, status reporting, and failure handling.

## Impact

- **Dependencies:** `play-services-auth` (`AuthorizationClient`) and `kotlinx-coroutines-play-services`.
- **Domain:**
  - models for settings, status, failure and cloud files;
  - interfaces for the passphrase store, preferences, cloud store, authorization and scheduler;
  - use cases to enable, disable, change the passphrase and run a backup.
- **Infrastructure:**
  - a Keystore-wrapped passphrase store (`tempo_backup_secret_prefs`, excluded from backup and transfer);
  - SharedPreferences for settings and status;
  - a Drive v3 REST client over `HttpURLConnection`;
  - `GoogleDriveAuthorization`;
  - a Hilt `DriveBackupWorker` and its scheduler;
  - a failure notifier.
- **Settings UI:** a delegate, contract additions, a section, a passphrase dialog, and the consent launcher.
- Backup and data-extraction rules, the sync-boundary doc, and the README section on backups.
