package com.mandrecode.tempo.features.backup.presentation

import android.app.PendingIntent
import android.content.Intent
import com.mandrecode.tempo.core.di.IoDispatcher
import com.mandrecode.tempo.features.backup.domain.model.CloudBackupFile
import com.mandrecode.tempo.features.backup.domain.model.DriveBackupFailure
import com.mandrecode.tempo.features.backup.domain.model.ImportMode
import com.mandrecode.tempo.features.backup.domain.repository.CloudBackupException
import com.mandrecode.tempo.features.backup.domain.repository.DriveBackupPreferences
import com.mandrecode.tempo.features.backup.domain.usecase.EnableDriveBackupUseCase
import com.mandrecode.tempo.features.backup.domain.usecase.HasLocalDataUseCase
import com.mandrecode.tempo.features.backup.domain.usecase.ListDriveBackupsUseCase
import com.mandrecode.tempo.features.backup.domain.usecase.RestoreDriveBackupUseCase
import com.mandrecode.tempo.features.backup.domain.usecase.RestoreOutcome
import com.mandrecode.tempo.infrastructure.backup.drive.GoogleDriveAuthorization
import com.mandrecode.tempo.infrastructure.backup.drive.GoogleDriveAuthorization.ConsentOutcome
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/** Where a restore from Google Drive is (#307). Null in a screen's state when none is under way. */
sealed interface DriveRestoreStep {
    /** Connecting to Drive and looking for backups. */
    data object Searching : DriveRestoreStep

    /** The newest backup, offered with its date and size. */
    data class Offer(
        val backup: CloudBackupFile,
    ) : DriveRestoreStep

    data object NoBackups : DriveRestoreStep

    /** This device already has data: merge into it, or replace it. */
    data class ChooseMode(
        val backup: CloudBackupFile,
    ) : DriveRestoreStep

    data class EnterPassphrase(
        val backup: CloudBackupFile,
        val mode: ImportMode,
        val previousAttemptWrong: Boolean = false,
    ) : DriveRestoreStep

    data object Restoring : DriveRestoreStep

    /** Done. [offerToKeepBackingUp] when Drive backup is not on yet on this device. */
    data class Restored(
        val imported: Int,
        val offerToKeepBackingUp: Boolean,
    ) : DriveRestoreStep

    data class Failed(
        val reason: DriveRestoreFailure,
    ) : DriveRestoreStep
}

/** Why a restore could not happen, each with its own explanation (#307). */
sealed interface DriveRestoreFailure {
    data class Cloud(
        val failure: DriveBackupFailure,
    ) : DriveRestoreFailure

    /** Play services could not authorise at all (no connection, outdated, not configured). */
    data object Unavailable : DriveRestoreFailure

    data object Corrupt : DriveRestoreFailure

    data class NewerVersion(
        val fileVersion: Int,
        val maxSupported: Int,
    ) : DriveRestoreFailure

    data object Invalid : DriveRestoreFailure
}

sealed interface DriveRestoreEvent {
    /** The consent screen returned; [data] is its result intent, null if cancelled. */
    data class ConsentFinished(
        val data: Intent?,
    ) : DriveRestoreEvent

    data object RestoreClicked : DriveRestoreEvent

    data class ModeChosen(
        val mode: ImportMode,
    ) : DriveRestoreEvent

    data class PassphraseEntered(
        val passphrase: CharArray,
    ) : DriveRestoreEvent

    data class KeepBackingUpChosen(
        val keep: Boolean,
    ) : DriveRestoreEvent

    data object Dismissed : DriveRestoreEvent
}

/**
 * Restoring a Google Drive backup (#307), shared by onboarding and Settings: connect, find the
 * newest backup, ask merge-or-replace only when this device already has data, ask the backup
 * passphrase, restore, and offer to keep backing up with that same passphrase (#305).
 */
