package com.mandrecode.tempo.features.backup.domain.usecase

import com.mandrecode.tempo.features.backup.domain.model.ImportMode
import com.mandrecode.tempo.features.backup.domain.model.ImportOutcome
import com.mandrecode.tempo.features.backup.domain.repository.BackupRepository
import com.mandrecode.tempo.features.backup.domain.scheduler.BackupReminderScheduler
import jakarta.inject.Inject
import kotlinx.coroutines.CancellationException

class ImportBackupUseCase
    @Inject
    constructor(
        private val backupRepository: BackupRepository,
        private val reminderScheduler: BackupReminderScheduler,
    ) {
        /**
         * Runs an import and keeps reminder alarms consistent with the database:
         * Replace cancels current alarms up front (its rows are about to go away),
         * and rescheduling always runs afterwards — on success it schedules the
         * imported reminders, on failure it restores alarms from the untouched data.
         */
        suspend operator fun invoke(
            json: String,
            mode: ImportMode,
            passphrase: CharArray? = null,
        ): ImportOutcome =
            try {
                if (mode == ImportMode.REPLACE) {
                    reminderScheduler.cancelAllReminders()
                }
                backupRepository.importFromJson(json, mode, passphrase)
            } finally {
                // Runs even when cancellation fails part-way: rescheduling reads
                // whatever the database now holds, restoring a consistent alarm set.
                rescheduleAfterImport()
            }

        /**
         * The import's own outcome is what the caller must hear: by now its transaction has committed
         * or rolled back. A scheduling failure here must not turn a committed import into a reported
         * failure ("nothing was changed") or replace the import's own error.
         */
        private suspend fun rescheduleAfterImport() {
            try {
                reminderScheduler.rescheduleAllReminders()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Alarms stay as they are until reminders are next scheduled; the data is right.
            }
        }
    }
