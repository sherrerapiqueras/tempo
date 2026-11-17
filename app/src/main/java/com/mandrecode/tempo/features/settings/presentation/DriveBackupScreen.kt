package com.mandrecode.tempo.features.settings.presentation

import android.text.format.DateUtils
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.mandrecode.tempo.R
import com.mandrecode.tempo.core.ui.glass.glassCardColor
import com.mandrecode.tempo.features.backup.domain.model.DriveBackupFailure
import com.mandrecode.tempo.features.settings.presentation.SettingsContract.UiEvent.DriveBackup as Event

internal const val DRIVE_BACKUP_STATUS_TEST_TAG = "drive_backup_status"
internal const val DRIVE_BACKUP_ENTRY_TEST_TAG = "drive_backup_entry"
internal const val DRIVE_BACKUP_MAIN_SWITCH_TEST_TAG = "drive_backup_main_switch"

/**
 * Google Drive backup's row on the main Settings page (#306): its switch turns backup on or off,
 * the rest opens [DriveBackupScreenContent]. The summary says whether the user is covered, so the
 * one thing worth knowing is visible without opening the screen.
 */
internal fun SettingsGroupScope.driveBackupEntry(
    drive: SettingsContract.DriveBackupUiState,
    onEvent: (SettingsContract.UiEvent) -> Unit,
    onOpen: () -> Unit,
) {
    item {
        val needsAttention = drive.enabled && drive.failure != null
        SettingsSplitSwitchItem(
            icon = R.drawable.ic_sync,
            title = stringResource(R.string.drive_backup_title),
            subtitle = drive.summary(),
            subtitleColor =
                if (needsAttention) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            checked = drive.enabled,
            onCheckedChange = { onEvent(if (it) Event.TurnOnClicked else Event.TurnOffClicked) },
            onClick = onOpen,
            modifier = Modifier.testTag(DRIVE_BACKUP_ENTRY_TEST_TAG),
        )
    }
}

@Composable
private fun SettingsContract.DriveBackupUiState.summary(): String =
    when {
        !enabled -> stringResource(R.string.drive_backup_turn_on_description)
        failure != null -> stringResource(R.string.drive_backup_entry_attention)
        lastSuccessAt != null -> stringResource(R.string.drive_backup_entry_last_backup, relativeTime(lastSuccessAt))
        else -> stringResource(R.string.drive_backup_entry_pending)
    }

private fun relativeTime(atMillis: Long): String =
    DateUtils
        .getRelativeTimeSpanString(atMillis, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS)
        .toString()

/**
 * Google Drive backup's own Settings page (#306). The main switch leads; while backup is on, the
 * status comes first, because whether the last backup worked, and when, is what tells a user
 * they are actually covered. Everything after it is configuration.
 */
@Composable
internal fun DriveBackupScreenContent(
    uiState: SettingsContract.UiState,
    onEvent: (SettingsContract.UiEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val drive = uiState.driveBackup
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(WindowInsets.navigationBars.asPaddingValues())
                .padding(horizontal = 16.dp)
                .padding(top = 24.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            DriveBackupMainSwitch(
                checked = drive.enabled,
                onCheckedChange = { onEvent(if (it) Event.TurnOnClicked else Event.TurnOffClicked) },
            )
            Text(
                text = stringResource(R.string.drive_backup_screen_intro),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }
        AnimatedVisibility(
            visible = drive.enabled,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
        ) {
            DriveBackupOnSections(drive = drive, onEvent = onEvent)
        }
        // Available whether backup is on or not: a restore is often the first thing a new phone needs.
        SettingsSection(title = stringResource(R.string.drive_backup_section_restore)) {
            item {
                SettingsItem(
                    icon = R.drawable.ic_file_download,
                    title = stringResource(R.string.drive_restore_title),
                    subtitle = stringResource(R.string.drive_restore_description),
                    trailingIcon = R.drawable.ic_chevron_right,
                    onClick = { onEvent(Event.RestoreClicked) },
                )
            }
        }
    }
}

/** The big switch at the top of the page, styled like Android's own main switch. */
@Composable
private fun DriveBackupMainSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    val title = stringResource(R.string.drive_backup_main_switch)
    Surface(
        shape = RoundedCornerShape(28.dp),
        color =
            if (checked) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                glassCardColor(MaterialTheme.colorScheme.surfaceContainerHigh)
            },
        contentColor =
            if (checked) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable { onCheckedChange(!checked) }
                .testTag(DRIVE_BACKUP_MAIN_SWITCH_TEST_TAG),
    ) {
        Row(
            modifier = Modifier.heightIn(min = 80.dp).padding(horizontal = 24.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f),
            )
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                modifier = Modifier.semantics { contentDescription = title },
            )
        }
    }
}

@Composable
private fun DriveBackupOnSections(
    drive: SettingsContract.DriveBackupUiState,
    onEvent: (SettingsContract.UiEvent) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
        SettingsSection(title = stringResource(R.string.drive_backup_section_backups)) {
            item { DriveBackupStatusItem(drive = drive, onEvent = onEvent) }
            item {
                SettingsItem(
                    icon = R.drawable.ic_file_upload,
                    title = stringResource(R.string.drive_backup_now),
                    subtitle = stringResource(R.string.drive_backup_now_description),
                    onClick = { onEvent(Event.BackUpNowClicked) },
                )
            }
        }
        SettingsSection(title = stringResource(R.string.drive_backup_section_schedule)) {
            item {
                SettingsItem(
                    icon = R.drawable.ic_repeat,
                    title = stringResource(R.string.drive_backup_frequency),
                    subtitle = drive.frequency.label(),
                    trailingIcon = R.drawable.ic_chevron_right,
                    onClick = { onEvent(Event.FrequencyClicked) },
                )
            }
            item {
                SettingsSwitchItem(
                    icon = R.drawable.ic_wifi,
                    title = stringResource(R.string.drive_backup_wifi_only),
                    checked = drive.unmeteredOnly,
                    onCheckedChange = { onEvent(Event.WifiOnlyToggled(it)) },
                )
            }
        }
        SettingsSection(title = stringResource(R.string.drive_backup_section_passphrase)) {
            item {
                SettingsItem(
                    icon = R.drawable.ic_edit,
                    title = stringResource(R.string.drive_backup_change_passphrase),
                    subtitle = stringResource(R.string.drive_backup_change_passphrase_description),
                    onClick = { onEvent(Event.ChangePassphraseClicked) },
                )
            }
        }
    }
}

@Composable
private fun DriveBackupStatusItem(
    drive: SettingsContract.DriveBackupUiState,
    onEvent: (SettingsContract.UiEvent) -> Unit,
) {
    val failure = drive.failure
    val lastBackup = drive.lastSuccessAt?.let(::relativeTime) ?: stringResource(R.string.drive_backup_never)
    val action =
        when (failure) {
            DriveBackupFailure.AUTHORIZATION_NEEDED -> Event.ReconnectClicked
            DriveBackupFailure.PASSPHRASE_UNAVAILABLE -> Event.ChangePassphraseClicked
            DriveBackupFailure.STORAGE_FULL -> Event.ManageStorageClicked
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
                modifier = Modifier.padding(start = 72.dp, end = 16.dp, bottom = 12.dp),
            )
            action?.let {
                Text(
                    text = stringResource(it.actionLabel()),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier =
                        Modifier
                            .clickable { onEvent(it) }
                            .padding(start = 72.dp, end = 16.dp, bottom = 16.dp),
                )
            }
        }
    }
}