class DriveRestoreFlow
    @Inject
    constructor(
        private val authorization: GoogleDriveAuthorization,
        private val listBackups: ListDriveBackupsUseCase,
        private val restore: RestoreDriveBackupUseCase,
        private val hasLocalData: HasLocalDataUseCase,
        private val enableBackup: EnableDriveBackupUseCase,
        private val backupPreferences: DriveBackupPreferences,
        @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    ) {
        /** What the flow needs from the screen hosting it. */
        class Host(
            val scope: CoroutineScope,
            val showStep: (DriveRestoreStep?) -> Unit,
            val launchConsent: (PendingIntent) -> Unit,
        )

        private var step: DriveRestoreStep? = null

        /** The passphrase that just restored, kept only until the user answers the keep-backing-up offer. */
        private var restoredWith: CharArray? = null

        /** True while "Keep backing up" is storing the passphrase; the flow must not close under it. */
        private var keeping = false

        fun start(host: Host) {
            show(DriveRestoreStep.Searching, host)
            host.scope.launch {
                try {
                    val consent = authorization.consentIntent()
                    if (consent == null) findBackups(host) else host.launchConsent(consent)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: CloudBackupException) {
                    show(failure(e.failure), host)
                }
            }
        }

        fun onEvent(
            event: DriveRestoreEvent,
            host: Host,
        ) {
            val current = step
            when (event) {
                is DriveRestoreEvent.ConsentFinished -> onConsentFinished(event, host)
                DriveRestoreEvent.RestoreClicked ->
                    if (current is DriveRestoreStep.Offer) {
                        host.scope.launch {
                            show(
                                if (hasLocalData()) {
                                    DriveRestoreStep.ChooseMode(current.backup)
                                } else {
                                    DriveRestoreStep.EnterPassphrase(current.backup, ImportMode.REPLACE)
                                },
                                host,
                            )
                        }
                    }
                is DriveRestoreEvent.ModeChosen ->
                    if (current is DriveRestoreStep.ChooseMode) {
                        show(DriveRestoreStep.EnterPassphrase(current.backup, event.mode), host)
                    }
                is DriveRestoreEvent.PassphraseEntered ->
                    if (current is DriveRestoreStep.EnterPassphrase) {
                        restore(current, event.passphrase, host)
                    } else {
                        event.passphrase.fill('\u0000')
                    }
                is DriveRestoreEvent.KeepBackingUpChosen -> keepBackingUp(event.keep, host)
                // Closing mid-save could end the host (onboarding finishes when the flow closes) and cancel it.
                DriveRestoreEvent.Dismissed -> if (!keeping) show(null, host)
            }
        }

        /**
         * Stores the passphrase that just restored, so backups continue with it (#305). Off the main
         * thread, since it creates a Keystore key and commits to disk, and the flow closes only once
         * it is done: a host may finish as soon as the flow closes. A Keystore that cannot keep it
         * leaves backup off, which Settings shows; the restore itself already succeeded.
         */
        private fun keepBackingUp(
            keep: Boolean,
            host: Host,
        ) {
            if (keeping) return
            val passphrase = restoredWith
            if (!keep || passphrase == null) {
                show(null, host)
                return
            }
            keeping = true
            host.scope.launch {
                try {
                    withContext(ioDispatcher) {
                        try {
                            enableBackup(passphrase.copyOf(), passphrase.copyOf())
                        } catch (e: CancellationException) {
                            throw e
                        } catch (_: Exception) {
                            // Backup stays off; see above.
                        }
                    }
                } finally {
                    keeping = false
                    // Wiped here too: a cancelled host never reaches show(null) below.
                    restoredWith?.fill('\u0000')
                    restoredWith = null
                }
                show(null, host)
            }
        }

        private fun onConsentFinished(
            event: DriveRestoreEvent.ConsentFinished,
            host: Host,
        ) {
            when (authorization.consentOutcome(event.data)) {
                ConsentOutcome.GRANTED -> host.scope.launch { findBackups(host) }
                ConsentOutcome.CANCELLED -> show(null, host)
                // An account was picked but Google refused: a terminal answer, not silence.
                ConsentOutcome.FAILED -> show(DriveRestoreStep.Failed(DriveRestoreFailure.Unavailable), host)
            }
        }

        private suspend fun findBackups(host: Host) {
            show(DriveRestoreStep.Searching, host)
            val next =
                when (val result = listBackups()) {
                    is ListDriveBackupsUseCase.Result.Found ->
                        result.backups.firstOrNull()?.let { DriveRestoreStep.Offer(it) } ?: DriveRestoreStep.NoBackups
                    is ListDriveBackupsUseCase.Result.Failed -> failure(result.failure)
                }
            show(next, host)
        }

        private fun restore(
            step: DriveRestoreStep.EnterPassphrase,
            passphrase: CharArray,
            host: Host,
        ) {
            show(DriveRestoreStep.Restoring, host)
            host.scope.launch {
                val outcome =
                    try {
                        restore(step.backup, step.mode, passphrase)
                    } catch (e: CancellationException) {
                        passphrase.fill('\u0000')
                        throw e
                    } catch (_: Exception) {
                        // A database or scheduler failure mid-import: the import's transaction left the
                        // data as it was, and the user gets a terminal answer instead of a spinner.
                        RestoreOutcome.CloudFailed(DriveBackupFailure.UNEXPECTED)
                    }
                val next =
                    when (outcome) {
                        is RestoreOutcome.Restored -> {
                            val offer = !backupPreferences.settings.value.enabled
                            if (offer) restoredWith = passphrase.copyOf()
                            DriveRestoreStep.Restored(outcome.summary.totalImported, offerToKeepBackingUp = offer)
                        }
                        RestoreOutcome.WrongPassphrase -> step.copy(previousAttemptWrong = true)
                        RestoreOutcome.Corrupt -> DriveRestoreStep.Failed(DriveRestoreFailure.Corrupt)
                        is RestoreOutcome.NewerVersion ->
                            DriveRestoreStep.Failed(
                                DriveRestoreFailure.NewerVersion(outcome.fileVersion, outcome.maxSupported),
                            )
                        RestoreOutcome.Invalid -> DriveRestoreStep.Failed(DriveRestoreFailure.Invalid)
                        is RestoreOutcome.CloudFailed ->
                            DriveRestoreStep.Failed(DriveRestoreFailure.Cloud(outcome.failure))
                    }
                passphrase.fill('\u0000')
                show(next, host)
            }
        }

        private fun failure(failure: DriveBackupFailure) =
            DriveRestoreStep.Failed(
                if (failure == DriveBackupFailure.UNEXPECTED) {
                    DriveRestoreFailure.Unavailable
                } else {
                    DriveRestoreFailure.Cloud(failure)
                },
            )

        private fun show(
            next: DriveRestoreStep?,
            host: Host,
        ) {
            if (next == null || next is DriveRestoreStep.Failed) {
                restoredWith?.fill('\u0000')
                restoredWith = null
            }
            step = next
            host.showStep(next)
        }
    }
