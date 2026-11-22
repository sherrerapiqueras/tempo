# Tasks

- [x] 1.1 `FeatureFlag` enum (activation issue, description) and `FeatureFlags` interface with `of(...)` for tests.
- [x] 1.2 `featureFlags` registry in `app/build.gradle.kts` generating `BuildConfig.FLAG_*`, with `-Ptempo.flag.<NAME>` overrides.
- [x] 1.3 `BuildFeatureFlags` / `FeatureFlagOverrides`, overrides honoured in debug only; bound in `PreferencesRepositoryModule`.
- [x] 1.4 Settings and onboarding read `isEnabled(FeatureFlag.DRIVE_BACKUP)`.
- [x] 1.5 Debug-only Tempo flags screen with restart; Kover exclusion.
- [x] 1.6 Tests for resolution and the fixed-value helper; view model tests updated.
- [x] 1.7 AGENTS.md "Feature flags" section.
- [x] 1.8 Verified: Gradle override flips `BuildConfig`; release manifest has no flags screen; on the emulator, turning `DRIVE_BACKUP` off and restarting hides Drive in Settings. Gates: ktlint, detekt, lint, unit tests, Kover, release build.
