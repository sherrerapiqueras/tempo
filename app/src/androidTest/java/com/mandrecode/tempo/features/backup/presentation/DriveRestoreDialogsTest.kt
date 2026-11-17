package com.mandrecode.tempo.features.backup.presentation

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import com.mandrecode.tempo.R
import com.mandrecode.tempo.core.ui.theme.TempoTheme
import com.mandrecode.tempo.features.backup.domain.model.CloudBackupFile
import com.mandrecode.tempo.features.backup.domain.model.DriveBackupFailure
import com.mandrecode.tempo.features.backup.domain.model.ImportMode
import org.junit.Rule
import org.junit.Test

class DriveRestoreDialogsTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private val events = mutableListOf<DriveRestoreEvent>()
    private val backup = CloudBackupFile(id = "a", name = "tempo-backup-1.tempo", createdAt = 1_794_909_600_000, sizeBytes = 2048)

    private fun text(id: Int) = InstrumentationRegistry.getInstrumentation().targetContext.getString(id)

    private fun show(step: DriveRestoreStep) {
        composeTestRule.setContent { TempoTheme { DriveRestoreDialogs(step = step, onEvent = { events += it }) } }
    }

    @Test
    fun theOfferShowsWhenTheBackupIsFromAndItsSize() {
        show(DriveRestoreStep.Offer(backup))

        composeTestRule.onNodeWithText("2026", substring = true).assertIsDisplayed()
        composeTestRule.onNodeWithText("2.0 kB", substring = true).assertIsDisplayed()
        composeTestRule.onNodeWithText(text(R.string.drive_restore_confirm)).performClick()

        assertThat(events).containsExactly(DriveRestoreEvent.RestoreClicked)
    }

    @Test
    fun aDeviceWithDataChoosesMergeOrReplace() {
        show(DriveRestoreStep.ChooseMode(backup))

        composeTestRule.onNodeWithText(text(R.string.drive_restore_mode_replace)).performClick()

        assertThat(events).containsExactly(DriveRestoreEvent.ModeChosen(ImportMode.REPLACE))
    }

    @Test
    fun aWrongPassphraseIsCalledOut() {
        show(DriveRestoreStep.EnterPassphrase(backup, ImportMode.REPLACE, previousAttemptWrong = true))

        composeTestRule.onNodeWithText(text(R.string.drive_restore_passphrase_wrong)).assertIsDisplayed()
    }

    @Test
    fun theEnteredPassphraseIsWhatIsSent() {
        show(DriveRestoreStep.EnterPassphrase(backup, ImportMode.REPLACE))

        composeTestRule.onNode(hasSetTextAction()).performTextInput("secret")
        composeTestRule.onNodeWithText(text(R.string.drive_restore_confirm)).performClick()

        assertThat((events.single() as DriveRestoreEvent.PassphraseEntered).passphrase.concatToString()).isEqualTo("secret")
    }

    @Test
    fun aNewerBackupSaysToUpdate() {
        show(DriveRestoreStep.Failed(DriveRestoreFailure.NewerVersion(fileVersion = 3, maxSupported = 2)))

        composeTestRule
            .onNodeWithText(
                InstrumentationRegistry.getInstrumentation().targetContext.getString(R.string.drive_restore_failed_newer, 3, 2),
            ).assertIsDisplayed()
    }

    @Test
    fun noNetworkIsNotReportedAsADamagedBackup() {
        show(DriveRestoreStep.Failed(DriveRestoreFailure.Cloud(DriveBackupFailure.NETWORK)))

        composeTestRule.onNodeWithText(text(R.string.drive_restore_failed_network)).assertIsDisplayed()
        composeTestRule.onNodeWithText(text(R.string.drive_restore_failed_corrupt)).assertDoesNotExist()
    }

    @Test
    fun afterRestoringItOffersToKeepBackingUp() {
        show(DriveRestoreStep.Restored(imported = 3, offerToKeepBackingUp = true))

        composeTestRule.onNodeWithText(text(R.string.drive_restore_keep_confirm)).performClick()

        assertThat(events).containsExactly(DriveRestoreEvent.KeepBackingUpChosen(true))
    }
}
