package com.mandrecode.tempo.infrastructure.reminders.receivers

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.BackoffPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkRequest
import com.mandrecode.tempo.infrastructure.reminders.workers.RescheduleRemindersWorker
import dagger.hilt.android.AndroidEntryPoint
import java.util.concurrent.TimeUnit

@AndroidEntryPoint
class BootAndTimeReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        if (shouldRescheduleReminders(intent.action)) {
            val workRequest =
                OneTimeWorkRequestBuilder<RescheduleRemindersWorker>()
                    .setBackoffCriteria(
                        BackoffPolicy.LINEAR,
                        WorkRequest.MIN_BACKOFF_MILLIS,
                        TimeUnit.MILLISECONDS,
                    ).build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                "BootAndTimeRescheduleRemindersWorker",
                ExistingWorkPolicy.REPLACE,
                workRequest,
            )
        }
    }

    companion object {
        /**
         * [AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED] is sent when the user
         * grants exact-alarm access (Android 12+). Without the grant the schedulers skip every
         * reminder, so whatever was skipped while it was missing — including after the user turned
         * it off and on again in system settings, which also clears every armed alarm — is only
         * armed once the app is next opened or the daily refresh runs, unless this re-arms it.
         * The literal matches the constant, which needs API 31 to reference.
         */
        private const val ACTION_EXACT_ALARM_PERMISSION_CHANGED =
            "android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED"

        fun shouldRescheduleReminders(action: String?): Boolean =
            action == Intent.ACTION_BOOT_COMPLETED ||
                action == Intent.ACTION_TIMEZONE_CHANGED ||
                action == Intent.ACTION_TIME_CHANGED ||
                action == Intent.ACTION_MY_PACKAGE_REPLACED ||
                action == ACTION_EXACT_ALARM_PERMISSION_CHANGED
    }
}
