package com.mandrecode.tempo.features.tasks.presentation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mandrecode.tempo.R
import com.mandrecode.tempo.core.ui.adaptive.DockedEditorPane
import com.mandrecode.tempo.core.ui.adaptive.SheetPlacement
import com.mandrecode.tempo.core.ui.adaptive.rememberSheetPlacement
import com.mandrecode.tempo.core.ui.components.ExpressiveSnackbarHost
import com.mandrecode.tempo.core.ui.components.HandleReminderPermissions
import com.mandrecode.tempo.core.ui.components.HoldSplashWhileFirstLoad
import com.mandrecode.tempo.core.ui.components.PermissionRevokedDialog
import com.mandrecode.tempo.core.ui.glass.ambientBackground
import com.mandrecode.tempo.core.ui.navigation.FloatingRailContentStartPadding
import com.mandrecode.tempo.core.ui.navigation.FloatingRailExpandedContentStartPadding
import com.mandrecode.tempo.core.ui.navigation.PendingNotificationAction
import com.mandrecode.tempo.core.ui.navigation.TasksFloatingBarState
import com.mandrecode.tempo.core.ui.navigation.adaptiveScreenContentLayout
import com.mandrecode.tempo.core.ui.navigation.animatedDockedContentBias
import com.mandrecode.tempo.core.ui.navigation.animatedRailClearance
import com.mandrecode.tempo.core.ui.navigation.floatingNavigationSnackbarBottomPadding
import com.mandrecode.tempo.core.ui.navigation.isFloatingNavigationRailLayout
import com.mandrecode.tempo.core.ui.util.rememberFrozenWhileHidden
import com.mandrecode.tempo.features.tasks.presentation.components.CategoryEditSheet
import com.mandrecode.tempo.features.tasks.presentation.components.TaskAttachmentActions
import com.mandrecode.tempo.features.tasks.presentation.components.TaskBottomSheet
import com.mandrecode.tempo.features.tasks.presentation.components.dialogs.DeleteCategoryDialog
import com.mandrecode.tempo.features.tasks.presentation.components.dialogs.DeleteCompletedConfirmationDialog
import com.mandrecode.tempo.features.tasks.presentation.components.dialogs.DeleteTaskConfirmDialog
import com.mandrecode.tempo.features.tasks.presentation.components.sections.SortBottomSheet

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TasksScreen(
    modifier: Modifier = Modifier,
    viewModel: TasksViewModel = viewModel(),
    pendingNotificationAction: PendingNotificationAction? = null,
    onConsumePendingNotificationAction: () -> Unit = {},
    topBar: @Composable () -> Unit = {},
    showAddTaskRailButton: Boolean = false,
    onFloatingBarStateChange: (TasksFloatingBarState) -> Unit = {},
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    HoldSplashWhileFirstLoad(isLoading = uiState.isLoading)
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val currentOnConsumePendingNotificationAction by rememberUpdatedState(onConsumePendingNotificationAction)

    LaunchedEffect(Unit) {
        viewModel.checkPermissionsAndSyncReminders()
    }

    LaunchedEffect(pendingNotificationAction) {
        when (val action = pendingNotificationAction) {
            is PendingNotificationAction.OpenTask -> {
                viewModel.openTaskFromNotification(action.taskId, action.originalReminderDate)
                currentOnConsumePendingNotificationAction()
            }

            PendingNotificationAction.OpenNewTaskDialog -> {
                viewModel.onEvent(TasksContract.UiEvent.ShowTaskDialog())
                currentOnConsumePendingNotificationAction()
            }

            else -> Unit
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer =
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_START) {
                    viewModel.onTabShown()
                    viewModel.checkPermissionsAndSyncReminders()
                }
            }
        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.uiEffect.collect { effect ->
            showTasksEffect(effect, context, snackbarHostState, viewModel::onEvent)
        }
    }

    val isRailLayout = isFloatingNavigationRailLayout()
    val editorPlacement = rememberSheetPlacement()
    val isDockedEditor = editorPlacement == SheetPlacement.DockedPane
    val railContentClearance =
        animatedRailClearance(
            when {
                !isRailLayout -> 0.dp
                isDockedEditor -> FloatingRailExpandedContentStartPadding
                else -> FloatingRailContentStartPadding
            },
        )
    val isEditorDocked = isDockedEditor && uiState.taskForm.isVisible
    val contentBias = animatedDockedContentBias(isEditorDocked)
    var contentBlockTop by remember { mutableStateOf(0.dp) }
    var editorDismissRequestKey by remember { mutableIntStateOf(0) }
    val onAddTask =
        remember(viewModel) {
            { viewModel.onEvent(TasksContract.UiEvent.ShowTaskDialog()) }
        }
    val onSort =
        remember(viewModel) {
            { viewModel.onEvent(TasksContract.UiEvent.ShowSortMenu) }
        }
    val onContentEvent: (TasksContract.UiEvent) -> Unit = { event ->
        val isSelectedCardActivated =
            isDockedEditor &&
                uiState.taskForm.isVisible &&
                event is TasksContract.UiEvent.ShowTaskDialog &&
                event.task?.id != null &&
                event.task.id == uiState.taskForm.editingTask?.id
        if (isSelectedCardActivated) {
            editorDismissRequestKey++
        } else {
            viewModel.onEvent(event)
        }
    }

    SideEffect {
        onFloatingBarStateChange(
            TasksFloatingBarState(
                visible = showAddTaskRailButton,
                sortOption = uiState.sortOption,
                onAddTask = onAddTask,
                onSort = onSort,
                sortMenuExpanded = isRailLayout && uiState.showSortBottomSheet,
                onSelectSortOption = { viewModel.onEvent(TasksContract.UiEvent.SetSortOption(it)) },
                onDismissSort = { viewModel.onEvent(TasksContract.UiEvent.HideSortMenu) },
                onReselect = { viewModel.onEvent(TasksContract.UiEvent.ShowDefaultCategory) },
            ),
        )
    }

    val currentEditorDismissRequestKey = rememberUpdatedState(editorDismissRequestKey)
    val editorContent =
        remember(viewModel) {
            movableContentOf { state: TasksContract.UiState, placement: SheetPlacement ->
                TaskEditor(
                    uiState = state,
                    onEvent = viewModel::onEvent,
                    placement = placement,
                    dismissRequestKey = currentEditorDismissRequestKey.value,
                )
            }
        }
    // TempoDockedSheet resets taskForm the instant dismissal is requested, before the docked
    // pane's own shrink-out animation finishes. Freezing the last visible state here stops the
    // exiting pane from recomposing into a blank "new task" form mid-animation.
    val frozenUiState = rememberFrozenWhileHidden(uiState, isLive = uiState.taskForm.isVisible)

    Box(modifier = modifier.fillMaxSize()) {
        Row(
            modifier =
                Modifier
                    .fillMaxSize()
                    // Explicit fill, not left to the root Surface behind it: on rail/expanded
                    // layouts adaptiveScreenContentLayout insets the Scaffold below to clear the
                    // floating rail, and the docked editor pane leaves a gutter and margins around
                    // its card, exposing this Row's own background in those gaps. It must match
                    // the Scaffold's top-block color, not whatever the root happens to be (root is
                    // deliberately colorScheme.surface — see MainActivity — to avoid a different
                    // flash on the tab-switch crossfade). On the Row rather than each child so the
                    // pane's fade-in doesn't blend its gutter up from the white root. The ambient
                    // backdrop is painted here once, for the whole page; everything inside it is
                    // transparent so the glows line up across the header, gutter and panel.
                    .ambientBackground(),
        ) {
            Box(
                modifier =
                    Modifier
                        .weight(1f)
                        .fillMaxHeight(),
            ) {
                Scaffold(
                    modifier =
                        Modifier.adaptiveScreenContentLayout(
                            railClearance = railContentClearance,
                            horizontalBias = contentBias,
                        ),
                    containerColor = Color.Transparent,
                    contentWindowInsets = WindowInsets(0),
                    topBar = topBar,
                ) {
                    Box(
                        modifier = Modifier.padding(it).fillMaxSize(),
                    ) {
                        TasksContent(
                            uiState = uiState,
                            onEvent = onContentEvent,
                            selectedTaskId = uiState.taskForm.editingTask?.id,
                            onContentBlockTopChange = { contentBlockTop = it },
                        )

                        Box(
                            modifier =
                                Modifier
                                    .fillMaxSize()
                                    .navigationBarsPadding()
                                    .padding(bottom = floatingNavigationSnackbarBottomPadding()),
                            contentAlignment = Alignment.BottomCenter,
                        ) {
                            ExpressiveSnackbarHost(snackbarHostState)
                        }
                    }
                }
            }
            DockedEditorPane(visible = isEditorDocked, contentBlockTop = contentBlockTop) {
                // Only while docked is where the editor belongs. When the window narrows past the
                // breakpoint the pane still plays its exit, but the editor has to move to the sheet
                // in the same frame, not be in both: a second copy would start empty and lose the draft.
                if (isDockedEditor) {
                    editorContent(frozenUiState, editorPlacement)
                }
            }
        }

        if (uiState.showPermissionRevokedDialog) {
            PermissionRevokedDialog(
                permissionInfo = uiState.permissionInfo,
                context = context,
                dismissPermissionRevokedDialog = {
                    viewModel.onEvent(TasksContract.UiEvent.DismissPermissionRevokedDialog)
                },
                confirmClearAllReminders = {
                    viewModel.onEvent(TasksContract.UiEvent.ConfirmClearAllReminders)
                },
                notificationPrefixRes = R.string.permission_revoked_notifications_prefix,
                fallbackRes = R.string.permission_revoked_fallback_tasks,
            )
        }

        if (!isDockedEditor && uiState.taskForm.isVisible) {
            editorContent(uiState, editorPlacement)
        }

        if (!uiState.showPermissionRevokedDialog) {
            HandleReminderPermissions(
                show = uiState.showPermissionRequestDialog,
                onGrantPermissions = {
                    viewModel.onEvent(TasksContract.UiEvent.DismissPermissionRequestDialog)
                    viewModel.onEvent(TasksContract.UiEvent.OnPermissionsGranted)
                },
                onDismiss = {
                    viewModel.onEvent(TasksContract.UiEvent.DismissPermissionRequestDialog)
                },
            )
        }

        if (uiState.categoryForm.isVisible) {
            val editingCategory = uiState.categoryForm.editingCategory
            CategoryEditSheet(
                category = editingCategory,
                categories = uiState.categories,
                nameError = uiState.categoryForm.nameError,
                onDismiss = { viewModel.onEvent(TasksContract.UiEvent.HideCategoryDialog) },
                onClearError = { viewModel.onEvent(TasksContract.UiEvent.ClearCategoryError) },
                onSave = { name, color, icon, isDefault, sectionsEnabled ->
                    if (editingCategory != null) {
                        viewModel.onEvent(
                            TasksContract.UiEvent.UpdateCategory(
                                editingCategory.copy(
                                    name = name,
                                    color = color,
                                    icon = icon,
                                    isDefault = isDefault,
                                    sectionsEnabled = sectionsEnabled,
                                ),
                            ),
                        )
                    } else {
                        viewModel.onEvent(
                            TasksContract.UiEvent.AddCategory(
                                name = name,
                                color = color,
                                icon = icon,
                                sectionsEnabled = sectionsEnabled,
                            ),
                        )
                    }
                },
                onDelete =
                    if (editingCategory != null && !editingCategory.isDefault) {
                        {
                            viewModel.onEvent(TasksContract.UiEvent.RequestDeleteCategory(editingCategory))
                        }
                    } else {
                        null
                    },
            )
        }

        uiState.categoryToDelete?.let { categoryToDelete ->
            if (uiState.showDeleteCategoryConfirmationDialog) {
                DeleteCategoryDialog(
                    onCancelDeleteCategory = { viewModel.onEvent(TasksContract.UiEvent.CancelDeleteCategory) },
                    onDeleteCategory = { viewModel.onEvent(TasksContract.UiEvent.DeleteCategory(it)) },
                    categoryToDelete = categoryToDelete,
                )
            }
        }

        if (uiState.showDeleteTaskConfirmationDialog && uiState.taskToDelete != null) {
            DeleteTaskConfirmDialog(
                onCancelDeleteTask = { viewModel.onEvent(TasksContract.UiEvent.CancelDeleteTask) },
                onConfirmDeleteTask = { viewModel.onEvent(TasksContract.UiEvent.ConfirmDeleteTask(it)) },
                taskToDelete = uiState.taskToDelete,
                subtasksCount = uiState.taskToDeleteSubtasksCount,
            )
        }

        if (uiState.showDeleteCompletedConfirmationDialog) {
            DeleteCompletedConfirmationDialog(
                onCancelDeleteCompletedTasks = { viewModel.onEvent(TasksContract.UiEvent.CancelDeleteCompletedTasks) },
                onConfirmDeleteCompletedTasks = {
                    viewModel.onEvent(TasksContract.UiEvent.ConfirmDeleteCompletedTasks)
                },
            )
        }

        if (uiState.showSortBottomSheet && !isRailLayout) {
            SortBottomSheet(
                currentSortOption = uiState.sortOption,
                onSelectSortOption = {
                    viewModel.onEvent(TasksContract.UiEvent.SetSortOption(it))
                },
                onDismiss = { viewModel.onEvent(TasksContract.UiEvent.HideSortMenu) },
            )
        }
    }
}

