package com.mandrecode.tempo.features.focus.presentation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import com.mandrecode.tempo.core.ui.adaptive.SheetPlacement
import com.mandrecode.tempo.core.ui.adaptive.rememberSheetPlacement
import com.mandrecode.tempo.core.ui.util.rememberFrozenWhileHidden

/**
 * Where Focus shows its task editor and its running session (#502): on a large window both dock in
 * one pane beside the agenda, as Tasks docks its editor, and opening one replaces the other.
 * Otherwise each is a bottom sheet. An edit opened from the Plan tasks sheet stays a sheet over it,
 * so closing the form returns to planning.
 */
internal class FocusEditorHost(
    /** Whether the pane is available right now; when false, editors and the session are sheets. */
    val canDock: Boolean,
    /** Whether the pane is showing, with a task or the session in it. */
    val isPaneVisible: Boolean,
    /** What the pane shows, kept through its exit once it has nothing left to show. */
    val shownPane: FocusPane,
    /** The task open in the pane, for its card to show as selected. */
    val selectedTaskId: Long?,
    /** The agenda's events, with a tap on the open task's card turned into closing it. */
    val onContentEvent: (FocusContract.UiEvent) -> Unit,
    /** The editor's task in the pane, kept through its exit after the target is cleared. */
    val shownTarget: FocusContract.TaskEditorTarget?,
    /** The one editor, which moves between pane and sheet keeping its draft. */
    val content: @Composable (FocusContract.TaskEditorTarget, SheetPlacement) -> Unit,
)

/** What Focus's docked pane holds. */
internal enum class FocusPane { NONE, EDITOR, SESSION }

/** Whether a task editor docks: only on a large window, and never over the Plan tasks sheet. */
internal fun canDockFocusEditor(
    placement: SheetPlacement,
    uiState: FocusContract.UiState,
): Boolean = placement == SheetPlacement.DockedPane && uiState.planSheet == null

/** What the pane shows: a task when one is open, else the session when it is, else nothing. */
internal fun focusPane(
    canDock: Boolean,
    isEditorOpen: Boolean,
    isSessionOpen: Boolean,
): FocusPane =
    when {
        !canDock -> FocusPane.NONE
        isEditorOpen -> FocusPane.EDITOR
        isSessionOpen -> FocusPane.SESSION
        else -> FocusPane.NONE
    }

/** A task has just opened while the session is docked, so the session gives up the pane. */
internal fun sessionYieldsToTask(
    canDock: Boolean,
    wasEditorOpen: Boolean,
    isEditorOpen: Boolean,
    isSessionOpen: Boolean,
): Boolean = canDock && isEditorOpen && !wasEditorOpen && isSessionOpen

/** The session is open while a task is docked, so the task gives up the pane. */
internal fun taskYieldsToSession(
    canDock: Boolean,
    isEditorOpen: Boolean,
    isSessionOpen: Boolean,
): Boolean = canDock && isEditorOpen && isSessionOpen

/** The existing task the editor is open on, if it is open on one. */
internal fun FocusContract.UiState.editingTaskId(): Long? {
    val existing = taskEditor as? FocusContract.TaskEditorTarget.Existing
    return existing?.task?.id
}

/** A tap on the card of the task already open beside the agenda, which closes it as in Tasks. */
internal fun closesDockedEditor(
    event: FocusContract.UiEvent,
    isDocked: Boolean,
    editingTaskId: Long?,
): Boolean = isDocked && event is FocusContract.UiEvent.EditTask && event.task.id == editingTaskId

@Composable
internal fun focusEditorHost(
    uiState: FocusContract.UiState,
    onEvent: (FocusContract.UiEvent) -> Unit,
    isSessionOpen: Boolean,
    onCloseSession: () -> Unit,
): FocusEditorHost {
    val canDock = canDockFocusEditor(rememberSheetPlacement(), uiState)
    val isEditorOpen = uiState.taskEditor != null
    val pane = focusPane(canDock, isEditorOpen, isSessionOpen)
    val isEditorDocked = pane == FocusPane.EDITOR
    val editingTaskId = uiState.editingTaskId()
    var dismissRequestKey by remember { mutableIntStateOf(0) }
    val currentDismissRequestKey by rememberUpdatedState(dismissRequestKey)
    val currentOnEvent by rememberUpdatedState(onEvent)
    val currentOnCloseSession by rememberUpdatedState(onCloseSession)
    // The pane holds one thing, the one opened last. A task opened while the session is docked
    // takes its place, and the rail's session pill brings the session back. Only an actual opening
    // counts: Focus is composed afresh when its tab returns, and the pill returns to it with the
    // session opened on top of whatever task was left open.
    val wasEditorOpen = remember { mutableStateOf(isEditorOpen) }
    LaunchedEffect(isEditorOpen) {
        val yields = sessionYieldsToTask(canDock, wasEditorOpen.value, isEditorOpen, isSessionOpen)
        wasEditorOpen.value = isEditorOpen
        if (yields) currentOnCloseSession()
    }
    // The session opened, or found open, while a task is docked closes the task, through the
    // editor's guarded dismiss so unsaved changes are confirmed first.
    LaunchedEffect(isSessionOpen) {
        if (taskYieldsToSession(canDock, isEditorOpen, isSessionOpen)) dismissRequestKey++
    }
    // One editor that moves between pane and sheet when the window crosses the breakpoint, keeping
    // the draft: a second copy would open the task afresh and lose what was typed.
    val content =
        remember {
            movableContentOf { target: FocusContract.TaskEditorTarget, placement: SheetPlacement ->
                FocusTaskEditor(
                    target = target,
                    onDismiss = { currentOnEvent(FocusContract.UiEvent.DismissEditor) },
                    placement = placement,
                    dismissRequestKey = currentDismissRequestKey,
                )
            }
        }
    return FocusEditorHost(
        canDock = canDock,
        isPaneVisible = pane != FocusPane.NONE,
        shownPane = rememberFrozenWhileHidden(pane, isLive = pane != FocusPane.NONE),
        selectedTaskId = editingTaskId.takeIf { isEditorDocked },
        onContentEvent = { event ->
            // Closed through the editor's guarded dismiss, which a dirty form confirms first.
            if (closesDockedEditor(event, isEditorDocked, editingTaskId)) dismissRequestKey++ else onEvent(event)
        },
        shownTarget = rememberFrozenWhileHidden(uiState.taskEditor, isLive = isEditorOpen),
        content = content,
    )
}
