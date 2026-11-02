package com.mandrecode.tempo.features.tasks.presentation.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.mandrecode.tempo.R
import com.mandrecode.tempo.core.domain.model.MonthDayOption
import com.mandrecode.tempo.core.domain.model.Periodicity
import com.mandrecode.tempo.core.domain.model.Priority
import com.mandrecode.tempo.core.ui.components.ConnectedSelectorButton
import com.mandrecode.tempo.core.ui.components.DayOfWeekSelector
import com.mandrecode.tempo.core.ui.components.SelectorButtonGroup
import com.mandrecode.tempo.core.ui.components.TaskCompletionCheckbox
import com.mandrecode.tempo.core.ui.components.selectorButtonColors
import com.mandrecode.tempo.core.ui.editor.EDITOR_PROPERTY_ROW_GAP
import com.mandrecode.tempo.core.ui.editor.EDITOR_TEXT_FIELD_BOTTOM_INSET
import com.mandrecode.tempo.core.ui.editor.EditorAddButton
import com.mandrecode.tempo.core.ui.editor.EditorPropertyRow
import com.mandrecode.tempo.core.ui.editor.EditorTitleFieldState
import com.mandrecode.tempo.core.ui.editor.EditorValueChip
import com.mandrecode.tempo.core.ui.editor.editorTransparentTextFieldColors
import com.mandrecode.tempo.core.ui.theme.TempoIcon
import com.mandrecode.tempo.core.ui.theme.inputTitle
import com.mandrecode.tempo.core.ui.util.DescriptionEditorState
import com.mandrecode.tempo.core.ui.util.IncrementalLinkVisualTransformation
import com.mandrecode.tempo.core.ui.util.color
import com.mandrecode.tempo.core.ui.util.titleResId
import com.mandrecode.tempo.features.tasks.domain.model.Category
import com.mandrecode.tempo.features.tasks.domain.model.TaskSection
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first

@Composable
internal fun TaskBottomSheetBody(
    state: TaskBottomSheetBodyState,
    descriptionState: DescriptionEditorState,
    descriptionLinkVisualTransformation: IncrementalLinkVisualTransformation,
    actions: TaskBottomSheetBodyActions,
    focusConfig: TaskBottomSheetFocusConfig,
) {
    Column {
        TaskTitleSection(
            state = state,
            actions = actions,
            focusConfig = focusConfig,
        )

        TaskDescriptionSection(
            descriptionState = descriptionState,
            linkVisualTransformation = descriptionLinkVisualTransformation,
            descriptionError = state.formState.descriptionError,
            onDescriptionChange = actions.onTaskDescriptionChanged,
            focusConfig = focusConfig,
        )

        Spacer(modifier = Modifier.height(EDITOR_PROPERTY_ROW_GAP - EDITOR_TEXT_FIELD_BOTTOM_INSET))

        TaskCategorySection(
            state = state,
            onSelectCategory = actions.onSelectCategory,
        )

        TaskSectionSection(state = state, onSelectSection = actions.onSelectSection)

        Spacer(modifier = Modifier.height(EDITOR_PROPERTY_ROW_GAP))

        TaskPrioritySection(
            state = state,
            onSetPriority = actions.onSetPriority,
            onClearPriority = actions.onClearPriority,
        )

        Spacer(modifier = Modifier.height(EDITOR_PROPERTY_ROW_GAP))

        TaskReminderAndPeriodicitySection(
            state = state,
            actions = actions,
        )

        actions.attachmentActions?.let { attachmentActions ->
            Spacer(modifier = Modifier.height(EDITOR_PROPERTY_ROW_GAP))
            TaskAttachmentsSection(
                attachments = state.formState.attachments,
                errors = state.formState.attachmentErrors,
                onAddAttachments = attachmentActions.onAddAttachments,
                onRemoveAttachment = attachmentActions.onRemoveAttachment,
                onAttachmentOpenFail = attachmentActions.onAttachmentOpenFail,
            )
        }

        TaskBottomSheetFooter(
            isEditingTask = state.isEditingTask,
            taskTitle = state.taskTitle,
            autoSaveEnabled = state.autoSaveEnabled,
            onDelete = actions.onDelete,
            onRequestDismiss = actions.onRequestDismiss,
            onConfirmClick = actions.onConfirmClick,
        )
    }
}

