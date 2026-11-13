package com.mandrecode.tempo.features.focus.presentation.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.Alignment
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
import com.mandrecode.tempo.core.ui.editor.EDITOR_CONTROL_HEIGHT
import com.mandrecode.tempo.core.ui.editor.EditorAddButton
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
 * What is being set on the session screen before it starts.
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

    /** These lengths and rounds as a run on the task the screen is showing, for this run alone. */
    fun startOnTask(): FocusContract.UiEvent.StartSession =
        FocusContract.UiEvent.StartSession(
            lengthMinutes = focusMinutes,
            breakMinutes = breakMinutes,
            rounds = rounds,
        )
}

/**
 * The setup for whatever the session screen is about, keyed by [subjectKey] so each subject keeps
 * its own. A saved session starts from its own values; a new one and a task start from the
 * configured lengths and a single round.
 */
@Composable
internal fun rememberSessionEditorForm(
    subjectKey: String?,
    target: FocusContract.SessionEditorTarget?,
    defaultFocusMinutes: Int,
    defaultBreakMinutes: Int,
): SessionEditorForm {
    val original = target?.session
    val title = rememberSaveable(subjectKey) { mutableStateOf(original?.title.orEmpty()) }
    val focusMinutes =
        rememberSaveable(subjectKey) { mutableIntStateOf(original?.focusMinutes ?: defaultFocusMinutes) }
    val breakMinutes =
        rememberSaveable(subjectKey) { mutableIntStateOf(original?.breakMinutes ?: defaultBreakMinutes) }
    val rounds = rememberSaveable(subjectKey) { mutableIntStateOf(original?.rounds ?: 1) }
    val linkedTaskId = rememberSaveable(subjectKey) { mutableStateOf(target?.linkedTask?.id) }
    val showsTitleError = rememberSaveable(subjectKey) { mutableStateOf(false) }
    return remember(subjectKey, original?.id) {
        SessionEditorForm(original, title, focusMinutes, breakMinutes, rounds, linkedTaskId, showsTitleError)
    }
}

/**
 * A session's name, typed where a running session's title stands.
 *
 * The editors' own title field, not a field of this sheet's: the same style, the same placeholder
 * treatment, the same error line, and Enter leaving the field rather than breaking the name in
 * two. A new session opens here with the keyboard up, as a new task does. One being changed opens
 * at rest: it is as likely the numbers that are wanted.
 */
@Composable
internal fun SessionNameField(
    form: SessionEditorForm,
    modifier: Modifier = Modifier,
) {
    val titleFocus = remember { FocusRequester() }
    LaunchedEffect(form.isNew) {
        if (form.isNew && form.title.isEmpty()) titleFocus.requestFocus()
    }
    // Asked for a name by pressing Start without one: the field takes the cursor, so the request
    // is seen where it has to be answered rather than left at the top of a sheet scrolled past it.
    LaunchedEffect(form.showsTitleError) {
        if (form.showsTitleError) titleFocus.requestFocus()
    }
    EditorTitleField(
        value = form.title,
        onValueChange = {
            form.title = it
            if (it.isNotBlank()) form.showsTitleError = false
        },
        placeholder = stringResource(R.string.session_editor_title_placeholder),
        error = stringResource(R.string.task_title_required).takeIf { form.showsTitleError },
        // The hero has already placed its text on the rows' start line.
        besideCheckbox = true,
        modifier = modifier,
        fieldModifier = Modifier.testTag(SESSION_EDITOR_TITLE_FIELD_TEST_TAG).focusRequester(titleFocus),
    )
}

/**
 * What a session is set to before it starts: how long to focus, how long to rest, how many times.
 *
 * The same rows whether the screen is about a session or a task, in the editors' own shape.
 */
@Composable
internal fun SessionSetupRows(
    form: SessionEditorForm,
    modifier: Modifier = Modifier,
) {
    SessionLengthRows(
        focusMinutes = form.focusMinutes,
        breakMinutes = form.breakMinutes,
        rounds = form.rounds,
        onFocusMinutesChange = { form.focusMinutes = it },
        onBreakMinutesChange = { form.breakMinutes = it },
        onRoundsChange = { form.rounds = it },
        modifier = modifier,
    )
}

