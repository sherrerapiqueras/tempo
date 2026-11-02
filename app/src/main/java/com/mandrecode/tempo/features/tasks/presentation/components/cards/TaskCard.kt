package com.mandrecode.tempo.features.tasks.presentation.components.cards

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.mandrecode.tempo.R
import com.mandrecode.tempo.core.ui.components.CardGroupPosition
import com.mandrecode.tempo.core.ui.components.cardFrostColor
import com.mandrecode.tempo.core.ui.components.glassCardElevation
import com.mandrecode.tempo.core.ui.components.selectedContainerColor
import com.mandrecode.tempo.core.ui.theme.MutedContentAlpha
import com.mandrecode.tempo.core.ui.theme.TempoSpacing.cardContentPadding
import com.mandrecode.tempo.core.ui.theme.TempoSpacing.cardLeadingContentGap
import com.mandrecode.tempo.core.ui.theme.cardTitle
import com.mandrecode.tempo.core.ui.util.EnhancedDescriptionText
import com.mandrecode.tempo.core.ui.util.color
import com.mandrecode.tempo.core.ui.util.sanitizeDescription
import com.mandrecode.tempo.features.tasks.domain.model.Category
import com.mandrecode.tempo.features.tasks.domain.model.Task
import kotlin.math.roundToInt

private val SubtaskFallbackItemHeight = 68.dp

// Shared duration for card content size and color animations so layout, color, and
// metadata-slot transitions finish together, avoiding a "sluggish" feeling where
// layout snaps mid color-fade.
internal const val CARD_CONTENT_ANIM_DURATION_MS = 200

/** How much of the frost a completed card keeps: it recedes, as the old 40% background did. */
private const val COMPLETED_FROST_FRACTION = 0.5f
internal const val TASK_METADATA_COMPLETED_DATE_TAG = "task_metadata_completed_date"
internal const val TASK_METADATA_REMINDER_DATE_TAG = "task_metadata_reminder_date"
internal const val TASK_COMPLETION_CONTROL_TAG = "task_completion_control"
internal const val TASK_CONTENT_TAG = "task_content"
internal const val TASK_TRAILING_ACTIONS_TAG = "task_trailing_actions"
internal const val TASK_DESCRIPTION_TAG = "task_description"

// Asymmetric fade durations for the SubtaskMetadataRow appearance/disappearance:
// fade-in is slightly slower so the badge feels like it settles into place, while
// fade-out is quicker so it gets out of the way for the toggle action.
internal const val METADATA_ROW_FADE_IN_MS = 180
internal const val METADATA_ROW_FADE_OUT_MS = 120

/**
 * Whether the metadata row has anything to say.
 *
 * Gated rather than always drawn: an empty row would push a title-only task off centre against its
 * checkbox. Tasks that already carry any metadata keep MetadataRow's own reserved badge slot, so a
 * reminder becoming a completion date does not change the card's height.
 *
 * Subtasks are excluded from the question entirely — a step's metadata belongs to its own row.
 */
private fun Task.hasMetadataToShow(
    category: Category?,
    subtasks: List<Task>,
): Boolean {
    if (parentTaskId != null) return false
    return category != null ||
        priority != null ||
        periodicity != null ||
        reminderDate != null ||
        completedAt != null ||
        hasAttachments ||
        subtasks.isNotEmpty()
}

