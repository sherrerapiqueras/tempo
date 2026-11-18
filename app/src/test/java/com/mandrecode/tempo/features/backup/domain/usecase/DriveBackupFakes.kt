package com.mandrecode.tempo.features.backup.domain.usecase

import com.mandrecode.tempo.features.backup.domain.model.CloudBackupFile
import com.mandrecode.tempo.features.backup.domain.model.DriveBackupFailure
import com.mandrecode.tempo.features.backup.domain.model.DriveBackupSettings
import com.mandrecode.tempo.features.backup.domain.model.DriveBackupStatus
import com.mandrecode.tempo.features.backup.domain.repository.BackupPassphraseStore
import com.mandrecode.tempo.features.backup.domain.repository.CloudAuthorization
import com.mandrecode.tempo.features.backup.domain.repository.CloudAuthorizationResult
import com.mandrecode.tempo.features.backup.domain.repository.CloudBackupStore
import com.mandrecode.tempo.features.backup.domain.repository.DriveBackupPreferences
import com.mandrecode.tempo.features.backup.domain.scheduler.DriveBackupScheduler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.time.Clock
import kotlin.time.Instant

class FakePassphraseStore(
    var stored: String? = null,
) : BackupPassphraseStore {
    override fun save(passphrase: CharArray) {
        stored = String(passphrase)
    }

    override fun read(): CharArray? = stored?.toCharArray()

    override fun clear() {
        stored = null
    }
}

class FakeDrivePreferences(
    settings: DriveBackupSettings = DriveBackupSettings(),
    override val installId: String = "this-install",
) : DriveBackupPreferences {
    override val settings = MutableStateFlow(settings)
    override val status = MutableStateFlow(DriveBackupStatus())

    override fun updateSettings(settings: DriveBackupSettings) {
        this.settings.value = settings
    }

    override fun recordSuccess(atMillis: Long) {
        status.value = status.value.copy(lastSuccessAt = atMillis)
    }

    override fun replaceLastSuccess(atMillis: Long?) {
        status.value = status.value.copy(lastSuccessAt = atMillis)
    }

    override fun clearFailure() {
        status.value = status.value.copy(lastFailure = null, lastFailureAt = null)
    }

    override fun recordFailure(
        failure: DriveBackupFailure,
        atMillis: Long,
    ) {
        status.value = status.value.copy(lastFailure = failure, lastFailureAt = atMillis)
    }
}

class FakeCloudStore : CloudBackupStore {
    val files = mutableListOf<CloudBackupFile>()
    val contents = mutableMapOf<String, String>()
    var failUploadWith: Exception? = null
    var failListWith: Exception? = null
    private var nextId = 0

    override suspend fun list(accessToken: String): List<CloudBackupFile> {
        failListWith?.let { throw it }
        return files.sortedByDescending { it.createdAt }
    }

    override suspend fun upload(
        accessToken: String,
        name: String,
        content: String,
    ): CloudBackupFile {
        failUploadWith?.let { throw it }
        val createdAt = name.removeSuffix(".tempo").substringAfterLast('-').toLong()
        val file =
            CloudBackupFile(
                id = "f${nextId++}",
                name = name,
                createdAt = createdAt,
                sizeBytes = content.length.toLong(),
            )
        files += file
        contents[file.id] = content
        return file
    }

    override suspend fun download(
        accessToken: String,
        fileId: String,
    ): String = contents.getValue(fileId)

    override suspend fun delete(
        accessToken: String,
        fileId: String,
    ) {
        files.removeAll { it.id == fileId }
        contents.remove(fileId)
    }
}

class FakeAuthorization(
    var result: CloudAuthorizationResult = CloudAuthorizationResult.Authorized("token"),
) : CloudAuthorization {
    override suspend fun authorize(): CloudAuthorizationResult = result
}

class FakeScheduler : DriveBackupScheduler {
    val synced = mutableListOf<DriveBackupSettings>()
    var runNowCalls = 0

    /** Thrown by [sync] for settings that turn backup on, like WorkManager refusing the work. */
    var failEnablingWith: Exception? = null
    var failRunNowWith: Exception? = null

    override fun sync(settings: DriveBackupSettings) {
        if (settings.enabled) failEnablingWith?.let { throw it }
        synced += settings
    }

    override fun runNow(settings: DriveBackupSettings) {
        failRunNowWith?.let { throw it }
        runNowCalls++
    }
}

class SteppingClock(
    var millis: Long = 1_000L,
) : Clock {
    override fun now(): Instant = Instant.fromEpochMilliseconds(millis++)
}

val StateFlow<DriveBackupStatus>.failure get() = value.currentFailure
