package com.mandrecode.tempo.infrastructure.focus

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.mandrecode.tempo.core.di.IoDispatcher
import com.mandrecode.tempo.features.focus.domain.repository.FocusSessionRepository
import com.mandrecode.tempo.features.focus.domain.usecase.FocusSessionUseCases
import com.mandrecode.tempo.features.tasks.domain.repository.TaskRepository
import com.mandrecode.tempo.features.tasks.domain.usecase.ToggleTaskCompletionUseCase
import com.mandrecode.tempo.infrastructure.notifications.RequestCodeGenerator
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * The session's own controls, from the notification.
 *
 * The same three the card and the session screen offer, so a running session can be finished,
 * paused or abandoned without opening the app — which is the point of the notification being there
 * at all.
 */
@AndroidEntryPoint
class FocusSessionActionReceiver : BroadcastReceiver() {
    @Inject
    lateinit var focusSessionUseCases: FocusSessionUseCases

    @Inject
    lateinit var sessionRepository: FocusSessionRepository

    @Inject
    lateinit var taskRepository: TaskRepository

    @Inject
    lateinit var toggleTaskCompletion: ToggleTaskCompletionUseCase

    @Inject
    @IoDispatcher
    lateinit var ioDispatcher: CoroutineDispatcher

    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        val action = intent.action ?: return
        val pendingResult = goAsync()
        CoroutineScope(ioDispatcher).launch {
            try {
                when (action) {
                    ACTION_PAUSE -> togglePause()
                    ACTION_STOP -> focusSessionUseCases.end()
                    ACTION_COMPLETE -> completeSessionTask()
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    /**
     * One action for both, because the notification only ever shows whichever the session is not
     * already doing — two buttons where one is always meaningless would be one too many.
     */
    private fun togglePause() {
        val session = sessionRepository.activeSession.value ?: return
        if (session.isPaused) focusSessionUseCases.resume() else focusSessionUseCases.pause()
    }

    /** Finishing the work finishes the session with it, the same as pressing it in the app. */
    private suspend fun completeSessionTask() {
        val session = sessionRepository.activeSession.value ?: return
        val task = session.taskId?.let { taskRepository.getTaskById(it) }
        if (task != null && !task.isCompleted) {
            toggleTaskCompletion(task)
        }
        focusSessionUseCases.end()
    }

    companion object {
        const val ACTION_PAUSE = "com.mandrecode.tempo.action.FOCUS_SESSION_PAUSE"
        const val ACTION_STOP = "com.mandrecode.tempo.action.FOCUS_SESSION_STOP"
        const val ACTION_COMPLETE = "com.mandrecode.tempo.action.FOCUS_SESSION_COMPLETE"

        /** Distinct request codes, or the three intents would overwrite each other. */
        fun pendingIntent(
            context: Context,
            action: String,
        ): PendingIntent =
            PendingIntent.getBroadcast(
                context,
                RequestCodeGenerator.forFocusSessionAction(action),
                Intent(context, FocusSessionActionReceiver::class.java).setAction(action),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
    }
}
