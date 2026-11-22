package com.mandrecode.tempo.features.settings.presentation

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import com.mandrecode.tempo.R
import com.mandrecode.tempo.core.domain.model.ThemeMode
import com.mandrecode.tempo.core.ui.theme.TempoTheme
import org.junit.Rule
import org.junit.Test

class SettingsContentTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private fun localizedString(id: Int): String = InstrumentationRegistry.getInstrumentation().targetContext.getString(id)

    @Test
    fun displaysAppVersion() {
        composeTestRule.setContent {
            TempoTheme {
                SettingsContent(
                    uiState = SettingsContract.UiState(appVersion = "1.0.0"),
                    onEvent = {},
                    onOnboardingClick = {},
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("1.0.0", substring = true).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun displaysThemeModes() {
        val themeText = localizedString(R.string.theme)

        composeTestRule.setContent {
            TempoTheme {
                SettingsContent(
                    uiState = SettingsContract.UiState(),
                    onEvent = {},
                    onOnboardingClick = {},
                )
            }
        }

        composeTestRule.waitForIdle()
        // Theme section should be visible
        composeTestRule
            .onNodeWithText(themeText, ignoreCase = true)
            .assertIsDisplayed()
    }

    @Test
    fun themeModeSelectionTriggersEvent() {
        val darkText = localizedString(R.string.theme_dark)
        var selectedMode: ThemeMode? = null

        composeTestRule.setContent {
            TempoTheme {
                SettingsContent(
                    uiState = SettingsContract.UiState(selectedThemeMode = ThemeMode.SYSTEM),
                    onEvent = { event ->
                        if (event is SettingsContract.UiEvent.ThemeModeSelected) {
                            selectedMode = event.mode
                        }
                    },
                    onOnboardingClick = {},
                )
            }
        }

        composeTestRule.waitForIdle()
        // Try clicking the dark mode option
        composeTestRule
            .onNodeWithText(darkText, substring = true, ignoreCase = true)
            .performClick()

        composeTestRule.waitForIdle()
        assertThat(selectedMode).isEqualTo(ThemeMode.DARK)
    }

    @Test
    fun onboardingSelectionTriggersCallback() {
        var onboardingClicked = false
        val viewOnboardingText = localizedString(R.string.view_onboarding)

        composeTestRule.setContent {
            TempoTheme {
                SettingsContent(
                    uiState = SettingsContract.UiState(),
                    onEvent = {},
                    onOnboardingClick = { onboardingClicked = true },
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule
            .onNodeWithText(viewOnboardingText, ignoreCase = true)
            .performScrollTo()
            .performClick()

        composeTestRule.waitForIdle()
        assertThat(onboardingClicked).isTrue()
    }

    @Test
    fun displaysSourceCodeEntryInAboutSection() {
        val sourceCodeText = localizedString(R.string.source_code)
        val sourceCodeDescription = localizedString(R.string.source_code_description)

        composeTestRule.setContent {
            TempoTheme {
                SettingsContent(
                    uiState = SettingsContract.UiState(appVersion = "1.0.0"),
                    onEvent = {},
                    onOnboardingClick = {},
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule
            .onNodeWithText(sourceCodeText, ignoreCase = true)
            .performScrollTo()
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithText(sourceCodeDescription, ignoreCase = true)
            .assertIsDisplayed()
    }

    @Test
    fun displaysPrivacyPolicyEntryInAboutSection() {
        val privacyPolicyText = localizedString(R.string.privacy_policy)
        val privacyPolicyDescription = localizedString(R.string.privacy_policy_description)

        composeTestRule.setContent {
            TempoTheme {
                SettingsContent(
                    uiState = SettingsContract.UiState(appVersion = "1.0.0"),
                    onEvent = {},
                    onOnboardingClick = {},
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule
            .onNodeWithText(privacyPolicyText, ignoreCase = true)
            .performScrollTo()
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithText(privacyPolicyDescription, ignoreCase = true)
            .assertIsDisplayed()
    }

    @Test
    fun retentionControlsAreHiddenWhenAutomaticRemovalIsDisabled() {
        val removeAfterText = localizedString(R.string.settings_remove_completed_after)

        composeTestRule.setContent {
            TempoTheme {
                SettingsContent(
                    uiState = SettingsContract.UiState(autoRemoveCompletedTasksEnabled = false),
                    onEvent = {},
                    onOnboardingClick = {},
                )
            }
        }

        composeTestRule.onNodeWithText(removeAfterText).assertDoesNotExist()
    }

    @Test
    fun incrementRetentionEmitsNextPreset() {
        val increaseDescription = localizedString(R.string.settings_increase_retention_days)
        var selectedDays: Int? = null

        composeTestRule.setContent {
            TempoTheme {
                SettingsContent(
                    uiState =
                        SettingsContract.UiState(
                            autoRemoveCompletedTasksEnabled = true,
                            completedTaskRetentionDays = 30,
                        ),
                    onEvent = { event ->
                        if (event is SettingsContract.UiEvent.CompletedTaskRetentionDaysChanged) {
                            selectedDays = event.days
                        }
                    },
                    onOnboardingClick = {},
                )
            }
        }

        composeTestRule
            .onNodeWithContentDescription(increaseDescription)
            .performScrollTo()
            .performClick()

        assertThat(selectedDays).isEqualTo(45)
    }
}