@Composable
private fun TaskTitleSection(
    state: TaskBottomSheetBodyState,
    actions: TaskBottomSheetBodyActions,
    focusConfig: TaskBottomSheetFocusConfig,
) {
    val task = state.task
    val onToggleCompletion = actions.onToggleCompletion
    val focusManager = LocalFocusManager.current
    val titleField = remember { EditorTitleFieldState() }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (task != null && onToggleCompletion != null) {
            TaskCompletionCheckbox(
                isCompleted = task.isCompleted,
                onToggle = { onToggleCompletion(task) },
            )
            Spacer(modifier = Modifier.width(8.dp))
        }
        TextField(
            value = titleField.displayed(state.taskTitle),
            onValueChange = { titleField.onValueChange(it, state.taskTitle, actions.onTaskTitleChanged) },
            placeholder = {
                Text(
                    stringResource(R.string.task_title_placeholder),
                    style = MaterialTheme.typography.inputTitle,
                )
            },
            textStyle = MaterialTheme.typography.inputTitle,
            colors = editorTransparentTextFieldColors(),
            modifier =
                Modifier
                    .weight(1f)
                    .testTag(TASK_BOTTOM_SHEET_TITLE_FIELD_TEST_TAG)
                    .taskTitleFocus(focusConfig),
            keyboardOptions = TASK_TITLE_KEYBOARD_OPTIONS,
            keyboardActions =
                KeyboardActions(
                    onDone = {
                        defaultKeyboardAction(ImeAction.Done)
                        focusManager.clearFocus()
                    },
                ),
            singleLine = false,
            maxLines = 3,
            isError = state.isTitleError || state.formState.titleError != null,
            supportingText =
                if (state.isTitleError || state.formState.titleError != null) {
                    {
                        Text(
                            state.formState.titleError?.let { stringResource(it) }
                                ?: stringResource(R.string.task_title_required),
                        )
                    }
                } else {
                    null
                },
        )
    }
}

private val TASK_TITLE_KEYBOARD_OPTIONS =
    KeyboardOptions(
        capitalization = KeyboardCapitalization.Sentences,
        imeAction = ImeAction.Done,
    )

@Composable
private fun TaskDescriptionSection(
    descriptionState: DescriptionEditorState,
    linkVisualTransformation: IncrementalLinkVisualTransformation,
    descriptionError: Int?,
    onDescriptionChange: (TextFieldValue) -> Unit,
    focusConfig: TaskBottomSheetFocusConfig,
) {
    EditorPropertyRow(
        iconPainter = painterResource(R.drawable.ic_short_text),
        iconContentDescription = stringResource(R.string.description),
        verticalAlignment = Alignment.Top,
        iconBoxModifier = Modifier.width(48.dp).padding(top = 16.dp),
    ) {
        Column(
            modifier =
                Modifier
                    .weight(1f)
                    .padding(start = 4.dp),
        ) {
            TaskDescriptionField(
                descriptionState = descriptionState,
                linkVisualTransformation = linkVisualTransformation,
                descriptionError = descriptionError,
                onDescriptionChange = onDescriptionChange,
                focusConfig = focusConfig,
            )
        }
    }
}

@Composable
private fun TaskDescriptionField(
    descriptionState: DescriptionEditorState,
    linkVisualTransformation: IncrementalLinkVisualTransformation,
    descriptionError: Int?,
    onDescriptionChange: (TextFieldValue) -> Unit,
    focusConfig: TaskBottomSheetFocusConfig,
) {
    val descriptionValue = descriptionState.value

    TextField(
        value = descriptionValue,
        onValueChange = onDescriptionChange,
        placeholder = {
            Text(
                stringResource(R.string.add_details),
                style = MaterialTheme.typography.bodyLarge,
            )
        },
        textStyle = MaterialTheme.typography.bodyLarge,
        visualTransformation = linkVisualTransformation,
        colors = editorTransparentTextFieldColors(),
        modifier =
            Modifier
                .fillMaxWidth()
                .testTag(TASK_BOTTOM_SHEET_DESCRIPTION_FIELD_TEST_TAG)
                .taskDescriptionFocus(focusConfig),
        maxLines = 5,
        keyboardOptions =
            KeyboardOptions(
                capitalization = KeyboardCapitalization.Sentences,
            ),
        isError = descriptionError != null,
        supportingText =
            if (descriptionError != null) {
                { Text(stringResource(descriptionError)) }
            } else {
                null
            },
    )
}

