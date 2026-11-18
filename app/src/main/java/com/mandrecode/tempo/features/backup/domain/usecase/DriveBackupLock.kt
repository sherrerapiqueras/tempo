package com.mandrecode.tempo.features.backup.domain.usecase

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * One Drive backup operation at a time in this process: backups with each other (#306), and a
 * restore with backups (#307). A backup that read the database before a restore committed would
 * otherwise upload the old data afterwards, as the newest backup. WorkManager runs backups in this
 * process, so a process-wide lock is enough.
 */
@Singleton
class DriveBackupLock
    @Inject
    constructor() {
        private val mutex = Mutex()

        suspend fun <T> withLock(block: suspend () -> T): T = mutex.withLock { block() }
    }
