package com.mandrecode.tempo.features.backup.domain.usecase

import com.mandrecode.tempo.features.backup.domain.model.DriveBackupFailure
import com.mandrecode.tempo.features.backup.domain.model.backupFileName
import com.mandrecode.tempo.features.backup.domain.repository.BackupPassphraseStore
import com.mandrecode.tempo.features.backup.domain.repository.BackupRepository
import com.mandrecode.tempo.features.backup.domain.repository.CloudAuthorization
import com.mandrecode.tempo.features.backup.domain.repository.CloudAuthorizationResult
import com.mandrecode.tempo.features.backup.domain.repository.CloudBackupException
import com.mandrecode.tempo.features.backup.domain.repository.CloudBackupStore
import com.mandrecode.tempo.features.backup.domain.repository.DriveBackupPreferences
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.withLock
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Clock

/**
 * Takes one Drive backup (#306): the encrypted backup file, written with the stored backup
 * passphrase (#305), uploaded to the app's Drive folder, then older backups past [RETENTION]
 * pruned. Every outcome is recorded, so Settings can say honestly when the last backup worked and
 * why the last one did not.
 *
 * One run at a time: the periodic and "back up now" work are separate WorkManager jobs that can
 * start together, and two runs would upload twice and prune each other's view of the folder.
 * [DriveBackupLock] serialises them, and a restore with them.
 */
@Singleton
class RunDriveBackupUseCase
    @Inject
    constructor(
        private val backupRepository: BackupRepository,
        private val passphraseStore: BackupPassphraseStore,
        private val preferences: DriveBackupPreferences,
        private val cloudStore: CloudBackupStore,
        private val authorization: CloudAuthorization,
        private val clock: Clock,
        private val lock: DriveBackupLock = DriveBackupLock(),
    ) {
        sealed interface Result {
            data object Succeeded : Result

            /** Backup is off: nothing ran. */
            data object Skipped : Result

            data class Failed(
                val failure: DriveBackupFailure,
            ) : Result
        }

        /**
         * [onRunning] hears true when this run really starts backing up, after any run ahead of it
         * and only if backup is on, and false when it stops. Both inside the lock, so whatever it
         * shows (a progress notification) belongs to the run doing the work, never to one waiting.
         */
        suspend operator fun invoke(onRunning: (Boolean) -> Unit = {}): Result =
            lock.withLock {
                if (!preferences.settings.value.enabled) return@withLock Result.Skipped
                onRunning(true)
                try {
                    runOnce()
                } finally {
                    onRunning(false)
                }
            }

        private suspend fun runOnce(): Result {
            val result: Result =
                try {
                    backUp()
                    Result.Succeeded
                } catch (e: CancellationException) {
                    throw e
                } catch (e: CloudBackupException) {
                    Result.Failed(e.failure)
                } catch (_: IOException) {
                    Result.Failed(DriveBackupFailure.NETWORK)
                } catch (_: Exception) {
                    Result.Failed(DriveBackupFailure.UNEXPECTED)
                }
            // Turned off while this run was working: its outcome is about backups that are off now,
            // so nothing is recorded and nothing is notified.
            return if (preferences.settings.value.enabled) record(result) else Result.Skipped
        }

        private fun record(result: Result): Result {
            val now = clock.now().toEpochMilliseconds()
            when (result) {
                is Result.Failed -> preferences.recordFailure(result.failure, now)
                Result.Succeeded -> preferences.recordSuccess(now)
                Result.Skipped -> Unit
            }
            return result
        }

        private suspend fun backUp() {
            val token =
                when (val auth = authorization.authorize()) {
                    is CloudAuthorizationResult.Authorized -> auth.accessToken
                    CloudAuthorizationResult.NeedsUserConsent ->
                        throw CloudBackupException(DriveBackupFailure.AUTHORIZATION_NEEDED)
                }
            val passphrase =
                passphraseStore.read() ?: throw CloudBackupException(DriveBackupFailure.PASSPHRASE_UNAVAILABLE)
            val content =
                try {
                    backupRepository.exportEncrypted(passphrase)
                } finally {
                    passphrase.fill('\u0000')
                }
            val now = clock.now().toEpochMilliseconds()
            cloudStore.upload(token, backupFileName(preferences.installId, now), content)
            prune(token)
        }

        /**
         * Keeps this install's newest [RETENTION] backups. Other devices on the same account share
         * the folder and keep their own. Failing to prune never fails the backup itself.
         */
        private suspend fun prune(token: String) {
            try {
                cloudStore
                    .list(token)
                    .filter { it.isFrom(preferences.installId) }
                    .sortedByDescending { it.createdAt }
                    .drop(RETENTION)
                    .forEach { cloudStore.delete(token, it.id) }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // The next successful backup prunes again.
            }
        }

        companion object {
            /** How many backups are kept in Drive. */
            const val RETENTION = 5
        }
    }
