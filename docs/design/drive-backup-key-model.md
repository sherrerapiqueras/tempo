# Drive Backup — Key Model

How scheduled Google Drive backups (#306) are encrypted, and how they're decrypted on a phone that
has never seen the key. Decided in #305.

## Decision

**A backup passphrase the user sets once, when turning Drive backup on.**

- Tempo stores it on the device, wrapped by an Android Keystore key, so scheduled backups can
  encrypt without anyone present.
- To restore on a new phone, the user types it again.
- It is never sent anywhere: not to Google, not to Tempo.

### What Tempo can honestly promise

- **Google can't read your backups.** Drive holds only ciphertext (AES-256-GCM under a key
  stretched from your passphrase with PBKDF2-HMAC-SHA256). Google never sees the passphrase.
- **Tempo can't read them either, and can't recover them.** If you forget the passphrase, nobody
  can decrypt the backups, including Tempo. That's the cost of the promise above, and the setup
  screen says so plainly.

### Options considered

| Option | Recoverability | Privacy claim | Why not chosen |
|:---|:---|:---|:---|
| **Backup passphrase** (chosen) | You must remember it | Strongest: the key exists only in your head and, wrapped, on your devices | — |
| Generated recovery code | You must keep the code | Same as above | A code shown once is easier to lose than a passphrase you chose, and it adds a second kind of secret to explain |
| Key in Block Store | Automatic on a new phone, through Google's device transfer | Weaker: the key travels with your Google account backup | Depends on #291 (milestone 2.0, blocked by account issuance), and only works where Block Store is available |

Block Store remains an option later. Once #291 lands, it could *additionally* carry the
passphrase, so a device transfer restores without typing it. That would be an opt-in
convenience, not a replacement, and the passphrase would still restore on its own.

## Storage on the device

- The passphrase is encrypted with AES-256-GCM under a non-exportable Android Keystore key
  (`tempo_backup_passphrase_key`). The result lives in its own SharedPreferences file,
  `tempo_backup_secret_prefs`, the same pattern as the database passphrase
  (`KeystoreDbPassphraseProvider`).
- A new passphrase gets a new key under the other of two aliases (`tempo_backup_passphrase_key`
  and `tempo_backup_passphrase_key_b`), and the old key is deleted only after the new blob is
  committed. A change that fails part-way leaves the previous passphrase working.
- That file is **excluded from Auto Backup and device transfer** (`backup_rules.xml`,
  `data_extraction_rules.xml`), like `tempo_secure_prefs`. A wrapped blob is useless without its
  hardware-bound key, so carrying it would only make a new phone think it has a passphrase it
  can't use.
- It is never logged, never shown again after setup, and never included in a backup.
- **If the Keystore key is lost** (lock-screen reset on some devices, or OS key invalidation),
  scheduled backup stops with an actionable "Set your backup passphrase again" failure. It never
  silently switches to an unencrypted or different key.

## Restore on a new device, end to end

1. The user installs Tempo and authorises Drive (`drive.appdata`) during onboarding (#307).
2. Tempo lists the backups in the app data folder and shows the newest one's date and size.
3. The user types the backup passphrase. A wrong passphrase is reported as wrong, distinctly from
   a corrupt file, a newer schema, or a network failure.
4. The backup is restored through the existing import path (`BackupRepository.importFromJson`):
   validation before any write, everything in one transaction.
5. On success Tempo offers to keep backing up to the same Drive with the passphrase just typed,
   so it's stored again on this device and nothing new has to be invented.

## Manual export and import

Manual export is unchanged: it asks for a passphrase per export and never stores it. Drive
backups use the stored backup passphrase. **Both produce the same file format**
(`docs/BACKUP_FORMAT.md`, encryption envelope version 1). A Drive backup downloaded by hand
imports through the normal Import with the backup passphrase, and the two paths never interfere.

## Envelope version

Unchanged: **encryption envelope version 1.** The stored passphrase feeds the same
passphrase-based envelope, so no reader needs to change.

## Without Block Store

This model doesn't use Block Store, so its absence changes nothing. Scheduled backup works on
any device with Google Play services and a Keystore.

## Changing or forgetting the passphrase

- **Changing it** from Settings applies to backups written afterwards. Existing backups in Drive
  stay readable only with the passphrase they were written under. The screen says so, and the
  next backup is taken immediately, so the newest backup always uses the current passphrase.
- **Forgetting it** can't be fixed: those backups can't be read. The user can set a new
  passphrase, and new backups will use it.

## Setup copy (implemented in #306)

The screen that turns Drive backup on must say, before the user confirms:

- that the passphrase is needed to restore on a new phone;
- that Tempo can't recover it, and a forgotten passphrase means the backups can't be read;
- that it's stored on this device to back up automatically, and never sent to Google or Tempo.

It asks for the passphrase twice, with the same minimum length as manual export.
