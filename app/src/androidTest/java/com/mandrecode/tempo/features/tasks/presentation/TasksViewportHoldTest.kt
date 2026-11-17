package com.mandrecode.tempo.features.tasks.presentation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.mandrecode.tempo.core.ui.theme.TempoTheme
import com.mandrecode.tempo.features.tasks.TEST_INBOX_CATEGORY
import com.mandrecode.tempo.features.tasks.domain.model.Task
import com.mandrecode.tempo.features.tasks.presentation.model.ActiveGroupKey
import com.mandrecode.tempo.features.tasks.presentation.model.CompletedGroupKey
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toPersistentList
import org.junit.Rule
import org.junit.Test

/**
 * The wiring, as opposed to the mechanism.
 *
 * `ViewportHoldTest` proves the helper works; this proves the Tasks list is actually holding it —
 * that the toggle goes through the wrapper that records the position, and that nothing has been
 * quietly reconnected to the bare `onEvent` since.
 */
class TasksViewportHoldTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    /** Enough to overflow any test device, so the completed section really is off-screen. */
    private val taskCount = 20

    private fun label(index: Int) = "Task %02d".format(index)

    /**
     * The list, wired to a state that actually moves the task, because a stubbed `onEvent` would
     * leave the list unchanged and there would be nothing for it to chase.
     */
    private fun setTasksList() {
        composeTestRule.setContent {
            var active by remember {
                mutableStateOf((1..taskCount).map { Task(id = it.toLong(), title = label(it), description = "") })
            }
            var completed by remember { mutableStateOf(emptyList<Task>()) }

            TempoTheme {
                TasksContent(
                    uiState =
                        TasksContract.UiState(
                            isLoading = false,
                            tasks = (active + completed).toPersistentList(),
                            categories = persistentListOf(TEST_INBOX_CATEGORY),
                            activeTasks = persistentMapOf(ActiveGroupKey.Flat to active.toPersistentList()),
                            completedTaskGroups =
                                persistentMapOf(CompletedGroupKey.Flat to completed.toPersistentList()),
                            // Expanded, so the moved row is drawn and the list has something it
                            // could follow. Collapsed there would be nothing to test.
                            showCompletedTasks = true,
                        ),
                    onEvent = { event ->
                        if (event is TasksContract.UiEvent.ToggleTaskCompletion) {
                            active = active.filterNot { it.id == event.task.id }
                            completed = completed + event.task.copy(isCompleted = true)
                        }
                    },
                )
            }
        }
        composeTestRule.waitForIdle()
    }

    @Test
    fun checkingTheTopTaskOff_leavesTheListWhereItWas() {
        setTasksList()
        composeTestRule.onNodeWithText(label(1)).assertIsDisplayed()

        // The first checkbox on screen belongs to the first row, which is the one at the top.
        composeTestRule.onAllNodesWithContentDescription("Mark as completed")[0].performClick()
        composeTestRule.waitForIdle()

        // The one under it moved up into its place, rather than the list going to the completed
        // section to watch where the checked one landed.
        composeTestRule.onNodeWithText(label(2)).assertIsDisplayed()
        composeTestRule.onNodeWithText(label(1)).assertIsNotDisplayed()
    }
}
