package com.mandrecode.tempo.features.settings.presentation

import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import androidx.annotation.StringRes
import com.mandrecode.tempo.core.domain.model.ThemeMode
import com.mandrecode.tempo.features.backup.domain.model.BackupFrequency
import com.mandrecode.tempo.features.backup.domain.model.DriveBackupFailure
import com.mandrecode.tempo.features.backup.domain.model.ImportConflict
import com.mandrecode.tempo.features.backup.domain.model.ImportMode
import com.mandrecode.tempo.features.backup.domain.model.ValidationIssue
import com.mandrecode.tempo.features.tasks.domain.repository.CompletedTaskRetentionPreferences
import com.mandrecode.tempo.features.tasks.domain.repository.MissedReminderPreferences
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime

/**
 * Contract for Settings screen following MVI pattern.
 */
object SettingsContract {
    /**
     * UI State for Settings screen.
     */
    data class UiState(
        val selectedThemeMode: ThemeMode = ThemeMode.SYSTEM,
        val availableThemeModes: ImmutableList<ThemeMode> =
            persistentListOf(
                ThemeMode.LIGHT,
                ThemeMode.DARK,
                ThemeMode.SYSTEM,
            ),
        val useTempoColors: Boolean = false,
        val appVersion: String = "",
        val autoRemoveCompletedTasksEnabled: Boolean = false,
        val completedTaskRetentionDays: Int = CompletedTaskRetentionPreferences.DEFAULT_RETENTION_DAYS,
        val missedReminderCatchUpEnabled: Boolean = MissedReminderPreferences.DEFAULT_ENABLED,
        val missedReminderCatchUpTime: LocalTime = MissedReminderPreferences.DEFAULT_CATCH_UP_TIME,
        val focusSessionLengthMinutes: Int = 25,
        val focusBreakLengthMinutes: Int = 5,
        val vacationModeActive: Boolean = false,
        val vacationStartDate: LocalDate? = null,
        val vacationEndDate: LocalDate? = null,
        val backupInProgress: Boolean = false,
        val backupDialog: BackupDialog? = null,
        val driveBackup: DriveBackupUiState = DriveBackupUiState(),
        val driveBackupDialog: DriveBackupDialog? = null,
    )

    /** Google Drive backup (#306): its configuration and what is known about recent backups. */
    data class DriveBackupUiState(
        val enabled: Boolean = false,
        val frequency: BackupFrequency = BackupFrequency.DAILY,
        val unmeteredOnly: Boolean = true,
        /** Epoch millis of the last successful backup, null when there has never been one. */
        val lastSuccessAt: Long? = null,
        /** The failure to report, only while it is newer than the last success. */
        val failure: DriveBackupFailure? = null,
    )

    /** Modal state of the Google Drive backup section. */
    sealed interface DriveBackupDialog {
        /** Set the backup passphrase: first time ([isChange] false) or a replacement. */
        data class SetPassphrase(
            val isChange: Boolean,
        ) : DriveBackupDialog

        data object ChooseFrequency : DriveBackupDialog

        data object ConfirmTurnOff : DriveBackupDialog

        /** Play services could not authorise at all (no connection, outdated, not configured). */
        data object Unavailable : DriveBackupDialog
    }

    /** Modal state of the Settings Backup section. */
    sealed interface BackupDialog {
        /** Export was requested; the user must set a passphrase before the file is written. */
        data object EnterExportPassphrase : BackupDialog

        /** A file was picked for import; the user chooses Merge or Replace. */
        data object ChooseImportMode : BackupDialog

        /** The picked file is encrypted; the user must enter the passphrase used to create it. */
        data class EnterImportPassphrase(
            val attemptsFailed: Boolean = false,
        ) : BackupDialog

        data class ImportSucceeded(
            val imported: Int,
            val skipped: Int,
            val conflicts: ImmutableList<ImportConflict>,
        ) : BackupDialog

        data class ImportFailed(
            val error: ImportError,
        ) : BackupDialog
    }

