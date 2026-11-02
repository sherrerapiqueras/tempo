package com.mandrecode.tempo.core.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mandrecode.tempo.R
import com.mandrecode.tempo.core.ui.adaptive.adaptiveHorizontalMargin
import com.mandrecode.tempo.core.ui.theme.IconCategory
import com.mandrecode.tempo.core.ui.theme.TempoIcon
import com.mandrecode.tempo.core.ui.theme.sheetTitle
import com.mandrecode.tempo.core.ui.util.rememberPressableChipAnimation
import com.mandrecode.tempo.core.ui.util.rememberPressableIconButtonAnimation

private val ICON_OPTION_SIZE = 48.dp
private val ROW_ITEM_MIN_GAP = 4.dp
private val ROW_HORIZONTAL_PADDING = 8.dp // 4dp start + 4dp end, matches the FlowRow's own padding

// Default/floor for narrow (compact phone) containers - matches the picker's original fixed size.
private const val DEFAULT_ITEMS_PER_ROW = 6

// Ceiling: one row slot per icon category (+1 for the trigger) - beyond this, extra slots would
// only add a second icon from a category already represented, which doesn't add more variety.
private val MAX_ITEMS_PER_ROW = IconCategory.entries.size + 1

// Same ceiling rationale as MAX_ITEMS_PER_ROW, but without the +1: the modal's per-category
// rows have no trailing trigger, so there's nothing to reserve a slot for.
private val MAX_MODAL_ITEMS_PER_ROW = IconCategory.entries.size

/**
 * How many 48dp icon slots (including the trailing trigger) to show, based on how many fit
 * across [availableWidth] at [ROW_ITEM_MIN_GAP] minimum spacing - clamped to
 * [DEFAULT_ITEMS_PER_ROW]..[MAX_ITEMS_PER_ROW], so compact phones always get at least
 * [DEFAULT_ITEMS_PER_ROW] slots even if that's tighter than [ROW_ITEM_MIN_GAP] allows, and wide
 * windows never grow past one slot per category.
 */
internal fun calculateItemsPerRow(availableWidth: Dp): Int =
    calculateAdaptiveItemCount(
        availableWidth = availableWidth,
        itemSize = ICON_OPTION_SIZE,
        minGap = ROW_ITEM_MIN_GAP,
        horizontalPadding = ROW_HORIZONTAL_PADDING,
        minCount = DEFAULT_ITEMS_PER_ROW,
        maxCount = MAX_ITEMS_PER_ROW,
    )

/**
 * Picks which icons the collapsed row should show: [sampledIcons] as-is when there's no
 * selection or the selection is already represented, otherwise the selected icon pinned to the
 * first slot with the remaining [slotCount] - 1 slots backfilled from [sampledIcons] *excluding*
 * its category - so a manually-picked icon never displaces the row's one-per-category coverage
 * by leaving a same-category duplicate behind while dropping a different category entirely.
 */
internal fun resolveRowIcons(
    sampledIcons: List<TempoIcon>,
    allIcons: List<TempoIcon>,
    selectedIconName: String?,
    slotCount: Int,
): List<TempoIcon> =
    when {
        selectedIconName == null -> sampledIcons
        sampledIcons.any { it.iconName == selectedIconName } -> sampledIcons
        else -> {
            val selectedIcon = allIcons.firstOrNull { it.iconName == selectedIconName }
            if (selectedIcon != null) {
                val remaining = sampledIcons.filter { it.category != selectedIcon.category }
                listOf(selectedIcon) + remaining.take(slotCount - 1)
            } else {
                sampledIcons
            }
        }
    }

