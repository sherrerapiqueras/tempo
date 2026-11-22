## ADDED Requirements

### Requirement: Drive backup can be withheld from a build
Google Drive backup and restore SHALL be offered only in builds where the Drive backup feature is enabled. In a build where it is disabled, the app SHALL NOT show the Google Drive entry in Settings, SHALL NOT open the Drive backup page on request, SHALL NOT offer a restore from Drive during onboarding, and SHALL NOT start any Drive backup work. Release builds SHALL keep it disabled until the OAuth consent screen is in production.

#### Scenario: Release build before the consent screen is published
- **WHEN** a user of a release build with the feature disabled opens Settings › Backup
- **THEN** only file export and import are listed

#### Scenario: Onboarding in a build without Drive backup
- **WHEN** a new user reaches the welcome page in a build with the feature disabled
- **THEN** no "restore from Google Drive" offer is shown

#### Scenario: Debug build
- **WHEN** a debug build is used
- **THEN** Drive backup and restore are offered as before
