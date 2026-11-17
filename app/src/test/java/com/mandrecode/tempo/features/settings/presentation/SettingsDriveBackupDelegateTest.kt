package com.mandrecode.tempo.features.settings.presentation

import android.app.PendingIntent
import android.content.Intent
import com.google.common.truth.Truth.assertThat
import com.mandrecode.tempo.features.backup.domain.model.BackupFrequency
import com.mandrecode.tempo.features.backup.domain.model.DriveBackupFailure
import com.mandrecode.tempo.features.backup.domain.repository.BackupPassphraseStore
import com.mandrecode.tempo.features.backup.domain.repository.CloudBackupException
import com.mandrecode.tempo.features.backup.domain.usecase.ChangeBackupPassphraseUseCase
import com.mandrecode.tempo.features.backup.domain.usecase.DisableDriveBackupUseCase
import com.mandrecode.tempo.features.backup.domain.usecase.EnableDriveBackupUseCase
import com.mandrecode.tempo.features.backup.domain.usecase.FakeAuthorization
import com.mandrecode.tempo.features.backup.domain.usecase.FakeCloudStore
import com.mandrecode.tempo.features.backup.domain.usecase.FakeDrivePreferences
import com.mandrecode.tempo.features.backup.domain.usecase.FakePassphraseStore
import com.mandrecode.tempo.features.backup.domain.usecase.FakeScheduler
import com.mandrecode.tempo.features.backup.domain.usecase.RefreshDriveBackupStatusUseCase
import com.mandrecode.tempo.features.backup.domain.usecase.RunDriveBackupNowUseCase
import com.mandrecode.tempo.features.backup.domain.usecase.SteppingClock
import com.mandrecode.tempo.features.backup.domain.usecase.UpdateDriveBackupScheduleUseCase
import com.mandrecode.tempo.features.backup.presentation.driveRestoreFlow
import com.mandrecode.tempo.infrastructure.backup.drive.GoogleDriveAuthorization
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Test
import com.mandrecode.tempo.features.settings.presentation.SettingsContract.DriveBackupDialog as Dialog
import com.mandrecode.tempo.features.settings.presentation.SettingsContract.UiEvent.DriveBackup as Event

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsDriveBackupDelegateTest {
    private val store = FakePassphraseStore()
    private val preferences = FakeDrivePreferences()
    private val scheduler = FakeScheduler()
    private val authorization = mockk<GoogleDriveAuthorization>()
    private val delegate = driveBackupDelegate(authorization, store, preferences, scheduler)
    private var state = SettingsContract.UiState()
    private val effects = mutableListOf<SettingsContract.UiEffect>()

    private fun TestScope.host() =
        SettingsBackupDelegate.Host(
            scope = backgroundScope,
            updateState = { state = it(state) },
            sendEffect = { effects += it },
        )

    @Test
    fun `turning on with access already granted asks for the passphrase, then enables`() =
        runTest(UnconfinedTestDispatcher()) {
            coEvery { authorization.consentIntent() } returns null
            val host = host()

            delegate.onEvent(Event.TurnOnClicked, host)
            assertThat(state.driveBackupDialog).isEqualTo(Dialog.SetPassphrase(isChange = false))

            delegate.onEvent(Event.PassphraseConfirmed("pw".toCharArray(), "pw".toCharArray()), host)

            assertThat(state.driveBackupDialog).isNull()
            assertThat(store.stored).isEqualTo("pw")
            assertThat(preferences.settings.value.enabled).isTrue()
            assertThat(scheduler.runNowCalls).isEqualTo(1)
        }

    @Test
    fun `turning on shows Google's consent, and only a granted result moves on`() =
        runTest(UnconfinedTestDispatcher()) {
            val consent = mockk<PendingIntent>()
            coEvery { authorization.consentIntent() } returns consent
            val host = host()

            delegate.onEvent(Event.TurnOnClicked, host)
            assertThat(effects.single()).isEqualTo(SettingsContract.UiEffect.LaunchDriveConsent(consent))

            every { authorization.isGranted(null) } returns false
            delegate.onEvent(Event.ConsentFinished(null), host)
            assertThat(state.driveBackupDialog).isNull()

            val granted = mockk<Intent>()
            every { authorization.isGranted(granted) } returns true
            delegate.onEvent(Event.TurnOnClicked, host)
            delegate.onEvent(Event.ConsentFinished(granted), host)
            assertThat(state.driveBackupDialog).isEqualTo(Dialog.SetPassphrase(isChange = false))
        }

    @Test
    fun `when Drive cannot be authorised at all, it says so`() =
        runTest(UnconfinedTestDispatcher()) {
            coEvery { authorization.consentIntent() } throws CloudBackupException(DriveBackupFailure.UNEXPECTED)

            delegate.onEvent(Event.TurnOnClicked, host())

            assertThat(state.driveBackupDialog).isEqualTo(Dialog.Unavailable)
        }

    @Test
    fun `reconnecting backs up again once access is granted`() =
        runTest(UnconfinedTestDispatcher()) {
            preferences.updateSettings(preferences.settings.value.copy(enabled = true))
            coEvery { authorization.consentIntent() } returns null

            delegate.onEvent(Event.ReconnectClicked, host())

            assertThat(scheduler.runNowCalls).isEqualTo(1)
            assertThat(state.driveBackupDialog).isNull()
        }

    @Test
    fun `a mismatched passphrase keeps the dialog open and stores nothing`() =
        runTest(UnconfinedTestDispatcher()) {
            coEvery { authorization.consentIntent() } returns null
            val host = host()
            delegate.onEvent(Event.TurnOnClicked, host)

            delegate.onEvent(Event.PassphraseConfirmed("a".toCharArray(), "b".toCharArray()), host)

            assertThat(state.driveBackupDialog).isEqualTo(Dialog.SetPassphrase(isChange = false))
            assertThat(store.stored).isNull()
        }

    @Test
    fun `changing the passphrase replaces it and backs up now`() =
        runTest(UnconfinedTestDispatcher()) {
            store.stored = "old"
            preferences.updateSettings(preferences.settings.value.copy(enabled = true))
            val host = host()

            delegate.onEvent(Event.ChangePassphraseClicked, host)
            delegate.onEvent(Event.PassphraseConfirmed("new".toCharArray(), "new".toCharArray()), host)

            assertThat(store.stored).isEqualTo("new")
            assertThat(scheduler.runNowCalls).isEqualTo(1)
        }

    @Test
    fun `frequency, wifi only and turning off update the settings`() =
        runTest(UnconfinedTestDispatcher()) {
            store.stored = "pw"
            preferences.updateSettings(preferences.settings.value.copy(enabled = true))
            val host = host()

            delegate.onEvent(Event.FrequencyClicked, host)
            assertThat(state.driveBackupDialog).isEqualTo(Dialog.ChooseFrequency)
            delegate.onEvent(Event.FrequencySelected(BackupFrequency.WEEKLY), host)
            delegate.onEvent(Event.WifiOnlyToggled(false), host)
            assertThat(preferences.settings.value.frequency).isEqualTo(BackupFrequency.WEEKLY)
            assertThat(preferences.settings.value.unmeteredOnly).isFalse()

            delegate.onEvent(Event.TurnOffClicked, host)
            assertThat(state.driveBackupDialog).isEqualTo(Dialog.ConfirmTurnOff)
            delegate.onEvent(Event.TurnOffConfirmed, host)
            assertThat(preferences.settings.value.enabled).isFalse()
            assertThat(store.stored).isNull()
        }

    @Test
    fun `back up now runs one and says so`() =
        runTest(UnconfinedTestDispatcher()) {
            preferences.updateSettings(preferences.settings.value.copy(enabled = true))

            delegate.onEvent(Event.BackUpNowClicked, host())

            assertThat(scheduler.runNowCalls).isEqualTo(1)
            assertThat(effects.single()).isInstanceOf(SettingsContract.UiEffect.ShowMessage::class.java)
        }

    @Test
    fun `state mirrors the stored settings and only a failure newer than the last success`() =
        runTest(UnconfinedTestDispatcher()) {
            delegate.start(host())
            preferences.updateSettings(preferences.settings.value.copy(enabled = true))
            preferences.recordFailure(DriveBackupFailure.STORAGE_FULL, atMillis = 10)
            assertThat(state.driveBackup.failure).isEqualTo(DriveBackupFailure.STORAGE_FULL)

            preferences.recordSuccess(atMillis = 20)

            assertThat(state.driveBackup.enabled).isTrue()
            assertThat(state.driveBackup.lastSuccessAt).isEqualTo(20)
            assertThat(state.driveBackup.failure).isNull()
        }

    @Test
    fun `a passphrase the Keystore cannot store is reported instead of crashing`() =
        runTest(UnconfinedTestDispatcher()) {
            coEvery { authorization.consentIntent() } returns null
            val failingStore =
                object : BackupPassphraseStore {
                    override fun save(passphrase: CharArray) = throw java.security.ProviderException("no keystore")

                    override fun read(): CharArray? = null

                    override fun clear() = Unit
                }
            val failing =
                SettingsDriveBackupDelegate(
                    authorization = authorization,
                    preferences = preferences,
                    enable = EnableDriveBackupUseCase(failingStore, preferences, scheduler),
                    changePassphrase = ChangeBackupPassphraseUseCase(failingStore, preferences, scheduler),
                    disable = DisableDriveBackupUseCase(failingStore, preferences, scheduler),
                    updateSchedule = UpdateDriveBackupScheduleUseCase(preferences, scheduler),
                    backUpNow = RunDriveBackupNowUseCase(preferences, scheduler),
                    restoreFlow = driveRestoreFlow(preferences = preferences, scheduler = scheduler),
                    refreshStatus = refreshStatus(preferences),
                    ioDispatcher = UnconfinedTestDispatcher(testScheduler),
                )
            val host = host()
            failing.onEvent(Event.TurnOnClicked, host)

            failing.onEvent(Event.PassphraseConfirmed("pw".toCharArray(), "pw".toCharArray()), host)

            assertThat(preferences.settings.value.enabled).isFalse()
            assertThat(state.driveBackupDialog).isNull()
            assertThat((effects.last() as SettingsContract.UiEffect.ShowMessage).message)
                .isEqualTo(com.mandrecode.tempo.R.string.drive_backup_passphrase_store_failed)
        }

    @Test
    fun `a full Drive opens Google storage`() =
        runTest(UnconfinedTestDispatcher()) {
            delegate.onEvent(Event.ManageStorageClicked, host())

            assertThat(effects.single()).isEqualTo(SettingsContract.UiEffect.OpenGoogleStorage)
        }

    @Test
    fun `dismissing closes the dialog`() =
        runTest(UnconfinedTestDispatcher()) {
            val host = host()
            delegate.onEvent(Event.TurnOffClicked, host)

            delegate.onEvent(Event.DialogDismissed, host)

            assertThat(state.driveBackupDialog).isNull()
        }
}

