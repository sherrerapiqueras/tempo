package com.mandrecode.tempo.core.data.preferences

import com.google.common.truth.Truth.assertThat
import com.mandrecode.tempo.core.domain.model.FeatureFlag
import com.mandrecode.tempo.core.domain.model.FeatureFlags
import org.junit.Test

class BuildFeatureFlagsTest {
    @Test
    fun `an override wins where overrides are allowed`() {
        assertThat(resolveFeatureFlag(buildValue = false, override = true, overridesAllowed = true)).isTrue()
        assertThat(resolveFeatureFlag(buildValue = true, override = false, overridesAllowed = true)).isFalse()
    }

    @Test
    fun `a release build ignores overrides`() {
        assertThat(resolveFeatureFlag(buildValue = false, override = true, overridesAllowed = false)).isFalse()
    }

    @Test
    fun `without an override the build value stands`() {
        assertThat(resolveFeatureFlag(buildValue = true, override = null, overridesAllowed = true)).isTrue()
        assertThat(resolveFeatureFlag(buildValue = false, override = null, overridesAllowed = true)).isFalse()
    }

    @Test
    fun `fixed flags report the listed values and treat the rest as off`() {
        assertThat(FeatureFlags.of(FeatureFlag.DRIVE_BACKUP to true).isEnabled(FeatureFlag.DRIVE_BACKUP)).isTrue()
        assertThat(FeatureFlags.of().isEnabled(FeatureFlag.DRIVE_BACKUP)).isFalse()
    }

    @Test
    fun `every flag names the issue that turns it on`() {
        FeatureFlag.entries.forEach { assertThat(it.activationIssue).isGreaterThan(0) }
    }
}
