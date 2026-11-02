package com.mandrecode.tempo.features.routines.presentation.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.mandrecode.tempo.R
import com.mandrecode.tempo.core.ui.editor.EDITOR_CONTROL_HEIGHT
import com.mandrecode.tempo.core.ui.theme.LocalIsDarkTheme
import com.mandrecode.tempo.core.ui.theme.resolveColor
import com.mandrecode.tempo.core.ui.util.rememberPressableChipAnimation
import com.mandrecode.tempo.features.routines.domain.model.Habit
import com.mandrecode.tempo.features.routines.domain.model.HabitType
import com.mandrecode.tempo.util.CompletionHistoryUtil
import com.mandrecode.tempo.util.DateTimeFormatter
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.todayIn
import kotlin.math.roundToInt
import kotlin.time.Clock

// Corner percent for the chain-creation color dot (50 = full circle).
private const val CHAIN_DOT_CORNER_PERCENT = 50

// Fallback height used during drag if a chain habit row hasn't been measured yet.
// Mirrors the SubtaskFallbackItemHeight pattern from TaskCard.kt for a smooth
// drag-and-drop experience.
private val ChainHabitFallbackItemHeight = 68.dp

// Visual feedback alphas while dragging chain habit rows.
private const val DRAG_ITEM_ALPHA = 0.8f
private const val DRAG_TARGET_ALPHA = 0.5f

internal const val AVAILABLE_HABIT_CHIP_TEST_TAG = "available_habit_chip"
internal const val SELECTED_HABIT_ROW_TEST_TAG = "selected_habit_row"

@Composable
fun HabitMultiSelector(
    habits: List<Habit>,
    selectedHabitIds: List<Long>,
    onSelectHabits: (List<Long>) -> Unit,
    modifier: Modifier = Modifier,
    selectedDate: kotlinx.datetime.LocalDate? = null,
    onToggleHabitCompletion: ((habitId: Long, isCompleted: Boolean) -> Unit)? = null,
) {
    // Only build habits can be added to chains
    val buildHabits = habits.filter { it.habitType == HabitType.BUILD }
    val availableHabits = buildHabits.filter { !selectedHabitIds.contains(it.id) }

    Column(
        modifier = modifier.fillMaxWidth(),
    ) {
        // Show selected habits first with drag-to-reorder controls
        if (selectedHabitIds.isNotEmpty()) {
            ChainHabitReorderableColumn(
                habits = habits,
                selectedHabitIds = selectedHabitIds,
                onSelectHabits = onSelectHabits,
                selectedDate = selectedDate,
                onToggleHabitCompletion = onToggleHabitCompletion,
            )
        }

        // Show available habits to select
        if (availableHabits.isNotEmpty()) {
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                contentPadding = PaddingValues(horizontal = 4.dp),
            ) {
                items(availableHabits) { habit ->
                    val chipInteractionSource = remember { MutableInteractionSource() }
                    val chipCornerRadius =
                        rememberPressableChipAnimation(
                            isSelected = false,
                            interactionSource = chipInteractionSource,
                        )

                    val chipShape = RoundedCornerShape(chipCornerRadius.value)
                    FilterChip(
                        modifier = Modifier.testTag(AVAILABLE_HABIT_CHIP_TEST_TAG),
                        selected = false,
                        onClick = {
                            onSelectHabits(selectedHabitIds + habit.id)
                        },
                        interactionSource = chipInteractionSource,
                        label = { Text(habit.title) },
                        colors =
                            FilterChipDefaults.filterChipColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            ),
                        shape = chipShape,
                        border = null,
                    )
                }
            }
        } else if (selectedHabitIds.isEmpty()) {
            // Only show this if both are empty
            NoAvailableHabitsMessage()
        }
    }
}

/**
 * A control's height, so it centres on the row's icon the way the chips it stands in for do, and no
 * inset, so it starts where the rows' content does.
 */
