package com.mandrecode.tempo.features.focus.presentation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import com.mandrecode.tempo.core.ui.adaptive.SheetPlacement
import com.mandrecode.tempo.core.ui.adaptive.rememberSheetPlacement
import com.mandrecode.tempo.core.ui.util.rememberFrozenWhileHidden

/**
 * Where Focus shows its task editor (#502): docked beside the agenda on a large window, as in Tasks,
 * otherwise a bottom sheet. An edit opened from the Plan tasks sheet stays a sheet over it, so
 * closing the form returns to planning.
 */
internal class FocusEditorHost(
    /** Whether an editor would dock right now; when false it shows as a sheet. */
    val canDock: Boolean,
    /** Whether an editor is open beside the agenda. */
    val isDocked: Boolean,
    /** The task open beside the agenda, for its card to show as selected. */
    val selectedTaskId: Long?,
    /** The agenda's events, with a tap on the open task's card turned into closing it. */
    val onContentEvent: (FocusContract.UiEvent) -> Unit,
    /** What the pane shows, kept through its exit after the target is cleared. */
    val shownTarget: FocusContract.TaskEditorTarget?,
    /** The one editor, which moves between pane and sheet keeping its draft. */
    val content: @Composable (FocusContract.TaskEditorTarget, SheetPlacement) -> Unit,
)

/** Whether a task editor docks: only on a large window, and never over the Plan tasks sheet. */
internal fun canDockFocusEditor(
    placement: SheetPlacement,
    uiState: FocusContract.UiState,
): Boolean = placement == SheetPlacement.DockedPane && uiState.planSheet == null

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
): FocusEditorHost {
    val canDock = canDockFocusEditor(rememberSheetPlacement(), uiState)
    val isDocked = canDock && uiState.taskEditor != null
    val editingTaskId = uiState.editingTaskId()
    var dismissRequestKey by remember { mutableIntStateOf(0) }
    val currentDismissRequestKey by rememberUpdatedState(dismissRequestKey)
    val currentOnEvent by rememberUpdatedState(onEvent)
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
        isDocked = isDocked,
        selectedTaskId = editingTaskId.takeIf { isDocked },
        onContentEvent = { event ->
            // Closed through the editor's guarded dismiss, which a dirty form confirms first.
            if (closesDockedEditor(event, isDocked, editingTaskId)) dismissRequestKey++ else onEvent(event)
        },
        shownTarget = rememberFrozenWhileHidden(uiState.taskEditor, isLive = uiState.taskEditor != null),
        content = content,
    )
}
