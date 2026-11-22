package com.mandrecode.tempo.features.onboarding.presentation

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.mandrecode.tempo.core.data.preferences.OnboardingPreferencesRepository
import com.mandrecode.tempo.core.data.preferences.ThemePreferencesRepository
import com.mandrecode.tempo.core.domain.model.FeatureFlag
import com.mandrecode.tempo.core.domain.model.FeatureFlags
import com.mandrecode.tempo.core.domain.model.ThemeMode
import com.mandrecode.tempo.features.backup.domain.model.ImportOutcome
import com.mandrecode.tempo.features.backup.domain.model.ImportSummary
import com.mandrecode.tempo.features.backup.domain.repository.BackupRepository
import com.mandrecode.tempo.features.backup.domain.usecase.FakeCloudStore
import com.mandrecode.tempo.features.backup.domain.usecase.ImportBackupUseCase
import com.mandrecode.tempo.features.backup.presentation.DriveRestoreEvent
import com.mandrecode.tempo.features.backup.presentation.DriveRestoreFlow
import com.mandrecode.tempo.features.backup.presentation.DriveRestoreStep
import com.mandrecode.tempo.features.backup.presentation.driveRestoreFlow
import com.mandrecode.tempo.infrastructure.backup.drive.GoogleDriveAuthorization
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
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

    @Test
    fun givenARestoreFromDrive_whenItsLastDialogCloses_thenOnboardingIsComplete() =
        runTest {
            val viewModel = createViewModel(restoreFlowWithOneBackup())
            restoreTheBackup(viewModel)
            assertThat(viewModel.uiState.value.driveRestore).isInstanceOf(DriveRestoreStep.Restored::class.java)

            viewModel.uiEffect.test {
                viewModel.onEvent(OnboardingContract.UiEvent.DriveRestore(DriveRestoreEvent.KeepBackingUpChosen(false)))
                advanceUntilIdle()

                assertThat(awaitItem()).isEqualTo(OnboardingContract.UiEffect.Exit)
                cancelAndIgnoreRemainingEvents()
            }
            verify { onboardingPreferencesRepository.setCompleted() }
        }

    @Test
    fun givenDriveBackupOffInThisBuild_thenNoRestoreIsOfferedOrStarted() =
        runTest {
            val viewModel = createViewModel(restoreFlowWithOneBackup(), driveBackupEnabled = false)

            assertThat(viewModel.uiState.value.isDriveRestoreAvailable).isFalse()
            viewModel.onEvent(OnboardingContract.UiEvent.RestoreFromDriveClicked)
            advanceUntilIdle()

            assertThat(viewModel.uiState.value.driveRestore).isNull()
        }

    @Test
    fun givenARestoreOffer_whenDismissed_thenOnboardingCarriesOn() =
        runTest {
            val viewModel = createViewModel(restoreFlowWithOneBackup())
            viewModel.onEvent(OnboardingContract.UiEvent.RestoreFromDriveClicked)
            advanceUntilIdle()

            viewModel.onEvent(OnboardingContract.UiEvent.DriveRestore(DriveRestoreEvent.Dismissed))
            advanceUntilIdle()

            assertThat(viewModel.uiState.value.driveRestore).isNull()
            verify(exactly = 0) { onboardingPreferencesRepository.setCompleted() }
        }

    private suspend fun restoreFlowWithOneBackup(): DriveRestoreFlow {
        val cloud = FakeCloudStore()
        cloud.upload("t", "tempo-backup-42.tempo", "encrypted")
        val importBackup = mockk<ImportBackupUseCase>()
        coEvery { importBackup(any(), any(), any()) } returns ImportOutcome.Success(ImportSummary())
        return driveRestoreFlow(
            consent = mockk<GoogleDriveAuthorization> { coEvery { consentIntent() } returns null },
            cloud = cloud,
            importBackup = importBackup,
            repository = mockk<BackupRepository> { coEvery { hasUserData() } returns false },
        )
    }

    private fun TestScope.restoreTheBackup(viewModel: OnboardingViewModel) {
        viewModel.onEvent(OnboardingContract.UiEvent.RestoreFromDriveClicked)
        advanceUntilIdle()
        viewModel.onEvent(OnboardingContract.UiEvent.DriveRestore(DriveRestoreEvent.RestoreClicked))
        advanceUntilIdle()
        viewModel.onEvent(
            OnboardingContract.UiEvent.DriveRestore(DriveRestoreEvent.PassphraseEntered("pw".toCharArray())),
        )
        advanceUntilIdle()
    }

    private fun createViewModel(
        driveRestore: DriveRestoreFlow = driveRestoreFlow(),
        driveBackupEnabled: Boolean = true,
    ): OnboardingViewModel =
        OnboardingViewModel(
            themePreferencesRepository = themePreferencesRepository,
            onboardingPreferencesRepository = onboardingPreferencesRepository,
            driveRestore = driveRestore,
            featureFlags = FeatureFlags.of(FeatureFlag.DRIVE_BACKUP to driveBackupEnabled),
        )
}
