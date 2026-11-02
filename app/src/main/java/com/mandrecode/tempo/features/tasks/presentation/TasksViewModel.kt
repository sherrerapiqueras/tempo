package com.mandrecode.tempo.features.tasks.presentation

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import com.mandrecode.tempo.core.data.preferences.TasksScreenPreferencesRepository
import com.mandrecode.tempo.core.di.DefaultDispatcher
import com.mandrecode.tempo.core.ui.components.SnackbarBoldSegment
import com.mandrecode.tempo.features.tasks.domain.model.Task
import com.mandrecode.tempo.features.tasks.domain.repository.CategoryRepository
import com.mandrecode.tempo.features.tasks.domain.repository.TaskAttachmentRepository
import com.mandrecode.tempo.features.tasks.domain.repository.TaskRepository
import com.mandrecode.tempo.features.tasks.domain.usecase.ClearAllTaskRemindersUseCase
import com.mandrecode.tempo.features.tasks.domain.usecase.CreateCategoryUseCase
import com.mandrecode.tempo.features.tasks.domain.usecase.CreateTaskUseCase
import com.mandrecode.tempo.features.tasks.domain.usecase.DeleteCategoryUseCase
import com.mandrecode.tempo.features.tasks.domain.usecase.DeleteCompletedTasksUseCase
import com.mandrecode.tempo.features.tasks.domain.usecase.DeleteTaskUseCase
import com.mandrecode.tempo.features.tasks.domain.usecase.ReorderCategoriesUseCase
import com.mandrecode.tempo.features.tasks.domain.usecase.ReorderTasksUseCase
import com.mandrecode.tempo.features.tasks.domain.usecase.RestoreDeletedCategoryUseCase
import com.mandrecode.tempo.features.tasks.domain.usecase.RestoreDeletedTasksUseCase
import com.mandrecode.tempo.features.tasks.domain.usecase.SetDefaultCategoryUseCase
import com.mandrecode.tempo.features.tasks.domain.usecase.TaskSectionUseCases
import com.mandrecode.tempo.features.tasks.domain.usecase.ToggleTaskCompletionUseCase
import com.mandrecode.tempo.features.tasks.domain.usecase.UpdateCategoryUseCase
import com.mandrecode.tempo.features.tasks.domain.usecase.UpdateTaskUseCase
import com.mandrecode.tempo.infrastructure.permissions.PermissionChecker
import dagger.hilt.android.lifecycle.HiltViewModel
import jakarta.inject.Inject
import kotlinx.collections.immutable.toPersistentSet
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import kotlin.time.Clock

