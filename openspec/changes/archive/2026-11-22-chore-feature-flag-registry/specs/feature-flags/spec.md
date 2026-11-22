## ADDED Requirements

### Requirement: Flags are declared once, with values per build
Every feature flag SHALL be declared in the `FeatureFlag` registry with the issue that turns it on, and SHALL have a debug and a release value in the build's flag registry. Code SHALL read flags through `FeatureFlags`, not through build constants.

#### Scenario: A flag missing from one side
- **WHEN** a flag is added to `FeatureFlag` but not to the build registry
- **THEN** the app does not compile

### Requirement: Flags can be tried in their other state
A developer SHALL be able to override any flag for a single build with a Gradle property, and in a debug build at runtime from a debug-only screen; runtime overrides SHALL apply from the next app start. A release build SHALL ignore runtime overrides and SHALL NOT contain the debug screen.

#### Scenario: Release behaviour in a debug build
- **WHEN** a developer turns `DRIVE_BACKUP` off on the Tempo flags screen and restarts Tempo
- **THEN** Settings › Backup shows only file import and export

#### Scenario: Release build with an override file present
- **WHEN** a release build finds a stored override
- **THEN** every flag keeps the value the build was made with

### Requirement: Flags are retired after launch
Once a flag is on in release builds for good, the flag and the code behind its "off" side SHALL be removed in the change that closes its activation issue.

#### Scenario: Drive backup goes live
- **WHEN** #565 turns `DRIVE_BACKUP` on in release
- **THEN** the same change may instead delete the flag, the hidden-entry branches in Settings and onboarding, and its registry entries
