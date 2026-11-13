package com.mandrecode.tempo.features.focus.domain.usecase

import com.mandrecode.tempo.features.focus.domain.repository.FocusSessionRepository
import com.mandrecode.tempo.features.tasks.domain.repository.TaskRepository
import jakarta.inject.Inject
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Starts a focus session on a task known only by its id — the entry point for a task reminder's
 * Start session action, which runs in the background with nothing but the id in its intent.
 *
 * The title is read from the task rather than carried in the intent, so a task renamed after its
 * reminder fired still runs under the name the user now knows it by.
 */
class StartFocusSessionOnTaskUseCase
    @Inject
    constructor(
        private val taskRepository: TaskRepository,
        private val sessionRepository: FocusSessionRepository,
        private val focusSessionUseCases: FocusSessionUseCases,
    ) {
        /** @return whether a session is now running on [taskId]. */
        suspend operator fun invoke(taskId: Long): Boolean =
            // Each tap arrives as its own broadcast on its own coroutine, so two reminders pressed
            // in quick succession would otherwise both read the old session and both replace it.
            startLock.withLock { startLocked(taskId) }

        private suspend fun startLocked(taskId: Long): Boolean {
            // A reminder can outlive its task's open state: it may have been ticked off in the app
            // since, and then there is no work left to focus on.
            val task = taskRepository.getTaskById(taskId)?.takeIf { !it.isCompleted } ?: return false

            // A session whose time ran out while its end alarm was still on its way is over, not
            // something to carry on with.
            focusSessionUseCases.reconcile()

            // Pressing start on the work already under way is carrying on, not starting over —
            // restarting would throw away the time already on the clock. A paused one is picked
            // back up, since start is what was asked for.
            //
            // In the rest between this task's rounds it is the next round that is asked for: the
            // run is the task's own, and a fresh single session would throw its rounds away.
            val running = sessionRepository.activeSession.value?.takeIf { it.taskId == taskId }
            if (running != null && !running.isBreak) {
                focusSessionUseCases.resume()
            } else if (running == null || !focusSessionUseCases.skipBreak()) {
                focusSessionUseCases.start(taskId = task.id, title = task.title)
                // The same as starting from the card: once running, the task is no longer a preview.
                sessionRepository.setPreviewTaskId(null)
            }
            return true
        }

        private companion object {
            /** Process-wide: the use case is unscoped, so an instance lock would guard nothing. */
            val startLock = Mutex()
        }
    }
