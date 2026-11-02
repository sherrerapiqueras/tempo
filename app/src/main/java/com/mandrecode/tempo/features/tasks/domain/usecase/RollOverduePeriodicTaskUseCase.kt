package com.mandrecode.tempo.features.tasks.domain.usecase

import com.mandrecode.tempo.core.domain.model.ScheduleResult
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
class RollOverduePeriodicTaskUseCase
    @Inject
    constructor(
        private val taskRepository: TaskRepository,
        private val taskReminderScheduler: TaskReminderScheduler,
        private val taskAttachmentRepository: TaskAttachmentRepository,
    ) {
        sealed class Result {
            data object NotApplicable : Result()

            data object MissingOriginal : Result()

            data object FailedToCreate : Result()

            /** The overdue task was the series' last occurrence, and is now a one-off task. */
            data object SeriesEnded : Result()

            data class CreatedNextInstance(
                val overdueTask: Task,
                val nextInstance: Task,
                val scheduleResult: ScheduleResult,
            ) : Result()

            data class ReusedNextInstance(
                val overdueTask: Task,
                val nextInstance: Task,
                val scheduleResult: ScheduleResult,
            ) : Result()
        }

        private data class RolloverCommit(
            val kind: Kind,
            val overdueTask: Task,
            val nextInstance: Task,
        ) {
            enum class Kind {
                CREATED,
                REUSED,
            }
        }

        private sealed class TransactionResult {
            data object NotApplicable : TransactionResult()

            data object MissingOriginal : TransactionResult()

            data object FailedToCreate : TransactionResult()

            data object SeriesEnded : TransactionResult()

            data class Committed(
                val commit: RolloverCommit,
            ) : TransactionResult()
        }

        suspend operator fun invoke(
            task: Task,
            now: LocalDateTime = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()),
        ): Result {
            val transactionResult =
                if (task.canRollOver(now)) {
                    taskRepository.runInTransaction {
                        rollOverInTransaction(task.id, now)
                    }
                } else {
                    null
                }

            return when (transactionResult) {
                null -> Result.NotApplicable
                TransactionResult.FailedToCreate -> Result.FailedToCreate
                TransactionResult.MissingOriginal -> Result.MissingOriginal
                TransactionResult.NotApplicable -> Result.NotApplicable
                // Touches no alarm: the reminder has passed, so none is pending, and cancelling
                // would also dismiss the notification this occurrence may have just posted.
                TransactionResult.SeriesEnded -> Result.SeriesEnded
                is TransactionResult.Committed -> transactionResult.commit.toResult()
            }
        }

        private suspend fun rollOverInTransaction(
            taskId: Long,
            now: LocalDateTime,
        ): TransactionResult {
            val currentTask = taskRepository.getTaskById(taskId)
            return when {
                currentTask == null -> TransactionResult.MissingOriginal
                !currentTask.canRollOver(now) -> TransactionResult.NotApplicable
                else -> {
                    val transactionResult =
                        findReusableCommit(currentTask)?.let { TransactionResult.Committed(it) }
                            ?: createNextInstanceOrEndSeries(currentTask, now)

                    if (transactionResult is TransactionResult.Committed) {
                        val rolledOverOriginal = transactionResult.commit.overdueTask.withoutRecurrence()
                        taskRepository.updateTask(rolledOverOriginal)
                        TransactionResult.Committed(
                            transactionResult.commit.copy(overdueTask = rolledOverOriginal),
                        )
                    } else {
                        transactionResult
                    }
                }
            }
        }

        private suspend fun findReusableCommit(currentTask: Task): RolloverCommit? {
            val nextInstanceId = currentTask.nextInstanceId ?: return null
            val nextInstance = taskRepository.getTaskById(nextInstanceId)
            return if (nextInstance == null || !nextInstance.isReusableNextInstance()) {
                taskRepository.updateTaskNextInstanceId(currentTask.id, null)
                null
            } else {
                RolloverCommit(
                    kind = RolloverCommit.Kind.REUSED,
                    overdueTask = currentTask,
                    nextInstance = nextInstance,
                )
            }
        }

        private suspend fun createNextInstanceOrEndSeries(
            currentTask: Task,
            now: LocalDateTime,
        ): TransactionResult {
            val nextReminderDate = TaskReminderDateUtil.nextOccurrenceWithinEnd(currentTask, now)
            return if (nextReminderDate == null) {
                endSeries(currentTask)
            } else {
                createNextInstance(currentTask, nextReminderDate)
            }
        }

        private suspend fun createNextInstance(
            currentTask: Task,
            nextReminderDate: LocalDateTime,
        ): TransactionResult {
            val subtasksBefore = taskRepository.getSubtasksSync(currentTask.id)
            val nextTaskInstance = currentTask.asNextOccurrence(nextReminderDate)
            val newParentId = taskRepository.insertTask(nextTaskInstance)
            if (newParentId <= 0) return TransactionResult.FailedToCreate

            val nextInstance = nextTaskInstance.copy(id = newParentId)

            taskRepository.updateTaskNextInstanceId(currentTask.id, newParentId)
            cloneSubtasksUnder(newParentId, subtasksBefore)
            // The next occurrence is the same piece of work, so it keeps the same files.
            taskAttachmentRepository.copyAttachments(currentTask.id, newParentId)

            return TransactionResult.Committed(
                RolloverCommit(
                    kind = RolloverCommit.Kind.CREATED,
                    overdueTask = currentTask.copy(nextInstanceId = newParentId),
                    nextInstance = nextInstance,
                ),
            )
        }

        private suspend fun endSeries(currentTask: Task): TransactionResult {
            taskRepository.updateTask(currentTask.withoutRecurrence().copy(nextInstanceId = null))
            return TransactionResult.SeriesEnded
        }

        private fun RolloverCommit.toResult(): Result {
            val scheduleResult = scheduleOrCancel(nextInstance)
            return when (kind) {
                RolloverCommit.Kind.CREATED ->
                    Result.CreatedNextInstance(
                        overdueTask = overdueTask,
                        nextInstance = nextInstance,
                        scheduleResult = scheduleResult,
                    )
                RolloverCommit.Kind.REUSED ->
                    Result.ReusedNextInstance(
                        overdueTask = overdueTask,
                        nextInstance = nextInstance,
                        scheduleResult = scheduleResult,
                    )
            }
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

        private fun Task.canRollOver(now: LocalDateTime): Boolean =
            !isCompleted &&
                parentTaskId == null &&
                periodicity != null &&
                reminderDate != null &&
                reminderDate < now

        private fun Task.isReusableNextInstance(): Boolean =
            !isCompleted &&
                parentTaskId == null &&
                periodicity != null

        private fun scheduleOrCancel(task: Task): ScheduleResult {
            if (task.reminderDate == null) {
                taskReminderScheduler.cancel(task)
                return ScheduleResult.Skipped
            }
            taskReminderScheduler.dismissNotification(task.id)
            val result = taskReminderScheduler.schedule(task)
            if (result is ScheduleResult.Skipped) taskReminderScheduler.cancel(task)
            return result
        }
    }
