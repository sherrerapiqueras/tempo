package com.mandrecode.tempo.features.tasks.presentation.components.sections

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mandrecode.tempo.R
import com.mandrecode.tempo.core.ui.adaptive.adaptiveHorizontalMargin
import com.mandrecode.tempo.core.ui.theme.LocalIsDarkTheme
import com.mandrecode.tempo.core.ui.theme.TempoIcon
import com.mandrecode.tempo.core.ui.theme.categoryChipSelected
import com.mandrecode.tempo.core.ui.theme.categoryChipUnselected
import com.mandrecode.tempo.core.ui.theme.metadataLabel
import com.mandrecode.tempo.core.ui.theme.resolveColor
import com.mandrecode.tempo.core.ui.util.rememberPressableChipAnimation
import com.mandrecode.tempo.core.ui.util.rememberPressableIconButtonAnimation
import com.mandrecode.tempo.features.tasks.domain.model.Category
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlin.math.roundToInt

@Composable
fun CategoryChipRow(
    categories: List<Category>,
    counts: Map<Long, Int>,
    selectedCategoryId: Long,
    onSelectCategory: (Long) -> Unit,
    onShowCategoryDialog: (Category?) -> Unit,
    onRequestDeleteCategory: (Category) -> Unit,
    onReorderCategories: (fromIndex: Int, toIndex: Int, categories: List<Category>) -> Unit,
    modifier: Modifier = Modifier,
    onAddSection: () -> Unit = {},
) {
    val listState = rememberLazyListState()
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current
    var draggedIndex by remember { mutableIntStateOf(-1) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    var targetIndex by remember { mutableIntStateOf(-1) }

    // Default category is pinned first; prevent dropping onto its position
    val minDropIndex = if (categories.firstOrNull()?.isDefault == true) 1 else 0

    LaunchedEffect(selectedCategoryId) {
        val index = categories.indexOfFirst { it.id == selectedCategoryId }
        if (index < 0) return@LaunchedEffect

        // Wait for layout to be ready, then check full visibility
        val isFullyVisible =
            snapshotFlow {
                val info = listState.layoutInfo
                if (info.visibleItemsInfo.isEmpty()) {
                    null
                } else {
                    val item = info.visibleItemsInfo.firstOrNull { it.index == index }
                    item != null &&
                        item.offset >= info.viewportStartOffset &&
                        (item.offset + item.size) <= info.viewportEndOffset
                }
            }.filterNotNull().first()

        if (!isFullyVisible) {
            listState.animateScrollToItem(index)
        }
    }

    LazyRow(
        state = listState,
        modifier =
            modifier
                .fillMaxWidth()
                .height(84.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(horizontal = adaptiveHorizontalMargin()),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        itemsIndexed(categories, key = { _, cat -> cat.id }) { index, category ->
            val isDragging = draggedIndex == index
            val isTarget = targetIndex == index
            val canDrag = !category.isDefault

            CategoryItem(
                category = category,
                count = counts[category.id] ?: 0,
                isSelected = selectedCategoryId == category.id,
                onSelectCategory = { onSelectCategory(category.id) },
                onEdit = { onShowCategoryDialog(category) },
                onRequestDelete = { onRequestDeleteCategory(category) },
                onAddSection = onAddSection,
                modifier =
                    Modifier
                        .graphicsLayer {
                            if (isDragging) {
                                translationX = dragOffset
                                alpha = 0.8f
                            } else if (isTarget) {
                                alpha = 0.5f
                            }
                        }.then(
                            if (canDrag) {
                                Modifier.pointerInput(categories) {
                                    val itemWidthWithSpacing =
                                        size.width + with(density) { 12.dp.toPx() }
                                    detectDragGesturesAfterLongPress(
                                        onDragStart = {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            draggedIndex = index
                                            dragOffset = 0f
                                            targetIndex = index
                                        },
                                        onDrag = { change, dragAmount ->
                                            change.consume()
                                            dragOffset += dragAmount.x
                                            val newTarget =
                                                (index + (dragOffset / itemWidthWithSpacing).roundToInt())
                                                    .coerceIn(minDropIndex, categories.size - 1)
                                            if (newTarget != targetIndex) {
                                                haptic.performHapticFeedback(
                                                    HapticFeedbackType.TextHandleMove,
                                                )
                                            }
                                            targetIndex = newTarget
                                        },
                                        onDragEnd = {
                                            if (draggedIndex != targetIndex && targetIndex >= 0) {
                                                onReorderCategories(
                                                    draggedIndex,
                                                    targetIndex,
                                                    categories,
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
                                }
                            } else {
                                Modifier
                            },
                        ),
            )
        }

        item {
            NewCategoryButton(
                onClick = { onShowCategoryDialog(null) },
            )
        }
    }
}

@Composable
private fun CategoryItem(
    category: Category,
    count: Int,
    isSelected: Boolean,
    onSelectCategory: () -> Unit,
    onEdit: () -> Unit,
    onRequestDelete: () -> Unit,
    onAddSection: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var isDropdownExpanded by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current
    val interactionSource = remember { MutableInteractionSource() }

    val animatedCornerRadius by rememberPressableChipAnimation(
        isSelected = isSelected,
        interactionSource = interactionSource,
        selectedRadius = 20.dp,
        unselectedRadius = 16.dp,
        pressedRadius = 12.dp,
    )

    val colorScheme = MaterialTheme.colorScheme
    val isDarkTheme = LocalIsDarkTheme.current
    val categoryColor = resolveColor(category.color, colorScheme, isDarkTheme)

    val containerColor by animateColorAsState(
        targetValue =
            if (isSelected) {
                categoryColor?.copy(alpha = 0.15f)
                    ?: MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            },
        label = "containerColor",
    )

    val contentColor by animateColorAsState(
        targetValue =
            if (isSelected) {
                categoryColor ?: MaterialTheme.colorScheme.onPrimaryContainer
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        label = "contentColor",
    )

    val height by animateDpAsState(
        targetValue = if (isSelected) 64.dp else 56.dp,
        label = "height",
    )

    val categoryName = category.name

    val chipShape = RoundedCornerShape(animatedCornerRadius)
    Surface(
        modifier =
            modifier
                .wrapContentWidth()
                .height(height)
                .clip(chipShape)
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = {
                        if (isSelected) {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            isDropdownExpanded = true
                        } else {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onSelectCategory()
                        }
                    },
                ),
        shape = chipShape,
        color = containerColor,
        // Before glass (and with it off): a quiet outline on the unselected chips.
        border = null,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = if (isSelected) Arrangement.Start else Arrangement.Center,
        ) {
            if (isSelected) {
                val boldColor = categoryColor ?: MaterialTheme.colorScheme.primary
                val iconTint = lerp(MaterialTheme.colorScheme.surface, boldColor, 0.15f)
                Box(
                    modifier =
                        Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(boldColor),
                    contentAlignment = Alignment.Center,
                ) {
                    val iconRes =
                        category.icon?.let { iconName ->
                            TempoIcon.fromName(iconName)?.iconRes
                        } ?: R.drawable.ic_category
                    Icon(
                        painter = painterResource(iconRes),
                        contentDescription = null,
                        tint = iconTint,
                        modifier = Modifier.size(20.dp),
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column {
                    Text(
                        text = categoryName,
                        style = MaterialTheme.typography.categoryChipSelected,
                        color = contentColor,
                        maxLines = 1,
                    )

                    Text(
                        text =
                            pluralStringResource(
                                R.plurals.tasks_count,
                                count,
                                count,
                            ),
                        style = MaterialTheme.typography.labelSmall,
                        color = contentColor.copy(alpha = 0.7f),
                    )
                }

                DropdownMenu(
                    expanded = isDropdownExpanded,
                    onDismissRequest = { isDropdownExpanded = false },
                    shape = RoundedCornerShape(16.dp),
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.edit)) },
                        leadingIcon = {
                            Icon(
                                painter = painterResource(R.drawable.ic_edit),
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                            )
                        },
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onEdit()
                            isDropdownExpanded = false
                        },
                    )
                    // Only a category with sections on offers to add one (#61).
                    if (category.sectionsEnabled) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.section_add)) },
                            leadingIcon = {
                                Icon(
                                    painter = painterResource(R.drawable.ic_add_row_below),
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp),
                                )
                            },
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onAddSection()
                                isDropdownExpanded = false
                            },
                        )
                    }
                    if (!category.isDefault) {
                        DropdownMenuItem(
                            text = {
                                Text(
                                    stringResource(R.string.delete),
                                    color = MaterialTheme.colorScheme.error,
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    painter = painterResource(R.drawable.ic_delete_forever),
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp),
                                    tint = MaterialTheme.colorScheme.error,
                                )
                            },
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onRequestDelete()
                                isDropdownExpanded = false
                            },
                        )
                    }
                }
            } else {
                Text(
                    text = categoryName,
                    style = MaterialTheme.typography.categoryChipUnselected,
                    color = contentColor,
                )

                val badgeColor = categoryColor ?: MaterialTheme.colorScheme.primary
                AnimatedContent(
                    targetState = count,
                    transitionSpec = {
                        (
                            fadeIn(animationSpec = tween(durationMillis = 180)) togetherWith
                                fadeOut(animationSpec = tween(durationMillis = 120))
                        ).using(SizeTransform(clip = false))
                    },
                    label = "categoryCountBadge",
                ) { animatedCount ->
                    if (animatedCount > 0) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Box(
                                modifier =
                                    Modifier
                                        .size(22.dp)
                                        .background(
                                            badgeColor,
                                            CircleShape,
                                        ),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = animatedCount.toString(),
                                    style = MaterialTheme.typography.metadataLabel,
                                    color = MaterialTheme.colorScheme.onPrimary,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NewCategoryButton(onClick: () -> Unit) {
    val haptic = LocalHapticFeedback.current
    val (interactionSource, animatedCornerRadius) = rememberPressableIconButtonAnimation()
    val isPressed by interactionSource.collectIsPressedAsState()

    val containerColor by animateColorAsState(
        targetValue =
            if (isPressed) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            },
        label = "newCategoryContainerColor",
    )

    val contentColor by animateColorAsState(
        targetValue =
            if (isPressed) {
                MaterialTheme.colorScheme.onPrimaryContainer
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            },
        label = "newCategoryContentColor",
    )

    val addShape = RoundedCornerShape(animatedCornerRadius.value)
    Surface(
        modifier =
            Modifier
                .size(56.dp)
                .clip(addShape)
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onClick()
                    },
                ),
        shape = addShape,
        color = containerColor,
        border = null,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                painter = painterResource(R.drawable.ic_add),
                contentDescription = stringResource(R.string.category_add_category),
                tint = contentColor,
                modifier = Modifier.size(24.dp),
            )
        }
    }
}
