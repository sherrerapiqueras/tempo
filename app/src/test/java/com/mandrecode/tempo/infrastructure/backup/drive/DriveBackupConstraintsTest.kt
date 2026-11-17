package com.mandrecode.tempo.infrastructure.backup.drive

import androidx.work.NetworkType
import com.google.common.truth.Truth.assertThat
import com.mandrecode.tempo.features.backup.domain.model.DriveBackupSettings
import org.junit.Test

class DriveBackupConstraintsTest {
    @Test
    fun `wifi only waits for an unmetered network, and never runs on a low battery`() {
        val settings = DriveBackupSettings(enabled = true, unmeteredOnly = true)
        val constraints = DriveBackupSchedulerImpl.constraints(settings)

        assertThat(constraints.requiredNetworkType).isEqualTo(NetworkType.UNMETERED)
        assertThat(constraints.requiresBatteryNotLow()).isTrue()
    }

    @Test
    fun `any network runs on mobile data too`() {
        val settings = DriveBackupSettings(enabled = true, unmeteredOnly = false)
        val constraints = DriveBackupSchedulerImpl.constraints(settings)

        assertThat(constraints.requiredNetworkType).isEqualTo(NetworkType.CONNECTED)
    }
}
