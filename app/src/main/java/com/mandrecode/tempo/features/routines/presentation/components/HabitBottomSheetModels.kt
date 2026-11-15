package com.mandrecode.tempo.features.routines.presentation.components

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.text.input.TextFieldValue
import com.mandrecode.tempo.core.domain.model.DayOfWeek
import com.mandrecode.tempo.core.domain.model.VacationPeriod
import com.mandrecode.tempo.features.routines.domain.model.Habit
import com.mandrecode.tempo.features.routines.domain.model.HabitChain
import com.mandrecode.tempo.features.routines.domain.model.HabitType
import com.mandrecode.tempo.features.routines.presentation.RoutinesContract
import com.mandrecode.tempo.features.routines.presentation.RoutinesContract.HabitSheetTab
import kotlinx.datetime.LocalDateTime

internal const val HABIT_BOTTOM_SHEET_DESCRIPTION_FIELD_TEST_TAG =
    "habit_bottom_sheet_description_field"

/**
 * Subset of [Habit] fields the sheet treats as form input. Excluding fields like
 * `completionHistory`, `id`, `createdDate` and `isCompleted` means refreshes
 * triggered by toggling a habit from inside the sheet do not invalidate the
 * sheet's `hasUnsavedChanges` calculation — mirroring how `TaskBottomSheet`
 * stays independent of completion changes.
 */
internal data class HabitFormSnapshot(
    val title: String,
    val description: String,
    val icon: String?,
    val colorKey: String?,
    val repeatDays: Set<DayOfWeek>?,
    val reminderDate: LocalDateTime?,
    val habitType: HabitType,
)

/**
 * Subset of [HabitChain] fields the sheet treats as form input. See
 * [HabitFormSnapshot] for the rationale.
 */
internal data class ChainFormSnapshot(
    val title: String,
    val description: String,
    val habitIds: List<Long>,
    val icon: String?,
    val colorKey: String?,
    val repeatDays: Set<DayOfWeek>?,
    val periodicReminder: LocalDateTime?,
)

internal data class HabitBottomSheetBodyState(
    val formState: RoutinesContract.HabitFormState,
    val selectedDate: kotlinx.datetime.LocalDate,
    val habits: List<Habit>,
    val title: String,
    val selectedHabitIds: List<Long>,
    val isTitleError: Boolean,
    val isHabitInChain: Boolean,
    val formattedReminder: String?,
    val colorScheme: ColorScheme,
    val isDarkTheme: Boolean,
    val vacationPeriods: List<VacationPeriod> = emptyList(),
    val isVacationModeActive: Boolean = false,
)

internal data class HabitBottomSheetBodyActions(
    val onSelectTab: (HabitSheetTab) -> Unit,
    val onSetHabitType: (HabitType) -> Unit,
    val onTitleChanged: (String) -> Unit,
    val onDescriptionChanged: (TextFieldValue) -> Unit,
    val onSetIcon: (String) -> Unit,
    val onClearIcon: () -> Unit,
    val onSetColorKey: (String) -> Unit,
    val onClearColor: () -> Unit,
    val onSetRepeatDays: ((Set<DayOfWeek>?) -> Unit)?,
    val onSelectHabits: (List<Long>) -> Unit,
    val onSetReminderClicked: () -> Unit,
    val onClearReminder: () -> Unit,
    val onToggleHabitCompletion: ((habitId: Long, isCompleted: Boolean) -> Unit)?,
)

internal data class HabitBottomSheetFocusConfig(
    val titleFocusRequester: FocusRequester,
)
