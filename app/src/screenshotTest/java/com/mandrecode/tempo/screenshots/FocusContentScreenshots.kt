package com.mandrecode.tempo.screenshots

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.android.tools.screenshot.PreviewTest
import com.mandrecode.tempo.core.domain.model.DailyFocusActivity
import com.mandrecode.tempo.core.domain.model.Priority
import com.mandrecode.tempo.core.ui.adaptive.DockedEditorPane
import com.mandrecode.tempo.core.ui.adaptive.rememberSheetPlacement
import com.mandrecode.tempo.core.ui.glass.ambientBackground
import com.mandrecode.tempo.core.ui.navigation.FloatingRailExpandedContentStartPadding
import com.mandrecode.tempo.core.ui.navigation.adaptiveScreenContentLayout
import com.mandrecode.tempo.features.focus.domain.model.FocusAgendaItem
import com.mandrecode.tempo.features.focus.presentation.FocusContent
import com.mandrecode.tempo.features.focus.presentation.FocusContract
import com.mandrecode.tempo.features.tasks.domain.model.Category
import com.mandrecode.tempo.features.tasks.domain.model.Task
import com.mandrecode.tempo.features.tasks.presentation.TasksContract
import com.mandrecode.tempo.features.tasks.presentation.components.TaskBottomSheet
import kotlinx.collections.immutable.persistentListOf
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.minus

// Fixtures are deliberately duplicated from `src/debug`'s FocusContentPreviews rather than
// shared with it: those previews are a scratchpad developers retune freely, and every tweak
// there would rewrite committed reference images. A screenshot test's input has to be pinned.
private val today = LocalDate(2026, 7, 29)

private fun task(
    id: Long,
    title: String,
    hour: Int?,
    priority: Priority? = null,
    isCompleted: Boolean = false,
    date: LocalDate = today,
) = Task(
    id = id,
    title = title,
    description = "",
    isCompleted = isCompleted,
    priority = priority,
    reminderDate = hour?.let { LocalDateTime(date, LocalTime(it, 0)) },
)

private fun history(vararg pairs: Pair<Int, Int>) =
    pairs
        .mapIndexed { index, (scheduled, completed) ->
            DailyFocusActivity(
                date = today.minus(pairs.size - 1 - index, DateTimeUnit.DAY),
                scheduledCount = scheduled,
                completedCount = completed,
            )
        }.let { persistentListOf(*it.toTypedArray()) }

private val midDayState =
    FocusContract.UiState(
        isLoading = false,
        today = today,
        streakDays = 14,
        history = history(3 to 3, 2 to 1, 0 to 0, 4 to 4, 2 to 2, 3 to 1, 5 to 1),
        scheduledCount = 5,
        completedCount = 1,
        upNext =
            persistentListOf(
                FocusAgendaItem.TaskEntry(
                    task(1, "Finish Q3 budget report", hour = 9, priority = Priority.HIGH),
                ),
                FocusAgendaItem.TaskEntry(
                    task(2, "Reply to the design review", hour = 11, priority = Priority.MEDIUM),
                ),
            ),
        overdue =
            persistentListOf(
                // id 4, not 2: id 2 is already the "Reply to the design review" entry in upNext,
                // and `UiState.entryFor()` resolves a task id against upNext + overdue + todayItems
                // with firstOrNull, so a duplicate makes this row unreachable by id.
                FocusAgendaItem.TaskEntry(
                    task(4, "Renew gym membership", hour = 10, date = today.minus(2, DateTimeUnit.DAY)),
                ),
            ),
        todayItems =
            persistentListOf(
                FocusAgendaItem.TaskEntry(
                    task(1, "Finish Q3 budget report", hour = 9, priority = Priority.HIGH),
                ),
                FocusAgendaItem.TaskEntry(task(2, "Reply to the design review", hour = 11, priority = Priority.MEDIUM)),
                FocusAgendaItem.TaskEntry(task(3, "Reply to client emails", hour = 12)),
                FocusAgendaItem.TaskEntry(task(5, "Send the invoice", hour = 14, isCompleted = true)),
            ),
        undatedTaskCount = 12,
    )

