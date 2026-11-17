package com.mandrecode.tempo.features.backup.domain.usecase

import com.mandrecode.tempo.features.backup.domain.model.BackupFrequency
import com.mandrecode.tempo.features.backup.domain.repository.BackupPassphraseStore
import com.mandrecode.tempo.features.backup.domain.repository.DriveBackupPreferences
import com.mandrecode.tempo.features.backup.domain.scheduler.DriveBackupScheduler
import javax.inject.Inject

/** Outcome of checking a passphrase typed twice. */
enum class PassphraseCheck {
    OK,
    EMPTY,
    MISMATCH,
}

/** The same rule as manual export: not empty, and both entries equal. */
fun checkPassphrase(
    passphrase: CharArray,
    confirmation: CharArray,
): PassphraseCheck =
    when {
        passphrase.isEmpty() -> PassphraseCheck.EMPTY
        !passphrase.contentEquals(confirmation) -> PassphraseCheck.MISMATCH
        else -> PassphraseCheck.OK
    }

/**
 * Turns Drive backup on (#306): stores the backup passphrase (#305), schedules backups and takes
 * the first one now. Drive authorisation is granted beforehand, interactively, by the caller.
 * Both arrays are zeroed before returning.
 */
class EnableDriveBackupUseCase
    @Inject
    constructor(
        private val passphraseStore: BackupPassphraseStore,
        private val preferences: DriveBackupPreferences,
        private val scheduler: DriveBackupScheduler,
    ) {
        operator fun invoke(
            passphrase: CharArray,
            confirmation: CharArray,
        ): PassphraseCheck =
            try {
                checkPassphrase(passphrase, confirmation).also { check ->
                    if (check == PassphraseCheck.OK) {
                        passphraseStore.save(passphrase)
                        val settings = preferences.settings.value.copy(enabled = true)
                        preferences.updateSettings(settings)
                        scheduler.sync(settings)
                        scheduler.runNow(settings)
                    }
                }
            } finally {
                passphrase.fill('\u0000')
                confirmation.fill('\u0000')
            }
    }

/**
 * Replaces the backup passphrase (#305): later backups use it, and one is taken now so the newest
 * backup already does. Also how a passphrase that can no longer be read is set again.
 */
class ChangeBackupPassphraseUseCase
    @Inject
    constructor(
        private val passphraseStore: BackupPassphraseStore,
        private val preferences: DriveBackupPreferences,
        private val scheduler: DriveBackupScheduler,
    ) {
        operator fun invoke(
            passphrase: CharArray,
            confirmation: CharArray,
        ): PassphraseCheck =
            try {
                checkPassphrase(passphrase, confirmation).also { check ->
                    if (check == PassphraseCheck.OK) {
                        passphraseStore.save(passphrase)
                        scheduler.runNow(preferences.settings.value)
                    }
                }
            } finally {
                passphrase.fill('\u0000')
                confirmation.fill('\u0000')
            }
    }

/**
 * Turns Drive backup off (#306): no more backups, and the passphrase is forgotten on this device.
 * Backups already in Drive stay there.
 */
class DisableDriveBackupUseCase
    @Inject
    constructor(
        private val passphraseStore: BackupPassphraseStore,
        private val preferences: DriveBackupPreferences,
        private val scheduler: DriveBackupScheduler,
    ) {
        operator fun invoke() {
            val settings = preferences.settings.value.copy(enabled = false)
            preferences.updateSettings(settings)
            scheduler.sync(settings)
            passphraseStore.clear()
        }
    }

/** Changes how often and on which networks Drive backups run (#306). */
class UpdateDriveBackupScheduleUseCase
    @Inject
    constructor(
        private val preferences: DriveBackupPreferences,
        private val scheduler: DriveBackupScheduler,
    ) {
        operator fun invoke(
            frequency: BackupFrequency = preferences.settings.value.frequency,
            unmeteredOnly: Boolean = preferences.settings.value.unmeteredOnly,
        ) {
            val settings = preferences.settings.value.copy(frequency = frequency, unmeteredOnly = unmeteredOnly)
            preferences.updateSettings(settings)
            scheduler.sync(settings)
        }
    }

/** Runs one Drive backup now, from Settings (#306). */
class RunDriveBackupNowUseCase
    @Inject
    constructor(
        private val preferences: DriveBackupPreferences,
        private val scheduler: DriveBackupScheduler,
    ) {
        operator fun invoke() {
            scheduler.runNow(preferences.settings.value)
        }
    }
