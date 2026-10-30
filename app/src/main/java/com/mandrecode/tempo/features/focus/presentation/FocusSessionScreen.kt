package com.mandrecode.tempo.features.focus.presentation

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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mandrecode.tempo.core.ui.adaptive.SheetPlacement
import com.mandrecode.tempo.core.ui.components.TempoModalBottomSheet
import com.mandrecode.tempo.core.ui.theme.inputTitle
import com.mandrecode.tempo.features.focus.domain.model.FocusSession
import com.mandrecode.tempo.features.focus.domain.model.TaskFocusToday
import com.mandrecode.tempo.features.focus.presentation.components.ReplaceSessionDialog
import com.mandrecode.tempo.features.focus.presentation.components.SessionBody
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
    onOpenTaskInTasks: (Long) -> Unit,
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
    val subject = rememberSaveable { mutableStateOf<Long?>(null) }
    subject.value = subject.value ?: uiState.sessionTaskId
    val subjectTaskId = subject.value
    val hasSomethingToShow = subjectTaskId != null && uiState.sessionTaskId == subjectTaskId
    LaunchedEffect(subjectTaskId, hasSomethingToShow) {
        if (subjectTaskId != null && !hasSomethingToShow) currentOnBack()
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
        if (onEditTask != null && editTask != null) onEditTask(editTask.task) else viewModel.onEvent(event)
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
            session = session,
            title = session?.taskTitle ?: entry?.task?.title.orEmpty(),
            uiState = uiState,
            onEvent = onEvent,
            onDismiss = currentOnBack,
            onOpenTaskInTasks = onOpenTaskInTasks,
            placement = placement,
            modifier = modifier,
        )
    }
}

/**
 * The running session, as a bottom sheet opened to its full height, or docked beside the Focus
 * agenda on a large window.
 *
 * A sheet rather than a slide-in screen of its own. Everything else in this app that shows you one
 * item — a task, a habit, the length of a break — arrives as a sheet from the bottom, and the
 * session arriving from the side made it the one surface with its own way of appearing. The task
 * title heads it, because while a session runs that task *is* the subject.
 */
@Composable
fun FocusSessionSheet(
    session: FocusSession?,
    title: String,
    uiState: FocusContract.UiState,
    onEvent: (FocusContract.UiEvent) -> Unit,
    onDismiss: () -> Unit,
    onOpenTaskInTasks: (Long) -> Unit,
    modifier: Modifier = Modifier,
    placement: SheetPlacement = SheetPlacement.BottomSheet,
) {
    TempoModalBottomSheet(onDismissRequest = onDismiss, modifier = modifier, placement = placement) { _ ->
        val task = uiState.sessionEntry?.task

        // The style the task and habit editors give their titles, so a session reads as another
        // sheet about one item rather than a screen with its own heading.
        Text(
            text = title,
            style = MaterialTheme.typography.inputTitle,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth().padding(horizontal = SheetTitlePadding),
        )

        SessionBody(
            session = session,
            plannedLength =
                session?.plannedLength
                    ?: FocusSession.lengthOf(uiState.defaultSessionLengthMinutes),
            focusToday = uiState.sessionEntry?.focusToday ?: TaskFocusToday(),
            subtasks = uiState.sessionSubtasks,
            task = task,
            categoryName = uiState.sessionEntry?.categoryName,
            onStart = { minutes -> onEvent(FocusContract.UiEvent.StartSession(minutes)) },
            onPauseResume = {
                onEvent(
                    if (session?.isPaused != false) {
                        FocusContract.UiEvent.ResumeSession
                    } else {
                        FocusContract.UiEvent.PauseSession
                    },
                )
            },
            onStop = { onEvent(FocusContract.UiEvent.StopSession) },
            onComplete = { onEvent(FocusContract.UiEvent.CompleteSessionTask) },
            onBackToWork = { onEvent(FocusContract.UiEvent.BackToWork) },
            onToggleSubtask = { onEvent(FocusContract.UiEvent.ToggleTaskCompletion(it)) },
            onEditSubtask = { onEvent(FocusContract.UiEvent.EditTask(it)) },
            onOpenInTasks = { task?.let { onOpenTaskInTasks(it.id) } },
        )
    }
}

private val SheetTitlePadding = 16.dp
