package com.mandrecode.tempo.features.focus.presentation

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mandrecode.tempo.core.ui.adaptive.SheetPlacement
import com.mandrecode.tempo.core.ui.components.TempoModalBottomSheet
import com.mandrecode.tempo.core.ui.editor.EDITOR_TEXT_FIELD_TOP_INSET
import com.mandrecode.tempo.core.ui.theme.inputTitle
import com.mandrecode.tempo.features.focus.domain.model.TaskFocusToday
import com.mandrecode.tempo.features.focus.presentation.components.DeleteSessionDialog
import com.mandrecode.tempo.features.focus.presentation.components.LinkTaskSheet
import com.mandrecode.tempo.features.focus.presentation.components.ReplaceSessionDialog
import com.mandrecode.tempo.features.focus.presentation.components.SessionBody
import com.mandrecode.tempo.features.focus.presentation.components.SessionBodyActions
import com.mandrecode.tempo.features.focus.presentation.components.SessionEditorForm
import com.mandrecode.tempo.features.focus.presentation.components.SessionLinkActions
import com.mandrecode.tempo.features.focus.presentation.components.SessionNameField
import com.mandrecode.tempo.features.focus.presentation.components.SessionSetupActions
import com.mandrecode.tempo.features.focus.presentation.components.rememberSessionEditorForm
import com.mandrecode.tempo.features.tasks.domain.model.Task
import kotlinx.collections.immutable.ImmutableList

/**
 * Entry point for the session sheet: resolves state and closes itself when the session ends, so the
 * caller only has to say how to dismiss it.
 *
 * On a large window Focus docks it beside the agenda instead (#502): [placement] is then
 * [SheetPlacement.DockedPane], and [onEditTask] hands a subtask's editor to Focus, whose pane it
 * takes over. Without [onEditTask] the subtask editor opens as a sheet over this one.
 */
@Composable
fun FocusSessionRoute(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    placement: SheetPlacement = SheetPlacement.BottomSheet,
    onEditTask: ((Task) -> Unit)? = null,
    viewModel: FocusViewModel = viewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    // Not `uiState.session`: while another task is being previewed this screen is about that task,
    // and whatever is counting down elsewhere is none of its business.
    val session = uiState.screenSession
    val entry = uiState.sessionEntry
    val currentOnBack by rememberUpdatedState(onBack)

    // The screen is about one task, latched the first time it hears of one. Once that task stops
    // being the subject — its session ended, or it was ticked off — there is nothing left to show,
    // and quietly falling back to whatever else happens to be running would swap the subject under
    // the user's hands.
    //
    // Latched on the first id that arrives rather than on the value at first composition: this
    // destination has its own ViewModel, and the state it reads is filled in by collectors that
    // ViewModel starts in its own init. Reading "nothing yet" as "nothing to show" would close the
    // screen the moment it opened. Raw ids, not the resolved entry, for the same reason — the
    // agenda the task is looked up in loads later still.
    //
    // A key rather than a task id: the subject may be a saved session, with or without a task (#517).
    //
    // A new session has no id until it is saved, so the key it was latched under is a stand-in:
    // once it is saved and still the subject, the screen moves on to its real one. That is the one
    // case where the subject is the same thing under a new name rather than a different thing.
    val subject = rememberSaveable { mutableStateOf<String?>(null) }
    val latched =
        latchedSessionSubject(
            latched = subject.value,
            current = uiState.sessionSubjectKey,
            setupSessionId = uiState.setupSessionId,
            startedSessionId = uiState.startedNewSessionId,
        )
    subject.value = nextSessionSubject(placement == SheetPlacement.DockedPane, latched, uiState.sessionSubjectKey)
    val subjectKey = subject.value
    val hasSomethingToShow = subjectKey != null && uiState.sessionSubjectKey == subjectKey
    LaunchedEffect(subjectKey, hasSomethingToShow) {
        if (subjectKey != null && !hasSomethingToShow) currentOnBack()
    }

    // The preview is this screen's own state: keeping it alive after leaving would make the next
    // visit open on a task the user did not pick.
    DisposableEffect(Unit) {
        onDispose { viewModel.onEvent(FocusContract.UiEvent.ClearSessionPreview) }
    }

    uiState.pendingStart?.let { pending ->
        ReplaceSessionDialog(pending = pending, onEvent = viewModel::onEvent)
    }

    val onEvent: (FocusContract.UiEvent) -> Unit = { event ->
        val editTask = event as? FocusContract.UiEvent.EditTask
        if (onEditTask != null && editTask != null && handsTaskToPane(uiState.setupSessionId)) {
            onEditTask(editTask.task)
        } else {
            viewModel.onEvent(event)
        }
    }

    uiState.taskEditor?.let { target ->
        FocusTaskEditor(
            target = target,
            onDismiss = { viewModel.onEvent(FocusContract.UiEvent.DismissEditor) },
            // Only reached as a sheet: a docked session hands its editor to Focus's pane.
            placement = SheetPlacement.BottomSheet,
            dismissRequestKey = 0,
        )
    }

    if (hasSomethingToShow) {
        FocusSessionSheet(
            uiState = uiState,
            onEvent = onEvent,
            onDismiss = currentOnBack,
            placement = placement,
            modifier = modifier,
        )
    }
}

