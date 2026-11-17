package com.mandrecode.tempo.features.tasks.presentation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import com.google.common.truth.Truth.assertThat
import com.mandrecode.tempo.core.domain.model.Priority
import com.mandrecode.tempo.core.ui.components.LOADING_INDICATOR_REVEAL_DELAY_MILLIS
import com.mandrecode.tempo.core.ui.theme.TempoTheme
import com.mandrecode.tempo.features.tasks.TEST_INBOX_CATEGORY
import com.mandrecode.tempo.features.tasks.domain.model.Category
import com.mandrecode.tempo.features.tasks.domain.model.Task
import com.mandrecode.tempo.features.tasks.presentation.model.ActiveGroupKey
import com.mandrecode.tempo.features.tasks.presentation.model.CompletedGroupKey
import com.mandrecode.tempo.features.tasks.presentation.model.ReorderableRun
import com.mandrecode.tempo.features.tasks.presentation.model.SortOption
import com.mandrecode.tempo.features.tasks.presentation.model.buildReorderableRuns
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toPersistentList
import kotlinx.datetime.LocalDateTime
import org.junit.Rule
import org.junit.Test

class TasksContentTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private val testCategory = Category(id = 1, name = "Work")

    @Test
    fun showsLoadingIndicator_whenLoading() {
        composeTestRule.setContent {
            TempoTheme {
                TasksContent(
                    uiState = TasksContract.UiState(isLoading = true),
                    onEvent = {},
                )
            }
        }

        // The indicator holds back for a moment so a quick load does not flash it (#438).
        composeTestRule.mainClock.advanceTimeBy(LOADING_INDICATOR_REVEAL_DELAY_MILLIS + 500)
        composeTestRule.onNodeWithContentDescription("Loading tasks", substring = true).assertIsDisplayed()
    }

    @Test
    fun showsEmptyState_whenNoTasks() {
        composeTestRule.setContent {
            TempoTheme {
                TasksContent(
                    uiState =
                        TasksContract.UiState(
                            isLoading = false,
                            tasks = persistentListOf(),
                            categories = persistentListOf(TEST_INBOX_CATEGORY),
                        ),
                    onEvent = {},
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Nothing here", substring = true).assertIsDisplayed()
    }

    @Test
    fun showsTaskTitle_whenTasksExist() {
        val task =
            Task(
                id = 1,
                title = "Buy groceries",
                description = "",
                categoryId = TEST_INBOX_CATEGORY.id,
                sortOrder = 0,
            )

        composeTestRule.setContent {
            TempoTheme {
                TasksContent(
                    uiState =
                        TasksContract.UiState(
                            isLoading = false,
                            tasks = persistentListOf(task),
                            activeTasks =
                                persistentMapOf(
                                    ActiveGroupKey.Flat to persistentListOf(task),
                                ),
                            categories = persistentListOf(TEST_INBOX_CATEGORY),
                        ),
                    onEvent = {},
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Buy groceries").assertIsDisplayed()
    }

    @Test
    fun showsCategoryChips() {
        composeTestRule.setContent {
            TempoTheme {
                TasksContent(
                    uiState =
                        TasksContract.UiState(
                            isLoading = false,
                            tasks = persistentListOf(),
                            categories = persistentListOf(TEST_INBOX_CATEGORY, testCategory),
                        ),
                    onEvent = {},
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Inbox").assertIsDisplayed()
        composeTestRule.onNodeWithText("Work").assertIsDisplayed()
    }

    @Test
    fun showsCompletedSection_whenCompletedTasksExist() {
        val completedTask =
            Task(
                id = 1,
                title = "Done task",
                description = "",
                isCompleted = true,
                categoryId = TEST_INBOX_CATEGORY.id,
                sortOrder = 0,
            )

        composeTestRule.setContent {
            TempoTheme {
                TasksContent(
                    uiState =
                        TasksContract.UiState(
                            isLoading = false,
                            tasks = persistentListOf(completedTask),
                            completedTaskGroups =
                                persistentMapOf(
                                    CompletedGroupKey.Flat to persistentListOf(completedTask),
                                ),
                            categories = persistentListOf(TEST_INBOX_CATEGORY),
                            showCompletedTasks = true,
                        ),
                    onEvent = {},
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Completed", substring = true).assertIsDisplayed()
        composeTestRule.onNodeWithText("Done task").assertIsDisplayed()
    }

    @Test
    fun clearCompleted_showsOnlyWhenExpanded_andRequestsDeletion() {
        val completedTask =
            Task(
                id = 1,
                title = "Done task",
                description = "",
                isCompleted = true,
                categoryId = TEST_INBOX_CATEGORY.id,
                sortOrder = 0,
            )
        val events = mutableListOf<TasksContract.UiEvent>()
        var showCompleted by mutableStateOf(false)

        composeTestRule.setContent {
            TempoTheme {
                TasksContent(
                    uiState =
                        TasksContract.UiState(
                            isLoading = false,
                            tasks = persistentListOf(completedTask),
                            completedTaskGroups =
                                persistentMapOf(
                                    CompletedGroupKey.Flat to persistentListOf(completedTask),
                                ),
                            categories = persistentListOf(TEST_INBOX_CATEGORY),
                            showCompletedTasks = showCompleted,
                        ),
                    onEvent = { events += it },
                )
            }
        }

        composeTestRule.onNodeWithText("Clear completed").assertDoesNotExist()

        showCompleted = true
        composeTestRule.waitForIdle()
        val taskBottom =
            composeTestRule
                .onNodeWithText("Done task")
                .fetchSemanticsNode()
                .boundsInRoot.bottom
        val clear = composeTestRule.onNodeWithText("Clear completed")
        clear.assertIsDisplayed()
        // Below the last completed task, not up in the separator.
        assertThat(clear.fetchSemanticsNode().boundsInRoot.top).isGreaterThan(taskBottom)
        clear.performClick()

        assertThat(events).containsExactly(TasksContract.UiEvent.RequestDeleteCompletedTasks)
    }

    @Test
    fun completedSection_manualMode_showsNoDividerOrLabel() {
        val completedTask =
            Task(
                id = 1,
                title = "Done task",
                description = "",
                isCompleted = true,
                categoryId = TEST_INBOX_CATEGORY.id,
                sortOrder = 0,
            )

        composeTestRule.setContent {
            TempoTheme {
                TasksContent(
                    uiState =
                        TasksContract.UiState(
                            isLoading = false,
                            tasks = persistentListOf(completedTask),
                            completedTaskGroups =
                                persistentMapOf(
                                    CompletedGroupKey.Flat to persistentListOf(completedTask),
                                ),
                            categories = persistentListOf(TEST_INBOX_CATEGORY),
                            showCompletedTasks = true,
                            sortOption = SortOption.MANUAL,
                        ),
                    onEvent = {},
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Completed", substring = true).assertIsDisplayed()
        composeTestRule.onNodeWithText("Done task").assertIsDisplayed()
        // No group labels should appear in manual mode
        composeTestRule.onNodeWithText("High").assertDoesNotExist()
        composeTestRule.onNodeWithText("A → Z").assertDoesNotExist()
    }

    @Test
    fun completedSection_priorityMode_showsDividerWithPriorityLabel() {
        val highTask =
            Task(
                id = 1,
                title = "Important task",
                description = "",
                isCompleted = true,
                priority = Priority.HIGH,
                categoryId = TEST_INBOX_CATEGORY.id,
                sortOrder = 0,
            )
        val lowTask =
            Task(
                id = 2,
                title = "Low priority task",
                description = "",
                isCompleted = true,
                priority = Priority.LOW,
                categoryId = TEST_INBOX_CATEGORY.id,
                sortOrder = 1,
            )

        composeTestRule.setContent {
            TempoTheme {
                TasksContent(
                    uiState =
                        TasksContract.UiState(
                            isLoading = false,
                            tasks = persistentListOf(highTask, lowTask),
                            completedTaskGroups =
                                persistentMapOf(
                                    CompletedGroupKey.ByPriority(Priority.HIGH) to
                                        persistentListOf(highTask),
                                    CompletedGroupKey.ByPriority(Priority.LOW) to
                                        persistentListOf(lowTask),
                                ),
                            categories = persistentListOf(TEST_INBOX_CATEGORY),
                            showCompletedTasks = true,
                            sortOption = SortOption.BY_PRIORITY,
                        ),
                    onEvent = {},
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Completed", substring = true).assertIsDisplayed()
        // First group label "High" shown in separator, second group "Low" as header
        composeTestRule.onNodeWithText("High").assertIsDisplayed()
        composeTestRule.onNodeWithText("Low").assertIsDisplayed()
    }

    @Test
    fun completedSection_titleMode_showsDividerWithAlphabeticalLabel() {
        val completedTask =
            Task(
                id = 1,
                title = "Alpha task",
                description = "",
                isCompleted = true,
                categoryId = TEST_INBOX_CATEGORY.id,
                sortOrder = 0,
            )

        composeTestRule.setContent {
            TempoTheme {
                TasksContent(
                    uiState =
                        TasksContract.UiState(
                            isLoading = false,
                            tasks = persistentListOf(completedTask),
                            completedTaskGroups =
                                persistentMapOf(
                                    CompletedGroupKey.ByTitle to persistentListOf(completedTask),
                                ),
                            categories = persistentListOf(TEST_INBOX_CATEGORY),
                            showCompletedTasks = true,
                            sortOption = SortOption.BY_TITLE,
                        ),
                    onEvent = {},
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Completed", substring = true).assertIsDisplayed()
        composeTestRule.onNodeWithText("A → Z").assertIsDisplayed()
        composeTestRule.onNodeWithText("Alpha task").assertIsDisplayed()
    }

    // Regression test for the isFirstVisibleItem fix: CompletedTasksSeparator used to keep its
    // 28dp top padding even when it was the only thing in the list (a category with no active
    // tasks), sitting noticeably lower than the top-of-list gap every other first item gets.
    @Test
    fun completedSection_asOnlyContent_hasSameTopInsetAsFirstActiveTask() {
        val completedTask =
            Task(
                id = 1,
                title = "Done task",
                description = "",
                isCompleted = true,
                categoryId = TEST_INBOX_CATEGORY.id,
                sortOrder = 0,
            )
        val completedOnlyState =
            TasksContract.UiState(
                isLoading = false,
                tasks = persistentListOf(completedTask),
                completedTaskGroups =
                    persistentMapOf(
                        CompletedGroupKey.Flat to persistentListOf(completedTask),
                    ),
                categories = persistentListOf(TEST_INBOX_CATEGORY),
                showCompletedTasks = true,
                sortOption = SortOption.MANUAL,
            )

        val activeTask =
            Task(
                id = 2,
                title = "Active task",
                description = "",
                categoryId = TEST_INBOX_CATEGORY.id,
                sortOrder = 0,
            )
        val activeOnlyState =
            TasksContract.UiState(
                isLoading = false,
                tasks = persistentListOf(activeTask),
                activeTasks =
                    persistentMapOf(
                        ActiveGroupKey.Flat to persistentListOf(activeTask),
                    ),
                categories = persistentListOf(TEST_INBOX_CATEGORY),
                sortOption = SortOption.MANUAL,
            )

        // A single setContent with mutable state (rather than two setContent calls) — the test
        // rule's underlying Activity only accepts one setContent call per test.
        var uiState by mutableStateOf(completedOnlyState)
        composeTestRule.setContent {
            TempoTheme {
                TasksContent(uiState = uiState, onEvent = {})
            }
        }
        composeTestRule.waitForIdle()
        val completedSeparatorTop =
            composeTestRule
                .onNodeWithText("Completed", substring = true)
                .fetchSemanticsNode()
                .boundsInRoot.top

        uiState = activeOnlyState
        composeTestRule.waitForIdle()
        val activeTaskTop =
            composeTestRule
                .onNodeWithText("Active task")
                .fetchSemanticsNode()
                .boundsInRoot.top

        assertThat(completedSeparatorTop).isWithin(1f).of(activeTaskTop)
    }

    @Test
    fun tiedTasks_longPressDrag_emitsReorderWithinTheRun() {
        val tied = tiedTasks()
        val events = mutableListOf<TasksContract.UiEvent>()

        composeTestRule.setContent {
            TempoTheme {
                TasksContent(
                    uiState = tiedUiState(tied, runs = buildReorderableRuns(listOf(tied), SortOption.BY_PRIORITY)),
                    onEvent = { events += it },
                )
            }
        }

        composeTestRule.onNodeWithText("Tied A").longPressDragDown()

        val reorder = events.filterIsInstance<TasksContract.UiEvent.ReorderTasks>().single()
        assertThat(reorder.fromIndex).isEqualTo(0)
        assertThat(reorder.toIndex).isEqualTo(2)
        assertThat(reorder.tasks.map { it.id }).containsExactly(1L, 2L, 3L).inOrder()
    }

    @Test
    fun taskTheSortDistinguishes_longPressDrag_emitsNothing() {
        val tied = tiedTasks()
        val events = mutableListOf<TasksContract.UiEvent>()

        composeTestRule.setContent {
            TempoTheme {
                // No runs: every task is distinguished by the sort, so none may be reordered.
                TasksContent(uiState = tiedUiState(tied, runs = persistentMapOf()), onEvent = { events += it })
            }
        }

        composeTestRule.onNodeWithText("Tied A").longPressDragDown()

        assertThat(events.filterIsInstance<TasksContract.UiEvent.ReorderTasks>()).isEmpty()
    }

    @Test
    fun manualMode_longPressDrag_stillReordersTheWholeActiveList() {
        val tasks = tiedTasks()
        val events = mutableListOf<TasksContract.UiEvent>()

        composeTestRule.setContent {
            TempoTheme {
                TasksContent(
                    uiState =
                        TasksContract.UiState(
                            isLoading = false,
                            tasks = tasks.toPersistentList(),
                            activeTasks = persistentMapOf(ActiveGroupKey.Flat to tasks.toPersistentList()),
                            reorderableRuns = buildReorderableRuns(listOf(tasks), SortOption.MANUAL),
                            categories = persistentListOf(TEST_INBOX_CATEGORY),
                            sortOption = SortOption.MANUAL,
                        ),
                    onEvent = { events += it },
                )
            }
        }

        composeTestRule.onNodeWithText("Tied A").longPressDragDown()

        val reorder = events.filterIsInstance<TasksContract.UiEvent.ReorderTasks>().single()
        assertThat(reorder.fromIndex).isEqualTo(0)
        assertThat(reorder.toIndex).isEqualTo(2)
        assertThat(reorder.tasks).hasSize(3)
    }

    /** Three active tasks nothing but manual order can tell apart. */
    private fun tiedTasks() =
        listOf("Tied A", "Tied B", "Tied C").mapIndexed { index, title ->
            Task(
                id = index + 1L,
                title = title,
                description = "",
                priority = Priority.HIGH,
                reminderDate = LocalDateTime(2025, 6, 1, 9, 0),
                categoryId = TEST_INBOX_CATEGORY.id,
                sortOrder = index,
            )
        }

    private fun tiedUiState(
        tasks: List<Task>,
        runs: ImmutableMap<Long, ReorderableRun>,
    ) = TasksContract.UiState(
        isLoading = false,
        tasks = tasks.toPersistentList(),
        activeTasks =
            persistentMapOf(
                ActiveGroupKey.ByPriority(Priority.HIGH) to tasks.toPersistentList(),
            ),
        reorderableRuns = runs,
        categories = persistentListOf(TEST_INBOX_CATEGORY),
        sortOption = SortOption.BY_PRIORITY,
    )

    /**
     * Long-presses the card and drags it far enough down to clear two slots of the drag's
     * per-card height estimate, then lifts.
     */
    private fun SemanticsNodeInteraction.longPressDragDown() {
        composeTestRule.waitForIdle()
        performTouchInput {
            down(center)
            moveBy(Offset.Zero, delayMillis = viewConfiguration.longPressTimeoutMillis + 100)
            moveBy(Offset(0f, 200.dp.toPx()), delayMillis = 100)
            up()
        }
        composeTestRule.waitForIdle()
    }
}
