package com.mandrecode.tempo.features.routines.presentation.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.mandrecode.tempo.R
import com.mandrecode.tempo.core.ui.components.ColorPicker
import com.mandrecode.tempo.core.ui.components.DayOfWeekSelector
import com.mandrecode.tempo.core.ui.components.HabitCompletionCheckbox
import com.mandrecode.tempo.core.ui.components.IconPicker
import com.mandrecode.tempo.core.ui.components.SelectorButtonGroup
import com.mandrecode.tempo.core.ui.editor.EDITOR_PROPERTY_ROW_GAP
import com.mandrecode.tempo.core.ui.editor.EDITOR_TEXT_FIELD_BOTTOM_INSET
import com.mandrecode.tempo.core.ui.editor.EditorAddButton
import com.mandrecode.tempo.core.ui.editor.EditorPropertyRow
import com.mandrecode.tempo.core.ui.editor.EditorTitleFieldState
import com.mandrecode.tempo.core.ui.editor.EditorValueChip
import com.mandrecode.tempo.core.ui.editor.editorTransparentTextFieldColors
import com.mandrecode.tempo.core.ui.theme.inputTitle
import com.mandrecode.tempo.core.ui.theme.resolveColor
import com.mandrecode.tempo.core.ui.util.DescriptionEditorState
import com.mandrecode.tempo.features.routines.domain.model.HabitType
import com.mandrecode.tempo.features.routines.presentation.RoutinesContract.HabitSheetTab
import com.mandrecode.tempo.features.routines.presentation.components.sections.HabitHistoryView
import com.mandrecode.tempo.util.CompletionHistoryUtil
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.todayIn
import kotlin.time.Clock

internal const val HABIT_BOTTOM_SHEET_TITLE_FIELD_TEST_TAG = "habit_bottom_sheet_title_field"

