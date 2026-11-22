package com.mandrecode.tempo.features.settings.presentation

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mandrecode.tempo.core.data.preferences.ThemePreferencesRepository
import com.mandrecode.tempo.core.domain.model.VacationPeriod
import com.mandrecode.tempo.core.domain.repository.VacationModeRepository
import com.mandrecode.tempo.features.focus.domain.repository.FocusSessionRepository
import com.mandrecode.tempo.features.tasks.domain.repository.CompletedTaskRetentionPreferences
import com.mandrecode.tempo.features.tasks.domain.repository.MissedReminderPreferences
import com.mandrecode.tempo.features.tasks.domain.scheduler.MissedReminderScheduler
import com.mandrecode.tempo.features.tasks.domain.usecase.ConfigureCompletedTaskRetentionUseCase
import com.mandrecode.tempo.features.widget.presentation.QuickAddTaskWidgetRefresher
import com.mandrecode.tempo.util.AppVersionProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import javax.inject.Inject
import kotlin.time.Clock

private const val LOG_TAG = "SettingsViewModel"

/** Snapshot of the preference stores mirrored into Settings state, as one emission. */
private data class PreferenceSnapshot(
    val retentionEnabled: Boolean,
    val retentionDays: Int,
    val catchUpEnabled: Boolean,
    val catchUpTime: LocalTime,
    val vacationPeriods: List<VacationPeriod>,
)

private fun today(): LocalDate = Clock.System.todayIn(TimeZone.currentSystemDefault())

