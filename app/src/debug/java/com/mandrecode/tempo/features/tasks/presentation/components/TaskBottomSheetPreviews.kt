package com.mandrecode.tempo.features.tasks.presentation.components

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.mandrecode.tempo.core.domain.model.DayOfWeek
import com.mandrecode.tempo.core.domain.model.MonthDayOption
import com.mandrecode.tempo.core.domain.model.Periodicity
import com.mandrecode.tempo.core.domain.model.Priority
import com.mandrecode.tempo.core.ui.theme.TempoTheme
import com.mandrecode.tempo.features.tasks.domain.model.Category
import com.mandrecode.tempo.features.tasks.domain.model.Task
import com.mandrecode.tempo.features.tasks.presentation.TasksContract
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime

// Colors are `ColorOption.labelKey` values and icons are `TempoIcon.iconName` values, because
// that is what `resolveColor()` and `TempoIcon.fromName()` match on. Drawable-style names
// ("ic_work", "material_blue") resolve to nothing and fall back silently to the default glyph
// and no color, so the preview stops showing what the screen actually renders.
private val previewCategories =
    listOf(
        Category(id = 1L, name = "Inbox", icon = "inbox", isDefault = true, sortOrder = 0),
        Category(id = 2L, name = "Work", color = "color_m3_blue", icon = "work", sortOrder = 1),
        Category(id = 3L, name = "Personal", color = "color_m3_green", icon = "home", sortOrder = 2),
    )

private val previewReminderDate = LocalDateTime(2026, 4, 20, 9, 0)

@Composable
private fun PreviewSheet(
    formState: TasksContract.TaskFormState,
    selectedCategoryIdFromFilter: Long = 1L,
    task: Task? = null,
) {
    TempoTheme {
        TaskBottomSheet(
            categories = previewCategories,
            selectedCategoryIdFromFilter = selectedCategoryIdFromFilter,
            formState = formState,
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
            task = task,
            onDelete = if (task != null) ({}) else null,
            onToggleCompletion = if (task != null) ({}) else null,
        )
    }
}

// region TaskBottomSheet – New Task Previews