@Composable
fun TaskItem(
    task: Task,
    onToggleCompletion: (Task) -> Unit,
    onEdit: (Task) -> Unit,
    modifier: Modifier = Modifier,
    onToggleSubtasksExpansion: (Boolean) -> Unit = {},
    onAddSubtask: (Long) -> Unit = {},
    onReorderSubtasks: (fromIndex: Int, toIndex: Int, subtasks: List<Task>) -> Unit = { _, _, _ -> },
    subtasks: List<Task> = emptyList(),
    isSubtasksExpanded: Boolean = true,
    initialDescriptionExpanded: Boolean = false,
    isSelected: Boolean = false,
    /**
     * Shown as the first label of the metadata row when set. Off by default: the Tasks list is
     * already grouped by category, where repeating it on every card would be noise. It earns its
     * place only where a card sits next to work drawn from every category at once.
     */
    category: Category? = null,
    /**
     * Extra controls belonging to this task, drawn inside the card beneath its content.
     *
     * A slot rather than a fixed set of buttons, because what belongs here is whatever the
     * surrounding surface is *for* — and putting those controls outside the card would leave the
     * action for a task sitting on no task in particular.
     */
    footer: (@Composable () -> Unit)? = null,
    /**
     * Controls belonging to this task, drawn *beside* its content rather than beneath it.
     *
     * The same slot content a narrow card puts in [footer]. On a wide card the text column stops
     * needing the whole width long before the controls stop needing a home, and stacking them
     * underneath spends a band of height on space the row already had to its right.
     *
     * An offer, not an instruction: the card takes it only while it is folded, and falls back to
     * [footer] once a description or a list of steps has made the content column tall. Pass both.
     *
     * Drawn after the card's own trailing icons, so it finishes at the card's edge and lines up
     * down the list whether or not a given row has a chevron to show.
     */
    trailingContent: (@Composable () -> Unit)? = null,
    /**
     * How the checkbox, the text and the trailing slot line up against each other.
     *
     * Top by default, which is what a list of mostly-text cards wants: the checkbox sits against the
     * title whatever the description does below it. A card carrying [trailingContent] is a different
     * shape — two columns of comparable height rather than one tall one with icons pinned beside it
     * — and there the three parts want centring on each other instead of hanging from the top edge.
     */
    verticalAlignment: Alignment.Vertical = Alignment.Top,
    /**
     * On by default, because in a list of work "break this down" is always a reasonable next move.
     * Off where the card is on screen to be answered rather than worked on — a surface asking one
     * question of every row has no business offering a second.
     */
    showAddSubtaskAction: Boolean = true,
    /** Where this card sits in a connected run of cards; its corners follow. */
    groupPosition: CardGroupPosition = CardGroupPosition.SINGLE,
) {
    val haptic = LocalHapticFeedback.current
    var isDescriptionOverflowing by remember { mutableStateOf(false) }
    val sanitizedDescription = remember(task.description) { sanitizeDescription(task.description) }

    var isDescriptionExpanded by remember(task.id) { mutableStateOf(initialDescriptionExpanded) }

    val frost = cardFrostColor()
    val baseCardColor =
        // A completed card recedes into the frost.
        if (task.isCompleted) {
            frost.copy(alpha = frost.alpha * COMPLETED_FROST_FRACTION)
        } else {
            frost
        }
    val cardColor by animateColorAsState(
        targetValue = selectedContainerColor(baseCardColor, isSelected),
        animationSpec = tween(CARD_CONTENT_ANIM_DURATION_MS),
        label = "card_color",
    )

    val cardScale by animateFloatAsState(
        targetValue = if (task.isCompleted) 0.98f else 1f,
        animationSpec =
            spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessLow,
            ),
        label = "card_scale",
    )

    val cardOffset by animateDpAsState(
        targetValue = if (task.isCompleted) 6.dp else 0.dp,
        animationSpec =
            spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = Spring.StiffnessMedium,
            ),
        label = "card_offset",
    )

    val cardCornerRadius by animateDpAsState(
        targetValue = if (task.isCompleted) 24.dp else 32.dp,
        animationSpec =
            spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = Spring.StiffnessMedium,
            ),
        label = "card_corner_radius",
    )

    val checkboxRadius by animateDpAsState(
        targetValue = if (task.isCompleted) 16.dp else 24.dp,
        animationSpec =
            spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = Spring.StiffnessMedium,
            ),
        label = "checkbox_radius",
    )

    val checkboxScale by animateFloatAsState(
        targetValue = if (task.isCompleted) 1.1f else 1f,
        animationSpec =
            spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMedium,
            ),
        label = "checkbox_scale",
    )

    val cardShape = groupPosition.animatedShape(cardCornerRadius)

    Card(
        onClick = { onEdit(task) },
        modifier =
            modifier
                .fillMaxWidth()
                .graphicsLayer {
                    translationY = cardOffset.toPx()
                }.semantics { selected = isSelected }
                .scale(cardScale),
        shape = cardShape,
        border = null,
        colors = CardDefaults.cardColors(containerColor = cardColor),
        elevation = glassCardElevation(),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
        ) {
            // Both sides of the split have to agree on a height for the side column to reach the
            // card's edges: the row takes the shortest height its content allows, and the content
            // then fills it. Only where there *is* a side column — an intrinsic pass costs a second
            // measure of every card in the list, and filling a height nothing has fixed yet makes
            // the content as tall as whatever bound it was handed, which off a list is the screen.
            // A card unfolded is a tall text column with a short one of controls beside it, and
            // dividing that vertically leaves the controls stranded in the middle of a column of
            // nothing while the checkbox drifts away from the title it belongs to. So the split is
            // for the card at rest: once it unfolds, the slot drops back underneath the content and
            // the card reads the way it does in the Tasks list, which is where the steps now are.
            val isUnfolded = isDescriptionExpanded || (subtasks.isNotEmpty() && isSubtasksExpanded)
            val hasSideColumn = trailingContent != null && !isUnfolded
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .then(if (hasSideColumn) Modifier.height(IntrinsicSize.Min) else Modifier),
            ) {
                Row(
                    modifier =
                        Modifier
                            .weight(1f)
                            .then(if (hasSideColumn) Modifier.fillMaxHeight() else Modifier)
                            .padding(cardContentPadding),
                    // Centring only means anything while the two halves are of a height. Unfolded,
                    // the checkbox and the chevron belong against the title, as everywhere else.
                    verticalAlignment = if (hasSideColumn) verticalAlignment else Alignment.Top,
                ) {
                    // Sized to match the title Box's heightIn(min = 48.dp) below — intentionally
                    // smaller than HabitItem's 56dp checkbox; see the comment there for why. Keep
                    // this in sync with that Box's heightIn(min) if either changes.
                    val completionA11yLabel =
                        stringResource(
                            if (task.isCompleted) R.string.mark_as_not_completed else R.string.mark_as_completed,
                        )
                    Box(
                        modifier =
                            Modifier
                                .testTag(TASK_COMPLETION_CONTROL_TAG)
                                .size(48.dp)
                                .semantics {
                                    role = Role.Checkbox
                                    toggleableState = if (task.isCompleted) ToggleableState.On else ToggleableState.Off
                                    contentDescription = completionA11yLabel
                                }.graphicsLayer {
                                    scaleX = checkboxScale
                                    scaleY = checkboxScale
                                }.clip(RoundedCornerShape(checkboxRadius))
                                .clickable {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onToggleCompletion(task)
                                },
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(
                            modifier =
                                Modifier
                                    .size(48.dp)
                                    .background(
                                        color =
                                            if (task.isCompleted) {
                                                MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                            } else {
                                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)
                                            },
                                        shape = RoundedCornerShape(checkboxRadius),
                                    ).then(
                                        if (!task.isCompleted) {
                                            Modifier.border(
                                                width = 2.dp,
                                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f),
                                                shape = RoundedCornerShape(checkboxRadius),
                                            )
                                        } else {
                                            Modifier
                                        },
                                    ),
                        )

                        if (task.isCompleted) {
                            Icon(
                                imageVector = Icons.Filled.Check,
                                contentDescription = null,
                                modifier = Modifier.size(24.dp),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(cardLeadingContentGap))

                    Column(
                        modifier =
                            Modifier
                                .testTag(TASK_CONTENT_TAG)
                                .weight(1f)
                                .animateContentSize(animationSpec = tween(CARD_CONTENT_ANIM_DURATION_MS)),
                    ) {
                        Box(
                            modifier = Modifier.heightIn(min = 48.dp),
                            contentAlignment = Alignment.CenterStart,
                        ) {
                            Column(
                                verticalArrangement = Arrangement.Center,
                            ) {
                                Text(
                                    text = task.title,
                                    style =
                                        MaterialTheme.typography.cardTitle.copy(
                                            textDecoration = if (task.isCompleted) TextDecoration.LineThrough else null,
                                        ),
                                    color =
                                        if (task.isCompleted) {
                                            MaterialTheme.colorScheme.onSurface.copy(alpha = MutedContentAlpha.TITLE)
                                        } else {
                                            MaterialTheme.colorScheme.onSurface
                                        },
                                    maxLines = if (isDescriptionExpanded) Int.MAX_VALUE else 2,
                                    overflow = TextOverflow.Ellipsis,
                                )

                                if (sanitizedDescription.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    EnhancedDescriptionText(
                                        text = sanitizedDescription,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color =
                                            if (task.isCompleted) {
                                                MaterialTheme.colorScheme.onSurface.copy(
                                                    alpha = MutedContentAlpha.DESCRIPTION,
                                                )
                                            } else {
                                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                            },
                                        maxLines = if (isDescriptionExpanded) Int.MAX_VALUE else 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.testTag(TASK_DESCRIPTION_TAG),
                                        onTextLayout = { textLayoutResult ->
                                            isDescriptionOverflowing =
                                                if (isDescriptionExpanded) {
                                                    textLayoutResult.lineCount > 1
                                                } else {
                                                    textLayoutResult.hasVisualOverflow
                                                }
                                        },
                                    )
                                }

                                // The rare title-only first-completion case animates smoothly via the
                                // animateContentSize on the enclosing Column.
                                if (task.hasMetadataToShow(category, subtasks)) {
                                    MetadataRow(task, subtasks, category)
                                }
                            }
                        }
                    }

                    val showExpandButton = subtasks.isNotEmpty() || isDescriptionOverflowing
                    val isExpanded =
                        if (subtasks.isNotEmpty()) isSubtasksExpanded else isDescriptionExpanded

                    Row(
                        modifier = Modifier.testTag(TASK_TRAILING_ACTIONS_TAG),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (showExpandButton) {
                            IconButton(
                                onClick = {
                                    if (subtasks.isNotEmpty()) {
                                        onToggleSubtasksExpansion(!isSubtasksExpanded)
                                        isDescriptionExpanded = !isSubtasksExpanded
                                    } else {
                                        isDescriptionExpanded = !isDescriptionExpanded
                                    }
                                },
                                modifier = Modifier.size(48.dp),
                            ) {
                                Icon(
                                    painter =
                                        painterResource(
                                            if (isExpanded) {
                                                R.drawable.ic_expand_less
                                            } else {
                                                R.drawable.ic_expand_more
                                            },
                                        ),
                                    contentDescription =
                                        if (isExpanded) {
                                            stringResource(R.string.collapse)
                                        } else {
                                            stringResource(R.string.expand)
                                        },
                                    modifier = Modifier.size(24.dp),
                                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                )
                            }
                        }

                        // Not on a card that is itself a step of something. Tasks nests exactly one
                        // level — only top-level tasks become cards, and a subtask row draws no
                        // children of its own — so a task added under a subtask would exist with
                        // nowhere in Tasks to show it. Focus is where this bites: it gives a dated
                        // subtask its own card when the parent is off the day, and that card carried
                        // the same button as any other.
                        //
                        // And not where the surrounding surface has asked its own question of the row.
                        if (task.parentTaskId == null && showAddSubtaskAction) {
                            IconButton(
                                onClick = { onAddSubtask(task.id) },
                                modifier = Modifier.size(48.dp),
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_add_row_below),
                                    contentDescription = stringResource(R.string.add_subtask),
                                    modifier = Modifier.size(24.dp),
                                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                )
                            }
                        }
                    }
                }

                // Outside the content's padding and outside the card's own icons, so it reaches the
                // card's edges rather than floating within them: a column of the card, divided off,
                // rather than a panel dropped on top of one. It only needs its inner corners — the
                // card clips the outer ones to whatever radius it is currently animating through.
                if (hasSideColumn) trailingContent?.invoke()
            }

            AnimatedVisibility(
                visible = subtasks.isNotEmpty() && isSubtasksExpanded,
                enter = expandVertically(spring(stiffness = Spring.StiffnessMedium)) + fadeIn(),
                exit = shrinkVertically(spring(stiffness = Spring.StiffnessMedium)) + fadeOut(),
            ) {
                val density = LocalDensity.current
                var draggedIndex by remember { mutableIntStateOf(-1) }
                var dragOffset by remember { mutableFloatStateOf(0f) }
                var targetIndex by remember { mutableIntStateOf(-1) }
                var measuredItemHeight by remember { mutableFloatStateOf(0f) }
                val spacingPx = remember(density) { with(density) { 8.dp.toPx() } }

                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, end = 16.dp, bottom = 16.dp, top = 0.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    subtasks.forEachIndexed { index, subtask ->
                        val isDragging = draggedIndex == index
                        val isTarget = targetIndex == index

                        SubtaskItem(
                            subtask = subtask,
                            onToggleCompletion = onToggleCompletion,
                            onEdit = onEdit,
                            haptic = haptic,
                            modifier =
                                Modifier
                                    .onSizeChanged { size ->
                                        if (measuredItemHeight == 0f) {
                                            measuredItemHeight = size.height.toFloat()
                                        }
                                    }.zIndex(if (isDragging) 1f else 0f)
                                    .graphicsLayer {
                                        if (isDragging) {
                                            translationY = dragOffset
                                            alpha = 0.8f
                                        } else if (isTarget) {
                                            alpha = 0.5f
                                        }
                                    }.pointerInput(subtasks) {
                                        detectDragGesturesAfterLongPress(
                                            onDragStart = {
                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                draggedIndex = index
                                                dragOffset = 0f
                                                targetIndex = index
                                            },
                                            onDrag = { change, dragAmount ->
                                                change.consume()
                                                dragOffset += dragAmount.y

                                                val itemHeight =
                                                    if (measuredItemHeight > 0f) {
                                                        measuredItemHeight + spacingPx
                                                    } else {
                                                        with(density) { SubtaskFallbackItemHeight.toPx() }
                                                    }
                                                val newTargetIndex =
                                                    (index + (dragOffset / itemHeight).roundToInt())
                                                        .coerceIn(0, subtasks.size - 1)
                                                if (newTargetIndex != targetIndex) {
                                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                }
                                                targetIndex = newTargetIndex
                                            },
                                            onDragEnd = {
                                                if (draggedIndex != targetIndex && targetIndex >= 0) {
                                                    onReorderSubtasks(
                                                        draggedIndex,
                                                        targetIndex,
                                                        subtasks,
                                                    )
                                                }
                                                draggedIndex = -1
                                                dragOffset = 0f
                                                targetIndex = -1
                                            },
                                            onDragCancel = {
                                                draggedIndex = -1
                                                dragOffset = 0f
                                                targetIndex = -1
                                            },
                                        )
                                    },
                        )
                    }
                }
            }

            // After the steps, not between them and the task they belong to. The band is shaped
            // to close the card — flush along the bottom, rounded only on top — and it can only
            // do that from the end of the column. Set above the steps it cut a task away from
            // what it is made of, and its flat edge ran into the first of them.
            //
            // Edge to edge, and the slot supplies its own padding: only it knows whether it is a
            // surface that has to reach the card's sides or content that should line up with the
            // text above.
            if (!hasSideColumn) footer?.invoke()
        }
    }
}