    /** User-facing import failure, mirroring the domain outcome plus file-read errors. */
    sealed interface ImportError {
        data class UnsupportedVersion(
            val fileVersion: Int,
            val maxSupported: Int,
        ) : ImportError

        data object CorruptFile : ImportError

        data class ValidationFailed(
            val issues: ImmutableList<ValidationIssue>,
        ) : ImportError

        data object ReadFailed : ImportError

        /** Unexpected failure while applying the import; the database was rolled back. */
        data object Unexpected : ImportError
    }

    /**
     * UI Events that can be triggered from the Settings screen.
     */
    sealed interface UiEvent {
        data class ThemeModeSelected(
            val mode: ThemeMode,
        ) : UiEvent

        data class TempoColorsToggled(
            val enabled: Boolean,
        ) : UiEvent

        data class AutoRemoveCompletedTasksToggled(
            val enabled: Boolean,
        ) : UiEvent

        data class CompletedTaskRetentionDaysChanged(
            val days: Int,
        ) : UiEvent

        data class MissedReminderCatchUpToggled(
            val enabled: Boolean,
        ) : UiEvent

        data class MissedReminderCatchUpTimeChanged(
            val time: LocalTime,
        ) : UiEvent

        data class FocusBreakLengthChanged(
            val minutes: Int,
        ) : UiEvent

        data class FocusSessionLengthChanged(
            val minutes: Int,
        ) : UiEvent

        data class VacationModeToggled(
            val enabled: Boolean,
        ) : UiEvent

        /** `null` clears the planned end, leaving the pause open-ended. */
        data class VacationEndDateChanged(
            val endInclusive: LocalDate?,
        ) : UiEvent

        data object ExportClicked : UiEvent

        data class ExportPassphraseConfirmed(
            val passphrase: CharArray,
            val confirmation: CharArray,
        ) : UiEvent

        data class ExportDestinationPicked(
            val uri: Uri,
        ) : UiEvent

        data object ExportCancelled : UiEvent

        data object ImportClicked : UiEvent

        data class ImportFilePicked(
            val uri: Uri,
        ) : UiEvent

        data class ImportPassphraseEntered(
            val passphrase: CharArray,
        ) : UiEvent

        data class ImportModeChosen(
            val mode: ImportMode,
        ) : UiEvent

        data object BackupDialogDismissed : UiEvent

        /** Events of the Google Drive backup section (#306). */
        sealed interface DriveBackup : UiEvent {
            data object TurnOnClicked : DriveBackup

            data object ReconnectClicked : DriveBackup

            /** The Drive consent screen returned; [data] is its result intent, null if cancelled. */
            data class ConsentFinished(
                val data: Intent?,
            ) : DriveBackup

            data class PassphraseConfirmed(
                val passphrase: CharArray,
                val confirmation: CharArray,
            ) : DriveBackup

            data object FrequencyClicked : DriveBackup

            data class FrequencySelected(
                val frequency: BackupFrequency,
            ) : DriveBackup

            data class WifiOnlyToggled(
                val enabled: Boolean,
            ) : DriveBackup

            data object BackUpNowClicked : DriveBackup

            data object ChangePassphraseClicked : DriveBackup

            data object TurnOffClicked : DriveBackup

            data object TurnOffConfirmed : DriveBackup

            data object DialogDismissed : DriveBackup
        }
    }

    /**
     * One-time UI Effects for Settings screen.
     */
    sealed interface UiEffect {
        data class LaunchExportPicker(
            val suggestedFileName: String,
        ) : UiEffect

        data object LaunchImportPicker : UiEffect

        /** Show Google's consent for Drive access (#306); the result comes back as ConsentFinished. */
        data class LaunchDriveConsent(
            val intent: PendingIntent,
        ) : UiEffect

        data class ShowMessage(
            @param:StringRes val message: Int,
            val formatArgs: List<Any> = emptyList(),
        ) : UiEffect
    }
}