@Preview(name = "Light", showBackground = true, device = "id:pixel_9")
@Preview(name = "Dark", showBackground = true, device = "id:pixel_9", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun TaskBottomSheetNewTaskPreview() {
    PreviewSheet(formState = TasksContract.TaskFormState(isVisible = true))
}

// endregion

// region TaskBottomSheet – Edit Task Previews

@Preview(name = "Light", showBackground = true, device = "id:pixel_9")
@Preview(name = "Dark", showBackground = true, device = "id:pixel_9", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun TaskBottomSheetEditTaskPreview() {
    val task =
        Task(
            id = 1L,
            title = "Review pull request",
            description = "Check the new category feature implementation",
            categoryId = 2L,
            priority = Priority.HIGH,
            isCompleted = false,
            sortOrder = 1,
        )
    PreviewSheet(
        formState = TasksContract.TaskFormState(isVisible = true),
        selectedCategoryIdFromFilter = 2L,
        task = task,
    )
}

@Preview(name = "Light", showBackground = true, device = "id:pixel_9")
@Preview(name = "Dark", showBackground = true, device = "id:pixel_9", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun TaskBottomSheetEditWithErrorPreview() {
    PreviewSheet(
        formState =
            TasksContract.TaskFormState(
                isVisible = true,
                titleError = com.mandrecode.tempo.R.string.error_task_title_too_long,
            ),
    )
}

// Regression preview for #683: long title on a completed task. The completedAt
// badge is intentionally not rendered inside the sheet (it lives in the task
// list view), so the title field can use the full available width and never
// needs to scroll.
@Preview(name = "Light", showBackground = true, device = "id:pixel_9")
@Preview(name = "Dark", showBackground = true, device = "id:pixel_9", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun TaskBottomSheetCompletedLongTitlePreview() {
    val task =
        Task(
            id = 2L,
            title = "Finish writing the migration guide for Jetpack Navigation 3 across the app",
            description = "Long-title regression preview for #683",
            categoryId = 2L,
            priority = Priority.MEDIUM,
            isCompleted = true,
            completedAt = LocalDateTime(2026, 4, 25, 16, 30),
            sortOrder = 1,
        )
    PreviewSheet(
        formState = TasksContract.TaskFormState(isVisible = true),
        selectedCategoryIdFromFilter = 2L,
        task = task,
    )
}

// endregion

// region TaskBottomSheet – Periodicity Previews

@Preview(name = "Light", showBackground = true, device = "id:pixel_9")
@Preview(name = "Dark", showBackground = true, device = "id:pixel_9", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PeriodicityEndDateRowAddPreview() {
    TempoTheme {
        Surface {
            PeriodicityEndDateRow(endDate = null, reminderDate = null, enabled = true, onEndDateChange = {})
        }
    }
}

@Preview(name = "Light", showBackground = true, device = "id:pixel_9")
@Preview(name = "Dark", showBackground = true, device = "id:pixel_9", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PeriodicityEndDateRowSetPreview() {
    TempoTheme {
        Surface {
            PeriodicityEndDateRow(
                endDate = LocalDate(2027, 3, 31),
                reminderDate = null,
                enabled = true,
                onEndDateChange = {},
            )
        }
    }
}

@Preview(name = "Light", showBackground = true, device = "id:pixel_9")
@Preview(name = "Dark", showBackground = true, device = "id:pixel_9", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun TaskBottomSheetHourlyPreview() {
    PreviewSheet(
        formState =
            TasksContract.TaskFormState(
                isVisible = true,
                reminderDate = previewReminderDate,
                periodicity = Periodicity.HOURLY,
                periodicityInterval = 3,
            ),
    )
}

@Preview(name = "Light", showBackground = true, device = "id:pixel_9")
@Preview(name = "Dark", showBackground = true, device = "id:pixel_9", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun TaskBottomSheetHourlyAtMaxPreview() {
    PreviewSheet(
        formState =
            TasksContract.TaskFormState(
                isVisible = true,
                reminderDate = previewReminderDate,
                periodicity = Periodicity.HOURLY,
                periodicityInterval = Periodicity.MAX_HOURLY_INTERVAL,
            ),
    )
}

@Preview(name = "Light", showBackground = true, device = "id:pixel_9")
@Preview(name = "Dark", showBackground = true, device = "id:pixel_9", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun TaskBottomSheetWeeklyPreview() {
    PreviewSheet(
        formState =
            TasksContract.TaskFormState(
                isVisible = true,
                reminderDate = previewReminderDate,
                periodicity = Periodicity.WEEKLY,
                periodicityInterval = 1,
                repeatDays =
                    persistentSetOf(
                        DayOfWeek.MONDAY,
                        DayOfWeek.WEDNESDAY,
                        DayOfWeek.FRIDAY,
                    ),
            ),
    )
}

@Preview(name = "Light", showBackground = true, device = "id:pixel_9")
@Preview(name = "Dark", showBackground = true, device = "id:pixel_9", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun TaskBottomSheetMonthlyPreview() {
    PreviewSheet(
        formState =
            TasksContract.TaskFormState(
                isVisible = true,
                reminderDate = previewReminderDate,
                periodicity = Periodicity.MONTHLY,
                periodicityInterval = 1,
                monthDayOption = MonthDayOption.LAST_DAY,
            ),
    )
}

// endregion

// region PeriodicityIntervalSelector – Standalone Preview

@Preview(name = "Light", showBackground = true)
@Preview(name = "Dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PeriodicityIntervalSelectorPreview() {
    TempoTheme {
        Surface {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(16.dp),
            ) {
                PeriodicityIntervalSelector(
                    interval = 1,
                    onIntervalChange = {},
                )
                PeriodicityIntervalSelector(
                    interval = 3,
                    onIntervalChange = {},
                    maxInterval = Periodicity.MAX_HOURLY_INTERVAL,
                )
                PeriodicityIntervalSelector(
                    interval = Periodicity.MAX_HOURLY_INTERVAL,
                    onIntervalChange = {},
                    maxInterval = Periodicity.MAX_HOURLY_INTERVAL,
                )
            }
        }
    }
}

// endregion
