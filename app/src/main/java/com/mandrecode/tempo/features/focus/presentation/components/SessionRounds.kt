package com.mandrecode.tempo.features.focus.presentation.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mandrecode.tempo.R
import com.mandrecode.tempo.features.focus.domain.model.SessionCycle

internal const val SESSION_ROUNDS_TEST_TAG = "session_rounds"

/**
 * Where a session's rounds stand, as the app would draw them.
 *
 * [done] rounds are behind you, [current] is the one under way — or the one a rest follows — and
 * the rest of [total] are still to come. Before a session starts nothing is current.
 */
internal data class SessionRounds(
    val total: Int,
    val current: Int,
    /** A rest follows a round that is finished, so the round it follows counts as done. */
    val isResting: Boolean = false,
) {
    val done: Int get() = if (isResting) current else (current - 1).coerceAtLeast(0)

    /** The round under way, or `null` before starting and during a rest. */
    val underWay: Int? get() = current.takeIf { it > 0 && !isResting }

    val pending: Int get() = total - done - if (underWay != null) 1 else 0

    companion object {
        /** A running session's rounds, or `null` when it has only the one. */
        fun of(
            cycle: SessionCycle?,
            isBreak: Boolean,
        ): SessionRounds? {
            val rounds = cycle?.takeIf { it.hasRounds } ?: return null
            return SessionRounds(rounds.totalRounds, rounds.round, isResting = isBreak)
        }

        /** The rounds a session is set to before it starts, or `null` for a single one. */
        fun planned(rounds: Int): SessionRounds? = rounds.takeIf { it > 1 }?.let { SessionRounds(it, current = 0) }
    }
}

/**
 * One marker per round: filled for those done, longer for the one under way, faint for those still
 * to come.
 *
 * A row of marks rather than only "Round 2 of 4", because what you want to know half-way through is
 * how much is left, and that is read faster from a shape than from two numbers. The words are still
 * there, on the line above, and are what a screen reader is given.
 */
@Composable
internal fun SessionRoundPips(
    rounds: SessionRounds,
    color: Color,
    modifier: Modifier = Modifier,
) {
    val description = stringResource(R.string.focus_session_round, rounds.current.coerceAtLeast(1), rounds.total)
    // One node for the whole row, read as words. The tag is set in the same block: this modifier
    // clears what the ones before it said, a test tag included.
    Row(
        modifier =
            modifier.clearAndSetSemantics {
                contentDescription = description
                testTag = SESSION_ROUNDS_TEST_TAG
            },
        horizontalArrangement = Arrangement.spacedBy(PipSpacing),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(rounds.total) { index ->
            val round = index + 1
            RoundPip(
                isUnderWay = round == rounds.underWay,
                isDone = round <= rounds.done,
                color = color,
            )
        }
    }
}

@Composable
private fun RoundPip(
    isUnderWay: Boolean,
    isDone: Boolean,
    color: Color,
) {
    val width by animateDpAsState(if (isUnderWay) UnderWayWidth else PipWidth, label = "round_pip_width")
    val fill by animateColorAsState(
        if (isDone || isUnderWay) color else color.copy(alpha = PENDING_ALPHA),
        label = "round_pip_color",
    )
    Box(
        modifier =
            Modifier
                .width(width)
                .height(PipHeight)
                .clip(RoundedCornerShape(percent = FULLY_ROUNDED))
                .background(fill),
    )
}

private val PipWidth: Dp = 14.dp
private val UnderWayWidth: Dp = 28.dp
private val PipHeight: Dp = 6.dp
private val PipSpacing: Dp = 5.dp
private const val PENDING_ALPHA = 0.24f
private const val FULLY_ROUNDED = 50
