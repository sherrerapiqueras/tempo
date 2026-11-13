package com.mandrecode.tempo.features.focus.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mandrecode.tempo.R
import com.mandrecode.tempo.features.focus.domain.model.FocusSession
import com.mandrecode.tempo.features.focus.domain.model.SessionCycle
import com.mandrecode.tempo.features.focus.domain.model.TaskFocusToday
import com.mandrecode.tempo.features.tasks.domain.model.Task
import kotlin.time.Clock

private val CardCornerRadius = 22.dp
private val RingSize = 68.dp

/**
 * The Up next card while a session is running.
 *
 * Same ground and same place in the layout as [UpNextCard] — the card transforms rather than being
 * replaced, so starting a session does not make the screen jump. The agenda underneath stays
 * usable: the point is to keep working, not to take the screen over.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun RunningSessionCard(
    session: FocusSession,
    onExpand: () -> Unit,
    onPauseResume: () -> Unit,
    onStop: () -> Unit,
    onComplete: () -> Unit,
    onBackToWork: () -> Unit,
    modifier: Modifier = Modifier,
    subtasks: List<Task> = emptyList(),
    focusToday: TaskFocusToday = TaskFocusToday(),
    clock: Clock = Clock.System,
) {
    val haptic = LocalHapticFeedback.current
    val remaining by rememberSessionCountdown(session, clock)
    val progress =
        if (session.plannedLength.inWholeSeconds <= 0) {
            0f
        } else {
            1f - (remaining.inWholeSeconds.toFloat() / session.plannedLength.inWholeSeconds)
        }.coerceIn(0f, 1f)

    val cardShape = RoundedCornerShape(CardCornerRadius)
    Surface(
        modifier = modifier,
        shape = cardShape,
        color = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
    ) {
        Column(
            modifier =
                Modifier
                    .clickable {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onExpand()
                    }.padding(horizontal = 16.dp, vertical = 14.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                SessionRing(progress = progress, session = session, label = remaining.asCountdownLabel())

                SessionTitleBlock(
                    title = session.title,
                    isPaused = session.isPaused,
                    isBreak = session.isBreak,
                    focusToday = focusToday,
                    cycle = session.cycle,
                    modifier = Modifier.weight(1f),
                )

                // Balances the ring on the other end of the row, and costs no height of its own —
                // which is what it was doing on the session sheet, where it took a whole line to
                // say two numbers.
                if (subtasks.isNotEmpty()) {
                    SubtaskCount(subtasks = subtasks)
                }
            }

            SessionCardActions(
                session = session,
                onPauseResume = onPauseResume,
                onStop = onStop,
                // A saved session linked to no task has nothing to mark done (#517).
                onComplete = onComplete.takeIf { session.taskId != null },
                onBackToWork = onBackToWork,
                modifier = Modifier.padding(top = 12.dp),
            )
        }
    }
}

@Composable
private fun SessionCardActions(
    session: FocusSession,
    onPauseResume: () -> Unit,
    onStop: () -> Unit,
    onComplete: (() -> Unit)?,
    onBackToWork: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = sessionCardActionColors()

    val running = runningGroupActions(session, onComplete, onPauseResume, onBackToWork)
    // A break's two choices are the whole set; a session keeps its way out.
    val stop = stopSessionAction(onStop).takeUnless { session.isBreak }

    // All three across one row where they fit, unlike the session screen's two-plus-one: the card
    // is a summary sitting above the rest of the day, and a second row of controls would cost it
    // more height than the actions are worth. On a narrow window three columns cannot hold their
    // labels, and a clipped "Mark do…" is worse than the extra row, so it splits like the screen.
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        if (maxWidth >= ThreeAcrossMinWidth) {
            SessionActionGroup(
                actions = running + listOfNotNull(stop),
                colors = colors,
                modifier = Modifier.fillMaxWidth(),
                compact = true,
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                SessionActionGroup(
                    actions = running,
                    colors = colors,
                    modifier = Modifier.fillMaxWidth(),
                    compact = true,
                )
                stop?.let { SessionActionButton(action = it, colors = colors, compact = true) }
            }
        }
    }
}

/**
 * Below this the three labels stop fitting side by side. Measured against the longest of them
 * rather than a screen breakpoint, since the card is also narrowed by a rail or a docked pane.
 */
private val ThreeAcrossMinWidth = 340.dp

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SessionRing(
    progress: Float,
    session: FocusSession,
    label: String,
) {
    Box(contentAlignment = Alignment.Center) {
        CircularWavyProgressIndicator(
            // A break unwinds: the ring empties as it runs, where a session fills. Opposite
            // directions for opposite things, so which one is under way reads without a label.
            progress = { if (session.isBreak) 1f - progress else progress },
            modifier = Modifier.size(RingSize),
            color = MaterialTheme.colorScheme.onTertiaryContainer,
            trackColor = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = TRACK_ALPHA),
            // The wave is the session's heartbeat: it travels while time is running down and
            // stands still the moment the user pauses.
            waveSpeed = if (session.isPaused) STILL else WaveSpeed,
        )
        if (session.isBreak) {
            // The cup says it at a glance, and the numerals are too small here to be missed. The
            // sheet keeps its countdown — that surface exists to show the time.
            Icon(
                painter = painterResource(R.drawable.ic_coffee),
                contentDescription = stringResource(R.string.focus_session_break),
                modifier = Modifier.size(20.dp),
            )
        } else {
            Text(text = label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
        }
    }
}

/** How much of the task is broken down: done over total, no more than that. */
@Composable
private fun SubtaskCount(subtasks: List<Task>) {
    Row(
        modifier =
            Modifier
                .clip(RoundedCornerShape(percent = FULLY_ROUNDED))
                .background(MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = COUNT_CONTAINER_ALPHA))
                .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_checklist),
            contentDescription = null,
            modifier = Modifier.size(14.dp),
        )
        Text(
            text = "${subtasks.count { it.isCompleted }}/${subtasks.size}",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun SessionTitleBlock(
    title: String,
    isPaused: Boolean,
    isBreak: Boolean,
    focusToday: TaskFocusToday,
    cycle: SessionCycle?,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        // A break is time away from the task, not time spent on it. Saying "Focusing" through one
        // told the user the opposite of what was happening.
        //
        // What the task has already had out of today rides on the same line: a session is rarely
        // the first go at something, and a card that says only "Focusing" makes every one of them
        // look like a standing start.
        Text(
            text =
                sessionStatusLine(
                    statusRes =
                        when {
                            isPaused -> R.string.focus_session_paused
                            isBreak -> R.string.focus_session_break
                            else -> R.string.focus_session_focusing
                        },
                    focusToday = focusToday,
                    cycle = cycle,
                ),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = LABEL_ALPHA),
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}

private const val FULLY_ROUNDED = 50
private const val COUNT_CONTAINER_ALPHA = 0.12f
private val STILL = 0.dp
private val WaveSpeed = 10.dp
private const val TRACK_ALPHA = 0.24f
private const val LABEL_ALPHA = 0.75f
private val PillRadius = 20.dp
private val PillPressedRadius = 10.dp
