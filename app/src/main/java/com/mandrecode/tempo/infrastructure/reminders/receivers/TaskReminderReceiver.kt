package com.mandrecode.tempo.infrastructure.reminders.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.annotation.VisibleForTesting
import com.mandrecode.tempo.core.di.IoDispatcher
import com.mandrecode.tempo.features.focus.domain.usecase.HasFocusTimeTodayUseCase
import com.mandrecode.tempo.features.tasks.domain.model.Task
import com.mandrecode.tempo.features.tasks.domain.repository.TaskRepository
import com.mandrecode.tempo.features.tasks.domain.usecase.RollOverduePeriodicTaskUseCase
import com.mandrecode.tempo.infrastructure.notifications.TaskReminderNotifier
import com.mandrecode.tempo.infrastructure.receivers.launchReceiverWork
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineDispatcher
import javax.inject.Inject

@AndroidEntryPoint
class TaskReminderReceiver : BroadcastReceiver() {
    @Inject
    lateinit var taskRepository: TaskRepository

    @Inject
    lateinit var rollOverduePeriodicTaskUseCase: RollOverduePeriodicTaskUseCase

    @Inject
    lateinit var taskReminderNotifier: TaskReminderNotifier

    @Inject
    lateinit var hasFocusTimeToday: HasFocusTimeTodayUseCase

    @Inject
    @IoDispatcher
    lateinit var ioDispatcher: CoroutineDispatcher

    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        val taskId = intent.getLongExtra(EXTRA_TASK_ID, -1L)
        if (taskId == -1L) return

        launchReceiverWork(ioDispatcher, "TaskReminderReceiver") {
            val task = taskRepository.getTaskById(taskId)
            if (shouldProcessTaskReminder(task)) {
                val activeTask = requireNotNull(task)
                val outcome = reminderOutcome(activeTask, hasFocusTimeToday(activeTask.id))

                if (outcome.notify) {
                    taskReminderNotifier.notify(activeTask)
                }
                // Preserve the overdue occurrence and schedule a linked next instance.
                if (outcome.rollOver) {
                    rollOverduePeriodicTaskUseCase(activeTask)
                }
            }
        }
    }

    /** The two things an alarm can do about a task, decided apart from each other. */
    @VisibleForTesting
    internal data class ReminderOutcome(
        val notify: Boolean,
        val rollOver: Boolean,
    )

    companion object {
        const val EXTRA_TASK_ID = "TASK_ID"
        const val EXTRA_OPEN_TASKS = "OPEN_TASKS"

        @VisibleForTesting
        internal fun shouldProcessTaskReminder(task: Task?): Boolean = task != null && !task.isCompleted

        /**
         * Focus time silences the notification and nothing else. Rollover is bookkeeping the alarm
         * owes the task whether or not it also speaks: dropping it because the user had a session
         * would strand a periodic task on an occurrence it has already passed, which is a data bug
         * wearing a notification bug's clothes.
         */
        @VisibleForTesting
        internal fun reminderOutcome(
            task: Task,
            hasFocusTimeToday: Boolean,
        ) = ReminderOutcome(
            notify = !hasFocusTimeToday,
            rollOver = task.periodicity != null && task.parentTaskId == null,
        )
    }
}
