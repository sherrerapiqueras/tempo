package com.mandrecode.tempo.features.backup.domain.usecase

import com.mandrecode.tempo.features.backup.domain.model.DriveBackupFailure
import com.mandrecode.tempo.features.backup.domain.repository.BackupPassphraseStore
import com.mandrecode.tempo.features.backup.domain.repository.BackupRepository
import com.mandrecode.tempo.features.backup.domain.repository.CloudAuthorization
import com.mandrecode.tempo.features.backup.domain.repository.CloudAuthorizationResult
import com.mandrecode.tempo.features.backup.domain.repository.CloudBackupException
import com.mandrecode.tempo.features.backup.domain.repository.CloudBackupStore
import com.mandrecode.tempo.features.backup.domain.repository.DriveBackupPreferences
import kotlinx.coroutines.CancellationException
import java.io.IOException
import javax.inject.Inject
import kotlin.time.Clock

/**
 * Takes one Drive backup (#306): the encrypted backup file, written with the stored backup
 * passphrase (#305), uploaded to the app's Drive folder, then older backups past [RETENTION]
 * pruned. Every outcome is recorded, so Settings can say honestly when the last backup worked and
 * why the last one did not.
 */
class RunDriveBackupUseCase
    @Inject
    constructor(
        private val backupRepository: BackupRepository,
        private val passphraseStore: BackupPassphraseStore,
        private val preferences: DriveBackupPreferences,
        private val cloudStore: CloudBackupStore,
        private val authorization: CloudAuthorization,
        private val clock: Clock,
    ) {
        sealed interface Result {
            data object Succeeded : Result

            /** Backup is off: nothing ran. */
            data object Skipped : Result

            data class Failed(
                val failure: DriveBackupFailure,
            ) : Result
        }

        suspend operator fun invoke(): Result {
            if (!preferences.settings.value.enabled) return Result.Skipped
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
            val now = clock.now().toEpochMilliseconds()
            when (result) {
                is Result.Failed -> preferences.recordFailure(result.failure, now)
                Result.Succeeded -> preferences.recordSuccess(now)
                // A run queued before backup was turned off: nothing ran, so nothing is recorded.
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
            cloudStore.upload(token, "$FILE_PREFIX$now$FILE_SUFFIX", content)
            prune(token)
        }

        /** Keeps the newest [RETENTION] backups. Failing to prune never fails the backup itself. */
        private suspend fun prune(token: String) {
            try {
                cloudStore
                    .list(token)
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
            const val FILE_PREFIX = "tempo-backup-"
            const val FILE_SUFFIX = ".tempo"
        }
    }