@HiltViewModel
class TasksViewModel
    @Inject
    constructor(
        internal val taskRepository: TaskRepository,
        internal val categoryRepository: CategoryRepository,
        internal val createTaskUseCase: CreateTaskUseCase,
        internal val updateTaskUseCase: UpdateTaskUseCase,
        internal val deleteTaskUseCase: DeleteTaskUseCase,
        internal val toggleTaskCompletionUseCase: ToggleTaskCompletionUseCase,
        internal val createCategoryUseCase: CreateCategoryUseCase,
        internal val updateCategoryUseCase: UpdateCategoryUseCase,
        internal val deleteCategoryUseCase: DeleteCategoryUseCase,
        internal val setDefaultCategoryUseCase: SetDefaultCategoryUseCase,
        internal val reorderCategoriesUseCase: ReorderCategoriesUseCase,
        internal val deleteCompletedTasksUseCase: DeleteCompletedTasksUseCase,
        internal val clearAllRemindersUseCase: ClearAllTaskRemindersUseCase,
        internal val reorderTasksUseCase: ReorderTasksUseCase,
        internal val permissionChecker: PermissionChecker,
        internal val tasksScreenPreferencesRepository: TasksScreenPreferencesRepository,
        @DefaultDispatcher internal val defaultDispatcher: CoroutineDispatcher,
        internal val restoreDeletedTasksUseCase: RestoreDeletedTasksUseCase,
        internal val restoreDeletedCategoryUseCase: RestoreDeletedCategoryUseCase,
        internal val taskAttachmentRepository: TaskAttachmentRepository,
        internal val sectionUseCases: TaskSectionUseCases,
        private val clock: Clock,
    ) : ViewModel() {
        private val initialSelectedCategoryId = tasksScreenPreferencesRepository.getSelectedCategoryId()

        private val _uiState =
            MutableStateFlow(
                TasksContract.UiState(
                    selectedCategoryId = initialSelectedCategoryId,
                    sortOption =
                        tasksScreenPreferencesRepository.getSortOption(initialSelectedCategoryId),
                    showCompletedTasks = tasksScreenPreferencesRepository.getShowCompletedTasks(),
                    collapsedSectionIds = tasksScreenPreferencesRepository.getCollapsedSectionIds().toPersistentSet(),
                ),
            )
        internal val mutableUiState: MutableStateFlow<TasksContract.UiState>
            get() = _uiState
        val uiState: StateFlow<TasksContract.UiState> = _uiState.asStateFlow()

        private val _uiEffect = Channel<TasksContract.UiEffect>(Channel.BUFFERED)
        val uiEffect = _uiEffect.receiveAsFlow()

        // For periodic tasks opened from a reminder notification, TaskReminderReceiver
        // pre-advances reminderDate to the next occurrence before the user sees the
        // sheet. We stash the original reminderDate here so that if the user toggles
        // completion from the bottom sheet, the use case computes the next occurrence
        // from the correct anchor (mirrors MarkAsCompletedReceiver). Cleared after the
        // first toggle, when a different task is opened, or when the form is dismissed.
        internal var pendingOriginalReminderDate: Pair<Long, LocalDateTime>? = null
        internal val pendingDeletionSnapshots = mutableMapOf<Long, PendingTaskDeletion>()
        internal var nextDeletionToken = 0L
        internal var nextEditorSessionId = 0L

        /** The day the tab was last shown on; a new one regroups the list (see [onTabShown]). */
        internal val currentDay = MutableStateFlow(systemToday())

        // Set only when this screen has told the user their reminders need permission — a request
        // after a save, or the revoked-permission dialog. The "reminders are active again" message
        // answers that, so it is shown once when permission comes back and never otherwise (#437).
        internal var remindersAwaitingPermission = false

        // The reminder lookup behind the revoked-permission check, kept so a newer check or
        // clearing every reminder can cancel it: a lookup that finishes after either would act on
        // reminders that no longer matter.
        internal var permissionLookupJob: Job? = null

        internal suspend fun showSnackbar(
            @StringRes messageResId: Int,
            formatArgs: List<Any> = emptyList(),
            @StringRes actionResId: Int? = null,
            deletionToken: Long? = null,
        ) {
            _uiEffect.send(
                TasksContract.UiEffect.ShowSnackbar(
                    messageResId = messageResId,
                    formatArgs = formatArgs,
                    actionResId = actionResId,
                    deletionToken = deletionToken,
                ),
            )
        }

        internal suspend fun showBoldSnackbar(
            @StringRes prefixResId: Int,
            word: String,
            @StringRes suffixResId: Int,
            @StringRes actionResId: Int? = null,
            deletionToken: Long? = null,
        ) {
            _uiEffect.send(
                TasksContract.UiEffect.ShowSnackbar(
                    boldSegment = SnackbarBoldSegment(prefixResId, word, suffixResId),
                    actionResId = actionResId,
                    deletionToken = deletionToken,
                ),
            )
        }

        init {
            loadData()
            observeEditedTaskAttachments()
        }

        /**
         * The Tasks tab has come into view, or the app back to the front with it showing (#438).
         * The tab is warmed up before it is first opened, so the day the date groups were drawn
         * for may have ended since: a new day regroups the list even though no task changed, and
         * yesterday's tasks move to Overdue. Every other regrouping reads the clock as well.
         */
        fun onTabShown() {
            currentDay.value = systemToday()
        }

        internal fun systemToday(): LocalDate = clock.todayIn(TimeZone.currentSystemDefault())

        fun onEvent(event: TasksContract.UiEvent) {
            when (event) {
                is TasksContract.UiEvent.CategorySelected -> selectCategory(event.categoryId)
                is TasksContract.UiEvent.ShowDefaultCategory -> showDefaultCategory()
                is TasksContract.UiEvent.ShowCategoryDialog -> showCategoryDialog(event.category)
                is TasksContract.UiEvent.HideCategoryDialog -> hideCategoryDialog()
                is TasksContract.UiEvent.AddCategory ->
                    addCategory(event.name, event.color, event.icon, event.sectionsEnabled)
                is TasksContract.UiEvent.UpdateCategory -> updateCategory(event.category)
                is TasksContract.UiEvent.RequestDeleteCategory -> requestDeleteCategory(event.category)
                is TasksContract.UiEvent.CancelDeleteCategory -> cancelDeleteCategory()
                is TasksContract.UiEvent.DeleteCategory -> deleteCategory(event.category)
                is TasksContract.UiEvent.ClearCategoryError -> clearCategoryError()
                is TasksContract.UiEvent.SetDefaultCategory -> setDefaultCategory(event.categoryId)
                is TasksContract.UiEvent.ReorderCategories ->
                    reorderCategories(event.fromIndex, event.toIndex, event.categories)
                is TasksContract.UiEvent.ShowTaskDialog ->
                    showTaskDialog(event.task, event.parentTaskId, event.sectionId)
                is TasksContract.UiEvent.HideTaskDialog -> hideTaskDialog()
                is TasksContract.UiEvent.CreateOrUpdateTask ->
                    createOrUpdateTask(
                        event.title,
                        event.description,
                        event.categoryId,
                        event.parentTaskId,
                        event.autoSave,
                    )
                is TasksContract.UiEvent.ToggleTaskCompletion -> toggleTaskCompletion(event.task)
                is TasksContract.UiEvent.RequestDeleteTask -> requestDeleteTask(event.task)
                is TasksContract.UiEvent.CancelDeleteTask -> cancelDeleteTask()
                is TasksContract.UiEvent.ConfirmDeleteTask -> confirmDeleteTask(event.task)
                is TasksContract.UiEvent.ClearTaskErrors -> clearTaskErrors()
                is TasksContract.UiEvent.SetPriority -> onSetPriority(event.priority)
                is TasksContract.UiEvent.ClearPriority -> onClearPriority()
                is TasksContract.UiEvent.SetReminder ->
                    setReminder(
                        event.year,
                        event.month,
                        event.day,
                        event.hour,
                        event.minute,
                    )
                is TasksContract.UiEvent.ClearReminder -> clearReminder()
                is TasksContract.UiEvent.AddAttachments -> addAttachments(event.uris)
                is TasksContract.UiEvent.RemoveAttachment -> removeAttachment(event.attachment)
                is TasksContract.UiEvent.AttachmentOpenFailed -> onAttachmentOpenFailed()
                is TasksContract.UiEvent.SetPeriodicity -> onSetPeriodicity(event.periodicity)
                is TasksContract.UiEvent.ClearPeriodicity -> onClearPeriodicity()
                is TasksContract.UiEvent.SetPeriodicityInterval -> onSetPeriodicityInterval(event.interval)
                is TasksContract.UiEvent.SetRepeatDays -> onSetRepeatDays(event.days)
                is TasksContract.UiEvent.SetMonthDayOption -> onSetMonthDayOption(event.option)
                is TasksContract.UiEvent.SetPeriodicityEndDate -> onSetPeriodicityEndDate(event.endDate)
                is TasksContract.UiEvent.SetSortOption -> setSortOption(event.sortOption)
                is TasksContract.UiEvent.ShowSortMenu -> showSortMenu()
                is TasksContract.UiEvent.HideSortMenu -> hideSortMenu()
                is TasksContract.UiEvent.ToggleCompletedTasksVisibility -> toggleCompletedTasksVisibility()
                is TasksContract.UiEvent.ToggleTaskExpanded -> toggleTaskExpanded(event.taskId)
                is TasksContract.UiEvent.ReorderTasks ->
                    reorderTasks(
                        event.fromIndex,
                        event.toIndex,
                        event.tasks,
                    )
                is TasksContract.UiEvent.ReorderSubtasks ->
                    reorderTasks(
                        event.fromIndex,
                        event.toIndex,
                        event.subtasks,
                    )
                is TasksContract.UiEvent.AddSection -> addSection(event.name, event.afterSectionId)
                is TasksContract.UiEvent.RenameSection -> renameSection(event.sectionId, event.name)
                is TasksContract.UiEvent.ReorderSections ->
                    reorderSections(event.fromIndex, event.toIndex, event.sections)
                is TasksContract.UiEvent.DeleteSection -> deleteSection(event.section)
                is TasksContract.UiEvent.ToggleSectionCollapsed -> toggleSectionCollapsed(event.sectionId)
                is TasksContract.UiEvent.MoveTaskToSection ->
                    moveTaskToSection(event.taskId, event.sectionId, event.index)
                is TasksContract.UiEvent.SetTaskSection -> setTaskFormSection(event.sectionId)
                is TasksContract.UiEvent.RequestDeleteCompletedTasks -> requestDeleteCompletedTasks()
                is TasksContract.UiEvent.CancelDeleteCompletedTasks -> cancelDeleteCompletedTasks()
                is TasksContract.UiEvent.ConfirmDeleteCompletedTasks -> confirmDeleteCompletedTasks()
                is TasksContract.UiEvent.DismissPermissionRequestDialog -> dismissPermissionRequestDialog()
                is TasksContract.UiEvent.DismissPermissionRevokedDialog -> dismissPermissionRevokedDialog()
                is TasksContract.UiEvent.ConfirmClearAllReminders -> confirmClearAllReminders()
                is TasksContract.UiEvent.OnPermissionsGranted -> onPermissionsGranted()
                is TasksContract.UiEvent.UndoDeletion -> undoDeletion(event.token)
                is TasksContract.UiEvent.DismissDeletionUndo -> dismissDeletionUndo(event.token)
            }
        }

        fun getSubtasks(parentId: Long): Flow<List<Task>> = taskRepository.getSubtasks(parentId)

        fun openTaskFromNotification(
            taskId: Long,
            originalReminderDate: LocalDateTime? = null,
        ) {
            openTaskFromNotificationInternal(taskId, originalReminderDate)
        }

        fun checkPermissionsAndSyncReminders() {
            checkPermissionsAndSyncRemindersInternal()
        }
    }
