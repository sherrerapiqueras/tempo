package com.mandrecode.tempo.features.backup.presentation

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.mandrecode.tempo.core.ui.theme.TempoTheme
import com.mandrecode.tempo.features.backup.domain.model.CloudBackupFile
import com.mandrecode.tempo.features.backup.domain.model.ImportMode

private val backup = CloudBackupFile(id = "a", name = "tempo-backup-x-1.tempo", createdAt = 1_794_909_600_000, sizeBytes = 48_000)

@Composable
private fun Step(step: DriveRestoreStep) {
    TempoTheme { DriveRestoreDialogs(step = step, onEvent = {}) }
}

@Preview(name = "Restore - offer", showBackground = true)
@Composable
private fun RestoreOfferPreview() = Step(DriveRestoreStep.Offer(backup))

@Preview(name = "Restore - offer - Dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun RestoreOfferDarkPreview() = Step(DriveRestoreStep.Offer(backup))

@Preview(name = "Restore - merge or replace", showBackground = true)
@Composable
private fun RestoreModePreview() = Step(DriveRestoreStep.ChooseMode(backup))

@Preview(name = "Restore - wrong passphrase", showBackground = true)
@Composable
private fun RestoreWrongPassphrasePreview() =
    Step(DriveRestoreStep.EnterPassphrase(backup, ImportMode.REPLACE, previousAttemptWrong = true))

@Preview(name = "Restore - keep backing up", showBackground = true)
@Composable
private fun RestoreKeepBackingUpPreview() = Step(DriveRestoreStep.Restored(imported = 12, offerToKeepBackingUp = true))

@Preview(name = "Restore - newer backup", showBackground = true)
@Composable
private fun RestoreNewerPreview() = Step(DriveRestoreStep.Failed(DriveRestoreFailure.NewerVersion(3, 2)))
