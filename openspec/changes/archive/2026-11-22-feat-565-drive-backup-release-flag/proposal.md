# Proposal

Related issue: [#565 — get Tempo ready for its first production release](https://github.com/sherrerapiqueras/tempo/issues/565)

## Why

Google Drive backup and restore are built (#306, #307), but the app's OAuth consent screen is still in testing: only listed test users can authorise it. A production build released before the consent screen is published would show Drive backup to everyone and fail at authorisation for almost all of them. The rest of the pending work should be able to ship without waiting for that.

## What Changes

- A build-time feature flag, `DRIVE_BACKUP_ENABLED`, on in debug builds and off in release builds.
- With it off: Settings › Backup shows no Google Drive entry (and a request to open its page lands on the main page), the onboarding welcome page offers no restore from Drive, and nothing of Drive backup starts (no status refresh, no scheduling).
- Turning it on is a one-line change in `app/build.gradle.kts`, listed as a step in #565 after the consent screen is in production.

**Non-goals**

- Remote or runtime flags: the app has no remote config, and the switch only needs to happen once.
- Removing or changing any Drive code; with the flag on, behaviour is exactly as before.
- File (SAF) export and import, which stay available in every build.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `drive-backup`: offered only in builds where the feature is enabled.

## Impact

- **Build:** a `buildConfigField` per build type.
- **Domain:** `FeatureFlags` model, provided from `BuildConfig` in `InfrastructureModule`.
- **UI:** `SettingsViewModel`/`SettingsContract`/`BackupSection`/`SettingsScaffold`, `OnboardingViewModel`/`OnboardingContract`/`OnboardingContent`.
- Debug builds, their screenshot references and instrumented tests are unchanged (the flag is on there).