/** Asked on [TempoConfirmDialog] in its destructive dress, as deleting a task is. */
@Composable
internal fun DeleteSessionDialog(
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

/** Focus, rest and rounds: three rows of the same shape, each a named number with its stepper. */
@Composable
private fun SessionLengthRows(
    focusMinutes: Int,
    breakMinutes: Int,
    rounds: Int,
    onFocusMinutesChange: (Int) -> Unit,
    onBreakMinutesChange: (Int) -> Unit,
    onRoundsChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(RowSpacing)) {
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
            modifier = Modifier.weight(1f).padding(start = RowContentInset),
        )
        stepper()
    }
}

/**
 * The task a session is for, in one place whatever the session is: the editors' Add button while
 * there is none, then a chip naming it.
 *
 * The chip opens the task itself, in its own sheet over this one — the task is right there to be
 * read or changed without leaving the session. Its cross takes the link off, and that is always
 * on offer before a session starts: a session standing alone is the ordinary case, and a task is
 * something it may be given. To link a different task, take this one off and pick again.
 */
@Composable
internal fun LinkedTaskRow(
    linkedTask: Task?,
    onOpen: (Task) -> Unit,
    /** Opens the picker. `null` when a task cannot be chosen here: the session is running. */
    onPick: (() -> Unit)?,
    /** Takes the link off. `null` once the session is running, when it is the timer's task. */
    onClear: (() -> Unit)?,
    modifier: Modifier = Modifier,
    /** One quiet line under the chip: what the linked task carries. */
    supporting: String? = null,
) {
    if (linkedTask == null && onPick == null) return
    EditorPropertyRow(
        iconPainter = painterResource(R.drawable.ic_checklist),
        iconContentDescription = stringResource(R.string.session_editor_linked_task),
        modifier = modifier,
        // Top-aligned, with the icon given the control's own height to centre in: the row grows
        // downward when the task has something to say, and the icon stays beside the chip.
        verticalAlignment = Alignment.Top,
        iconBoxModifier = Modifier.width(RowIconWidth).height(EDITOR_CONTROL_HEIGHT),
    ) {
        // One column for the control and the line under it, so they start at the same place by
        // construction — the place every other row's content starts.
        Column(modifier = Modifier.weight(1f).padding(start = RowContentInset)) {
            if (linkedTask == null) {
                EditorAddButton(
                    label = stringResource(R.string.session_editor_link_task),
                    onClick = { onPick?.invoke() },
                )
            } else {
                EditorValueChip(
                    label = linkedTask.title,
                    onClick = { onOpen(linkedTask) },
                    onClear = onClear,
                    clearContentDescription = stringResource(R.string.session_editor_unlink_task),
                    // The chip brings an inset of its own, for the editors it was made for; here
                    // the column has already placed it.
                    modifier = Modifier.offset(x = -ChipOwnInset),
                )
            }
            AnimatedVisibility(visible = linkedTask != null && supporting != null) {
                Text(
                    text = supporting.orEmpty(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    // Under the chip's own text, not its edge: the line is the chip's caption, and
                    // a caption starting left of the words it belongs to reads as a row of its own.
                    modifier = Modifier.fillMaxWidth().padding(start = ChipLabelInset, top = 4.dp),
                )
            }
        }
    }
}

/**
 * Picks the task a session is for, from the open ones.
 *
 * The sort sheet's header and rows: choosing one of a list is the same act here as there.
 */
@Composable
internal fun LinkTaskSheet(
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

/** The width the editors give a row's icon. */
private val RowIconWidth = 48.dp

/**
 * Where every row's content starts, past its icon: the stepper rows' names, the linked task's
 * control and the line under it. One value, so the rows share a start line.
 */
private val RowContentInset = 4.dp

/** The start inset `EditorValueChip` applies to itself. */
private val ChipOwnInset = 4.dp

/** How far inside its own edge an input chip starts its label. */
private val ChipLabelInset = 12.dp