@Composable
internal fun HabitBottomSheetBody(
    state: HabitBottomSheetBodyState,
    descriptionState: DescriptionEditorState,
    actions: HabitBottomSheetBodyActions,
    focusConfig: HabitBottomSheetFocusConfig,
) {
    Column {
        val editingHabit = state.formState.editingHabit
        val editingHabitChain = state.formState.editingHabitChain
        val isEditing = editingHabit != null || editingHabitChain != null
        val isCreatingForChain = state.formState.targetChainId != null

        if (!isEditing && !isCreatingForChain) {
            SelectorButtonGroup(
                options = HabitSheetTab.entries,
                isChecked = { it == state.formState.selectedTab },
                onClick = { actions.onSelectTab(it) },
                label = { tab ->
                    when (tab) {
                        HabitSheetTab.HABIT -> stringResource(R.string.habit)
                        HabitSheetTab.HABIT_CHAIN -> stringResource(R.string.habit_chain)
                    }
                },
            )

            Spacer(modifier = Modifier.height(24.dp))
        }

        AnimatedVisibility(
            visible =
                state.formState.selectedTab == HabitSheetTab.HABIT &&
                    !isCreatingForChain &&
                    !state.isHabitInChain,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            Column {
                HabitTypeSelector(
                    selectedType = state.formState.selectedHabitType,
                    onTypeSelect = actions.onSetHabitType,
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(modifier = Modifier.height(24.dp))
            }
        }

        HabitTitleSection(
            state = state,
            actions = actions,
            focusConfig = focusConfig,
            editingHabit = editingHabit,
            editingHabitChain = editingHabitChain,
        )

        HabitDescriptionSection(
            descriptionState = descriptionState,
            descriptionError = state.formState.descriptionError,
            onDescriptionChange = actions.onDescriptionChanged,
            focusConfig = focusConfig,
        )

        Spacer(modifier = Modifier.height(EDITOR_PROPERTY_ROW_GAP - EDITOR_TEXT_FIELD_BOTTOM_INSET))

        HabitIconSection(
            selectedIcon = state.formState.selectedIcon,
            onSetIcon = actions.onSetIcon,
            onClearIcon = actions.onClearIcon,
        )

        Spacer(modifier = Modifier.height(EDITOR_PROPERTY_ROW_GAP))

        if (!state.isHabitInChain) {
            HabitColorAndRepeatSection(
                state = state,
                actions = actions,
            )
        }

        if (state.formState.selectedTab == HabitSheetTab.HABIT_CHAIN) {
            HabitChainSelectionSection(
                habits = state.habits,
                selectedHabitIds = state.selectedHabitIds,
                onSelectHabits = actions.onSelectHabits,
                selectedDate = state.selectedDate,
                onToggleHabitCompletion =
                    if (editingHabitChain != null) {
                        actions.onToggleHabitCompletion
                    } else {
                        null
                    },
            )

            Spacer(modifier = Modifier.height(EDITOR_PROPERTY_ROW_GAP))
        }

        val showReminderUI =
            when (state.formState.selectedTab) {
                HabitSheetTab.HABIT -> !state.isHabitInChain
                HabitSheetTab.HABIT_CHAIN -> true
            }
        val showHistory =
            when (state.formState.selectedTab) {
                HabitSheetTab.HABIT -> editingHabit != null
                HabitSheetTab.HABIT_CHAIN -> editingHabitChain != null
            }

        if (showReminderUI) {
            HabitReminderSection(
                formattedReminder = state.formattedReminder,
                onSetReminderClick = actions.onSetReminderClicked,
                onClearReminder = actions.onClearReminder,
            )

            Spacer(modifier = Modifier.height(EDITOR_PROPERTY_ROW_GAP))
        }

        if (state.formState.selectedTab == HabitSheetTab.HABIT && editingHabit != null) {
            HabitHistorySection(
                completionHistory = editingHabit.completionHistory,
                createdDate = editingHabit.createdDate.date,
                repeatDays = editingHabit.repeatDays,
                habitType = state.formState.selectedHabitType,
                vacationPeriods = state.vacationPeriods,
            )

            Spacer(modifier = Modifier.height(EDITOR_PROPERTY_ROW_GAP))
        }

        if (state.formState.selectedTab == HabitSheetTab.HABIT_CHAIN && editingHabitChain != null) {
            HabitHistorySection(
                completionHistory = editingHabitChain.completionHistory,
                createdDate = editingHabitChain.createdDate.date,
                repeatDays = editingHabitChain.repeatDays,
                habitType = null,
                vacationPeriods = state.vacationPeriods,
            )

            Spacer(modifier = Modifier.height(EDITOR_PROPERTY_ROW_GAP))
        }

        // Closes out the form: by this point the reminder row and the history dots have both
        // been seen, so the note explains what the pause does to them without interrupting them.
        if (state.isVacationModeActive && (showReminderUI || showHistory)) {
            VacationModeNotice()

            Spacer(modifier = Modifier.height(EDITOR_PROPERTY_ROW_GAP))
        }

        HabitBottomSheetFooter(
            selectedTab = state.formState.selectedTab,
            editingHabit = editingHabit != null,
            editingHabitChain = editingHabitChain != null,
            selectedHabitIds = state.selectedHabitIds,
            title = state.title,
            hasUnsavedChanges = state.hasUnsavedChanges,
            autoSaveEnabled = state.autoSaveEnabled,
            onDeleteHabit = actions.onDeleteHabit,
            onDeleteHabitChain = actions.onDeleteHabitChain,
            onRequestDismiss = actions.onRequestDismiss,
            onConfirmClick = actions.onConfirmClick,
        )
    }
}

@Composable
private fun HabitTitleSection(
    state: HabitBottomSheetBodyState,
    actions: HabitBottomSheetBodyActions,
    focusConfig: HabitBottomSheetFocusConfig,
    editingHabit: com.mandrecode.tempo.features.routines.domain.model.Habit?,
    editingHabitChain: com.mandrecode.tempo.features.routines.domain.model.HabitChain?,
) {
    val onToggleHabitCompletion = actions.onToggleHabitCompletion
    when {
        state.formState.selectedTab == HabitSheetTab.HABIT &&
            editingHabit != null &&
            onToggleHabitCompletion != null ->
            HabitTitleCheckboxRow(
                state = state,
                actions = actions,
                focusConfig = focusConfig,
                editingHabit = editingHabit,
                onToggleHabitCompletion = onToggleHabitCompletion,
            )

        state.formState.selectedTab == HabitSheetTab.HABIT_CHAIN &&
            editingHabitChain != null &&
            onToggleHabitCompletion != null ->
            HabitChainTitleCheckboxRow(
                state = state,
                actions = actions,
                focusConfig = focusConfig,
                editingHabitChain = editingHabitChain,
                onToggleHabitCompletion = onToggleHabitCompletion,
            )

        else ->
            HabitTitleField(
                state = state,
                actions = actions,
                focusConfig = focusConfig,
                modifier = Modifier.fillMaxWidth(),
            )
    }
}

@Composable
private fun HabitTitleCheckboxRow(
    state: HabitBottomSheetBodyState,
    actions: HabitBottomSheetBodyActions,
    focusConfig: HabitBottomSheetFocusConfig,
    editingHabit: com.mandrecode.tempo.features.routines.domain.model.Habit,
    onToggleHabitCompletion: (habitId: Long, isCompleted: Boolean) -> Unit,
) {
    val isCompleted =
        remember(editingHabit.completionHistory, state.selectedDate) {
            CompletionHistoryUtil.isDateInHistory(editingHabit.completionHistory, state.selectedDate.toString())
        }
    val today = Clock.System.todayIn(TimeZone.currentSystemDefault())
    val yesterday = today.minus(DatePeriod(days = 1))
    HabitCheckboxTitleRow(
        state = state,
        actions = actions,
        focusConfig = focusConfig,
        isCompleted = isCompleted,
        canToggle = state.selectedDate == today || state.selectedDate == yesterday,
        iconName = state.formState.selectedIcon ?: editingHabit.icon,
        colorKey = state.formState.selectedColorKey ?: editingHabit.colorKey,
        onToggle = { onToggleHabitCompletion(editingHabit.id, !isCompleted) },
    )
}

@Composable
internal fun HabitCheckboxTitleRow(
    state: HabitBottomSheetBodyState,
    actions: HabitBottomSheetBodyActions,
    focusConfig: HabitBottomSheetFocusConfig,
    isCompleted: Boolean,
    canToggle: Boolean,
    iconName: String?,
    colorKey: String?,
    onToggle: () -> Unit,
) {
    val resolvedColor =
        remember(colorKey, state.colorScheme, state.isDarkTheme) {
            colorKey?.let { resolveColor(it, state.colorScheme, state.isDarkTheme) }
        }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HabitCompletionCheckbox(
            isCompleted = isCompleted,
            onToggle = onToggle,
            color = resolvedColor,
            iconName = iconName,
            canToggle = canToggle,
            isContainerCompleted = false,
        )
        Spacer(modifier = Modifier.width(8.dp))
        HabitTitleField(
            state = state,
            actions = actions,
            focusConfig = focusConfig,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun HabitTitleField(
    state: HabitBottomSheetBodyState,
    actions: HabitBottomSheetBodyActions,
    focusConfig: HabitBottomSheetFocusConfig,
    modifier: Modifier = Modifier,
) {
    val focusManager = LocalFocusManager.current
    val titleField = remember { EditorTitleFieldState() }
    TextField(
        value = titleField.displayed(state.title),
        onValueChange = { titleField.onValueChange(it, state.title, actions.onTitleChanged) },
        placeholder = {
            Text(
                when (state.formState.selectedTab) {
                    HabitSheetTab.HABIT ->
                        when (state.formState.selectedHabitType) {
                            HabitType.BUILD -> stringResource(R.string.habit_title_placeholder)
                            HabitType.QUIT -> stringResource(R.string.habit_title_placeholder_quit)
                        }

                    HabitSheetTab.HABIT_CHAIN -> stringResource(R.string.habit_chain_title_placeholder)
                },
                style = MaterialTheme.typography.inputTitle,
            )
        },
        textStyle = MaterialTheme.typography.inputTitle,
        colors = editorTransparentTextFieldColors(),
        modifier =
            modifier
                .testTag(HABIT_BOTTOM_SHEET_TITLE_FIELD_TEST_TAG)
                .focusRequester(focusConfig.titleFocusRequester),
        keyboardOptions =
            KeyboardOptions(
                capitalization = KeyboardCapitalization.Sentences,
                imeAction = ImeAction.Done,
            ),
        keyboardActions =
            androidx.compose.foundation.text.KeyboardActions(
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

@Composable
private fun HabitDescriptionSection(
    descriptionState: DescriptionEditorState,
    descriptionError: Int?,
    onDescriptionChange: (androidx.compose.ui.text.input.TextFieldValue) -> Unit,
    focusConfig: HabitBottomSheetFocusConfig,
) {
    EditorPropertyRow(
        iconPainter = painterResource(R.drawable.ic_short_text),
        iconContentDescription = stringResource(R.string.description),
        verticalAlignment = Alignment.Top,
        iconBoxModifier = Modifier.width(48.dp).padding(top = 16.dp),
    ) {
        TextField(
            value = descriptionState.value,
            onValueChange = onDescriptionChange,
            placeholder = {
                Text(
                    stringResource(R.string.add_details),
                    style = MaterialTheme.typography.bodyLarge,
                )
            },
            textStyle = MaterialTheme.typography.bodyLarge,
            colors = editorTransparentTextFieldColors(),
            modifier =
                Modifier
                    .weight(1f)
                    .padding(start = 4.dp)
                    .testTag(HABIT_BOTTOM_SHEET_DESCRIPTION_FIELD_TEST_TAG)
                    .focusRequester(focusConfig.descriptionFocusRequester),
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
}

@Composable
private fun HabitIconSection(
    selectedIcon: String?,
    onSetIcon: (String) -> Unit,
    onClearIcon: () -> Unit,
) {
    EditorPropertyRow(
        iconPainter = painterResource(R.drawable.ic_mood),
        iconContentDescription = stringResource(R.string.habit_icon_label),
        verticalAlignment = Alignment.Top,
        iconBoxModifier = Modifier.size(48.dp),
    ) {
        IconPicker(
            selectedIconName = selectedIcon,
            onSelectIcon = onSetIcon,
            onClearIcon = onClearIcon,
            enabled = true,
            disabledMessage = null,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun HabitColorAndRepeatSection(
    state: HabitBottomSheetBodyState,
    actions: HabitBottomSheetBodyActions,
) {
    Column {
        EditorPropertyRow(
            iconPainter = painterResource(R.drawable.ic_draw),
            iconContentDescription = stringResource(R.string.material_you_colors),
            verticalAlignment = Alignment.Top,
            iconBoxModifier = Modifier.size(48.dp),
        ) {
            ColorPicker(
                selectedColorKey = state.formState.selectedColorKey,
                onSelectColorKey = actions.onSetColorKey,
                onClearColor = actions.onClearColor,
                modifier = Modifier.weight(1f),
            )
        }

        Spacer(modifier = Modifier.height(EDITOR_PROPERTY_ROW_GAP))

        val showRepeatDays =
            state.formState.selectedTab != HabitSheetTab.HABIT ||
                state.formState.selectedHabitType != HabitType.QUIT
        AnimatedVisibility(
            visible = showRepeatDays,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            Column {
                // Beside the icon like every other row; the selector scrolls on a phone too narrow
                // for seven comfortable targets.
                EditorPropertyRow(
                    iconPainter = painterResource(R.drawable.ic_repeat),
                    iconContentDescription = stringResource(R.string.repeat),
                ) {
                    DayOfWeekSelector(
                        selectedDays = state.formState.selectedRepeatDays,
                        onDaysChange = { days -> actions.onSetRepeatDays?.invoke(days) },
                        modifier = Modifier.weight(1f).padding(start = 4.dp),
                    )
                }

                Spacer(modifier = Modifier.height(EDITOR_PROPERTY_ROW_GAP))
            }
        }
    }
}

@Composable
private fun HabitChainSelectionSection(
    habits: List<com.mandrecode.tempo.features.routines.domain.model.Habit>,
    selectedHabitIds: List<Long>,
    onSelectHabits: (List<Long>) -> Unit,
    selectedDate: kotlinx.datetime.LocalDate,
    onToggleHabitCompletion: ((habitId: Long, isCompleted: Boolean) -> Unit)?,
) {
    val iconTopPadding = if (selectedHabitIds.isEmpty()) 12.dp else 16.dp

    EditorPropertyRow(
        iconPainter = painterResource(R.drawable.ic_list),
        iconContentDescription = stringResource(R.string.select_habits),
        verticalAlignment = Alignment.Top,
        iconBoxModifier =
            Modifier
                .width(48.dp)
                .padding(top = iconTopPadding),
    ) {
        HabitMultiSelector(
            habits = habits,
            selectedHabitIds = selectedHabitIds,
            onSelectHabits = onSelectHabits,
            modifier = Modifier.weight(1f),
            selectedDate = selectedDate,
            onToggleHabitCompletion = onToggleHabitCompletion,
        )
    }
}

@Composable
private fun HabitReminderSection(
    formattedReminder: String?,
    onSetReminderClick: () -> Unit,
    onClearReminder: () -> Unit,
) {
    EditorPropertyRow(
        iconPainter = painterResource(R.drawable.ic_reminder),
        iconContentDescription = stringResource(R.string.reminder_label),
    ) {
        if (formattedReminder != null) {
            EditorValueChip(
                label = formattedReminder,
                onClick = onSetReminderClick,
                onClear = onClearReminder,
                clearContentDescription = stringResource(R.string.clear_reminder),
            )
        } else {
            EditorAddButton(
                label = stringResource(R.string.add_reminder),
                onClick = onSetReminderClick,
                modifier = Modifier.padding(start = 4.dp),
            )
        }
    }
}

@Composable
private fun HabitHistorySection(
    completionHistory: String,
    createdDate: kotlinx.datetime.LocalDate,
    repeatDays: Set<com.mandrecode.tempo.core.domain.model.DayOfWeek>?,
    habitType: HabitType?,
    vacationPeriods: List<com.mandrecode.tempo.core.domain.model.VacationPeriod>,
) {
    EditorPropertyRow(
        iconPainter = painterResource(R.drawable.ic_calendar),
        iconContentDescription = stringResource(R.string.completion_history),
    ) {
        Column(
            modifier = Modifier.weight(1f),
        ) {
            HabitHistoryView(
                completionHistory = completionHistory,
                createdDate = createdDate,
                modifier = Modifier.fillMaxWidth(),
                repeatDays = repeatDays,
                habitType = habitType ?: HabitType.BUILD,
                vacationPeriods = vacationPeriods,
            )
        }
    }
}
