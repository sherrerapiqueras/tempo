# Spec Delta

## Purpose

Backs the user's data up to their own Google Drive on a schedule, encrypted with a passphrase only they know, and tells them honestly whether it's working.

## ADDED Requirements

### Requirement: Turning on Drive backup
The user SHALL be able to turn on Drive backup from Settings. Turning it on SHALL require authorising Google Drive for the app-data folder only, and setting a backup passphrase entered twice. Before confirming, the setup SHALL state that the passphrase is needed to restore on a new phone, that it can't be recovered, and that it's stored on this device and never sent to Google or Tempo.

#### Scenario: Setup
- **WHEN** the user turns on Drive backup, authorises Drive and confirms a passphrase
- **THEN** backup is on, scheduled, and a first backup starts immediately

#### Scenario: Authorisation declined
- **WHEN** the user declines the Drive authorisation
- **THEN** backup stays off and nothing is stored

### Requirement: Scheduled backups
While on, backups SHALL run on the chosen frequency (daily or weekly), only on the chosen network kind (Wi-Fi only, or any network), and "Back up now" SHALL run one immediately.

#### Scenario: Wi-Fi only
- **WHEN** backup is set to Wi-Fi only and the device is on mobile data
- **THEN** the scheduled backup waits until an unmetered network is available

### Requirement: Backups are encrypted with the backup passphrase
Each backup SHALL be the app's encrypted backup file, encrypted with the stored backup passphrase, so it can be restored with that passphrase on any device.

#### Scenario: Restorable with the passphrase
- **WHEN** a Drive backup is downloaded and imported with the backup passphrase
- **THEN** it imports like any manual export

### Requirement: Retention
After each successful backup, only the 5 newest backups SHALL remain in Drive.

#### Scenario: Sixth backup
- **WHEN** a sixth backup succeeds
- **THEN** the oldest backup is deleted and 5 remain

### Requirement: Honest status
Settings SHALL show when the last backup succeeded, "Never" if none has, and the last failure if it is more recent than the last success, with its cause.

#### Scenario: Never backed up
- **WHEN** backup was turned on but no backup has succeeded
- **THEN** the status says it has never backed up

### Requirement: Actionable failures
A failed backup SHALL record a specific cause: no network, Drive storage full, authorisation needed, passphrase unavailable on this device, or unexpected. Network failures SHALL be retried automatically. Authorisation, storage and passphrase failures SHALL be shown with an action that fixes them, and SHALL raise a notification.

#### Scenario: Authorisation expired
- **WHEN** a scheduled backup finds Drive authorisation is no longer granted
- **THEN** the status shows "Reconnect Google Drive", a notification is raised, and reconnecting resumes backups

#### Scenario: Passphrase lost on this device
- **WHEN** the stored passphrase can no longer be read
- **THEN** the status asks the user to set the backup passphrase again, and no backup is written with any other key

### Requirement: Turning off
Turning Drive backup off SHALL stop scheduled backups and forget the passphrase on this device, and SHALL leave existing backups in Drive untouched.

#### Scenario: Off
- **WHEN** the user turns Drive backup off
- **THEN** no further backups run and the stored passphrase is gone

### Requirement: Changing the passphrase
Changing the backup passphrase SHALL apply to backups written afterwards, and SHALL take a backup immediately.

#### Scenario: New passphrase
- **WHEN** the user changes the backup passphrase
- **THEN** a backup is taken with the new passphrase right away
