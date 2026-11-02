package com.mandrecode.tempo.features.tasks.presentation

import androidx.annotation.StringRes
import com.mandrecode.tempo.core.domain.model.DayOfWeek
import com.mandrecode.tempo.core.domain.model.MonthDayOption
import com.mandrecode.tempo.core.domain.model.Periodicity
import com.mandrecode.tempo.core.domain.model.Priority
import com.mandrecode.tempo.core.ui.components.SnackbarBoldSegment
import com.mandrecode.tempo.core.ui.model.PermissionInfo
import com.mandrecode.tempo.features.tasks.domain.model.Category
import com.mandrecode.tempo.features.tasks.domain.model.Task
import com.mandrecode.tempo.features.tasks.domain.model.TaskAttachment
import com.mandrecode.tempo.features.tasks.domain.model.TaskSection
import com.mandrecode.tempo.features.tasks.presentation.model.ActiveGroupKey
import com.mandrecode.tempo.features.tasks.presentation.model.CompletedGroupKey
import com.mandrecode.tempo.features.tasks.presentation.model.ReorderableRun
import com.mandrecode.tempo.features.tasks.presentation.model.SectionedTaskList
import com.mandrecode.tempo.features.tasks.presentation.model.SortOption
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.PersistentSet
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime

/**
 * Contract for Tasks screen following MVI pattern.
 */
object TasksContract {
    /**
     * UI State for Tasks screen.
     */
    data class UiState(
        val tasks: ImmutableList<Task> = persistentListOf(),
        val categories: ImmutableList<Category> = persistentListOf(),
        val isLoading: Boolean = true,
        val selectedCategoryId: Long = 0L,
        val sortOption: SortOption = SortOption.MANUAL,
        val taskForm: TaskFormState = TaskFormState(),
        val categoryForm: CategoryFormState = CategoryFormState(),
        val taskToDelete: Task? = null,
        val taskToDeleteSubtasksCount: Int = 0,
        val categoryToDelete: Category? = null,
        val permissionInfo: PermissionInfo = PermissionInfo(),
        val showDeleteCategoryConfirmationDialog: Boolean = false,
        val showDeleteTaskConfirmationDialog: Boolean = false,
        val showDeleteCompletedConfirmationDialog: Boolean = false,
        val showPermissionRequestDialog: Boolean = false,
        val showPermissionRevokedDialog: Boolean = false,
        val expandedTaskIds: PersistentSet<Long> = persistentSetOf(),
        val showCompletedTasks: Boolean = true,
        val showSortBottomSheet: Boolean = false,
        val uncompletedTasksCounts: ImmutableMap<Long, Int> = persistentMapOf(),
        val activeTasks: ImmutableMap<ActiveGroupKey, ImmutableList<Task>> = persistentMapOf(),
        val completedTaskGroups: ImmutableMap<CompletedGroupKey, ImmutableList<Task>> = persistentMapOf(),
        val subtasksMap: ImmutableMap<Long, ImmutableList<Task>> = persistentMapOf(),
        /**
         * Active tasks the current sort cannot tell apart, keyed by task id. A task present here
         * may be dragged into a different position within its run; a task absent from it is
         * distinguished by the sort and therefore not draggable.
         */
        val reorderableRuns: ImmutableMap<Long, ReorderableRun> = persistentMapOf(),
        /**
         * The selected category's active list split by section (#61), or null when the category
         * has no sections — then [activeTasks] drives the list exactly as before sections existed.
         */
        val sectionedList: SectionedTaskList? = null,
        /** Every category's sections in order, for the editor's Section options. */
        val sectionsByCategory: ImmutableMap<Long, ImmutableList<TaskSection>> = persistentMapOf(),
        /** Sections the user collapsed; remembered on the device across navigation and restarts. */
        val collapsedSectionIds: PersistentSet<Long> = persistentSetOf(),
    )

    data class TaskFormState(
        val isVisible: Boolean = false,
        val editorSessionId: Long = 0L,
        val editingTask: Task? = null,
        val priority: Priority? = null,
        val reminderDate: LocalDateTime? = null,
        val periodicity: Periodicity? = null,
        val periodicityInterval: Int = 1,
        val repeatDays: PersistentSet<DayOfWeek>? = null,
        val monthDayOption: MonthDayOption? = null,
        val periodicityEndDate: LocalDate? = null,
        val parentTaskId: Long? = null,
        val parentTask: Task? = null,
        /** The section the task will be saved into (#61), or null for unsectioned. */
        val sectionId: Long? = null,
        @StringRes val titleError: Int? = null,
        @StringRes val descriptionError: Int? = null,
        /**
         * The task's attachments. For a task being edited these are its saved rows, kept live; for
         * a new task they are files already copied in and waiting for the task to be saved (#380).
         */
        val attachments: ImmutableList<TaskAttachment> = persistentListOf(),
        /**
         * What went wrong with the last attachment action, shown inside the editor: a snackbar
         * would sit under the sheet's own window and could expire unseen. Every problem of a
         * multi-file pick is listed. Cleared by the next attachment action.
         */
        val attachmentErrors: ImmutableList<Int> = persistentListOf(),
    )

    data class CategoryFormState(
        val isVisible: Boolean = false,
        val editingCategory: Category? = null,
        @StringRes val nameError: Int? = null,
    )

    /**
     * UI Events that can be triggered from the Tasks screen.
     */
    sealed interface UiEvent {
        // Category events
        data class CategorySelected(
            val categoryId: Long,
        ) : UiEvent

        /** The Tasks tab was tapped while already open: go back to the default category (#452). */
        data object ShowDefaultCategory : UiEvent

        data class ShowCategoryDialog(
            val category: Category? = null,
        ) : UiEvent

        data object HideCategoryDialog : UiEvent

