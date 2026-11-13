package com.mandrecode.tempo.features.focus.presentation.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mandrecode.tempo.R
import com.mandrecode.tempo.core.ui.adaptive.adaptiveHorizontalMargin
import com.mandrecode.tempo.core.ui.components.TempoConfirmDialog
import com.mandrecode.tempo.core.ui.components.TempoModalBottomSheet
import com.mandrecode.tempo.core.ui.components.ValueStepper
import com.mandrecode.tempo.core.ui.editor.EDITOR_TEXT_FIELD_BOTTOM_INSET
import com.mandrecode.tempo.core.ui.editor.EditorAddButton
import com.mandrecode.tempo.core.ui.editor.EditorBottomSheetFooter
import com.mandrecode.tempo.core.ui.editor.EditorPropertyRow
import com.mandrecode.tempo.core.ui.editor.EditorTitleField
import com.mandrecode.tempo.core.ui.editor.EditorValueChip
import com.mandrecode.tempo.core.ui.theme.subtaskTitle
import com.mandrecode.tempo.features.focus.domain.model.FocusSession
import com.mandrecode.tempo.features.focus.domain.model.SavedSession
import com.mandrecode.tempo.features.focus.presentation.FocusContract
import com.mandrecode.tempo.features.tasks.domain.model.Task
import com.mandrecode.tempo.features.tasks.presentation.components.sections.TasksSheetHeader
import com.mandrecode.tempo.features.tasks.presentation.components.sections.TasksSheetRow
import com.mandrecode.tempo.features.tasks.presentation.components.sections.TasksSheetRowSpacing
import kotlinx.collections.immutable.ImmutableList

internal const val SESSION_EDITOR_TITLE_FIELD_TEST_TAG = "session_editor_title_field"

/**
 * The session sheet: a new saved session, or one being changed (#517).
 *
 * Built from the task and habit editors' own parts — their title field, their icon-led rows, their
 * footer — so a session is made the way everything else in the app is made. What it asks for is
 * shorter than either: a name, how long to focus, how long to rest, how many times, and optionally
 * the task it is for.
 *
 * A new session is saved by its button. One that exists saves as the sheet closes, the way an
 * existing task does, and its footer offers deleting it instead.
 */
@Composable
internal fun SessionEditorSheet(
    target: FocusContract.SessionEditorTarget,
    linkableTasks: ImmutableList<Task>,
    defaultFocusMinutes: Int,
    defaultBreakMinutes: Int,
    onEvent: (FocusContract.UiEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val form = rememberSessionEditorForm(target, defaultFocusMinutes, defaultBreakMinutes)
    var isPickingTask by rememberSaveable(form.sessionId) { mutableStateOf(false) }
    var isConfirmingDelete by rememberSaveable(form.sessionId) { mutableStateOf(false) }

    TempoModalBottomSheet(
        // An existing session keeps what was changed, as an existing task does. A new one that was
        // never confirmed is not created by closing the sheet on it.
        onDismissRequest = {
            onEvent(
                if (form.savesOnClose) {
                    FocusContract.UiEvent.SaveSession(form.closingDraft)
                } else {
                    FocusContract.UiEvent.DismissSessionEditor
                },
            )
        },
        modifier = modifier,
        hasUnsavedChanges = form.isNew && form.canSave,
    ) { onRequestDismiss ->
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = adaptiveHorizontalMargin())
                    .padding(top = 8.dp, bottom = 32.dp),
        ) {
            SessionEditorFields(
                form = form,
                linkedTask = form.linkedTask(linkableTasks, target.linkedTask),
                onPickTask = { isPickingTask = true },
            )
            SessionEditorActions(
                form = form,
                onEvent = onEvent,
                onDelete = { isConfirmingDelete = true },
                onRequestDismiss = onRequestDismiss,
            )
        }
    }

    if (isPickingTask) {
        LinkTaskSheet(
            tasks = linkableTasks,
            selectedTaskId = form.linkedTaskId,
            onPick = { task ->
                form.linkedTaskId = task.id
                isPickingTask = false
            },
            onDismiss = { isPickingTask = false },
        )
    }

    target.session?.takeIf { isConfirmingDelete }?.let { session ->
        DeleteSessionDialog(
            session = session,
            onConfirm = {
                isConfirmingDelete = false
                onEvent(FocusContract.UiEvent.DeleteSession(session.id))
            },
            onCancel = { isConfirmingDelete = false },
        )
    }
}

