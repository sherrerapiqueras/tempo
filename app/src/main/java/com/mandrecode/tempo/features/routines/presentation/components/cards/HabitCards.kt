package com.mandrecode.tempo.features.routines.presentation.components.cards

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mandrecode.tempo.R
import com.mandrecode.tempo.core.ui.components.cardFrostColor
import com.mandrecode.tempo.core.ui.components.glassCardElevation
import com.mandrecode.tempo.core.ui.components.selectedContainerColor
import com.mandrecode.tempo.core.ui.components.selectedTintOverlayColor
import com.mandrecode.tempo.core.ui.theme.LocalIsDarkTheme
import com.mandrecode.tempo.core.ui.theme.MutedContentAlpha
import com.mandrecode.tempo.core.ui.theme.TempoIcon
import com.mandrecode.tempo.core.ui.theme.TempoSpacing.cardLeadingContentGap
import com.mandrecode.tempo.core.ui.theme.cardTitle
import com.mandrecode.tempo.core.ui.theme.resolveColor
import com.mandrecode.tempo.core.ui.theme.timelineTime
import com.mandrecode.tempo.core.ui.util.EnhancedDescriptionText
import com.mandrecode.tempo.core.ui.util.sanitizeDescription
import com.mandrecode.tempo.features.routines.domain.model.Habit
import com.mandrecode.tempo.util.CompletionHistoryUtil
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.todayIn
import kotlin.time.Clock

private const val HABIT_COLLAPSED_MAX_LINES = 1

/**
 * Habit rows are a little more compact than they used to be (#454): a 48dp checkbox with 12dp of
 * vertical padding, about 72dp in all. A habit inside a chain is exactly the same row.
 */
internal val HabitCheckboxSize = 48.dp

