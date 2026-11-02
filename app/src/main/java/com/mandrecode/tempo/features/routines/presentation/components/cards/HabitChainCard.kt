package com.mandrecode.tempo.features.routines.presentation.components.cards

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import com.mandrecode.tempo.R
import com.mandrecode.tempo.core.ui.components.cardFrostColor
import com.mandrecode.tempo.core.ui.components.glassCardElevation
import com.mandrecode.tempo.core.ui.components.selectedContainerColor
import com.mandrecode.tempo.core.ui.theme.LocalIsDarkTheme
import com.mandrecode.tempo.core.ui.theme.TempoIcon
import com.mandrecode.tempo.core.ui.theme.TempoSpacing.cardLeadingContentGap
import com.mandrecode.tempo.core.ui.theme.chainTitle
import com.mandrecode.tempo.core.ui.theme.resolveColor
import com.mandrecode.tempo.core.ui.util.EnhancedDescriptionText
import com.mandrecode.tempo.core.ui.util.sanitizeDescription
import com.mandrecode.tempo.features.routines.domain.model.Habit
import com.mandrecode.tempo.features.routines.domain.model.HabitChain
import com.mandrecode.tempo.util.CompletionHistoryUtil
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.todayIn
import kotlin.time.Clock

/** Where the chain's title sits in the card: the time beside it is centred on this line. */
private val ChainTitleTopPadding = 14.dp

/**
 * The height of the chain title's first line, from the text style rather than a fixed dp, so the
 * time beside it stays centred on the title at any font scale.
 */
@Composable
private fun chainTitleLineHeight(): Dp {
    val lineHeight = MaterialTheme.typography.chainTitle.lineHeight
    return with(LocalDensity.current) {
        if (lineHeight.isSpecified) lineHeight.toDp() else DefaultChainTitleLineHeight
    }
}

private val DefaultChainTitleLineHeight = 32.dp

