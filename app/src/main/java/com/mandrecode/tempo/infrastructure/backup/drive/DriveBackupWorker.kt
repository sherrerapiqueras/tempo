package com.mandrecode.tempo.infrastructure.backup.drive

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.mandrecode.tempo.features.backup.domain.model.DriveBackupFailure
import com.mandrecode.tempo.features.backup.domain.usecase.RunDriveBackupUseCase
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Runs one Drive backup (#306). Only a network failure is retried (with backoff): authorisation,
 * storage and passphrase failures need the user, so they are recorded, notified, and left for the
 * next scheduled run instead of spinning.
 */
@HiltWorker
class DriveBackupWorker
    @AssistedInject
    constructor(
        @Assisted appContext: Context,
        @Assisted workerParams: WorkerParameters,
        private val runDriveBackup: RunDriveBackupUseCase,
        private val notifier: DriveBackupNotifier,
    ) : CoroutineWorker(appContext, workerParams) {
        override suspend fun doWork(): Result {
            val outcome =
                runDriveBackup { running -> if (running) notifier.showProgress() else notifier.hideProgress() }
            return when (outcome) {
                is RunDriveBackupUseCase.Result.Failed ->
                    when {
                        outcome.failure == DriveBackupFailure.NETWORK && runAttemptCount < MAX_NETWORK_RETRIES ->
                            Result.retry()
                        outcome.failure.needsUser -> {
                            notifier.notifyFailure(outcome.failure)
                            Result.success()
                        }
                        else -> Result.success()
                    }
                else -> {
                    notifier.clear()
                    Result.success()
                }
            }
        }

        private companion object {
            const val MAX_NETWORK_RETRIES = 5
        }
    }
