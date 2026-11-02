package com.mandrecode.tempo.features.routines.presentation.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.listSaver
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
import com.mandrecode.tempo.core.domain.model.VacationPeriod
import com.mandrecode.tempo.core.ui.adaptive.SheetPlacement
import com.mandrecode.tempo.core.ui.components.HandleReminderPermissions
import com.mandrecode.tempo.core.ui.components.TempoTimePickerDialog
import com.mandrecode.tempo.core.ui.editor.EDITOR_AUTO_SAVE_DEBOUNCE_MS
import com.mandrecode.tempo.core.ui.editor.canAutosaveEditorTitle
import com.mandrecode.tempo.core.ui.editor.editorSaveableKey
import com.mandrecode.tempo.core.ui.editor.editorSheetHorizontalPadding
import com.mandrecode.tempo.core.ui.editor.rememberEditorAutosaveController
import com.mandrecode.tempo.core.ui.editor.resolveEditorTitleEdit
import com.mandrecode.tempo.core.ui.theme.LocalIsDarkTheme
import com.mandrecode.tempo.core.ui.theme.TempoIcon
import com.mandrecode.tempo.core.ui.theme.getMaterialYouColors
import com.mandrecode.tempo.core.ui.theme.getMonochromeColor
import com.mandrecode.tempo.core.ui.theme.getPastelColors
import com.mandrecode.tempo.core.ui.theme.resolveColor
import com.mandrecode.tempo.core.ui.theme.resolveColorToKey
import com.mandrecode.tempo.core.ui.util.DebouncedSnapshotEffect
import com.mandrecode.tempo.core.ui.util.DescriptionEditorState
import com.mandrecode.tempo.core.ui.util.selectRandomColor
import com.mandrecode.tempo.features.routines.domain.model.Habit
import com.mandrecode.tempo.features.routines.domain.model.HabitChain
import com.mandrecode.tempo.features.routines.domain.model.HabitType
import com.mandrecode.tempo.features.routines.presentation.RoutinesContract
import com.mandrecode.tempo.features.routines.presentation.RoutinesContract.HabitSheetTab
import com.mandrecode.tempo.infrastructure.permissions.hasNotificationPermissions
import com.mandrecode.tempo.util.findChainForHabit
import kotlinx.datetime.TimeZone
import kotlinx.datetime.format.FormatStringsInDatetimeFormats
import kotlinx.datetime.number
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock

