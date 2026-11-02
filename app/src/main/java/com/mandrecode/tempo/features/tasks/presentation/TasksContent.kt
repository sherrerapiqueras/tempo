package com.mandrecode.tempo.features.tasks.presentation

import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.mandrecode.tempo.R
import com.mandrecode.tempo.core.domain.model.Priority
import com.mandrecode.tempo.core.ui.adaptive.WindowWidthClass
import com.mandrecode.tempo.core.ui.adaptive.currentWindowWidthClass
import com.mandrecode.tempo.core.ui.components.CardGroupPosition
import com.mandrecode.tempo.core.ui.components.ConnectedCardGap
import com.mandrecode.tempo.core.ui.components.FirstLoadCrossfade
import com.mandrecode.tempo.core.ui.components.TempoLoadingIndicator
import com.mandrecode.tempo.core.ui.components.WavyDivider
import com.mandrecode.tempo.core.ui.glass.glassPanel
import com.mandrecode.tempo.core.ui.navigation.floatingNavigationBottomClearancePadding
import com.mandrecode.tempo.core.ui.theme.groupLabel
import com.mandrecode.tempo.core.ui.util.rememberViewportHold
import com.mandrecode.tempo.features.tasks.domain.model.Task
import com.mandrecode.tempo.features.tasks.presentation.components.cards.TaskItem
import com.mandrecode.tempo.features.tasks.presentation.components.sections.CategoryChipRow
import com.mandrecode.tempo.features.tasks.presentation.components.sections.EmptyStateContent
import com.mandrecode.tempo.features.tasks.presentation.components.sections.SectionReorderSheet
import com.mandrecode.tempo.features.tasks.presentation.model.ActiveGroupKey
import com.mandrecode.tempo.features.tasks.presentation.model.CompletedGroupKey
import com.mandrecode.tempo.features.tasks.presentation.model.ReorderableRun
import com.mandrecode.tempo.features.tasks.presentation.model.SortOption
import com.mandrecode.tempo.util.DateTimeFormatter
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toPersistentList
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.todayIn
import kotlin.math.roundToInt
import kotlin.time.Clock

private const val ACTIVE_GROUP_OVERDUE_RANK = 0
private const val ACTIVE_GROUP_DATED_RANK = 1
private const val ACTIVE_GROUP_NO_DATE_RANK = 2
private const val ACTIVE_GROUP_FALLBACK_RANK = 3
private const val COMPLETED_GROUP_DATED_RANK = 0
private const val COMPLETED_GROUP_NO_DATE_RANK = 1
private const val COMPLETED_GROUP_FALLBACK_RANK = 2
private const val DRAGGED_ITEM_ALPHA = 0.8f
private const val TARGET_ITEM_ALPHA = 0.5f
private val ContentBlockTopCornerRadius = 28.dp
private val ContentBlockShape =
    RoundedCornerShape(topStart = ContentBlockTopCornerRadius, topEnd = ContentBlockTopCornerRadius)
internal const val TASKS_CONTENT_BLOCK_TEST_TAG = "tasks_content_block"

// Rough per-card height used to translate a drag distance into a number of slots. The list is
// lazy, so measured heights aren't available for cards that haven't been composed yet.
private val DragItemHeightEstimate = 96.dp

