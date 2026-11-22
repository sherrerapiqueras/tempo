package com.mandrecode.tempo.features.widget.presentation

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.updateAll

/**
 * A row's check: completes the task, or reopens a completed one, through the same use case as a tap
 * in the app, so rollover, reminders and periodicity behave identically.
 *
 * The task is read again rather than trusted from the widget's snapshot: the snapshot may be minutes
 * old, and toggling a task that has since been completed elsewhere would reopen it.
 */
class ToggleTaskFromWidgetAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        val taskId = parameters[TaskIdKey] ?: return
        val expectedCompleted = parameters[WasCompletedKey] ?: return
        val graph = TodayWidgetEntryPoint.from(context)
        val task = graph.taskRepository().getTaskById(taskId)
        if (task != null && task.isCompleted == expectedCompleted) {
            graph.toggleTaskCompletion()(task)
        }
        // The updater redraws on the change too; this makes the tapped widget answer at once.
        TodayWidget().updateAll(context)
    }

    companion object {
        val TaskIdKey = ActionParameters.Key<Long>("widget_task_id")
        val WasCompletedKey = ActionParameters.Key<Boolean>("widget_task_was_completed")
    }
}
