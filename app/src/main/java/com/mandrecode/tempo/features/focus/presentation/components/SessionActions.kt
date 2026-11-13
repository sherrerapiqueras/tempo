package com.mandrecode.tempo.features.focus.presentation.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonGroup
import androidx.compose.material3.ButtonGroupScope
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mandrecode.tempo.R
import com.mandrecode.tempo.core.ui.util.rememberPressableButtonAnimation
import com.mandrecode.tempo.features.focus.domain.model.FocusSession
import com.mandrecode.tempo.features.focus.domain.model.SessionCycle
import com.mandrecode.tempo.features.focus.domain.model.TaskFocusToday

/**
 * How much weight a session action carries.
 *
 * [FILLED] finishes the work for good, [TONAL] is reversible, [DISCARD] throws the session away —
 * three different kinds of act, so three different looks. The running-session card and the session
 * screen share these, so an action never changes weight depending on where you press it.
 */
internal enum class ButtonEmphasis {
    FILLED,
    TONAL,

    /** Throws the session away — outlined in the error colour, so it reads as "undo this". */
    DISCARD,

    /** Available but not being suggested: outlined, in the surface's own ink rather than red. */
    QUIET,
}

/**
 * The colours a session action draws from.
 *
 * Passed in rather than read from the theme, because the same three weights have to render on two
 * different surfaces: the session screen's plain background and the card's tertiary container.
 */
internal data class SessionActionColors(
    val filledContainer: Color,
    val filledContent: Color,
    val tonalContainer: Color,
    val tonalContent: Color,
    val discardContent: Color,
    val discardBorder: Color,
    val quietContent: Color,
    val quietBorder: Color,
)

@Composable
internal fun sessionScreenActionColors(): SessionActionColors =
    SessionActionColors(
        filledContainer = MaterialTheme.colorScheme.primary,
        filledContent = MaterialTheme.colorScheme.onPrimary,
        tonalContainer = MaterialTheme.colorScheme.secondaryContainer,
        tonalContent = MaterialTheme.colorScheme.onSecondaryContainer,
        discardContent = MaterialTheme.colorScheme.error,
        discardBorder = MaterialTheme.colorScheme.error.copy(alpha = DISCARD_BORDER_ALPHA),
        quietContent = MaterialTheme.colorScheme.onSurfaceVariant,
        quietBorder = MaterialTheme.colorScheme.outlineVariant,
    )

@Composable
internal fun sessionCardActionColors(): SessionActionColors =
    SessionActionColors(
        filledContainer = MaterialTheme.colorScheme.onTertiaryContainer,
        filledContent = MaterialTheme.colorScheme.tertiaryContainer,
        tonalContainer = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = TONAL_ALPHA),
        tonalContent = MaterialTheme.colorScheme.onTertiaryContainer,
        discardContent = MaterialTheme.colorScheme.onTertiaryContainer,
        discardBorder = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = TONAL_ALPHA),
        quietContent = MaterialTheme.colorScheme.onTertiaryContainer,
        quietBorder = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = TONAL_ALPHA),
    )

/**
 * A session length as it reads on the day's surfaces: `25 min` under the hour, `01:30` over it.
 *
 * Matches [asCountdownLabel]'s rollover, so the length you chose and the time left after starting
 * are written the same way rather than one saying "90 min" and the other "01:29".
 */
@Composable
internal fun sessionLengthLabel(minutes: Int): String =
    if (minutes < MINUTES_PER_HOUR) {
        stringResource(R.string.focus_session_length_minutes, minutes)
    } else {
        "%02d:%02d".format(minutes / MINUTES_PER_HOUR, minutes % MINUTES_PER_HOUR)
    }

private const val MINUTES_PER_HOUR = 60

/**
 * What the session is doing, and what the task has already had out of today — `Focusing`, or
 * `Focusing · 2 sessions done`.
 *
 * One line for both, on the card and on the session screen alike. A timer counting down says
 * nothing about whether this is the first go at the work or the fourth, and that is the difference
 * between a standing start and picking something back up.
 */
