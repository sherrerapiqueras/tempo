package com.mandrecode.tempo.features.routines.presentation.components.sections

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mandrecode.tempo.R
import com.mandrecode.tempo.core.domain.model.DayOfWeek
import com.mandrecode.tempo.core.domain.model.VacationPeriod
import com.mandrecode.tempo.core.ui.theme.LocalIsDarkTheme
import com.mandrecode.tempo.core.ui.theme.PastelGreenDark
import com.mandrecode.tempo.core.ui.theme.PastelGreenLight
import com.mandrecode.tempo.core.ui.theme.PastelPurpleDark
import com.mandrecode.tempo.core.ui.theme.PastelPurpleLight
import com.mandrecode.tempo.core.ui.theme.PastelYellowDark
import com.mandrecode.tempo.core.ui.theme.PastelYellowLight
import com.mandrecode.tempo.features.routines.domain.model.HabitType
import com.mandrecode.tempo.util.CompletionHistoryUtil
import com.mandrecode.tempo.util.DateTimeFormatter
import com.mandrecode.tempo.util.StreakStatus
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import kotlin.time.Clock

private val DotSize = 10.dp
private val DotSpacing = 3.dp
private const val PAUSED_DOT_ALPHA = 0.3f
internal const val HABIT_HISTORY_ELLIPSIS_TEST_TAG = "habit_history_ellipsis"
internal const val HABIT_HISTORY_DOT_ROW_TEST_TAG = "habit_history_dot_row"
internal const val HABIT_HISTORY_STREAK_PILL_TEST_TAG = "habit_history_streak_pill"

// Streak milestone tiers from #547: yellow at one week, green at two, purple at three.
private const val STREAK_TIER_YELLOW_DAYS = 7
private const val STREAK_TIER_GREEN_DAYS = 14
private const val STREAK_TIER_PURPLE_DAYS = 21

// Fixed pill corner radius: fully rounds a single-line pill at default font size, yet stays
// inside the text padding when the label wraps, so the corners never clip it.
private val STREAK_PILL_CORNER_RADIUS = 12.dp
private val STREAK_PILL_HORIZONTAL_PADDING = 8.dp
private val STREAK_PILL_VERTICAL_PADDING = 3.dp
private const val STREAK_LABEL_MAX_WIDTH_FRACTION = 0.5f

private data class StreakLabelUiModel(
    val text: String,
    val minWidth: Dp,
)

/**
 * A GitHub-style contribution graph for habit tracking.
 * Shows the last N days of completion history with dots and a streak label.
 * The number of visible dots adapts to the available width.
 */