/** Asked on [TempoConfirmDialog] in its destructive dress, as deleting a task is. */
@Composable
private fun DeleteSessionDialog(
    session: SavedSession,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    TempoConfirmDialog(
        title = stringResource(R.string.delete_session_title),
        confirmLabel = stringResource(R.string.delete),
        onConfirm = onConfirm,
        onCancel = onCancel,
        text = { Text(stringResource(R.string.delete_session_message, session.title)) },
    )
}

/**
 * What is being typed into the session sheet.
 *
 * Held here rather than in the view model, the way the task and habit editors hold their drafts: a
 * text field fed from a state flow loses its cursor to every round trip.
 */
@Stable
internal class SessionEditorForm(
    private val original: SavedSession?,
    title: MutableState<String>,
    focusMinutes: MutableIntState,
    breakMinutes: MutableIntState,
    rounds: MutableIntState,
    linkedTaskId: MutableState<Long?>,
    showsTitleError: MutableState<Boolean>,
) {
    var title by title
    var focusMinutes by focusMinutes
    var breakMinutes by breakMinutes
    var rounds by rounds
    var linkedTaskId by linkedTaskId
    var showsTitleError by showsTitleError

    val sessionId: Long get() = original?.id ?: 0L
    val isNew: Boolean get() = original == null
    val canSave: Boolean get() = title.isNotBlank()

    val draft: FocusContract.SessionDraft
        get() =
            FocusContract.SessionDraft(
                id = sessionId,
                title = title,
                focusMinutes = focusMinutes,
                breakMinutes = breakMinutes,
                rounds = rounds,
                taskId = linkedTaskId,
            )

    /**
     * What closing the sheet saves. A name rubbed out and left empty goes back to what it was
     * rather than taking the other changes down with it.
     */
    val closingDraft: FocusContract.SessionDraft
        get() = draft.copy(title = title.ifBlank { original?.title.orEmpty() })

    /** Whether closing the sheet should save: an existing session that has been changed. */
    val savesOnClose: Boolean
        get() = original != null && closingDraft.toSavedSession().normalized() != original

    /**
     * The chosen task, from the list when it is there. The list loads a beat after the sheet
     * opens, and until then the task the session came with stands in for itself.
     */
    fun linkedTask(
        linkableTasks: List<Task>,
        originalTask: Task?,
    ): Task? {
        val id = linkedTaskId ?: return null
        return linkableTasks.firstOrNull { it.id == id } ?: originalTask?.takeIf { it.id == id }
    }
}

@Composable
private fun rememberSessionEditorForm(
    target: FocusContract.SessionEditorTarget,
    defaultFocusMinutes: Int,
    defaultBreakMinutes: Int,
): SessionEditorForm {
    val original = target.session
    val key = original?.id ?: 0L
    val title = rememberSaveable(key) { mutableStateOf(original?.title.orEmpty()) }
    val focusMinutes = rememberSaveable(key) { mutableIntStateOf(original?.focusMinutes ?: defaultFocusMinutes) }
    val breakMinutes = rememberSaveable(key) { mutableIntStateOf(original?.breakMinutes ?: defaultBreakMinutes) }
    val rounds = rememberSaveable(key) { mutableIntStateOf(original?.rounds ?: 1) }
    val linkedTaskId = rememberSaveable(key) { mutableStateOf(target.linkedTask?.id) }
    val showsTitleError = rememberSaveable(key) { mutableStateOf(false) }
    return remember(key) {
        SessionEditorForm(original, title, focusMinutes, breakMinutes, rounds, linkedTaskId, showsTitleError)
    }
}