@Composable
internal fun sessionStatusLine(
    statusRes: Int,
    focusToday: TaskFocusToday,
    cycle: SessionCycle? = null,
): String {
    val status = stringResource(statusRes)
    // A session that repeats says where in its rounds it is — the one thing its countdown cannot.
    // That outranks the day's tally, which belongs to the task and reads on the task's own card.
    val detail =
        cycle
            ?.takeIf { it.hasRounds }
            ?.let { stringResource(R.string.focus_session_round, it.round, it.totalRounds) }
            ?: focusToday.soFarTodayLabel()
            ?: return status
    return "$status · $detail"
}

/**
 * One action's label, icon and weight, so a group can be described as data.
 *
 * A painter rather than a drawable id, so the tick can be the same `Icons.Filled.Check` the task
 * and habit cards use to mean done — one glyph for one meaning, wherever it appears.
 */
internal data class SessionAction(
    val label: String,
    val icon: Painter,
    val emphasis: ButtonEmphasis,
    val onClick: () -> Unit,
)

/**
 * Done and pause for a running session; for a break, the way back to the work.
 *
 * A break has no work to finish and little worth pausing — stopping the clock on a rest is a
 * distinction without a difference — so the reversible action there is cutting it short and
 * starting again, which is the thing people actually want halfway through one.
 */
@Composable
internal fun runningGroupActions(
    session: FocusSession,
    /** `null` when the session is linked to no task: there is then nothing to mark done (#517). */
    onComplete: (() -> Unit)?,
    onPauseResume: () -> Unit,
    onBackToWork: () -> Unit,
): List<SessionAction> {
    val doneLabel = stringResource(R.string.focus_session_mark_done)
    val doneGlyph = doneIcon()
    val markDone: (ButtonEmphasis) -> SessionAction? = { emphasis ->
        onComplete?.let {
            SessionAction(label = doneLabel, icon = doneGlyph, emphasis = emphasis, onClick = it)
        }
    }
    val pause =
        SessionAction(
            label =
                if (session.isPaused) {
                    stringResource(R.string.focus_session_resume)
                } else {
                    stringResource(R.string.focus_session_pause)
                },
            icon = painterResource(if (session.isPaused) R.drawable.ic_play_arrow else R.drawable.ic_pause),
            emphasis = ButtonEmphasis.TONAL,
            onClick = onPauseResume,
        )
    // A break has two ways out, and abandoning it is neither: either you go back to the work or
    // the work is done. Stopping the break said nothing about the task and left it exactly as it
    // was, which is why "Stop here" read as doing nothing here.
    if (session.isBreak) {
        return listOfNotNull(
            SessionAction(
                label = stringResource(R.string.focus_session_back_to_it),
                icon = painterResource(R.drawable.ic_play_arrow),
                emphasis = ButtonEmphasis.FILLED,
                onClick = onBackToWork,
            ),
            markDone(ButtonEmphasis.TONAL),
        )
    }

    return listOfNotNull(markDone(ButtonEmphasis.FILLED), pause)
}

@Composable
internal fun stopSessionAction(onStop: () -> Unit): SessionAction =
    SessionAction(
        label = stringResource(R.string.focus_session_stop),
        icon = painterResource(R.drawable.ic_stop),
        emphasis = ButtonEmphasis.DISCARD,
        onClick = onStop,
    )

/**
 * Two or three actions drawn as a connected button group.
 *
 * A real [ButtonGroup] rather than a plain row: pressing one item widens it and squeezes its
 * neighbour, which is the interaction that tells you the two belong together. Its items are custom
 * because the group's own `clickableItem` offers no colour control, and the whole point here is
 * that the two buttons do *not* look alike.
 *
 * The shapes are the connected treatment — round on the group's outer ends, nearly square where
 * the two meet — so the pair reads as one control split in two rather than as two loose buttons.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun SessionActionGroup(
    actions: List<SessionAction>,
    colors: SessionActionColors,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    ButtonGroup(
        overflowIndicator = {},
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(GroupItemSpacing),
    ) {
        actions.forEachIndexed { index, action ->
            groupedAction(
                action = action,
                colors = colors,
                isFirst = index == 0,
                isLast = index == actions.lastIndex,
                compact = compact,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
private fun ButtonGroupScope.groupedAction(
    action: SessionAction,
    colors: SessionActionColors,
    isFirst: Boolean,
    isLast: Boolean,
    compact: Boolean,
) {
    customItem(
        buttonGroupContent = {
            val (interactionSource, outerRadius) =
                rememberPressableButtonAnimation(
                    baseRadius = PillRadius,
                    pressedRadius = PillPressedRadius,
                )
            SessionActionSurface(
                action = action,
                colors = colors,
                shape =
                    RoundedCornerShape(
                        topStart = if (isFirst) outerRadius.value else InnerRadius,
                        bottomStart = if (isFirst) outerRadius.value else InnerRadius,
                        topEnd = if (isLast) outerRadius.value else InnerRadius,
                        bottomEnd = if (isLast) outerRadius.value else InnerRadius,
                    ),
                interactionSource = interactionSource,
                compact = compact,
                modifier = Modifier.weight(1f).animateWidth(interactionSource),
            )
        },
        // Nothing overflows: the group only ever holds the two actions handed to it.
        menuContent = {},
    )
}

/** A single action on its own line, for the one that is not part of a group. */
@Composable
internal fun SessionActionButton(
    action: SessionAction,
    colors: SessionActionColors,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    val (interactionSource, radius) =
        rememberPressableButtonAnimation(baseRadius = PillRadius, pressedRadius = PillPressedRadius)
    SessionActionSurface(
        action = action,
        colors = colors,
        shape = RoundedCornerShape(radius.value),
        interactionSource = interactionSource,
        compact = compact,
        modifier = modifier.fillMaxWidth(),
    )
}

