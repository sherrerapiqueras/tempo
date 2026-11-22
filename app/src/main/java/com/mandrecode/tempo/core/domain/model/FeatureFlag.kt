package com.mandrecode.tempo.core.domain.model

/**
 * Features that ship in the code but are switched per build (`featureFlags` in
 * `app/build.gradle.kts`, which sets each one's debug and release value).
 *
 * Every flag names the issue that turns it on for good. Once it is on in release, the flag and the
 * code behind its "off" side are deleted (see AGENTS.md, "Feature flags").
 */
enum class FeatureFlag(
    /** The issue that turns this flag on in release builds. */
    val activationIssue: Int,
    /** What the flag gates, for the debug flags screen. */
    val description: String,
) {
    /**
     * Google Drive backup and restore (#306, #307). Off in release until the OAuth consent screen
     * is in production: until then only listed test users can authorise the app.
     */
    DRIVE_BACKUP(
        activationIssue = 565,
        description = "Google Drive backup and restore",
    ),
}

/** Whether each [FeatureFlag] is on in this build. */
fun interface FeatureFlags {
    fun isEnabled(flag: FeatureFlag): Boolean

    companion object {
        /** Fixed values, for tests and previews; flags not listed are off. */
        fun of(vararg enabled: Pair<FeatureFlag, Boolean>): FeatureFlags {
            val values = enabled.toMap()
            return FeatureFlags { flag -> values[flag] ?: false }
        }
    }
}
