# Proposal

Follow-up to #579 (Drive backup hidden in release builds, #565).

## Why

#579 introduced a single build-time flag for Drive backup. More will come (anything that ships before it may be used), and without a convention each one would be wired differently, be hard to try in its other state, and linger after launch.

## What Changes

- One registry: `FeatureFlag` (domain enum: each flag with its activation issue and description) and the `featureFlags` map in `app/build.gradle.kts` (debug and release values), generating `BuildConfig.FLAG_<NAME>`.
- Consumers read `FeatureFlags.isEnabled(flag)`; tests use `FeatureFlags.of(...)`.
- A Gradle override for one build: `-Ptempo.flag.<NAME>=true|false`.
- A debug-only "Tempo flags" screen (its own launcher entry, `src/debug`) to override any flag, applied on restart; release builds ignore overrides and don't contain the screen.
- AGENTS.md rules: how to declare, read, try and retire a flag; every flag needs an activation issue and is deleted with its "off" code once on in release.

**Non-goals**

- Remote config or any network-backed flags (Tempo has no server of its own).
- Gradual rollouts: Play's staged rollouts and tracks cover those.

## Capabilities

### New Capabilities

- `feature-flags`: how build-time flags are declared, resolved, overridden and retired.

### Modified Capabilities

None. `DRIVE_BACKUP` keeps the behaviour #579 specified.

## Impact

- **Build:** `featureFlags` registry and `tempo.flag.*` properties in `app/build.gradle.kts`; Kover excludes the debug-only screen.
- **Domain:** `FeatureFlag`, `FeatureFlags` (replaces #579's `FeatureFlags` data class).
- **Data:** `BuildFeatureFlags` (+ `FeatureFlagOverrides`), bound in `PreferencesRepositoryModule`.
- **Debug source set:** `FeatureFlagsActivity`, its manifest entry and strings.