@OptIn(
    ExperimentalMaterial3Api::class,
    FormatStringsInDatetimeFormats::class,
)
@Composable
internal fun HabitBottomSheetContent(
    formState: RoutinesContract.HabitFormState,
    selectedDate: kotlinx.datetime.LocalDate,
    habits: List<Habit>,
    habitChains: List<HabitChain>,
    onSelectTab: (HabitSheetTab) -> Unit,
    onSetHabitType: (HabitType) -> Unit,
    onSetReminder: (Int, Int, Int, Int, Int) -> Unit,
    onClearReminder: () -> Unit,
    onSetColorKey: (String) -> Unit,
    onClearColor: () -> Unit,
    onSetIcon: (String) -> Unit,
    onClearIcon: () -> Unit,
    onDismiss: () -> Unit,
    onClearErrors: () -> Unit,
    onConfirmHabit: (title: String, description: String) -> Unit,
    onConfirmHabitChain: (title: String, description: String, habitIds: List<Long>) -> Unit,
    modifier: Modifier = Modifier,
    vacationPeriods: List<VacationPeriod> = emptyList(),
    isVacationModeActive: Boolean = false,
    onAutoSaveHabit: ((title: String, description: String) -> Unit)? = null,
    onAutoSaveHabitChain: ((title: String, description: String, habitIds: List<Long>) -> Unit)? = null,
    onDeleteHabit: (() -> Unit)? = null,
    onDeleteHabitChain: (() -> Unit)? = null,
    onSetRepeatDays: ((Set<DayOfWeek>?) -> Unit)? = null,
    onToggleHabitCompletion: ((habitId: Long, isCompleted: Boolean) -> Unit)? = null,
    placement: SheetPlacement? = null,
    dismissRequestKey: Int = 0,
) {
    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current
    val editingTargetId =
        when (formState.selectedTab) {
            HabitSheetTab.HABIT -> formState.editingHabit?.id
            HabitSheetTab.HABIT_CHAIN -> formState.editingHabitChain?.id
        }
    val editorKey =
        remember(formState.editorSessionId, formState.selectedTab, editingTargetId) {
            editorSaveableKey(formState.editorSessionId, formState.selectedTab, editingTargetId)
        }

    var title by rememberSaveable(editorKey) {
        mutableStateOf(
            when (formState.selectedTab) {
                HabitSheetTab.HABIT -> formState.editingHabit?.title ?: ""
                HabitSheetTab.HABIT_CHAIN -> formState.editingHabitChain?.title ?: ""
            },
        )
    }
    val initialDescription =
        remember(editorKey) {
            when (formState.selectedTab) {
                HabitSheetTab.HABIT -> formState.editingHabit?.description.orEmpty()
                HabitSheetTab.HABIT_CHAIN -> formState.editingHabitChain?.description.orEmpty()
            }
        }
    var savedDescription by
        rememberSaveable(editorKey, stateSaver = TextFieldValue.Saver) {
            mutableStateOf(TextFieldValue(initialDescription))
        }
    val descriptionState =
        remember(editorKey) {
            DescriptionEditorState(savedDescription)
        }
    val updateDescription: (TextFieldValue) -> Unit = { value ->
        savedDescription = value
        descriptionState.update(value)
    }
    var isDescriptionDirty by
        rememberSaveable(editorKey) {
            mutableStateOf(false)
        }
    var selectedHabitIds by rememberSaveable(
        editorKey,
        stateSaver =
            listSaver(
                save = { it },
                restore = { it },
            ),
    ) {
        mutableStateOf(
            formState.editingHabitChain?.habitIds ?: emptyList(),
        )
    }

    var isTitleError by remember(editorKey) { mutableStateOf(false) }
    var showPermissionCheck by remember { mutableStateOf(false) }
    var autoSelectedColor by remember {
        mutableStateOf(
            if (formState.editingHabit == null && formState.editingHabitChain == null) {
                formState.selectedColorKey
            } else {
                null
            },
        )
    }
    var autoSelectedIcon by remember {
        mutableStateOf(
            if (formState.editingHabit == null && formState.editingHabitChain == null) {
                formState.selectedIcon
            } else {
                null
            },
        )
    }
    val titleFocusRequester = remember { FocusRequester() }
    val currentOnClearReminder by rememberUpdatedState(onClearReminder)
    val currentOnSetColorKey by rememberUpdatedState(onSetColorKey)
    val currentOnSetIcon by rememberUpdatedState(onSetIcon)

    val habitChain =
        remember(formState.editingHabit, habitChains) {
            formState.editingHabit?.let { habit ->
                findChainForHabit(habit, habitChains)
            }
        }

    val isHabitInChain =
        remember(habitChain, formState.targetChainId) {
            habitChain != null || formState.targetChainId != null
        }

    val formattedReminder = rememberFormattedDateTime(formState.reminderDate)
    var showTimePicker by remember { mutableStateOf(false) }

    val onSetReminderClicked: () -> Unit = {
        showPermissionCheck = true
    }

    val now = remember { Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()) }

    if (showTimePicker) {
        TempoTimePickerDialog(
            initialHour = formState.reminderDate?.hour ?: now.hour,
            initialMinute = formState.reminderDate?.minute ?: now.minute,
            onConfirm = { hour, minute ->
                val referenceDate = selectedDate
                onSetReminder(
                    referenceDate.year,
                    referenceDate.month.number,
                    referenceDate.day,
                    hour,
                    minute,
                )
                showTimePicker = false
            },
            onDismiss = { showTimePicker = false },
        )
    }

    if (showPermissionCheck) {
        HandleReminderPermissions(
            show = true,
            onGrantPermissions = {
                showTimePicker = true
                showPermissionCheck = false
            },
            onDismiss = {
                showPermissionCheck = false
            },
        )
    }

    LaunchedEffect(formState.editingHabit, formState.reminderDate) {
        if (formState.editingHabit != null && formState.reminderDate != null) {
            if (!context.hasNotificationPermissions()) {
                currentOnClearReminder()
            }
        }
    }

    val isDarkTheme = LocalIsDarkTheme.current
    val colorScheme = MaterialTheme.colorScheme
    val materialYouColors = getMaterialYouColors(colorScheme)
    val pastelColors = getPastelColors()

    LaunchedEffect(formState.shouldAutoSelectColor) {
        if (formState.shouldAutoSelectColor && formState.selectedColorKey == null) {
            val existingColors =
                when (formState.selectedTab) {
                    HabitSheetTab.HABIT ->
                        habits.mapNotNull {
                            it.colorKey?.let { key ->
                                resolveColor(
                                    key,
                                    colorScheme,
                                    isDarkTheme,
                                )
                            }
                        }

                    HabitSheetTab.HABIT_CHAIN ->
                        habitChains.mapNotNull {
                            it.colorKey?.let { key ->
                                resolveColor(
                                    key,
                                    colorScheme,
                                    isDarkTheme,
                                )
                            }
                        }
                }

            val availableOptions = materialYouColors + getMonochromeColor() + pastelColors

            if (availableOptions.isNotEmpty()) {
                val selectedColor =
                    selectRandomColor(
                        availableOptions = availableOptions,
                        isDarkTheme = isDarkTheme,
                        existingColors = existingColors,
                    )
                resolveColorToKey(selectedColor, colorScheme, isDarkTheme)?.let {
                    autoSelectedColor = it
                    currentOnSetColorKey(it)
                }
            }
        }
    }

    LaunchedEffect(title, formState.shouldAutoSelectIcon) {
        if (formState.shouldAutoSelectIcon && formState.selectedIcon == null && title.isNotBlank()) {
            val suggestedIcon = TempoIcon.suggestIcon(title, context)
            if (suggestedIcon != null) {
                autoSelectedIcon = suggestedIcon.iconName
                currentOnSetIcon(suggestedIcon.iconName)
            }
        }
    }

    LaunchedEffect(Unit) {
        if (formState.editingHabit == null && formState.editingHabitChain == null) {
            titleFocusRequester.requestFocus()
        }
    }

    val editingHabitSnapshot =
        remember(formState.editingHabit) {
            formState.editingHabit?.let {
                HabitFormSnapshot(
                    title = it.title,
                    description = it.description,
                    icon = it.icon,
                    colorKey = it.colorKey,
                    repeatDays = it.repeatDays,
                    reminderDate = it.reminderDate,
                    habitType = it.habitType,
                )
            }
        }
    val editingChainSnapshot =
        remember(formState.editingHabitChain) {
            formState.editingHabitChain?.let {
                ChainFormSnapshot(
                    title = it.title,
                    description = it.description,
                    habitIds = it.habitIds,
                    icon = it.icon,
                    colorKey = it.colorKey,
                    repeatDays = it.repeatDays,
                    periodicReminder = it.periodicReminder,
                )
            }
        }
    val initialSnapshot: Any? =
        when (formState.selectedTab) {
            HabitSheetTab.HABIT -> editingHabitSnapshot
            HabitSheetTab.HABIT_CHAIN -> editingChainSnapshot
        }

    val hasUnsavedChanges =
        remember(
            formState.selectedTab,
            editingHabitSnapshot,
            editingChainSnapshot,
            title,
            isDescriptionDirty,
            selectedHabitIds,
            formState.selectedIcon,
            formState.selectedColorKey,
            formState.selectedRepeatDays,
            formState.reminderDate,
            formState.selectedHabitType,
            autoSelectedColor,
            autoSelectedIcon,
        ) {
            when (formState.selectedTab) {
                HabitSheetTab.HABIT -> {
                    val habit = editingHabitSnapshot
                    if (habit == null) {
                        title.isNotBlank() ||
                            isDescriptionDirty ||
                            formState.selectedIcon != autoSelectedIcon ||
                            formState.selectedColorKey != autoSelectedColor ||
                            formState.selectedRepeatDays != null ||
                            formState.reminderDate != null ||
                            formState.selectedHabitType != HabitType.BUILD
                    } else {
                        title != habit.title ||
                            isDescriptionDirty ||
                            formState.selectedIcon != habit.icon ||
                            formState.selectedColorKey != habit.colorKey ||
                            formState.selectedRepeatDays != habit.repeatDays ||
                            formState.reminderDate != habit.reminderDate ||
                            formState.selectedHabitType != habit.habitType
                    }
                }

                HabitSheetTab.HABIT_CHAIN -> {
                    val chain = editingChainSnapshot
                    if (chain == null) {
                        title.isNotBlank() ||
                            isDescriptionDirty ||
                            selectedHabitIds.isNotEmpty() ||
                            formState.selectedIcon != autoSelectedIcon ||
                            formState.selectedColorKey != autoSelectedColor ||
                            formState.selectedRepeatDays != null ||
                            formState.reminderDate != null
                    } else {
                        title != chain.title ||
                            isDescriptionDirty ||
                            selectedHabitIds != chain.habitIds ||
                            formState.selectedIcon != chain.icon ||
                            formState.selectedColorKey != chain.colorKey ||
                            formState.selectedRepeatDays != chain.repeatDays ||
                            formState.reminderDate != chain.periodicReminder
                    }
                }
            }
        }
    val isEditingHabit =
        formState.selectedTab == HabitSheetTab.HABIT &&
            formState.editingHabit != null
    val isEditingHabitChain =
        formState.selectedTab == HabitSheetTab.HABIT_CHAIN &&
            formState.editingHabitChain != null
    val autoSaveHabitEnabled = isEditingHabit && onAutoSaveHabit != null
    val autoSaveHabitChainEnabled = isEditingHabitChain && onAutoSaveHabitChain != null
    val autoSaveEnabled = autoSaveHabitEnabled || autoSaveHabitChainEnabled

    val autosaveController =
        rememberEditorAutosaveController(initialSnapshot = initialSnapshot, key = editorKey)
    val isSnapshotSaveable: (Any) -> Boolean = { snapshot ->
        when (snapshot) {
            is HabitFormSnapshot -> snapshot.title.isNotBlank()
            is ChainFormSnapshot -> snapshot.title.isNotBlank()
            else -> false
        }
    }
    val dispatchAutosave: (Any) -> Unit = { snapshot ->
        when (snapshot) {
            is HabitFormSnapshot -> onAutoSaveHabit?.invoke(snapshot.title, snapshot.description)
            is ChainFormSnapshot ->
                onAutoSaveHabitChain?.invoke(
                    snapshot.title,
                    snapshot.description,
                    snapshot.habitIds,
                )
        }
    }
    DebouncedSnapshotEffect(
        enabled = autoSaveEnabled,
        key = editorKey,
        debounceMillis = EDITOR_AUTO_SAVE_DEBOUNCE_MS,
        snapshotProvider = {
            when (formState.selectedTab) {
                HabitSheetTab.HABIT ->
                    HabitFormSnapshot(
                        title = title,
                        description = descriptionState.value.text,
                        icon = formState.selectedIcon,
                        colorKey = formState.selectedColorKey,
                        repeatDays = formState.selectedRepeatDays,
                        reminderDate = formState.reminderDate,
                        habitType = formState.selectedHabitType,
                    )

                HabitSheetTab.HABIT_CHAIN ->
                    ChainFormSnapshot(
                        title = title,
                        description = descriptionState.value.text,
                        habitIds = selectedHabitIds,
                        icon = formState.selectedIcon,
                        colorKey = formState.selectedColorKey,
                        repeatDays = formState.selectedRepeatDays,
                        periodicReminder = formState.reminderDate,
                    )
            }
        },
        onSnapshot = { snapshot ->
            autosaveController.trySave(snapshot, isSaveable = isSnapshotSaveable, onSave = dispatchAutosave)
        },
    )

    val onSheetDismissRequest: () -> Unit = {
        val currentSnapshot: Any =
            when (formState.selectedTab) {
                HabitSheetTab.HABIT ->
                    HabitFormSnapshot(
                        title = title,
                        description = descriptionState.value.text,
                        icon = formState.selectedIcon,
                        colorKey = formState.selectedColorKey,
                        repeatDays = formState.selectedRepeatDays,
                        reminderDate = formState.reminderDate,
                        habitType = formState.selectedHabitType,
                    )

                HabitSheetTab.HABIT_CHAIN ->
                    ChainFormSnapshot(
                        title = title,
                        description = descriptionState.value.text,
                        habitIds = selectedHabitIds,
                        icon = formState.selectedIcon,
                        colorKey = formState.selectedColorKey,
                        repeatDays = formState.selectedRepeatDays,
                        periodicReminder = formState.reminderDate,
                    )
            }
        if (autoSaveEnabled && title.isNotBlank()) {
            autosaveController.trySave(currentSnapshot, isSaveable = isSnapshotSaveable, onSave = dispatchAutosave)
        }
        onDismiss()
    }

    com.mandrecode.tempo.core.ui.components.TempoModalBottomSheet(
        onDismissRequest = onSheetDismissRequest,
        modifier = modifier,
        hasUnsavedChanges = if (autoSaveEnabled && canAutosaveEditorTitle(title)) false else hasUnsavedChanges,
        adaptivePlacement = true,
        placement = placement,
        dismissRequestKey = dismissRequestKey,
    ) { onRequestDismiss ->
        val focusManager = LocalFocusManager.current
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = editorSheetHorizontalPadding())
                    .padding(top = 8.dp, bottom = 32.dp),
        ) {
            HabitBottomSheetBody(
                descriptionState = descriptionState,
                state =
                    HabitBottomSheetBodyState(
                        formState = formState,
                        selectedDate = selectedDate,
                        habits = habits,
                        title = title,
                        selectedHabitIds = selectedHabitIds,
                        isTitleError = isTitleError,
                        isHabitInChain = isHabitInChain,
                        formattedReminder = formattedReminder,
                        hasUnsavedChanges = hasUnsavedChanges,
                        autoSaveEnabled = autoSaveEnabled,
                        colorScheme = colorScheme,
                        isDarkTheme = isDarkTheme,
                        vacationPeriods = vacationPeriods,
                        isVacationModeActive = isVacationModeActive,
                    ),
                actions =
                    HabitBottomSheetBodyActions(
                        onSelectTab = onSelectTab,
                        onSetHabitType = onSetHabitType,
                        onTitleChanged = { newValue ->
                            val edited = resolveEditorTitleEdit(newValue, title)
                            if (edited == null) {
                                focusManager.clearFocus()
                            } else {
                                title = edited
                                isTitleError = edited.isBlank()
                                onClearErrors()
                            }
                        },
                        onDescriptionChanged = {
                            updateDescription(it)
                            isDescriptionDirty = descriptionState.value.text != initialDescription
                            onClearErrors()
                        },
                        onSetIcon = onSetIcon,
                        onClearIcon = onClearIcon,
                        onSetColorKey = onSetColorKey,
                        onClearColor = onClearColor,
                        onSetRepeatDays = onSetRepeatDays,
                        onSelectHabits = { selectedHabitIds = it },
                        onSetReminderClicked = onSetReminderClicked,
                        onClearReminder = onClearReminder,
                        onToggleHabitCompletion = onToggleHabitCompletion,
                        onDeleteHabit = onDeleteHabit,
                        onDeleteHabitChain = onDeleteHabitChain,
                        onRequestDismiss = onRequestDismiss,
                        onConfirmClick = {
                            isTitleError = title.isBlank()
                            if (!isTitleError) {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                when (formState.selectedTab) {
                                    HabitSheetTab.HABIT ->
                                        onConfirmHabit(title, descriptionState.value.text)
                                    HabitSheetTab.HABIT_CHAIN ->
                                        onConfirmHabitChain(
                                            title,
                                            descriptionState.value.text,
                                            selectedHabitIds,
                                        )
                                }
                            }
                        },
                    ),
                focusConfig =
                    HabitBottomSheetFocusConfig(
                        titleFocusRequester = titleFocusRequester,
                    ),
            )
        }
    }
}