@Composable
internal fun HabitItem(
    habit: Habit,
    isCompleted: Boolean,
    onToggle: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color? = null,
    cardShape: Shape = RoundedCornerShape(24.dp),
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    horizontalPadding: Dp = 16.dp,
    verticalPadding: Dp = 12.dp,
    checkboxSize: Dp = HabitCheckboxSize,
    canToggle: Boolean = true,
    isContainerCompleted: Boolean = isCompleted,
    isSelected: Boolean = false,
    enableClick: Boolean = true,
    trailingContent: (@Composable () -> Unit)? = null,
) {
    val haptic = LocalHapticFeedback.current
    var isDescriptionExpanded by remember(habit.id) { mutableStateOf(false) }
    var isDescriptionOverflowing by remember(habit.id) { mutableStateOf(false) }
    val sanitizedDescription =
        remember(habit.id, habit.description) { sanitizeDescription(habit.description) }

    LaunchedEffect(sanitizedDescription) {
        isDescriptionExpanded = false
        isDescriptionOverflowing = false
    }

    val descriptionMaxLines = if (isDescriptionExpanded) Int.MAX_VALUE else HABIT_COLLAPSED_MAX_LINES

    // Snappier spring for radius morphing: a circle that squares off a little once done.
    val checkboxRadius by animateDpAsState(
        targetValue = if (isCompleted) checkboxSize * 0.3f else checkboxSize / 2,
        animationSpec =
            spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = Spring.StiffnessMedium,
            ),
        label = "checkbox_radius",
    )

    // Visual "pop" scale animation for toggling — matches TaskItem's checkbox scale (1.1f).
    val checkboxScale by animateFloatAsState(
        targetValue = if (isCompleted) 1.1f else 1f,
        animationSpec =
            spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMedium,
            ),
        label = "checkbox_scale",
    )

    val checkboxBgColor by animateColorAsState(
        targetValue =
            if (isCompleted) {
                if (!isContainerCompleted) {
                    (color ?: MaterialTheme.colorScheme.primary).copy(alpha = 0.08f)
                } else {
                    MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.15f)
                }
            } else {
                (color ?: MaterialTheme.colorScheme.primary).copy(alpha = 0.12f)
            },
        animationSpec = tween(300),
        label = "checkbox_bg_color",
    )

    val iconTintColor by animateColorAsState(
        targetValue =
            if (!isContainerCompleted) {
                color ?: MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onPrimary
            },
        animationSpec = tween(300),
        label = "icon_tint_color",
    )

    val selectionColor by animateColorAsState(
        targetValue = selectedTintOverlayColor(isSelected),
        animationSpec = tween(220),
        label = "habit_item_selection_color",
    )

    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(cardShape)
                .background(selectionColor)
                .semantics { selected = isSelected }
                .then(if (enableClick) Modifier.clickable { onClick() } else Modifier)
                .padding(horizontal = horizontalPadding, vertical = verticalPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Animated checkbox, sized to match the content Column's heightIn(min = checkboxSize)
        // below. Smaller than TaskItem's 48dp checkbox on purpose (#454): a habit is a single
        // quick tap, so its row stays compact; the whole row remains tappable for editing.
        val completionA11yLabel =
            stringResource(
                if (isCompleted) R.string.mark_as_not_completed else R.string.mark_as_completed,
            )
        Box(
            modifier =
                Modifier
                    .size(checkboxSize)
                    .semantics {
                        role = Role.Checkbox
                        toggleableState = if (isCompleted) ToggleableState.On else ToggleableState.Off
                        contentDescription = completionA11yLabel
                        if (!canToggle) disabled()
                    }.graphicsLayer {
                        scaleX = checkboxScale
                        scaleY = checkboxScale
                        alpha = if (canToggle) 1f else 0.5f
                    }.clip(RoundedCornerShape(checkboxRadius))
                    .clickable(enabled = canToggle) {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onToggle()
                    },
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier =
                    Modifier
                        .size(checkboxSize)
                        .background(
                            color = checkboxBgColor,
                            shape = RoundedCornerShape(checkboxRadius),
                        ).then(
                            if (!isCompleted) {
                                Modifier.border(
                                    width = 2.dp,
                                    color =
                                        (
                                            color
                                                ?: MaterialTheme.colorScheme.primary
                                        ).copy(alpha = 0.4f),
                                    shape = RoundedCornerShape(checkboxRadius),
                                )
                            } else {
                                Modifier
                            },
                        ),
            )

            if (isCompleted) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = null,
                    modifier = Modifier.size(checkboxSize / 2),
                    tint = iconTintColor,
                )
            } else {
                // Show habit icon if available
                habit.icon?.let { iconName ->
                    TempoIcon.fromName(iconName)?.let { habitIcon ->
                        Icon(
                            painter = painterResource(habitIcon.iconRes),
                            contentDescription = null,
                            modifier =
                                Modifier
                                    .size(checkboxSize / 2)
                                    .testTag("habitIcon"),
                            tint = iconTintColor,
                        )
                    }
                }
            }

            // Visual indicator when toggle is disabled
            if (!canToggle) {
                Box(
                    modifier =
                        Modifier
                            .matchParentSize()
                            .background(Color.Black.copy(alpha = 0.08f)),
                )
            }
        }

        Spacer(modifier = Modifier.width(cardLeadingContentGap))

        Column(
            modifier =
                Modifier
                    .weight(1f)
                    .heightIn(min = checkboxSize),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = habit.title,
                style =
                    MaterialTheme.typography.cardTitle.copy(
                        textDecoration = if (isCompleted) TextDecoration.LineThrough else null,
                    ),
                color = if (isCompleted) contentColor.copy(alpha = MutedContentAlpha.TITLE) else contentColor,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )

            if (sanitizedDescription.isNotBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                EnhancedDescriptionText(
                    text = sanitizedDescription,
                    style = MaterialTheme.typography.bodyMedium,
                    color =
                        if (isCompleted) {
                            contentColor.copy(alpha = MutedContentAlpha.DESCRIPTION)
                        } else {
                            contentColor.copy(alpha = 0.7f)
                        },
                    maxLines = descriptionMaxLines,
                    overflow = TextOverflow.Ellipsis,
                    onTextLayout = { textLayoutResult ->
                        isDescriptionOverflowing =
                            if (isDescriptionExpanded) {
                                textLayoutResult.lineCount > HABIT_COLLAPSED_MAX_LINES
                            } else {
                                textLayoutResult.hasVisualOverflow
                            }
                    },
                )
            }
        }

        if (isDescriptionOverflowing) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.align(Alignment.Top),
            ) {
                IconButton(
                    onClick = { isDescriptionExpanded = !isDescriptionExpanded },
                    modifier = Modifier.size(checkboxSize),
                ) {
                    Icon(
                        painter =
                            painterResource(
                                if (isDescriptionExpanded) {
                                    R.drawable.ic_expand_less
                                } else {
                                    R.drawable.ic_expand_more
                                },
                            ),
                        contentDescription =
                            if (isDescriptionExpanded) {
                                stringResource(R.string.collapse)
                            } else {
                                stringResource(R.string.expand)
                            },
                        modifier = Modifier.size(24.dp),
                        tint = contentColor.copy(alpha = 0.6f),
                    )
                }
            }
        }

        if (trailingContent != null) {
            Spacer(modifier = Modifier.width(8.dp))
            trailingContent()
        }
    }
}

/**
 * The time a scheduled card sits at, in the column beside the card, centred on the card's first
 * row. A habit card is a single row, so that is its middle; a chain card is tall, so its time stays
 * with the chain's title ([firstRowHeight] below [firstRowTop]) rather than floating beside
 * whichever habit sits in its middle. Set in the theme's tertiary, the timeline's own accent: it is
 * readable on the panel in every palette, and apart from both the habit colours on the cards and
 * the primary used for selection.
 */
