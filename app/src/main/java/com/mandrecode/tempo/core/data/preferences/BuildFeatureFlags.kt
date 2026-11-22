package com.mandrecode.tempo.core.data.preferences

import android.content.Context
import androidx.core.content.edit
import com.mandrecode.tempo.BuildConfig
import com.mandrecode.tempo.core.domain.model.FeatureFlag
import com.mandrecode.tempo.core.domain.model.FeatureFlags
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [FeatureFlags] as this build sets them, with overrides from the debug flags screen.
 *
 * Overrides only count in debug builds: a release build always has the values it was built with,
 * whatever a preferences file says.
 */
@Singleton
class BuildFeatureFlags
    @Inject
    constructor(
        @ApplicationContext context: Context,
    ) : FeatureFlags,
        FeatureFlagOverrides {
        private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

        override fun isEnabled(flag: FeatureFlag): Boolean =
            resolveFeatureFlag(
                buildValue = buildValue(flag),
                override = override(flag),
                overridesAllowed = BuildConfig.DEBUG,
            )

        override fun buildValue(flag: FeatureFlag): Boolean =
            when (flag) {
                FeatureFlag.DRIVE_BACKUP -> BuildConfig.FLAG_DRIVE_BACKUP
            }

        override fun override(flag: FeatureFlag): Boolean? {
            val key = flag.name
            return if (prefs.contains(key)) prefs.getBoolean(key, false) else null
        }

        override fun setOverride(
            flag: FeatureFlag,
            value: Boolean?,
        ) {
            prefs.edit(commit = true) { if (value == null) remove(flag.name) else putBoolean(flag.name, value) }
        }

        private companion object {
            const val PREFS = "feature_flag_overrides"
        }
    }

/** Debug-only overrides of [FeatureFlags], for the debug flags screen. */
interface FeatureFlagOverrides {
    /** The value the build was made with. */
    fun buildValue(flag: FeatureFlag): Boolean

    /** The override in force, or null when the flag follows its build value. */
    fun override(flag: FeatureFlag): Boolean?

    /** Overrides [flag], or clears its override when [value] is null. Read at the next app start. */
    fun setOverride(
        flag: FeatureFlag,
        value: Boolean?,
    )
}

/** A flag's value: its override where overrides are allowed and one is set, its build value otherwise. */
internal fun resolveFeatureFlag(
    buildValue: Boolean,
    override: Boolean?,
    overridesAllowed: Boolean,
): Boolean = if (overridesAllowed && override != null) override else buildValue
