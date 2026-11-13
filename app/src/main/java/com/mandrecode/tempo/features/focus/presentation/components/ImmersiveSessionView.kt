package com.mandrecode.tempo.features.focus.presentation.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mandrecode.tempo.R
import com.mandrecode.tempo.core.ui.editor.EditorBottomSheetFooter
import com.mandrecode.tempo.core.ui.util.color
import com.mandrecode.tempo.core.ui.util.titleResId
import com.mandrecode.tempo.features.focus.domain.model.FocusSession
import com.mandrecode.tempo.features.focus.domain.model.TaskFocusToday
import com.mandrecode.tempo.features.tasks.domain.model.Task
import com.mandrecode.tempo.features.tasks.presentation.components.cards.SubtaskItem
import com.mandrecode.tempo.util.DateTimeFormatter
import kotlin.time.Clock

/** Small enough to share a line with the title, large enough to read the time across a desk. */
private val DialSize = 104.dp

/** Where an editor row's icon starts, so the hero's text stands on the same line as the rows. */
private val HeroTextStartInset = 8.dp

/** The gap between the body's rows, as the task and habit editors space theirs. */
private val RowGap = 20.dp

/** The gap the setup rows keep between themselves, kept between them and the task's row too. */
private val SetupRowsGap = 12.dp

/**
 * What the session screen's body can do, as one value: starting and saving before it runs, the
 * timer's own controls while it does, and the task's rows throughout.
 */
internal data class SessionBodyActions(
    val onStart: () -> Unit,
    val onPauseResume: () -> Unit,
    val onStop: () -> Unit,
    /** `null` when there is no open task to finish: a saved session linked to none (#517). */
    val onComplete: (() -> Unit)?,
    val onBackToWork: () -> Unit,
    val onToggleSubtask: (Task) -> Unit,
    val onEditSubtask: (Task) -> Unit,
    /** The linked task's row: opening it, choosing one, taking it off. */
    val link: SessionLinkActions,
    /** Set while a saved session is being set up: its save and its way out. */
    val setup: SessionSetupActions? = null,
)

/**
 * What the linked-task row can do. [onPick] and [onUnlink] are `null` once the session is running:
 * the task is then what the timer is on, and not something to swap under it.
 */
internal data class SessionLinkActions(
    val onOpenTask: (Task) -> Unit,
    val onPick: (() -> Unit)?,
    val onUnlink: (() -> Unit)?,
)

/** What only a saved session being set up offers. [onDelete] is `null` for one not saved yet. */
internal data class SessionSetupActions(
    val onSave: () -> Unit,
    val onCancel: () -> Unit,
    val onDelete: (() -> Unit)?,
)

