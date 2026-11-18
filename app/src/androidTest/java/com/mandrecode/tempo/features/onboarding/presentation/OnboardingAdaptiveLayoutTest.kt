package com.mandrecode.tempo.features.onboarding.presentation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import com.google.common.truth.Truth.assertThat
import com.mandrecode.tempo.core.ui.theme.TempoTheme
import org.junit.Rule
import org.junit.Test

class OnboardingAdaptiveLayoutTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun givenCompactLayout_whenRendered_thenUsesSinglePane() {
        setContent(OnboardingLayout(isExpanded = false, isShort = false))

        composeTestRule.onNodeWithTag(OnboardingTestTags.SINGLE_PANE).assertIsDisplayed()
    }

    @Test
    fun givenExpandedLayout_whenRendered_thenUsesTwoPanes() {
        setContent(OnboardingLayout(isExpanded = true, isShort = false))

        composeTestRule.onNodeWithTag(OnboardingTestTags.TWO_PANE).assertIsDisplayed()
    }

    @Test
    fun givenShortWindow_whenWelcomeRendered_thenRestoreOfferCanBeReached() {
        var restoreClicked = false
        composeTestRule.setContent {
            TempoTheme {
                // About what a phone in landscape leaves for the page between the progress bar and footer.
                Box(modifier = Modifier.height(230.dp)) {
                    OnboardingWelcomePage(
                        layout = OnboardingLayout(isExpanded = false, isShort = true),
                        onRestoreClick = { restoreClicked = true },
                    )
                }
            }
        }

        composeTestRule
            .onNodeWithTag(OnboardingTestTags.RESTORE)
            .performScrollTo()
            .assertIsDisplayed()
            .performClick()

        assertThat(restoreClicked).isTrue()
    }

    private fun setContent(layout: OnboardingLayout) {
        composeTestRule.setContent {
            TempoTheme {
                AdaptiveOnboardingPage(
                    layout = layout,
                    intro = {},
                    body = {},
                )
            }
        }
    }
}