@Composable
private fun NoAvailableHabitsMessage() {
    Box(
        modifier = Modifier.fillMaxWidth().heightIn(min = EDITOR_CONTROL_HEIGHT),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(
            text = stringResource(R.string.no_habits_available),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Renders the list of habits selected for a chain with long-press drag-to-reorder.
 * Mirrors the subtask drag implementation in TaskCard.kt for visual and interaction
 * consistency between subtasks and chain habits (issue #682).
 */
@Composable
private fun ChainHabitReorderableColumn(
    habits: List<Habit>,
    selectedHabitIds: List<Long>,
    onSelectHabits: (List<Long>) -> Unit,
    selectedDate: kotlinx.datetime.LocalDate?,
    onToggleHabitCompletion: ((habitId: Long, isCompleted: Boolean) -> Unit)?,
) {
    val state = rememberChainDragState()

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(start = 4.dp, bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        selectedHabitIds.forEachIndexed { index, habitId ->
            val habit = habits.find { it.id == habitId } ?: return@forEachIndexed
            SelectedHabitItem(
                habit = habit,
                onRemove = { onSelectHabits(selectedHabitIds - habitId) },
                selectedDate = selectedDate,
                onToggleHabitCompletion = onToggleHabitCompletion,
                modifier =
                    Modifier.chainHabitDragModifier(
                        index = index,
                        selectedHabitIds = selectedHabitIds,
                        onSelectHabits = onSelectHabits,
                        state = state,
                    ),
            )
        }
    }
}

/** Mutable state shared across rows during a chain habit drag-to-reorder gesture. */
private class ChainDragState(
    draggedIndex: Int = -1,
    dragOffset: Float = 0f,
    targetIndex: Int = -1,
    measuredItemHeight: Float = 0f,
) {
    var draggedIndex by mutableIntStateOf(draggedIndex)
    var dragOffset by mutableFloatStateOf(dragOffset)
    var targetIndex by mutableIntStateOf(targetIndex)
    var measuredItemHeight by mutableFloatStateOf(measuredItemHeight)

    fun reset() {
        draggedIndex = -1
        dragOffset = 0f
        targetIndex = -1
    }
}

@Composable
private fun rememberChainDragState(): ChainDragState = remember { ChainDragState() }

@Composable
private fun Modifier.chainHabitDragModifier(
    index: Int,
    selectedHabitIds: List<Long>,
    onSelectHabits: (List<Long>) -> Unit,
    state: ChainDragState,
): Modifier {
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current
    val spacingPx = remember(density) { with(density) { 8.dp.toPx() } }
    val isDragging = state.draggedIndex == index
    val isTarget = state.targetIndex == index && state.draggedIndex != index

    return this
        .onSizeChanged { size ->
            if (state.measuredItemHeight == 0f) {
                state.measuredItemHeight = size.height.toFloat()
            }
        }.zIndex(if (isDragging) 1f else 0f)
        .graphicsLayer {
            if (isDragging) {
                translationY = state.dragOffset
                alpha = DRAG_ITEM_ALPHA
            } else if (isTarget) {
                alpha = DRAG_TARGET_ALPHA
            }
        }.pointerInput(selectedHabitIds) {
            detectDragGesturesAfterLongPress(
                onDragStart = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    state.draggedIndex = index
                    state.dragOffset = 0f
                    state.targetIndex = index
                },
                onDrag = { change, dragAmount ->
                    change.consume()
                    state.dragOffset += dragAmount.y
                    val itemHeight =
                        resolveDragItemHeight(state.measuredItemHeight, spacingPx, density)
                    val newTargetIndex =
                        (index + (state.dragOffset / itemHeight).roundToInt())
                            .coerceIn(0, selectedHabitIds.size - 1)
                    if (newTargetIndex != state.targetIndex) {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    }
                    state.targetIndex = newTargetIndex
                },
                onDragEnd = {
                    commitChainReorder(
                        state.draggedIndex,
                        state.targetIndex,
                        selectedHabitIds,
                        onSelectHabits,
                    )
                    state.reset()
                },
                onDragCancel = { state.reset() },
            )
        }
}

private fun resolveDragItemHeight(
    measuredItemHeight: Float,
    spacingPx: Float,
    density: androidx.compose.ui.unit.Density,
): Float =
    if (measuredItemHeight > 0f) {
        measuredItemHeight + spacingPx
    } else {
        with(density) { ChainHabitFallbackItemHeight.toPx() }
    }

private fun commitChainReorder(
    draggedIndex: Int,
    targetIndex: Int,
    selectedHabitIds: List<Long>,
    onSelectHabits: (List<Long>) -> Unit,
) {
    if (selectedHabitIds.isEmpty() || draggedIndex == targetIndex) return
    if (draggedIndex !in selectedHabitIds.indices || targetIndex !in selectedHabitIds.indices) return

    val reordered = selectedHabitIds.toMutableList()
    val moved = reordered.removeAt(draggedIndex)
    reordered.add(targetIndex, moved)
    onSelectHabits(reordered)
}

@Composable
private fun ChainHabitLeadingIndicator(
    showCheckbox: Boolean,
    isCompleted: Boolean,
    canToggle: Boolean,
    checkboxRadius: Dp,
    accentColor: Color,
    iconName: String?,
    onToggle: () -> Unit,
) {
    if (showCheckbox) {
        // 36dp custom checkbox matching SubtaskItem exactly. Uses the resolved
        // habit color so each habit keeps its identity while the visual weight
        // matches subtasks.
        ChainHabitCheckbox(
            isCompleted = isCompleted,
            canToggle = canToggle,
            radius = checkboxRadius,
            accentColor = accentColor,
            iconName = iconName,
            onToggle = onToggle,
        )
    } else {
        // Chain creation flow (no selectedDate / no toggle callback): render a
        // small color dot so each habit retains its color identity without
        // showing a non-functional checkbox placeholder.
        Box(
            modifier =
                Modifier
                    .size(12.dp)
                    .clip(RoundedCornerShape(CHAIN_DOT_CORNER_PERCENT))
                    .background(accentColor),
        )
    }
}

@Composable
private fun SelectedHabitItem(
    habit: Habit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
    selectedDate: kotlinx.datetime.LocalDate? = null,
    onToggleHabitCompletion: ((habitId: Long, isCompleted: Boolean) -> Unit)? = null,
) {
    val isDarkTheme = LocalIsDarkTheme.current
    val colorScheme = MaterialTheme.colorScheme

    // Remember the resolved color to avoid recalculating on every recomposition
    val habitColor =
        remember(habit.colorKey, colorScheme, isDarkTheme) {
            resolveColor(habit.colorKey, colorScheme, isDarkTheme)
        }

    val showCheckbox = selectedDate != null && onToggleHabitCompletion != null
    val isCompleted =
        if (showCheckbox) {
            val dateStr = selectedDate.toString()
            remember(habit.completionHistory, dateStr) {
                CompletionHistoryUtil.isDateInHistory(habit.completionHistory, dateStr)
            }
        } else {
            false
        }
    val canToggle =
        if (showCheckbox) {
            val today = Clock.System.todayIn(TimeZone.currentSystemDefault())
            val yesterday = today.minus(DatePeriod(days = 1))
            selectedDate == today || selectedDate == yesterday
        } else {
            false
        }

    val rowBackground by animateColorAsState(
        targetValue =
            if (isCompleted) {
                colorScheme.onSurface.copy(alpha = 0.02f)
            } else {
                colorScheme.onSurface.copy(alpha = 0.05f)
            },
        animationSpec = tween(durationMillis = 300),
        label = "chain_habit_row_bg",
    )

    val checkboxRadius by animateDpAsState(
        targetValue = if (isCompleted) 12.dp else 18.dp,
        animationSpec =
            spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = Spring.StiffnessMedium,
            ),
        label = "chain_habit_checkbox_radius",
    )

    Row(
        modifier =
            modifier
                .testTag(SELECTED_HABIT_ROW_TEST_TAG)
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(rowBackground)
                .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_drag_handle),
            contentDescription = stringResource(R.string.reorder_habit),
            modifier = Modifier.size(20.dp),
            tint = colorScheme.onSurface.copy(alpha = 0.5f),
        )

        Spacer(modifier = Modifier.width(8.dp))

        ChainHabitLeadingIndicator(
            showCheckbox = showCheckbox,
            isCompleted = isCompleted,
            canToggle = canToggle,
            checkboxRadius = checkboxRadius,
            accentColor = habitColor ?: colorScheme.primary,
            iconName = habit.icon,
            onToggle = {
                if (canToggle && onToggleHabitCompletion != null) {
                    onToggleHabitCompletion(habit.id, !isCompleted)
                }
            },
        )

        Spacer(modifier = Modifier.width(16.dp))

        Text(
            text = habit.title,
            style =
                MaterialTheme.typography.bodyMedium.copy(
                    textDecoration = if (isCompleted) TextDecoration.LineThrough else null,
                ),
            color =
                if (isCompleted) {
                    colorScheme.onSurface.copy(alpha = 0.5f)
                } else {
                    colorScheme.onSurface.copy(alpha = 0.9f)
                },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )

        Spacer(modifier = Modifier.width(8.dp))

        Box(
            modifier =
                Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .clickable(
                        onClick = onRemove,
                        role = Role.Button,
                    ),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier.size(36.dp),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = stringResource(R.string.remove_habit),
                    tint = colorScheme.onSurface.copy(alpha = 0.5f),
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

@Composable
internal fun rememberFormattedDateTime(dateTime: LocalDateTime?): String? {
    val context = LocalContext.current
    return remember(dateTime) {
        dateTime?.let {
            DateTimeFormatter.format(
                it,
                DateTimeFormatter.Format.Full,
                context,
            )
        }
    }
}

/**
 * Chain-level counterpart to the single-habit title checkbox (issue #45): shows the chain's
 * icon/color and toggles completion for every member habit at once, so a chain can be
 * completed or undone from the bottom sheet the same way a single habit can.
 */
@Composable
internal fun HabitChainTitleCheckboxRow(
    state: HabitBottomSheetBodyState,
    actions: HabitBottomSheetBodyActions,
    focusConfig: HabitBottomSheetFocusConfig,
    editingHabitChain: com.mandrecode.tempo.features.routines.domain.model.HabitChain,
    onToggleHabitCompletion: (habitId: Long, isCompleted: Boolean) -> Unit,
) {
    val dateStr = remember(state.selectedDate) { state.selectedDate.toString() }
    val chainHabits =
        remember(state.habits, editingHabitChain.habitIds) {
            val habitsById = state.habits.associateBy { it.id }
            editingHabitChain.habitIds.mapNotNull { id -> habitsById[id] }
        }
    val isCompleted =
        remember(chainHabits, dateStr) {
            chainHabits.isNotEmpty() &&
                chainHabits.all { CompletionHistoryUtil.isDateInHistory(it.completionHistory, dateStr) }
        }
    val today = Clock.System.todayIn(TimeZone.currentSystemDefault())
    val yesterday = today.minus(DatePeriod(days = 1))

    HabitCheckboxTitleRow(
        state = state,
        actions = actions,
        focusConfig = focusConfig,
        isCompleted = isCompleted,
        canToggle = chainHabits.isNotEmpty() && (state.selectedDate == today || state.selectedDate == yesterday),
        iconName = state.formState.selectedIcon ?: editingHabitChain.icon,
        colorKey = state.formState.selectedColorKey ?: editingHabitChain.colorKey,
        onToggle = {
            val target = !isCompleted
            chainHabits.forEach { habit ->
                val habitIsCompleted = CompletionHistoryUtil.isDateInHistory(habit.completionHistory, dateStr)
                if (habitIsCompleted != target) {
                    onToggleHabitCompletion(habit.id, target)
                }
            }
        },
    )
}
