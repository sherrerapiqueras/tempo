package com.mandrecode.tempo.core.ui.adaptive

import androidx.compose.ui.unit.dp
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AdaptiveMarginTest {
    @Test
    fun `a compact window gets the 16dp margin`() {
        assertThat(adaptiveHorizontalMargin(WindowWidthClass.COMPACT)).isEqualTo(16.dp)
    }

    @Test
    fun `every larger window gets the 24dp margin`() {
        WindowWidthClass.entries.filter { it != WindowWidthClass.COMPACT }.forEach { widthClass ->
            assertThat(adaptiveHorizontalMargin(widthClass)).isEqualTo(24.dp)
        }
    }
}