/** The name, the three numbers and the linked task. */
@Composable
private fun ColumnScope.SessionEditorFields(
    form: SessionEditorForm,
    linkedTask: Task?,
    onPickTask: () -> Unit,
) {
    // A new session starts at its name, with the keyboard up, as a new task does. One being
    // changed opens at rest: it is as likely the numbers that are wanted.
    val titleFocus = remember { FocusRequester() }
    LaunchedEffect(form.isNew) {
        if (form.isNew) titleFocus.requestFocus()
    }
    EditorTitleField(
        value = form.title,
        onValueChange = {
            form.title = it
            if (it.isNotBlank()) form.showsTitleError = false
        },
        placeholder = stringResource(R.string.session_editor_title_placeholder),
        error = stringResource(R.string.task_title_required).takeIf { form.showsTitleError },
        besideCheckbox = false,
        fieldModifier = Modifier.testTag(SESSION_EDITOR_TITLE_FIELD_TEST_TAG).focusRequester(titleFocus),
    )
    Spacer(modifier = Modifier.height(RowSpacing - EDITOR_TEXT_FIELD_BOTTOM_INSET + TitleGap))

    SessionLengthRows(
        focusMinutes = form.focusMinutes,
        breakMinutes = form.breakMinutes,
        rounds = form.rounds,
        onFocusMinutesChange = { form.focusMinutes = it },
        onBreakMinutesChange = { form.breakMinutes = it },
        onRoundsChange = { form.rounds = it },
    )

    Spacer(modifier = Modifier.height(RowSpacing))
    LinkedTaskRow(
        linkedTask = linkedTask,
        onPick = onPickTask,
        onClear = { form.linkedTaskId = null },
    )
}

/**
 * Starting, and the editors' own footer beneath it.
 *
 * Start is the session screen's filled button, saying the length it will run for: saving a session
 * and running it is one thought as often as it is two. Pressed with no name it asks for one rather
 * than doing nothing.
 */
@Composable
private fun ColumnScope.SessionEditorActions(
    form: SessionEditorForm,
    onEvent: (FocusContract.UiEvent) -> Unit,
    onDelete: () -> Unit,
    onRequestDismiss: () -> Unit,
) {
    SessionActionButton(
        action =
            SessionAction(
                label = stringResource(R.string.focus_session_start, sessionLengthLabel(form.focusMinutes)),
                icon = painterResource(R.drawable.ic_play_arrow),
                emphasis = ButtonEmphasis.FILLED,
                onClick = {
                    if (form.canSave) {
                        onEvent(FocusContract.UiEvent.SaveSession(form.draft, startNow = true))
                    } else {
                        form.showsTitleError = true
                    }
                },
            ),
        colors = sessionScreenActionColors(),
        modifier = Modifier.padding(top = 28.dp),
    )

    EditorBottomSheetFooter(
        hasDeleteAction = !form.isNew,
        deleteLabel = stringResource(R.string.delete_session),
        onDelete = onDelete,
        autoSaveEnabled = false,
        confirmEnabled = form.canSave,
        confirmLabel = stringResource(R.string.add_session),
        onRequestDismiss = onRequestDismiss,
        onConfirmClick = { onEvent(FocusContract.UiEvent.SaveSession(form.draft)) },
    )
}

/** Focus, rest and rounds: three rows of the same shape, each a named number with its stepper. */
@Composable
private fun SessionLengthRows(
    focusMinutes: Int,
    breakMinutes: Int,
    rounds: Int,
    onFocusMinutesChange: (Int) -> Unit,
    onBreakMinutesChange: (Int) -> Unit,
    onRoundsChange: (Int) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(RowSpacing)) {
        StepperRow(
            iconRes = R.drawable.ic_timer,
            name = stringResource(R.string.session_editor_focus_time),
        ) {
            ValueStepper(
                value = focusMinutes,
                label = sessionLengthLabel(focusMinutes),
                onValueChange = onFocusMinutesChange,
                range = FocusSession.SESSION_LENGTH_RANGE,
                // In fives, like the quick adjust on the session screen: lengths as people say them.
                step = FocusSession.LENGTH_STEP_MINUTES,
                decreaseDescription = stringResource(R.string.focus_session_length_decrease),
                increaseDescription = stringResource(R.string.focus_session_length_increase),
            )
        }
        StepperRow(
            iconRes = R.drawable.ic_coffee,
            name = stringResource(R.string.session_editor_rest_time),
        ) {
            ValueStepper(
                value = breakMinutes,
                label = sessionLengthLabel(breakMinutes),
                onValueChange = onBreakMinutesChange,
                // By the minute: a rest is short, and the difference between three and five is the
                // whole of it.
                range = FocusSession.BREAK_LENGTH_RANGE,
                decreaseDescription = stringResource(R.string.session_editor_rest_decrease),
                increaseDescription = stringResource(R.string.session_editor_rest_increase),
            )
        }
        StepperRow(
            iconRes = R.drawable.ic_repeat,
            name = stringResource(R.string.session_editor_rounds),
        ) {
            ValueStepper(
                value = rounds,
                label = rounds.toString(),
                onValueChange = onRoundsChange,
                range = FocusSession.ROUNDS_RANGE,
                decreaseDescription = stringResource(R.string.session_editor_rounds_decrease),
                increaseDescription = stringResource(R.string.session_editor_rounds_increase),
            )
        }
    }
}