@Composable
internal fun RowScope.TimelineTime(
    timeLabel: String?,
    modifier: Modifier = Modifier,
    firstRowTop: Dp = 0.dp,
    firstRowHeight: Dp? = null,
) {
    val sizing = if (firstRowHeight != null) Modifier.height(firstRowHeight) else Modifier.fillMaxHeight()
    Box(
        modifier =
            modifier
                .width(TimelineTimeColumnWidth)
                .padding(top = firstRowTop)
                .then(sizing)
                .padding(end = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (timeLabel != null) {
            Text(
                text = timeLabel,
                style = MaterialTheme.typography.timelineTime,
                color = MaterialTheme.colorScheme.tertiary,
            )
        }
    }
}

/**
 * The timeline's vertical line beside a card, one colour for every card (the tertiary of the times
 * beside it, softened) so it reads as a single line down the day rather than a stripe per habit.
 * [extendBelow] carries it across the gap to the next card.
 */
@Composable
internal fun TimelineRail(
    extendBelow: Dp,
    modifier: Modifier = Modifier,
) {
    val lineColor = MaterialTheme.colorScheme.tertiary.copy(alpha = TIMELINE_LINE_ALPHA)
    Box(
        modifier =
            modifier
                .width(2.dp)
                .fillMaxHeight()
                .drawBehind {
                    drawRect(lineColor, size = Size(size.width, size.height + extendBelow.toPx()))
                },
    )
}

private val TimelineTimeColumnWidth = 64.dp
private const val TIMELINE_LINE_ALPHA = 0.4f

@Composable
fun HabitCard(
    habit: Habit,
    selectedDate: LocalDate,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    onToggle: ((Long, Boolean) -> Unit)? = null,
    timeLabel: String? = null,
    showTimeline: Boolean = true,
    timelineExtension: Dp = 0.dp,
    isSelected: Boolean = false,
) {
    val dateStr = selectedDate.toString()
    val isCompleted =
        remember(habit.completionHistory, dateStr) {
            CompletionHistoryUtil.isDateInHistory(habit.completionHistory, dateStr)
        }

    val today = remember { Clock.System.todayIn(TimeZone.currentSystemDefault()) }
    val yesterday = remember(today) { today.minus(DatePeriod(days = 1)) }

    // Toggling is only allowed for Yesterday and Today
    val canToggle = selectedDate == today || selectedDate == yesterday

    // A compact habit row reads as a pill; it squares off a little once done.
    val cardCornerRadius by animateDpAsState(
        targetValue = if (isCompleted) 24.dp else 32.dp,
        animationSpec =
            spring(
                dampingRatio = Spring.DampingRatioNoBouncy, // More stable for day transitions
                stiffness = Spring.StiffnessMedium,
            ),
        label = "habit_card_corner_radius",
    )

    val isDarkTheme = LocalIsDarkTheme.current
    val resolvedHabitColor =
        resolveColor(
            colorKey = habit.colorKey,
            colorScheme = MaterialTheme.colorScheme,
            isDarkTheme = isDarkTheme,
        )

    val baseContainerColor =
        if (isCompleted) {
            resolvedHabitColor ?: MaterialTheme.colorScheme.primary
        } else {
            resolvedHabitColor?.copy(alpha = 0.15f)?.compositeOver(cardFrostColor())
                ?: cardFrostColor()
        }
    val containerColor by animateColorAsState(
        targetValue = selectedContainerColor(baseContainerColor, isSelected),
        animationSpec = tween(300),
        label = "habit_card_color",
    )

    val contentColor by animateColorAsState(
        targetValue =
            if (isCompleted) {
                MaterialTheme.colorScheme.onPrimary
            } else {
                MaterialTheme.colorScheme.onSurface
            },
        animationSpec = tween(300),
        label = "habit_card_content_color",
    )

    val cardShape = RoundedCornerShape(cardCornerRadius)

    Row(
        // As in HabitChainCard: the parameter existed but never reached the root, so a caller's
        // item animation was silently dropped.
        modifier =
            modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Max),
        horizontalArrangement = Arrangement.Start,
    ) {
        if (showTimeline) {
            TimelineTime(timeLabel)

            TimelineRail(extendBelow = timelineExtension)

            Spacer(modifier = Modifier.width(16.dp))
        }

        Card(
            onClick = onEdit,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .semantics { selected = isSelected },
            shape = cardShape,
            border = null,
            colors =
                CardDefaults.cardColors(
                    containerColor = containerColor,
                    contentColor = contentColor,
                ),
            elevation = glassCardElevation(),
        ) {
            HabitItem(
                habit = habit,
                isCompleted = isCompleted,
                onToggle = { onToggle?.invoke(habit.id, !isCompleted) },
                onClick = onEdit,
                color = resolvedHabitColor,
                cardShape = cardShape,
                contentColor = contentColor,
                canToggle = canToggle,
                enableClick = false,
            )
        }
    }
}
