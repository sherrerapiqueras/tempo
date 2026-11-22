package com.mandrecode.tempo.features.focus.presentation.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mandrecode.tempo.R
import com.mandrecode.tempo.core.domain.model.DailyFocusActivity
import com.mandrecode.tempo.core.domain.model.FocusDayState
import com.mandrecode.tempo.core.ui.theme.TempoMotionTokens
import com.mandrecode.tempo.features.focus.domain.model.FocusHeadlineBand
import com.mandrecode.tempo.util.DateTimeFormatter
import kotlinx.collections.immutable.ImmutableList
import kotlinx.datetime.LocalDate

private val HeroCornerRadius = 24.dp
private val HistoryDotSize = 10.dp
private val HistoryDotSpacing = 4.dp
private val ProgressIndicatorSize = 64.dp

/**
 * The Focus summary card.
 *
 * The streak sits in the small header line beside the weekday rather than in the headline: what the
 * user wants first is how today is going, not how long the run is. The headline itself is band
 * copy, so it changes as the day fills in without ever quoting a percentage.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun FocusSummaryHero(
    today: LocalDate?,
    streakDays: Int,
    history: ImmutableList<DailyFocusActivity>,
    scheduledCount: Int,
    completedCount: Int,
    progress: Float,
    band: FocusHeadlineBand,
    modifier: Modifier = Modifier,
) {
    val progressDescription =
        pluralStringResource(R.plurals.focus_progress_description, scheduledCount, completedCount, scheduledCount)
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec =
            androidx.compose.animation.core
                .tween(TempoMotionTokens.DURATION_STANDARD_MILLIS),
        label = "focus_day_progress",
    )

    val heroShape = RoundedCornerShape(HeroCornerRadius)
    Surface(
        modifier = modifier,
        shape = heroShape,
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                HeroHeaderLine(today = today, streakDays = streakDays)
                Text(
                    text = stringResource(band.headlineRes),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
                if (history.isNotEmpty()) {
                    HistoryRow(
                        history = history,
                        modifier = Modifier.padding(top = 10.dp),
                    )
                }
            }

            CircularWavyProgressIndicator(
                progress = { animatedProgress },
                modifier =
                    Modifier
                        .size(ProgressIndicatorSize)
                        .semantics {
                            contentDescription = progressDescription
                        },
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                trackColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = TRACK_ALPHA),
                // Frozen wave: the hero reports the day, it is not an activity in progress. Motion
                // here would read as something happening right now, which is the session's job.
                waveSpeed = STILL,
            )
        }
    }
}

@Composable
private fun HeroHeaderLine(
    today: LocalDate?,
    streakDays: Int,
) {
    val weekday = today?.let { DateTimeFormatter.formatWeekday(it) }
    val streak =
        if (streakDays > 0) {
            pluralStringResource(R.plurals.focus_streak_days, streakDays, streakDays)
        } else {
            null
        }
    val header = listOfNotNull(weekday, streak).joinToString(SEPARATOR)

    if (header.isNotBlank()) {
        Text(
            text = header,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = HEADER_ALPHA),
        )
    }
}

/**
 * The last N days as dots, in the same three states the full history grid uses: a ramp would imply
 * a precision these counts do not have.
 */
@Composable
private fun HistoryRow(
    history: ImmutableList<DailyFocusActivity>,
    modifier: Modifier = Modifier,
) {
    val onContainer = MaterialTheme.colorScheme.onPrimaryContainer
    val description = pluralStringResource(R.plurals.focus_history_description, history.size, history.size)
    Row(
        modifier =
            modifier.semantics {
                contentDescription = description
            },
        horizontalArrangement = Arrangement.spacedBy(HistoryDotSpacing),
    ) {
        history.forEach { day ->
            Box(
                modifier =
                    Modifier
                        .size(HistoryDotSize)
                        .clip(CircleShape)
                        .background(onContainer.forState(day.state)),
            )
        }
    }
}

private fun Color.forState(state: FocusDayState): Color =
    when (state) {
        FocusDayState.QUIET -> copy(alpha = QUIET_DOT_ALPHA)
        FocusDayState.SOME -> copy(alpha = SOME_DOT_ALPHA)
        FocusDayState.ALL -> this
    }

private val FocusHeadlineBand.headlineRes: Int
    get() =
        when (this) {
            FocusHeadlineBand.NOTHING_SCHEDULED -> R.string.focus_headline_nothing_scheduled
            FocusHeadlineBand.JUST_STARTED -> R.string.focus_headline_just_started
            FocusHeadlineBand.UNDER_WAY -> R.string.focus_headline_under_way
            FocusHeadlineBand.NEARLY_THERE -> R.string.focus_headline_nearly_there
            FocusHeadlineBand.COMPLETE -> R.string.focus_headline_complete
        }

private val STILL = 0.dp
private const val SEPARATOR = " · "
private const val HEADER_ALPHA = 0.78f
private const val TRACK_ALPHA = 0.24f
private const val QUIET_DOT_ALPHA = 0.22f
private const val SOME_DOT_ALPHA = 0.55f