/**
 * The session itself: dial, rounds, linked task and controls. Hosted by [FocusSessionScreen], which
 * supplies the heading and the way out — this composable deliberately owns neither, so the screen
 * around it decides how the user leaves.
 *
 * One body for before and during, and for a session however it came about. A null [session] is the
 * screen opened to look at the work, or to set a session up, before committing: the same layout,
 * the dial full and still, the lengths and rounds there to change, and starting as the action
 * offered. Starting does not swap this for something else — the setup folds away and the controls
 * change under the same dial (#517).
 *
 * As tall as what it holds, not the whole screen: a session with no task is a short sheet.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun SessionBody(
    session: FocusSession?,
    form: SessionEditorForm,
    focusToday: TaskFocusToday,
    subtasks: List<Task>,
    task: Task?,
    categoryName: String?,
    actions: SessionBodyActions,
    modifier: Modifier = Modifier,
    clock: Clock = Clock.System,
    /** The title — a name field or plain text — and anything under it, beside the dial. */
    heading: @Composable ColumnScope.() -> Unit,
) {
    val counted by rememberSessionCountdown(session, clock)
    // Before it starts there is nothing to count down, so the ring shows the whole session ahead —
    // at the length being set, so changing it is seen where it will be counted.
    val remaining = if (session == null) FocusSession.lengthOf(form.focusMinutes) else counted
    val elapsedFraction =
        if (session == null || session.plannedLength.inWholeSeconds <= 0) {
            0f
        } else {
            1f - (remaining.inWholeSeconds.toFloat() / session.plannedLength.inWholeSeconds)
        }.coerceIn(0f, 1f)
    // A break drains where a session fills, the same way the card's ring does. The two surfaces
    // show the same countdown, so they have to run the same direction.
    val progress = if (session?.isBreak == true) 1f - elapsedFraction else elapsedFraction

    Column(
        modifier =
            modifier
                .fillMaxWidth()
                // No background of its own. The sheet already has one, and painting a second over
                // it drew a seam across the top where the handle and title sat on a different tone.
                .verticalScroll(rememberScrollState())
                // Matches the inset the top app bar gives its title, so the description below it
                // starts on the same line rather than stepping in.
                .padding(horizontal = SessionHorizontalPadding),
    ) {
        SessionHero(
            session = session,
            focusToday = focusToday,
            progress = progress,
            countdownLabel = remaining.asCountdownLabel(),
            rounds = session?.let { SessionRounds.of(it.cycle, it.isBreak) } ?: SessionRounds.planned(form.rounds),
            heading = heading,
        )

        // The task the session is for, in the same place whether the session was opened from the
        // task or had it linked: its row, then what the row's task carries.
        SessionLinkedTask(task = task, categoryName = categoryName, link = actions.link)

        SessionSubtasks(subtasks = subtasks, actions = actions)

        // Folds away as the session starts: the numbers are what the timer now runs on, and a
        // stepper beside a running countdown would be offering a change it cannot make.
        AnimatedVisibility(
            visible = session == null,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            // The rows' own gap, so the task's row and the three under it read as one list.
            SessionSetupRows(form = form, modifier = Modifier.padding(top = SetupRowsGap))
        }

        SessionControls(
            session = session,
            form = form,
            sessionsToday = focusToday.sessions,
            actions = actions,
        )
    }
}

/**
 * The task's steps, under the ring.
 *
 * Narrower than the ring above them: a checklist reads down a column, and lines running the full
 * width of a tablet are a long way for the eye to travel back.
 *
 * The Tasks screen's own row, not a copy of it: same labels, same checkbox animation, and tapping
 * one opens the same editor it opens there. A subtask that behaved differently depending on the
 * screen you found it on would be a different thing wearing the same name.
 */
