package com.mandrecode.tempo.features.settings.presentation

import com.mandrecode.tempo.R
import com.mandrecode.tempo.features.backup.domain.repository.CloudBackupException
import com.mandrecode.tempo.features.backup.domain.repository.DriveBackupPreferences
import com.mandrecode.tempo.features.backup.domain.usecase.ChangeBackupPassphraseUseCase
import com.mandrecode.tempo.features.backup.domain.usecase.DisableDriveBackupUseCase
import com.mandrecode.tempo.features.backup.domain.usecase.EnableDriveBackupUseCase
import com.mandrecode.tempo.features.backup.domain.usecase.PassphraseCheck
import com.mandrecode.tempo.features.backup.domain.usecase.RunDriveBackupNowUseCase
import com.mandrecode.tempo.features.backup.domain.usecase.UpdateDriveBackupScheduleUseCase
import com.mandrecode.tempo.infrastructure.backup.drive.GoogleDriveAuthorization
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject
import com.mandrecode.tempo.features.settings.presentation.SettingsContract.DriveBackupDialog as Dialog
import com.mandrecode.tempo.features.settings.presentation.SettingsContract.UiEvent.DriveBackup as Event

/**
 * Owns the Google Drive backup section of Settings (#306) for [SettingsViewModel]: mirrors the
 * stored settings and status into state, runs the consent → passphrase setup, and the rest of
 * the section's actions through their use cases.
 */
class SettingsDriveBackupDelegate
    @Inject
    constructor(
        private val authorization: GoogleDriveAuthorization,
        private val preferences: DriveBackupPreferences,
        private val enable: EnableDriveBackupUseCase,
        private val changePassphrase: ChangeBackupPassphraseUseCase,
        private val disable: DisableDriveBackupUseCase,
        private val updateSchedule: UpdateDriveBackupScheduleUseCase,
        private val backUpNow: RunDriveBackupNowUseCase,
    ) {
        /** What the consent being shown was asked for. */
        private enum class ConsentPurpose { TURN_ON, RECONNECT }

        private var pendingConsent: ConsentPurpose? = null

        /** The dialog this delegate last showed; every change to it goes through [showDialog]. */
        private var dialog: Dialog? = null

        /** Keeps the section in step with the stored settings and backup status. */
        fun start(host: SettingsBackupDelegate.Host) {
            host.scope.launch {
                combine(preferences.settings, preferences.status) { settings, status ->
                    SettingsContract.DriveBackupUiState(
                        enabled = settings.enabled,
                        frequency = settings.frequency,
                        unmeteredOnly = settings.unmeteredOnly,
                        lastSuccessAt = status.lastSuccessAt,
                        failure = status.currentFailure,
                    )
                }.collect { drive -> host.updateState { it.copy(driveBackup = drive) } }
            }
        }

        fun onEvent(
            event: Event,
            host: SettingsBackupDelegate.Host,
        ) {
            when (event) {
                Event.TurnOnClicked -> requestConsent(ConsentPurpose.TURN_ON, host)
                Event.ReconnectClicked -> requestConsent(ConsentPurpose.RECONNECT, host)
                is Event.ConsentFinished ->
                    if (authorization.isGranted(event.data)) onConsentGranted(host) else pendingConsent = null
                is Event.PassphraseConfirmed -> confirmPassphrase(event, host)
                Event.FrequencyClicked -> showDialog(Dialog.ChooseFrequency, host)
                is Event.FrequencySelected -> {
                    updateSchedule(frequency = event.frequency)
                    showDialog(null, host)
                }
                is Event.WifiOnlyToggled -> updateSchedule(unmeteredOnly = event.enabled)
                Event.BackUpNowClicked -> {
                    backUpNow()
                    host.sendEffect(SettingsContract.UiEffect.ShowMessage(R.string.drive_backup_now_started))
                }
                Event.ChangePassphraseClicked -> showDialog(Dialog.SetPassphrase(isChange = true), host)
                Event.TurnOffClicked -> showDialog(Dialog.ConfirmTurnOff, host)
                Event.TurnOffConfirmed -> {
                    disable()
                    showDialog(null, host)
                }
                Event.DialogDismissed -> showDialog(null, host)
            }
        }

        private fun requestConsent(
            purpose: ConsentPurpose,
            host: SettingsBackupDelegate.Host,
        ) {
            pendingConsent = purpose
            host.scope.launch {
                try {
                    val consent = authorization.consentIntent()
                    if (consent == null) {
                        onConsentGranted(host)
                    } else {
                        host.sendEffect(SettingsContract.UiEffect.LaunchDriveConsent(consent))
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (_: CloudBackupException) {
                    pendingConsent = null
                    showDialog(Dialog.Unavailable, host)
                }
            }
        }

        private fun onConsentGranted(host: SettingsBackupDelegate.Host) {
            when (pendingConsent) {
                ConsentPurpose.TURN_ON -> showDialog(Dialog.SetPassphrase(isChange = false), host)
                ConsentPurpose.RECONNECT -> {
                    backUpNow()
                    host.sendEffect(SettingsContract.UiEffect.ShowMessage(R.string.drive_backup_now_started))
                }
                null -> Unit
            }
            pendingConsent = null
        }

        private fun confirmPassphrase(
            event: Event.PassphraseConfirmed,
            host: SettingsBackupDelegate.Host,
        ) {
            val setPassphrase = dialog as? Dialog.SetPassphrase ?: return
            val check =
                if (setPassphrase.isChange) {
                    changePassphrase(event.passphrase, event.confirmation)
                } else {
                    enable(event.passphrase, event.confirmation)
                }
            if (check == PassphraseCheck.OK) {
                showDialog(null, host)
                host.sendEffect(SettingsContract.UiEffect.ShowMessage(R.string.drive_backup_now_started))
            }
        }

        private fun showDialog(
            dialog: Dialog?,
            host: SettingsBackupDelegate.Host,
        ) {
            this.dialog = dialog
            host.updateState { it.copy(driveBackupDialog = dialog) }
        }
    }
