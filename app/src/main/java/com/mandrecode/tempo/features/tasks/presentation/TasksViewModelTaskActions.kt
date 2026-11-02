package com.mandrecode.tempo.features.tasks.presentation

import androidx.lifecycle.viewModelScope
import com.mandrecode.tempo.R
import com.mandrecode.tempo.core.domain.model.DayOfWeek
import com.mandrecode.tempo.core.domain.model.MonthDayOption
import com.mandrecode.tempo.core.domain.model.Periodicity
import com.mandrecode.tempo.core.domain.model.Priority
import com.mandrecode.tempo.core.domain.model.ScheduleResult
import com.mandrecode.tempo.core.domain.util.TitleDescriptionValidationResult
import com.mandrecode.tempo.core.domain.util.ValidationUtils
import com.mandrecode.tempo.core.ui.util.toUserFacingMessage
import com.mandrecode.tempo.features.tasks.domain.model.Task
import com.mandrecode.tempo.features.tasks.domain.model.TaskAttachment
import com.mandrecode.tempo.features.tasks.domain.usecase.CreateTaskUseCase
import com.mandrecode.tempo.features.tasks.domain.usecase.ToggleTaskCompletionUseCase
import com.mandrecode.tempo.features.tasks.domain.usecase.UpdateTaskUseCase
import com.mandrecode.tempo.features.tasks.presentation.TasksContract.TaskFormState
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toPersistentSet
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime

/** @return `true` if [result] is an error and the corresponding form error field was set. */
private fun TasksViewModel.applyTaskFormValidationError(result: TitleDescriptionValidationResult): Boolean {
    when (result) {
        TitleDescriptionValidationResult.TitleEmpty ->
            mutableUiState.update { it.copy(taskForm = it.taskForm.copy(titleError = R.string.task_title_required)) }

        TitleDescriptionValidationResult.TitleTooLong ->
            mutableUiState.update { it.copy(taskForm = it.taskForm.copy(titleError = R.string.error_task_title_too_long)) }

        TitleDescriptionValidationResult.DescriptionTooLong ->
            mutableUiState.update {
                it.copy(taskForm = it.taskForm.copy(descriptionError = R.string.error_task_description_too_long))
            }

        TitleDescriptionValidationResult.Valid -> Unit
    }
    return result != TitleDescriptionValidationResult.Valid
}

internal fun TasksViewModel.createOrUpdateTask(
    title: String,
    description: String,
    categoryId: Long,
    parentTaskId: Long? = null,
    autoSave: Boolean = false,
) {
    val validationResult = ValidationUtils.validateTitleAndDescription(title, description)
    if (applyTaskFormValidationError(validationResult)) return

    val taskToEdit = mutableUiState.value.taskForm.editingTask
    val sanitized = sanitizePeriodicityFields()

    if (taskToEdit != null) {
        val shouldUnlink =
            taskToEdit.parentTaskId != null && categoryId != taskToEdit.categoryId
        val editFields = taskToEdit.editFieldsFrom(sanitized)
        val updatedTask =
            taskToEdit.copy(
                title = title,
                description = description,
                categoryId = categoryId,
                priority = editFields.priority,
                reminderDate = editFields.reminderDate,
                periodicity = editFields.periodicity,
                periodicityInterval = editFields.periodicityInterval,
                repeatDays = editFields.repeatDays,
                monthDayOption = editFields.monthDayOption,
                periodicityEndDate = editFields.periodicityEndDate,
                parentTaskId = if (shouldUnlink) null else taskToEdit.parentTaskId,
            )
        val withSection =
            updatedTask.copy(sectionId = resolvedFormSectionId(categoryId, updatedTask.parentTaskId))
        updateTask(withSection, comesFromDialog = !autoSave)
    } else {
        val newTask =
            Task(
                title = title,
                description = description,
                categoryId = categoryId,
                priority = sanitized.priority,
                reminderDate = sanitized.reminderDate,
                periodicity = sanitized.periodicity,
                periodicityInterval = sanitized.periodicityInterval,
                repeatDays = sanitized.repeatDays,
                monthDayOption = sanitized.monthDayOption,
                periodicityEndDate = sanitized.periodicityEndDate,
                parentTaskId = parentTaskId,
                sectionId = resolvedFormSectionId(categoryId, parentTaskId),
            )
        addTask(newTask, pendingAttachments = mutableUiState.value.taskForm.attachments)

        if (parentTaskId != null) {
            expandTask(parentTaskId)
        }
    }

    if (!autoSave) {
        hideTaskDialog()
    }
}

