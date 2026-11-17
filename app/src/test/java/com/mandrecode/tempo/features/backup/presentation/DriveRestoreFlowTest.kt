package com.mandrecode.tempo.features.backup.presentation

import android.app.PendingIntent
import android.content.Intent
import com.google.common.truth.Truth.assertThat
import com.mandrecode.tempo.features.backup.domain.model.DriveBackupFailure
import com.mandrecode.tempo.features.backup.domain.model.DriveBackupSettings
import com.mandrecode.tempo.features.backup.domain.model.ImportMode
import com.mandrecode.tempo.features.backup.domain.model.ImportOutcome
import com.mandrecode.tempo.features.backup.domain.model.ImportSummary
import com.mandrecode.tempo.features.backup.domain.repository.BackupRepository
import com.mandrecode.tempo.features.backup.domain.repository.CloudAuthorizationResult
import com.mandrecode.tempo.features.backup.domain.repository.CloudBackupException
import com.mandrecode.tempo.features.backup.domain.usecase.EnableDriveBackupUseCase
import com.mandrecode.tempo.features.backup.domain.usecase.FakeAuthorization
import com.mandrecode.tempo.features.backup.domain.usecase.FakeCloudStore
import com.mandrecode.tempo.features.backup.domain.usecase.FakeDrivePreferences
import com.mandrecode.tempo.features.backup.domain.usecase.FakePassphraseStore
import com.mandrecode.tempo.features.backup.domain.usecase.FakeScheduler
import com.mandrecode.tempo.features.backup.domain.usecase.HasLocalDataUseCase
import com.mandrecode.tempo.features.backup.domain.usecase.ImportBackupUseCase
import com.mandrecode.tempo.features.backup.domain.usecase.ListDriveBackupsUseCase
import com.mandrecode.tempo.features.backup.domain.usecase.RestoreDriveBackupUseCase
import com.mandrecode.tempo.infrastructure.backup.drive.GoogleDriveAuthorization
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DriveRestoreFlowTest {
    private val consent = mockk<GoogleDriveAuthorization> { coEvery { consentIntent() } returns null }
    private val auth = FakeAuthorization()
    private val cloud = FakeCloudStore()
    private val importBackup = mockk<ImportBackupUseCase>()
    private var deviceHasData = false
    private val repository = mockk<BackupRepository> { coEvery { hasUserData() } answers { deviceHasData } }
    private val store = FakePassphraseStore()
    private val preferences = FakeDrivePreferences()
    private val scheduler = FakeScheduler()
    private val flow =
        driveRestoreFlow(consent, auth, cloud, importBackup, repository, store, preferences, scheduler)
    private var step: DriveRestoreStep? = null
    private val consents = mutableListOf<PendingIntent>()

    private fun TestScope.host() =
        DriveRestoreFlow.Host(scope = backgroundScope, showStep = { step = it }, launchConsent = { consents += it })

    private suspend fun backup() = cloud.upload("t", "tempo-backup-42.tempo", "encrypted")

    @Test
    fun `with access granted it offers the newest backup`() =
        runTest(UnconfinedTestDispatcher()) {
            backup()
            val newest = cloud.upload("t", "tempo-backup-99.tempo", "newer")

            flow.start(host())

            assertThat(step).isEqualTo(DriveRestoreStep.Offer(newest))
        }

    @Test
    fun `it asks for consent first, and a declined consent ends the flow`() =
        runTest(UnconfinedTestDispatcher()) {
            val intent = mockk<PendingIntent>()
            coEvery { consent.consentIntent() } returns intent
            every { consent.isGranted(null) } returns false
            val host = host()

            flow.start(host)
            assertThat(consents).containsExactly(intent)
            assertThat(step).isEqualTo(DriveRestoreStep.Searching)

            flow.onEvent(DriveRestoreEvent.ConsentFinished(null), host)
            assertThat(step).isNull()
        }

    @Test
    fun `a granted consent goes on to look for backups`() =
        runTest(UnconfinedTestDispatcher()) {
            coEvery { consent.consentIntent() } returns mockk()
            val granted = mockk<Intent>()
            every { consent.isGranted(granted) } returns true
            val host = host()

            flow.start(host)
            flow.onEvent(DriveRestoreEvent.ConsentFinished(granted), host)

            assertThat(step).isEqualTo(DriveRestoreStep.NoBackups)
        }

    @Test
    fun `when Drive cannot authorise at all, it says so`() =
        runTest(UnconfinedTestDispatcher()) {
            coEvery { consent.consentIntent() } throws CloudBackupException(DriveBackupFailure.UNEXPECTED)

            flow.start(host())

            assertThat(step).isEqualTo(DriveRestoreStep.Failed(DriveRestoreFailure.Unavailable))
        }

    @Test
    fun `a fresh device restores by replacing, without asking`() =
        runTest(UnconfinedTestDispatcher()) {
            val file = backup()
            val host = host()
            flow.start(host)

            flow.onEvent(DriveRestoreEvent.RestoreClicked, host)

            assertThat(step).isEqualTo(DriveRestoreStep.EnterPassphrase(file, ImportMode.REPLACE))
        }

    @Test
    fun `a device with data asks merge or replace`() =
        runTest(UnconfinedTestDispatcher()) {
            val file = backup()
            deviceHasData = true
            val host = host()
            flow.start(host)

            flow.onEvent(DriveRestoreEvent.RestoreClicked, host)
            assertThat(step).isEqualTo(DriveRestoreStep.ChooseMode(file))

            flow.onEvent(DriveRestoreEvent.ModeChosen(ImportMode.MERGE), host)
            assertThat(step).isEqualTo(DriveRestoreStep.EnterPassphrase(file, ImportMode.MERGE))
        }

    @Test
    fun `a wrong passphrase asks again and says it was wrong`() =
        runTest(UnconfinedTestDispatcher()) {
            val file = backup()
            coEvery { importBackup(any(), any(), any()) } returns ImportOutcome.WrongPassphrase
            val host = host()
            flow.start(host)
            flow.onEvent(DriveRestoreEvent.RestoreClicked, host)

            flow.onEvent(DriveRestoreEvent.PassphraseEntered("nope".toCharArray()), host)

            assertThat(step)
                .isEqualTo(DriveRestoreStep.EnterPassphrase(file, ImportMode.REPLACE, previousAttemptWrong = true))
        }

    @Test
    fun `restoring offers to keep backing up with the same passphrase`() =
        runTest(UnconfinedTestDispatcher()) {
            backup()
            coEvery { importBackup(any(), any(), any()) } returns ImportOutcome.Success(ImportSummary())
            val host = host()
            flow.start(host)
            flow.onEvent(DriveRestoreEvent.RestoreClicked, host)
            val passphrase = "right".toCharArray()

            flow.onEvent(DriveRestoreEvent.PassphraseEntered(passphrase), host)
            assertThat(step).isEqualTo(DriveRestoreStep.Restored(imported = 0, offerToKeepBackingUp = true))
            assertThat(passphrase.all { it == '\u0000' }).isTrue()

            flow.onEvent(DriveRestoreEvent.KeepBackingUpChosen(true), host)

            assertThat(store.stored).isEqualTo("right")
            assertThat(preferences.settings.value.enabled).isTrue()
            assertThat(step).isNull()
        }

    @Test
    fun `declining to keep backing up stores nothing`() =
        runTest(UnconfinedTestDispatcher()) {
            backup()
            coEvery { importBackup(any(), any(), any()) } returns ImportOutcome.Success(ImportSummary())
            val host = host()
            flow.start(host)
            flow.onEvent(DriveRestoreEvent.RestoreClicked, host)
            flow.onEvent(DriveRestoreEvent.PassphraseEntered("right".toCharArray()), host)

            flow.onEvent(DriveRestoreEvent.KeepBackingUpChosen(false), host)

            assertThat(store.stored).isNull()
            assertThat(preferences.settings.value.enabled).isFalse()
        }

    @Test
    fun `with backup already on, a restore does not offer it again`() =
        runTest(UnconfinedTestDispatcher()) {
            preferences.updateSettings(DriveBackupSettings(enabled = true))
            backup()
            coEvery { importBackup(any(), any(), any()) } returns ImportOutcome.Success(ImportSummary())
            val host = host()
            flow.start(host)
            flow.onEvent(DriveRestoreEvent.RestoreClicked, host)

            flow.onEvent(DriveRestoreEvent.PassphraseEntered("right".toCharArray()), host)

            assertThat(step).isEqualTo(DriveRestoreStep.Restored(imported = 0, offerToKeepBackingUp = false))
        }

    @Test
    fun `failures are specific`() =
        runTest(UnconfinedTestDispatcher()) {
            backup()
            val host = host()

            suspend fun failureFor(result: ImportOutcome): DriveRestoreStep? {
                coEvery { importBackup(any(), any(), any()) } returns result
                flow.start(host)
                flow.onEvent(DriveRestoreEvent.RestoreClicked, host)
                flow.onEvent(DriveRestoreEvent.PassphraseEntered("pw".toCharArray()), host)
                return step
            }

            assertThat(failureFor(ImportOutcome.CorruptFile))
                .isEqualTo(DriveRestoreStep.Failed(DriveRestoreFailure.Corrupt))
            assertThat(failureFor(ImportOutcome.UnsupportedVersion(3, 2)))
                .isEqualTo(DriveRestoreStep.Failed(DriveRestoreFailure.NewerVersion(3, 2)))
            assertThat(failureFor(ImportOutcome.ValidationFailed(emptyList())))
                .isEqualTo(DriveRestoreStep.Failed(DriveRestoreFailure.Invalid))

            auth.result = CloudAuthorizationResult.NeedsUserConsent
            flow.start(host)
            assertThat(step)
                .isEqualTo(DriveRestoreStep.Failed(DriveRestoreFailure.Cloud(DriveBackupFailure.AUTHORIZATION_NEEDED)))
        }

    @Test
    fun `an unexpected failure during the restore ends in a failure and wipes the passphrase`() =
        runTest(UnconfinedTestDispatcher()) {
            backup()
            coEvery { importBackup(any(), any(), any()) } throws IllegalStateException("disk full")
            val host = host()
            flow.start(host)
            flow.onEvent(DriveRestoreEvent.RestoreClicked, host)
            val passphrase = "pw".toCharArray()

            flow.onEvent(DriveRestoreEvent.PassphraseEntered(passphrase), host)

            assertThat(step)
                .isEqualTo(DriveRestoreStep.Failed(DriveRestoreFailure.Cloud(DriveBackupFailure.UNEXPECTED)))
            assertThat(passphrase.all { it == '\u0000' }).isTrue()
        }

    @Test
    fun `dismissing ends the flow`() =
        runTest(UnconfinedTestDispatcher()) {
            backup()
            val host = host()
            flow.start(host)

            flow.onEvent(DriveRestoreEvent.Dismissed, host)

            assertThat(step).isNull()
        }
}

/** A restore flow on fakes, also used by the Settings and onboarding tests that build whole screens. */
internal fun driveRestoreFlow(
    consent: GoogleDriveAuthorization = mockk(relaxed = true),
    auth: FakeAuthorization = FakeAuthorization(),
    cloud: FakeCloudStore = FakeCloudStore(),
    importBackup: ImportBackupUseCase = mockk(relaxed = true),
    repository: BackupRepository = mockk(relaxed = true),
    store: FakePassphraseStore = FakePassphraseStore(),
    preferences: FakeDrivePreferences = FakeDrivePreferences(),
    scheduler: FakeScheduler = FakeScheduler(),
) = DriveRestoreFlow(
    authorization = consent,
    listBackups = ListDriveBackupsUseCase(auth, cloud),
    restore = RestoreDriveBackupUseCase(auth, cloud, importBackup),
    hasLocalData = HasLocalDataUseCase(repository),
    enableBackup = EnableDriveBackupUseCase(store, preferences, scheduler),
    backupPreferences = preferences,
)