private val emptyDayState =
    FocusContract.UiState(
        isLoading = false,
        today = today,
        streakDays = 0,
        history = history(0 to 0, 0 to 0, 0 to 0, 0 to 0, 0 to 0, 0 to 0, 0 to 0),
        undatedTaskCount = 3,
    )

/**
 * A populated Focus day. Covers the hero, the streak strip, and both agenda sections, and pins
 * the bottom clearance `FocusContent` asks `floatingNavigationBottomClearancePadding()` for —
 * which is itself window-width dependent, so it differs across the three specs.
 */
@PreviewTest
@PreviewAdaptiveFormFactors
@Composable
private fun FocusContentMidDay() {
    ScreenshotTheme {
        // The screen paints the page's ambient backdrop; the content is transparent over it.
        Box(modifier = Modifier.fillMaxSize().ambientBackground()) {
            FocusContent(uiState = midDayState, onEvent = {})
        }
    }
}

/** Nothing scheduled. The empty state centres itself, so it is the width-sensitive one. */
@PreviewTest
@PreviewAdaptiveFormFactors
@Composable
private fun FocusContentEmptyDay() {
    ScreenshotTheme {
        // The screen paints the page's ambient backdrop; the content is transparent over it.
        Box(modifier = Modifier.fillMaxSize().ambientBackground()) {
            FocusContent(uiState = emptyDayState, onEvent = {})
        }
    }
}

private val dockedEditorCategories =
    listOf(
        Category(id = 1L, name = "Inbox", icon = "inbox", isDefault = true, sortOrder = 0),
        Category(id = 2L, name = "Work", color = "color_m3_blue", icon = "work", sortOrder = 1),
    )

private val dockedEditedTask = task(3, "Reply to client emails", hour = 12).copy(categoryId = 2L)

/**
 * Focus with a task open in the docked editor (#502), built like `DockedTasksScreen`: the real
 * agenda behind the expanded rail's clearance, leaning towards the pane, and the real editor in the
 * real [DockedEditorPane], so the seam between the agenda's content block and the pane is pinned.
 */
@PreviewTest
@PreviewDockedWindow
@Composable
private fun DockedFocusScreenEditTask() {
    ScreenshotTheme {
        var contentBlockTop by remember { mutableStateOf(0.dp) }
        Row(modifier = Modifier.fillMaxSize().ambientBackground()) {
            Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                FocusContent(
                    uiState = midDayState,
                    onEvent = {},
                    selectedTaskId = dockedEditedTask.id,
                    onContentBlockTopChange = { contentBlockTop = it },
                    modifier =
                        Modifier.adaptiveScreenContentLayout(
                            railClearance = FloatingRailExpandedContentStartPadding,
                            horizontalBias = 1f,
                        ),
                )
            }
            DockedEditorPane(visible = true, contentBlockTop = contentBlockTop) {
                TaskBottomSheet(
                    categories = dockedEditorCategories,
                    selectedCategoryIdFromFilter = 2L,
                    formState = TasksContract.TaskFormState(isVisible = true),
                    onSetPriority = {},
                    onClearPriority = {},
                    onSetReminder = { _, _, _, _, _ -> },
                    onClearReminder = {},
                    onSetPeriodicity = {},
                    onClearPeriodicity = {},
                    onSetPeriodicityInterval = {},
                    onSetRepeatDays = {},
                    onSetMonthDayOption = {},
                    onDismiss = {},
                    onClearErrors = {},
                    onConfirm = { _, _, _ -> },
                    task = dockedEditedTask,
                    onDelete = {},
                    onToggleCompletion = {},
                    placement = rememberSheetPlacement(),
                )
            }
        }
    }
}
