package com.mandrecode.tempo.features.settings.presentation

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.mandrecode.tempo.R
import com.mandrecode.tempo.core.ui.components.TempoMessageDialog
import com.mandrecode.tempo.core.ui.glass.DialogWindowGlass
import com.mandrecode.tempo.core.ui.theme.dialogTitle
import com.mandrecode.tempo.features.backup.domain.model.BackupEntityKind
import com.mandrecode.tempo.features.backup.domain.model.ConflictReason
import com.mandrecode.tempo.features.backup.domain.model.ImportConflict
import com.mandrecode.tempo.features.backup.domain.model.ValidationIssue
import com.mandrecode.tempo.features.backup.domain.model.ValidationIssueKind

@Composable
internal fun BackupSection(
    uiState: SettingsContract.UiState,
    onEvent: (SettingsContract.UiEvent) -> Unit,
    onOpenDriveBackup: () -> Unit = {},
) {
    SettingsSection(title = stringResource(R.string.settings_backup)) {
        driveBackupEntry(drive = uiState.driveBackup, onEvent = onEvent, onOpen = onOpenDriveBackup)
        item {
            SettingsItem(
                icon = R.drawable.ic_file_download,
                title = stringResource(R.string.backup_import_title),
                subtitle = stringResource(R.string.backup_import_description),
                trailingIcon = R.drawable.ic_chevron_right,
                onClick = { onEvent(SettingsContract.UiEvent.ImportClicked) },
            )
        }
        item {
            SettingsItem(
                icon = R.drawable.ic_file_upload,
                title = stringResource(R.string.backup_export_title),
                subtitle = stringResource(R.string.backup_export_description),
                trailingIcon = R.drawable.ic_chevron_right,
                onClick = { onEvent(SettingsContract.UiEvent.ExportClicked) },
            )
        }
    }
    BackupDialogs(uiState = uiState, onEvent = onEvent)
}

@Composable
private fun BackupDialogs(
    uiState: SettingsContract.UiState,
    onEvent: (SettingsContract.UiEvent) -> Unit,
) {
    when (val dialog = uiState.backupDialog) {
        is SettingsContract.BackupDialog.EnterExportPassphrase ->
            ExportPassphraseDialog(onEvent = onEvent)

        is SettingsContract.BackupDialog.ChooseImportMode ->
            ImportModeDialog(onEvent = onEvent)

        is SettingsContract.BackupDialog.EnterImportPassphrase ->
            ImportPassphraseDialog(dialog = dialog, onEvent = onEvent)

        is SettingsContract.BackupDialog.ImportSucceeded ->
            ImportSucceededDialog(dialog = dialog, onEvent = onEvent)

        is SettingsContract.BackupDialog.ImportFailed ->
            ImportFailedDialog(error = dialog.error, onEvent = onEvent)

        null -> if (uiState.backupInProgress) BackupProgressDialog()
    }
}

@Composable
private fun ImportSucceededDialog(
    dialog: SettingsContract.BackupDialog.ImportSucceeded,
    onEvent: (SettingsContract.UiEvent) -> Unit,
) {
    TempoMessageDialog(
        title = stringResource(R.string.backup_import_success_title),
        onDismiss = { onEvent(SettingsContract.UiEvent.BackupDialogDismissed) },
    ) {
        Column(
            modifier = Modifier.verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(stringResource(R.string.backup_import_summary_imported, dialog.imported))
            Text(stringResource(R.string.backup_import_summary_skipped, dialog.skipped))
            Text(stringResource(R.string.backup_import_summary_conflicts, dialog.conflicts.size))
            dialog.conflicts.forEach { conflict ->
                Text(
                    text = conflict.label(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ImportFailedDialog(
    error: SettingsContract.ImportError,
    onEvent: (SettingsContract.UiEvent) -> Unit,
) {
    TempoMessageDialog(
        title = stringResource(R.string.backup_import_error_title),
        onDismiss = { onEvent(SettingsContract.UiEvent.BackupDialogDismissed) },
    ) {
        Column(
            modifier = Modifier.verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(error.message())
            if (error is SettingsContract.ImportError.ValidationFailed) {
                error.issues.forEach { issue ->
                    Text(
                        text = issue.label(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun ValidationIssue.label(): AnnotatedString =
    when (kind) {
        ValidationIssueKind.DUPLICATE_ID ->
            AnnotatedString(stringResource(R.string.backup_issue_duplicate_id, detail))

        ValidationIssueKind.UNKNOWN_CATEGORY_REFERENCE ->
            boldNameMessage(
                prefixResId = R.string.backup_issue_unknown_category_prefix,
                name = detail,
                suffixResId = R.string.backup_issue_unknown_category_suffix,
            )

        ValidationIssueKind.UNKNOWN_PARENT_TASK_REFERENCE ->
            boldNameMessage(
                prefixResId = R.string.backup_issue_unknown_parent_task_prefix,
                name = detail,
                suffixResId = R.string.backup_issue_unknown_parent_task_suffix,
            )

        ValidationIssueKind.UNKNOWN_CHAIN_REFERENCE ->
            AnnotatedString(stringResource(R.string.backup_issue_unknown_chain, detail))

        ValidationIssueKind.UNKNOWN_HABIT_REFERENCE ->
            AnnotatedString(stringResource(R.string.backup_issue_unknown_habit, detail))
    }

@Composable
private fun boldNameMessage(
    @StringRes prefixResId: Int,
    name: String,
    @StringRes suffixResId: Int,
): AnnotatedString =
    buildAnnotatedString {
        append(stringResource(prefixResId))
        append(" ")
        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
            append(name)
        }
        append(stringResource(suffixResId))
    }

@Composable
private fun BackupProgressDialog() {
    AlertDialog(
        onDismissRequest = {},
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
        title = {
            DialogWindowGlass()
            Text(
                text = stringResource(R.string.backup_in_progress),
                style = MaterialTheme.typography.dialogTitle,
            )
        },
        text = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(modifier = Modifier.size(32.dp))
            }
        },
        confirmButton = {},
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun SettingsContract.ImportError.message(): String =
    when (this) {
        is SettingsContract.ImportError.UnsupportedVersion ->
            stringResource(R.string.backup_import_error_unsupported_version, fileVersion, maxSupported)

        is SettingsContract.ImportError.CorruptFile ->
            stringResource(R.string.backup_import_error_corrupt)

        is SettingsContract.ImportError.ValidationFailed ->
            stringResource(R.string.backup_import_error_validation)

        is SettingsContract.ImportError.ReadFailed ->
            stringResource(R.string.backup_import_error_read)

        is SettingsContract.ImportError.Unexpected ->
            stringResource(R.string.backup_import_error_unexpected)
    }

@Composable
private fun ImportConflict.label(): AnnotatedString {
    val kindLabel =
        stringResource(
            when (kind) {
                BackupEntityKind.CATEGORY -> R.string.backup_entity_category
                BackupEntityKind.TASK -> R.string.backup_entity_task
                BackupEntityKind.HABIT -> R.string.backup_entity_habit
                BackupEntityKind.HABIT_CHAIN -> R.string.backup_entity_habit_chain
            },
        )
    val reasonLabel =
        stringResource(
            when (reason) {
                ConflictReason.CONTENT_MISMATCH -> R.string.backup_conflict_reason_content
                ConflictReason.PARENT_CONFLICTED -> R.string.backup_conflict_reason_parent
            },
        )
    return buildAnnotatedString {
        append(kindLabel)
        append(" ")
        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
            append(displayName)
        }
        append(stringResource(R.string.backup_conflict_entry_suffix, reasonLabel))
    }
}
