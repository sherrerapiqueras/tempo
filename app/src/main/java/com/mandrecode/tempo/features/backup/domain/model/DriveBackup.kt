package com.mandrecode.tempo.features.backup.domain.model

/** How often scheduled Drive backups run (#306). */
enum class BackupFrequency(
    val days: Int,
) {
    DAILY(days = 1),
    WEEKLY(days = DAYS_IN_WEEK),
}

private const val DAYS_IN_WEEK = 7

/** The user's Drive backup configuration (#306). */
data class DriveBackupSettings(
    val enabled: Boolean = false,
    val frequency: BackupFrequency = BackupFrequency.DAILY,
    /** Back up only on unmetered networks (Wi-Fi), so a backup never uses mobile data. */
    val unmeteredOnly: Boolean = true,
)

/** Why a Drive backup did not complete. Each cause has its own message and action. */
enum class DriveBackupFailure {
    /** No connection or a network error. Retried automatically. */
    NETWORK,

    /** Google Drive authorisation was revoked or expired: the user must reconnect. */
    AUTHORIZATION_NEEDED,

    /** The user's Google storage is full. */
    STORAGE_FULL,

    /** The stored backup passphrase can no longer be read on this device: it must be set again. */
    PASSPHRASE_UNAVAILABLE,

    /** Anything else. */
    UNEXPECTED,
    ;

    /** Whether the user has to act for backups to work again, which warrants a notification. */
    val needsUser: Boolean
        get() = this == AUTHORIZATION_NEEDED || this == STORAGE_FULL || this == PASSPHRASE_UNAVAILABLE
}

/**
 * What is known about recent backups (#306). Times are epoch milliseconds, null when it never
 * happened. A failure matters only while it is newer than the last success, see [currentFailure].
 */
data class DriveBackupStatus(
    val lastSuccessAt: Long? = null,
    val lastFailureAt: Long? = null,
    val lastFailure: DriveBackupFailure? = null,
) {
    /** The failure to show, or null when the last attempt succeeded or nothing failed yet. */
    val currentFailure: DriveBackupFailure?
        get() =
            lastFailure?.takeIf { lastFailureAt != null && (lastSuccessAt == null || lastFailureAt > lastSuccessAt) }
}

/** A backup file stored in the cloud (#306, #307). */
data class CloudBackupFile(
    val id: String,
    val name: String,
    /** Epoch milliseconds of creation. */
    val createdAt: Long,
    val sizeBytes: Long,
)
