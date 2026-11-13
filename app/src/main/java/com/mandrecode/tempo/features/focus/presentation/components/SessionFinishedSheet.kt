package com.mandrecode.tempo.features.focus.presentation.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mandrecode.tempo.R
import com.mandrecode.tempo.core.ui.adaptive.adaptiveHorizontalMargin
import com.mandrecode.tempo.core.ui.components.TempoModalBottomSheet
import com.mandrecode.tempo.features.focus.presentation.FocusContract

/**
 * What the user sees when a session's time runs out.
 *
 * Only that ending: stopping early and marking the task done are decisions the user has already
 * made, and this sheet exists to ask a question, not to announce a result. Here there genuinely is
 * one — the timer stopped without anyone choosing to stop.
 *
 * States what happened and offers the next move. Deliberately no streak, no score, no celebration:
 * the summary reports, it does not congratulate. Stopping is a first-class choice sitting beside
 * the others rather than a dismissal hidden in the corner — a Pomodoro app that makes stopping feel
 * like failure is the thing this feature is trying not to be.
 */
@Composable
internal fun SessionFinishedSheet(
    finished: FocusContract.FinishedSession,
    nextSessionMinutes: Int,
    onEvent: (FocusContract.UiEvent) -> Unit,
) {
    TempoModalBottomSheet(
        onDismissRequest = { onEvent(FocusContract.UiEvent.DismissFinishedSession) },
    ) { _ ->
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = adaptiveHorizontalMargin())
                    .padding(top = 12.dp, bottom = 8.dp),
        ) {
            FinishedHeading(finished = finished)
            FinishedChoices(
                finished = finished,
                nextSessionMinutes = nextSessionMinutes,
                onEvent = onEvent,
            )
        }
    }
}

@Composable
private fun ColumnScope.FinishedHeading(finished: FocusContract.FinishedSession) {
    Text(
        text =
            if (finished.wasBreak) {
                stringResource(R.string.focus_session_break_over_heading)
            } else {
                stringResource(R.string.focus_session_finished_heading)
            },
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.Bold,
    )
    Text(
        // A break's minutes were not spent on the task, so only a focus session names them.
        text =
            if (finished.wasBreak) {
                finished.title
            } else {
                stringResource(
                    R.string.focus_session_finished_subtitle,
                    finished.title,
                    finished.minutes,
                )
            },
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 4.dp),
    )
}

@Composable
private fun ColumnScope.FinishedChoices(
    finished: FocusContract.FinishedSession,
    nextSessionMinutes: Int,
    onEvent: (FocusContract.UiEvent) -> Unit,
) {
    val colors = sessionScreenActionColors()
    val carryOn =
        if (finished.wasBreak) {
            SessionAction(
                label = stringResource(R.string.focus_session_back_to_it),
                icon = painterResource(R.drawable.ic_play_arrow),
                emphasis = ButtonEmphasis.FILLED,
                onClick = { onEvent(FocusContract.UiEvent.StartAnotherSession) },
            )
        } else {
            SessionAction(
                label = stringResource(R.string.focus_session_take_break, finished.breakMinutes),
                icon = painterResource(R.drawable.ic_coffee),
                emphasis = ButtonEmphasis.FILLED,
                onClick = { onEvent(FocusContract.UiEvent.TakeBreak) },
            )
        }
    // "Done for now" said the work was unfinished while wearing a tick, and did neither — it only
    // closed the sheet. This is the same act, and the same word, as marking the task done anywhere
    // else in the app. Closing without deciding is still there: the sheet dismisses on a swipe or
    // a tap outside.
    // Only where there is a task: a saved session linked to none has nothing to tick (#517), and
    // its sheet offers the way on alone.
    val markDone =
        SessionAction(
            label = stringResource(R.string.focus_session_mark_done),
            icon = doneIcon(),
            emphasis = ButtonEmphasis.TONAL,
            onClick = { onEvent(FocusContract.UiEvent.CompleteSessionTask) },
        ).takeIf { finished.hasTask }

    // The two first-class choices as one connected control, matching the card and the session
    // screen. Finishing stays a peer here rather than a dismissal in the corner: a Pomodoro app
    // that makes stopping feel like failure is the thing this feature is trying not to be.
    SessionActionGroup(
        actions = listOfNotNull(carryOn, markDone),
        colors = colors,
        modifier = Modifier.fillMaxWidth().padding(top = 20.dp),
        compact = true,
    )

    // A break already offers going back to the work, so it needs no second way to say so.
    if (!finished.wasBreak) {
        SessionActionButton(
            action =
                SessionAction(
                    // The configured length, not the elapsed time: this offers the next session,
                    // and stopping early must not shrink what "another" means.
                    label = stringResource(R.string.focus_session_another, nextSessionMinutes),
                    icon = painterResource(R.drawable.ic_play_arrow),
                    emphasis = ButtonEmphasis.QUIET,
                    onClick = { onEvent(FocusContract.UiEvent.StartAnotherSession) },
                ),
            colors = colors,
            modifier = Modifier.padding(top = 8.dp),
            compact = true,
        )
    }
}