        data class AddCategory(
            val name: String,
            val color: String? = null,
            val icon: String? = null,
            val sectionsEnabled: Boolean = false,
        ) : UiEvent

        data class UpdateCategory(
            val category: Category,
        ) : UiEvent

        data class RequestDeleteCategory(
            val category: Category,
        ) : UiEvent

        data object CancelDeleteCategory : UiEvent

        data class DeleteCategory(
            val category: Category,
        ) : UiEvent

        data object ClearCategoryError : UiEvent

        data class SetDefaultCategory(
            val categoryId: Long,
        ) : UiEvent

        data class ReorderCategories(
            val fromIndex: Int,
            val toIndex: Int,
            val categories: List<Category>,
        ) : UiEvent

        // Task CRUD events
        data class ShowTaskDialog(
            val task: Task? = null,
            val parentTaskId: Long? = null,
            /** Preselects a section for a new task, as the section header's add action does. */
            val sectionId: Long? = null,
        ) : UiEvent

        data object HideTaskDialog : UiEvent

        data class CreateOrUpdateTask(
            val title: String,
            val description: String,
            val categoryId: Long,
            val parentTaskId: Long? = null,
            val autoSave: Boolean = false,
        ) : UiEvent

        data class ToggleTaskCompletion(
            val task: Task,
        ) : UiEvent

        data class RequestDeleteTask(
            val task: Task,
        ) : UiEvent

        data object CancelDeleteTask : UiEvent

        data class ConfirmDeleteTask(
            val task: Task,
        ) : UiEvent

        data object ClearTaskErrors : UiEvent

        // Task options
        data class SetPriority(
            val priority: Priority,
        ) : UiEvent

        data object ClearPriority : UiEvent

        data class SetReminder(
            val year: Int,
            val month: Int,
            val day: Int,
            val hour: Int,
            val minute: Int,
        ) : UiEvent

        data object ClearReminder : UiEvent

        /** Files picked in the editor, as `content://` URIs. */
        data class AddAttachments(
            val uris: List<String>,
        ) : UiEvent

        data class RemoveAttachment(
            val attachment: TaskAttachment,
        ) : UiEvent

        /** No app on the device could open an attachment. */
        data object AttachmentOpenFailed : UiEvent

        data class SetPeriodicity(
            val periodicity: Periodicity,
        ) : UiEvent

        data object ClearPeriodicity : UiEvent

        data class SetPeriodicityInterval(
            val interval: Int,
        ) : UiEvent

        data class SetRepeatDays(
            val days: Set<DayOfWeek>?,
        ) : UiEvent

        data class SetMonthDayOption(
            val option: MonthDayOption?,
        ) : UiEvent

        data class SetPeriodicityEndDate(
            val endDate: LocalDate?,
        ) : UiEvent

        // Sort & display
        data class SetSortOption(
            val sortOption: SortOption,
        ) : UiEvent

        data object ShowSortMenu : UiEvent

        data object HideSortMenu : UiEvent

        data object ToggleCompletedTasksVisibility : UiEvent

        data class ToggleTaskExpanded(
            val taskId: Long,
        ) : UiEvent

        data class ReorderTasks(
            val fromIndex: Int,
            val toIndex: Int,
            val tasks: List<Task>,
        ) : UiEvent

        data class ReorderSubtasks(
            val fromIndex: Int,
            val toIndex: Int,
            val subtasks: List<Task>,
        ) : UiEvent

        // Completed tasks bulk delete
        data object RequestDeleteCompletedTasks : UiEvent

        data object CancelDeleteCompletedTasks : UiEvent

        data object ConfirmDeleteCompletedTasks : UiEvent

        // Permissions
        data object DismissPermissionRequestDialog : UiEvent

        data object DismissPermissionRevokedDialog : UiEvent

        data object ConfirmClearAllReminders : UiEvent

        data object OnPermissionsGranted : UiEvent

        // Sections (#61)

        /** Adds a section, last in the category or right after [afterSectionId]. */
        data class AddSection(
            val name: String,
            val afterSectionId: Long? = null,
        ) : UiEvent

        data class RenameSection(
            val sectionId: Long,
            val name: String,
        ) : UiEvent

        data class ReorderSections(
            val fromIndex: Int,
            val toIndex: Int,
            val sections: List<TaskSection>,
        ) : UiEvent

        data class DeleteSection(
            val section: TaskSection,
        ) : UiEvent

        data class ToggleSectionCollapsed(
            val sectionId: Long,
        ) : UiEvent

        /** Moves a task into [sectionId] (null = unsectioned) at [index] among its active tasks. */
        data class MoveTaskToSection(
            val taskId: Long,
            val sectionId: Long?,
            val index: Int,
        ) : UiEvent

        /** The editor's Section choice; null is "No section". */
        data class SetTaskSection(
            val sectionId: Long?,
        ) : UiEvent

        data class UndoDeletion(
            val token: Long,
        ) : UiEvent

        data class DismissDeletionUndo(
            val token: Long,
        ) : UiEvent
    }

    /**
     * One-time UI Effects for Tasks screen.
     */
    sealed interface UiEffect {
        data class ShowSnackbar(
            @StringRes val messageResId: Int? = null,
            val formatArgs: List<Any> = emptyList(),
            val boldSegment: SnackbarBoldSegment? = null,
            @StringRes val actionResId: Int? = null,
            val deletionToken: Long? = null,
        ) : UiEffect {
            init {
                require((messageResId != null) != (boldSegment != null)) {
                    "ShowSnackbar requires exactly one of messageResId or boldSegment"
                }
                require(boldSegment == null || formatArgs.isEmpty()) {
                    "ShowSnackbar.formatArgs is ignored when boldSegment is set and must be empty"
                }
            }
        }
    }
}
