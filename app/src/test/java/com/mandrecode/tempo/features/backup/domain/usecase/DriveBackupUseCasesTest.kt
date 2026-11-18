package com.mandrecode.tempo.features.backup.domain.usecase

import com.google.common.truth.Truth.assertThat
import com.mandrecode.tempo.features.backup.domain.model.BackupFrequency
import com.mandrecode.tempo.features.backup.domain.model.DriveBackupFailure
import com.mandrecode.tempo.features.backup.domain.model.DriveBackupSettings
import com.mandrecode.tempo.features.backup.domain.model.backupFileName
import com.mandrecode.tempo.features.backup.domain.repository.BackupRepository
import com.mandrecode.tempo.features.backup.domain.repository.CloudAuthorizationResult
import com.mandrecode.tempo.features.backup.domain.repository.CloudBackupException
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import org.junit.Test
import java.io.IOException

class DriveBackupUseCasesTest {
    private val store = FakePassphraseStore()
    private val preferences = FakeDrivePreferences()
    private val scheduler = FakeScheduler()
    private val cloud = FakeCloudStore()
    private val auth = FakeAuthorization()
    private val clock = SteppingClock()
    private val exportedWith = slot<CharArray>()
    private val backupRepository =
        mockk<BackupRepository> {
            coEvery { exportEncrypted(capture(exportedWith)) } answers { "encrypted:" + String(exportedWith.captured) }
        }
    private val run = RunDriveBackupUseCase(backupRepository, store, preferences, cloud, auth, clock)

    private fun enabled() = preferences.updateSettings(DriveBackupSettings(enabled = true))

    @Test
    fun `enabling stores the passphrase, schedules and backs up now, and wipes the input`() {
        val passphrase = "secret".toCharArray()
        val confirmation = "secret".toCharArray()

        val check = EnableDriveBackupUseCase(store, preferences, scheduler)(passphrase, confirmation)

        assertThat(check).isEqualTo(PassphraseCheck.OK)
        assertThat(store.stored).isEqualTo("secret")
        assertThat(preferences.settings.value.enabled).isTrue()
        assertThat(scheduler.synced.last().enabled).isTrue()
        assertThat(scheduler.runNowCalls).isEqualTo(1)
        assertThat(passphrase.all { it == '\u0000' }).isTrue()
        assertThat(confirmation.all { it == '\u0000' }).isTrue()
    }

    @Test
    fun `when scheduling fails, enabling puts everything back and reports the failure`() {
        scheduler.failEnablingWith = IllegalStateException("work manager")

        val thrown =
            runCatching {
                EnableDriveBackupUseCase(store, preferences, scheduler)("secret".toCharArray(), "secret".toCharArray())
            }.exceptionOrNull()

        assertThat(thrown).isInstanceOf(IllegalStateException::class.java)
        assertThat(store.stored).isNull()
        assertThat(preferences.settings.value.enabled).isFalse()
    }

    @Test
    fun `failing to queue the first backup still leaves backup on`() {
        scheduler.failRunNowWith = IllegalStateException("work manager")

        val enable = EnableDriveBackupUseCase(store, preferences, scheduler)

        val check = enable("secret".toCharArray(), "secret".toCharArray())

        assertThat(check).isEqualTo(PassphraseCheck.OK)
        assertThat(store.stored).isEqualTo("secret")
        assertThat(preferences.settings.value.enabled).isTrue()
    }

    @Test
    fun `a passphrase change stands even if the backup after it can't be queued`() {
        store.stored = "old"
        enabled()
        scheduler.failRunNowWith = IllegalStateException("work manager")

        val change = ChangeBackupPassphraseUseCase(store, preferences, scheduler)

        val check = change("new".toCharArray(), "new".toCharArray())

        assertThat(check).isEqualTo(PassphraseCheck.OK)
        assertThat(store.stored).isEqualTo("new")
    }

    @Test
    fun `enabling refuses an empty or mismatched passphrase and changes nothing`() {
        val enable = EnableDriveBackupUseCase(store, preferences, scheduler)

        assertThat(enable(CharArray(0), CharArray(0))).isEqualTo(PassphraseCheck.EMPTY)
        assertThat(enable("a".toCharArray(), "b".toCharArray())).isEqualTo(PassphraseCheck.MISMATCH)
        assertThat(store.stored).isNull()
        assertThat(preferences.settings.value.enabled).isFalse()
        assertThat(scheduler.runNowCalls).isEqualTo(0)
    }

