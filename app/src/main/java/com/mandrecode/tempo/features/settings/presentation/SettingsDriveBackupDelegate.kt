package com.mandrecode.tempo.features.settings.presentation

import com.mandrecode.tempo.R
import com.mandrecode.tempo.core.di.IoDispatcher
import com.mandrecode.tempo.features.backup.domain.repository.CloudBackupException
import com.mandrecode.tempo.features.backup.domain.repository.DriveBackupPreferences
import com.mandrecode.tempo.features.backup.domain.scheduler.DriveBackupScheduler
import com.mandrecode.tempo.features.backup.domain.usecase.ChangeBackupPassphraseUseCase
import com.mandrecode.tempo.features.backup.domain.usecase.DisableDriveBackupUseCase
import com.mandrecode.tempo.features.backup.domain.usecase.EnableDriveBackupUseCase
import com.mandrecode.tempo.features.backup.domain.usecase.PassphraseCheck
import com.mandrecode.tempo.features.backup.domain.usecase.RefreshDriveBackupStatusUseCase
import com.mandrecode.tempo.features.backup.domain.usecase.RunDriveBackupNowUseCase
import com.mandrecode.tempo.features.backup.domain.usecase.UpdateDriveBackupScheduleUseCase
import com.mandrecode.tempo.infrastructure.backup.drive.GoogleDriveAuthorization
import com.mandrecode.tempo.infrastructure.backup.drive.GoogleDriveAuthorization.ConsentOutcome
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import com.mandrecode.tempo.features.settings.presentation.SettingsContract.DriveBackupDialog as Dialog
import com.mandrecode.tempo.features.settings.presentation.SettingsContract.UiEvent.DriveBackup as Event

/**
 * Owns Google Drive backup in Settings (#306) for [SettingsViewModel], both its row on the main page
 * and its own page: mirrors the stored settings and status into state, runs the consent →
 * passphrase setup, and the page's other actions through their use cases.
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
        private val refreshStatus: RefreshDriveBackupStatusUseCase,
        private val scheduler: DriveBackupScheduler,
        @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    ) {
        /** What the consent being shown was asked for. */
        private enum class ConsentPurpose { TURN_ON, RECONNECT }

        private var pendingConsent: ConsentPurpose? = null

        /**
         * Turning off, on, and changing the passphrase all touch the stored passphrase off the main
         * thread. One at a time, in the order asked: a turn-off still running must not erase the
         * passphrase a quick turn-on has just stored.
         */
        private val passphraseWork = Mutex()

        /** The dialog this delegate last showed; every change to it goes through [showDialog]. */
        private var dialog: Dialog? = null

        /** Whether backup was on at the last update, null before the first one. */
        private var wasEnabled: Boolean? = null

        /** Keeps the section in step with the stored settings and backup status. */
        fun start(host: SettingsBackupDelegate.Host) {
            host.scope.launch {
                combine(preferences.settings, preferences.status, scheduler.running) { settings, status, running ->
                    SettingsContract.DriveBackupUiState(
                        enabled = settings.enabled,
                        frequency = settings.frequency,
                        unmeteredOnly = settings.unmeteredOnly,
                        lastSuccessAt = status.lastSuccessAt,
                        failure = status.currentFailure,
                        running = running,
                    )
                }.collect { drive ->
                    // Turned on just now, here or by a restore's "keep backing up": its alerts, and its
                    // progress, need notification permission, which Android 13+ asks for at runtime.
                    if (drive.enabled && wasEnabled == false) {
                        host.sendEffect(SettingsContract.UiEffect.RequestNotificationPermission)
                    }
                    wasEnabled = drive.enabled
                    host.updateState { it.copy(driveBackup = drive) }
                }
            }
            host.scope.launch { refreshStatus() }
        }

        fun onEvent(
            event: Event,
            host: SettingsBackupDelegate.Host,
        ) {
            when (event) {
                Event.TurnOnClicked -> requestConsent(ConsentPurpose.TURN_ON, host)
                Event.ReconnectClicked -> requestConsent(ConsentPurpose.RECONNECT, host)
                is Event.ConsentFinished -> onConsentFinished(event, host)
                is Event.PassphraseConfirmed -> confirmPassphrase(event, host)
                is Event.FrequencySelected, is Event.WifiOnlyToggled ->
                    onScheduleEvent(event)
                // The page's own progress says it started; no message on top.
                Event.BackUpNowClicked -> backUpNow()
                Event.ChangePassphraseClicked -> showDialog(Dialog.SetPassphrase(isChange = true), host)
                Event.TurnOffClicked -> showDialog(Dialog.ConfirmTurnOff, host)
                Event.TurnOffConfirmed -> {
                    showDialog(null, host)
                    // Off the main thread: forgetting the passphrase deletes a Keystore key and commits to disk.
                    host.scope.launch { passphraseWork.withLock { withContext(ioDispatcher) { disable() } } }
                }
                Event.DialogDismissed -> showDialog(null, host)
                Event.ManageStorageClicked -> host.sendEffect(SettingsContract.UiEffect.OpenGoogleStorage)
            }
        }

        private fun onScheduleEvent(event: Event) {
            when (event) {
                is Event.FrequencySelected -> updateSchedule(frequency = event.frequency)
                is Event.WifiOnlyToggled -> updateSchedule(unmeteredOnly = event.enabled)
                else -> Unit
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

        private fun onConsentFinished(
            event: Event.ConsentFinished,
            host: SettingsBackupDelegate.Host,
        ) {
            when (authorization.consentOutcome(event.data)) {
                ConsentOutcome.GRANTED -> onConsentGranted(host)
                ConsentOutcome.CANCELLED -> pendingConsent = null
                // An account was picked but Google refused (for instance, no OAuth client for this
                // build): say so, rather than returning to Settings as if nothing had been asked.
                ConsentOutcome.FAILED -> {
                    pendingConsent = null
                    showDialog(Dialog.Unavailable, host)
                }
            }
        }

        private fun onConsentGranted(host: SettingsBackupDelegate.Host) {
            when (pendingConsent) {
                ConsentPurpose.TURN_ON -> showDialog(Dialog.SetPassphrase(isChange = false), host)
                ConsentPurpose.RECONNECT -> {
                    // Access is back: the lost-access failure is fixed, whatever the next backup says.
                    preferences.clearFailure()
                    backUpNow()
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
            host.scope.launch {
                // Off the main thread: storing it generates a Keystore key and commits to disk.
                val check =
                    passphraseWork.withLock {
                        withContext(ioDispatcher) {
                            try {
                                if (setPassphrase.isChange) {
                                    changePassphrase(event.passphrase, event.confirmation)
                                } else {
                                    enable(event.passphrase, event.confirmation)
                                }
                            } catch (e: CancellationException) {
                                throw e
                            } catch (_: Exception) {
                                // A Keystore that cannot create or use a key, or a failed write.
                                null
                            }
                        }
                    }
                when (check) {
                    PassphraseCheck.OK -> showDialog(null, host)
                    null -> {
                        showDialog(null, host)
                        host.sendEffect(
                            SettingsContract.UiEffect.ShowMessage(R.string.drive_backup_passphrase_store_failed),
                        )
                    }
                    else -> Unit
                }
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
