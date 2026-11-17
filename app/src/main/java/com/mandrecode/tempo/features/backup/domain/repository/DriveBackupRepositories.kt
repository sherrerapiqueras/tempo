package com.mandrecode.tempo.features.backup.domain.repository

import com.mandrecode.tempo.features.backup.domain.model.CloudBackupFile
import com.mandrecode.tempo.features.backup.domain.model.DriveBackupFailure
import com.mandrecode.tempo.features.backup.domain.model.DriveBackupSettings
import com.mandrecode.tempo.features.backup.domain.model.DriveBackupStatus
import kotlinx.coroutines.flow.StateFlow

/**
 * The backup passphrase (#305), stored on this device only and never included in any backup or
 * device transfer. Callers zero the arrays they get once done with them.
 */
interface BackupPassphraseStore {
    fun save(passphrase: CharArray)

    /** The stored passphrase, or null when none is set or it can no longer be read. */
    fun read(): CharArray?

    fun clear()
}

/** Drive backup settings and the record of recent attempts (#306). */
interface DriveBackupPreferences {
    val settings: StateFlow<DriveBackupSettings>
    val status: StateFlow<DriveBackupStatus>

    fun updateSettings(settings: DriveBackupSettings)

    fun recordSuccess(atMillis: Long)

    fun recordFailure(
        failure: DriveBackupFailure,
        atMillis: Long,
    )
}

/** Thrown by [CloudBackupStore] and [CloudAuthorization] with the cause a user can act on. */
class CloudBackupException(
    val failure: DriveBackupFailure,
    cause: Throwable? = null,
) : Exception(failure.name, cause)

/** Where backups are kept: the app's hidden folder in the user's Google Drive (#304). */
interface CloudBackupStore {
    /** Backups, newest first. */
    suspend fun list(accessToken: String): List<CloudBackupFile>

    suspend fun upload(
        accessToken: String,
        name: String,
        content: String,
    ): CloudBackupFile

    suspend fun download(
        accessToken: String,
        fileId: String,
    ): String

    suspend fun delete(
        accessToken: String,
        fileId: String,
    )
}

/** Outcome of asking for Drive access without showing anything to the user. */
sealed interface CloudAuthorizationResult {
    data class Authorized(
        val accessToken: String,
    ) : CloudAuthorizationResult

    /** The user has to grant (or grant again) access interactively. */
    data object NeedsUserConsent : CloudAuthorizationResult
}

/** Gets a Drive access token without UI, for scheduled work (#304). */
interface CloudAuthorization {
    suspend fun authorize(): CloudAuthorizationResult
}
