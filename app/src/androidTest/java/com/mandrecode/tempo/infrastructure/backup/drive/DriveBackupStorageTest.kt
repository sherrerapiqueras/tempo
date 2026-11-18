package com.mandrecode.tempo.infrastructure.backup.drive

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.mandrecode.tempo.features.backup.domain.model.BackupFrequency
import com.mandrecode.tempo.features.backup.domain.model.DriveBackupFailure
import com.mandrecode.tempo.features.backup.domain.model.DriveBackupSettings
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** The Keystore-backed passphrase store and the preferences, on a real device (#306). */
@RunWith(AndroidJUnit4::class)
class DriveBackupStorageTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val store = KeystoreBackupPassphraseStore(context)

    @Before
    @After
    fun clean() {
        store.clear()
        context.deleteSharedPreferences(SharedPrefsDriveBackupPreferences.PREFS_NAME)
    }

    @Test
    fun aPassphraseRoundTripsThroughTheKeystore() {
        store.save("correct horse ñ ✓".toCharArray())

        assertThat(store.read()?.let(::String)).isEqualTo("correct horse ñ ✓")
    }

    @Test
    fun theStoredBlobIsNotThePassphrase() {
        store.save("plain-secret".toCharArray())

        val raw = context.getSharedPreferences(KeystoreBackupPassphraseStore.PREFS_FILE, Context.MODE_PRIVATE).all.toString()
        assertThat(raw).doesNotContain("plain-secret")
    }

    @Test
    fun clearingForgetsIt() {
        store.save("secret".toCharArray())

        store.clear()

        assertThat(store.read()).isNull()
    }

    @Test
    fun replacingThePassphraseReadsTheNewOne() {
        store.save("old".toCharArray())
        store.save("new".toCharArray())

        assertThat(store.read()?.let(::String)).isEqualTo("new")
    }

    @Test
    fun aBlobWhoseKeyIsGoneReadsAsNoPassphrase() {
        store.save("secret".toCharArray())
        ALIASES.forEach { keyStore().deleteEntry(it) }

        assertThat(store.read()).isNull()
    }

    @Test
    fun aChangeRetiresTheOldKeyOnlyAfterTheNewOneWorks() {
        store.save("first".toCharArray())
        val firstAlias = ALIASES.single { keyStore().containsAlias(it) }

        store.save("second".toCharArray())

        assertThat(keyStore().containsAlias(firstAlias)).isFalse()
        assertThat(ALIASES.count { keyStore().containsAlias(it) }).isEqualTo(1)
        assertThat(store.read()?.let(::String)).isEqualTo("second")
    }

    @Test
    fun clearingRemovesEveryKey() {
        store.save("first".toCharArray())
        store.save("second".toCharArray())

        store.clear()

        assertThat(ALIASES.none { keyStore().containsAlias(it) }).isTrue()
    }

    private fun keyStore() =
        java.security.KeyStore
            .getInstance("AndroidKeyStore")
            .apply { load(null) }

    @Test
    fun settingsAndStatusSurviveANewInstance() {
        SharedPrefsDriveBackupPreferences(context).apply {
            updateSettings(DriveBackupSettings(enabled = true, frequency = BackupFrequency.WEEKLY, unmeteredOnly = false))
            recordSuccess(1_000)
            recordFailure(DriveBackupFailure.STORAGE_FULL, 2_000)
        }

        val reread = SharedPrefsDriveBackupPreferences(context)

        assertThat(reread.settings.value)
            .isEqualTo(DriveBackupSettings(enabled = true, frequency = BackupFrequency.WEEKLY, unmeteredOnly = false))
        assertThat(reread.status.value.lastSuccessAt).isEqualTo(1_000)
        assertThat(reread.status.value.currentFailure).isEqualTo(DriveBackupFailure.STORAGE_FULL)
    }

    private companion object {
        val ALIASES = listOf("tempo_backup_passphrase_key", "tempo_backup_passphrase_key_b")
    }
}