    @Test
    fun `disabling stops backups and forgets the passphrase`() {
        store.stored = "secret"
        enabled()

        DisableDriveBackupUseCase(store, preferences, scheduler)()

        assertThat(preferences.settings.value.enabled).isFalse()
        assertThat(scheduler.synced.last().enabled).isFalse()
        assertThat(store.stored).isNull()
    }

    @Test
    fun `changing the passphrase stores it and backs up now`() {
        store.stored = "old"
        enabled()

        ChangeBackupPassphraseUseCase(store, preferences, scheduler)("new".toCharArray(), "new".toCharArray())

        assertThat(store.stored).isEqualTo("new")
        assertThat(scheduler.runNowCalls).isEqualTo(1)
    }

    @Test
    fun `turning backup on again forgets a failure from before it was off`() {
        preferences.recordFailure(DriveBackupFailure.AUTHORIZATION_NEEDED, atMillis = 5)

        EnableDriveBackupUseCase(store, preferences, scheduler)("secret".toCharArray(), "secret".toCharArray())

        assertThat(preferences.status.value.currentFailure).isNull()
    }

    @Test
    fun `setting the passphrase again clears the unreadable-passphrase failure`() {
        enabled()
        preferences.recordFailure(DriveBackupFailure.PASSPHRASE_UNAVAILABLE, atMillis = 5)

        ChangeBackupPassphraseUseCase(store, preferences, scheduler)("new".toCharArray(), "new".toCharArray())

        assertThat(preferences.status.value.currentFailure).isNull()
    }

    @Test
    fun `a mismatched passphrase leaves the failure showing`() {
        enabled()
        preferences.recordFailure(DriveBackupFailure.PASSPHRASE_UNAVAILABLE, atMillis = 5)

        ChangeBackupPassphraseUseCase(store, preferences, scheduler)("a".toCharArray(), "b".toCharArray())

        assertThat(preferences.status.value.currentFailure).isEqualTo(DriveBackupFailure.PASSPHRASE_UNAVAILABLE)
    }

    @Test
    fun `choosing the frequency already set reschedules nothing`() {
        enabled()
        val before = scheduler.synced.size

        UpdateDriveBackupScheduleUseCase(preferences, scheduler)(frequency = preferences.settings.value.frequency)

        assertThat(scheduler.synced).hasSize(before)
    }

    @Test
    fun `updating the schedule saves and reschedules`() {
        enabled()

        UpdateDriveBackupScheduleUseCase(preferences, scheduler)(
            frequency = BackupFrequency.WEEKLY,
            unmeteredOnly = false,
        )

        assertThat(preferences.settings.value).isEqualTo(
            DriveBackupSettings(enabled = true, frequency = BackupFrequency.WEEKLY, unmeteredOnly = false),
        )
        assertThat(scheduler.synced.last()).isEqualTo(preferences.settings.value)
    }

    @Test
    fun `a backup uploads the file encrypted with the stored passphrase and records success`() =
        runTest {
            store.stored = "secret"
            enabled()

            val result = run()

            assertThat(result).isEqualTo(RunDriveBackupUseCase.Result.Succeeded)
            assertThat(cloud.contents.values.single()).isEqualTo("encrypted:secret")
            assertThat(cloud.files.single().name).matches("tempo-backup-this-install-\\d+\\.tempo")
            assertThat(preferences.status.value.lastSuccessAt).isNotNull()
            assertThat(preferences.status.failure).isNull()
        }

    @Test
    fun `nothing runs while backup is off, and nothing is recorded as a backup`() =
        runTest {
            store.stored = "secret"

            assertThat(run()).isEqualTo(RunDriveBackupUseCase.Result.Skipped)
            assertThat(cloud.files).isEmpty()
            assertThat(preferences.status.value.lastSuccessAt).isNull()
        }

    private val refresh = RefreshDriveBackupStatusUseCase(preferences, auth, cloud, clock)

    @Test
    fun `refreshing clears a last backup that is no longer in Drive`() =
        runTest {
            enabled()
            preferences.recordSuccess(500)

            refresh()

            assertThat(preferences.status.value.lastSuccessAt).isNull()
        }

