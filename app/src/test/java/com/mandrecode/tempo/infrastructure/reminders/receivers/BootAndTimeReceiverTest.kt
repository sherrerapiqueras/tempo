package com.mandrecode.tempo.infrastructure.reminders.receivers

import android.content.Intent
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class BootAndTimeReceiverTest {
    @Test
    fun `shouldRescheduleReminders returns true for boot completed`() {
        val result = BootAndTimeReceiver.shouldRescheduleReminders(Intent.ACTION_BOOT_COMPLETED)

        assertThat(result).isTrue()
    }

    @Test
    fun `shouldRescheduleReminders returns true for timezone changed`() {
        val result = BootAndTimeReceiver.shouldRescheduleReminders(Intent.ACTION_TIMEZONE_CHANGED)

        assertThat(result).isTrue()
    }

    @Test
    fun `shouldRescheduleReminders returns true for time changed`() {
        val result = BootAndTimeReceiver.shouldRescheduleReminders(Intent.ACTION_TIME_CHANGED)

        assertThat(result).isTrue()
    }

    @Test
    fun `shouldRescheduleReminders returns true for app update`() {
        val result = BootAndTimeReceiver.shouldRescheduleReminders(Intent.ACTION_MY_PACKAGE_REPLACED)

        assertThat(result).isTrue()
    }

    @Test
    fun `shouldRescheduleReminders returns true when exact alarm access is granted`() {
        val result =
            BootAndTimeReceiver.shouldRescheduleReminders(
                "android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED",
            )

        assertThat(result).isTrue()
    }

    @Test
    fun `shouldRescheduleReminders returns false for unrelated action`() {
        val result = BootAndTimeReceiver.shouldRescheduleReminders("some.other.action")

        assertThat(result).isFalse()
    }

    @Test
    fun `shouldRescheduleReminders returns false for null action`() {
        val result = BootAndTimeReceiver.shouldRescheduleReminders(null)

        assertThat(result).isFalse()
    }
}
