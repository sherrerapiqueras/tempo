package com.mandrecode.tempo.features.backup.domain.usecase

import com.google.common.truth.Truth.assertThat
import com.mandrecode.tempo.features.backup.domain.model.ImportMode
import com.mandrecode.tempo.features.backup.domain.model.ImportOutcome
import com.mandrecode.tempo.features.backup.domain.model.ImportSummary
import com.mandrecode.tempo.features.backup.domain.repository.BackupRepository
import com.mandrecode.tempo.features.backup.domain.scheduler.BackupReminderScheduler
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.Test

class ImportBackupUseCaseTest {
    private val backupRepository: BackupRepository = mockk(relaxed = true)
    private val reminderScheduler: BackupReminderScheduler = mockk(relaxed = true)
    private val useCase = ImportBackupUseCase(backupRepository, reminderScheduler)

    @Test
    fun `replace cancels alarms before importing and reschedules after`() =
        runTest {
            coEvery { backupRepository.importFromJson(any(), any(), any()) } returns
                ImportOutcome.Success(ImportSummary())

            useCase("{}", ImportMode.REPLACE)

            coVerifyOrder {
                reminderScheduler.cancelAllReminders()
                backupRepository.importFromJson("{}", ImportMode.REPLACE, null)
                reminderScheduler.rescheduleAllReminders()
            }
        }

    @Test
    fun `merge does not cancel alarms but still reschedules`() =
        runTest {
            coEvery { backupRepository.importFromJson(any(), any(), any()) } returns
                ImportOutcome.Success(ImportSummary())

            useCase("{}", ImportMode.MERGE)

            coVerify(exactly = 0) { reminderScheduler.cancelAllReminders() }
            verify { reminderScheduler.rescheduleAllReminders() }
        }

    @Test
    fun `reschedules even when the repository throws`() =
        runTest {
            coEvery { backupRepository.importFromJson(any(), any(), any()) } throws IllegalStateException("boom")

            val thrown = runCatching { useCase("{}", ImportMode.REPLACE) }.exceptionOrNull()

            assertThat(thrown).isInstanceOf(IllegalStateException::class.java)
            verify { reminderScheduler.rescheduleAllReminders() }
        }

    @Test
    fun `reschedules even when cancelling alarms throws`() =
        runTest {
            coEvery { reminderScheduler.cancelAllReminders() } throws IllegalStateException("alarm service gone")

            val thrown = runCatching { useCase("{}", ImportMode.REPLACE) }.exceptionOrNull()

            assertThat(thrown).isInstanceOf(IllegalStateException::class.java)
            verify { reminderScheduler.rescheduleAllReminders() }
        }

    @Test
    fun `a rescheduling failure after a committed import keeps the import's outcome`() =
        runTest {
            val outcome = ImportOutcome.Success(ImportSummary())
            coEvery { backupRepository.importFromJson(any(), any(), any()) } returns outcome
            coEvery { reminderScheduler.rescheduleAllReminders() } throws IllegalStateException("work manager")

            assertThat(useCase("{}", ImportMode.REPLACE)).isEqualTo(outcome)
        }

    @Test
    fun `a rescheduling failure doesn't hide the import's own error`() =
        runTest {
            coEvery { backupRepository.importFromJson(any(), any(), any()) } throws IllegalStateException("import")
            coEvery { reminderScheduler.rescheduleAllReminders() } throws IllegalArgumentException("work manager")

            val thrown = runCatching { useCase("{}", ImportMode.REPLACE) }.exceptionOrNull()

            assertThat(thrown).hasMessageThat().isEqualTo("import")
        }

    @Test
    fun `returns the repository outcome`() =
        runTest {
            val outcome = ImportOutcome.UnsupportedVersion(fileVersion = 9, maxSupported = 1)
            coEvery { backupRepository.importFromJson(any(), any(), any()) } returns outcome

            assertThat(useCase("{}", ImportMode.MERGE)).isEqualTo(outcome)
        }
}