@Composable
fun HabitHistoryView(
    completionHistory: String,
    createdDate: LocalDate,
    modifier: Modifier = Modifier,
    maxDays: Int = 21,
    repeatDays: Set<DayOfWeek>? = null,
    habitType: HabitType = HabitType.BUILD,
    today: LocalDate = Clock.System.todayIn(TimeZone.currentSystemDefault()),
    vacationPeriods: List<VacationPeriod> = emptyList(),
) {
    // Parse completion history (format: "2024-01-01,2024-01-03,...")
    val completedDates =
        remember(completionHistory) {
            if (completionHistory.isBlank()) {
                emptySet()
            } else {
                completionHistory
                    .split(",")
                    .mapNotNull {
                        try {
                            LocalDate.parse(it.trim())
                        } catch (_: Exception) {
                            null
                        }
                    }.toSet()
            }
        }

    // Effective start date, accounting for backfilled completions before creation
    val effectiveCreatedDate =
        remember(createdDate, completedDates) {
            val oldestCompletion = completedDates.minOrNull()
            if (oldestCompletion != null && oldestCompletion < createdDate) {
                oldestCompletion
            } else {
                createdDate
            }
        }

    // Calculate the date range to display
    val daysToShow =
        remember(effectiveCreatedDate, today, maxDays) {
            val daysSinceCreation = (today.toEpochDays() - effectiveCreatedDate.toEpochDays()).toInt()
            minOf(maxOf(daysSinceCreation + 1, 1), maxDays)
        }

    // Generate dates for the last N days
    val dateRange =
        remember(today, daysToShow) {
            (daysToShow - 1 downTo 0).map { daysAgo ->
                LocalDate.fromEpochDays((today.toEpochDays() - daysAgo).toInt())
            }
        }

    val streakStatus =
        remember(completionHistory, repeatDays, today, vacationPeriods) {
            CompletionHistoryUtil.getStreakStatus(completionHistory, today, repeatDays, vacationPeriods)
        }

    val streakLabelUiModel =
        rememberStreakLabelUiModel(
            completionHistory = completionHistory,
            streakStatus = streakStatus,
            daysToShow = daysToShow,
            habitType = habitType,
            today = today,
            repeatDays = repeatDays,
            vacationPeriods = vacationPeriods,
        )

    BoxWithConstraints(
        modifier =
            modifier
                .padding(vertical = 4.dp)
                .padding(start = 4.dp),
    ) {
        // Cap the label so a long one (e.g. the recovering copy at a large font scale) wraps
        // instead of taking the dots' space.
        val maxLabelWidth = maxWidth * STREAK_LABEL_MAX_WIDTH_FRACTION
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BoxWithConstraints(
                modifier = Modifier.weight(1f),
            ) {
                val fittingDots = computeFittingDots(maxWidth)
                val needsTruncation = dateRange.size > fittingDots
                // Reserve one dot's worth of space for the ellipsis when truncating,
                // but show at least one dot (skip ellipsis if space is too tight).
                // When nothing fits at all, render nothing.
                val effectiveMaxDots =
                    if (needsTruncation && fittingDots >= 2) {
                        fittingDots - 1
                    } else {
                        minOf(fittingDots, dateRange.size)
                    }

                val visibleDates = dateRange.takeLast(effectiveMaxDots)
                val showEllipsis = needsTruncation && fittingDots >= 2

                Row(
                    modifier = Modifier.testTag(HABIT_HISTORY_DOT_ROW_TEST_TAG),
                    horizontalArrangement = Arrangement.spacedBy(DotSpacing),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (showEllipsis) {
                        Text(
                            text = stringResource(R.string.ellipsis),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.testTag(HABIT_HISTORY_ELLIPSIS_TEST_TAG),
                        )
                    }

                    visibleDates.forEach { date ->
                        val isScheduled = CompletionHistoryUtil.isScheduledOn(date, repeatDays)
                        val isCompleted = isScheduled && date in completedDates
                        val isBeforeCreation = date < effectiveCreatedDate
                        // A completion recorded while paused still reads as completed, matching the
                        // streak rule that credits it.
                        val isPaused =
                            isScheduled &&
                                !isCompleted &&
                                CompletionHistoryUtil.isPausedOn(date, vacationPeriods)

                        HabitDot(
                            date = date,
                            isCompleted = isCompleted,
                            isBeforeCreation = isBeforeCreation,
                            isScheduled = isScheduled,
                            isPaused = isPaused,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            StreakLabel(
                text = streakLabelUiModel.text,
                streak = streakStatus.streak,
                minWidth = streakLabelUiModel.minWidth,
                maxWidth = maxLabelWidth,
            )
        }
    }
}

@Composable
private fun rememberStreakLabelUiModel(
    completionHistory: String,
    streakStatus: StreakStatus,
    daysToShow: Int,
    habitType: HabitType,
    today: LocalDate,
    repeatDays: Set<DayOfWeek>?,
    vacationPeriods: List<VacationPeriod>,
): StreakLabelUiModel {
    val noStreakText =
        if (daysToShow <= 1) {
            stringResource(R.string.habit_history_today)
        } else {
            pluralStringResource(R.plurals.habit_history_last_days, daysToShow, daysToShow)
        }
    val streakText = streakLabelText(streakStatus, habitType) ?: noStreakText
    val toggledStreakStatus =
        remember(completionHistory, today, repeatDays, vacationPeriods) {
            val isTodayCompleted = CompletionHistoryUtil.isDateInHistory(completionHistory, today.toString())
            val toggledHistory =
                CompletionHistoryUtil.updateCompletionHistoryForDate(
                    currentHistory = completionHistory,
                    date = today,
                    isCompleted = !isTodayCompleted,
                )
            CompletionHistoryUtil.getStreakStatus(
                completionHistory = toggledHistory,
                today = today,
                repeatDays = repeatDays,
                vacationPeriods = vacationPeriods,
            )
        }
    val toggledStreakText = streakLabelText(toggledStreakStatus, habitType) ?: noStreakText

    val textMeasurer = rememberTextMeasurer()
    val labelTextStyle = MaterialTheme.typography.labelSmall
    val density = LocalDensity.current
    val minWidth =
        remember(streakText, noStreakText, toggledStreakText, labelTextStyle, textMeasurer, density) {
            val maxLabelWidthPx =
                listOf(noStreakText, streakText, toggledStreakText)
                    .maxOf { candidate ->
                        textMeasurer
                            .measure(
                                text = AnnotatedString(candidate),
                                style = labelTextStyle,
                                maxLines = 1,
                            ).size.width
                    }
            with(density) {
                maxLabelWidthPx.toDp() + (STREAK_PILL_HORIZONTAL_PADDING * 2)
            }
        }

    return StreakLabelUiModel(
        text = streakText,
        minWidth = minWidth,
    )
}

/**
 * The streak label for [status], or `null` when there is no streak. A streak surviving a missed
 * day (#422) gets the encouraging variant.
 */
@Composable
private fun streakLabelText(
    status: StreakStatus,
    habitType: HabitType,
): String? {
    if (status.streak <= 0) return null
    val pluralRes =
        when {
            habitType == HabitType.QUIT && status.isRecovering -> R.plurals.habit_streak_quit_recovering_label
            habitType == HabitType.QUIT -> R.plurals.habit_streak_quit_label
            status.isRecovering -> R.plurals.habit_streak_recovering_label
            else -> R.plurals.habit_streak_label
        }
    return pluralStringResource(pluralRes, status.streak, status.streak)
}

/**
 * Renders the streak label. For active streaks (>0) wraps the text in a pill whose
 * color reflects the milestone tier from issue #547:
 *  - >= 21 days: purple ("habit built")
 *  - >= 14 days: green
 *  - >= 7 days:  yellow
 *  - 1..6 days:  neutral pill (onSurfaceVariant)
 *  - 0 days:     pill with transparent background and neutral onSurfaceVariant text.
 *                The Surface is still rendered with the same padding/shape as the
 *                colored pill so the 0->1 transition does not pop the layout — only
 *                the background color and text color animate.
 */
@Composable
private fun StreakLabel(
    text: String,
    streak: Int,
    minWidth: Dp,
    maxWidth: Dp,
) {
    val hasStreak = streak > 0
    val tierColor = streakTierColor(streak)
    val neutralColor = MaterialTheme.colorScheme.onSurfaceVariant

    // Animate the pill background and text color so the transition from
    // "no streak" -> "1 day streak" doesn't pop the layout. The container
    // size is kept stable by always rendering the Surface with the same
    // padding, so the host bottom sheet doesn't have to re-measure.
    val backgroundColor by animateColorAsState(
        targetValue =
            if (hasStreak) {
                tierColor.copy(alpha = 0.12f)
            } else {
                Color.Transparent
            },
        animationSpec = tween(durationMillis = 220),
        label = "streakPillBackground",
    )
    val textColor by animateColorAsState(
        targetValue = if (hasStreak) tierColor else neutralColor,
        animationSpec = tween(durationMillis = 220),
        label = "streakPillTextColor",
    )

    val pillShape = RoundedCornerShape(STREAK_PILL_CORNER_RADIUS)
    Surface(
        modifier = Modifier.testTag(HABIT_HISTORY_STREAK_PILL_TEST_TAG),
        shape = pillShape,
        color = backgroundColor,
    ) {
        Box(
            modifier = Modifier.widthIn(min = minWidth.coerceAtMost(maxWidth), max = maxWidth),
            contentAlignment = Alignment.CenterEnd,
        ) {
            AnimatedContent(
                targetState = text,
                transitionSpec = {
                    (
                        fadeIn(animationSpec = tween(durationMillis = 180)) togetherWith
                            fadeOut(animationSpec = tween(durationMillis = 120))
                    ).using(SizeTransform(clip = false))
                },
                label = "streakPillText",
            ) { animatedText ->
                Text(
                    text = animatedText,
                    style = MaterialTheme.typography.labelSmall,
                    color = textColor,
                    textAlign = TextAlign.End,
                    modifier =
                        Modifier.padding(
                            horizontal = STREAK_PILL_HORIZONTAL_PADDING,
                            vertical = STREAK_PILL_VERTICAL_PADDING,
                        ),
                )
            }
        }
    }
}

@Composable
private fun streakTierColor(streak: Int): Color {
    val isDark = LocalIsDarkTheme.current
    return when {
        streak >= STREAK_TIER_PURPLE_DAYS -> if (isDark) PastelPurpleDark else PastelPurpleLight
        streak >= STREAK_TIER_GREEN_DAYS -> if (isDark) PastelGreenDark else PastelGreenLight
        streak >= STREAK_TIER_YELLOW_DAYS -> if (isDark) PastelYellowDark else PastelYellowLight
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
}

/**
 * Computes how many dots fit in the given [availableWidth].
 * Called inside the weight(1f) slot, so the width already excludes the streak label.
 */
@Composable
private fun computeFittingDots(availableWidth: Dp): Int {
    val density = LocalDensity.current
    val availablePx = with(density) { availableWidth.toPx() }
    val dotPx = with(density) { DotSize.toPx() }
    val spacingPx = with(density) { DotSpacing.toPx() }
    // Each dot occupies dotSize + spacing (except the last one)
    return if (availablePx < dotPx) {
        0
    } else {
        ((availablePx + spacingPx) / (dotPx + spacingPx)).toInt()
    }
}

@Composable
private fun HabitDot(
    date: LocalDate,
    isCompleted: Boolean,
    isBeforeCreation: Boolean,
    isScheduled: Boolean,
    isPaused: Boolean = false,
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val outlineColor = MaterialTheme.colorScheme.outlineVariant

    // Animate the fill color so today's dot smoothly transitions from
    // outlined (not completed) to filled (completed) when the user checks
    // the habit from the bottom sheet.
    val backgroundColor by animateColorAsState(
        targetValue =
            when {
                !isScheduled -> outlineColor.copy(alpha = 0.35f)
                isCompleted -> primaryColor
                // Paused days sit between "done" and "missed": a faded fill of the completed
                // color, so the gap next to an unchanged streak label reads as intentional.
                isPaused -> primaryColor.copy(alpha = PAUSED_DOT_ALPHA)
                else -> Color.Transparent
            },
        animationSpec = tween(durationMillis = 220),
        label = "habitDotBackground",
    )
    // Only an in-range scheduled day that is neither done nor paused shows as an empty ring.
    val isMissed = isScheduled && !isCompleted && !isPaused
    // Border fades out as the fill comes in (only relevant for in-range, scheduled dots).
    val borderWidth by animateDpAsState(
        targetValue =
            if (isMissed && !isBeforeCreation) {
                1.5.dp
            } else {
                0.dp
            },
        animationSpec = tween(durationMillis = 220),
        label = "habitDotBorder",
    )

    val dateString =
        DateTimeFormatter.formatDate(
            date = date,
            context = LocalContext.current,
            useNaturalDates = true,
        )
    val description =
        when {
            !isScheduled -> stringResource(R.string.habit_history_dot_unscheduled, dateString)
            isCompleted -> stringResource(R.string.habit_history_dot_completed, dateString)
            isPaused -> stringResource(R.string.habit_history_dot_paused, dateString)
            else -> stringResource(R.string.habit_history_dot_missed, dateString)
        }

    Box(
        modifier =
            Modifier
                .size(DotSize)
                .clip(CircleShape)
                .background(backgroundColor)
                .then(
                    if (borderWidth > 0.dp) {
                        Modifier.border(
                            width = borderWidth,
                            color = outlineColor,
                            shape = CircleShape,
                        )
                    } else {
                        Modifier
                    },
                ).semantics { contentDescription = description },
    )
}
