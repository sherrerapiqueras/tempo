package com.mandrecode.tempo.infrastructure.backup.drive

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.WorkManager
import com.google.common.truth.Truth.assertThat
import com.mandrecode.tempo.features.backup.domain.model.DriveBackupFailure
import com.mandrecode.tempo.features.backup.domain.model.DriveBackupSettings
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Scheduling side effects of turning Drive backup off (#306), on a real device. */
@RunWith(AndroidJUnit4::class)
class DriveBackupSchedulerTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val notifier = DriveBackupNotifier(context)
    private val scheduler = DriveBackupSchedulerImpl(WorkManager.getInstance(context), notifier)

    @Before
    fun allowNotifications() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            InstrumentationRegistry
                .getInstrumentation()
                .uiAutomation
                .grantRuntimePermission(context.packageName, Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    @Test
    fun turningBackupOffRemovesTheBackupStoppedNotification() {
        notifier.notifyFailure(DriveBackupFailure.AUTHORIZATION_NEEDED)
        assertThat(waitFor { backupNotifications().isNotEmpty() }).isTrue()

        scheduler.sync(DriveBackupSettings(enabled = false))

        assertThat(waitFor { backupNotifications().isEmpty() }).isTrue()
    }

    /** Posting and cancelling reach the notification service asynchronously. */
    private fun waitFor(condition: () -> Boolean): Boolean {
        val deadline = System.currentTimeMillis() + WAIT_MILLIS
        while (System.currentTimeMillis() < deadline) {
            if (condition()) return true
            Thread.sleep(POLL_MILLIS)
        }
        return condition()
    }

    private fun backupNotifications() =
        context
            .getSystemService(NotificationManager::class.java)
            .activeNotifications
            .filter { it.notification.channelId == DriveBackupNotifier.CHANNEL_ID }

    private companion object {
        const val WAIT_MILLIS = 3_000L
        const val POLL_MILLIS = 50L
    }
}