    @Test
    fun `refreshing takes the newest backup Drive actually holds`() =
        runTest {
            enabled()
            preferences.recordSuccess(500)
            cloud.upload("t", backupFileName("this-install", 300), "x")
            cloud.upload("t", backupFileName("this-install", 400), "y")
            cloud.upload("t", backupFileName("other-phone", 900), "z")

            refresh()

            assertThat(preferences.status.value.lastSuccessAt).isEqualTo(400)
        }

    @Test
    fun `refreshing reports lost access, and keeps the status when offline or off`() =
        runTest {
            enabled()
            preferences.recordSuccess(500)
            cloud.failListWith = IOException("offline")
            refresh()
            assertThat(preferences.status.value.lastSuccessAt).isEqualTo(500)

            clock.millis += RefreshDriveBackupStatusUseCase.MIN_INTERVAL_MILLIS
            auth.result = CloudAuthorizationResult.NeedsUserConsent
            refresh()
            assertThat(preferences.status.failure).isEqualTo(DriveBackupFailure.AUTHORIZATION_NEEDED)

            preferences.updateSettings(DriveBackupSettings(enabled = false))
            auth.result = CloudAuthorizationResult.Authorized("t")
            cloud.failListWith = null
            refresh()
            assertThat(preferences.status.value.lastSuccessAt).isEqualTo(500)
        }

    @Test
    fun `a check that failed offline is tried again on the next look`() =
        runTest {
            enabled()
            preferences.recordSuccess(500)
            cloud.failListWith = IOException("offline")
            refresh()

            cloud.failListWith = null
            refresh()

            assertThat(preferences.status.value.lastSuccessAt).isNull()
        }

    @Test
    fun `a token Drive rejects while listing is reported as lost access`() =
        runTest {
            enabled()
            preferences.recordSuccess(500)
            cloud.failListWith = CloudBackupException(DriveBackupFailure.AUTHORIZATION_NEEDED)

            refresh()

            assertThat(preferences.status.failure).isEqualTo(DriveBackupFailure.AUTHORIZATION_NEEDED)
        }

    @Test
    fun `two runs started together take turns instead of overlapping`() =
        runTest {
            store.stored = "secret"
            enabled()
            var inFlight = 0
            var overlapped = false
            coEvery { backupRepository.exportEncrypted(any()) } coAnswers {
                inFlight++
                if (inFlight > 1) overlapped = true
                yield()
                inFlight--
                "encrypted"
            }

            val first = async { run() }
            val second = async { run() }
            first.await()
            second.await()

            assertThat(overlapped).isFalse()
            assertThat(cloud.files).hasSize(2)
        }

    @Test
    fun `a run that backup was turned off during records nothing`() =
        runTest {
            store.stored = "secret"
            enabled()
            cloud.failUploadWith = CloudBackupException(DriveBackupFailure.STORAGE_FULL)
            coEvery { backupRepository.exportEncrypted(any()) } answers {
                preferences.updateSettings(DriveBackupSettings(enabled = false))
                "encrypted"
            }

            assertThat(run()).isEqualTo(RunDriveBackupUseCase.Result.Skipped)
            assertThat(preferences.status.value.lastFailure).isNull()
        }

    @Test
    fun `progress is reported by the run doing the work, not by one waiting or skipped`() =
        runTest {
            store.stored = "secret"
            enabled()
            val events = mutableListOf<String>()
            coEvery { backupRepository.exportEncrypted(any()) } coAnswers {
                yield()
                "encrypted"
            }

            val first = async { run.invoke { events += "first $it" } }
            val second = async { run.invoke { events += "second $it" } }
            first.await()
            second.await()
            preferences.updateSettings(DriveBackupSettings(enabled = false))
            run.invoke { events += "skipped $it" }

            assertThat(events).containsExactly("first true", "first false", "second true", "second false").inOrder()
        }

    @Test
    fun `a status check waits for a running backup instead of overwriting its result`() =
        runTest {
            enabled()
            val lock = DriveBackupLock()
            val check = RefreshDriveBackupStatusUseCase(preferences, auth, cloud, clock, lock)
            val backupDone = CompletableDeferred<Unit>()
            launch {
                lock.withLock {
                    backupDone.await()
                    cloud.upload("t", backupFileName("this-install", 900), "x")
                    preferences.recordSuccess(900)
                }
            }
            runCurrent()

            val checking = async { check() }
            runCurrent()
            backupDone.complete(Unit)
            checking.await()

            assertThat(preferences.status.value.lastSuccessAt).isEqualTo(900)
        }

