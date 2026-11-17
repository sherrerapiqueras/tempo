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
import javax.inject.Singleton
import kotlin.time.Clock

/**
 * Checks the recorded status against what Drive actually holds (#306), so Settings never claims
 * a backup that is gone. Disconnecting Tempo from Drive deletes its app folder: the last success
 * then becomes the newest backup still there, or none, and lost access is reported. A network
 * failure changes nothing; the next look tries again.
 */
@Singleton
class RefreshDriveBackupStatusUseCase
    @Inject
    constructor(
        private val preferences: DriveBackupPreferences,
        private val authorization: CloudAuthorization,
        private val cloudStore: CloudBackupStore,
        private val clock: Clock,
    ) {
        /** When the last check ran, so opening Settings repeatedly does not hit the network each time. */
        @Volatile
        private var lastCheckedAt: Long? = null

        suspend operator fun invoke() {
            if (!preferences.settings.value.enabled) return
            val now = clock.now().toEpochMilliseconds()
            lastCheckedAt?.let { if (now - it < MIN_INTERVAL_MILLIS) return }
            lastCheckedAt = now
            try {
                when (val auth = authorization.authorize()) {
                    CloudAuthorizationResult.NeedsUserConsent ->
                        preferences.recordFailure(
                            DriveBackupFailure.AUTHORIZATION_NEEDED,
                            clock.now().toEpochMilliseconds(),
                        )
                    is CloudAuthorizationResult.Authorized -> {
                        // This install's own backups: another device's must not read as this one's.
                        val newest =
                            cloudStore
                                .list(auth.accessToken)
                                .filter { it.isFrom(preferences.installId) }
                                .maxOfOrNull { it.createdAt }
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

        companion object {
            /** Disconnects are still noticed within the hour, without a request on every visit. */
            const val MIN_INTERVAL_MILLIS = 60 * 60 * 1000L
        }
    }
