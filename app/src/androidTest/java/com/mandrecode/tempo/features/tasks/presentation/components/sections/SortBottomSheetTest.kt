package com.mandrecode.tempo.features.tasks.presentation.components.sections

import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import com.mandrecode.tempo.core.ui.theme.TempoTheme
import com.mandrecode.tempo.features.tasks.presentation.model.SortOption
import org.junit.Rule
import org.junit.Test

class SortBottomSheetTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private fun label(option: SortOption) = InstrumentationRegistry.getInstrumentation().targetContext.getString(option.labelResId)

    @Test
    fun givenACurrentOption_thenOnlyThatRowIsSelected() {
        composeTestRule.setContent {
            TempoTheme {
                SortBottomSheet(currentSortOption = SortOption.BY_DATE, onDismiss = {}, onSelectSortOption = {})
            }
        }

        composeTestRule.onNodeWithText(label(SortOption.BY_DATE)).assertIsSelected()
        composeTestRule.onNodeWithText(label(SortOption.MANUAL)).assertIsNotSelected()
    }

    @Test
    fun whenARowIsTapped_thenTheSheetClosesWithThatOption() {
        var selected: SortOption? = null
        var dismissed = false
        composeTestRule.setContent {
            TempoTheme {
                SortBottomSheet(
                    currentSortOption = SortOption.MANUAL,
                    onDismiss = { dismissed = true },
                    onSelectSortOption = { selected = it },
                )
            }
        }

        composeTestRule.onNodeWithText(label(SortOption.BY_DATE)).performClick()
        composeTestRule.waitUntil(timeoutMillis = 5_000) { dismissed }

        assertThat(selected).isEqualTo(SortOption.BY_DATE)
    }
}
