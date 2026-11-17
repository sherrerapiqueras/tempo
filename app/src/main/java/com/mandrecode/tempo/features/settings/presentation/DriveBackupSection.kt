package com.mandrecode.tempo.features.settings.presentation

import android.text.format.DateUtils
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.mandrecode.tempo.R
import com.mandrecode.tempo.core.ui.components.TempoConfirmDialog
import com.mandrecode.tempo.core.ui.components.TempoMessageDialog
import com.mandrecode.tempo.features.backup.domain.model.BackupFrequency
import com.mandrecode.tempo.features.backup.domain.model.DriveBackupFailure
import com.mandrecode.tempo.features.settings.presentation.SettingsContract.DriveBackupDialog as Dialog
import com.mandrecode.tempo.features.settings.presentation.SettingsContract.UiEvent.DriveBackup as Event

internal const val DRIVE_BACKUP_STATUS_TEST_TAG = "drive_backup_status"

/**
 * Google Drive backup (#306). The status line comes first: whether the last backup worked, and
 * when, is what tells a user they are actually covered. Everything else is configuration.
 */
@Composable
internal fun DriveBackupSection(
    uiState: SettingsContract.UiState,
    onEvent: (SettingsContract.UiEvent) -> Unit,
) {
    val drive = uiState.driveBackup
    SettingsSection(title = stringResource(R.string.drive_backup_title)) {
        item(visible = !drive.enabled) {
            SettingsItem(
                icon = R.drawable.ic_sync,
                title = stringResource(R.string.drive_backup_turn_on_title),
                subtitle = stringResource(R.string.drive_backup_turn_on_description),
                trailingIcon = R.drawable.ic_chevron_right,
                onClick = { onEvent(Event.TurnOnClicked) },
            )
        }
        item(visible = drive.enabled) {
            DriveBackupStatusItem(drive = drive, onEvent = onEvent)
        }
        item(visible = drive.enabled) {
            SettingsItem(
                icon = R.drawable.ic_file_upload,
                title = stringResource(R.string.drive_backup_now),
                subtitle = stringResource(R.string.drive_backup_now_description),
                onClick = { onEvent(Event.BackUpNowClicked) },
            )
        }
        item(visible = drive.enabled) {
            SettingsItem(
                icon = R.drawable.ic_repeat,
                title = stringResource(R.string.drive_backup_frequency),
                subtitle = drive.frequency.label(),
                trailingIcon = R.drawable.ic_chevron_right,
                onClick = { onEvent(Event.FrequencyClicked) },
            )
        }
        item(visible = drive.enabled) {
            SettingsSwitchItem(
                icon = R.drawable.ic_savings,
                title = stringResource(R.string.drive_backup_wifi_only),
                checked = drive.unmeteredOnly,
                onCheckedChange = { onEvent(Event.WifiOnlyToggled(it)) },
            )
        }
        item(visible = drive.enabled) {
            SettingsItem(
                icon = R.drawable.ic_edit,
                title = stringResource(R.string.drive_backup_change_passphrase),
                subtitle = stringResource(R.string.drive_backup_change_passphrase_description),
                onClick = { onEvent(Event.ChangePassphraseClicked) },
            )
        }
        item(visible = drive.enabled) {
            SettingsItem(
                icon = R.drawable.ic_close,
                title = stringResource(R.string.drive_backup_turn_off),
                subtitle = stringResource(R.string.drive_backup_turn_off_description),
                onClick = { onEvent(Event.TurnOffClicked) },
            )
        }
    }
    DriveBackupDialogs(dialog = uiState.driveBackupDialog, frequency = drive.frequency, onEvent = onEvent)
}

@Composable
private fun DriveBackupStatusItem(
    drive: SettingsContract.DriveBackupUiState,
    onEvent: (SettingsContract.UiEvent) -> Unit,
) {
    val failure = drive.failure
    val lastBackup =
        drive.lastSuccessAt?.let {
            DateUtils.getRelativeTimeSpanString(it, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString()
        } ?: stringResource(R.string.drive_backup_never)
    val action =
        when (failure) {
            DriveBackupFailure.AUTHORIZATION_NEEDED -> Event.ReconnectClicked
            DriveBackupFailure.PASSPHRASE_UNAVAILABLE -> Event.ChangePassphraseClicked
            else -> null
        }
    Column(modifier = Modifier.testTag(DRIVE_BACKUP_STATUS_TEST_TAG)) {
        SettingsItem(
            icon = if (failure == null) R.drawable.ic_history else R.drawable.ic_warning,
            title = stringResource(R.string.drive_backup_last_backup),
            subtitle = lastBackup,
            onClick = { action?.let(onEvent) },
        )
        if (failure != null) {
            Text(
                text = failure.message(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(start = 56.dp, end = 16.dp, bottom = 12.dp),
            )
            action?.let {
                Text(
                    text = stringResource(it.actionLabel()),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier =
                        Modifier
                            .clickable { onEvent(it) }
                            .padding(start = 56.dp, end = 16.dp, bottom = 16.dp),
                )
            }
        }
    }
}

@Composable
private fun DriveBackupDialogs(
    dialog: Dialog?,
    frequency: BackupFrequency,
    onEvent: (SettingsContract.UiEvent) -> Unit,
) {
    when (dialog) {
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
private fun BackupFrequency.label(): String =
    stringResource(
        when (this) {
            BackupFrequency.DAILY -> R.string.drive_backup_frequency_daily
            BackupFrequency.WEEKLY -> R.string.drive_backup_frequency_weekly
        },
    )

@Composable
private fun DriveBackupFailure.message(): String =
    stringResource(
        when (this) {
            DriveBackupFailure.NETWORK -> R.string.drive_backup_failure_network
            DriveBackupFailure.AUTHORIZATION_NEEDED -> R.string.drive_backup_failure_authorization
            DriveBackupFailure.STORAGE_FULL -> R.string.drive_backup_failure_storage
            DriveBackupFailure.PASSPHRASE_UNAVAILABLE -> R.string.drive_backup_failure_passphrase
            DriveBackupFailure.UNEXPECTED -> R.string.drive_backup_failure_unexpected
        },
    )

private fun Event.actionLabel(): Int =
    when (this) {
        Event.ReconnectClicked -> R.string.drive_backup_reconnect
        else -> R.string.drive_backup_set_passphrase
    }
