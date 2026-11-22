package com.mandrecode.tempo.features.settings.presentation

import android.content.Context
import android.content.SharedPreferences
import com.google.common.truth.Truth.assertThat
import com.mandrecode.tempo.core.data.preferences.ThemePreferencesRepository
import com.mandrecode.tempo.core.data.preferences.VacationModePreferencesImpl
import com.mandrecode.tempo.core.domain.model.ThemeMode
import com.mandrecode.tempo.core.domain.model.VacationPeriod
import com.mandrecode.tempo.core.domain.repository.VacationModeRepository
import com.mandrecode.tempo.features.backup.domain.repository.BackupRepository
import com.mandrecode.tempo.features.backup.domain.usecase.ExportBackupUseCase
import com.mandrecode.tempo.features.backup.domain.usecase.ImportBackupUseCase
import com.mandrecode.tempo.features.focus.domain.repository.FocusSessionRepository
import com.mandrecode.tempo.features.tasks.domain.repository.CompletedTaskRetentionPreferences
import com.mandrecode.tempo.features.tasks.domain.repository.MissedReminderPreferences
import com.mandrecode.tempo.features.tasks.domain.scheduler.MissedReminderScheduler
import com.mandrecode.tempo.features.tasks.domain.usecase.ConfigureCompletedTaskRetentionUseCase
import com.mandrecode.tempo.features.widget.presentation.QuickAddTaskWidgetRefresher
import com.mandrecode.tempo.infrastructure.backup.BackupFileDataSource
import com.mandrecode.tempo.util.AppVersionInfo
import com.mandrecode.tempo.util.AppVersionProvider
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.time.Clock

