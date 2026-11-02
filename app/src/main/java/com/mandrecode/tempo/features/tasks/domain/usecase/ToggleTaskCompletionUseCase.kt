package com.mandrecode.tempo.features.tasks.domain.usecase

import com.mandrecode.tempo.core.domain.model.ScheduleResult
import com.mandrecode.tempo.core.domain.usecase.DailyActivityRecorder
import com.mandrecode.tempo.features.tasks.domain.model.Task
import com.mandrecode.tempo.features.tasks.domain.repository.TaskAttachmentRepository
import com.mandrecode.tempo.features.tasks.domain.repository.TaskRepository
import com.mandrecode.tempo.features.tasks.domain.scheduler.TaskReminderScheduler
import com.mandrecode.tempo.features.tasks.domain.util.TaskReminderDateUtil
import jakarta.inject.Inject
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
class ToggleTaskCompletionUseCase
    @Inject
    constructor(
        private val taskRepository: TaskRepository,
        private val taskReminderScheduler: TaskReminderScheduler,
        private val updateTaskUseCase: UpdateTaskUseCase,
        private val dailyActivityRecorder: DailyActivityRecorder,
        private val taskAttachmentRepository: TaskAttachmentRepository,
    ) {
        sealed class Result {
            data class PeriodicCompleted(
                val updateResult: UpdateTaskUseCase.Result,
            ) : Result()

            data class ParentToggled(
                val isCompleted: Boolean,
                val updateResult: UpdateTaskUseCase.Result,
            ) : Result()

            data class SubtaskToggled(
                val updateResult: UpdateTaskUseCase.Result,
            ) : Result()
        }

        suspend operator fun invoke(task: Task): Result {
            val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())

            val result =
                when {
                    task.periodicity != null && !task.isCompleted && task.parentTaskId == null ->
                        completePeriodic(task, now)
                    task.parentTaskId == null && task.isCompleted && task.nextInstanceId != null ->
                        detachPeriodicCompletion(task)
                    task.parentTaskId == null ->
                        toggleNonPeriodicParent(task, now)
                    else ->
                        toggleSubtask(task, now)
                }

            // Recorded after the toggle so the recount sees the new completion state.
            dailyActivityRecorder.recordToday()
            return result
        }

        private data class PeriodicCommit(
            val archivedTask: Task,
            val nextInstance: Task?,
        )

        // ── Branch A: complete a periodic parent ────────────────────────────────────────
        private suspend fun completePeriodic(
            task: Task,
            now: LocalDateTime,
        ): Result {
            // Snapshot subtasks BEFORE the transaction so the post-commit scheduler calls have what
            // they need.
            val subtasksBefore = taskRepository.getSubtasksSync(task.id)

            val commit =
                taskRepository.runInTransaction {
                    performPeriodicCompletionTxn(task, now, subtasksBefore)
                }

            taskReminderScheduler.cancel(commit.archivedTask)
            subtasksBefore.forEach { subtask ->
                if (subtask.reminderDate != null) taskReminderScheduler.cancel(subtask)
            }
            val scheduleResult = commit.nextInstance?.let { scheduleOrCancel(it) } ?: ScheduleResult.Skipped
            return Result.PeriodicCompleted(UpdateTaskUseCase.Result.Success(scheduleResult))
        }

        private suspend fun performPeriodicCompletionTxn(
            task: Task,
            now: LocalDateTime,
            subtasksBefore: List<Task>,
        ): PeriodicCommit {
            val currentTask = taskRepository.getTaskById(task.id) ?: task

            // 1. Persist archived copy with recurrence stripped. We bypass UpdateTaskUseCase
            //    so the scheduler isn't called inside the transaction — we run it after commit.
            val archivedTask =
                currentTask.withoutRecurrence().copy(
                    isCompleted = true,
                    completedAt = now,
                    nextInstanceId = null,
                )
            taskRepository.updateTask(archivedTask)

            // 2. Mark only incomplete subtasks completed so rollback can keep prior completions.
            taskRepository.completeIncompleteSubtasks(task.id, now)

            var nextInstance =
                currentTask.nextInstanceId?.let { nextInstanceId ->
                    val existingNextInstance = taskRepository.getTaskById(nextInstanceId)
                    val reusableNextInstance =
                        existingNextInstance?.takeIf {
                            !it.isCompleted && it.parentTaskId == null && it.periodicity != null
                        }
                    if (reusableNextInstance != null) {
                        taskRepository.updateTaskNextInstanceId(currentTask.id, reusableNextInstance.id)
                        reusableNextInstance
                    } else {
                        taskRepository.updateTaskNextInstanceId(currentTask.id, null)
                        null
                    }
                }

            if (nextInstance == null) {
                // `task` carries the occurrence being completed, which callers may have restored to
                // the one that fired. No date means this was the last occurrence of the series.
                val nextReminderDate =
                    TaskReminderDateUtil.nextOccurrenceWithinEnd(
                        currentTask.copy(reminderDate = task.reminderDate),
                        now,
                    )
                if (nextReminderDate != null) {
                    val nextTaskInstance = currentTask.asNextOccurrence(nextReminderDate)
                    val newParentId = taskRepository.insertTask(nextTaskInstance)
                    if (newParentId > 0) {
                        taskRepository.updateTaskNextInstanceId(currentTask.id, newParentId)
                        cloneSubtasksUnder(newParentId, subtasksBefore)
                        // The next occurrence is the same piece of work, so it keeps the same files.
                        taskAttachmentRepository.copyAttachments(currentTask.id, newParentId)
                        nextInstance = nextTaskInstance.copy(id = newParentId)
                    }
                }
            }

            return PeriodicCommit(
                archivedTask = archivedTask.copy(nextInstanceId = nextInstance?.id),
                nextInstance = nextInstance,
            )
        }

        private suspend fun cloneSubtasksUnder(
            newParentId: Long,
            subtasksBefore: List<Task>,
        ) {
            if (subtasksBefore.isEmpty()) return
            val maxSortOrder = taskRepository.getMaxSubtaskSortOrder(newParentId)
            val clones =
                subtasksBefore.mapIndexed { index, subtask ->
                    subtask.copy(
                        id = 0,
                        isCompleted = false,
                        completedAt = null,
                        parentTaskId = newParentId,
                        reminderDate = null,
                        periodicity = null,
                        sortOrder = maxSortOrder + index + 1,
                        nextInstanceId = null,
                    )
                }
            val cloneIds = taskRepository.insertTasks(clones)
            // Each step of the next occurrence keeps the files of the step it was cloned from.
            subtasksBefore.zip(cloneIds).forEach { (original, cloneId) ->
                if (cloneId > 0) taskAttachmentRepository.copyAttachments(original.id, cloneId)
            }
        }

        private fun scheduleOrCancel(task: Task): ScheduleResult {
            if (task.reminderDate == null) {
                taskReminderScheduler.cancel(task)
                return ScheduleResult.Skipped
            }
            taskReminderScheduler.dismissNotification(task.id)
            val r = taskReminderScheduler.schedule(task)
            if (r is ScheduleResult.Skipped) taskReminderScheduler.cancel(task)
            return r
        }

        private data class DetachCommit(
            val restoredTask: Task,
            val restoredSubtasks: List<Task>,
        )

        // ── Branch A-detach: uncheck an archived occurrence without changing the next one ──
        private suspend fun detachPeriodicCompletion(archivedTask: Task): Result {
            val archivedSubtasks = taskRepository.getSubtasksSync(archivedTask.id)

            val commit =
                taskRepository.runInTransaction {
                    performDetachTxn(archivedTask, archivedSubtasks)
                }

            scheduleRestoredSubtasks(commit.restoredSubtasks)
            val scheduleResult = scheduleOrCancel(commit.restoredTask)
            return Result.ParentToggled(
                isCompleted = false,
                updateResult = UpdateTaskUseCase.Result.Success(scheduleResult),
            )
        }

        private suspend fun performDetachTxn(
            archivedTask: Task,
            archivedSubtasks: List<Task>,
        ): DetachCommit {
            // Keep the next occurrence independent. The archived occurrence retains its original
            // reminder date, but recurrence must remain stripped so only the next occurrence owns
            // future scheduling.
            val restoredTask =
                archivedTask.withoutRecurrence().copy(
                    isCompleted = false,
                    completedAt = null,
                    nextInstanceId = null,
                )
            taskRepository.updateTask(restoredTask)

            val restoredSubtasks = restoreAutoCompletedSubtasks(archivedTask.completedAt, archivedSubtasks)

            return DetachCommit(restoredTask, restoredSubtasks)
        }

        private suspend fun restoreAutoCompletedSubtasks(
            archivedCompletedAt: LocalDateTime?,
            archivedSubtasks: List<Task>,
        ): List<Task> {
            if (archivedCompletedAt == null) return emptyList()
            // Auto-completed subtasks are stamped with the exact parent completion timestamp.
            val toRestore =
                archivedSubtasks.filter { sub ->
                    sub.isCompleted && sub.completedAt == archivedCompletedAt
                }
            return toRestore.map { sub ->
                val restored = sub.copy(isCompleted = false, completedAt = null)
                taskRepository.updateTask(restored)
                restored
            }
        }

        private fun scheduleRestoredSubtasks(restoredSubtasks: List<Task>) {
            restoredSubtasks.forEach { sub ->
                if (sub.reminderDate != null) {
                    taskReminderScheduler.schedule(sub)
                }
            }
        }

        // ── Branch B: parent without periodicity ────────────────────────────────────────
        private suspend fun toggleNonPeriodicParent(
            task: Task,
            now: LocalDateTime,
        ): Result {
            val isNowCompleted = !task.isCompleted
            val updatedTask =
                task.copy(
                    isCompleted = isNowCompleted,
                    completedAt = if (isNowCompleted) now else null,
                    // Re-completing an archived task without rolling back makes the existing
                    // nextInstanceId link stale (the spawn is no longer "the latest action").
                    // Clear it so a future uncheck doesn't try to roll back stale state.
                    nextInstanceId = if (isNowCompleted) null else task.nextInstanceId,
                )
            val updateResult = updateTaskUseCase(updatedTask)

            val subtasks = taskRepository.getSubtasksSync(task.id)
            taskRepository.updateSubtasksCompletion(
                task.id,
                isNowCompleted,
                if (isNowCompleted) now else null,
            )
            subtasks.forEach { subtask ->
                if (isNowCompleted) {
                    if (subtask.reminderDate != null) taskReminderScheduler.cancel(subtask)
                } else {
                    if (subtask.reminderDate != null) taskReminderScheduler.schedule(subtask)
                }
            }

            return Result.ParentToggled(isNowCompleted, updateResult)
        }

        // ── Branch C: subtask ───────────────────────────────────────────────────────────
        private suspend fun toggleSubtask(
            task: Task,
            now: LocalDateTime,
        ): Result {
            val isNowCompleted = !task.isCompleted
            val updatedTask =
                task.copy(
                    isCompleted = isNowCompleted,
                    completedAt = if (isNowCompleted) now else null,
                )
            val updateResult = updateTaskUseCase(updatedTask)
            return Result.SubtaskToggled(updateResult)
        }
    }
