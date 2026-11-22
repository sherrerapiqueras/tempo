package com.mandrecode.tempo.core.domain.model

/**
 * Features that ship in the code but are switched per build, set in `app/build.gradle.kts`.
 *
 * @property driveBackupEnabled Google Drive backup and restore (#306, #307). Off in release builds
 * until the OAuth consent screen is in production (#565): before that, Google only lets listed
 * test users authorise the app, so the feature would fail for everyone else. On in debug builds.
 */
data class FeatureFlags(
    val driveBackupEnabled: Boolean,
)
