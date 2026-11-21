package com.mandrecode.tempo.infrastructure.focus

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.mandrecode.tempo.core.di.IoDispatcher
import com.mandrecode.tempo.features.focus.domain.usecase.StartFocusSessionOnTaskUseCase
import com.mandrecode.tempo.infrastructure.notifications.NotificationSyncManager
import com.mandrecode.tempo.infrastructure.receivers.launchReceiverWork
import com.mandrecode.tempo.infrastructure.reminders.receivers.TaskReminderReceiver
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineDispatcher
import javax.inject.Inject

/**
 * A task reminder's Start session action: begins a focus session on the reminded task without
 * opening the app. The session's own ongoing notification takes the reminder's place, so it is the
 * confirmation that something happened.
 */
@AndroidEntryPoint
class StartFocusSessionReceiver : BroadcastReceiver() {
    @Inject
    lateinit var startFocusSessionOnTask: StartFocusSessionOnTaskUseCase

    @Inject
    lateinit var notificationSyncManager: NotificationSyncManager

    @Inject
    @IoDispatcher
    lateinit var ioDispatcher: CoroutineDispatcher

    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        val taskId = taskIdFrom(intent) ?: return
        // Whatever the outcome, the user has answered the reminder — leaving it in the shade next
        // to the session it just started would be the same task announced twice.
        notificationSyncManager.dismissTaskNotification(taskId)

        launchReceiverWork(ioDispatcher, "StartFocusSessionReceiver") {
            startFocusSessionOnTask(taskId)
        }
    }

    companion object {
        fun taskIdFrom(intent: Intent): Long? =
            intent
                .getLongExtra(TaskReminderReceiver.EXTRA_TASK_ID, 0L)
                .takeIf { it != 0L }
    }
}