@Composable
private fun TaskCategorySection(
    state: TaskBottomSheetBodyState,
    onSelectCategory: (Long) -> Unit,
) {
    val isAddingNewSubtask = state.task == null && state.formState.parentTaskId != null
    val categoryEnabled = !isAddingNewSubtask

    EditorPropertyRow(
        iconPainter = painterResource(R.drawable.ic_category),
        iconContentDescription = stringResource(R.string.category_label),
    ) {
        val categoryListState =
            rememberAutoScrollToSelectedCategory(
                categories = state.categories,
                selectedCategoryId = state.selectedCategoryId,
            )

        LazyRow(
            state = categoryListState,
            contentPadding = PaddingValues(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            itemsIndexed(state.categories) { index, category ->
                val isSelected = category.id == state.selectedCategoryId

                ConnectedSelectorButton(
                    label = category.name,
                    checked = isSelected,
                    index = index,
                    count = state.categories.size,
                    onClick = {
                        if (categoryEnabled) {
                            onSelectCategory(category.id)
                        }
                    },
                    enabled = categoryEnabled,
                    icon = {
                        val iconRes =
                            category.icon?.let { iconName ->
                                TempoIcon.fromName(iconName)?.iconRes
                            } ?: R.drawable.ic_category
                        Icon(
                            painter = painterResource(iconRes),
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                    },
                )
            }
        }
    }
}

/**
 * The Section options (#61): "No section" then the category's sections, as one connected
 * single-choice group that scrolls when long. Only for a top-level task in a sectioned category.
 */
@Composable
private fun TaskSectionSection(
    state: TaskBottomSheetBodyState,
    onSelectSection: (Long?) -> Unit,
) {
    val isSubtask = state.formState.parentTaskId != null || state.task?.parentTaskId != null
    // Moving the task to a category with sections (or out of one) opens and closes the row rather
    // than snapping it in; while it closes it keeps the sections it was showing.
    var shownSections by remember { mutableStateOf(state.sections) }
    if (state.sections.isNotEmpty()) shownSections = state.sections
    AnimatedVisibility(
        visible = state.sections.isNotEmpty() && !isSubtask,
        enter = expandVertically() + fadeIn(),
        exit = shrinkVertically() + fadeOut(),
    ) {
        Column {
            Spacer(modifier = Modifier.height(EDITOR_PROPERTY_ROW_GAP))
            TaskSectionRow(sections = shownSections, selectedSectionId = state.formState.sectionId, onSelectSection)
        }
    }
}

@Composable
private fun TaskSectionRow(
    sections: List<TaskSection>,
    selectedSectionId: Long?,
    onSelectSection: (Long?) -> Unit,
) {
    val options = listOf<TaskSection?>(null) + sections
    val selectedIndex = options.indexOfFirst { it?.id == selectedSectionId }.coerceAtLeast(0)
    val listState = rememberLazyListState()
    LaunchedEffect(selectedIndex, options.size) { listState.animateScrollToItem(selectedIndex) }

    EditorPropertyRow(
        iconPainter = painterResource(R.drawable.ic_view_agenda),
        iconContentDescription = stringResource(R.string.section_label),
    ) {
        LazyRow(
            state = listState,
            contentPadding = PaddingValues(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            itemsIndexed(options) { index, section ->
                ConnectedSelectorButton(
                    label = section?.name ?: stringResource(R.string.section_none),
                    checked = index == selectedIndex,
                    index = index,
                    count = options.size,
                    onClick = { onSelectSection(section?.id) },
                    modifier = Modifier.testTag("taskSectionChip_${section?.id ?: "none"}"),
                )
            }
        }
    }
}

@Composable
private fun rememberAutoScrollToSelectedCategory(
    categories: List<Category>,
    selectedCategoryId: Long,
): LazyListState {
    val listState = rememberLazyListState()

    LaunchedEffect(selectedCategoryId, categories) {
        val index = categories.indexOfFirst { it.id == selectedCategoryId }
        if (index < 0) return@LaunchedEffect

        val isFullyVisible =
            snapshotFlow {
                val info = listState.layoutInfo
                if (info.visibleItemsInfo.isEmpty()) {
                    null
                } else {
                    val item = info.visibleItemsInfo.firstOrNull { it.index == index }
                    item != null &&
                        item.offset >= info.viewportStartOffset &&
                        (item.offset + item.size) <= info.viewportEndOffset
                }
            }.filterNotNull().first()

        if (!isFullyVisible) {
            listState.animateScrollToItem(index)
        }
    }

    return listState
}

@Composable
private fun TaskPrioritySection(
    state: TaskBottomSheetBodyState,
    onSetPriority: (Priority) -> Unit,
    onClearPriority: () -> Unit,
) {
    EditorPropertyRow(
        iconPainter = painterResource(R.drawable.ic_flag),
        iconContentDescription = stringResource(R.string.sort_option_priority),
    ) {
        SelectorButtonGroup(
            options = Priority.priorities,
            isChecked = { it == state.formState.priority },
            onClick = { priority ->
                if (!state.isPriorityReadOnly) {
                    if (state.formState.priority == priority) onClearPriority() else onSetPriority(priority)
                }
            },
            label = { stringResource(it.titleResId) },
            enabled = !state.isPriorityReadOnly,
            colors = { priority ->
                selectorButtonColors(
                    checkedContainerColor = priority.color.copy(alpha = 0.2f),
                    checkedContentColor = priority.color,
                )
            },
            itemModifier = { Modifier.testTag("taskPriorityChip_${it.name}") },
            // No flag per option: the row's own icon is the flag, and three labels need the room.
            modifier = Modifier.weight(1f).padding(start = 4.dp),
        )
    }
}

@Composable
private fun TaskReminderAndPeriodicitySection(
    state: TaskBottomSheetBodyState,
    actions: TaskBottomSheetBodyActions,
) {
    Column {
        if (state.formattedReminder != null) {
            EditorPropertyRow(
                iconPainter = painterResource(R.drawable.ic_reminder),
                iconContentDescription = stringResource(R.string.reminder_label),
            ) {
                EditorValueChip(
                    label = state.formattedReminder,
                    onClick = actions.onSetReminderClicked,
                    onClear = actions.onClearReminder.takeIf { state.task == null || !state.task.isCompleted },
                    clearContentDescription = stringResource(R.string.clear_reminder),
                )
            }

            Spacer(modifier = Modifier.height(EDITOR_PROPERTY_ROW_GAP))

            if ((state.task == null || state.task.parentTaskId == null) && state.formState.parentTaskId == null) {
                TaskPeriodicitySection(
                    state = state,
                    actions = actions,
                )
            }
        } else {
            EditorPropertyRow(
                iconPainter = painterResource(R.drawable.ic_reminder),
                iconContentDescription = stringResource(R.string.reminder_label),
            ) {
                EditorAddButton(
                    label = stringResource(R.string.add_reminder),
                    onClick = actions.onSetReminderClicked,
                    enabled = state.task == null || !state.task.isCompleted,
                    modifier = Modifier.padding(start = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun TaskPeriodicitySection(
    state: TaskBottomSheetBodyState,
    actions: TaskBottomSheetBodyActions,
) {
    val isPeriodicityReadOnly = state.task?.isCompleted == true
    EditorPropertyRow(
        iconPainter = painterResource(R.drawable.ic_repeat),
        iconContentDescription = stringResource(R.string.repeat),
        iconBoxModifier =
            Modifier
                .width(48.dp)
                .then(
                    if (isPeriodicityReadOnly) {
                        Modifier.alpha(0.5f)
                    } else {
                        Modifier
                    },
                ),
    ) {
        val periodicityListState = rememberLazyListState()

        LaunchedEffect(state.formState.periodicity) {
            val index =
                Periodicity.periods.indexOfFirst {
                    it == state.formState.periodicity
                }
            if (index < 0) return@LaunchedEffect

            val isFullyVisible =
                snapshotFlow {
                    val info = periodicityListState.layoutInfo
                    if (info.visibleItemsInfo.isEmpty()) {
                        null
                    } else {
                        val item =
                            info.visibleItemsInfo.firstOrNull {
                                it.index == index
                            }
                        item != null &&
                            item.offset >= info.viewportStartOffset &&
                            (item.offset + item.size) <=
                            info.viewportEndOffset
                    }
                }.filterNotNull().first()

            if (!isFullyVisible) {
                periodicityListState.animateScrollToItem(index)
            }
        }

        LazyRow(
            state = periodicityListState,
            contentPadding = PaddingValues(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            itemsIndexed(Periodicity.periods) { index, period ->
                val isSelected = state.formState.periodicity == period

                ConnectedSelectorButton(
                    label =
                        periodicityChipLabel(
                            period = period,
                            isSelected = isSelected,
                            interval = state.formState.periodicityInterval,
                            repeatDays = state.formState.repeatDays,
                            monthDayOption = state.formState.monthDayOption,
                        ),
                    checked = isSelected,
                    index = index,
                    count = Periodicity.periods.size,
                    enabled = !isPeriodicityReadOnly,
                    onClick = {
                        if (isSelected) {
                            actions.onClearPeriodicity()
                        } else {
                            actions.onSetPeriodicity(period)
                        }
                    },
                    colors =
                        selectorButtonColors(),
                    modifier =
                        Modifier.testTag(
                            "taskPeriodicityChip_${period.name}",
                        ),
                )
            }
        }
    }

    AnimatedVisibility(
        visible = state.formState.periodicity != null,
        enter = expandVertically() + fadeIn(),
        exit = shrinkVertically() + fadeOut(),
    ) {
        Column {
            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Spacer(modifier = Modifier.width(48.dp))

                PeriodicityIntervalSelector(
                    interval = state.formState.periodicityInterval,
                    onIntervalChange = actions.onSetPeriodicityInterval,
                    modifier = Modifier.padding(start = 4.dp),
                    enabled = !isPeriodicityReadOnly,
                    maxInterval =
                        if (state.formState.periodicity == Periodicity.HOURLY) {
                            Periodicity.MAX_HOURLY_INTERVAL
                        } else {
                            Int.MAX_VALUE
                        },
                )
            }
        }
    }

    AnimatedContent(
        targetState = state.formState.periodicity,
        transitionSpec = {
            (fadeIn() + expandVertically()).togetherWith(
                fadeOut() + shrinkVertically(),
            )
        },
        label = "periodicity_sub_options",
    ) { periodicity ->
        when (periodicity) {
            Periodicity.WEEKLY -> {
                Column {
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Spacer(modifier = Modifier.width(48.dp))

                        DayOfWeekSelector(
                            selectedDays = state.formState.repeatDays,
                            onDaysChange = actions.onSetRepeatDays,
                            modifier = Modifier.weight(1f),
                            nullMeansEveryDay = false,
                            enabled = !isPeriodicityReadOnly,
                        )
                    }
                }
            }

            Periodicity.MONTHLY -> {
                Column {
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Spacer(modifier = Modifier.width(48.dp))

                        SelectorButtonGroup(
                            options = MonthDayOption.options,
                            isChecked = { option ->
                                state.formState.monthDayOption == option ||
                                    (state.formState.monthDayOption == null && option == MonthDayOption.SAME_DAY)
                            },
                            onClick = { option ->
                                actions.onSetMonthDayOption(option.takeUnless { it == MonthDayOption.SAME_DAY })
                            },
                            label = { option ->
                                when (option) {
                                    MonthDayOption.SAME_DAY -> stringResource(R.string.same_day)
                                    MonthDayOption.FIRST_DAY -> stringResource(R.string.first_day_of_month)
                                    MonthDayOption.LAST_DAY -> stringResource(R.string.last_day_of_month)
                                }
                            },
                            enabled = !isPeriodicityReadOnly,
                            colors = {
                                selectorButtonColors(
                                    checkedContainerColor = MaterialTheme.colorScheme.tertiaryContainer,
                                    checkedContentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                                )
                            },
                            modifier = Modifier.weight(1f).padding(start = 4.dp),
                        )
                    }
                }
            }

            else -> { /* No sub-options for DAILY / YEARLY */ }
        }
    }

    AnimatedVisibility(
        visible = state.formState.periodicity != null,
        enter = expandVertically() + fadeIn(),
        exit = shrinkVertically() + fadeOut(),
    ) {
        PeriodicityEndDateRow(
            endDate = state.formState.periodicityEndDate,
            reminderDate = state.formState.reminderDate?.date,
            enabled = !isPeriodicityReadOnly,
            onEndDateChange = actions.onSetPeriodicityEndDate,
        )
    }
}