    @Test
    fun `only the newest backups are kept`() =
        runTest {
            store.stored = "secret"
            enabled()
            repeat(RunDriveBackupUseCase.RETENTION + 2) { run() }

            assertThat(cloud.files).hasSize(RunDriveBackupUseCase.RETENTION)
            assertThat(cloud.files.map { it.createdAt }).isEqualTo(cloud.files.map { it.createdAt }.sorted())
        }

    @Test
    fun `retention and status ignore another device's backups on the same account`() =
        runTest {
            store.stored = "secret"
            enabled()
            repeat(RunDriveBackupUseCase.RETENTION + 1) {
                cloud.upload("t", backupFileName("other-phone", 10L + it), "o")
            }

            run()

            assertThat(cloud.files.count { it.isFrom("other-phone") }).isEqualTo(RunDriveBackupUseCase.RETENTION + 1)
            assertThat(cloud.files.count { it.isFrom("this-install") }).isEqualTo(1)
        }

    @Test
    fun `opening Settings again soon does not check Drive again`() =
        runTest {
            enabled()
            preferences.recordSuccess(500)
            refresh()
            assertThat(preferences.status.value.lastSuccessAt).isNull()

            cloud.upload("t", backupFileName("this-install", 700), "x")
            refresh()

            assertThat(preferences.status.value.lastSuccessAt).isNull()
        }

    @Test
    fun `a failure to prune does not fail the backup`() =
        runTest {
            store.stored = "secret"
            enabled()
            cloud.failListWith = IOException("offline")

            assertThat(run()).isEqualTo(RunDriveBackupUseCase.Result.Succeeded)
        }

    @Test
    fun `lost authorisation is recorded as needing the user`() =
        runTest {
            store.stored = "secret"
            enabled()
            auth.result = CloudAuthorizationResult.NeedsUserConsent

            assertThat(run()).isEqualTo(RunDriveBackupUseCase.Result.Failed(DriveBackupFailure.AUTHORIZATION_NEEDED))
            assertThat(preferences.status.failure).isEqualTo(DriveBackupFailure.AUTHORIZATION_NEEDED)
            assertThat(cloud.files).isEmpty()
        }

    @Test
    fun `an unreadable passphrase never backs up with another key`() =
        runTest {
            enabled()

            assertThat(run()).isEqualTo(RunDriveBackupUseCase.Result.Failed(DriveBackupFailure.PASSPHRASE_UNAVAILABLE))
            assertThat(cloud.files).isEmpty()
        }

    @Test
    fun `network and storage failures keep their cause`() =
        runTest {
            store.stored = "secret"
            enabled()

            cloud.failUploadWith = IOException("offline")
            assertThat(run()).isEqualTo(RunDriveBackupUseCase.Result.Failed(DriveBackupFailure.NETWORK))

            cloud.failUploadWith = CloudBackupException(DriveBackupFailure.STORAGE_FULL)
            assertThat(run()).isEqualTo(RunDriveBackupUseCase.Result.Failed(DriveBackupFailure.STORAGE_FULL))

            cloud.failUploadWith = IllegalStateException("boom")
            assertThat(run()).isEqualTo(RunDriveBackupUseCase.Result.Failed(DriveBackupFailure.UNEXPECTED))
        }

    @Test
    fun `a success after a failure clears the shown failure`() =
        runTest {
            store.stored = "secret"
            enabled()
            cloud.failUploadWith = IOException("offline")
            run()
            assertThat(preferences.status.failure).isEqualTo(DriveBackupFailure.NETWORK)

            cloud.failUploadWith = null
            run()

            assertThat(preferences.status.failure).isNull()
        }

    @Test
    fun `needsUser marks the failures a notification is for`() {
        assertThat(DriveBackupFailure.entries.filter { it.needsUser })
            .containsExactly(
                DriveBackupFailure.AUTHORIZATION_NEEDED,
                DriveBackupFailure.STORAGE_FULL,
                DriveBackupFailure.PASSPHRASE_UNAVAILABLE,
            )
    }
}