@Composable
fun HabitChainCard(
    habitChain: HabitChain,
    chainHabits: List<Habit>,
    selectedDate: LocalDate,
    onEdit: () -> Unit,
    modifier: Modifier = Modifier,
    onAddHabit: (() -> Unit)? = null,
    onHabitToggle: ((Long, Boolean) -> Unit)? = null,
    onHabitClick: ((Long) -> Unit)? = null,
    timeLabel: String? = null,
    showTimeline: Boolean = true,
    timelineExtension: Dp = 0.dp,
    isSelected: Boolean = false,
    selectedHabitId: Long? = null,
) {
    val dateStr = selectedDate.toString()
    val today = Clock.System.todayIn(TimeZone.currentSystemDefault())
    val yesterday = today.minus(DatePeriod(days = 1))

    val isFuture = selectedDate > today

    // Toggling is only allowed for Yesterday and Today
    val canToggle = selectedDate == today || selectedDate == yesterday

    val (completedCount, totalCount, progress) =
        remember(chainHabits, dateStr) {
            val total = chainHabits.size
            if (total == 0) {
                Triple(0, 0, 0f)
            } else {
                val completed =
                    chainHabits.count { habit ->
                        CompletionHistoryUtil.isDateInHistory(habit.completionHistory, dateStr)
                    }
                Triple(completed, total, completed.toFloat() / total.toFloat())
            }
        }

    val allCompleted = completedCount == totalCount && totalCount > 0
    val sanitizedChainDescription =
        remember(habitChain.description) { sanitizeDescription(habitChain.description) }

    val cardCornerRadius by animateDpAsState(
        targetValue = if (allCompleted) 20.dp else 28.dp,
        animationSpec =
            spring(
                dampingRatio = Spring.DampingRatioNoBouncy, // More stable for day transitions
                stiffness = Spring.StiffnessMedium,
            ),
        label = "habit_chain_card_corner_radius",
    )

    val isDarkTheme = LocalIsDarkTheme.current
    val resolvedChainColor =
        resolveColor(
            colorKey = habitChain.colorKey,
            colorScheme = MaterialTheme.colorScheme,
            isDarkTheme = isDarkTheme,
        )

    val baseContainerColor =
        if (allCompleted) {
            resolvedChainColor ?: MaterialTheme.colorScheme.primary
        } else {
            resolvedChainColor?.copy(alpha = 0.15f)?.compositeOver(cardFrostColor())
                ?: cardFrostColor()
        }
    val containerColor by animateColorAsState(
        targetValue = selectedContainerColor(baseContainerColor, isSelected),
        animationSpec = tween(300),
        label = "habit_chain_card_color",
    )

    val contentColor by animateColorAsState(
        targetValue =
            if (allCompleted) {
                MaterialTheme.colorScheme.onPrimary
            } else {
                MaterialTheme.colorScheme.onSurface
            },
        animationSpec = tween(300),
        label = "habit_chain_card_content_color",
    )

    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec =
            spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = Spring.StiffnessMedium,
            ),
        label = "habit_chain_progress",
    )

    val cardShape = RoundedCornerShape(cardCornerRadius)

    // Title, progress and icons share the chain colour until every habit is done, then the
    // card's content colour.
    val accentColor =
        if (allCompleted) contentColor else resolvedChainColor ?: MaterialTheme.colorScheme.primary

    Row(
        // The caller's modifier reaches the root, which it never used to: the parameter was
        // declared and dropped, so a list handing this card an item animation got nothing back.
        modifier =
            modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Max),
        horizontalArrangement = Arrangement.Start,
    ) {
        if (showTimeline) {
            TimelineTime(
                timeLabel = timeLabel,
                firstRowTop = ChainTitleTopPadding,
                firstRowHeight = chainTitleLineHeight(),
            )

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
            Box(modifier = Modifier.fillMaxWidth()) {
                // Faded chain icon behind the header, top right
                habitChain.icon?.let { iconName ->
                    TempoIcon.fromName(iconName)?.let { habitIcon ->
                        Box(modifier = Modifier.matchParentSize()) {
                            Icon(
                                painter = painterResource(habitIcon.iconRes),
                                contentDescription = null,
                                modifier =
                                    Modifier
                                        .align(Alignment.TopEnd)
                                        .size(120.dp)
                                        .offset(x = 24.dp, y = (-16).dp)
                                        .graphicsLayer {
                                            alpha = 0.08f
                                            rotationZ = -25f
                                        },
                                tint = accentColor,
                            )
                        }
                    }
                }

                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(top = ChainTitleTopPadding),
                ) {
                    HabitChainHeader(
                        title = habitChain.title,
                        description = sanitizedChainDescription,
                        accentColor = accentColor,
                        contentColor = contentColor,
                    )

                    if (!isFuture && totalCount > 0) {
                        ChainProgress(
                            completedCount = completedCount,
                            totalCount = totalCount,
                            progress = { animatedProgress },
                            allCompleted = allCompleted,
                            accentColor = accentColor,
                            contentColor = contentColor,
                        )
                    }

                    // The chain's habits are always listed (#454): they are what you tick off,
                    // so hiding them behind an expand arrow only added a tap. Adding one is the
                    // list's last row, where the new habit will appear.
                    Column(modifier = Modifier.padding(top = 4.dp, start = 4.dp, end = 4.dp)) {
                        chainHabits.forEach { habit ->
                            key(habit.id) {
                                val habitIsCompleted =
                                    CompletionHistoryUtil.isDateInHistory(habit.completionHistory, dateStr)
                                HabitItem(
                                    habit = habit,
                                    isCompleted = habitIsCompleted,
                                    onToggle = { onHabitToggle?.invoke(habit.id, !habitIsCompleted) },
                                    onClick = { onHabitClick?.invoke(habit.id) },
                                    color = resolvedChainColor,
                                    // Same row as a standalone habit; only the colours come from
                                    // the chain.
                                    cardShape = RoundedCornerShape(24.dp),
                                    contentColor = contentColor,
                                    canToggle = canToggle,
                                    isContainerCompleted = allCompleted,
                                    isSelected = habit.id == selectedHabitId,
                                )
                            }
                        }

                        if (onAddHabit != null) {
                            AddHabitRow(
                                onClick = onAddHabit,
                                accentColor = accentColor,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Title and description; nothing else competes for the header's width. */
@Composable
private fun HabitChainHeader(
    title: String,
    description: String,
    accentColor: Color,
    contentColor: Color,
) {
    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.chainTitle,
            color = accentColor,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )

        if (description.isNotBlank()) {
            EnhancedDescriptionText(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = contentColor.copy(alpha = 0.7f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/**
 * A short bar with the done/total count beside it; once every habit is done, a single
 * "All done" chip instead, since a full bar next to 3/3 says the same thing twice.
 */
@Composable
private fun ChainProgress(
    completedCount: Int,
    totalCount: Int,
    progress: () -> Float,
    allCompleted: Boolean,
    accentColor: Color,
    contentColor: Color,
) {
    Row(
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (!allCompleted) {
            LinearProgressIndicator(
                progress = progress,
                modifier =
                    Modifier
                        .width(ChainProgressBarWidth)
                        .height(6.dp)
                        .clip(CircleShape),
                color = accentColor,
                trackColor = accentColor.copy(alpha = 0.15f),
                drawStopIndicator = {},
            )
        }

        val pillShape = RoundedCornerShape(50)
        Surface(
            shape = pillShape,
            // The accent is already the content colour once every habit is done.
            color = accentColor.copy(alpha = if (allCompleted) 0.2f else 0.12f),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (allCompleted) {
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp).padding(end = 2.dp),
                        tint = contentColor,
                    )
                }
                Text(
                    text =
                        if (allCompleted) {
                            stringResource(R.string.habit_chain_card_all_done, totalCount)
                        } else {
                            stringResource(R.string.habit_chain_progress_count, completedCount, totalCount)
                        },
                    style = MaterialTheme.typography.labelSmall,
                    color = contentColor,
                )
            }
        }
    }
}

/**
 * The list's last row: a dashed circle where the checkbox would be, then "Add habit". Same size
 * as a habit row, so the card ends with the same 12dp a standalone habit has under its checkbox.
 */
@Composable
private fun AddHabitRow(
    onClick: () -> Unit,
    accentColor: Color,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier =
                Modifier
                    .size(HabitCheckboxSize)
                    .drawBehind {
                        drawCircle(
                            color = accentColor.copy(alpha = 0.6f),
                            radius = size.minDimension / 2 - 1.dp.toPx(),
                            style =
                                Stroke(
                                    width = 1.5.dp.toPx(),
                                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx())),
                                ),
                        )
                    },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.Add,
                contentDescription = null,
                modifier = Modifier.size(22.dp),
                tint = accentColor,
            )
        }
        Spacer(modifier = Modifier.width(cardLeadingContentGap))
        Text(
            text = stringResource(R.string.add_habit),
            style = MaterialTheme.typography.bodyLarge,
            color = accentColor,
        )
    }
}

private val ChainProgressBarWidth = 110.dp
