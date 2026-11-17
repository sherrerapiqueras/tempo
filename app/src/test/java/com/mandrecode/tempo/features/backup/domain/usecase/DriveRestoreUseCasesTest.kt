package com.mandrecode.tempo.features.backup.domain.usecase

import com.google.common.truth.Truth.assertThat
import com.mandrecode.tempo.features.backup.domain.model.CloudBackupFile
import com.mandrecode.tempo.features.backup.domain.model.DriveBackupFailure
import com.mandrecode.tempo.features.backup.domain.model.ImportMode
import com.mandrecode.tempo.features.backup.domain.model.ImportOutcome
import com.mandrecode.tempo.features.backup.domain.model.ImportSummary
import com.mandrecode.tempo.features.backup.domain.repository.BackupRepository
import com.mandrecode.tempo.features.backup.domain.repository.CloudAuthorizationResult
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.io.IOException

class DriveRestoreUseCasesTest {
    private val cloud = FakeCloudStore()
    private val auth = FakeAuthorization()
    private val importBackup = mockk<ImportBackupUseCase>()
    private val list = ListDriveBackupsUseCase(auth, cloud)
    private val restore = RestoreDriveBackupUseCase(auth, cloud, importBackup)

    @Test
    fun `backups are listed newest first`() =
        runTest {
            cloud.upload("t", "tempo-backup-10.tempo", "a")
            cloud.upload("t", "tempo-backup-30.tempo", "b")
            cloud.upload("t", "tempo-backup-20.tempo", "c")

            val found = list() as ListDriveBackupsUseCase.Result.Found

            assertThat(found.backups.map { it.createdAt }).containsExactly(30L, 20L, 10L).inOrder()
        }

    @Test
    fun `listing without access or network says which`() =
        runTest {
            auth.result = CloudAuthorizationResult.NeedsUserConsent
            assertThat(list()).isEqualTo(ListDriveBackupsUseCase.Result.Failed(DriveBackupFailure.AUTHORIZATION_NEEDED))

            auth.result = CloudAuthorizationResult.Authorized("t")
            cloud.failListWith = IOException("offline")
            assertThat(list()).isEqualTo(ListDriveBackupsUseCase.Result.Failed(DriveBackupFailure.NETWORK))
        }

    @Test
    fun `restoring imports the downloaded file with the passphrase and mode`() =
        runTest {
            val file = cloud.upload("t", "tempo-backup-5.tempo", "encrypted")
            coEvery { importBackup("encrypted", ImportMode.REPLACE, any()) } returns
                ImportOutcome.Success(ImportSummary())

            val outcome = restore(file, ImportMode.REPLACE, "pw".toCharArray())

            assertThat(outcome).isInstanceOf(RestoreOutcome.Restored::class.java)
            coVerify { importBackup("encrypted", ImportMode.REPLACE, match { String(it) == "pw" }) }
        }

    @Test
    fun `each import failure keeps its own meaning`() =
        runTest {
            val file = cloud.upload("t", "tempo-backup-5.tempo", "x")

            suspend fun outcomeFor(result: ImportOutcome): RestoreOutcome {
                coEvery { importBackup(any(), any(), any()) } returns result
                return restore(file, ImportMode.MERGE, "pw".toCharArray())
            }

            assertThat(outcomeFor(ImportOutcome.WrongPassphrase)).isEqualTo(RestoreOutcome.WrongPassphrase)
            assertThat(outcomeFor(ImportOutcome.CorruptFile)).isEqualTo(RestoreOutcome.Corrupt)
            assertThat(outcomeFor(ImportOutcome.UnsupportedVersion(3, 2))).isEqualTo(RestoreOutcome.NewerVersion(3, 2))
            assertThat(outcomeFor(ImportOutcome.ValidationFailed(emptyList()))).isEqualTo(RestoreOutcome.Invalid)
        }

    @Test
    fun `a download failure changes nothing and says why`() =
        runTest {
            auth.result = CloudAuthorizationResult.NeedsUserConsent
            val file =
                com.mandrecode.tempo.features.backup.domain.model
                    .CloudBackupFile("id", "n", 1, 1)

            val outcome = restore(file, ImportMode.REPLACE, "pw".toCharArray())

            assertThat(outcome).isEqualTo(RestoreOutcome.CloudFailed(DriveBackupFailure.AUTHORIZATION_NEEDED))
            coVerify(exactly = 0) { importBackup(any(), any(), any()) }
        }

    @Test
    fun `local data is whatever the repository says`() =
        runTest {
            val repository = mockk<BackupRepository> { coEvery { hasUserData() } returns true }

            assertThat(HasLocalDataUseCase(repository)()).isTrue()
        }
}
