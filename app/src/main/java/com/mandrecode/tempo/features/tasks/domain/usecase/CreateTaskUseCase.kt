package com.mandrecode.tempo.features.tasks.domain.usecase

import com.mandrecode.tempo.core.domain.model.ScheduleResult
import com.mandrecode.tempo.core.domain.util.TitleDescriptionValidationResult
import com.mandrecode.tempo.core.domain.util.ValidationUtils
import com.mandrecode.tempo.features.tasks.domain.model.Task
import com.mandrecode.tempo.features.tasks.domain.repository.TaskRepository
import com.mandrecode.tempo.features.tasks.domain.scheduler.TaskReminderScheduler
import com.mandrecode.tempo.features.tasks.domain.util.TaskReminderDateUtil
import jakarta.inject.Inject

class CreateTaskUseCase
    @Inject
    constructor(
        private val taskRepository: TaskRepository,
        private val taskReminderScheduler: TaskReminderScheduler,
    ) {
        sealed class Result {
            data class Success(
                val taskId: Long,
                val scheduleResult: ScheduleResult,
                val reminderAdvanced: Boolean = false,
                val pastReminderWontFire: Boolean = false,
            ) : Result()

            data class ValidationError(
                val type: ValidationErrorType,
            ) : Result()
        }

        enum class ValidationErrorType { TITLE_EMPTY, TITLE_TOO_LONG, DESCRIPTION_TOO_LONG }

        suspend operator fun invoke(task: Task): Result {
            val trimmedTask = task.copy(title = task.title.trim(), description = task.description.trim())
            when (ValidationUtils.validateTitleAndDescription(trimmedTask.title, trimmedTask.description)) {
                TitleDescriptionValidationResult.TitleEmpty ->
                    return Result.ValidationError(ValidationErrorType.TITLE_EMPTY)
                TitleDescriptionValidationResult.TitleTooLong ->
                    return Result.ValidationError(ValidationErrorType.TITLE_TOO_LONG)
                TitleDescriptionValidationResult.DescriptionTooLong ->
                    return Result.ValidationError(ValidationErrorType.DESCRIPTION_TOO_LONG)
                TitleDescriptionValidationResult.Valid -> {}
            }

            val adjustedTask = TaskReminderDateUtil.advanceReminderIfNeeded(trimmedTask)
            val reminderAdvanced = adjustedTask.reminderDate != trimmedTask.reminderDate
            val pastReminderWontFire = TaskReminderDateUtil.isPastReminderThatWontFire(trimmedTask)
            val parentId = adjustedTask.parentTaskId?.let { topLevelAncestorOf(it) }
            val maxSortOrder =
                if (parentId != null) {
                    taskRepository.getMaxSubtaskSortOrder(parentId)
                } else {
                    taskRepository.getMaxSortOrder(adjustedTask.categoryId)
                }
            val taskWithSortOrder =
                adjustedTask.copy(parentTaskId = parentId, sortOrder = maxSortOrder + 1)
            val newTaskId = taskRepository.insertTask(taskWithSortOrder)
            val taskWithId = taskWithSortOrder.copy(id = newTaskId)

            val scheduleResult =
                if (taskWithId.reminderDate != null) {
                    taskReminderScheduler.schedule(taskWithId)
                } else {
                    ScheduleResult.Skipped
                }
            return Result.Success(newTaskId, scheduleResult, reminderAdvanced, pastReminderWontFire)
        }

        /**
         * The task a new subtask should actually hang off: [parentId] itself, or its top-level
         * ancestor when [parentId] is a subtask.
         *
         * Tasks nests exactly one level — only top-level tasks become cards, and a subtask row
         * draws no children of its own — so a task created beneath a subtask would exist with
         * nowhere in Tasks to show it. Nothing in the app asks for one, and this is what keeps that
         * true no matter which screen grows an "add subtask" affordance next: the new task becomes
         * a sibling of the subtask rather than a child of it.
         */
        private suspend fun topLevelAncestorOf(parentId: Long): Long {
            val seen = mutableSetOf<Long>()
            var candidate = parentId
            while (seen.add(candidate)) {
                // A parent that is not there is left as asked — the insert answers for that, the
                // same way it did before this walk existed.
                candidate = taskRepository.getTaskById(candidate)?.parentTaskId ?: return candidate
            }
            // Only a corrupt snapshot links parents in a loop, and no task in one is a real root,
            // so there is nothing better to offer than what was asked for.
            return parentId
        }
    }
