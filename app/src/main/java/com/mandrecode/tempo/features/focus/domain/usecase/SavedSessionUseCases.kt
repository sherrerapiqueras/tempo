package com.mandrecode.tempo.features.focus.domain.usecase

import com.mandrecode.tempo.features.focus.domain.model.SavedSession
import com.mandrecode.tempo.features.focus.domain.model.SavedSessionEntry
import com.mandrecode.tempo.features.focus.domain.repository.SavedSessionRepository
import com.mandrecode.tempo.features.tasks.domain.model.Task
import com.mandrecode.tempo.features.tasks.domain.repository.TaskRepository
import jakarta.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

/**
 * The saved sessions, each with the task it points at.
 *
 * The link is resolved here, against every task rather than the day's agenda: a session may be for
 * work that is not on today at all, and Focus still has to say what it is for. A link whose task
 * has been deleted resolves to nothing, which is how the session comes to stand alone again.
 */
class GetSavedSessionsUseCase
    @Inject
    constructor(
        private val savedSessionRepository: SavedSessionRepository,
        private val taskRepository: TaskRepository,
    ) {
        operator fun invoke(): Flow<List<SavedSessionEntry>> =
            combine(savedSessionRepository.getAll(), taskRepository.getAllTasks()) { sessions, tasks ->
                val tasksById = tasks.associateBy(Task::id)
                sessions.map { session ->
                    SavedSessionEntry(session = session, linkedTask = session.taskId?.let(tasksById::get))
                }
            }
    }

/**
 * Creates a session, or saves changes to one that exists.
 *
 * A session with no name is not saved: the name is the only thing that tells two of them apart on
 * the row. Everything else is brought inside what a timer can run rather than refused.
 */
class SaveSavedSessionUseCase
    @Inject
    constructor(
        private val savedSessionRepository: SavedSessionRepository,
    ) {
        /** @return the session as stored, with its id, or `null` when it has no name. */
        suspend operator fun invoke(session: SavedSession): SavedSession? {
            val normalized = session.normalized()
            if (normalized.title.isEmpty()) return null
            return if (normalized.id == 0L) {
                normalized.copy(id = savedSessionRepository.insert(normalized))
            } else {
                savedSessionRepository.update(normalized)
                normalized
            }
        }
    }

class DeleteSavedSessionUseCase
    @Inject
    constructor(
        private val savedSessionRepository: SavedSessionRepository,
    ) {
        suspend operator fun invoke(id: Long) = savedSessionRepository.delete(id)
    }

/**
 * The tasks a session can be linked to: open, top-level work, by name.
 *
 * Subtasks are left out. They are steps of something, and the something is what a session is for.
 */
class GetLinkableTasksUseCase
    @Inject
    constructor(
        private val taskRepository: TaskRepository,
    ) {
        operator fun invoke(): Flow<List<Task>> =
            taskRepository.getAllTasks().map { tasks ->
                tasks
                    .filter { !it.isCompleted && it.parentTaskId == null }
                    .sortedBy { it.title.lowercase() }
            }
    }
