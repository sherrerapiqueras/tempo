package com.mandrecode.tempo.infrastructure.reminders.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.mandrecode.tempo.core.di.IoDispatcher
import com.mandrecode.tempo.infrastructure.receivers.launchReceiverWork
import com.mandrecode.tempo.infrastructure.reminders.MissedReminderCatchUpRunner
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineDispatcher
import javax.inject.Inject

/**
 * Daily catch-up for reminders the user never saw: re-posts the reminder notification of every
 * task that is still incomplete and whose reminder time has already passed, then arms tomorrow's
 * catch-up. Read-only — no task is modified, so periodic rollover behavior is untouched.
 */
@AndroidEntryPoint
class MissedReminderCatchUpReceiver : BroadcastReceiver() {
    @Inject
    lateinit var catchUpRunner: MissedReminderCatchUpRunner

    @Inject
    @IoDispatcher
    lateinit var ioDispatcher: CoroutineDispatcher

    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        launchReceiverWork(ioDispatcher, "MissedReminderCatchUpReceiver") {
            catchUpRunner.run()
        }
    }
}
