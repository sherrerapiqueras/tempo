package com.mandrecode.tempo.screenshots

import androidx.compose.runtime.Composable
import com.android.tools.screenshot.PreviewTest
import com.mandrecode.tempo.features.tasks.domain.model.Category
import com.mandrecode.tempo.features.tasks.domain.model.Task
import com.mandrecode.tempo.features.tasks.domain.model.TaskSection
import com.mandrecode.tempo.features.tasks.presentation.TasksContent
import com.mandrecode.tempo.features.tasks.presentation.TasksContract
import com.mandrecode.tempo.features.tasks.presentation.model.ActiveGroupKey
import com.mandrecode.tempo.features.tasks.presentation.model.SectionUi
import com.mandrecode.tempo.features.tasks.presentation.model.SectionedTaskList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.persistentSetOf

// Pinned here rather than shared with the debug previews, for the reason FocusContentScreenshots
// gives: a screenshot test's input must not move when someone retunes a scratchpad preview.
private val sv = Category(id = 3L, name = "S&V", color = "color_m3_green", icon = "home", isDefault = true)
private val cat = TaskSection(id = 10L, categoryId = 3L, name = "Cat", sortOrder = 0)
private val chores = TaskSection(id = 11L, categoryId = 3L, name = "Home chores", sortOrder = 1)
private val shopping = TaskSection(id = 12L, categoryId = 3L, name = "Shopping", sortOrder = 2)
private val garden = TaskSection(id = 13L, categoryId = 3L, name = "Garden", sortOrder = 3)

private fun task(
    id: Long,
    title: String,
    sectionId: Long?,
) = Task(id = id, title = title, description = "", categoryId = sv.id, sectionId = sectionId)

private val unsectioned = task(1L, "Pay the internet bill", null)

private val sectionedState =
    TasksContract.UiState(
        isLoading = false,
        categories = persistentListOf(sv),
        selectedCategoryId = sv.id,
        activeTasks = persistentMapOf(ActiveGroupKey.Flat to persistentListOf(unsectioned)),
        sectionedList =
            SectionedTaskList(
                unsectioned = persistentListOf(unsectioned),
                sections =
                    persistentListOf(
                        SectionUi(
                            cat,
                            persistentListOf(task(2L, "Buy litter", cat.id), task(3L, "Book vet check-up", cat.id)),
                            overdueCount = 0,
                        ),
                        SectionUi(chores, persistentListOf(task(4L, "Run the dishwasher", chores.id)), overdueCount = 1),
                        SectionUi(
                            shopping,
                            persistentListOf(task(5L, "Oat milk", shopping.id), task(6L, "Cat food", shopping.id)),
                            overdueCount = 0,
                        ),
                        SectionUi(garden, persistentListOf(), overdueCount = 0),
                    ),
            ),
        collapsedSectionIds = persistentSetOf(chores.id),
    )

/**
 * A category with sections (#61) at the three adaptive widths: one list at each, and on a tablet the
 * section shortcuts under the categories.
 */
@PreviewTest
@PreviewAdaptiveFormFactors
@Composable
private fun TasksContentSections() {
    ScreenshotTheme {
        TasksContent(uiState = sectionedState, onEvent = {})
    }
}
