package com.mandrecode.tempo.features.focus.presentation

import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTextInput
import com.mandrecode.tempo.core.data.preferences.TasksScreenPreferencesRepository
import com.mandrecode.tempo.core.ui.adaptive.SheetPlacement
import com.mandrecode.tempo.core.ui.theme.TempoTheme
import com.mandrecode.tempo.features.tasks.domain.model.Category
import com.mandrecode.tempo.features.tasks.domain.repository.CategoryRepository
import com.mandrecode.tempo.features.tasks.domain.repository.TaskRepository
import com.mandrecode.tempo.features.tasks.domain.usecase.TaskSectionUseCases
import com.mandrecode.tempo.features.tasks.presentation.TasksViewModel
import com.mandrecode.tempo.features.tasks.presentation.components.TASK_BOTTOM_SHEET_TITLE_FIELD_TEST_TAG
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOf
import org.junit.Rule
import org.junit.Test

/** The task editor Focus opens over itself (#434). */
class FocusTaskEditorTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    /**
     * The view model outlives a configuration change; the editor's composable does not. Coming
     * back must not open the editor a second time, which would reset the half-written task.
     */
    @Test
    fun recreatingTheScreen_keepsTheDraft() = recreatingTheScreenKeepsTheDraft(SheetPlacement.BottomSheet)

    /** The same holds beside the agenda on a large window (#502). */
    @Test
    fun recreatingTheScreen_keepsTheDraftInTheDockedPane() = recreatingTheScreenKeepsTheDraft(SheetPlacement.DockedPane)

    private fun recreatingTheScreenKeepsTheDraft(placement: SheetPlacement) {
        val viewModel = tasksViewModel()
        val restorationTester = StateRestorationTester(composeTestRule)
        restorationTester.setContent {
            TempoTheme {
                FocusTaskEditor(
                    target = FocusContract.TaskEditorTarget.NewTask,
                    onDismiss = {},
                    placement = placement,
                    dismissRequestKey = 0,
                    viewModel = viewModel,
                )
            }
        }
        composeTestRule.onNodeWithTag(TASK_BOTTOM_SHEET_TITLE_FIELD_TEST_TAG).performTextInput("Pay rent")

        restorationTester.emulateSavedInstanceStateRestore()

        composeTestRule.onNodeWithTag(TASK_BOTTOM_SHEET_TITLE_FIELD_TEST_TAG).assertTextContains("Pay rent")
    }

    private fun tasksViewModel(): TasksViewModel {
        val taskRepository = mockk<TaskRepository>(relaxed = true)
        val categoryRepository = mockk<CategoryRepository>(relaxed = true)
        val preferences = mockk<TasksScreenPreferencesRepository>(relaxed = true)
        coEvery { taskRepository.getAllTasks() } returns flowOf(emptyList())
        coEvery { categoryRepository.getAllCategories() } returns
            flowOf(listOf(Category(id = -1, name = "Inbox", isDefault = true)))
        every { preferences.getSelectedCategoryId() } returns -1L
        return TasksViewModel(
            taskRepository,
            categoryRepository,
            mockk(relaxed = true),
            mockk(relaxed = true),
            mockk(relaxed = true),
            mockk(relaxed = true),
            mockk(relaxed = true),
            mockk(relaxed = true),
            mockk(relaxed = true),
            mockk(relaxed = true),
            mockk(relaxed = true),
            mockk(relaxed = true),
            mockk(relaxed = true),
            mockk(relaxed = true),
            mockk(relaxed = true),
            preferences,
            Dispatchers.Unconfined,
            mockk(relaxed = true),
            mockk(relaxed = true),
            mockk(relaxed = true),
            mockk<TaskSectionUseCases>(relaxed = true) {
                every { repository.getAllSections() } returns flowOf(emptyList())
            },
            kotlin.time.Clock.System,
        )
    }
}
