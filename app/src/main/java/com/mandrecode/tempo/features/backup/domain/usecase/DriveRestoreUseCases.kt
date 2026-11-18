package com.mandrecode.tempo.features.backup.domain.usecase

import com.mandrecode.tempo.features.backup.domain.model.CloudBackupFile
import com.mandrecode.tempo.features.backup.domain.model.DriveBackupFailure
import com.mandrecode.tempo.features.backup.domain.model.ImportMode
import com.mandrecode.tempo.features.backup.domain.model.ImportOutcome
import com.mandrecode.tempo.features.backup.domain.model.ImportSummary
import com.mandrecode.tempo.features.backup.domain.repository.BackupRepository
import com.mandrecode.tempo.features.backup.domain.repository.CloudAuthorization
import com.mandrecode.tempo.features.backup.domain.repository.CloudAuthorizationResult
import com.mandrecode.tempo.features.backup.domain.repository.CloudBackupException
import com.mandrecode.tempo.features.backup.domain.repository.CloudBackupStore
import kotlinx.coroutines.CancellationException
import java.io.IOException
import javax.inject.Inject

/** Gets a Drive access token, or throws the [CloudBackupException] a user can act on. */
private suspend fun CloudAuthorization.requireToken(): String =
    when (val result = authorize()) {
        is CloudAuthorizationResult.Authorized -> result.accessToken
        CloudAuthorizationResult.NeedsUserConsent -> throw CloudBackupException(DriveBackupFailure.AUTHORIZATION_NEEDED)
    }

/**
 * Runs [block], turning every failure but cancellation into a [CloudBackupException] with its cause.
 * Deliberately broad: whatever goes wrong, a restore must reach a terminal answer.
 */
@Suppress("TooGenericExceptionCaught")
private suspend fun <T> cloudCall(block: suspend () -> T): Result<T> =
    try {
        Result.success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: CloudBackupException) {
        Result.failure(e)
    } catch (e: IOException) {
        Result.failure(CloudBackupException(DriveBackupFailure.NETWORK, e))
    } catch (e: Exception) {
        // Anything else, such as a response that doesn't parse: still a terminal answer, never a hang.
        Result.failure(CloudBackupException(DriveBackupFailure.UNEXPECTED, e))
    }

/** The backups this Google account has in Drive (#307), newest first. */
class ListDriveBackupsUseCase
    @Inject
    constructor(
        private val authorization: CloudAuthorization,
        private val cloudStore: CloudBackupStore,
    ) {
        sealed interface Result {
            data class Found(
                val backups: List<CloudBackupFile>,
            ) : Result

            data class Failed(
                val failure: DriveBackupFailure,
            ) : Result
        }

        suspend operator fun invoke(): Result =
            cloudCall { cloudStore.list(authorization.requireToken()).sortedByDescending { it.createdAt } }
                .fold(
                    onSuccess = { Result.Found(it) },
                    onFailure = { Result.Failed((it as CloudBackupException).failure) },
                )
    }

/** How a restore ended (#307). Each case has its own explanation and next step. */
sealed interface RestoreOutcome {
    data class Restored(
        val summary: ImportSummary,
    ) : RestoreOutcome

    data object WrongPassphrase : RestoreOutcome

    data object Corrupt : RestoreOutcome

    data class NewerVersion(
        val fileVersion: Int,
        val maxSupported: Int,
    ) : RestoreOutcome

    data object Invalid : RestoreOutcome

    data class CloudFailed(
        val failure: DriveBackupFailure,
    ) : RestoreOutcome
}

/**
 * Restores a Drive backup (#307): downloads it and imports it like any backup file, so the data
 * is validated before anything is written, written in one transaction, and reminders are
 * rescheduled. Any outcome but [RestoreOutcome.Restored] leaves local data untouched.
 */
class RestoreDriveBackupUseCase
    @Inject
    constructor(
        private val authorization: CloudAuthorization,
        private val cloudStore: CloudBackupStore,
        private val importBackup: ImportBackupUseCase,
        private val lock: DriveBackupLock = DriveBackupLock(),
    ) {
        suspend operator fun invoke(
            backup: CloudBackupFile,
            mode: ImportMode,
            passphrase: CharArray,
        ): RestoreOutcome {
            val content =
                cloudCall { cloudStore.download(authorization.requireToken(), backup.id) }
                    .getOrElse { return RestoreOutcome.CloudFailed((it as CloudBackupException).failure) }
            // Not while a backup runs: one that read the database before this import committed would
            // then upload the old data as the newest backup. Downloading can overlap; writing can't.
            return when (val outcome = lock.withLock { importBackup(content, mode, passphrase) }) {
                is ImportOutcome.Success -> RestoreOutcome.Restored(outcome.summary)
                ImportOutcome.WrongPassphrase -> RestoreOutcome.WrongPassphrase
                ImportOutcome.CorruptFile -> RestoreOutcome.Corrupt
                is ImportOutcome.UnsupportedVersion ->
                    RestoreOutcome.NewerVersion(outcome.fileVersion, outcome.maxSupported)
                is ImportOutcome.ValidationFailed -> RestoreOutcome.Invalid
            }
        }
    }

/**
 * Whether this device already holds the user's own data (#307): any task, habit or chain, or a
 * category beyond the seeded default. A restore onto such a device is a merge decision, never a
 * silent replace.
 */
class HasLocalDataUseCase
    @Inject
    constructor(
        private val backupRepository: BackupRepository,
    ) {
        suspend operator fun invoke(): Boolean = backupRepository.hasUserData()
    }