@Composable
internal fun TaskEditor(
    uiState: TasksContract.UiState,
    onEvent: (TasksContract.UiEvent) -> Unit,
    placement: SheetPlacement,
    dismissRequestKey: Int,
) {
    TaskBottomSheet(
        categories = uiState.categories,
        sectionsByCategory = uiState.enabledSectionsByCategory(),
        onSelectSection = { onEvent(TasksContract.UiEvent.SetTaskSection(it)) },
        selectedCategoryIdFromFilter = uiState.taskEditorCategoryId(),
        formState = uiState.taskForm,
        onSetPriority = { onEvent(TasksContract.UiEvent.SetPriority(it)) },
        onClearPriority = { onEvent(TasksContract.UiEvent.ClearPriority) },
        onSetReminder = { year, month, day, hour, minute ->
            onEvent(TasksContract.UiEvent.SetReminder(year, month, day, hour, minute))
        },
        onClearReminder = { onEvent(TasksContract.UiEvent.ClearReminder) },
        onSetPeriodicity = { onEvent(TasksContract.UiEvent.SetPeriodicity(it)) },
        onClearPeriodicity = { onEvent(TasksContract.UiEvent.ClearPeriodicity) },
        onSetPeriodicityInterval = { onEvent(TasksContract.UiEvent.SetPeriodicityInterval(it)) },
        onSetRepeatDays = { onEvent(TasksContract.UiEvent.SetRepeatDays(it)) },
        onSetMonthDayOption = { onEvent(TasksContract.UiEvent.SetMonthDayOption(it)) },
        onSetPeriodicityEndDate = { onEvent(TasksContract.UiEvent.SetPeriodicityEndDate(it)) },
        onDismiss = { onEvent(TasksContract.UiEvent.HideTaskDialog) },
        onClearErrors = { onEvent(TasksContract.UiEvent.ClearTaskErrors) },
        onConfirm = { title, description, categoryId ->
            onEvent(
                TasksContract.UiEvent.CreateOrUpdateTask(
                    title = title,
                    description = description,
                    categoryId = categoryId,
                    parentTaskId = uiState.taskForm.parentTaskId,
                    autoSave = false,
                ),
            )
        },
        onAutoSave = { title, description, categoryId ->
            onEvent(
                TasksContract.UiEvent.CreateOrUpdateTask(
                    title = title,
                    description = description,
                    categoryId = categoryId,
                    parentTaskId = uiState.taskForm.parentTaskId,
                    autoSave = true,
                ),
            )
        },
        task = uiState.taskForm.editingTask,
        onDelete =
            uiState.taskForm.editingTask?.let { editingTask ->
                { onEvent(TasksContract.UiEvent.RequestDeleteTask(editingTask)) }
            },
        onToggleCompletion = { onEvent(TasksContract.UiEvent.ToggleTaskCompletion(it)) },
        placement = placement,
        dismissRequestKey = dismissRequestKey,
        attachmentActions =
            TaskAttachmentActions(
                onAddAttachments = { onEvent(TasksContract.UiEvent.AddAttachments(it)) },
                onRemoveAttachment = { onEvent(TasksContract.UiEvent.RemoveAttachment(it)) },
                onAttachmentOpenFail = { onEvent(TasksContract.UiEvent.AttachmentOpenFailed) },
            ),
    )
}

private fun TasksContract.UiState.taskEditorCategoryId(): Long =
    when {
        taskForm.editingTask != null -> taskForm.editingTask.categoryId
        taskForm.parentTask != null -> taskForm.parentTask.categoryId
        else -> selectedCategoryId
    }

/** Only categories with sections on offer the editor's choice; a hidden section is kept as it is. */
private fun TasksContract.UiState.enabledSectionsByCategory() =
    sectionsByCategory.filterKeys { id -> categories.any { it.id == id && it.sectionsEnabled } }