/** A delegate on fakes, also used by the ViewModel tests that build Settings whole. */
internal fun driveBackupDelegate(
    authorization: GoogleDriveAuthorization = mockk(relaxed = true),
    store: FakePassphraseStore = FakePassphraseStore(),
    preferences: FakeDrivePreferences = FakeDrivePreferences(),
    scheduler: FakeScheduler = FakeScheduler(),
) = SettingsDriveBackupDelegate(
    authorization = authorization,
    preferences = preferences,
    enable = EnableDriveBackupUseCase(store, preferences, scheduler),
    changePassphrase = ChangeBackupPassphraseUseCase(store, preferences, scheduler),
    disable = DisableDriveBackupUseCase(store, preferences, scheduler),
    updateSchedule = UpdateDriveBackupScheduleUseCase(preferences, scheduler),
    backUpNow = RunDriveBackupNowUseCase(preferences, scheduler),
    restoreFlow = driveRestoreFlow(store = store, preferences = preferences, scheduler = scheduler),
    refreshStatus = refreshStatus(preferences),
    ioDispatcher = UnconfinedTestDispatcher(),
)

private fun refreshStatus(preferences: FakeDrivePreferences) =
    RefreshDriveBackupStatusUseCase(preferences, FakeAuthorization(), FakeCloudStore(), SteppingClock())
