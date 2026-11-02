package com.mandrecode.tempo.core.ui.theme

import android.app.UiModeManager
import com.google.common.truth.Truth.assertThat
import com.mandrecode.tempo.core.domain.model.ThemeMode
import org.junit.Test

class ApplicationNightModeTest {
    @Test
    fun givenEachThemeMode_whenMapped_thenTheSystemGetsTheMatchingNightMode() {
        assertThat(ThemeMode.LIGHT.toUiModeManagerNightMode()).isEqualTo(UiModeManager.MODE_NIGHT_NO)
        assertThat(ThemeMode.DARK.toUiModeManagerNightMode()).isEqualTo(UiModeManager.MODE_NIGHT_YES)
        assertThat(ThemeMode.SYSTEM.toUiModeManagerNightMode()).isEqualTo(UiModeManager.MODE_NIGHT_AUTO)
    }
}
