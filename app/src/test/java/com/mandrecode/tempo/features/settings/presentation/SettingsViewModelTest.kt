package com.mandrecode.tempo.features.settings.presentation

import android.net.Uri
import android.util.Log
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.mandrecode.tempo.core.data.preferences.ThemePreferencesRepository
import com.mandrecode.tempo.core.domain.model.ThemeMode
import com.mandrecode.tempo.core.domain.repository.VacationModeRepository
import com.mandrecode.tempo.features.backup.domain.model.ImportMode
import com.mandrecode.tempo.features.backup.domain.model.ImportOutcome
import com.mandrecode.tempo.features.backup.domain.model.ImportSummary
import com.mandrecode.tempo.features.backup.domain.repository.BackupRepository
import com.mandrecode.tempo.features.backup.domain.usecase.ExportBackupUseCase
import com.mandrecode.tempo.features.backup.domain.usecase.ImportBackupUseCase
import com.mandrecode.tempo.features.focus.domain.repository.FocusSessionRepository
import com.mandrecode.tempo.features.tasks.domain.repository.CompletedTaskRetentionPreferences
import com.mandrecode.tempo.features.tasks.domain.repository.MissedReminderPreferences
import com.mandrecode.tempo.features.tasks.domain.scheduler.MissedReminderScheduler
import com.mandrecode.tempo.features.tasks.domain.usecase.ConfigureCompletedTaskRetentionUseCase
import com.mandrecode.tempo.features.widget.presentation.TodayWidgetRefresher
import com.mandrecode.tempo.infrastructure.backup.BackupFileDataSource
import com.mandrecode.tempo.util.AppVersionInfo
import com.mandrecode.tempo.util.AppVersionProvider
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import io.mockk.verifyOrder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.LocalTime
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {
    private val focusSessionRepository =
        mockk<FocusSessionRepository>(relaxed = true) {
            // A relaxed mock returns a mock StateFlow, which never emits — the ViewModel collects
            // this on init, so it has to be a real flow.
            every { defaultLengthMinutes } returns MutableStateFlow(25)
            every { breakLengthMinutes } returns MutableStateFlow(5)
        }
    private lateinit var viewModel: SettingsViewModel
    private lateinit var themePreferencesRepository: ThemePreferencesRepository
    private lateinit var appVersionProvider: AppVersionProvider
    private lateinit var completedTaskRetentionPreferences: CompletedTaskRetentionPreferences
    private lateinit var configureCompletedTaskRetention: ConfigureCompletedTaskRetentionUseCase
    private lateinit var missedReminderPreferences: MissedReminderPreferences
    private lateinit var missedReminderScheduler: MissedReminderScheduler
    private lateinit var exportBackup: ExportBackupUseCase
    private lateinit var importBackup: ImportBackupUseCase
    private lateinit var backupRepository: BackupRepository
    private lateinit var backupFileDataSource: BackupFileDataSource
    private lateinit var todayWidgetRefresher: TodayWidgetRefresher
    private lateinit var vacationModeRepository: VacationModeRepository
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        mockkStatic(Log::class)
        every { Log.e(any(), any(), any()) } returns 0
        themePreferencesRepository = mockk(relaxed = true)
        appVersionProvider =
            mockk {
                every { getVersionInfo() } returns AppVersionInfo("1.0", 1)
            }
        completedTaskRetentionPreferences = mockk(relaxed = true)
        configureCompletedTaskRetention = mockk(relaxed = true)
        missedReminderPreferences = mockk(relaxed = true)
        missedReminderScheduler = mockk(relaxed = true)
        exportBackup = mockk(relaxed = true)
        importBackup = mockk(relaxed = true)
        backupRepository = mockk(relaxed = true)
        backupFileDataSource = mockk(relaxed = true)
        todayWidgetRefresher = mockk(relaxed = true)
        vacationModeRepository =
            mockk(relaxed = true) {
                every { periods } returns MutableStateFlow(emptyList())
            }

        coEvery { themePreferencesRepository.getThemeMode() } returns flowOf(ThemeMode.SYSTEM)
        coEvery { themePreferencesRepository.getUseTempoColors() } returns flowOf(false)
        every { completedTaskRetentionPreferences.isEnabled } returns MutableStateFlow(false)
        every { completedTaskRetentionPreferences.retentionDays } returns MutableStateFlow(30)
        every { missedReminderPreferences.isEnabled } returns MutableStateFlow(true)
        every { missedReminderPreferences.catchUpTime } returns MutableStateFlow(LocalTime(hour = 9, minute = 0))

        viewModel = createViewModel()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkStatic(Log::class)
    }

    @Test
    fun `initialState has correct default values`() =
        runTest {
            advanceUntilIdle()

            assertThat(viewModel.uiState.value.selectedThemeMode).isEqualTo(ThemeMode.SYSTEM)
        }

    @Test
    fun `themeModeSelected calls repository`() =
        runTest {
            advanceUntilIdle()
            viewModel.onEvent(SettingsContract.UiEvent.ThemeModeSelected(ThemeMode.DARK))
            coVerify { themePreferencesRepository.setThemeMode(ThemeMode.DARK) }
        }

    @Test
    fun `tempoColorsToggled calls repository`() =
        runTest {
            advanceUntilIdle()
            viewModel.onEvent(SettingsContract.UiEvent.TempoColorsToggled(true))
            advanceUntilIdle()
            coVerify { themePreferencesRepository.setUseTempoColors(true) }
            // The widget draws in the app's colours, so it is redrawn right away.
            coVerify { todayWidgetRefresher.refresh() }
        }

    @Test
    fun `tempoColorsObserver updates state when repository changes`() =
        runTest {
            coEvery { themePreferencesRepository.getUseTempoColors() } returns flowOf(true)

            // Re-init VM
            viewModel = createViewModel()
            advanceUntilIdle()

            assertThat(viewModel.uiState.value.useTempoColors).isTrue()
        }

    @Test
    fun `themeModeObserver updates state when repository changes`() =
        runTest {
            coEvery { themePreferencesRepository.getThemeMode() } returns flowOf(ThemeMode.LIGHT)

            // Re-init VM
            viewModel = createViewModel()
            advanceUntilIdle()

            assertThat(viewModel.uiState.value.selectedThemeMode).isEqualTo(ThemeMode.LIGHT)
        }

    @Test
    fun `availableThemeModes contains all three options`() =
        runTest {
            advanceUntilIdle()
            val availableModes = viewModel.uiState.value.availableThemeModes

            assertThat(availableModes).hasSize(3)
            assertThat(availableModes).contains(ThemeMode.LIGHT)
            assertThat(availableModes).contains(ThemeMode.DARK)
            assertThat(availableModes).contains(ThemeMode.SYSTEM)
        }

    @Test
    fun `initialState formats version info with optional commit sha`() =
        runTest {
            every { appVersionProvider.getVersionInfo() } returns AppVersionInfo("1.0", 1, "abcdef0")
            viewModel = createViewModel()
            advanceUntilIdle()
            assertThat(viewModel.uiState.value.appVersion).isEqualTo("1.0 (abcdef0)")

            every { appVersionProvider.getVersionInfo() } returns AppVersionInfo("1.0", 1, " ")
            viewModel = createViewModel()
            advanceUntilIdle()
            assertThat(viewModel.uiState.value.appVersion).isEqualTo("1.0")
            advanceUntilIdle()
            assertThat(viewModel.uiState.value.appVersion).isEqualTo("1.0")
        }

    @Test
    fun `retention preferences update settings state`() =
        runTest {
            every { completedTaskRetentionPreferences.isEnabled } returns MutableStateFlow(true)
            every { completedTaskRetentionPreferences.retentionDays } returns MutableStateFlow(45)
            viewModel = createViewModel()

            advanceUntilIdle()

            assertThat(viewModel.uiState.value.autoRemoveCompletedTasksEnabled).isTrue()
            assertThat(viewModel.uiState.value.completedTaskRetentionDays).isEqualTo(45)
        }

    @Test
    fun `auto removal toggle configures current retention`() =
        runTest {
            advanceUntilIdle()

            viewModel.onEvent(SettingsContract.UiEvent.AutoRemoveCompletedTasksToggled(true))

            verify { configureCompletedTaskRetention(true, 30) }
            assertThat(viewModel.uiState.value.autoRemoveCompletedTasksEnabled).isTrue()
        }

    @Test
    fun `retention day change is normalized and configured`() =
        runTest {
            advanceUntilIdle()

            viewModel.onEvent(SettingsContract.UiEvent.CompletedTaskRetentionDaysChanged(44))

            verify { configureCompletedTaskRetention(false, 44) }
            assertThat(viewModel.uiState.value.completedTaskRetentionDays).isEqualTo(45)
        }

    @Test
    fun `missed reminder preferences update settings state`() =
        runTest {
            every { missedReminderPreferences.isEnabled } returns MutableStateFlow(false)
            every { missedReminderPreferences.catchUpTime } returns MutableStateFlow(LocalTime(hour = 7, minute = 30))
            viewModel = createViewModel()

            advanceUntilIdle()

            assertThat(viewModel.uiState.value.missedReminderCatchUpEnabled).isFalse()
            assertThat(viewModel.uiState.value.missedReminderCatchUpTime)
                .isEqualTo(LocalTime(hour = 7, minute = 30))
        }

    @Test
    fun `catch-up toggle persists the preference and re-arms the catch-up`() =
        runTest {
            advanceUntilIdle()

            viewModel.onEvent(SettingsContract.UiEvent.MissedReminderCatchUpToggled(false))

            verifyOrder {
                missedReminderPreferences.setEnabled(false)
                missedReminderScheduler.sync()
            }
        }

    @Test
    fun `catch-up time change persists the preference and re-arms the catch-up`() =
        runTest {
            advanceUntilIdle()

            viewModel.onEvent(
                SettingsContract.UiEvent.MissedReminderCatchUpTimeChanged(LocalTime(hour = 6, minute = 45)),
            )

            verifyOrder {
                missedReminderPreferences.setCatchUpTime(LocalTime(hour = 6, minute = 45))
                missedReminderScheduler.sync()
            }
        }

    @Test
    fun `export click shows the passphrase dialog first`() =
        runTest {
            advanceUntilIdle()
            viewModel.onEvent(SettingsContract.UiEvent.ExportClicked)
            advanceUntilIdle()

            assertThat(viewModel.uiState.value.backupDialog)
                .isEqualTo(SettingsContract.BackupDialog.EnterExportPassphrase)
            coVerify(exactly = 0) { exportBackup(any()) }
        }

    @Test
    fun `confirmed passphrase generates backup and launches picker with suggested name`() =
        runTest {
            coEvery { exportBackup(any()) } returns
                ExportBackupUseCase.Export(json = "{}", suggestedFileName = "backup-20260721-1000.tempo")

            viewModel.uiEffect.test {
                viewModel.onEvent(SettingsContract.UiEvent.ExportClicked)
                viewModel.onEvent(
                    SettingsContract.UiEvent.ExportPassphraseConfirmed("secret".toCharArray(), "secret".toCharArray()),
                )
                advanceUntilIdle()

                assertThat(awaitItem()).isEqualTo(
                    SettingsContract.UiEffect.LaunchExportPicker("backup-20260721-1000.tempo"),
                )
            }
        }

    @Test
    fun `mismatched passphrase confirmation does not export`() =
        runTest {
            viewModel.onEvent(SettingsContract.UiEvent.ExportClicked)
            viewModel.onEvent(
                SettingsContract.UiEvent.ExportPassphraseConfirmed(
                    "secret".toCharArray(),
                    "different".toCharArray(),
                ),
            )
            advanceUntilIdle()

            coVerify(exactly = 0) { exportBackup(any()) }
            assertThat(viewModel.uiState.value.backupDialog)
                .isEqualTo(SettingsContract.BackupDialog.EnterExportPassphrase)
        }

    @Test
    fun `picked export destination writes the pending json and confirms`() =
        runTest {
            coEvery { exportBackup(any()) } returns
                ExportBackupUseCase.Export(json = "{\"encryptionVersion\":1}", suggestedFileName = "f.tempo")
            val uri = mockk<Uri>()
            every { backupFileDataSource.locationLabel(uri) } returns null

            viewModel.uiEffect.test {
                viewModel.onEvent(SettingsContract.UiEvent.ExportClicked)
                viewModel.onEvent(
                    SettingsContract.UiEvent.ExportPassphraseConfirmed(
                        "secret".toCharArray(),
                        "secret".toCharArray(),
                    ),
                )
                advanceUntilIdle()
                awaitItem()
                viewModel.onEvent(SettingsContract.UiEvent.ExportDestinationPicked(uri))
                advanceUntilIdle()

                coVerify { backupFileDataSource.write(uri, "{\"encryptionVersion\":1}") }
                assertThat(awaitItem()).isEqualTo(
                    SettingsContract.UiEffect.ShowMessage(com.mandrecode.tempo.R.string.backup_export_success),
                )
            }
        }

    @Test
    fun `picked export destination names the destination folder when known`() =
        runTest {
            coEvery { exportBackup(any()) } returns
                ExportBackupUseCase.Export(json = "{}", suggestedFileName = "f.tempo")
            val uri = mockk<Uri>()
            every { backupFileDataSource.locationLabel(uri) } returns "Downloads"

            viewModel.uiEffect.test {
                viewModel.onEvent(SettingsContract.UiEvent.ExportClicked)
                viewModel.onEvent(
                    SettingsContract.UiEvent.ExportPassphraseConfirmed(
                        "secret".toCharArray(),
                        "secret".toCharArray(),
                    ),
                )
                advanceUntilIdle()
                awaitItem()
                viewModel.onEvent(SettingsContract.UiEvent.ExportDestinationPicked(uri))
                advanceUntilIdle()

                assertThat(awaitItem()).isEqualTo(
                    SettingsContract.UiEffect.ShowMessage(
                        com.mandrecode.tempo.R.string.backup_export_success_at,
                        listOf("Downloads"),
                    ),
                )
            }
        }

    @Test
    fun `picked import file that is not an encrypted envelope shows a corrupt file error`() =
        runTest {
            // Every valid export is an encrypted envelope now — there's no legacy unencrypted
            // format to fall back to — so content that isn't one (the default relaxed-mock
            // response for isEncryptedBackup) is reported as corrupt right away.
            viewModel.onEvent(SettingsContract.UiEvent.ImportFilePicked(mockk()))
            advanceUntilIdle()

            assertThat(viewModel.uiState.value.backupDialog)
                .isEqualTo(
                    SettingsContract.BackupDialog.ImportFailed(SettingsContract.ImportError.CorruptFile),
                )
        }

    @Test
    fun `choosing a mode runs the import and shows the summary`() =
        runTest {
            val uri = mockk<Uri>()
            coEvery { backupFileDataSource.read(uri) } returns "{\"encryptionVersion\":1}"
            every { backupRepository.isEncryptedBackup("{\"encryptionVersion\":1}") } returns true
            coEvery { importBackup(any(), ImportMode.MERGE, any()) } returns
                ImportOutcome.Success(ImportSummary())

            viewModel.onEvent(SettingsContract.UiEvent.ImportFilePicked(uri))
            advanceUntilIdle()
            viewModel.onEvent(SettingsContract.UiEvent.ImportPassphraseEntered("secret".toCharArray()))
            advanceUntilIdle()
            viewModel.onEvent(SettingsContract.UiEvent.ImportModeChosen(ImportMode.MERGE))
            advanceUntilIdle()

            val dialog = viewModel.uiState.value.backupDialog
            assertThat(dialog).isInstanceOf(SettingsContract.BackupDialog.ImportSucceeded::class.java)
            assertThat(viewModel.uiState.value.backupInProgress).isFalse()
        }

    @Test
    fun `unreadable import file shows a read error`() =
        runTest {
            val uri = mockk<Uri>()
            coEvery { backupFileDataSource.read(uri) } throws java.io.IOException("boom")

            viewModel.onEvent(SettingsContract.UiEvent.ImportFilePicked(uri))
            advanceUntilIdle()
            viewModel.onEvent(SettingsContract.UiEvent.ImportModeChosen(ImportMode.REPLACE))
            advanceUntilIdle()

            assertThat(viewModel.uiState.value.backupDialog)
                .isEqualTo(
                    SettingsContract.BackupDialog.ImportFailed(SettingsContract.ImportError.ReadFailed),
                )
        }

    @Test
    fun `unsupported version outcome maps to the version error dialog`() =
        runTest {
            val uri = mockk<Uri>()
            coEvery { backupFileDataSource.read(uri) } returns "{\"encryptionVersion\":1}"
            every { backupRepository.isEncryptedBackup("{\"encryptionVersion\":1}") } returns true
            coEvery { importBackup(any(), any(), any()) } returns
                ImportOutcome.UnsupportedVersion(fileVersion = 7, maxSupported = 1)

            viewModel.onEvent(SettingsContract.UiEvent.ImportFilePicked(uri))
            advanceUntilIdle()
            viewModel.onEvent(SettingsContract.UiEvent.ImportPassphraseEntered("secret".toCharArray()))
            advanceUntilIdle()
            viewModel.onEvent(SettingsContract.UiEvent.ImportModeChosen(ImportMode.MERGE))
            advanceUntilIdle()

            assertThat(viewModel.uiState.value.backupDialog)
                .isEqualTo(
                    SettingsContract.BackupDialog.ImportFailed(
                        SettingsContract.ImportError.UnsupportedVersion(fileVersion = 7, maxSupported = 1),
                    ),
                )
        }

    @Test
    fun `encrypted import file shows the passphrase dialog before mode choice`() =
        runTest {
            val uri = mockk<Uri>()
            coEvery { backupFileDataSource.read(uri) } returns "{\"encryptionVersion\":1}"
            every { backupRepository.isEncryptedBackup("{\"encryptionVersion\":1}") } returns true

            viewModel.onEvent(SettingsContract.UiEvent.ImportFilePicked(uri))
            advanceUntilIdle()

            assertThat(viewModel.uiState.value.backupDialog)
                .isEqualTo(SettingsContract.BackupDialog.EnterImportPassphrase())
        }

    @Test
    fun `wrong passphrase on import loops back to the passphrase dialog`() =
        runTest {
            val uri = mockk<Uri>()
            coEvery { backupFileDataSource.read(uri) } returns "{\"encryptionVersion\":1}"
            every { backupRepository.isEncryptedBackup(any()) } returns true
            coEvery { importBackup(any(), any(), any()) } returns ImportOutcome.WrongPassphrase

            viewModel.onEvent(SettingsContract.UiEvent.ImportFilePicked(uri))
            advanceUntilIdle()
            viewModel.onEvent(SettingsContract.UiEvent.ImportPassphraseEntered("wrong".toCharArray()))
            advanceUntilIdle()
            viewModel.onEvent(SettingsContract.UiEvent.ImportModeChosen(ImportMode.MERGE))
            advanceUntilIdle()

            assertThat(viewModel.uiState.value.backupDialog)
                .isEqualTo(SettingsContract.BackupDialog.EnterImportPassphrase(attemptsFailed = true))
        }

    @Test
    fun `dismissing the backup dialog clears it`() =
        runTest {
            viewModel.onEvent(SettingsContract.UiEvent.ImportFilePicked(mockk()))
            viewModel.onEvent(SettingsContract.UiEvent.BackupDialogDismissed)
            advanceUntilIdle()

            assertThat(viewModel.uiState.value.backupDialog).isNull()
        }

    @Test
    fun `export destination without a confirmed passphrase shows an error instead of silently doing nothing`() =
        runTest {
            val uri = mockk<Uri>()

            viewModel.uiEffect.test {
                viewModel.onEvent(SettingsContract.UiEvent.ExportDestinationPicked(uri))
                advanceUntilIdle()

                assertThat(awaitItem()).isEqualTo(
                    SettingsContract.UiEffect.ShowMessage(com.mandrecode.tempo.R.string.backup_export_error),
                )
            }
            coVerify(exactly = 0) { exportBackup(any()) }
            coVerify(exactly = 0) { backupFileDataSource.write(any(), any()) }
        }

    @Test
    fun `failed export write shows the error message`() =
        runTest {
            coEvery { exportBackup(any()) } returns
                ExportBackupUseCase.Export(json = "{}", suggestedFileName = "f.tempo")
            coEvery { backupFileDataSource.write(any(), any()) } throws java.io.IOException("nope")
            val uri = mockk<Uri>()

            viewModel.uiEffect.test {
                viewModel.onEvent(SettingsContract.UiEvent.ExportClicked)
                viewModel.onEvent(
                    SettingsContract.UiEvent.ExportPassphraseConfirmed(
                        "secret".toCharArray(),
                        "secret".toCharArray(),
                    ),
                )
                advanceUntilIdle()
                awaitItem()
                viewModel.onEvent(SettingsContract.UiEvent.ExportDestinationPicked(uri))
                advanceUntilIdle()

                assertThat(awaitItem()).isEqualTo(
                    SettingsContract.UiEffect.ShowMessage(com.mandrecode.tempo.R.string.backup_export_error),
                )
            }
        }

    @Test
    fun `failed export write keeps the pending payload so a retry can succeed without re-entering the passphrase`() =
        runTest {
            coEvery { exportBackup(any()) } returns
                ExportBackupUseCase.Export(json = "{\"encryptionVersion\":1}", suggestedFileName = "f.tempo")
            coEvery { backupFileDataSource.write(any(), any()) } throws java.io.IOException("nope") andThen Unit
            val uri = mockk<Uri>()

            viewModel.onEvent(SettingsContract.UiEvent.ExportClicked)
            viewModel.onEvent(
                SettingsContract.UiEvent.ExportPassphraseConfirmed(
                    "secret".toCharArray(),
                    "secret".toCharArray(),
                ),
            )
            advanceUntilIdle()
            viewModel.onEvent(SettingsContract.UiEvent.ExportDestinationPicked(uri))
            advanceUntilIdle()

            // Retry without generating a new export or re-entering the passphrase.
            viewModel.onEvent(SettingsContract.UiEvent.ExportDestinationPicked(uri))
            advanceUntilIdle()

            coVerify(exactly = 1) { exportBackup(any()) }
            coVerify(exactly = 2) { backupFileDataSource.write(uri, "{\"encryptionVersion\":1}") }
        }

    @Test
    fun `failed export generation shows the export error message`() =
        runTest {
            coEvery { exportBackup(any()) } throws IllegalStateException("db gone")

            viewModel.uiEffect.test {
                viewModel.onEvent(SettingsContract.UiEvent.ExportClicked)
                viewModel.onEvent(
                    SettingsContract.UiEvent.ExportPassphraseConfirmed(
                        "secret".toCharArray(),
                        "secret".toCharArray(),
                    ),
                )
                advanceUntilIdle()

                assertThat(awaitItem()).isEqualTo(
                    SettingsContract.UiEffect.ShowMessage(com.mandrecode.tempo.R.string.backup_export_error),
                )
            }
            assertThat(viewModel.uiState.value.backupInProgress).isFalse()
        }

    @Test
    fun `unexpected import failure shows the unexpected error dialog`() =
        runTest {
            val uri = mockk<Uri>()
            coEvery { backupFileDataSource.read(uri) } returns "{\"encryptionVersion\":1}"
            every { backupRepository.isEncryptedBackup("{\"encryptionVersion\":1}") } returns true
            coEvery { importBackup(any(), any(), any()) } throws IllegalStateException("boom")

            viewModel.onEvent(SettingsContract.UiEvent.ImportFilePicked(uri))
            advanceUntilIdle()
            viewModel.onEvent(SettingsContract.UiEvent.ImportPassphraseEntered("secret".toCharArray()))
            advanceUntilIdle()
            viewModel.onEvent(SettingsContract.UiEvent.ImportModeChosen(ImportMode.MERGE))
            advanceUntilIdle()

            assertThat(viewModel.uiState.value.backupDialog)
                .isEqualTo(
                    SettingsContract.BackupDialog.ImportFailed(SettingsContract.ImportError.Unexpected),
                )
            assertThat(viewModel.uiState.value.backupInProgress).isFalse()
        }

    @Test
    fun `choosing a mode without a picked file does nothing`() =
        runTest {
            viewModel.onEvent(SettingsContract.UiEvent.ImportModeChosen(ImportMode.MERGE))
            advanceUntilIdle()

            assertThat(viewModel.uiState.value.backupDialog).isNull()
            coVerify(exactly = 0) { importBackup(any(), any(), any()) }
        }

    @Test
    fun `export cancelled clears the pending payload so a later pick shows an error instead of writing`() =
        runTest {
            coEvery { exportBackup(any()) } returns
                ExportBackupUseCase.Export(json = "{}", suggestedFileName = "f.tempo")
            val uri = mockk<Uri>()

            viewModel.onEvent(SettingsContract.UiEvent.ExportClicked)
            viewModel.onEvent(
                SettingsContract.UiEvent.ExportPassphraseConfirmed(
                    "secret".toCharArray(),
                    "secret".toCharArray(),
                ),
            )
            advanceUntilIdle()
            viewModel.onEvent(SettingsContract.UiEvent.ExportCancelled)
            viewModel.onEvent(SettingsContract.UiEvent.ExportDestinationPicked(uri))
            advanceUntilIdle()

            coVerify(exactly = 1) { exportBackup(any()) }
            coVerify(exactly = 0) { backupFileDataSource.write(any(), any()) }
        }

    @Test
    fun `corrupt file outcome maps to the corrupt error dialog`() =
        runTest {
            val uri = mockk<Uri>()
            coEvery { backupFileDataSource.read(uri) } returns "{\"encryptionVersion\":1}"
            every { backupRepository.isEncryptedBackup("{\"encryptionVersion\":1}") } returns true
            coEvery { importBackup(any(), any(), any()) } returns ImportOutcome.CorruptFile

            viewModel.onEvent(SettingsContract.UiEvent.ImportFilePicked(uri))
            advanceUntilIdle()
            viewModel.onEvent(SettingsContract.UiEvent.ImportPassphraseEntered("secret".toCharArray()))
            advanceUntilIdle()
            viewModel.onEvent(SettingsContract.UiEvent.ImportModeChosen(ImportMode.MERGE))
            advanceUntilIdle()

            assertThat(viewModel.uiState.value.backupDialog)
                .isEqualTo(
                    SettingsContract.BackupDialog.ImportFailed(SettingsContract.ImportError.CorruptFile),
                )
        }

    @Test
    fun `validation failure outcome maps to the validation error dialog`() =
        runTest {
            val uri = mockk<Uri>()
            coEvery { backupFileDataSource.read(uri) } returns "{\"encryptionVersion\":1}"
            every { backupRepository.isEncryptedBackup("{\"encryptionVersion\":1}") } returns true
            coEvery { importBackup(any(), any(), any()) } returns ImportOutcome.ValidationFailed(emptyList())

            viewModel.onEvent(SettingsContract.UiEvent.ImportFilePicked(uri))
            advanceUntilIdle()
            viewModel.onEvent(SettingsContract.UiEvent.ImportPassphraseEntered("secret".toCharArray()))
            advanceUntilIdle()
            viewModel.onEvent(SettingsContract.UiEvent.ImportModeChosen(ImportMode.REPLACE))
            advanceUntilIdle()

            assertThat(viewModel.uiState.value.backupDialog)
                .isInstanceOf(SettingsContract.BackupDialog.ImportFailed::class.java)
        }

    private fun createViewModel(): SettingsViewModel =
        SettingsViewModel(
            themePreferencesRepository,
            appVersionProvider,
            completedTaskRetentionPreferences,
            configureCompletedTaskRetention,
            missedReminderPreferences,
            missedReminderScheduler,
            SettingsBackupDelegate(exportBackup, importBackup, backupRepository, backupFileDataSource),
            driveBackupDelegate(),
            vacationModeRepository,
            focusSessionRepository,
            todayWidgetRefresher,
        )
}
