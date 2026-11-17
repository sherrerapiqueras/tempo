package com.mandrecode.tempo.features.onboarding.presentation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import com.mandrecode.tempo.R
import com.mandrecode.tempo.core.ui.theme.TempoTheme
import org.junit.Rule
import org.junit.Test

class OnboardingContentTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun givenContentPage_whenRendered_thenSkipIsAvailable() {
        var uiState by mutableStateOf(OnboardingContract.UiState())
        composeTestRule.setContent {
            TempoTheme {
                OnboardingContent(
                    uiState = uiState,
                    onEvent = {},
                )
            }
        }

        repeat(OnboardingContract.PAGE_COUNT - 1) { page ->
            composeTestRule.runOnIdle {
                uiState = uiState.copy(currentPage = page)
            }

            composeTestRule.onNodeWithTag(OnboardingTestTags.SKIP).assertIsDisplayed().assertIsEnabled()
        }
    }

    @Test
    fun givenWelcomePage_whenRendered_thenSkipIsNotAvailable() {
        setContent(OnboardingContract.UiState(currentPage = OnboardingContract.PAGE_COUNT - 1))

        composeTestRule.onAllNodesWithTag(OnboardingTestTags.SKIP).assertCountEquals(0)
    }

    @Test
    fun givenWelcomePage_whenRestoreClicked_thenRestoreFromDriveIsRequested() {
        var emittedEvent: OnboardingContract.UiEvent? = null
        setContent(OnboardingContract.UiState(currentPage = OnboardingContract.Page.WELCOME), onEvent = { emittedEvent = it })

        composeTestRule.onNodeWithTag(OnboardingTestTags.RESTORE).assertIsDisplayed().performClick()

        assertThat(emittedEvent).isEqualTo(OnboardingContract.UiEvent.RestoreFromDriveClicked)
    }

    @Test
    fun givenFirstPage_whenForwardClicked_thenNextEventIsEmitted() {
        var emittedEvent: OnboardingContract.UiEvent? = null
        setContent(OnboardingContract.UiState(), onEvent = { emittedEvent = it })

        composeTestRule.onNodeWithTag(OnboardingTestTags.FORWARD).performClick()

        assertThat(emittedEvent).isEqualTo(OnboardingContract.UiEvent.NextClicked)
    }

    @Test
    fun givenAnyPage_whenRendered_thenProgressHasOneSegmentPerPage() {
        setContent(OnboardingContract.UiState(currentPage = 1))

        val segments =
            composeTestRule.onAllNodesWithTag(
                testTag = OnboardingTestTags.PROGRESS_SEGMENT,
                useUnmergedTree = true,
            )
        segments.assertCountEquals(OnboardingContract.PAGE_COUNT)
    }

    @Test
    fun givenAnyPage_whenRendered_thenProgressExposesItsCurrentStep() {
        val currentPage = OnboardingContract.Page.ROUTINES
        setContent(OnboardingContract.UiState(currentPage = currentPage))

        val rangeInfo =
            composeTestRule
                .onNodeWithTag(OnboardingTestTags.PROGRESS)
                .fetchSemanticsNode()
                .config[SemanticsProperties.ProgressBarRangeInfo]

        assertThat(rangeInfo.current).isEqualTo(currentPage.toFloat())
        assertThat(rangeInfo.range).isEqualTo(0f..(OnboardingContract.PAGE_COUNT - 1).toFloat())
        assertThat(rangeInfo.steps).isEqualTo(OnboardingContract.PAGE_COUNT - 2)
    }

    @Test
    fun givenAnyPage_whenRendered_thenProgressIsAbovePageContent() {
        setContent(OnboardingContract.UiState())

        val progressBottom =
            composeTestRule
                .onNodeWithTag(OnboardingTestTags.PROGRESS)
                .fetchSemanticsNode()
                .boundsInRoot.bottom
        val pageTop =
            composeTestRule
                .onNodeWithTag(OnboardingTestTags.PAGE)
                .fetchSemanticsNode()
                .boundsInRoot.top

        assertThat(progressBottom).isAtMost(pageTop)
    }

    @Test
    fun givenAppearancePage_whenDynamicColorsClicked_thenSelectionEventIsEmitted() {
        var emittedEvent: OnboardingContract.UiEvent? = null
        setContent(
            OnboardingContract.UiState(currentPage = OnboardingContract.Page.APPEARANCE),
            onEvent = { emittedEvent = it },
        )

        val dynamicLabel =
            InstrumentationRegistry.getInstrumentation().targetContext.getString(R.string.settings_color_scheme_dynamic)
        composeTestRule
            .onNode(hasText(dynamicLabel) and hasClickAction())
            .performSemanticsAction(SemanticsActions.OnClick)

        assertThat(emittedEvent).isEqualTo(OnboardingContract.UiEvent.UseTempoColorsToggled(false))
    }

    @Test
    fun givenAppearancePage_whenForwardClicked_thenNextEventIsEmitted() {
        var emittedEvent: OnboardingContract.UiEvent? = null
        setContent(
            OnboardingContract.UiState(currentPage = OnboardingContract.Page.APPEARANCE),
            onEvent = { emittedEvent = it },
        )

        composeTestRule.onNodeWithTag(OnboardingTestTags.FORWARD).performClick()

        assertThat(emittedEvent).isEqualTo(OnboardingContract.UiEvent.NextClicked)
    }

    @Test
    fun givenWelcomePage_whenForwardClicked_thenFinishEventIsEmitted() {
        var emittedEvent: OnboardingContract.UiEvent? = null
        setContent(
            OnboardingContract.UiState(currentPage = OnboardingContract.PAGE_COUNT - 1),
            onEvent = { emittedEvent = it },
        )

        composeTestRule.onNodeWithTag(OnboardingTestTags.FORWARD).performClick()

        assertThat(emittedEvent).isEqualTo(OnboardingContract.UiEvent.FinishClicked)
    }

    @Test
    fun givenLastPage_whenRendered_thenStartActionIsWiderThanBack() {
        setContent(OnboardingContract.UiState(currentPage = OnboardingContract.PAGE_COUNT - 1))

        val backWidth =
            composeTestRule
                .onNodeWithTag(OnboardingTestTags.BACK)
                .fetchSemanticsNode()
                .boundsInRoot.width
        val startWidth =
            composeTestRule
                .onNodeWithTag(OnboardingTestTags.FORWARD)
                .fetchSemanticsNode()
                .boundsInRoot.width

        assertThat(startWidth).isGreaterThan(backWidth)
    }

    @Test
    fun givenLastPage_whenRendered_thenCenteredTempoWelcomeIsDisplayed() {
        setContent(OnboardingContract.UiState(currentPage = OnboardingContract.PAGE_COUNT - 1))

        val appName = InstrumentationRegistry.getInstrumentation().targetContext.getString(R.string.app_name)
        composeTestRule.onNodeWithTag(OnboardingTestTags.WELCOME_LOGO).assertIsDisplayed()
        composeTestRule.onNodeWithText(appName).assertIsDisplayed()
        composeTestRule.onAllNodesWithContentDescription(appName).assertCountEquals(0)
    }

    private fun setContent(
        uiState: OnboardingContract.UiState,
        onEvent: (OnboardingContract.UiEvent) -> Unit = {},
    ) {
        composeTestRule.setContent {
            TempoTheme {
                OnboardingContent(
                    uiState = uiState,
                    onEvent = onEvent,
                )
            }
        }
    }
}
