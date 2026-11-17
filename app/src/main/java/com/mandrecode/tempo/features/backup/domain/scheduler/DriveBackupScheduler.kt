package com.mandrecode.tempo.features.backup.domain.scheduler

import com.mandrecode.tempo.features.backup.domain.model.DriveBackupSettings

/** Runs Drive backups in the background (#306). */
interface DriveBackupScheduler {
    /** Schedules (or reschedules) periodic backups for [settings], or cancels them when off. */
    fun sync(settings: DriveBackupSettings)

    /** Runs one backup as soon as constraints allow. */
    fun runNow(settings: DriveBackupSettings)
}
