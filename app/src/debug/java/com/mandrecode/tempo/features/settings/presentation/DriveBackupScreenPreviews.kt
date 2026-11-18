package com.mandrecode.tempo.features.settings.presentation

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.mandrecode.tempo.core.ui.theme.TempoTheme
import com.mandrecode.tempo.features.backup.domain.model.DriveBackupFailure

private val on = SettingsContract.DriveBackupUiState(enabled = true, lastSuccessAt = 1_794_909_600_000)

@Composable
private fun DriveBackupPreviewContent(state: SettingsContract.UiState) {
    TempoTheme {
        DriveBackupScreenContent(uiState = state, onEvent = {})
        DriveBackupDialogHost(uiState = state, onEvent = {})
    }
}

@Preview(name = "Drive backup page - off", showBackground = true)
@Composable
private fun DriveBackupOffPreview() = DriveBackupPreviewContent(SettingsContract.UiState())

@Preview(name = "Drive backup page - on", showBackground = true, heightDp = 900)
@Composable
private fun DriveBackupOnPreview() = DriveBackupPreviewContent(SettingsContract.UiState(driveBackup = on))

@Preview(
    name = "Drive backup page - on - Dark",
    showBackground = true,
    heightDp = 900,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun DriveBackupOnDarkPreview() = DriveBackupPreviewContent(SettingsContract.UiState(driveBackup = on))

@Preview(name = "Drive backup page - authorisation lost", showBackground = true, heightDp = 900)
@Composable
private fun DriveBackupReconnectPreview() =
    DriveBackupPreviewContent(
        SettingsContract.UiState(driveBackup = on.copy(failure = DriveBackupFailure.AUTHORIZATION_NEEDED)),
    )

@Preview(name = "Drive backup page - set passphrase", showBackground = true)
@Composable
private fun DriveBackupPassphrasePreview() =
    DriveBackupPreviewContent(
        SettingsContract.UiState(driveBackupDialog = SettingsContract.DriveBackupDialog.SetPassphrase(isChange = false)),
    )

@Preview(name = "Backup & Restore - Drive on", showBackground = true)
@Composable
private fun BackupSectionDriveOnPreview() {
    TempoTheme { BackupSection(uiState = SettingsContract.UiState(driveBackup = on), onEvent = {}) }
}