/**
 * The session, as a bottom sheet opened to its full height, or docked beside the Focus agenda on a
 * large window.
 *
 * A sheet rather than a slide-in screen of its own. Everything else in this app that shows you one
 * item — a task, a habit, the length of a break — arrives as a sheet from the bottom, and the
 * session arriving from the side made it the one surface with its own way of appearing.
 *
 * The one surface a session has (#517): a task, a saved session and a new session all open here,
 * are set up here, and run here. Its heading is the task's title, or the session's name — typed in
 * place while a saved session is being set up.
 */
@Composable
fun FocusSessionSheet(
    uiState: FocusContract.UiState,
    onEvent: (FocusContract.UiEvent) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    placement: SheetPlacement = SheetPlacement.BottomSheet,
) {
    val session = uiState.screenSession
    // Only while it has not started: once running it is a timer like any other.
    val target = uiState.sessionSetup.takeIf { session == null }
    val form =
        rememberSessionEditorForm(
            subjectKey = uiState.sessionSubjectKey,
            target = uiState.sessionSetup,
            defaultFocusMinutes = uiState.defaultSessionLengthMinutes,
            defaultBreakMinutes = uiState.breakLengthMinutes,
        )
    val dialogs = remember { SessionSetupDialogState() }

    TempoModalBottomSheet(
        // A saved session keeps what was changed as the sheet closes, as an existing task does. A
        // new one that was never confirmed is not created by closing the sheet on it.
        onDismissRequest = {
            if (target != null && form.savesOnClose) onEvent(FocusContract.UiEvent.SaveSession(form.closingDraft))
            onDismiss()
        },
        modifier = modifier,
        hasUnsavedChanges = target?.isNew == true && form.canSave,
        placement = placement,
    ) { onRequestDismiss ->
        // The task the session is for: the one it is running on or was opened from, or the one
        // just chosen for a session still being set up.
        val linked = if (target != null) uiState.taskEntry(form.linkedTaskId) else uiState.sessionEntry
        val topRoom = if (placement == SheetPlacement.DockedPane) DockedTitleTopPadding else 0.dp
        val setup = target?.let { sessionSetupActions(it, form, dialogs, onEvent, onRequestDismiss) }
        SessionBody(
            session = session,
            // Docked there is no drag handle above the sheet, so it takes the room the docked task
            // editor leaves above its own title (#502).
            modifier = Modifier.padding(top = topRoom),
            heading = {
                SessionSheetHeading(
                    title = session?.title ?: linked?.task?.title.orEmpty(),
                    form = form.takeIf { target != null },
                )
            },
            form = form,
            focusToday = linked?.focusToday ?: TaskFocusToday(),
            subtasks = linked?.subtasks.orEmpty(),
            task = linked?.task,
            categoryName = linked?.categoryName,
            actions =
                sessionBodyActions(
                    uiState = uiState,
                    form = form,
                    target = target,
                    onEvent = onEvent,
                    link = sessionLinkActions(uiState, form, target, dialogs, onEvent),
                    setupActions = setup,
                ),
        )
    }

    if (target != null) {
        SessionSetupDialogs(
            state = dialogs,
            target = target,
            form = form,
            linkableTasks = uiState.linkableTasks,
            onEvent = onEvent,
        )
    }
}

/** What only a saved session being set up can do: save, back out, delete. */
private fun sessionSetupActions(
    target: FocusContract.SessionEditorTarget,
    form: SessionEditorForm,
    dialogs: SessionSetupDialogState,
    onEvent: (FocusContract.UiEvent) -> Unit,
    onCancel: () -> Unit,
): SessionSetupActions =
    SessionSetupActions(
        onSave = { onEvent(FocusContract.UiEvent.SaveSession(form.draft)) },
        onCancel = onCancel,
        // One not saved yet has nothing to delete; backing out of it is the same thing.
        onDelete = { dialogs.isConfirmingDelete = true }.takeIf { !target.isNew },
    )

/** Which of a session's two questions is being asked: which task it is for, or whether to delete it. */
@Stable
private class SessionSetupDialogState {
    var isPickingTask by mutableStateOf(false)
    var isConfirmingDelete by mutableStateOf(false)
}

