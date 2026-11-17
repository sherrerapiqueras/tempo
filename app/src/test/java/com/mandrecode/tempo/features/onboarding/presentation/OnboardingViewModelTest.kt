package com.mandrecode.tempo.features.onboarding.presentation

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.mandrecode.tempo.core.data.preferences.OnboardingPreferencesRepository
import com.mandrecode.tempo.core.data.preferences.ThemePreferencesRepository
import com.mandrecode.tempo.core.domain.model.ThemeMode
import com.mandrecode.tempo.features.backup.presentation.driveRestoreFlow
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class OnboardingViewModelTest {
    private val testDispatcher = StandardTestDispatcher()
    private lateinit var themePreferencesRepository: ThemePreferencesRepository
    private lateinit var onboardingPreferencesRepository: OnboardingPreferencesRepository
    private lateinit var useTempoColors: MutableStateFlow<Boolean>
    private lateinit var themeMode: MutableStateFlow<ThemeMode>

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        themePreferencesRepository = mockk(relaxed = true)
        onboardingPreferencesRepository = mockk(relaxed = true)
        useTempoColors = MutableStateFlow(false)
        themeMode = MutableStateFlow(ThemeMode.SYSTEM)

        every { themePreferencesRepository.getUseTempoColors() } returns useTempoColors
        every { themePreferencesRepository.getThemeMode() } returns themeMode
        every { onboardingPreferencesRepository.isCompleted } returns MutableStateFlow(false)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun givenAppearancePage_whenUseTempoColorsToggled_thenSettingsRepositoryIsUpdated() {
        val viewModel = createViewModel()

        viewModel.onEvent(OnboardingContract.UiEvent.UseTempoColorsToggled(false))

        verify { themePreferencesRepository.setUseTempoColors(false) }
    }

    @Test
    fun givenAppearancePage_whenThemeModeSelected_thenSettingsRepositoryIsUpdated() {
        val viewModel = createViewModel()

        viewModel.onEvent(OnboardingContract.UiEvent.ThemeModeSelected(ThemeMode.DARK))

        verify { themePreferencesRepository.setThemeMode(ThemeMode.DARK) }
    }

    @Test
    fun givenFirstPage_whenNextAndBackClicked_thenPageChangesInOrder() =
        runTest {
            val viewModel = createViewModel()

            viewModel.onEvent(OnboardingContract.UiEvent.NextClicked)
            assertThat(viewModel.uiState.value.currentPage).isEqualTo(1)

            viewModel.onEvent(OnboardingContract.UiEvent.BackClicked)
            assertThat(viewModel.uiState.value.currentPage).isEqualTo(0)
        }

    @Test
    fun givenPageBoundary_whenNavigatingPastIt_thenPageRemainsInBounds() =
        runTest {
            val viewModel = createViewModel()

            viewModel.onEvent(OnboardingContract.UiEvent.BackClicked)
            assertThat(viewModel.uiState.value.currentPage).isEqualTo(0)

            repeat(OnboardingContract.PAGE_COUNT + 1) {
                viewModel.onEvent(OnboardingContract.UiEvent.NextClicked)
            }
            assertThat(viewModel.uiState.value.currentPage).isEqualTo(OnboardingContract.PAGE_COUNT - 1)
        }

    @Test
    fun givenOnboarding_whenSkipped_thenCompletionAndExitAreEmitted() =
        runTest {
            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.uiEffect.test {
                viewModel.onEvent(OnboardingContract.UiEvent.SkipClicked)
                advanceUntilIdle()

                assertThat(awaitItem())
                    .isEqualTo(OnboardingContract.UiEffect.Exit)
                cancelAndIgnoreRemainingEvents()
            }
            verify { onboardingPreferencesRepository.setCompleted() }
        }

    private fun createViewModel(): OnboardingViewModel =
        OnboardingViewModel(
            themePreferencesRepository = themePreferencesRepository,
            onboardingPreferencesRepository = onboardingPreferencesRepository,
            driveRestore = driveRestoreFlow(),
        )
}