@Composable
private fun SessionActionSurface(
    action: SessionAction,
    colors: SessionActionColors,
    shape: RoundedCornerShape,
    interactionSource: MutableInteractionSource,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    // A floor rather than a fixed height: a wrapped label still gets the room it needs, but the
    // short ones stop sitting lower than whatever they are lined up beside.
    val sized = modifier.heightIn(min = if (compact) CompactMinHeight else StandardMinHeight)
    val haptic = LocalHapticFeedback.current

    Surface(
        onClick = {
            // A commitment gets the firmer confirmation; the reversible ones do not.
            haptic.performHapticFeedback(
                if (action.emphasis == ButtonEmphasis.FILLED) {
                    HapticFeedbackType.LongPress
                } else {
                    HapticFeedbackType.TextHandleMove
                },
            )
            action.onClick()
        },
        modifier = sized,
        interactionSource = interactionSource,
        shape = shape,
        color =
            when (action.emphasis) {
                ButtonEmphasis.FILLED -> colors.filledContainer
                ButtonEmphasis.TONAL -> colors.tonalContainer
                ButtonEmphasis.DISCARD, ButtonEmphasis.QUIET -> Color.Transparent
            },
        contentColor =
            when (action.emphasis) {
                ButtonEmphasis.FILLED -> colors.filledContent
                ButtonEmphasis.TONAL -> colors.tonalContent
                ButtonEmphasis.DISCARD -> colors.discardContent
                ButtonEmphasis.QUIET -> colors.quietContent
            },
        border =
            when (action.emphasis) {
                ButtonEmphasis.DISCARD -> BorderStroke(1.dp, colors.discardBorder)
                ButtonEmphasis.QUIET -> BorderStroke(1.dp, colors.quietBorder)
                ButtonEmphasis.FILLED, ButtonEmphasis.TONAL -> null
            },
    ) {
        SessionActionLabel(action = action, compact = compact)
    }
}

@Composable
private fun SessionActionLabel(
    action: SessionAction,
    compact: Boolean,
) {
    Row(
        modifier =
            Modifier.padding(
                horizontal = if (compact) 6.dp else 12.dp,
                vertical = if (compact) 10.dp else 14.dp,
            ),
        horizontalArrangement =
            Arrangement.spacedBy(if (compact) 4.dp else 8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // The glyph carries the action at a glance — a tick, a pause bar, a stop square — so the
        // three are told apart before the labels are read.
        Icon(
            painter = action.icon,
            contentDescription = null,
            modifier = Modifier.size(if (compact) 16.dp else 18.dp),
        )
        Text(
            text = action.label,
            style =
                if (compact) {
                    MaterialTheme.typography.labelMedium
                } else {
                    MaterialTheme.typography.labelLarge
                },
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private val PillRadius = 24.dp
private val PillPressedRadius = 12.dp
private val InnerRadius = 6.dp
private val GroupItemSpacing: Dp = 2.dp
private val StandardMinHeight = 52.dp
private val CompactMinHeight = 44.dp
private const val DISCARD_BORDER_ALPHA = 0.4f
private const val TONAL_ALPHA = 0.12f