@Composable
fun TasksContent(
    uiState: TasksContract.UiState,
    onEvent: (TasksContract.UiEvent) -> Unit,
    modifier: Modifier = Modifier,
    selectedTaskId: Long? = null,
    onContentBlockTopChange: (Dp) -> Unit = {},
) {
    val density = LocalDensity.current
    val listState = rememberLazyListState()
    val listBottomPadding = floatingNavigationBottomClearancePadding(defaultPadding = 16.dp)

    val activeTaskGroups = uiState.activeTasks
    val completedTaskGroups = uiState.completedTaskGroups
    val subtasksMap = uiState.subtasksMap
    val sectionedList = uiState.sectionedList
    // Naming, renaming and reordering sections (#61) are screen-local and end with the category.
    val sectionEditState = remember(uiState.selectedCategoryId) { SectionEditState() }
    val useStickySectionHeaders = isShortWindow()
    val showSectionShortcuts = currentWindowWidthClass().isAtLeast(WindowWidthClass.LARGE)
    val dragState = remember { TaskDragState() }
    // Tasks move between sections in any sort (#61); only Manual also places them by hand.
    val sectionDragState =
        remember(uiState.selectedCategoryId, uiState.sortOption) {
            SectionDragState(manualOrder = uiState.sortOption == SortOption.MANUAL)
        }

    val hasCompletedTasks by remember(completedTaskGroups) {
        derivedStateOf {
            completedTaskGroups.isNotEmpty()
        }
    }

    val hasActiveTasks by remember(activeTaskGroups) {
        derivedStateOf {
            activeTaskGroups.isNotEmpty()
        }
    }

    // Checking a task off sends it to the completed section, which is below — and the list would
    // otherwise go after it, because the row checked is usually the first one visible. Held here
    // rather than inside the row so that both sections get it from one place, and un-checking, which
    // makes the same trip in reverse, is covered by the same line.
    // Counts, not the sections themselves: the key has to change when a row crosses, and only then.
    val activeTaskCount = activeTaskGroups.values.sumOf { it.size }
    val completedTaskCount = completedTaskGroups.values.sumOf { it.size }
    val holdViewport = rememberViewportHold(listState, activeTaskCount, completedTaskCount)
    val onRowEvent: (TasksContract.UiEvent) -> Unit = { event ->
        if (event is TasksContract.UiEvent.ToggleTaskCompletion) holdViewport()
        onEvent(event)
    }

    Box(
        // Transparent: the page's backdrop (background tone and header gradient) is painted once by
        // the screen around this, and by the preview hosts.
        modifier = modifier.fillMaxSize(),
    ) {
        // Each card's place in its connected run: per section, per header group, or the whole list.
        val groupPositions =
            remember(sectionedList, activeTaskGroups, completedTaskGroups, uiState.sortOption) {
                listCardGroupPositions(
                    sectioned = sectionedList,
                    activeRuns = sortedActiveEntries(activeTaskGroups, uiState.sortOption).map { (_, tasks) -> tasks },
                    separateActiveRuns = uiState.sortOption.hasActiveGroupHeaders,
                    completedRuns =
                        sortedCompletedEntries(completedTaskGroups, uiState.sortOption).map { (_, tasks) -> tasks },
                    separateCompletedRuns = uiState.sortOption.hasCompletedGroupHeaders,
                )
            }
        // A list row's card; [gestures] carries the cross-section drag, otherwise the tied-run reorder applies.
        val taskRow: @Composable LazyItemScope.(Task, Modifier?) -> Unit = { task, gestures ->
            val taskSubtasks = subtasksMap[task.id] ?: persistentListOf()
            TaskListItem(
                task = task,
                subtasks = taskSubtasks,
                isSubtasksExpanded = task.id in uiState.expandedTaskIds || taskSubtasks.isEmpty(),
                isSelected = task.id == selectedTaskId,
                onEvent = onRowEvent,
                groupPosition = groupPositions[task.id] ?: CardGroupPosition.SINGLE,
                modifier =
                    Modifier
                        .animateItem()
                        .then(gestures ?: Modifier)
                        .taskReorderGestures(
                            enabled = gestures == null,
                            reorderableRun = uiState.reorderableRuns[task.id],
                            taskId = task.id,
                            dragState = dragState,
                            onReorder = { from, to, runTasks ->
                                onEvent(TasksContract.UiEvent.ReorderTasks(from, to, runTasks))
                            },
                        ),
            )
        }
        FirstLoadCrossfade(
            isLoading = uiState.isLoading,
            loading = { TempoLoadingIndicator(message = stringResource(R.string.loading_tasks)) },
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
            ) {
                // The header's height is where the rounded content block starts, which a docked editor pane
                // beside this content lines its own surface up with (see DockedEditorPane).
                CategoryChipRow(
                    modifier =
                        Modifier.onSizeChanged {
                            onContentBlockTopChange(with(density) { it.height.toDp() })
                        },
                    categories = uiState.categories,
                    counts = uiState.uncompletedTasksCounts,
                    selectedCategoryId = uiState.selectedCategoryId,
                    onSelectCategory = { onEvent(TasksContract.UiEvent.CategorySelected(it)) },
                    onShowCategoryDialog = { onEvent(TasksContract.UiEvent.ShowCategoryDialog(it)) },
                    onRequestDeleteCategory = {
                        onEvent(
                            TasksContract.UiEvent.RequestDeleteCategory(
                                it,
                            ),
                        )
                    },
                    onReorderCategories = { fromIndex, toIndex, categories ->
                        onEvent(
                            TasksContract.UiEvent.ReorderCategories(fromIndex, toIndex, categories),
                        )
                    },
                    onAddSection = { sectionEditState.newSectionDraft = NewSectionDraft.AtEnd },
                )

                if (showSectionShortcuts && sectionedList != null && sectionedList.sections.isNotEmpty()) {
                    TasksSectionShortcuts(
                        list = sectionedList,
                        category = uiState.categories.firstOrNull { it.id == uiState.selectedCategoryId },
                        listState = listState,
                        collapsedSectionIds = uiState.collapsedSectionIds,
                        editState = sectionEditState,
                    )
                }

                Box(
                    modifier =
                        Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .testTag(TASKS_CONTENT_BLOCK_TEST_TAG)
                            // clip before background: background() alone doesn't clip children,
                            // so list overscroll/ripple effects would otherwise draw past the
                            // rounded top corners instead of respecting the seam.
                            .clip(ContentBlockShape)
                            .glassPanel(ContentBlockShape, MaterialTheme.colorScheme.surface)
                            .onGloballyPositioned { sectionDragState?.setViewport(it.boundsInRoot()) },
                ) {
                    sectionDragState?.let { drag ->
                        SectionDragAutoScroll(drag) { delta -> listState.scrollBy(delta) }
                    }
                    val hasNothingToList = !hasActiveTasks && completedTaskGroups.isEmpty()
                    // An empty category keeps the empty screen even with sections on; one that has
                    // sections lists them, and naming the first section needs the list (#61).
                    val hasSectionUi =
                        !sectionedList?.sections.isNullOrEmpty() || sectionEditState.newSectionDraft != null
                    if (hasNothingToList && !hasSectionUi) {
                        EmptyStateContent()
                    } else {
                        LazyColumn(
                            state = listState,
                            // 16dp at the top, the same as the sides and the same as Routines,
                            // which is the same block with the same 28dp corners. The first card
                            // starts inside that curve, so anything less reads as the card being
                            // shoved against the seam rather than sitting in the panel.
                            contentPadding =
                                PaddingValues(
                                    start = 16.dp,
                                    end = 16.dp,
                                    top = 16.dp,
                                    bottom = listBottomPadding,
                                ),
                            // The seam between the cards of a connected run; headers and other
                            // rows add their own room (GroupSeparation).
                            verticalArrangement = Arrangement.spacedBy(ConnectedCardGap),
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            if (sectionedList != null) {
                                sectionedTaskItems(
                                    list = sectionedList,
                                    collapsedSectionIds = uiState.collapsedSectionIds,
                                    useStickyHeaders = useStickySectionHeaders,
                                    editState = sectionEditState,
                                    onEvent = onEvent,
                                    taskRow = taskRow,
                                    dragState = sectionDragState,
                                )
                            } else {
                                val activeEntries =
                                    sortedActiveEntries(
                                        groups = activeTaskGroups,
                                        sortOption = uiState.sortOption,
                                    )
                                val showActiveHeaders = uiState.sortOption.hasActiveGroupHeaders

                                activeEntries.forEachIndexed { groupIndex, (key, tasksForGroup) ->
                                    if (showActiveHeaders) {
                                        item(key = "active_header_${key.stableKey}") {
                                            ActiveGroupHeader(
                                                label = resolveActiveGroupLabel(key),
                                                isFirst = groupIndex == 0,
                                                modifier =
                                                    Modifier
                                                        .animateItem()
                                                        .groupSeparation(above = groupIndex > 0),
                                            )
                                        }
                                    }

                                    items(
                                        items = tasksForGroup,
                                        key = { task -> task.id },
                                        contentType = { "task" },
                                    ) { task ->
                                        taskRow(task, null)
                                    }
                                }
                            }

                            if (hasCompletedTasks) {
                                val sortOption = uiState.sortOption
                                val showDivider = sortOption.hasCompletedGroupHeaders
                                val completedEntries =
                                    sortedCompletedEntries(
                                        groups = completedTaskGroups,
                                        sortOption = sortOption,
                                    )

                                item(key = "completed_separator") {
                                    // A sectioned list always ends in its section controls (Add
                                    // section, or the field naming a new one), so the separator
                                    // follows them even when no task is active.
                                    val followsContent = hasActiveTasks || sectionedList != null
                                    val firstGroupLabel =
                                        if (showDivider) {
                                            resolveGroupLabel(completedEntries.firstOrNull()?.key)
                                        } else {
                                            null
                                        }
                                    CompletedTasksSeparator(
                                        isExpanded = uiState.showCompletedTasks,
                                        onToggle = {
                                            onEvent(TasksContract.UiEvent.ToggleCompletedTasksVisibility)
                                        },
                                        showDivider = showDivider,
                                        firstGroupLabel = firstGroupLabel,
                                        isFirstVisibleItem = !followsContent,
                                        followsSectionControls = sectionedList != null,
                                        modifier =
                                            Modifier
                                                .animateItem()
                                                .groupSeparation(above = followsContent),
                                    )
                                }

                                if (uiState.showCompletedTasks) {
                                    completedEntries.forEachIndexed { index, (key, tasksForGroup) ->
                                        // Skip first group header — it's shown in the separator
                                        if (index > 0 && showDivider) {
                                            item(key = "group_header_${key.stableKey}") {
                                                CompletedGroupHeader(
                                                    label = resolveGroupLabel(key),
                                                    modifier =
                                                        Modifier
                                                            .animateItem()
                                                            .groupSeparation(above = true),
                                                )
                                            }
                                        }

                                        items(
                                            items = tasksForGroup,
                                            key = { task -> task.id },
                                            contentType = { "task" },
                                        ) { task ->
                                            val taskSubtasks = subtasksMap[task.id] ?: persistentListOf()

                                            TaskListItem(
                                                task = task,
                                                subtasks = taskSubtasks,
                                                isSubtasksExpanded =
                                                    task.id in uiState.expandedTaskIds || taskSubtasks.isEmpty(),
                                                isSelected = task.id == selectedTaskId,
                                                onEvent = onRowEvent,
                                                groupPosition =
                                                    groupPositions[task.id] ?: CardGroupPosition.SINGLE,
                                                modifier = Modifier.animateItem(),
                                            )
                                        }
                                    }

                                    // Below the last completed task rather than in the separator: it
                                    // only shows once the tasks it deletes are on screen.
                                    item(key = "completed_clear") {
                                        ClearCompletedFooter(
                                            onClick = {
                                                onEvent(TasksContract.UiEvent.RequestDeleteCompletedTasks)
                                            },
                                            modifier = Modifier.animateItem().padding(top = GroupSeparation),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        if (sectionEditState.isReorderSheetVisible && sectionedList != null) {
            SectionReorderSheet(
                sections = sectionedList.sections.map { it.section }.toPersistentList(),
                onReorder = { from, to, sections ->
                    onEvent(TasksContract.UiEvent.ReorderSections(from, to, sections))
                },
                onDismiss = { sectionEditState.isReorderSheetVisible = false },
            )
        }
    }
}

@Composable
internal fun TaskListItem(
    task: Task,
    subtasks: ImmutableList<Task>,
    isSubtasksExpanded: Boolean,
    isSelected: Boolean,
    onEvent: (TasksContract.UiEvent) -> Unit,
    modifier: Modifier = Modifier,
    groupPosition: CardGroupPosition = CardGroupPosition.SINGLE,
) {
    val onToggleSubtasksExpansion =
        remember(task.id) {
            { _: Boolean -> onEvent(TasksContract.UiEvent.ToggleTaskExpanded(task.id)) }
        }
    val onAddSubtask =
        remember {
            { parentId: Long ->
                onEvent(TasksContract.UiEvent.ShowTaskDialog(parentTaskId = parentId))
            }
        }

    TaskItem(
        task = task,
        isSelected = isSelected,
        onToggleCompletion = { onEvent(TasksContract.UiEvent.ToggleTaskCompletion(it)) },
        onToggleSubtasksExpansion = onToggleSubtasksExpansion,
        onEdit = { onEvent(TasksContract.UiEvent.ShowTaskDialog(task = it)) },
        onAddSubtask = onAddSubtask,
        onReorderSubtasks = { from, to, subs ->
            onEvent(TasksContract.UiEvent.ReorderSubtasks(from, to, subs))
        },
        subtasks = subtasks,
        isSubtasksExpanded = isSubtasksExpanded,
        groupPosition = groupPosition,
        modifier = modifier,
    )
}

/**
 * Drag state for the task list.
 *
 * The drop target is tracked as (run key, index within run) rather than a list index: once tasks
 * are grouped under headers the same index exists in every group, so a bare index would light up
 * the wrong card.
 */
@Stable
private class TaskDragState {
    var draggedTaskId by mutableStateOf<Long?>(null)
    var draggedRunKey by mutableStateOf<String?>(null)
    var targetIndex by mutableIntStateOf(-1)
    var offsetY by mutableFloatStateOf(0f)

    fun onDragStart(
        run: ReorderableRun,
        taskId: Long,
    ) {
        draggedTaskId = taskId
        draggedRunKey = run.key
        targetIndex = run.indexInRun
        offsetY = 0f
    }

    /** Returns true when the drop target moved, so the caller can fire the tick haptic. */
    fun onDrag(
        run: ReorderableRun,
        dragAmountY: Float,
        itemHeightPx: Float,
    ): Boolean {
        offsetY += dragAmountY
        val newTargetIndex =
            (run.indexInRun + (offsetY / itemHeightPx).roundToInt())
                .coerceIn(0, run.tasks.lastIndex)
        val moved = newTargetIndex != targetIndex
        targetIndex = newTargetIndex
        return moved
    }

    fun reset() {
        draggedTaskId = null
        draggedRunKey = null
        targetIndex = -1
        offsetY = 0f
    }
}

/**
 * Long-press drag-and-drop for a task, confined to [reorderableRun] — the run of tasks the
 * current sort cannot tell apart. A `null` run means the sort distinguishes this task from its
 * neighbours, so its position is not the user's to set and the modifier is a no-op.
 */
@Composable
private fun Modifier.taskReorderGestures(
    reorderableRun: ReorderableRun?,
    taskId: Long,
    dragState: TaskDragState,
    onReorder: (fromIndex: Int, toIndex: Int, tasks: List<Task>) -> Unit,
    enabled: Boolean = true,
): Modifier {
    if (reorderableRun == null || !enabled) return this

    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current
    val isDragging = dragState.draggedTaskId == taskId
    val isTarget =
        !isDragging &&
            dragState.draggedRunKey == reorderableRun.key &&
            dragState.targetIndex == reorderableRun.indexInRun

    return this
        .zIndex(if (isDragging) 1f else 0f)
        .graphicsLayer {
            if (isDragging) {
                translationY = dragState.offsetY
                alpha = DRAGGED_ITEM_ALPHA
            } else if (isTarget) {
                alpha = TARGET_ITEM_ALPHA
            }
        }.pointerInput(reorderableRun) {
            detectDragGesturesAfterLongPress(
                onDragStart = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    dragState.onDragStart(reorderableRun, taskId)
                },
                onDrag = { change, dragAmount ->
                    change.consume()
                    val itemHeight = with(density) { DragItemHeightEstimate.toPx() }
                    if (dragState.onDrag(reorderableRun, dragAmount.y, itemHeight)) {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    }
                },
                onDragEnd = {
                    val target = dragState.targetIndex
                    if (target >= 0 && target != reorderableRun.indexInRun) {
                        onReorder(reorderableRun.indexInRun, target, reorderableRun.tasks)
                    }
                    dragState.reset()
                },
                onDragCancel = { dragState.reset() },
            )
        }
}

@Composable
internal fun ClearCompletedFooter(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
    ) {
        TextButton(
            onClick = onClick,
            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_remove_done),
                contentDescription = null,
                modifier = Modifier.size(ButtonDefaults.IconSize),
            )
            Spacer(modifier = Modifier.size(ButtonDefaults.IconSpacing))
            Text(text = stringResource(R.string.clear_completed))
        }
    }
}

@Composable
internal fun CompletedGroupHeader(
    label: String?,
    modifier: Modifier = Modifier,
) {
    if (label == null) return

    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(top = 12.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        WavyDivider(modifier = Modifier.weight(1f))
        Text(
            text = label,
            style = MaterialTheme.typography.groupLabel,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 12.dp, end = 8.dp),
        )
    }
}

/**
 * Resolves a [CompletedGroupKey] to its user-visible label string.
 */
@Composable
internal fun resolveGroupLabel(key: CompletedGroupKey?): String? {
    val today = Clock.System.todayIn(TimeZone.currentSystemDefault())
    val yesterday = today.minus(1, DateTimeUnit.DAY)

    return when (key) {
        is CompletedGroupKey.ByDate ->
            when (key.date) {
                today -> stringResource(R.string.completed_today)
                yesterday -> stringResource(R.string.completed_yesterday)
                null -> stringResource(R.string.completed_older)
                else -> DateTimeFormatter.formatDate(key.date)
            }

        is CompletedGroupKey.ByPriority ->
            when (key.priority) {
                Priority.HIGH -> stringResource(R.string.priority_high)
                Priority.MEDIUM -> stringResource(R.string.priority_medium)
                Priority.LOW -> stringResource(R.string.priority_low)
                null -> stringResource(R.string.priority_none)
            }

        CompletedGroupKey.ByTitle -> stringResource(R.string.completed_alphabetical)

        CompletedGroupKey.Flat, null -> null
    }
}

private fun sortedActiveEntries(
    groups: Map<ActiveGroupKey, ImmutableList<Task>>,
    sortOption: SortOption,
): List<Map.Entry<ActiveGroupKey, ImmutableList<Task>>> {
    val entries = groups.entries.toList()
    return when (sortOption) {
        SortOption.BY_DATE ->
            entries.sortedWith(
                compareBy<Map.Entry<ActiveGroupKey, ImmutableList<Task>>> { entry ->
                    when (val key = entry.key) {
                        ActiveGroupKey.Overdue -> ACTIVE_GROUP_OVERDUE_RANK
                        is ActiveGroupKey.ByDate ->
                            if (key.date != null) {
                                ACTIVE_GROUP_DATED_RANK
                            } else {
                                ACTIVE_GROUP_NO_DATE_RANK
                            }

                        else -> ACTIVE_GROUP_FALLBACK_RANK
                    }
                }.thenBy { (it.key as? ActiveGroupKey.ByDate)?.date },
            )

        SortOption.BY_PRIORITY ->
            entries.sortedBy { (it.key as? ActiveGroupKey.ByPriority)?.priority?.sortOrder ?: Int.MAX_VALUE }

        else -> entries
    }
}

internal fun sortedCompletedEntries(
    groups: Map<CompletedGroupKey, ImmutableList<Task>>,
    sortOption: SortOption,
): List<Map.Entry<CompletedGroupKey, ImmutableList<Task>>> {
    val entries = groups.entries.toList()
    return when (sortOption) {
        SortOption.BY_DATE ->
            entries.sortedWith(
                compareBy<Map.Entry<CompletedGroupKey, ImmutableList<Task>>> { entry ->
                    when (val key = entry.key) {
                        is CompletedGroupKey.ByDate ->
                            if (key.date == null) {
                                COMPLETED_GROUP_NO_DATE_RANK
                            } else {
                                COMPLETED_GROUP_DATED_RANK
                            }

                        else -> COMPLETED_GROUP_FALLBACK_RANK
                    }
                }.thenByDescending { (it.key as? CompletedGroupKey.ByDate)?.date },
            )

        SortOption.BY_PRIORITY ->
            entries.sortedBy { (it.key as? CompletedGroupKey.ByPriority)?.priority?.sortOrder ?: Int.MAX_VALUE }

        else -> entries
    }
}

/** Stable key string for use in LazyColumn item keys. */
private val CompletedGroupKey.stableKey: String
    get() =
        when (this) {
            is CompletedGroupKey.ByDate -> "date_${date ?: "older"}"
            is CompletedGroupKey.ByPriority -> "priority_${priority?.name ?: "none"}"
            CompletedGroupKey.ByTitle -> "title"
            CompletedGroupKey.Flat -> "flat"
        }

@Composable
internal fun ActiveGroupHeader(
    label: String?,
    isFirst: Boolean,
    modifier: Modifier = Modifier,
) {
    if (label == null) return

    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(top = if (isFirst) 0.dp else 12.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        WavyDivider(modifier = Modifier.weight(1f))
        Text(
            text = label,
            style = MaterialTheme.typography.groupLabel,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 12.dp, end = 8.dp),
        )
    }
}

/**
 * Resolves an [ActiveGroupKey] to its user-visible label string.
 */
@Composable
private fun resolveActiveGroupLabel(key: ActiveGroupKey?): String? {
    val today = Clock.System.todayIn(TimeZone.currentSystemDefault())
    val tomorrow = today.plus(1, DateTimeUnit.DAY)

    return when (key) {
        ActiveGroupKey.Overdue -> stringResource(R.string.active_overdue)

        is ActiveGroupKey.ByDate ->
            when (key.date) {
                null -> stringResource(R.string.active_no_date)
                today -> stringResource(R.string.completed_today)
                tomorrow -> stringResource(R.string.tomorrow)
                else -> DateTimeFormatter.formatDate(key.date)
            }

        is ActiveGroupKey.ByPriority ->
            when (key.priority) {
                Priority.HIGH -> stringResource(R.string.priority_high)
                Priority.MEDIUM -> stringResource(R.string.priority_medium)
                Priority.LOW -> stringResource(R.string.priority_low)
                null -> stringResource(R.string.priority_none)
            }

        ActiveGroupKey.ByTitle, ActiveGroupKey.Flat, null -> null
    }
}

/** Stable key string for use in LazyColumn item keys. */
private val ActiveGroupKey.stableKey: String
    get() =
        when (this) {
            ActiveGroupKey.Overdue -> "active_overdue"
            is ActiveGroupKey.ByDate -> "active_date_${date ?: "no_date"}"
            is ActiveGroupKey.ByPriority -> "active_priority_${priority?.name ?: "none"}"
            ActiveGroupKey.ByTitle -> "active_title"
            ActiveGroupKey.Flat -> "active_flat"
        }
