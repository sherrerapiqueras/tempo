package com.mandrecode.tempo.features.onboarding.presentation

import com.mandrecode.tempo.features.settings.presentation.SettingsContract

internal fun OnboardingContract.UiState.toSettingsUiState(): SettingsContract.UiState =
    SettingsContract.UiState(
        selectedThemeMode = selectedThemeMode,
        availableThemeModes = availableThemeModes,
        useTempoColors = useTempoColors,
    )

internal fun SettingsContract.UiEvent.toOnboardingEvent(): OnboardingContract.UiEvent? =
    when (this) {
        is SettingsContract.UiEvent.ThemeModeSelected -> OnboardingContract.UiEvent.ThemeModeSelected(mode)
        is SettingsContract.UiEvent.TempoColorsToggled -> OnboardingContract.UiEvent.UseTempoColorsToggled(enabled)

        is SettingsContract.UiEvent.FocusSessionLengthChanged,
        is SettingsContract.UiEvent.FocusBreakLengthChanged,
        is SettingsContract.UiEvent.AutoRemoveCompletedTasksToggled,
        is SettingsContract.UiEvent.CompletedTaskRetentionDaysChanged,
        is SettingsContract.UiEvent.MissedReminderCatchUpToggled,
        is SettingsContract.UiEvent.MissedReminderCatchUpTimeChanged,
        is SettingsContract.UiEvent.VacationModeToggled,
        is SettingsContract.UiEvent.VacationEndDateChanged,
        is SettingsContract.UiEvent.ExportClicked,
        is SettingsContract.UiEvent.ExportPassphraseConfirmed,
        is SettingsContract.UiEvent.ExportDestinationPicked,
        is SettingsContract.UiEvent.ExportCancelled,
        is SettingsContract.UiEvent.ImportClicked,
        is SettingsContract.UiEvent.ImportFilePicked,
        is SettingsContract.UiEvent.ImportPassphraseEntered,
        is SettingsContract.UiEvent.ImportModeChosen,
        is SettingsContract.UiEvent.BackupDialogDismissed,
        is SettingsContract.UiEvent.DriveBackup,
        -> null
    }