@Composable
private fun SessionSubtasks(
    subtasks: List<Task>,
    actions: SessionBodyActions,
) {
    if (subtasks.isEmpty()) return
    val haptic = LocalHapticFeedback.current
    Column(
        modifier =
            Modifier
                .widthIn(max = SubtaskColumnMaxWidth)
                .fillMaxWidth()
                .padding(top = 28.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        subtasks.forEach { subtask ->
            SubtaskItem(
                subtask = subtask,
                onToggleCompletion = actions.onToggleSubtask,
                onEdit = actions.onEditSubtask,
                haptic = haptic,
            )
        }
    }
}

/**
 * What a session that has not started yet offers: starting it at the length set above.
 *
 * One button, saying the length it will run for. The lengths and rounds have their own rows now,
 * shared with saved sessions, so the second "start with" button that used to carry a stepper of its
 * own is gone — it was a second place to say the same thing.
 *
 * A saved session being set up adds the editors' footer: saving without starting for a new one,
 * deleting for one that exists.
 */
@Composable
private fun ColumnScope.PreStartControls(
    form: SessionEditorForm,
    colors: SessionActionColors,
    sessionsToday: Int,
    actions: SessionBodyActions,
) {
    val setup = actions.setup
    val onComplete = actions.onComplete
    if (setup == null && sessionsToday > 0 && onComplete != null) {
        WorkedOnControls(colors = colors, onStart = actions.onStart, onComplete = onComplete)
        return
    }

    SessionActionButton(
        action =
            SessionAction(
                label = stringResource(R.string.focus_session_start, sessionLengthLabel(form.focusMinutes)),
                icon = painterResource(R.drawable.ic_play_arrow),
                emphasis = ButtonEmphasis.FILLED,
                onClick = actions.onStart,
            ),
        colors = colors,
        modifier = Modifier.padding(top = 32.dp),
    )

    if (setup != null) {
        EditorBottomSheetFooter(
            hasDeleteAction = setup.onDelete != null,
            deleteLabel = stringResource(R.string.delete_session),
            onDelete = setup.onDelete,
            autoSaveEnabled = false,
            confirmEnabled = form.canSave,
            confirmLabel = stringResource(R.string.add_session),
            onRequestDismiss = setup.onCancel,
            onConfirmClick = setup.onSave,
        )
    }
}

/**
 * What a task that has already had a run at it offers.
 *
 * Worked on, not untouched: picking it back up is the same act as starting, so the standard button
 * says so and finishing joins it — the pair the finished-break sheet offers, in the place that
 * would otherwise have said "Start 25 min" as though nothing had happened.
 */
@Composable
private fun ColumnScope.WorkedOnControls(
    colors: SessionActionColors,
    onStart: () -> Unit,
    onComplete: () -> Unit,
) {
    SessionActionGroup(
        actions =
            listOf(
                SessionAction(
                    label = stringResource(R.string.focus_session_back_to_it),
                    icon = painterResource(R.drawable.ic_play_arrow),
                    emphasis = ButtonEmphasis.FILLED,
                    onClick = onStart,
                ),
                SessionAction(
                    label = stringResource(R.string.focus_session_mark_done),
                    icon = doneIcon(),
                    emphasis = ButtonEmphasis.TONAL,
                    onClick = onComplete,
                ),
            ),
        colors = colors,
        modifier = Modifier.fillMaxWidth().padding(top = 32.dp),
    )
}

/**
 * The top of the sheet: what the session is and where it stands, with its countdown beside it.
 *
 * Start-aligned text on one side, the dial on the other — the Focus tab's own summary card, turned
 * to one session. The dial stood alone in the middle of the sheet once, the only centred thing on
 * a sheet whose heading and rows all start at the same edge; here everything shares that edge and
 * the dial closes the line the title opens.
 *
 * Under the title, what the dial cannot say: whether it is running, what the task has already had
 * out of today, and how far through its rounds the session is.
 */
@Composable
private fun SessionHero(
    session: FocusSession?,
    focusToday: TaskFocusToday,
    progress: Float,
    countdownLabel: String,
    rounds: SessionRounds?,
    heading: @Composable ColumnScope.() -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(modifier = Modifier.weight(1f).padding(start = HeroTextStartInset)) {
            heading()
            SessionRingStatus(
                // The same line the card carries, so what the day has already put into this task
                // reads the same whether you are glancing at the row or standing in the session.
                label =
                    sessionStatusLine(
                        statusRes =
                            when {
                                session == null -> R.string.focus_session_not_started
                                session.isPaused -> R.string.focus_session_paused
                                session.isBreak -> R.string.focus_session_break
                                else -> R.string.focus_session_focusing
                            },
                        focusToday = focusToday,
                        cycle = session?.cycle,
                    ),
                iconRes = R.drawable.ic_coffee.takeIf { session?.isBreak == true },
            )
            // How many rounds, and how far through them. Shown before starting too, so setting
            // four rounds is seen as four before it is agreed to.
            AnimatedVisibility(visible = rounds != null) {
                rounds?.let {
                    SessionRoundPips(
                        rounds = it,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 10.dp),
                    )
                }
            }
        }
        SessionDial(
            progress = progress,
            isRunning = session?.isPaused == false,
            isBreak = session?.isBreak == true,
            colors = sessionSheetDialColors(),
            modifier = Modifier.size(DialSize),
            strokeWidth = 4.dp,
        ) {
            Text(
                text = countdownLabel,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

/**
 * The linked task as a row among the session's other rows: the chip that names it, and under the
 * chip one quiet line of what the task carries — its category, its priority, when it is due.
 *
 * A line rather than a row of coloured pills. The pills were three more shapes competing with the
 * dial for the eye, for facts that are there to be glanced at; the task's own sheet, one tap on
 * the chip away, is where they are shown in full.
 *
 * One block for a session opened from a task and for one that had a task linked to it. They are
 * the same thing once the link is there, and showing them two ways said otherwise.
 */
@Composable
private fun SessionLinkedTask(
    task: Task?,
    categoryName: String?,
    link: SessionLinkActions,
) {
    LinkedTaskRow(
        linkedTask = task,
        onOpen = link.onOpenTask,
        onPick = link.onPick,
        onClear = link.onUnlink,
        supporting = task?.let { sessionTaskSummary(it, categoryName) },
        modifier = Modifier.padding(top = RowGap),
    )
}

/**
 * `Inbox · High · 2:00 PM`, or `null` when the task carries none of them.
 *
 * The reminder shows its time alone: the date is already implied by the session being set up now.
 */
@Composable
private fun sessionTaskSummary(
    task: Task,
    categoryName: String?,
): String? {
    val context = LocalContext.current
    val parts =
        listOfNotNull(
            categoryName?.takeIf { it.isNotBlank() },
            task.priority?.let { stringResource(it.titleResId) },
            task.reminderDate?.let { DateTimeFormatter.formatTimeOfDay(it.time, context) },
        )
    return parts.takeIf { it.isNotEmpty() }?.joinToString(" · ")
}

@Composable
private fun SessionRingStatus(
    label: String,
    iconRes: Int?,
) {
    Row(
        modifier = Modifier.padding(top = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        iconRes?.let {
            Icon(
                painter = painterResource(it),
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * The buttons under the session: Start before it runs, the timer's own controls once it does.
 *
 * One slot whose contents change, rather than two layouts: starting is the same session carrying
 * on, and the buttons crossing over in place is what says so.
 */
@Composable
private fun SessionControls(
    session: FocusSession?,
    form: SessionEditorForm,
    sessionsToday: Int,
    actions: SessionBodyActions,
) {
    val colors = sessionScreenActionColors()

    AnimatedContent(
        targetState = session != null,
        transitionSpec = { fadeIn() togetherWith fadeOut() using SizeTransform(clip = false) },
        label = "session_controls",
        modifier = Modifier.fillMaxWidth().padding(bottom = 32.dp),
    ) { isRunning ->
        Column(modifier = Modifier.fillMaxWidth()) {
            if (isRunning) {
                // Null only while this side is on its way out, with the session already gone.
                session?.let { RunningControls(session = it, colors = colors, actions = actions) }
            } else {
                PreStartControls(form = form, colors = colors, sessionsToday = sessionsToday, actions = actions)
            }
        }
    }
}

@Composable
private fun ColumnScope.RunningControls(
    session: FocusSession,
    colors: SessionActionColors,
    actions: SessionBodyActions,
) {
    // Finishing and pausing are the two you reach for while working, so they sit together as one
    // control. Stopping throws the session away, so it stays on its own line where it cannot be hit
    // by aiming slightly wide.
    SessionActionGroup(
        actions = runningGroupActions(session, actions.onComplete, actions.onPauseResume, actions.onBackToWork),
        colors = colors,
        modifier = Modifier.fillMaxWidth().padding(top = 32.dp),
    )
    if (!session.isBreak) {
        SessionActionButton(
            action = stopSessionAction(actions.onStop),
            colors = colors,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

private val SessionHorizontalPadding = 16.dp
private val SubtaskRadius = 14.dp

private val SubtaskColumnMaxWidth = 420.dp
