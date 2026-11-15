package com.mandrecode.tempo.features.focus.presentation.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mandrecode.tempo.R
import com.mandrecode.tempo.core.ui.components.ValueStepper
import com.mandrecode.tempo.core.ui.editor.EDITOR_CONTROL_HEIGHT
import com.mandrecode.tempo.core.ui.editor.EDITOR_SET_ITEM_CORNER_RADIUS
import com.mandrecode.tempo.core.ui.editor.EditorPropertyRow
import com.mandrecode.tempo.core.ui.editor.EditorTitleField
import com.mandrecode.tempo.core.ui.editor.EditorValueChip
import com.mandrecode.tempo.features.focus.domain.model.FocusSession
import com.mandrecode.tempo.features.focus.domain.model.SavedSession
import com.mandrecode.tempo.features.focus.presentation.FocusContract
import com.mandrecode.tempo.features.tasks.domain.model.Task
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
    /** Whether this is a new topic session, named here, rather than a run on a task. */
    val isNew: Boolean,
    title: MutableState<String>,
    focusMinutes: MutableIntState,
    breakMinutes: MutableIntState,
    rounds: MutableIntState,
    showsTitleError: MutableState<Boolean>,
) {
    var title by title
    var focusMinutes by focusMinutes
    var breakMinutes by breakMinutes
    var rounds by rounds
    var showsTitleError by showsTitleError

    val canStart: Boolean get() = title.isNotBlank()

    /** The topic session this form describes, with no id: one of the same name is the same topic. */
    val newSession: SavedSession
        get() =
            SavedSession(
                id = SavedSession.NEW_ID,
                title = title,
                focusMinutes = focusMinutes,
                breakMinutes = breakMinutes,
                rounds = rounds,
            )

    /** Fills the form from a recent session, to run it again as it was or with a change. */
    fun fillFrom(recent: SavedSession) {
        title = recent.title
        focusMinutes = recent.focusMinutes
        breakMinutes = recent.breakMinutes
        rounds = recent.rounds
        showsTitleError = false
    }

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
 * its own. Everything starts from the configured lengths and a single round.
 */
@Composable
internal fun rememberSessionEditorForm(
    subjectKey: String?,
    isNew: Boolean,
    defaultFocusMinutes: Int,
    defaultBreakMinutes: Int,
): SessionEditorForm {
    val title = rememberSaveable(subjectKey) { mutableStateOf("") }
    val focusMinutes = rememberSaveable(subjectKey) { mutableIntStateOf(defaultFocusMinutes) }
    val breakMinutes = rememberSaveable(subjectKey) { mutableIntStateOf(defaultBreakMinutes) }
    val rounds = rememberSaveable(subjectKey) { mutableIntStateOf(1) }
    val showsTitleError = rememberSaveable(subjectKey) { mutableStateOf(false) }
    return remember(subjectKey, isNew) {
        SessionEditorForm(isNew, title, focusMinutes, breakMinutes, rounds, showsTitleError)
    }
}

/**
 * A session's name, typed where a running session's title stands.
 *
 * The editors' own title field, not a field of this sheet's: the same style, the same placeholder
 * treatment, the same error line, and Enter leaving the field rather than breaking the name in
 * two. A new session opens here with the keyboard up, as a new task does.
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
 * The task a session is on, as a link to the task itself: a chip naming it, with what it carries
 * on a second line, that opens the task in its own sheet over this one. The task is right there to
 * be read or changed without leaving the session.
 *
 * Only a session opened from a task, or running on one, has it. A topic session stands alone.
 */
@Composable
internal fun LinkedTaskRow(
    linkedTask: Task?,
    onOpen: (Task) -> Unit,
    modifier: Modifier = Modifier,
    /** The chip's second line: what the linked task carries. */
    supporting: String? = null,
) {
    if (linkedTask == null) return
    EditorPropertyRow(
        iconPainter = painterResource(R.drawable.ic_checklist),
        iconContentDescription = stringResource(R.string.session_editor_linked_task),
        modifier = modifier,
    ) {
        // What the task carries rides inside the chip, as its second line: it starts where the
        // task's name does, because it is part of the same thing.
        EditorValueChip(
            label = linkedTask.title,
            onClick = { onOpen(linkedTask) },
            onClear = null,
            clearContentDescription = "",
            supporting = supporting,
            // The task's notes are read in the task's own sheet, which this chip opens; here it is
            // enough to say that there are some.
            supportingIcon = painterResource(R.drawable.ic_short_text).takeIf { linkedTask.description.isNotBlank() },
            supportingIconDescription = stringResource(R.string.session_linked_task_has_description),
            modifier = Modifier.weight(1f, fill = false),
        )
    }
}

/**
 * The topic sessions started lately, to run one again: a tap fills the form with its name, lengths
 * and rounds, and Start runs it. The list keeps itself short — the oldest drops off as new ones
 * are started — so there is nothing here to manage.
 */
@Composable
internal fun RecentSessionsRow(
    sessions: ImmutableList<SavedSession>,
    selectedTitle: String,
    onPick: (SavedSession) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (sessions.isEmpty()) return
    val haptic = LocalHapticFeedback.current
    EditorPropertyRow(
        iconPainter = painterResource(R.drawable.ic_history),
        iconContentDescription = stringResource(R.string.session_editor_recent),
        modifier = modifier,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier =
                Modifier
                    .weight(1f)
                    .padding(start = RowContentInset)
                    .horizontalScroll(rememberScrollState())
                    .testTag(RECENT_SESSIONS_ROW_TEST_TAG),
        ) {
            sessions.forEach { recent ->
                FilterChip(
                    selected = recent.title.trim().equals(selectedTitle.trim(), ignoreCase = true),
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onPick(recent)
                    },
                    label = { Text(text = recent.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    shape = RoundedCornerShape(EDITOR_SET_ITEM_CORNER_RADIUS),
                    modifier = Modifier.height(EDITOR_CONTROL_HEIGHT),
                )
            }
        }
    }
}

internal const val RECENT_SESSIONS_ROW_TEST_TAG = "recent_sessions_row"

/** The gap between rows, as the task and habit editors space theirs. */
private val RowSpacing = 12.dp

/**
 * Where every row's content starts, past its icon: the stepper rows' names and the linked task's
 * control. One value — the chip's own start inset — so the rows share a start line.
 */
private val RowContentInset = 4.dp