/**
 * Vacation mode in Settings. Runs the ViewModel against the real preferences-backed repository
 * over an in-memory string, so the period semantics under test are the shipped ones rather than
 * a re-implementation in a fake.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelVacationModeTest {
    private lateinit var viewModel: SettingsViewModel
    private lateinit var vacationModeRepository: VacationModeRepository
    private var storedVacationPeriods: String? = null
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        storedVacationPeriods = null
        vacationModeRepository = createVacationModeRepository()
        viewModel = createViewModel()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `toggling vacation mode on starts a pause from today`() =
        runTest {
            advanceUntilIdle()

            viewModel.onEvent(SettingsContract.UiEvent.VacationModeToggled(true))
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertThat(state.vacationModeActive).isTrue()
            assertThat(state.vacationStartDate).isEqualTo(today())
            assertThat(state.vacationEndDate).isNull()
            assertThat(vacationModeRepository.periods.value)
                .containsExactly(VacationPeriod(today()))
        }

    @Test
    fun `toggling vacation mode off clears a pause started the same day`() =
        runTest {
            advanceUntilIdle()
            viewModel.onEvent(SettingsContract.UiEvent.VacationModeToggled(true))
            advanceUntilIdle()

            viewModel.onEvent(SettingsContract.UiEvent.VacationModeToggled(false))
            advanceUntilIdle()

            assertThat(viewModel.uiState.value.vacationModeActive).isFalse()
            assertThat(vacationModeRepository.periods.value).isEmpty()
        }

    @Test
    fun `setting an end date stores it and surfaces it in state`() =
        runTest {
            advanceUntilIdle()
            viewModel.onEvent(SettingsContract.UiEvent.VacationModeToggled(true))
            advanceUntilIdle()
            val end = today().plusDays(6)

            viewModel.onEvent(SettingsContract.UiEvent.VacationEndDateChanged(end))
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertThat(state.vacationModeActive).isTrue()
            assertThat(state.vacationEndDate).isEqualTo(end)
        }

    @Test
    fun `clearing the end date leaves the pause open-ended`() =
        runTest {
            advanceUntilIdle()
            viewModel.onEvent(SettingsContract.UiEvent.VacationModeToggled(true))
            viewModel.onEvent(SettingsContract.UiEvent.VacationEndDateChanged(today().plusDays(6)))
            advanceUntilIdle()

            viewModel.onEvent(SettingsContract.UiEvent.VacationEndDateChanged(null))
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertThat(state.vacationModeActive).isTrue()
            assertThat(state.vacationEndDate).isNull()
        }

    @Test
    fun `an end date before the period start is ignored`() =
        runTest {
            advanceUntilIdle()
            viewModel.onEvent(SettingsContract.UiEvent.VacationModeToggled(true))
            advanceUntilIdle()

            viewModel.onEvent(SettingsContract.UiEvent.VacationEndDateChanged(today().plusDays(-1)))
            advanceUntilIdle()

            assertThat(viewModel.uiState.value.vacationModeActive).isTrue()
            assertThat(viewModel.uiState.value.vacationEndDate).isNull()
        }

    @Test
    fun `a period whose planned end has passed reads as inactive`() =
        runTest {
            storedVacationPeriods = "2020-01-01..2020-01-10"
            vacationModeRepository = createVacationModeRepository()
            viewModel = createViewModel()
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertThat(state.vacationModeActive).isFalse()
            assertThat(state.vacationStartDate).isNull()
            assertThat(state.vacationEndDate).isNull()
        }

    private fun createViewModel(): SettingsViewModel {
        val themePreferences =
            mockk<ThemePreferencesRepository>(relaxed = true) {
                coEvery { getThemeMode() } returns flowOf(ThemeMode.SYSTEM)
                coEvery { getUseTempoColors() } returns flowOf(false)
            }
        val retentionPreferences =
            mockk<CompletedTaskRetentionPreferences>(relaxed = true) {
                every { isEnabled } returns MutableStateFlow(false)
                every { retentionDays } returns MutableStateFlow(30)
            }
        val missedReminderPreferences =
            mockk<MissedReminderPreferences>(relaxed = true) {
                every { isEnabled } returns MutableStateFlow(true)
                every { catchUpTime } returns MutableStateFlow(LocalTime(hour = 9, minute = 0))
            }
        return SettingsViewModel(
            themePreferences,
            mockk<AppVersionProvider> { every { getVersionInfo() } returns AppVersionInfo("1.0", 1) },
            retentionPreferences,
            mockk<ConfigureCompletedTaskRetentionUseCase>(relaxed = true),
            missedReminderPreferences,
            mockk<MissedReminderScheduler>(relaxed = true),
            SettingsBackupDelegate(
                mockk<ExportBackupUseCase>(relaxed = true),
                mockk<ImportBackupUseCase>(relaxed = true),
                mockk<BackupRepository>(relaxed = true),
                mockk<BackupFileDataSource>(relaxed = true),
            ),
            driveBackupDelegate(),
            vacationModeRepository,
            mockk<FocusSessionRepository>(relaxed = true) {
                every { defaultLengthMinutes } returns MutableStateFlow(25)
                every { breakLengthMinutes } returns MutableStateFlow(5)
            },
            mockk<QuickAddTaskWidgetRefresher>(relaxed = true),
        )
    }

    private fun createVacationModeRepository(): VacationModeRepository {
        val editor =
            mockk<SharedPreferences.Editor>(relaxed = true) {
                every { putString(any(), any()) } answers {
                    storedVacationPeriods = secondArg()
                    this@mockk
                }
            }
        val preferences =
            mockk<SharedPreferences> {
                every { getString(any(), any()) } answers { storedVacationPeriods ?: secondArg() }
                every { edit() } returns editor
            }
        val context =
            mockk<Context> {
                every { getSharedPreferences(any(), any()) } returns preferences
            }
        return VacationModePreferencesImpl(context)
    }

    private fun today(): LocalDate = Clock.System.todayIn(TimeZone.currentSystemDefault())

    private fun LocalDate.plusDays(days: Int): LocalDate = LocalDate.fromEpochDays(toEpochDays() + days)
}
