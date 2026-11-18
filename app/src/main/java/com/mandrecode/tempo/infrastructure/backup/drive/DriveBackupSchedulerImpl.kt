package com.mandrecode.tempo.infrastructure.backup.drive

import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.mandrecode.tempo.features.backup.domain.model.DriveBackupSettings
import com.mandrecode.tempo.features.backup.domain.scheduler.DriveBackupScheduler
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/** Schedules [DriveBackupWorker] with WorkManager (#306). */
class DriveBackupSchedulerImpl
    @Inject
    constructor(
        private val workManager: WorkManager,
        private val notifier: DriveBackupNotifier,
    ) : DriveBackupScheduler {
        override fun sync(settings: DriveBackupSettings) {
            if (!settings.enabled) {
                workManager.cancelUniqueWork(PERIODIC_WORK)
                workManager.cancelUniqueWork(ONE_TIME_WORK)
                // No run will clear an earlier "backup stopped" notification now: it's about backups that are off.
                notifier.clear()
                return
            }
            val request =
                PeriodicWorkRequestBuilder<DriveBackupWorker>(settings.frequency.days.toLong(), TimeUnit.DAYS)
                    .setConstraints(constraints(settings))
                    .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_MINUTES, TimeUnit.MINUTES)
                    .build()
            workManager.enqueueUniquePeriodicWork(PERIODIC_WORK, ExistingPeriodicWorkPolicy.UPDATE, request)
        }

        override fun runNow(settings: DriveBackupSettings) {
            if (!settings.enabled) return
            val request =
                OneTimeWorkRequestBuilder<DriveBackupWorker>()
                    .setConstraints(constraints(settings))
                    .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_MINUTES, TimeUnit.MINUTES)
                    .build()
            workManager.enqueueUniqueWork(ONE_TIME_WORK, ExistingWorkPolicy.REPLACE, request)
        }

        companion object {
            const val PERIODIC_WORK = "DriveBackupPeriodicWorker"
            const val ONE_TIME_WORK = "DriveBackupNowWorker"
            private const val BACKOFF_MINUTES = 15L

            fun constraints(settings: DriveBackupSettings): Constraints =
                Constraints
                    .Builder()
                    .setRequiredNetworkType(
                        if (settings.unmeteredOnly) NetworkType.UNMETERED else NetworkType.CONNECTED,
                    ).setRequiresBatteryNotLow(true)
                    .build()
        }
    }
