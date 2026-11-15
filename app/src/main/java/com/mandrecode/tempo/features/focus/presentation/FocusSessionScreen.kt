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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
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
import com.mandrecode.tempo.features.focus.presentation.components.RecentSessionsRow
import com.mandrecode.tempo.features.focus.presentation.components.ReplaceSessionDialog
import com.mandrecode.tempo.features.focus.presentation.components.SessionBody
import com.mandrecode.tempo.features.focus.presentation.components.SessionBodyActions
import com.mandrecode.tempo.features.focus.presentation.components.SessionEditorForm
import com.mandrecode.tempo.features.focus.presentation.components.SessionNameField
import com.mandrecode.tempo.features.focus.presentation.components.rememberSessionEditorForm
import com.mandrecode.tempo.features.tasks.domain.model.Task

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
    // A key rather than a task id: the subject may be a topic session, with no task (#517).
    //
    // A new session has no id until it starts, so the key it was latched under is a stand-in: once
    // it is running, the screen moves on to its real one. That is the one case where the subject
    // is the same thing under a new name rather than a different thing.
    val subject = rememberSaveable { mutableStateOf<String?>(null) }
    val latched =
        latchedSessionSubject(
            latched = subject.value,
            current = uiState.sessionSubjectKey,
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
 * The session, as a bottom sheet as tall as what it holds, or docked beside the Focus agenda on a
 * large window.
 *
 * A sheet rather than a slide-in screen of its own. Everything else in this app that shows you one
 * item — a task, a habit, the length of a break — arrives as a sheet from the bottom.
 *
 * The one surface a session has (#517): a task and a new topic session both open here, are set up
 * here, and run here. Its heading is the task's title, or the topic's name — typed in place while
 * a new one is being set up, with the recent ones offered to run again.
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
    val isSettingUp = uiState.isSettingUpSession && session == null
    val form =
        rememberSessionEditorForm(
            subjectKey = uiState.sessionSubjectKey,
            isNew = uiState.isSettingUpSession,
            defaultFocusMinutes = uiState.defaultSessionLengthMinutes,
            defaultBreakMinutes = uiState.breakLengthMinutes,
        )

    TempoModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = modifier,
        // A new session that was never started is not created by closing the sheet on it, so a
        // name typed and left is asked about first.
        hasUnsavedChanges = isSettingUp && form.canStart,
        placement = placement,
    ) { _ ->
        val linked = uiState.sessionEntry
        val topRoom = if (placement == SheetPlacement.DockedPane) DockedTitleTopPadding else 0.dp
        SessionBody(
            session = session,
            // Docked there is no drag handle above the sheet, so it takes the room the docked task
            // editor leaves above its own title (#502).
            modifier = Modifier.padding(top = topRoom),
            heading = {
                SessionSheetHeading(
                    title = session?.title ?: linked?.task?.title.orEmpty(),
                    form = form.takeIf { isSettingUp },
                )
            },
            form = form,
            focusToday = linked?.focusToday ?: TaskFocusToday(),
            subtasks = linked?.subtasks.orEmpty(),
            task = linked?.task,
            categoryName = linked?.categoryName,
            setupExtras =
                if (isSettingUp && uiState.recentSessions.isNotEmpty()) {
                    {
                        RecentSessionsRow(
                            sessions = uiState.recentSessions,
                            selectedTitle = form.title,
                            onPick = form::fillFrom,
                        )
                    }
                } else {
                    null
                },
            actions = sessionBodyActions(uiState, form, isSettingUp, onEvent),
        )
    }
}

/**
 * What the body's buttons do, for whichever subject the sheet has.
 *
 * Start is where the two kinds of subject differ: a new topic session is started and remembered,
 * and one with no name yet is asked for one instead; a task is run at the lengths and rounds set,
 * for this run alone.
 */
private fun sessionBodyActions(
    uiState: FocusContract.UiState,
    form: SessionEditorForm,
    isSettingUp: Boolean,
    onEvent: (FocusContract.UiEvent) -> Unit,
): SessionBodyActions =
    SessionBodyActions(
        onStart = {
            when {
                !isSettingUp -> onEvent(form.startOnTask())
                form.canStart -> onEvent(FocusContract.UiEvent.StartNewSession(form.newSession))
                else -> form.showsTitleError = true
            }
        },
        onPauseResume = { uiState.screenSession?.let { onEvent(it.pauseResumeEvent()) } },
        onStop = { onEvent(FocusContract.UiEvent.StopSession) },
        onComplete = { onEvent(FocusContract.UiEvent.CompleteSessionTask) }.takeIf { uiState.canCompleteScreenTask },
        onBackToWork = { onEvent(FocusContract.UiEvent.BackToWork) },
        onToggleSubtask = { onEvent(FocusContract.UiEvent.ToggleTaskCompletion(it)) },
        onEditSubtask = { onEvent(FocusContract.UiEvent.EditTask(it)) },
        onOpenTask = { onEvent(FocusContract.UiEvent.EditTask(it)) },
    )

/**
 * The sheet's heading: the name field while a new session is being set up, otherwise the title of
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