@HiltViewModel
class SettingsViewModel
    @Inject
    constructor(
        private val themePreferencesRepository: ThemePreferencesRepository,
        private val appVersionProvider: AppVersionProvider,
        private val completedTaskRetentionPreferences: CompletedTaskRetentionPreferences,
        private val configureCompletedTaskRetention: ConfigureCompletedTaskRetentionUseCase,
        private val missedReminderPreferences: MissedReminderPreferences,
        private val missedReminderScheduler: MissedReminderScheduler,
        private val backupDelegate: SettingsBackupDelegate,
        private val driveBackupDelegate: SettingsDriveBackupDelegate,
        private val vacationModeRepository: VacationModeRepository,
        private val focusSessionRepository: FocusSessionRepository,
        private val quickAddTaskWidgetRefresher: QuickAddTaskWidgetRefresher,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow(SettingsContract.UiState())
        val uiState: StateFlow<SettingsContract.UiState> = _uiState.asStateFlow()

        private val _uiEffect = Channel<SettingsContract.UiEffect>(Channel.BUFFERED)
        val uiEffect = _uiEffect.receiveAsFlow()

        private val backupHost =
            SettingsBackupDelegate.Host(
                scope = viewModelScope,
                updateState = { transform -> _uiState.update(transform) },
                sendEffect = { effect -> _uiEffect.trySend(effect) },
            )

        init {
            observeThemeMode()
            observeTempoColors()
            loadVersionInfo()
            observeFocusPreferences()
            observePreferenceStores()
            driveBackupDelegate.start(backupHost)
        }

        private fun observeThemeMode() {
            viewModelScope.launch {
                themePreferencesRepository.getThemeMode().collect { mode ->
                    _uiState.update { it.copy(selectedThemeMode = mode) }
                }
            }
        }

        private fun observeTempoColors() {
            viewModelScope.launch {
                themePreferencesRepository.getUseTempoColors().collect { enabled ->
                    _uiState.update { it.copy(useTempoColors = enabled) }
                }
            }
        }

        private fun loadVersionInfo() {
            val versionInfo = appVersionProvider.getVersionInfo()
            _uiState.update {
                it.copy(appVersion = versionInfo.displayName)
            }
        }

        private fun observeFocusPreferences() {
            viewModelScope.launch {
                focusSessionRepository.defaultLengthMinutes.collect { minutes ->
                    _uiState.update { it.copy(focusSessionLengthMinutes = minutes) }
                }
            }
            viewModelScope.launch {
                focusSessionRepository.breakLengthMinutes.collect { minutes ->
                    _uiState.update { it.copy(focusBreakLengthMinutes = minutes) }
                }
            }
        }

        fun onEvent(event: SettingsContract.UiEvent) {
            when (event) {
                is SettingsContract.UiEvent.ThemeModeSelected -> {
                    themePreferencesRepository.setThemeMode(event.mode)
                }

                is SettingsContract.UiEvent.TempoColorsToggled -> {
                    themePreferencesRepository.setUseTempoColors(event.enabled)
                    refreshQuickAddTaskWidget()
                }

                is SettingsContract.UiEvent.FocusSessionLengthChanged -> {
                    focusSessionRepository.setDefaultLengthMinutes(event.minutes)
                }

                is SettingsContract.UiEvent.FocusBreakLengthChanged -> {
                    focusSessionRepository.setBreakLengthMinutes(event.minutes)
                }

                is SettingsContract.UiEvent.AutoRemoveCompletedTasksToggled -> {
                    updateCompletedTaskRetention(
                        enabled = event.enabled,
                        days = _uiState.value.completedTaskRetentionDays,
                    )
                }

                is SettingsContract.UiEvent.CompletedTaskRetentionDaysChanged -> {
                    updateCompletedTaskRetention(
                        enabled = _uiState.value.autoRemoveCompletedTasksEnabled,
                        days = event.days,
                    )
                }

                is SettingsContract.UiEvent.MissedReminderCatchUpToggled -> {
                    missedReminderPreferences.setEnabled(event.enabled)
                    missedReminderScheduler.sync()
                }

                is SettingsContract.UiEvent.MissedReminderCatchUpTimeChanged -> {
                    missedReminderPreferences.setCatchUpTime(event.time)
                    missedReminderScheduler.sync()
                }

                is SettingsContract.UiEvent.VacationModeToggled ->
                    vacationModeRepository.setPaused(today(), event.enabled)

                is SettingsContract.UiEvent.VacationEndDateChanged ->
                    vacationModeRepository.setPlannedEnd(today(), event.endInclusive)

                is SettingsContract.UiEvent.ExportClicked,
                is SettingsContract.UiEvent.ExportPassphraseConfirmed,
                is SettingsContract.UiEvent.ExportDestinationPicked,
                is SettingsContract.UiEvent.ExportCancelled,
                is SettingsContract.UiEvent.ImportClicked,
                is SettingsContract.UiEvent.ImportFilePicked,
                is SettingsContract.UiEvent.ImportPassphraseEntered,
                is SettingsContract.UiEvent.ImportModeChosen,
                is SettingsContract.UiEvent.BackupDialogDismissed,
                -> backupDelegate.onEvent(event, backupHost)

                is SettingsContract.UiEvent.DriveBackup -> driveBackupDelegate.onEvent(event, backupHost)
            }
        }

        // Refresh any placed widget instances immediately rather than waiting for the next
        // system-triggered update, since the widget has no periodic refresh. Best-effort: a
        // refresh failure (e.g. no widget instances placed) must never crash this unrelated
        // Settings toggle, but cancellation must still propagate normally. The exception type
        // from AppWidgetManager/Glance internals isn't a documented, narrow set, so this mirrors
        // the same generic-catch pattern already used for other best-effort operations (see
        // TasksViewModelTaskActions.addTask()).
        @Suppress("TooGenericExceptionCaught")
        private fun refreshQuickAddTaskWidget() {
            viewModelScope.launch {
                try {
                    quickAddTaskWidgetRefresher.refresh()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.e(LOG_TAG, "Unable to refresh Quick Add Task widget instances", e)
                }
            }
        }

        /**
         * Mirrors the preference stores (completed-task retention, missed-reminder catch-up,
         * vacation mode) into state.
         *
         * Vacation mode is derived from whether a stored period covers *today* rather than from a
         * flag, so a period whose planned end date has passed simply reads as off — no timer,
         * alarm, or midnight job involved.
         */
        private fun observePreferenceStores() {
            viewModelScope.launch {
                combine(
                    completedTaskRetentionPreferences.isEnabled,
                    completedTaskRetentionPreferences.retentionDays,
                    missedReminderPreferences.isEnabled,
                    missedReminderPreferences.catchUpTime,
                    vacationModeRepository.periods,
                ) { retentionEnabled, retentionDays, catchUpEnabled, catchUpTime, vacationPeriods ->
                    PreferenceSnapshot(
                        retentionEnabled = retentionEnabled,
                        retentionDays = retentionDays,
                        catchUpEnabled = catchUpEnabled,
                        catchUpTime = catchUpTime,
                        vacationPeriods = vacationPeriods,
                    )
                }.collect { preferences ->
                    val vacation = VacationPeriod.activeOn(preferences.vacationPeriods, today())
                    _uiState.update {
                        it.copy(
                            autoRemoveCompletedTasksEnabled = preferences.retentionEnabled,
                            completedTaskRetentionDays =
                                CompletedTaskRetentionPreferences.normalizeRetentionDays(preferences.retentionDays),
                            missedReminderCatchUpEnabled = preferences.catchUpEnabled,
                            missedReminderCatchUpTime = preferences.catchUpTime,
                            vacationModeActive = vacation != null,
                            vacationStartDate = vacation?.start,
                            vacationEndDate = vacation?.endInclusive,
                        )
                    }
                }
            }
        }

        private fun updateCompletedTaskRetention(
            enabled: Boolean,
            days: Int,
        ) {
            configureCompletedTaskRetention(enabled, days)
            _uiState.update {
                it.copy(
                    autoRemoveCompletedTasksEnabled = enabled,
                    completedTaskRetentionDays = CompletedTaskRetentionPreferences.normalizeRetentionDays(days),
                )
            }
        }
    }
