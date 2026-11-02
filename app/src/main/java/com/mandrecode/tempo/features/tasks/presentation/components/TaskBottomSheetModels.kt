package com.mandrecode.tempo.features.tasks.presentation.components

import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.text.input.TextFieldValue
import com.mandrecode.tempo.core.domain.model.DayOfWeek
import com.mandrecode.tempo.core.domain.model.MonthDayOption
import com.mandrecode.tempo.core.domain.model.Periodicity
import com.mandrecode.tempo.core.domain.model.Priority
import com.mandrecode.tempo.features.tasks.domain.model.Category
import com.mandrecode.tempo.features.tasks.domain.model.Task
import com.mandrecode.tempo.features.tasks.domain.model.TaskAttachment
import com.mandrecode.tempo.features.tasks.domain.model.TaskSection
import com.mandrecode.tempo.features.tasks.presentation.TasksContract
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime

internal const val TASK_BOTTOM_SHEET_TITLE_FIELD_TEST_TAG = "task_bottom_sheet_title_field"
internal const val TASK_BOTTOM_SHEET_DESCRIPTION_FIELD_TEST_TAG = "task_bottom_sheet_description_field"
internal const val TASK_PERIODICITY_END_DATE_ROW_TEST_TAG = "task_periodicity_end_date_row"

/**
 * Subset of [Task] fields the sheet treats as form input. Captured once when
 * the sheet opens so subsequent live refreshes of [Task] (e.g. periodic
 * completion archiving the original and stripping recurrence on the archived
 * copy) do not invalidate the user's in-progress edits or trigger a spurious
 * "Discard changes?" dialog. Mirrors the [HabitFormSnapshot] pattern in
 * `HabitBottomSheet`.
 */
internal data class TaskFormSnapshot(
    val title: String,
    val description: String,
    val categoryId: Long,
    val priority: Priority?,
    val reminderDate: LocalDateTime?,
    val periodicity: Periodicity?,
    val periodicityInterval: Int,
    val repeatDays: Set<DayOfWeek>?,
    val monthDayOption: MonthDayOption?,
    val periodicityEndDate: LocalDate?,
    val sectionId: Long? = null,
)

internal data class TaskBottomSheetBodyState(
    val categories: List<Category>,
    val formState: TasksContract.TaskFormState,
    val task: Task?,
    val taskTitle: String,
    val selectedCategoryId: Long,
    val formattedReminder: String?,
    val isTitleError: Boolean,
    val autoSaveEnabled: Boolean,
    val isEditingTask: Boolean,
    val isPriorityReadOnly: Boolean,
    /** The selected category's sections (#61); the Section row is hidden when there are none. */
    val sections: List<TaskSection> = emptyList(),
)

internal data class TaskBottomSheetBodyActions(
    val onTaskTitleChanged: (String) -> Unit,
    val onTaskDescriptionChanged: (TextFieldValue) -> Unit,
    val onSelectCategory: (Long) -> Unit,
    val onSetPriority: (Priority) -> Unit,
    val onClearPriority: () -> Unit,
    val onSetReminderClicked: () -> Unit,
    val onClearReminder: () -> Unit,
    val onSetPeriodicity: (Periodicity) -> Unit,
    val onClearPeriodicity: () -> Unit,
    val onSetPeriodicityInterval: (Int) -> Unit,
    val onSetRepeatDays: (Set<DayOfWeek>?) -> Unit,
    val onSetMonthDayOption: (MonthDayOption?) -> Unit,
    val onRequestDismiss: () -> Unit,
    val onDelete: (() -> Unit)?,
    val onToggleCompletion: ((Task) -> Unit)?,
    val onConfirmClick: () -> Unit,
    val attachmentActions: TaskAttachmentActions? = null,
    val onSelectSection: (Long?) -> Unit = {},
    val onSetPeriodicityEndDate: (LocalDate?) -> Unit = {},
)

/** What the attachments section can ask for; the section is only shown when the host provides it. */
data class TaskAttachmentActions(
    val onAddAttachments: (List<String>) -> Unit,
    val onRemoveAttachment: (TaskAttachment) -> Unit,
    val onAttachmentOpenFail: () -> Unit,
)

internal data class TaskBottomSheetFocusConfig(
    val titleFocusRequester: FocusRequester,
    val descriptionFocusRequester: FocusRequester,
    val focusState: TaskEditorFocusState,
    val clearEditorFocus: () -> Unit,
    val interceptsLegacyImeBack: Boolean = TASK_EDITOR_INTERCEPTS_LEGACY_IME_BACK,
)
