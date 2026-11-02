package com.mandrecode.tempo.features.tasks.presentation.components

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.mandrecode.tempo.core.domain.model.DayOfWeek
import com.mandrecode.tempo.core.domain.model.MonthDayOption
import com.mandrecode.tempo.core.domain.model.Periodicity
import com.mandrecode.tempo.core.domain.model.Priority
import com.mandrecode.tempo.core.ui.adaptive.SheetPlacement
import com.mandrecode.tempo.features.tasks.domain.model.Category
import com.mandrecode.tempo.features.tasks.domain.model.Task
import com.mandrecode.tempo.features.tasks.domain.model.TaskSection
import com.mandrecode.tempo.features.tasks.presentation.TasksContract
import kotlinx.datetime.LocalDate
import kotlinx.datetime.format.FormatStringsInDatetimeFormats

@OptIn(ExperimentalMaterial3Api::class, FormatStringsInDatetimeFormats::class)
@Composable
fun TaskBottomSheet(
    categories: List<Category>,
    selectedCategoryIdFromFilter: Long?,
    formState: TasksContract.TaskFormState,
    onSetPriority: (Priority) -> Unit,
    onClearPriority: () -> Unit,
    onSetReminder: (Int, Int, Int, Int, Int) -> Unit,
    onClearReminder: () -> Unit,
    onSetPeriodicity: (Periodicity) -> Unit,
    onClearPeriodicity: () -> Unit,
    onSetPeriodicityInterval: (Int) -> Unit,
    onSetRepeatDays: (Set<DayOfWeek>?) -> Unit,
    onSetMonthDayOption: (MonthDayOption?) -> Unit,
    onDismiss: () -> Unit,
    onClearErrors: () -> Unit,
    onConfirm: (title: String, description: String, categoryId: Long) -> Unit,
    modifier: Modifier = Modifier,
    onAutoSave: ((title: String, description: String, categoryId: Long) -> Unit)? = null,
    task: Task? = null,
    onDelete: (() -> Unit)? = null,
    onToggleCompletion: ((Task) -> Unit)? = null,
    placement: SheetPlacement? = null,
    dismissRequestKey: Int = 0,
    attachmentActions: TaskAttachmentActions? = null,
    sectionsByCategory: Map<Long, List<TaskSection>> = emptyMap(),
    onSelectSection: (Long?) -> Unit = {},
    onSetPeriodicityEndDate: (LocalDate?) -> Unit = {},
) {
    TaskBottomSheetContent(
        categories = categories,
        selectedCategoryIdFromFilter = selectedCategoryIdFromFilter,
        formState = formState,
        onSetPriority = onSetPriority,
        onClearPriority = onClearPriority,
        onSetReminder = onSetReminder,
        onClearReminder = onClearReminder,
        onSetPeriodicity = onSetPeriodicity,
        onClearPeriodicity = onClearPeriodicity,
        onSetPeriodicityInterval = onSetPeriodicityInterval,
        onSetRepeatDays = onSetRepeatDays,
        onSetMonthDayOption = onSetMonthDayOption,
        onDismiss = onDismiss,
        onClearErrors = onClearErrors,
        onConfirm = onConfirm,
        modifier = modifier,
        onAutoSave = onAutoSave,
        task = task,
        onDelete = onDelete,
        onToggleCompletion = onToggleCompletion,
        placement = placement,
        dismissRequestKey = dismissRequestKey,
        attachmentActions = attachmentActions,
        sectionsByCategory = sectionsByCategory,
        onSelectSection = onSelectSection,
        onSetPeriodicityEndDate = onSetPeriodicityEndDate,
    )
}
