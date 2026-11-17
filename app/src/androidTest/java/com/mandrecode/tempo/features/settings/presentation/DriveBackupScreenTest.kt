package com.mandrecode.tempo.features.settings.presentation

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import com.mandrecode.tempo.R
import com.mandrecode.tempo.core.ui.theme.TempoTheme
import com.mandrecode.tempo.features.backup.domain.model.DriveBackupFailure
import org.junit.Rule
import org.junit.Test
import com.mandrecode.tempo.features.settings.presentation.SettingsContract.UiEvent.DriveBackup as Event

class DriveBackupScreenTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private val events = mutableListOf<SettingsContract.UiEvent>()
    private var opened = false

    private fun text(id: Int) = InstrumentationRegistry.getInstrumentation().targetContext.getString(id)

    private fun showScreen(state: SettingsContract.UiState) {
        composeTestRule.setContent {
            TempoTheme {
                DriveBackupScreenContent(uiState = state, onEvent = { events += it })
                DriveBackupDialogHost(uiState = state, onEvent = { events += it })
            }
        }
    }

    private fun showEntry(state: SettingsContract.UiState) {
        composeTestRule.setContent {
            TempoTheme {
                BackupSection(uiState = state, onEvent = { events += it }, onOpenDriveBackup = { opened = true })
            }
        }
    }

    private val on = SettingsContract.UiState(driveBackup = SettingsContract.DriveBackupUiState(enabled = true))

    @Test
    fun theEntrySwitchTurnsBackupOn() {
        showEntry(SettingsContract.UiState())

        composeTestRule.onNodeWithContentDescription(text(R.string.drive_backup_title)).assertIsOff().performClick()

        assertThat(events).containsExactly(Event.TurnOnClicked)
        assertThat(opened).isFalse()
    }

    @Test
    fun theRestOfTheEntryOpensTheDriveBackupPage() {
        showEntry(on)

        composeTestRule.onNodeWithText(text(R.string.drive_backup_title)).performClick()

        assertThat(opened).isTrue()
        assertThat(events).isEmpty()
    }

    @Test
    fun theEntrySaysWhenBackupNeedsAttention() {
        showEntry(on.copy(driveBackup = on.driveBackup.copy(failure = DriveBackupFailure.AUTHORIZATION_NEEDED)))

        composeTestRule.onNodeWithText(text(R.string.drive_backup_entry_attention)).assertIsDisplayed()
    }

    @Test
    fun offShowsOnlyTheMainSwitch() {
        showScreen(SettingsContract.UiState())

        composeTestRule.onNodeWithContentDescription(text(R.string.drive_backup_main_switch)).assertIsOff()
        composeTestRule.onNodeWithText(text(R.string.drive_backup_now)).assertDoesNotExist()
    }

    @Test
    fun theMainSwitchTurnsBackupOff() {
        showScreen(on)

        composeTestRule.onNodeWithTag(DRIVE_BACKUP_MAIN_SWITCH_TEST_TAG).performClick()

        assertThat(events).containsExactly(Event.TurnOffClicked)
    }

    @Test
    fun onWithNoBackupYetSaysNever() {
        showScreen(on)

        composeTestRule.onNodeWithContentDescription(text(R.string.drive_backup_main_switch)).assertIsOn()
        composeTestRule.onNodeWithText(text(R.string.drive_backup_last_backup)).assertIsDisplayed()
        composeTestRule.onNodeWithText(text(R.string.drive_backup_never)).assertIsDisplayed()
    }

    @Test
    fun lostAuthorisationShowsWhyAndOffersToReconnect() {
        showScreen(on.copy(driveBackup = on.driveBackup.copy(failure = DriveBackupFailure.AUTHORIZATION_NEEDED)))

        composeTestRule.onNodeWithText(text(R.string.drive_backup_failure_authorization)).assertIsDisplayed()
        composeTestRule.onNodeWithText(text(R.string.drive_backup_reconnect)).performClick()

        assertThat(events).containsExactly(Event.ReconnectClicked)
    }

    @Test
    fun anUnreadablePassphraseOffersToSetItAgain() {
        showScreen(on.copy(driveBackup = on.driveBackup.copy(failure = DriveBackupFailure.PASSPHRASE_UNAVAILABLE)))

        composeTestRule.onNodeWithText(text(R.string.drive_backup_set_passphrase)).performClick()

        assertThat(events).containsExactly(Event.ChangePassphraseClicked)
    }

    @Test
    fun aFullDriveOffersToManageStorage() {
        showScreen(on.copy(driveBackup = on.driveBackup.copy(failure = DriveBackupFailure.STORAGE_FULL)))

        composeTestRule.onNodeWithText(text(R.string.drive_backup_manage_storage)).performClick()

        assertThat(events).containsExactly(Event.ManageStorageClicked)
    }

    @Test
    fun setupStatesWhatIsNeededToRestoreBeforeConfirming() {
        showScreen(on.copy(driveBackupDialog = SettingsContract.DriveBackupDialog.SetPassphrase(isChange = false)))

        composeTestRule.onNodeWithText(text(R.string.drive_backup_setup_message)).assertIsDisplayed()
        composeTestRule.onNodeWithText(text(R.string.drive_backup_setup_confirm)).assertExists()
    }

    @Test
    fun restoreIsOfferedEvenWhileBackupIsOff() {
        showScreen(SettingsContract.UiState())

        composeTestRule.onNodeWithText(text(R.string.drive_restore_title)).performClick()

        assertThat(events).containsExactly(Event.RestoreClicked)
    }
}
