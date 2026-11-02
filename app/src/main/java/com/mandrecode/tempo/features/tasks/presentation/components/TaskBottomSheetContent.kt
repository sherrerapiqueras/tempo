package com.mandrecode.tempo.features.tasks.presentation.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SelectableDates
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.mandrecode.tempo.core.domain.model.DayOfWeek
import com.mandrecode.tempo.core.domain.model.MonthDayOption
import com.mandrecode.tempo.core.domain.model.Periodicity
import com.mandrecode.tempo.core.domain.model.Priority
import com.mandrecode.tempo.core.ui.adaptive.SheetPlacement
import com.mandrecode.tempo.core.ui.components.HandleReminderPermissions
import com.mandrecode.tempo.core.ui.components.TempoTimePickerDialog
import com.mandrecode.tempo.core.ui.editor.EDITOR_AUTO_SAVE_DEBOUNCE_MS
import com.mandrecode.tempo.core.ui.editor.editorSaveableKey
import com.mandrecode.tempo.core.ui.editor.rememberEditorAutosaveController
import com.mandrecode.tempo.core.ui.editor.resolveEditorTitleChange
import com.mandrecode.tempo.core.ui.util.DebouncedSnapshotEffect
import com.mandrecode.tempo.core.ui.util.DescriptionEditorState
import com.mandrecode.tempo.core.ui.util.IncrementalLinkVisualTransformation
import com.mandrecode.tempo.features.tasks.domain.model.Category
import com.mandrecode.tempo.features.tasks.domain.model.Task
import com.mandrecode.tempo.features.tasks.domain.model.TaskSection
import com.mandrecode.tempo.features.tasks.presentation.TasksContract
import com.mandrecode.tempo.infrastructure.permissions.hasNotificationPermissions
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.format.FormatStringsInDatetimeFormats
import kotlinx.datetime.number
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock

