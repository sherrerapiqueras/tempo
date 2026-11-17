package com.mandrecode.tempo.features.settings.presentation

import android.content.res.Configuration
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.mandrecode.tempo.core.ui.theme.TempoTheme
import com.mandrecode.tempo.features.backup.domain.model.DriveBackupFailure

private val on = SettingsContract.DriveBackupUiState(enabled = true, lastSuccessAt = 1_794_909_600_000)

@Composable
private fun DriveBackupSectionPreviewContent(state: SettingsContract.UiState) {
    TempoTheme { Column { DriveBackupSection(uiState = state, onEvent = {}) } }
}

@Preview(name = "Drive backup - off", showBackground = true)
@Composable
private fun DriveBackupOffPreview() = DriveBackupSectionPreviewContent(SettingsContract.UiState())

@Preview(name = "Drive backup - on", showBackground = true)
@Composable
private fun DriveBackupOnPreview() = DriveBackupSectionPreviewContent(SettingsContract.UiState(driveBackup = on))

@Preview(name = "Drive backup - on - Dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun DriveBackupOnDarkPreview() = DriveBackupSectionPreviewContent(SettingsContract.UiState(driveBackup = on))

@Preview(name = "Drive backup - authorisation lost", showBackground = true)
@Composable
private fun DriveBackupReconnectPreview() =
    DriveBackupSectionPreviewContent(
        SettingsContract.UiState(driveBackup = on.copy(failure = DriveBackupFailure.AUTHORIZATION_NEEDED)),
    )

@Preview(name = "Drive backup - set passphrase", showBackground = true)
@Composable
private fun DriveBackupPassphrasePreview() =
    DriveBackupSectionPreviewContent(
        SettingsContract.UiState(driveBackupDialog = SettingsContract.DriveBackupDialog.SetPassphrase(isChange = false)),
    )
