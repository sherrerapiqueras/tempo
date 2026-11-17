# Spec Delta

## Purpose

Gets a user's data back from their Google Drive backup on a new or reset phone, or from Settings at any time, without ever losing or duplicating what is already there.

## ADDED Requirements

### Requirement: Restore is offered during onboarding and in Settings
Onboarding's last page and Settings SHALL offer to restore from Google Drive. Starting it SHALL connect to Drive (asking consent when needed) and SHALL offer the newest backup found, showing when it was taken and its size, or say that the account has no backups.

#### Scenario: A backup exists
- **WHEN** the user starts a restore and the account has backups
- **THEN** the newest one is offered with its date and size

#### Scenario: None exists
- **WHEN** the account has no Tempo backups
- **THEN** the user is told so and nothing changes

### Requirement: Restoring onto existing data is a choice
When the device already holds the user's data (any task, habit or chain, or a category beyond the seeded default), the user SHALL choose to merge or replace before restoring. A device with no such data SHALL be restored by replacing without asking.

#### Scenario: Fresh install
- **WHEN** a fresh install restores a backup
- **THEN** it does not ask merge or replace

#### Scenario: Device in use
- **WHEN** a device that already has tasks restores a backup
- **THEN** the user chooses merge or replace first

### Requirement: The backup passphrase decrypts the restore
Restoring SHALL ask for the backup passphrase. A wrong passphrase SHALL ask again, stating it was wrong, and SHALL change nothing.

#### Scenario: Wrong then right
- **WHEN** the user enters a wrong passphrase and then the right one
- **THEN** the first attempt is reported as wrong and the second restores

### Requirement: Failures are specific and harmless
A restore that cannot complete SHALL say why: a damaged backup, a backup from a newer version (with advice to update), inconsistent contents, no network, no Drive access, or Drive unavailable. Local data SHALL be unchanged in every case.

#### Scenario: Newer backup
- **WHEN** the backup was written by a newer Tempo version
- **THEN** the user is told to update Tempo, and nothing is changed

### Requirement: Offer to keep backing up
After a successful restore on a device where Drive backup is off, the user SHALL be offered to turn it on with the passphrase they just used. Declining SHALL store nothing.

#### Scenario: Keep backing up
- **WHEN** the user accepts after restoring
- **THEN** Drive backup is on with that passphrase
