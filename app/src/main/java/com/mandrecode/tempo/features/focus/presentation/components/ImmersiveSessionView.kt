package com.mandrecode.tempo.features.focus.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mandrecode.tempo.R
import com.mandrecode.tempo.core.ui.components.TempoLinkButton
import com.mandrecode.tempo.core.ui.components.ValueStepper
import com.mandrecode.tempo.core.ui.util.color
import com.mandrecode.tempo.core.ui.util.containerColor
import com.mandrecode.tempo.core.ui.util.titleResId
import com.mandrecode.tempo.features.focus.domain.model.FocusSession
import com.mandrecode.tempo.features.focus.domain.model.TaskFocusToday
import com.mandrecode.tempo.features.tasks.domain.model.Task
import com.mandrecode.tempo.features.tasks.presentation.components.cards.SubtaskItem
import com.mandrecode.tempo.util.DateTimeFormatter
import kotlin.time.Clock
import kotlin.time.Duration

private val RingSize = 220.dp
private const val FULLY_ROUNDED = 50

/**
 * The session itself: ring, task subtasks and controls. Hosted by [FocusSessionScreen], which
 * supplies the top bar and the back affordance — this composable deliberately owns neither, so the
 * screen around it decides how the user leaves.
 *
 * A null [session] is the screen opened from Up next to look at the work before committing: the
 * same layout, the ring full and still, and starting as the one action offered.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun SessionBody(
    session: FocusSession?,
    plannedLength: Duration,
    focusToday: TaskFocusToday,
    subtasks: List<Task>,
    task: Task?,
    categoryName: String?,
    onStart: (Int) -> Unit,
    onPauseResume: () -> Unit,
    onStop: () -> Unit,
    /** `null` when there is no task to finish: a saved session linked to none (#517). */
    onComplete: (() -> Unit)?,
    onBackToWork: () -> Unit,
    onToggleSubtask: (Task) -> Unit,
    onEditSubtask: (Task) -> Unit,
    onOpenInTasks: () -> Unit,
    modifier: Modifier = Modifier,
    clock: Clock = Clock.System,
) {
    val haptic = LocalHapticFeedback.current
    val counted by rememberSessionCountdown(session, clock)
    // Before it starts there is nothing to count down, so the ring shows the whole session ahead.
    val remaining = if (session == null) plannedLength else counted
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
                .fillMaxSize()
                // No background of its own. The sheet already has one, and painting a second over
                // it drew a seam across the top where the handle and title sat on a different tone.
                .verticalScroll(rememberScrollState())
                // Matches the inset the top app bar gives its title, so the description below it
                // starts on the same line rather than stepping in.
                .padding(horizontal = SessionHorizontalPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        SessionSubject(
            session = session,
            task = task,
            categoryName = categoryName,
            focusToday = focusToday,
            progress = progress,
            countdownLabel = remaining.asCountdownLabel(),
            onOpenInTasks = onOpenInTasks,
        )

        if (subtasks.isNotEmpty()) {
            Column(
                // Narrower than the ring above it: a checklist reads down a column, and lines
                // running the full width of a tablet are a long way for the eye to travel back.
                modifier =
                    Modifier
                        .widthIn(max = SubtaskColumnMaxWidth)
                        .fillMaxWidth()
                        .padding(top = 28.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                // The Tasks screen's own row, not a copy of it: same labels, same checkbox
                // animation, and tapping one opens the same editor it opens there. A subtask that
                // behaved differently depending on the screen you found it on would be a different
                // thing wearing the same name.
                subtasks.forEach { subtask ->
                    SubtaskItem(
                        subtask = subtask,
                        onToggleCompletion = onToggleSubtask,
                        onEdit = onEditSubtask,
                        haptic = haptic,
                    )
                }
            }
        }

        SessionControls(
            session = session,
            plannedLength = plannedLength,
            sessionsToday = focusToday.sessions,
            onStart = onStart,
            onPauseResume = onPauseResume,
            onStop = onStop,
            onComplete = onComplete,
            onBackToWork = onBackToWork,
        )
    }
}

/**
 * What a session that has not started yet offers: the length you set, and the length you want today.
 *
 * Two buttons rather than one adjustable one. Starting at the configured length has to stay a
 * single tap — that is the whole point of having configured it — so the adjustable start sits
 * below with its modifier beside it, for the session that wants to be different just this once.
 */
@Composable
private fun ColumnScope.PreStartControls(
    plannedLength: Duration,
    colors: SessionActionColors,
    sessionsToday: Int,
    onStart: (Int) -> Unit,
    onComplete: () -> Unit,
) {
    if (sessionsToday > 0) {
        WorkedOnControls(
            plannedLength = plannedLength,
            colors = colors,
            onStart = onStart,
            onComplete = onComplete,
        )
        return
    }

    val standardMinutes = plannedLength.inWholeMinutes.toInt()
    var customMinutes by remember(standardMinutes) { mutableIntStateOf(standardMinutes) }

    SessionActionButton(
        action =
            SessionAction(
                label = stringResource(R.string.focus_session_start, sessionLengthLabel(standardMinutes)),
                icon = painterResource(R.drawable.ic_play_arrow),
                emphasis = ButtonEmphasis.FILLED,
                onClick = { onStart(standardMinutes) },
            ),
        colors = colors,
        modifier = Modifier.padding(top = 32.dp),
    )

    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Button first, then the length: the row reads as one sentence — "start with … minutes" —
        // rather than asking you to set a number before you know what it is for.
        SessionActionButton(
            action =
                SessionAction(
                    label = stringResource(R.string.focus_session_start_custom),
                    icon = painterResource(R.drawable.ic_play_arrow),
                    emphasis = ButtonEmphasis.QUIET,
                    onClick = { onStart(customMinutes) },
                ),
            colors = colors,
            modifier = Modifier.weight(1f),
        )
        ValueStepper(
            value = customMinutes,
            label = sessionLengthLabel(customMinutes),
            onValueChange = { customMinutes = it },
            range = FocusSession.SESSION_LENGTH_RANGE,
            step = FocusSession.LENGTH_STEP_MINUTES,
            // Matched to the buttons either side of it, so the row is one band rather than a tall
            // control with two short ones tucked against it.
            buttonHeight = SessionControlHeight,
            decreaseDescription = stringResource(R.string.focus_session_length_decrease),
            increaseDescription = stringResource(R.string.focus_session_length_increase),
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
    plannedLength: Duration,
    colors: SessionActionColors,
    onStart: (Int) -> Unit,
    onComplete: () -> Unit,
) {
    SessionActionGroup(
        actions =
            listOf(
                SessionAction(
                    label = stringResource(R.string.focus_session_back_to_it),
                    icon = painterResource(R.drawable.ic_play_arrow),
                    emphasis = ButtonEmphasis.FILLED,
                    onClick = { onStart(plannedLength.inWholeMinutes.toInt()) },
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

/** What the session is on: its description, the ring, and the task's category, priority and time. */
@Composable
private fun ColumnScope.SessionSubject(
    session: FocusSession?,
    task: Task?,
    categoryName: String?,
    focusToday: TaskFocusToday,
    progress: Float,
    countdownLabel: String,
    onOpenInTasks: () -> Unit,
) {
    SessionRing(
        progress = progress,
        label = countdownLabel,
        isPaused = session?.isPaused ?: true,
        // The same line the card carries, so what the day has already put into this task reads the
        // same whether you are glancing at the row or standing in the session itself.
        statusLabel =
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
        statusIconRes = R.drawable.ic_coffee.takeIf { session?.isBreak == true },
        modifier = Modifier.padding(top = 24.dp),
    )

    // Below the ring rather than above it: the countdown is what this sheet is for, and pushing it
    // down the page behind a paragraph buried the one thing you opened it to see. Here the
    // description reads as the first of the task's details rather than a preamble to the timer.
    task?.description?.takeIf { it.isNotBlank() }?.let { description ->
        Text(
            text = description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(top = 20.dp),
        )
    }

    if (task != null) {
        SessionTaskMetadata(
            task = task,
            // A saved session runs under its own name, so the task it is for has to be said.
            showsTaskTitle = session?.cycle != null,
            categoryName = categoryName,
            minutesToday = focusToday.minutes,
            modifier = Modifier.padding(top = 24.dp),
        )
        // Sits with the task's own details, not down among the timer controls: leaving the screen
        // by accident when reaching for stop or done would cost the session.
        TempoLinkButton(
            label = stringResource(R.string.focus_session_open_in_tasks),
            iconRes = R.drawable.ic_open_in_new,
            onClick = onOpenInTasks,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SessionRing(
    progress: Float,
    label: String,
    isPaused: Boolean,
    statusLabel: String,
    modifier: Modifier = Modifier,
    statusIconRes: Int? = null,
) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        CircularWavyProgressIndicator(
            progress = { progress },
            modifier = Modifier.size(RingSize),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.primary.copy(alpha = TRACK_ALPHA),
            // Travels while running, still while paused — same rule as the card's ring.
            waveSpeed = if (isPaused) STILL else WaveSpeed,
        )
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = label,
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
            )
            SessionRingStatus(
                label = statusLabel,
                iconRes = statusIconRes,
            )
        }
    }
}

@Composable
private fun SessionRingStatus(
    label: String,
    iconRes: Int?,
) {
    Row(
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
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SessionControls(
    session: FocusSession?,
    plannedLength: Duration,
    sessionsToday: Int,
    onStart: (Int) -> Unit,
    onPauseResume: () -> Unit,
    onStop: () -> Unit,
    onComplete: (() -> Unit)?,
    onBackToWork: () -> Unit,
) {
    val colors = sessionScreenActionColors()

    Column(modifier = Modifier.fillMaxWidth().padding(bottom = 32.dp)) {
        if (session == null) {
            PreStartControls(
                plannedLength = plannedLength,
                colors = colors,
                sessionsToday = sessionsToday,
                onStart = onStart,
                // Only a task is ever shown before it starts, so there is always one to finish.
                onComplete = onComplete ?: {},
            )
        } else {
            // Finishing and pausing are the two you reach for while working, so they sit together
            // as one control. Stopping throws the session away, so it stays on its own line where
            // it cannot be hit by aiming slightly wide.
            SessionActionGroup(
                actions = runningGroupActions(session, onComplete, onPauseResume, onBackToWork),
                colors = colors,
                modifier = Modifier.fillMaxWidth().padding(top = 32.dp),
            )
            if (!session.isBreak) {
                SessionActionButton(
                    action = stopSessionAction(onStop),
                    colors = colors,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }
}

/**
 * Category, priority and reminder for the task under way, as the full pills the task editor uses
 * rather than the compact badges its card shows — this screen has the room, and the editor's
 * vocabulary is the one that names each property outright.
 *
 * The reminder is the exception: the date is already implied by the session being under way, so it
 * shows the time alone.
 */
@Composable
private fun SessionTaskMetadata(
    task: Task,
    showsTaskTitle: Boolean,
    categoryName: String?,
    minutesToday: Int,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current

    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (showsTaskTitle) {
            SessionMetadataPill(
                iconRes = R.drawable.ic_checklist,
                label = task.title,
                containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                contentColor = MaterialTheme.colorScheme.onSurface,
            )
        }
        // Leads the row: what this task has already taken out of the day is the one thing here the
        // task's own card cannot tell you, and it is the reason you might choose a longer session
        // or call it finished.
        if (minutesToday > 0) {
            SessionMetadataPill(
                iconRes = R.drawable.ic_timer,
                label = stringResource(R.string.focus_today_minutes, minutesToday),
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
        categoryName?.takeIf { it.isNotBlank() }?.let { name ->
            SessionMetadataPill(
                iconRes = R.drawable.ic_category,
                label = name,
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
        task.priority?.let { priority ->
            SessionMetadataPill(
                iconRes = R.drawable.ic_flag,
                label = stringResource(priority.titleResId),
                containerColor = priority.containerColor,
                contentColor = priority.color,
            )
        }
        task.reminderDate?.let { reminder ->
            SessionMetadataPill(
                iconRes = R.drawable.ic_notifications,
                label = DateTimeFormatter.formatTimeOfDay(reminder.time, context),
                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
            )
        }
    }
}

@Composable
private fun SessionMetadataPill(
    iconRes: Int,
    label: String,
    containerColor: Color,
    contentColor: Color,
) {
    val pillShape = RoundedCornerShape(percent = FULLY_ROUNDED)
    Surface(
        shape = pillShape,
        color = containerColor,
        contentColor = contentColor,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private val STILL = 0.dp
private val WaveSpeed = 10.dp
private const val TRACK_ALPHA = 0.22f
private val SessionHorizontalPadding = 16.dp
private val SubtaskRadius = 14.dp

/** One height for everything in the session's control rows, buttons and stepper alike. */
private val SessionControlHeight = 52.dp
private val SubtaskColumnMaxWidth = 420.dp
