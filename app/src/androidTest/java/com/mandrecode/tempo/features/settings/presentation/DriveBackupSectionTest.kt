package com.mandrecode.tempo.features.settings.presentation

import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
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

class DriveBackupSectionTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private val events = mutableListOf<SettingsContract.UiEvent>()

    private fun text(id: Int) = InstrumentationRegistry.getInstrumentation().targetContext.getString(id)

    private fun show(state: SettingsContract.UiState) {
        composeTestRule.setContent {
            TempoTheme {
                Column { DriveBackupSection(uiState = state, onEvent = { events += it }) }
            }
        }
    }

    private val on = SettingsContract.UiState(driveBackup = SettingsContract.DriveBackupUiState(enabled = true))

    @Test
    fun offOffersToTurnOnAndNothingElse() {
        show(SettingsContract.UiState())

        composeTestRule.onNodeWithText(text(R.string.drive_backup_turn_on_title)).performClick()

        composeTestRule.onNodeWithText(text(R.string.drive_backup_now)).assertDoesNotExist()
        assertThat(events).containsExactly(Event.TurnOnClicked)
    }

    @Test
    fun onWithNoBackupYetSaysNever() {
        show(on)

        composeTestRule.onNodeWithText(text(R.string.drive_backup_last_backup)).assertIsDisplayed()
        composeTestRule.onNodeWithText(text(R.string.drive_backup_never)).assertIsDisplayed()
        composeTestRule.onNodeWithText(text(R.string.drive_backup_turn_on_title)).assertDoesNotExist()
    }

    @Test
    fun lostAuthorisationShowsWhyAndOffersToReconnect() {
        show(on.copy(driveBackup = on.driveBackup.copy(failure = DriveBackupFailure.AUTHORIZATION_NEEDED)))

        composeTestRule.onNodeWithText(text(R.string.drive_backup_failure_authorization)).assertIsDisplayed()
        composeTestRule.onNodeWithText(text(R.string.drive_backup_reconnect)).performClick()

        assertThat(events).containsExactly(Event.ReconnectClicked)
    }

    @Test
    fun anUnreadablePassphraseOffersToSetItAgain() {
        show(on.copy(driveBackup = on.driveBackup.copy(failure = DriveBackupFailure.PASSPHRASE_UNAVAILABLE)))

        composeTestRule.onNodeWithText(text(R.string.drive_backup_set_passphrase)).performClick()

        assertThat(events).containsExactly(Event.ChangePassphraseClicked)
    }

    @Test
    fun setupStatesWhatIsNeededToRestoreBeforeConfirming() {
        show(on.copy(driveBackupDialog = SettingsContract.DriveBackupDialog.SetPassphrase(isChange = false)))

        composeTestRule.onNodeWithText(text(R.string.drive_backup_setup_message)).assertIsDisplayed()
        composeTestRule.onNodeWithText(text(R.string.drive_backup_setup_confirm)).assertExists()
    }
}