private data class TaskEditFields(
    val priority: Priority?,
    val reminderDate: LocalDateTime?,
    val periodicity: Periodicity?,
    val periodicityInterval: Int,
    val repeatDays: Set<DayOfWeek>?,
    val monthDayOption: MonthDayOption?,
    val periodicityEndDate: LocalDate?,
)

private fun Task.editFieldsFrom(form: TaskFormState): TaskEditFields =
    if (isCompleted) {
        TaskEditFields(
            priority = priority,
            reminderDate = reminderDate,
            periodicity = periodicity,
            periodicityInterval = periodicityInterval,
            repeatDays = repeatDays,
            monthDayOption = monthDayOption,
            periodicityEndDate = periodicityEndDate,
        )
    } else {
        TaskEditFields(
            priority = form.priority,
            reminderDate = form.reminderDate,
            periodicity = form.periodicity,
            periodicityInterval = form.periodicityInterval,
            repeatDays = form.repeatDays,
            monthDayOption = form.monthDayOption,
            periodicityEndDate = form.periodicityEndDate,
        )
    }

internal fun TasksViewModel.sanitizePeriodicityFields(): TaskFormState {
    val form = mutableUiState.value.taskForm
    val periodicity = form.periodicity
    val interval =
        when {
            periodicity == null -> 1
            periodicity == Periodicity.HOURLY ->
                form.periodicityInterval.coerceIn(1, Periodicity.MAX_HOURLY_INTERVAL)
            else -> form.periodicityInterval.coerceAtLeast(1)
        }
    return form.copy(
        periodicityInterval = interval,
        repeatDays = if (periodicity == Periodicity.WEEKLY) form.repeatDays else null,
        monthDayOption = if (periodicity == Periodicity.MONTHLY) form.monthDayOption else null,
        periodicityEndDate = if (periodicity != null) form.periodicityEndDate else null,
    )
}

