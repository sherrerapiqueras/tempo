package com.mandrecode.tempo.features.routines.presentation.components.sections

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ButtonGroup
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.material3.ToggleButtonShapes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.mandrecode.tempo.R
import com.mandrecode.tempo.core.ui.adaptive.adaptiveHorizontalMargin
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock

private val DaySegmentHeight = 56.dp
private val TodayMarkerSize = 4.dp

private const val DAYS_BEFORE_TODAY = 1
private const val DAYS_AFTER_TODAY = 5

/** Yesterday through five days ahead: the window Routines lets you look at. */
internal fun routineFilterDays(today: LocalDate): List<LocalDate> =
    (-DAYS_BEFORE_TODAY..DAYS_AFTER_TODAY).map { today.plus(DatePeriod(days = it)) }

/**
 * The week Routines shows (#335): seven days as one connected, single-select [ButtonGroup], each
 * segment a short weekday over its date. All seven fit the width without scrolling; today carries a
 * dot, and every segment is announced with its relative name ("Today", "Tomorrow") or full weekday.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun DayFilterRow(
    selectedDate: LocalDate,
    onSelectDate: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    val today =
        Clock.System
            .now()
            .toLocalDateTime(TimeZone.currentSystemDefault())
            .date
    val days = remember(today) { routineFilterDays(today) }
    val haptic = LocalHapticFeedback.current

    ButtonGroup(
        overflowIndicator = {},
        modifier = modifier.fillMaxWidth().padding(horizontal = adaptiveHorizontalMargin(), vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
    ) {
        days.forEachIndexed { index, date ->
            customItem(
                buttonGroupContent = {
                    val interactionSource = remember { MutableInteractionSource() }
                    val description = dayDescription(date, today)
                    val isSelected = date == selectedDate
                    ToggleButton(
                        checked = isSelected,
                        onCheckedChange = {
                            if (!isSelected) {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onSelectDate(date)
                            }
                        },
                        shapes = segmentShapes(index, days.lastIndex),
                        colors =
                            ToggleButtonDefaults.colors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                checkedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                checkedContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            ),
                        interactionSource = interactionSource,
                        contentPadding = PaddingValues(0.dp),
                        modifier =
                            Modifier
                                .weight(1f)
                                // A floor, not a fixed height: at large font sizes the two lines and
                                // today's dot need more, and the whole row grows with them.
                                .heightIn(min = DaySegmentHeight)
                                .animateWidth(interactionSource)
                                .semantics {
                                    role = Role.RadioButton
                                    contentDescription = description
                                },
                    ) {
                        DaySegmentLabel(date = date, isToday = date == today)
                    }
                },
                menuContent = {},
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun segmentShapes(
    index: Int,
    lastIndex: Int,
): ToggleButtonShapes =
    when (index) {
        0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
        lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
        else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
    }

@Composable
private fun DaySegmentLabel(
    date: LocalDate,
    isToday: Boolean,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = stringResource(date.dayOfWeek.shortNameRes()), style = MaterialTheme.typography.labelSmall)
        Text(text = date.day.toString(), style = MaterialTheme.typography.titleSmall)
        // Today's dot keeps its place on every day, so the labels line up across the row.
        Box(
            modifier =
                Modifier
                    .padding(top = 2.dp)
                    .size(TodayMarkerSize)
                    .background(if (isToday) MaterialTheme.colorScheme.primary else Color.Transparent, CircleShape),
        )
    }
}

@Composable
private fun dayDescription(
    date: LocalDate,
    today: LocalDate,
): String {
    val relative =
        when (date) {
            today -> R.string.today
            today.minus(DatePeriod(days = 1)) -> R.string.yesterday
            today.plus(DatePeriod(days = 1)) -> R.string.tomorrow
            else -> null
        }
    val weekday = stringResource(date.dayOfWeek.fullNameRes())
    // Whole phrases with placeholders, so each language orders and punctuates them its own way.
    return if (relative != null) {
        stringResource(R.string.day_filter_relative_day_description, stringResource(relative), weekday, date.day)
    } else {
        stringResource(R.string.day_filter_day_description, weekday, date.day)
    }
}

private fun DayOfWeek.shortNameRes(): Int =
    when (this) {
        DayOfWeek.MONDAY -> R.string.mon
        DayOfWeek.TUESDAY -> R.string.tue
        DayOfWeek.WEDNESDAY -> R.string.wed
        DayOfWeek.THURSDAY -> R.string.thu
        DayOfWeek.FRIDAY -> R.string.fri
        DayOfWeek.SATURDAY -> R.string.sat
        DayOfWeek.SUNDAY -> R.string.sun
    }

private fun DayOfWeek.fullNameRes(): Int =
    when (this) {
        DayOfWeek.MONDAY -> R.string.monday
        DayOfWeek.TUESDAY -> R.string.tuesday
        DayOfWeek.WEDNESDAY -> R.string.wednesday
        DayOfWeek.THURSDAY -> R.string.thursday
        DayOfWeek.FRIDAY -> R.string.friday
        DayOfWeek.SATURDAY -> R.string.saturday
        DayOfWeek.SUNDAY -> R.string.sunday
    }
