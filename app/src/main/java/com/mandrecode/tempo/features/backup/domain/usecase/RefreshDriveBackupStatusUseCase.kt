package com.mandrecode.tempo.features.backup.domain.usecase

import com.mandrecode.tempo.features.backup.domain.model.DriveBackupFailure
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
 * Checks the recorded status against what Drive actually holds (#306), so Settings never claims
 * a backup that is gone. Disconnecting Tempo from Drive deletes its app folder: the last success
 * then becomes the newest backup still there, or none, and lost access is reported. A network
 * failure changes nothing; the next look tries again.
 */
class RefreshDriveBackupStatusUseCase
    @Inject
    constructor(
        private val preferences: DriveBackupPreferences,
        private val authorization: CloudAuthorization,
        private val cloudStore: CloudBackupStore,
        private val clock: Clock,
    ) {
        suspend operator fun invoke() {
            if (!preferences.settings.value.enabled) return
            try {
                when (val auth = authorization.authorize()) {
                    CloudAuthorizationResult.NeedsUserConsent ->
                        preferences.recordFailure(
                            DriveBackupFailure.AUTHORIZATION_NEEDED,
                            clock.now().toEpochMilliseconds(),
                        )
                    is CloudAuthorizationResult.Authorized -> {
                        val newest = cloudStore.list(auth.accessToken).maxOfOrNull { it.createdAt }
                        if (newest != preferences.status.value.lastSuccessAt) preferences.replaceLastSuccess(newest)
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: CloudBackupException) {
                // Reported by the next backup run, with its cause.
            } catch (_: IOException) {
                // Offline: keep what is known.
            }
        }
    }