@Composable
private fun StepperRow(
    iconRes: Int,
    name: String,
    stepper: @Composable () -> Unit,
) {
    EditorPropertyRow(
        iconPainter = painterResource(iconRes),
        // The name is written beside it.
        iconContentDescription = null,
    ) {
        Text(
            text = name,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
        stepper()
    }
}

/**
 * The task the session is for: the editor's Add button until one is chosen, then a chip that can be
 * tapped to change it or cleared — the way a reminder is set on a task.
 */
@Composable
private fun LinkedTaskRow(
    linkedTask: Task?,
    onPick: () -> Unit,
    onClear: () -> Unit,
) {
    EditorPropertyRow(
        iconPainter = painterResource(R.drawable.ic_checklist),
        iconContentDescription = stringResource(R.string.session_editor_linked_task),
    ) {
        if (linkedTask == null) {
            EditorAddButton(
                label = stringResource(R.string.session_editor_link_task),
                onClick = onPick,
            )
        } else {
            EditorValueChip(
                label = linkedTask.title,
                onClick = onPick,
                onClear = onClear,
                clearContentDescription = stringResource(R.string.session_editor_unlink_task),
                modifier = Modifier.weight(1f, fill = false),
            )
        }
    }
}

/**
 * Picks the task a session is for, from the open ones.
 *
 * The sort sheet's header and rows: choosing one of a list is the same act here as there.
 */
@Composable
private fun LinkTaskSheet(
    tasks: ImmutableList<Task>,
    selectedTaskId: Long?,
    onPick: (Task) -> Unit,
    onDismiss: () -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    TempoModalBottomSheet(onDismissRequest = onDismiss) { _ ->
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = 32.dp),
        ) {
            TasksSheetHeader(title = stringResource(R.string.session_editor_link_task))

            if (tasks.isEmpty()) {
                Text(
                    text = stringResource(R.string.session_editor_no_tasks),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = adaptiveHorizontalMargin()).padding(top = 8.dp),
                )
            }

            Column(
                verticalArrangement = Arrangement.spacedBy(TasksSheetRowSpacing),
                modifier = Modifier.padding(horizontal = adaptiveHorizontalMargin()).padding(top = 8.dp),
            ) {
                tasks.forEach { task ->
                    val isSelected = task.id == selectedTaskId
                    val colors = MaterialTheme.colorScheme
                    TasksSheetRow(
                        color = if (isSelected) colors.primaryContainer else colors.surfaceContainerHighest,
                        contentModifier =
                            Modifier.clickable(role = Role.RadioButton) {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onPick(task)
                            },
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_tasks_outlined),
                            contentDescription = null,
                            tint = if (isSelected) colors.onPrimaryContainer else colors.onSurfaceVariant,
                            modifier = Modifier.size(48.dp).padding(12.dp),
                        )
                        Text(
                            text = task.title,
                            style = MaterialTheme.typography.subtaskTitle,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f).padding(vertical = 8.dp),
                        )
                        if (isSelected) {
                            Icon(
                                painter = painterResource(R.drawable.ic_check),
                                contentDescription = null,
                                modifier = Modifier.size(48.dp).padding(12.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

/** The gap between rows, as the task and habit editors space theirs. */
private val RowSpacing = 12.dp

/** What the title's own bottom inset leaves short of a row's gap. */
private val TitleGap = 16.dp
