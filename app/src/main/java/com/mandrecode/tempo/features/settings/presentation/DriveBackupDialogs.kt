package com.mandrecode.tempo.features.settings.presentation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.mandrecode.tempo.R
import com.mandrecode.tempo.core.ui.components.TempoConfirmDialog
import com.mandrecode.tempo.core.ui.components.TempoMessageDialog
import com.mandrecode.tempo.features.backup.domain.model.BackupFrequency
import com.mandrecode.tempo.features.backup.domain.model.DriveBackupFailure
import com.mandrecode.tempo.features.backup.presentation.DriveRestoreDialogs
import com.mandrecode.tempo.features.settings.presentation.SettingsContract.DriveBackupDialog as Dialog
import com.mandrecode.tempo.features.settings.presentation.SettingsContract.UiEvent.DriveBackup as Event

/**
 * The Drive backup dialogs, shown over whichever Settings page is open: the switch on the main
 * page starts the same setup and turn-off flows as the one on the Drive backup page, and a
 * restore (#307) can start from that page too.
 */
@Composable
internal fun DriveBackupDialogHost(
    uiState: SettingsContract.UiState,
    onEvent: (SettingsContract.UiEvent) -> Unit,
) {
    val frequency = uiState.driveBackup.frequency
    when (val dialog = uiState.driveBackupDialog) {
        is Dialog.SetPassphrase -> DriveBackupPassphraseDialog(isChange = dialog.isChange, onEvent = onEvent)
        Dialog.ChooseFrequency -> FrequencyDialog(selected = frequency, onEvent = onEvent)
        Dialog.ConfirmTurnOff ->
            TempoConfirmDialog(
                title = stringResource(R.string.drive_backup_turn_off_title),
                confirmLabel = stringResource(R.string.drive_backup_turn_off_confirm),
                onConfirm = { onEvent(Event.TurnOffConfirmed) },
                onCancel = { onEvent(Event.DialogDismissed) },
                isDestructive = true,
            ) {
                Text(stringResource(R.string.drive_backup_turn_off_message))
            }
        Dialog.Unavailable ->
            TempoMessageDialog(
                title = stringResource(R.string.drive_backup_unavailable_title),
                onDismiss = { onEvent(Event.DialogDismissed) },
            ) {
                Text(stringResource(R.string.drive_backup_unavailable_message))
            }
        null -> Unit
    }
    DriveRestoreDialogs(step = uiState.driveRestore, onEvent = { onEvent(Event.Restore(it)) })
}

/** Setting the passphrase states what the user must keep to restore (#305), before they confirm. */
@Composable
private fun DriveBackupPassphraseDialog(
    isChange: Boolean,
    onEvent: (SettingsContract.UiEvent) -> Unit,
) {
    var passphrase by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    val mismatch = confirmation.isNotEmpty() && passphrase != confirmation
    TempoConfirmDialog(
        title = stringResource(if (isChange) R.string.drive_backup_change_title else R.string.drive_backup_setup_title),
        confirmLabel =
            stringResource(if (isChange) R.string.drive_backup_change_confirm else R.string.drive_backup_setup_confirm),
        onConfirm = { onEvent(Event.PassphraseConfirmed(passphrase.toCharArray(), confirmation.toCharArray())) },
        onCancel = { onEvent(Event.DialogDismissed) },
        isDestructive = false,
        confirmEnabled = passphrase.isNotEmpty() && passphrase == confirmation,
    ) {
        Column {
            Text(
                stringResource(
                    if (isChange) R.string.drive_backup_change_message else R.string.drive_backup_setup_message,
                ),
            )
            OutlinedTextField(
                value = passphrase,
                onValueChange = { passphrase = it },
                label = { Text(stringResource(R.string.drive_backup_passphrase_label)) },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
            )
            OutlinedTextField(
                value = confirmation,
                onValueChange = { confirmation = it },
                label = { Text(stringResource(R.string.drive_backup_passphrase_confirm_label)) },
                singleLine = true,
                isError = mismatch,
                supportingText =
                    if (mismatch) {
                        { Text(stringResource(R.string.backup_export_passphrase_mismatch)) }
                    } else {
                        null
                    },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
        }
    }
}

@Composable
private fun FrequencyDialog(
    selected: BackupFrequency,
    onEvent: (SettingsContract.UiEvent) -> Unit,
) {
    TempoMessageDialog(
        title = stringResource(R.string.drive_backup_frequency),
        onDismiss = { onEvent(Event.DialogDismissed) },
    ) {
        Column {
            BackupFrequency.entries.forEach { frequency ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clickable { onEvent(Event.FrequencySelected(frequency)) }
                            .padding(vertical = 4.dp),
                ) {
                    RadioButton(
                        selected = frequency == selected,
                        onClick = { onEvent(Event.FrequencySelected(frequency)) },
                    )
                    Text(frequency.label())
                }
            }
        }
    }
}

@Composable
internal fun BackupFrequency.label(): String =
    stringResource(
        when (this) {
            BackupFrequency.DAILY -> R.string.drive_backup_frequency_daily
            BackupFrequency.WEEKLY -> R.string.drive_backup_frequency_weekly
        },
    )

@Composable
internal fun DriveBackupFailure.message(): String =
    stringResource(
        when (this) {
            DriveBackupFailure.NETWORK -> R.string.drive_backup_failure_network
            DriveBackupFailure.AUTHORIZATION_NEEDED -> R.string.drive_backup_failure_authorization
            DriveBackupFailure.STORAGE_FULL -> R.string.drive_backup_failure_storage
            DriveBackupFailure.PASSPHRASE_UNAVAILABLE -> R.string.drive_backup_failure_passphrase
            DriveBackupFailure.UNEXPECTED -> R.string.drive_backup_failure_unexpected
        },
    )

internal fun Event.actionLabel(): Int =
    when (this) {
        Event.ReconnectClicked -> R.string.drive_backup_reconnect
        Event.ManageStorageClicked -> R.string.drive_backup_manage_storage
        else -> R.string.drive_backup_set_passphrase
    }