internal fun TasksViewModel.addTask(
    task: Task,
    pendingAttachments: List<TaskAttachment> = emptyList(),
) {
    viewModelScope.launch {
        try {
            when (val result = createTaskUseCase(task)) {
                is CreateTaskUseCase.Result.ValidationError -> {
                    val messageRes =
                        when (result.type) {
                            CreateTaskUseCase.ValidationErrorType.TITLE_EMPTY ->
                                R.string.task_title_required
                            CreateTaskUseCase.ValidationErrorType.TITLE_TOO_LONG ->
                                R.string.error_task_title_too_long
                            CreateTaskUseCase.ValidationErrorType.DESCRIPTION_TOO_LONG ->
                                R.string.error_task_description_too_long
                        }
                    showSnackbar(messageRes)
                }

                is CreateTaskUseCase.Result.Success -> {
                    // The editor has closed by now, so a failure is a snackbar, not a line in it.
                    if (!attachPendingAttachments(pendingAttachments, result.taskId)) {
                        showSnackbar(R.string.msg_attachment_failed)
                    }
                    val messageResId =
                        when {
                            result.scheduleResult is ScheduleResult.PermissionError -> {
                                requestReminderPermission()
                                R.string.msg_permission_needed
                            }
                            result.scheduleResult is ScheduleResult.Failure ->
                                R.string.msg_task_added_failed_scheduling
                            result.reminderAdvanced ->
                                R.string.msg_task_added_reminder_advanced
                            result.pastReminderWontFire ->
                                R.string.msg_task_added_past_reminder
                            else -> R.string.msg_task_added_success
                        }

                    mutableUiState.update {
                        it.copy(taskForm = TaskFormState())
                    }
                    showSnackbar(messageResId)
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            showSnackbar(
                R.string.msg_task_add_failed,
                listOf(e.toUserFacingMessage()),
            )
        }
    }
}

internal fun TasksViewModel.updateTask(
    task: Task,
    comesFromDialog: Boolean = false,
) {
    viewModelScope.launch {
        try {
            when (val result = updateTaskUseCase(task)) {
                is UpdateTaskUseCase.Result.ValidationError -> {
                    val messageRes =
                        when (result.type) {
                            CreateTaskUseCase.ValidationErrorType.TITLE_EMPTY ->
                                R.string.task_title_required
                            CreateTaskUseCase.ValidationErrorType.TITLE_TOO_LONG ->
                                R.string.error_task_title_too_long
                            CreateTaskUseCase.ValidationErrorType.DESCRIPTION_TOO_LONG ->
                                R.string.error_task_description_too_long
                        }
                    showSnackbar(messageRes)
                }

                is UpdateTaskUseCase.Result.Success -> {
                    val message =
                        when {
                            result.scheduleResult is ScheduleResult.PermissionError -> {
                                requestReminderPermission()
                                R.string.msg_permission_needed
                            }
                            result.scheduleResult is ScheduleResult.Failure ->
                                R.string.msg_task_updated_failed_scheduling
                            result.reminderAdvanced ->
                                R.string.msg_task_updated_reminder_advanced
                            result.pastReminderWontFire ->
                                R.string.msg_task_updated_past_reminder
                            comesFromDialog -> R.string.msg_task_updated_success
                            else -> null
                        }

                    mutableUiState.update { currentState ->
                        if (comesFromDialog) {
                            currentState.copy(taskForm = TaskFormState())
                        } else {
                            currentState
                        }
                    }

                    if (message != null) {
                        showSnackbar(message)
                    }
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            showSnackbar(R.string.msg_task_update_failed, listOf(e.toUserFacingMessage()))
        }
    }
}

internal fun TasksViewModel.requestDeleteTask(task: Task) {
    viewModelScope.launch {
        val subtasksCount = taskRepository.getSubtasksSync(task.id).size
        mutableUiState.update {
            it.copy(
                showDeleteTaskConfirmationDialog = true,
                taskToDelete = task,
                taskToDeleteSubtasksCount = subtasksCount,
            )
        }
    }
}

internal fun TasksViewModel.cancelDeleteTask() {
    mutableUiState.update {
        it.copy(
            showDeleteTaskConfirmationDialog = false,
            taskToDelete = null,
            taskToDeleteSubtasksCount = 0,
        )
    }
}

internal fun TasksViewModel.confirmDeleteTask(task: Task) {
    deleteTask(task)
    mutableUiState.update {
        it.copy(
            showDeleteTaskConfirmationDialog = false,
            taskToDelete = null,
            taskToDeleteSubtasksCount = 0,
            taskForm = TaskFormState(),
        )
    }
}

internal fun TasksViewModel.deleteTask(task: Task) =
    viewModelScope.launch {
        try {
            val snapshot = deleteTaskUseCase(task)
            val token = storePendingDeletion(PendingTaskDeletion.Tasks(snapshot))
            showSnackbar(
                messageResId = R.string.task_deleted,
                actionResId = R.string.undo,
                deletionToken = token,
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            showSnackbar(R.string.error_deleting_task)
        }
    }

internal fun TasksViewModel.toggleTaskCompletion(taskToToggle: Task) {
    viewModelScope.launch {
        if (!taskToToggle.isCompleted) {
            mutableUiState.update { it.copy(showCompletedTasks = true) }
        }

        val pending = pendingOriginalReminderDate
        val (effectiveTask, usesPendingReminderDate) =
            resolveEffectiveTaskForToggle(
                taskToToggle = taskToToggle,
                pendingOriginal = pending,
            )
        try {
            val result = toggleTaskCompletionUseCase(effectiveTask)
            handleToggleResult(taskToToggle.id, result)
            if (usesPendingReminderDate && pending == pendingOriginalReminderDate) {
                pendingOriginalReminderDate = null
            }
            refreshEditingTaskIfNeeded(taskToToggle.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            showSnackbar(R.string.msg_task_update_failed, listOf(e.toUserFacingMessage()))
        }
    }
}

private fun resolveEffectiveTaskForToggle(
    taskToToggle: Task,
    pendingOriginal: Pair<Long, LocalDateTime>?,
): Pair<Task, Boolean> {
    val usesPendingReminderDate =
        pendingOriginal != null &&
            pendingOriginal.first == taskToToggle.id &&
            taskToToggle.periodicity != null
    val effectiveTask =
        if (usesPendingReminderDate) {
            taskToToggle.copy(reminderDate = pendingOriginal.second)
        } else {
            taskToToggle
        }
    return effectiveTask to usesPendingReminderDate
}

private suspend fun TasksViewModel.handleToggleResult(
    taskId: Long,
    result: ToggleTaskCompletionUseCase.Result,
) {
    when (result) {
        is ToggleTaskCompletionUseCase.Result.PeriodicCompleted -> {
            handleSchedulePermissionError(result.updateResult)
        }

        is ToggleTaskCompletionUseCase.Result.ParentToggled -> {
            if (result.isCompleted) {
                collapseTask(taskId)
            } else {
                expandTask(taskId)
            }
            handleSchedulePermissionError(result.updateResult)
        }

        is ToggleTaskCompletionUseCase.Result.SubtaskToggled -> {
            handleSchedulePermissionError(result.updateResult)
        }
    }
}

internal suspend fun TasksViewModel.refreshEditingTaskIfNeeded(toggledTaskId: Long) {
    val editingId =
        mutableUiState.value.taskForm.editingTask
            ?.id
    if (editingId != toggledTaskId) return
    val refreshed =
        try {
            taskRepository.getTaskById(toggledTaskId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
    if (refreshed != null) {
        mutableUiState.update {
            it.copy(
                taskForm =
                    it.taskForm.copy(
                        editingTask = refreshed,
                    ),
            )
        }
    }
}

internal suspend fun TasksViewModel.handleSchedulePermissionError(updateResult: UpdateTaskUseCase.Result) {
    if (updateResult is UpdateTaskUseCase.Result.Success &&
        updateResult.scheduleResult is ScheduleResult.PermissionError
    ) {
        requestReminderPermission()
        showSnackbar(R.string.msg_permission_needed)
    }
}

internal fun TasksViewModel.showTaskDialog(
    task: Task? = null,
    parentTaskId: Long? = null,
    sectionId: Long? = null,
) {
    pendingOriginalReminderDate = null
    viewModelScope.launch {
        val parentTask =
            if (parentTaskId != null) {
                try {
                    taskRepository.getTaskById(parentTaskId)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    showSnackbar(
                        R.string.msg_error_loading_parent,
                        listOf(e.toUserFacingMessage()),
                    )
                    null
                }
            } else {
                null
            }

        val editorSessionId = ++nextEditorSessionId
        mutableUiState.update {
            it.copy(
                taskForm =
                    TaskFormState(
                        isVisible = true,
                        editorSessionId = editorSessionId,
                        editingTask = task,
                        priority = task?.priority,
                        reminderDate = task?.reminderDate,
                        periodicity = task?.periodicity,
                        periodicityInterval = task?.periodicityInterval ?: 1,
                        repeatDays = task?.repeatDays?.toPersistentSet(),
                        monthDayOption = task?.monthDayOption,
                        periodicityEndDate = task?.periodicityEndDate,
                        parentTaskId = parentTaskId,
                        parentTask = parentTask,
                        sectionId = task?.sectionId ?: sectionId,
                    ),
            )
        }
    }
}

internal fun TasksViewModel.hideTaskDialog() {
    pendingOriginalReminderDate = null
    mutableUiState.update {
        it.copy(taskForm = TaskFormState())
    }
}

internal fun TasksViewModel.onSetPriority(priority: Priority) {
    mutableUiState.update { it.copy(taskForm = it.taskForm.copy(priority = priority)) }
}

internal fun TasksViewModel.onClearPriority() {
    mutableUiState.update { it.copy(taskForm = it.taskForm.copy(priority = null)) }
}

internal fun TasksViewModel.onSetPeriodicity(periodicity: Periodicity) {
    mutableUiState.update {
        val form = it.taskForm
        val isHourly = periodicity == Periodicity.HOURLY
        val wasHourly = form.periodicity == Periodicity.HOURLY
        val repeatDays =
            when {
                isHourly -> null
                periodicity == Periodicity.WEEKLY && form.repeatDays == null ->
                    form.reminderDate?.let { date ->
                        persistentSetOf(DayOfWeek.fromKotlinDayOfWeek(date.date.dayOfWeek))
                    }
                else -> form.repeatDays
            }
        val monthDayOption = if (isHourly) null else form.monthDayOption
        val interval =
            if (isHourly != wasHourly) 1 else form.periodicityInterval
        it.copy(
            taskForm =
                form.copy(
                    periodicity = periodicity,
                    periodicityInterval = interval,
                    repeatDays = repeatDays,
                    monthDayOption = monthDayOption,
                ),
        )
    }
}

internal fun TasksViewModel.onClearPeriodicity() {
    mutableUiState.update {
        it.copy(
            taskForm =
                it.taskForm.copy(
                    periodicity = null,
                    periodicityInterval = 1,
                    repeatDays = null,
                    monthDayOption = null,
                    periodicityEndDate = null,
                ),
        )
    }
}

internal fun TasksViewModel.onSetPeriodicityInterval(interval: Int) {
    mutableUiState.update {
        val form = it.taskForm
        val maxInterval =
            if (form.periodicity == Periodicity.HOURLY) Periodicity.MAX_HOURLY_INTERVAL else Int.MAX_VALUE
        val clamped = interval.coerceIn(1, maxInterval)
        it.copy(taskForm = form.copy(periodicityInterval = clamped))
    }
}

internal fun TasksViewModel.onSetRepeatDays(days: Set<DayOfWeek>?) {
    mutableUiState.update { it.copy(taskForm = it.taskForm.copy(repeatDays = days?.toPersistentSet())) }
}

internal fun TasksViewModel.onSetMonthDayOption(option: MonthDayOption?) {
    mutableUiState.update { it.copy(taskForm = it.taskForm.copy(monthDayOption = option)) }
}

internal fun TasksViewModel.onSetPeriodicityEndDate(endDate: LocalDate?) {
    mutableUiState.update { it.copy(taskForm = it.taskForm.copy(periodicityEndDate = endDate)) }
}

internal fun TasksViewModel.openTaskFromNotificationInternal(
    taskId: Long,
    originalReminderDate: LocalDateTime? = null,
) {
    viewModelScope.launch {
        val task = taskRepository.getTaskById(taskId)
        if (task != null) {
            pendingOriginalReminderDate =
                originalReminderDate?.let { taskId to it }
            val sortOption = tasksScreenPreferencesRepository.getSortOption(task.categoryId)
            val editorSessionId = ++nextEditorSessionId
            mutableUiState.update {
                it.copy(
                    selectedCategoryId = task.categoryId,
                    sortOption = sortOption,
                    taskForm =
                        TaskFormState(
                            isVisible = true,
                            editorSessionId = editorSessionId,
                            editingTask = task,
                            priority = task.priority,
                            reminderDate = task.reminderDate,
                            periodicity = task.periodicity,
                            periodicityInterval = task.periodicityInterval,
                            repeatDays = task.repeatDays?.toPersistentSet(),
                            monthDayOption = task.monthDayOption,
                            periodicityEndDate = task.periodicityEndDate,
                        ),
                )
            }
        }
    }
}

internal fun TasksViewModel.setReminder(
    year: Int,
    month: Int,
    day: Int,
    hour: Int,
    minute: Int,
) {
    val newReminderDate = LocalDateTime(year, month, day, hour, minute)
    mutableUiState.update {
        val form = it.taskForm
        it.copy(
            taskForm =
                form.copy(
                    reminderDate = newReminderDate,
                    // An end date before the first occurrence means nothing, so it doesn't stay.
                    periodicityEndDate = form.periodicityEndDate?.takeIf { end -> end >= newReminderDate.date },
                ),
        )
    }
}

internal fun TasksViewModel.clearReminder() {
    mutableUiState.update {
        it.copy(
            taskForm =
                it.taskForm.copy(
                    reminderDate = null,
                    periodicity = null,
                    periodicityInterval = 1,
                    repeatDays = null,
                    monthDayOption = null,
                    periodicityEndDate = null,
                ),
        )
    }
}

internal fun TasksViewModel.toggleTaskExpanded(taskId: Long) {
    mutableUiState.update {
        val newExpandedIds =
            if (taskId in it.expandedTaskIds) {
                it.expandedTaskIds.remove(taskId)
            } else {
                it.expandedTaskIds.add(taskId)
            }
        it.copy(expandedTaskIds = newExpandedIds)
    }
}

internal fun TasksViewModel.expandTask(taskId: Long) {
    mutableUiState.update {
        it.copy(expandedTaskIds = it.expandedTaskIds.add(taskId))
    }
}

internal fun TasksViewModel.collapseTask(taskId: Long) {
    mutableUiState.update {
        it.copy(expandedTaskIds = it.expandedTaskIds.remove(taskId))
    }
}

internal fun TasksViewModel.clearTaskErrors() {
    mutableUiState.update { state ->
        if (state.taskForm.titleError == null && state.taskForm.descriptionError == null) {
            state
        } else {
            state.copy(
                taskForm = state.taskForm.copy(titleError = null, descriptionError = null),
            )
        }
    }
}

internal fun TasksViewModel.reorderTasks(
    fromIndex: Int,
    toIndex: Int,
    tasks: List<Task>,
) {
    viewModelScope.launch {
        try {
            reorderTasksUseCase(fromIndex, toIndex, tasks)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            showSnackbar(
                R.string.msg_task_reorder_failed,
                listOf(e.toUserFacingMessage()),
            )
        }
    }
}