/**
 * Icon picker component for selecting habit icons.
 * Shows a row sampled across icon categories that fits the width of the container,
 * plus a trigger that opens a modal with every icon grouped by category.
 * When an icon is selected, it is always shown somewhere in the row.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun IconPicker(
    selectedIconName: String?,
    onSelectIcon: (String) -> Unit,
    onClearIcon: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    disabledMessage: String? = null,
) {
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val itemsPerRow = calculateItemsPerRow(maxWidth)
        val firstRowIconsCount = itemsPerRow - 1

        val allIcons = remember { TempoIcon.getAllIcons() }
        var showCategoryModal by remember { mutableStateOf(false) }

        // Re-sampled only when the row's slot count actually changes (e.g. the window is
        // resized), not on unrelated recompositions (e.g. typing in another field).
        val sampledIcons =
            remember(firstRowIconsCount) {
                TempoIcon.sampleAcrossCategories(allIcons, firstRowIconsCount)
            }

        val iconsToDisplay = resolveRowIcons(sampledIcons, allIcons, selectedIconName, firstRowIconsCount)

        if (showCategoryModal) {
            IconCategoryModal(
                allIcons = allIcons,
                selectedIconName = selectedIconName,
                enabled = enabled,
                onSelectIcon = {
                    onSelectIcon(it)
                    showCategoryModal = false
                },
                onDismissRequest = { showCategoryModal = false },
            )
        }

        Column(
            modifier = Modifier.fillMaxWidth(),
        ) {
            FlowRow(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(start = 4.dp, end = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalArrangement = Arrangement.spacedBy(12.dp),
                maxItemsInEachRow = itemsPerRow,
            ) {
                iconsToDisplay.forEach { tempoIcon ->
                    IconOption(
                        tempoIcon = tempoIcon,
                        isSelected = selectedIconName == tempoIcon.iconName,
                        enabled = enabled,
                        onClick = {
                            if (selectedIconName == tempoIcon.iconName) {
                                onClearIcon()
                            } else {
                                onSelectIcon(tempoIcon.iconName)
                            }
                        },
                    )
                }

                // Add spacers to push the trigger to the bottom right and respect the grid
                // This ensures that partial rows have the same spacing as full rows
                val totalVisible = iconsToDisplay.size + 1
                val remainder = totalVisible % itemsPerRow
                if (remainder != 0) {
                    val spacersNeeded = itemsPerRow - remainder
                    repeat(spacersNeeded) {
                        Box(modifier = Modifier.size(ICON_OPTION_SIZE))
                    }
                }

                BrowseCategoriesButton(
                    onClick = { showCategoryModal = true },
                    enabled = enabled,
                )
            }

            if (!enabled && disabledMessage != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = disabledMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
        }
    }
}

/**
 * Individual icon option in the picker
 */
@Composable
private fun IconOption(
    tempoIcon: TempoIcon,
    isSelected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    val containerSize = 48.dp
    val iconBoxSize = 40.dp
    val iconSize = 24.dp
    val interactionSource = remember { MutableInteractionSource() }

    // To make the inner box follow the "exact same shape" as the 16dp outer ring,
    // we use a 12dp radius for the 40dp box (16dp outer - 4dp padding = 12dp inner).
    // This creates perfectly parallel/concentric curves. Shares the same reactive
    // press-morph convention as CategoryChipRow/ExpressiveChip.
    val animatedCornerRadius by rememberPressableChipAnimation(
        isSelected = isSelected,
        interactionSource = interactionSource,
        selectedRadius = 12.dp,
        unselectedRadius = 16.dp,
        pressedRadius = 8.dp,
    )

    val containerColor by animateColorAsState(
        targetValue =
            if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
        animationSpec = tween(300),
        label = "icon_container_color",
    )

    val contentColor by animateColorAsState(
        targetValue =
            if (isSelected) {
                MaterialTheme.colorScheme.onPrimaryContainer
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        animationSpec = tween(300),
        label = "icon_content_color",
    )

    val borderAlpha by animateFloatAsState(
        targetValue = if (isSelected) 1f else 0f,
        animationSpec = tween(300),
        label = "border_alpha",
    )

    Box(
        modifier =
            Modifier
                .size(containerSize)
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    enabled = enabled,
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onClick()
                    },
                ),
        contentAlignment = Alignment.Center,
    ) {
        // High-fidelity selection ring with fade animation
        Box(
            modifier =
                Modifier
                    .matchParentSize()
                    .graphicsLayer { alpha = borderAlpha }
                    .border(
                        width = 2.dp,
                        color = MaterialTheme.colorScheme.primary,
                        shape = RoundedCornerShape(16.dp),
                    ),
        )

        Box(
            modifier =
                Modifier
                    .size(iconBoxSize)
                    .clip(RoundedCornerShape(animatedCornerRadius))
                    .background(
                        color =
                            if (enabled) {
                                containerColor
                            } else {
                                containerColor.copy(alpha = 0.5f)
                            },
                    ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(tempoIcon.iconRes),
                contentDescription = tempoIcon.iconName,
                modifier = Modifier.size(iconSize),
                tint = if (enabled) contentColor else contentColor.copy(alpha = 0.5f),
            )
        }
    }
}

