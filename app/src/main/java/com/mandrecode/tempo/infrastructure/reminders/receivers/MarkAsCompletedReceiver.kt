package com.mandrecode.tempo.infrastructure.reminders.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.mandrecode.tempo.features.tasks.domain.repository.TaskRepository
import com.mandrecode.tempo.features.tasks.domain.usecase.ToggleTaskCompletionUseCase
import com.mandrecode.tempo.infrastructure.receivers.launchReceiverWork
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.datetime.LocalDateTime
import javax.inject.Inject

@AndroidEntryPoint
class MarkAsCompletedReceiver : BroadcastReceiver() {
    @Inject
    lateinit var taskRepository: TaskRepository

    @Inject
    lateinit var toggleTaskCompletionUseCase: ToggleTaskCompletionUseCase

    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        val taskId = intent.getLongExtra(TaskReminderReceiver.EXTRA_TASK_ID, 0)
        if (taskId != 0L) {
            launchReceiverWork(Dispatchers.IO, "MarkAsCompletedReceiver") {
                val task = taskRepository.getTaskById(taskId)
                if (task != null && !task.isCompleted) {
                    // For periodic tasks, TaskReminderReceiver already advanced
                    // reminderDate to the next occurrence. Restore the original
                    // so the use case computes the correct next instance date.
                    val originalReminderDate =
                        intent
                            .getStringExtra(EXTRA_ORIGINAL_REMINDER_DATE)
                            ?.let { runCatching { LocalDateTime.parse(it) }.getOrNull() }
                    val taskToComplete =
                        if (originalReminderDate != null && task.periodicity != null) {
                            task.copy(reminderDate = originalReminderDate)
                        } else {
                            task
                        }
                    toggleTaskCompletionUseCase(taskToComplete)
                }
            }
        }
    }

    companion object {
        const val EXTRA_ORIGINAL_REMINDER_DATE = "ORIGINAL_REMINDER_DATE"
    }
}