/** The task picker and the delete confirmation, over the sheet that asked for them. */
@Composable
private fun SessionSetupDialogs(
    state: SessionSetupDialogState,
    target: FocusContract.SessionEditorTarget,
    form: SessionEditorForm,
    linkableTasks: ImmutableList<Task>,
    onEvent: (FocusContract.UiEvent) -> Unit,
) {
    if (state.isPickingTask) {
        LinkTaskSheet(
            tasks = linkableTasks,
            selectedTaskId = form.linkedTaskId,
            onPick = { task ->
                form.linkedTaskId = task.id
                state.isPickingTask = false
            },
            onDismiss = { state.isPickingTask = false },
        )
    }
    target.session?.takeIf { state.isConfirmingDelete }?.let { saved ->
        DeleteSessionDialog(
            session = saved,
            onConfirm = {
                state.isConfirmingDelete = false
                onEvent(FocusContract.UiEvent.DeleteSession(saved.id))
            },
            onCancel = { state.isConfirmingDelete = false },
        )
    }
}

/**
 * What the body's buttons do, for whichever subject the sheet has.
 *
 * Start is where the two kinds of subject differ: a saved session is saved as it stands and run,
 * and one with no name yet is asked for one instead; a task is run at the lengths and rounds set,
 * for this run alone.
 */
private fun sessionBodyActions(
    uiState: FocusContract.UiState,
    form: SessionEditorForm,
    target: FocusContract.SessionEditorTarget?,
    onEvent: (FocusContract.UiEvent) -> Unit,
    link: SessionLinkActions,
    setupActions: SessionSetupActions?,
): SessionBodyActions =
    SessionBodyActions(
        onStart = {
            when {
                target == null -> onEvent(form.startOnTask())
                form.canSave -> onEvent(FocusContract.UiEvent.SaveSession(form.draft, startNow = true))
                else -> form.showsTitleError = true
            }
        },
        onPauseResume = { uiState.screenSession?.let { onEvent(it.pauseResumeEvent()) } },
        onStop = { onEvent(FocusContract.UiEvent.StopSession) },
        onComplete = { onEvent(FocusContract.UiEvent.CompleteSessionTask) }.takeIf { uiState.canCompleteScreenTask },
        onBackToWork = { onEvent(FocusContract.UiEvent.BackToWork) },
        onToggleSubtask = { onEvent(FocusContract.UiEvent.ToggleTaskCompletion(it)) },
        onEditSubtask = { onEvent(FocusContract.UiEvent.EditTask(it)) },
        link = link,
        setup = setupActions,
    )

/**
 * What the linked-task row does, for whichever subject the sheet has.
 *
 * The chip always opens the task, in its own sheet over this one. Before the session starts the
 * link can always be taken off: a session being set up simply forgets it, and a sheet opened on a
 * task becomes a new session without one. Only a session being set up can pick a task — a sheet
 * opened on a task already has it. Once running, the task is what the timer is on.
 */
private fun sessionLinkActions(
    uiState: FocusContract.UiState,
    form: SessionEditorForm,
    target: FocusContract.SessionEditorTarget?,
    dialogs: SessionSetupDialogState,
    onEvent: (FocusContract.UiEvent) -> Unit,
): SessionLinkActions {
    val isRunning = uiState.screenSession != null
    return SessionLinkActions(
        onOpenTask = { onEvent(FocusContract.UiEvent.EditTask(it)) },
        onPick = { dialogs.isPickingTask = true }.takeIf { target != null },
        onUnlink =
            when {
                isRunning -> null
                target != null -> ({ form.linkedTaskId = null })
                else -> ({ onEvent(FocusContract.UiEvent.UnlinkPreviewTask) })
            },
    )
}

/**
 * The sheet's heading: the name field while a saved session is being set up, otherwise the title of
 * what is running or being looked at. The two cross over in place when a session starts.
 *
 * Start-aligned and in the style the task and habit editors give their titles, so a session reads
 * as another sheet about one item. It stands beside the dial, in the body's hero, which is what
 * places it.
 *
 * The title and nothing under it. The linked task's description stood here once, and read as the
 * session's own; it is the task's, and the task's sheet — one tap on its chip away — is where it
 * is read. The chip says when there is one.
 */
@Composable
private fun SessionSheetHeading(
    title: String,
    form: SessionEditorForm?,
) {
    AnimatedContent(
        targetState = form != null,
        transitionSpec = { fadeIn() togetherWith fadeOut() },
        label = "session_title",
        modifier = Modifier.fillMaxWidth(),
    ) { isEditable ->
        if (isEditable && form != null) {
            SessionNameField(form = form)
        } else {
            Text(
                text = title,
                style = MaterialTheme.typography.inputTitle,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                // The room the title field keeps round its text, so the heading does not move when
                // a session starts and the field gives way to this.
                modifier = Modifier.fillMaxWidth().padding(vertical = EDITOR_TEXT_FIELD_TOP_INSET),
            )
        }
    }
}

/** How far below the pane's top edge the docked task editor's title starts. */
private val DockedTitleTopPadding = 24.dp