/**
 * Trailing trigger that opens [IconCategoryModal] to browse every icon by category.
 */
@Composable
private fun BrowseCategoriesButton(
    onClick: () -> Unit,
    enabled: Boolean,
) {
    val haptic = LocalHapticFeedback.current
    val (interactionSource, cornerRadius) =
        rememberPressableIconButtonAnimation(
            baseRadius = 16.dp,
            pressedRadius = 12.dp,
        )

    val size = 48.dp
    val iconSize = 20.dp

    val containerColor by animateColorAsState(
        targetValue =
            if (enabled) {
                // Slightly different shade for the action button
                MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.8f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
            },
        label = "browse_categories_container_color",
    )

    val contentColor by animateColorAsState(
        targetValue =
            if (enabled) {
                MaterialTheme.colorScheme.onSecondaryContainer
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            },
        label = "browse_categories_content_color",
    )

    Box(
        modifier =
            Modifier
                .size(size)
                .clip(RoundedCornerShape(cornerRadius.value))
                .background(containerColor)
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    enabled = enabled,
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onClick()
                    },
                ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_chevron_right),
            contentDescription = stringResource(R.string.icon_picker_browse_categories),
            modifier = Modifier.size(iconSize),
            tint = contentColor,
        )
    }
}

/**
 * Modal listing every [TempoIcon], grouped and headed by its [IconCategory]. Reuses the same
 * adaptive row-sizing the collapsed row and [ColorPicker] use, so every category's icons line
 * up on the same column grid instead of each row wrapping independently.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun IconCategoryModal(
    allIcons: List<TempoIcon>,
    selectedIconName: String?,
    enabled: Boolean,
    onSelectIcon: (String) -> Unit,
    onDismissRequest: () -> Unit,
) {
    val iconsByCategory = remember(allIcons) { allIcons.groupBy { it.category } }
    val horizontalMargin = adaptiveHorizontalMargin()

    TempoModalBottomSheet(onDismissRequest = onDismissRequest) {
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val itemsPerRow =
                calculateAdaptiveItemCount(
                    availableWidth = maxWidth,
                    itemSize = ICON_OPTION_SIZE,
                    minGap = ROW_ITEM_MIN_GAP,
                    horizontalPadding = horizontalMargin * 2,
                    minCount = DEFAULT_ITEMS_PER_ROW,
                    maxCount = MAX_MODAL_ITEMS_PER_ROW,
                )

            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = horizontalMargin)
                        .padding(top = 12.dp, bottom = 32.dp),
            ) {
                Text(
                    text = stringResource(R.string.icon_picker_all_icons_title),
                    style = MaterialTheme.typography.sheetTitle,
                )

                IconCategory.entries.forEach { category ->
                    val categoryIcons = iconsByCategory[category].orEmpty()
                    if (categoryIcons.isNotEmpty()) {
                        Text(
                            text = stringResource(category.labelRes),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 20.dp, bottom = 8.dp),
                        )
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(ROW_ITEM_MIN_GAP),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            maxItemsInEachRow = itemsPerRow,
                        ) {
                            categoryIcons.forEach { tempoIcon ->
                                IconOption(
                                    tempoIcon = tempoIcon,
                                    isSelected = selectedIconName == tempoIcon.iconName,
                                    enabled = enabled,
                                    onClick = { onSelectIcon(tempoIcon.iconName) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