@OptIn(ExperimentalMaterial3Api::class, FormatStringsInDatetimeFormats::class)
@Composable
internal fun TaskBottomSheetContent(
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
    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current
    val editingTargetId = task?.id
    val editorKey =
        remember(formState.editorSessionId, editingTargetId) {
            editorSaveableKey(formState.editorSessionId, editingTargetId)
        }
    var taskTitle by rememberSaveable(editorKey) { mutableStateOf(task?.title ?: "") }
    val initialDescription = remember(editorKey) { task?.description.orEmpty() }
    var savedDescription by
        rememberSaveable(editorKey, stateSaver = TextFieldValue.Saver) {
            mutableStateOf(TextFieldValue(initialDescription))
        }
    val descriptionState =
        remember(editorKey) {
            DescriptionEditorState(savedDescription)
        }
    val descriptionLinkVisualTransformation = rememberLinkStyling(descriptionState)
    val updateDescription: (TextFieldValue) -> Unit = { value ->
        descriptionLinkVisualTransformation.update(value.text)
        savedDescription = value
        descriptionState.update(value)
    }
    var isDescriptionDirty by rememberSaveable(editorKey) { mutableStateOf(false) }

    val defaultCategoryId = categories.firstOrNull { it.isDefault }?.id ?: categories.firstOrNull()?.id ?: 0L
    val initialCategoryId =
        when {
            task != null -> task.categoryId
            selectedCategoryIdFromFilter != null &&
                selectedCategoryIdFromFilter != defaultCategoryId ->
                selectedCategoryIdFromFilter
            else -> defaultCategoryId
        }
    var selectedCategoryId by
        rememberSaveable(editorKey, selectedCategoryIdFromFilter, defaultCategoryId) {
            mutableLongStateOf(initialCategoryId)
        }
    var isTitleError by remember(editorKey) { mutableStateOf(false) }
    var showPermissionCheck by remember { mutableStateOf(false) }
    val titleFocusRequester = remember { FocusRequester() }
    val descriptionFocusRequester = remember { FocusRequester() }
    val editorFocusState = remember { TaskEditorFocusState() }
    var focusDescriptionTrigger by remember { mutableIntStateOf(0) }

    LaunchedEffect(focusDescriptionTrigger) {
        if (focusDescriptionTrigger > 0) {
            descriptionFocusRequester.requestFocus()
        }
    }

    val formattedReminder = rememberFormattedDateTime(formState.reminderDate)
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    var tempSelectedDate by remember { mutableStateOf<LocalDate?>(null) }

    val onSetReminderClicked: () -> Unit = {
        if (task == null || !task.isCompleted) {
            showPermissionCheck = true
        }
    }

    if (showDatePicker) {
        val futureDatesOnly =
            remember {
                object : SelectableDates {
                    override fun isSelectableDate(utcTimeMillis: Long): Boolean {
                        val today =
                            Clock.System
                                .now()
                                .toLocalDateTime(TimeZone.currentSystemDefault())
                                .date
                        return utcTimeMillis >= today.atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds()
                    }
                }
            }
        com.mandrecode.tempo.core.ui.components.TempoDatePickerDialog(
            initialDate =
                formState.reminderDate?.date
                    ?: Clock.System
                        .now()
                        .toLocalDateTime(TimeZone.currentSystemDefault())
                        .date,
            onConfirm = { date ->
                tempSelectedDate = date
                showDatePicker = false
                showTimePicker = true
            },
            onDismiss = { showDatePicker = false },
            selectableDates = futureDatesOnly,
        )
    }

    if (showTimePicker) {
        val now = remember { Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()) }

        TempoTimePickerDialog(
            initialHour = formState.reminderDate?.hour ?: now.hour,
            initialMinute = formState.reminderDate?.minute ?: now.minute,
            onConfirm = { hour, minute ->
                val dateToUse = tempSelectedDate ?: now.date

                onSetReminder(
                    dateToUse.year,
                    dateToUse.month.number,
                    dateToUse.day,
                    hour,
                    minute,
                )
                showTimePicker = false
                tempSelectedDate = null
            },
            onDismiss = {
                showTimePicker = false
                tempSelectedDate = null
            },
        )
    }

    if (showPermissionCheck) {
        HandleReminderPermissions(
            show = true,
            onGrantPermissions = {
                showDatePicker = true
                showPermissionCheck = false
            },
            onDismiss = {
                showPermissionCheck = false
            },
        )
    }

    val currentOnClearReminder by rememberUpdatedState(onClearReminder)
    LaunchedEffect(task) {
        if (task != null && formState.reminderDate != null) {
            if (!context.hasNotificationPermissions()) {
                currentOnClearReminder()
            }
        }
    }

    LaunchedEffect(Unit) {
        if (task == null) {
            titleFocusRequester.requestFocus()
        }
    }

    val taskSnapshot =
        remember(editorKey) {
            task?.let {
                TaskFormSnapshot(
                    title = it.title,
                    description = it.description,
                    categoryId = it.categoryId,
                    priority = it.priority,
                    reminderDate = it.reminderDate,
                    periodicity = it.periodicity,
                    periodicityInterval = it.periodicityInterval,
                    repeatDays = it.repeatDays,
                    monthDayOption = it.monthDayOption,
                    periodicityEndDate = it.periodicityEndDate,
                    sectionId = it.sectionId,
                )
            }
        }

    val hasUnsavedChanges =
        remember(
            taskSnapshot,
            taskTitle,
            isDescriptionDirty,
            selectedCategoryId,
            initialCategoryId,
            formState.priority,
            formState.reminderDate,
            formState.periodicity,
            formState.periodicityInterval,
            formState.repeatDays,
            formState.monthDayOption,
            formState.periodicityEndDate,
            formState.attachments,
            formState.sectionId,
        ) {
            if (taskSnapshot == null) {
                taskTitle.isNotBlank() ||
                    isDescriptionDirty ||
                    // Files picked into a task that does not exist yet go with the form.
                    formState.attachments.isNotEmpty() ||
                    selectedCategoryId != initialCategoryId ||
                    formState.priority != null ||
                    formState.reminderDate != null ||
                    formState.periodicity != null
            } else {
                taskTitle != taskSnapshot.title ||
                    isDescriptionDirty ||
                    selectedCategoryId != taskSnapshot.categoryId ||
                    formState.priority != taskSnapshot.priority ||
                    formState.reminderDate != taskSnapshot.reminderDate ||
                    formState.periodicity != taskSnapshot.periodicity ||
                    formState.periodicityInterval != taskSnapshot.periodicityInterval ||
                    formState.repeatDays != taskSnapshot.repeatDays ||
                    formState.monthDayOption != taskSnapshot.monthDayOption ||
                    formState.periodicityEndDate != taskSnapshot.periodicityEndDate ||
                    formState.sectionId != taskSnapshot.sectionId
            }
        }
    val isEditingTask = task != null
    val autoSaveEnabled = isEditingTask && onAutoSave != null
    val isPriorityReadOnly = task?.isCompleted == true

    val autosaveController =
        rememberEditorAutosaveController(initialSnapshot = taskSnapshot, key = editorKey)
    val dispatchAutosave: (TaskFormSnapshot) -> Unit = { snapshot ->
        onAutoSave?.invoke(snapshot.title, snapshot.description, snapshot.categoryId)
    }
    DebouncedSnapshotEffect(
        enabled = autoSaveEnabled,
        key = editorKey,
        debounceMillis = EDITOR_AUTO_SAVE_DEBOUNCE_MS,
        snapshotProvider = {
            TaskFormSnapshot(
                title = taskTitle,
                description = descriptionState.value.text,
                categoryId = selectedCategoryId,
                priority = formState.priority,
                reminderDate = formState.reminderDate,
                periodicity = formState.periodicity,
                periodicityInterval = formState.periodicityInterval,
                repeatDays = formState.repeatDays,
                monthDayOption = formState.monthDayOption,
                periodicityEndDate = formState.periodicityEndDate,
                sectionId = formState.sectionId,
            )
        },
        onSnapshot = { snapshot ->
            autosaveController.trySave(
                snapshot,
                isSaveable = { it.title.isNotBlank() },
                onSave = dispatchAutosave,
            )
        },
    )

    val onSheetDismissRequest: () -> Unit = {
        val currentSnapshot =
            TaskFormSnapshot(
                title = taskTitle,
                description = descriptionState.value.text,
                categoryId = selectedCategoryId,
                priority = formState.priority,
                reminderDate = formState.reminderDate,
                periodicity = formState.periodicity,
                periodicityInterval = formState.periodicityInterval,
                repeatDays = formState.repeatDays,
                monthDayOption = formState.monthDayOption,
                periodicityEndDate = formState.periodicityEndDate,
                sectionId = formState.sectionId,
            )
        if (autoSaveEnabled && currentSnapshot.title.isNotBlank()) {
            autosaveController.trySave(
                currentSnapshot,
                isSaveable = { it.title.isNotBlank() },
                onSave = dispatchAutosave,
            )
        }
        onDismiss()
    }

    com.mandrecode.tempo.core.ui.components.TempoModalBottomSheet(
        onDismissRequest = onSheetDismissRequest,
        modifier = modifier,
        hasUnsavedChanges = if (autoSaveEnabled && taskTitle.isNotBlank()) false else hasUnsavedChanges,
        adaptivePlacement = true,
        placement = placement,
        dismissRequestKey = dismissRequestKey,
    ) { onRequestDismiss ->
        TaskEditorBackHandler(focusState = editorFocusState)
        val focusManager = LocalFocusManager.current
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp)
                    .padding(top = 8.dp, bottom = 32.dp),
        ) {
            TaskBottomSheetBody(
                descriptionState = descriptionState,
                descriptionLinkVisualTransformation = descriptionLinkVisualTransformation,
                state =
                    TaskBottomSheetBodyState(
                        categories = categories,
                        formState = formState,
                        task = task,
                        taskTitle = taskTitle,
                        selectedCategoryId = selectedCategoryId,
                        formattedReminder = formattedReminder,
                        isTitleError = isTitleError,
                        autoSaveEnabled = autoSaveEnabled,
                        isEditingTask = isEditingTask,
                        isPriorityReadOnly = isPriorityReadOnly,
                        sections = sectionsByCategory[selectedCategoryId].orEmpty(),
                    ),
                actions =
                    TaskBottomSheetBodyActions(
                        onTaskTitleChanged = { newValue ->
                            val change = resolveEditorTitleChange(newValue, taskTitle, descriptionState.value.text)
                            if (change == null) {
                                focusManager.clearFocus()
                            } else {
                                taskTitle = change.title
                                isTitleError = change.title.isBlank()
                                onClearErrors()
                                change.description?.let {
                                    updateDescription(it)
                                    isDescriptionDirty = descriptionState.value.text != initialDescription
                                    focusDescriptionTrigger++
                                }
                            }
                        },
                        onTaskDescriptionChanged = {
                            updateDescription(
                                applyTaskDescriptionDashFormatting(
                                    previousValue = descriptionState.value,
                                    proposedValue = it,
                                ),
                            )
                            isDescriptionDirty = descriptionState.value.text != initialDescription
                            onClearErrors()
                        },
                        onSelectCategory = { categoryId ->
                            // A section belongs to one category (#61): picking another clears it.
                            if (categoryId != selectedCategoryId) onSelectSection(null)
                            selectedCategoryId = categoryId
                        },
                        onSetPriority = onSetPriority,
                        onClearPriority = onClearPriority,
                        onSetReminderClicked = onSetReminderClicked,
                        onClearReminder = onClearReminder,
                        onSetPeriodicity = onSetPeriodicity,
                        onClearPeriodicity = onClearPeriodicity,
                        onSetPeriodicityInterval = onSetPeriodicityInterval,
                        onSetRepeatDays = onSetRepeatDays,
                        onSetMonthDayOption = onSetMonthDayOption,
                        onRequestDismiss = onRequestDismiss,
                        onDelete = onDelete,
                        onToggleCompletion = onToggleCompletion,
                        onConfirmClick = {
                            isTitleError = taskTitle.isBlank()
                            if (!isTitleError) {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onConfirm(
                                    taskTitle,
                                    descriptionState.value.text,
                                    selectedCategoryId,
                                )
                            }
                        },
                        attachmentActions = attachmentActions,
                        onSelectSection = onSelectSection,
                        onSetPeriodicityEndDate = onSetPeriodicityEndDate,
                    ),
                focusConfig =
                    TaskBottomSheetFocusConfig(
                        titleFocusRequester = titleFocusRequester,
                        descriptionFocusRequester = descriptionFocusRequester,
                        focusState = editorFocusState,
                        clearEditorFocus = { focusManager.clearFocus() },
                    ),
            )
        }
    }
}

@Composable
private fun rememberLinkStyling(descriptionState: DescriptionEditorState): IncrementalLinkVisualTransformation {
    val linkColor = MaterialTheme.colorScheme.primary
    return remember(descriptionState, linkColor) {
        IncrementalLinkVisualTransformation(
            initialText = descriptionState.value.text,
            linkColor = linkColor,
        )
    }
}
