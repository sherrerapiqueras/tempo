package com.mandrecode.tempo.features.backup.presentation

import android.text.format.DateUtils
import android.text.format.Formatter
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.mandrecode.tempo.R
import com.mandrecode.tempo.core.ui.components.TempoConfirmDialog
import com.mandrecode.tempo.core.ui.components.TempoMessageDialog
import com.mandrecode.tempo.features.backup.domain.model.DriveBackupFailure
import com.mandrecode.tempo.features.backup.domain.model.ImportMode

/** The dialogs of a restore from Google Drive (#307), for whichever screen hosts the flow. */
@Composable
internal fun DriveRestoreDialogs(
    step: DriveRestoreStep?,
    onEvent: (DriveRestoreEvent) -> Unit,
) {
    val dismiss = { onEvent(DriveRestoreEvent.Dismissed) }
    when (step) {
        DriveRestoreStep.Searching -> ProgressDialog(stringResource(R.string.drive_restore_searching))
        DriveRestoreStep.Restoring -> ProgressDialog(stringResource(R.string.drive_restore_in_progress))
        is DriveRestoreStep.Offer -> {
            val context = LocalContext.current
            val date =
                DateUtils.formatDateTime(
                    context,
                    step.backup.createdAt,
                    DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_TIME or DateUtils.FORMAT_SHOW_YEAR,
                )
            val size = Formatter.formatShortFileSize(context, step.backup.sizeBytes)
            TempoConfirmDialog(
                title = stringResource(R.string.drive_restore_offer_title),
                confirmLabel = stringResource(R.string.drive_restore_confirm),
                onConfirm = { onEvent(DriveRestoreEvent.RestoreClicked) },
                onCancel = dismiss,
                isDestructive = false,
            ) { Text(stringResource(R.string.drive_restore_offer_message, date, size)) }
        }
        DriveRestoreStep.NoBackups ->
            TempoMessageDialog(title = stringResource(R.string.drive_restore_none_title), onDismiss = dismiss) {
                Text(stringResource(R.string.drive_restore_none_message))
            }
        is DriveRestoreStep.ChooseMode ->
            TempoConfirmDialog(
                title = stringResource(R.string.drive_restore_mode_title),
                confirmLabel = stringResource(R.string.drive_restore_mode_merge),
                cancelLabel = stringResource(R.string.drive_restore_mode_replace),
                onConfirm = { onEvent(DriveRestoreEvent.ModeChosen(ImportMode.MERGE)) },
                onCancel = { onEvent(DriveRestoreEvent.ModeChosen(ImportMode.REPLACE)) },
                isDestructive = false,
            ) { Text(stringResource(R.string.drive_restore_mode_message)) }
        is DriveRestoreStep.EnterPassphrase -> PassphraseDialog(step.previousAttemptWrong, onEvent)
        is DriveRestoreStep.Restored ->
            if (step.offerToKeepBackingUp) {
                TempoConfirmDialog(
                    title = stringResource(R.string.drive_restore_done_title),
                    confirmLabel = stringResource(R.string.drive_restore_keep_confirm),
                    cancelLabel = stringResource(R.string.drive_restore_keep_decline),
                    onConfirm = { onEvent(DriveRestoreEvent.KeepBackingUpChosen(true)) },
                    onCancel = { onEvent(DriveRestoreEvent.KeepBackingUpChosen(false)) },
                    isDestructive = false,
                ) { Text(stringResource(R.string.drive_restore_keep_message)) }
            } else {
                TempoMessageDialog(title = stringResource(R.string.drive_restore_done_title), onDismiss = dismiss) {
                    Text(stringResource(R.string.drive_restore_done_message))
                }
            }
        is DriveRestoreStep.Failed ->
            TempoMessageDialog(title = stringResource(R.string.drive_restore_failed_title), onDismiss = dismiss) {
                Text(step.reason.message())
            }
        null -> Unit
    }
}

@Composable
private fun ProgressDialog(message: String) {
    TempoMessageDialog(title = message, onDismiss = {}) {
        LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
    }
}

@Composable
private fun PassphraseDialog(
    previousAttemptWrong: Boolean,
    onEvent: (DriveRestoreEvent) -> Unit,
) {
    var passphrase by remember(previousAttemptWrong) { mutableStateOf("") }
    TempoConfirmDialog(
        title = stringResource(R.string.drive_restore_passphrase_title),
        confirmLabel = stringResource(R.string.drive_restore_confirm),
        onConfirm = { onEvent(DriveRestoreEvent.PassphraseEntered(passphrase.toCharArray())) },
        onCancel = { onEvent(DriveRestoreEvent.Dismissed) },
        isDestructive = false,
        confirmEnabled = passphrase.isNotEmpty(),
    ) {
        Column {
            Text(stringResource(R.string.drive_restore_passphrase_message))
            OutlinedTextField(
                value = passphrase,
                onValueChange = { passphrase = it },
                label = { Text(stringResource(R.string.drive_backup_passphrase_label)) },
                singleLine = true,
                isError = previousAttemptWrong,
                supportingText =
                    if (previousAttemptWrong) {
                        { Text(stringResource(R.string.drive_restore_passphrase_wrong)) }
                    } else {
                        null
                    },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
            )
        }
    }
}

@Composable
private fun DriveRestoreFailure.message(): String =
    when (this) {
        DriveRestoreFailure.Corrupt -> stringResource(R.string.drive_restore_failed_corrupt)
        DriveRestoreFailure.Invalid -> stringResource(R.string.drive_restore_failed_invalid)
        is DriveRestoreFailure.NewerVersion ->
            stringResource(R.string.drive_restore_failed_newer, fileVersion, maxSupported)
        DriveRestoreFailure.Unavailable -> stringResource(R.string.drive_backup_unavailable_message)
        is DriveRestoreFailure.Cloud ->
            stringResource(
                when (failure) {
                    DriveBackupFailure.NETWORK -> R.string.drive_restore_failed_network
                    DriveBackupFailure.AUTHORIZATION_NEEDED -> R.string.drive_restore_failed_authorization
                    else -> R.string.drive_restore_failed_unexpected
                },
            )
    }
