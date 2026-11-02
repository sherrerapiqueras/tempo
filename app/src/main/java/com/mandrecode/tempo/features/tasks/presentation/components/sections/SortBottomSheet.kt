package com.mandrecode.tempo.features.tasks.presentation.components.sections

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mandrecode.tempo.R
import com.mandrecode.tempo.core.ui.adaptive.adaptiveHorizontalMargin
import com.mandrecode.tempo.core.ui.components.TempoModalBottomSheet
import com.mandrecode.tempo.core.ui.theme.sheetTitle
import com.mandrecode.tempo.core.ui.theme.subtaskTitle
import com.mandrecode.tempo.core.ui.util.getIconForSortOption
import com.mandrecode.tempo.core.ui.util.rememberPressableChipAnimation
import com.mandrecode.tempo.features.tasks.presentation.model.SortOption

internal val TasksSheetRowHeight = 56.dp
internal val TasksSheetRowSpacing = 6.dp
private val TasksSheetRowCornerRadius = 24.dp
private val TasksSheetRowSelectedCornerRadius = 16.dp

@Composable
fun SortBottomSheet(
    currentSortOption: SortOption,
    onDismiss: () -> Unit,
    onSelectSortOption: (SortOption) -> Unit,
    modifier: Modifier = Modifier,
) {
    val pendingSortOption = remember { mutableStateOf<SortOption?>(null) }
    TempoModalBottomSheet(
        onDismissRequest = {
            pendingSortOption.value?.let(onSelectSortOption)
            pendingSortOption.value = null
            onDismiss()
        },
        modifier = modifier,
        adaptivePlacement = false,
    ) { onRequestDismiss ->
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(bottom = 32.dp),
        ) {
            TasksSheetHeader(title = stringResource(R.string.sort_by))

            Column(
                verticalArrangement = Arrangement.spacedBy(TasksSheetRowSpacing),
                modifier =
                    Modifier
                        .selectableGroup()
                        .padding(horizontal = adaptiveHorizontalMargin())
                        .padding(top = 8.dp),
            ) {
                SortOption.entries.forEach { sortOption ->
                    SortOptionItem(
                        sortOption = sortOption,
                        isSelected = currentSortOption == sortOption,
                        onClick = {
                            pendingSortOption.value = sortOption
                            onRequestDismiss()
                        },
                    )
                }
            }
        }
    }
}

@Composable
internal fun SortOptionItem(
    sortOption: SortOption,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val haptic = LocalHapticFeedback.current
    val interactionSource = remember { MutableInteractionSource() }
    val cornerRadius by rememberPressableChipAnimation(
        isSelected = isSelected,
        interactionSource = interactionSource,
        selectedRadius = TasksSheetRowSelectedCornerRadius,
        unselectedRadius = TasksSheetRowCornerRadius,
    )
    TasksSheetRow(
        color = if (isSelected) colors.primaryContainer else colors.surfaceContainerHighest,
        // Marked the way a completed card is: 8dp squarer than its idle self.
        cornerRadius = cornerRadius,
        contentModifier =
            Modifier.selectable(
                selected = isSelected,
                interactionSource = interactionSource,
                indication = ripple(),
                role = Role.RadioButton,
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onClick()
                },
            ),
    ) {
        Icon(
            painter = painterResource(getIconForSortOption(sortOption)),
            contentDescription = null,
            tint = if (isSelected) colors.onPrimaryContainer else colors.onSurfaceVariant,
            modifier = Modifier.size(48.dp).padding(12.dp),
        )
        Text(
            text = stringResource(sortOption.labelResId),
            style = MaterialTheme.typography.subtaskTitle,
            modifier = Modifier.weight(1f),
        )
        if (isSelected) {
            Icon(
                painter = painterResource(R.drawable.ic_check),
                contentDescription = null,
                modifier = Modifier.size(48.dp).padding(12.dp),
            )
        }
    }
}

/** The card of a Tasks bottom sheet's row (sort, reorder sections), so every one reads the same. */
@Composable
internal fun TasksSheetRow(
    color: Color,
    modifier: Modifier = Modifier,
    contentModifier: Modifier = Modifier,
    cornerRadius: Dp = TasksSheetRowCornerRadius,
    shadowElevation: Dp = 0.dp,
    content: @Composable RowScope.() -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(cornerRadius),
        color = color,
        shadowElevation = shadowElevation,
        modifier = modifier.fillMaxWidth().heightIn(min = TasksSheetRowHeight),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = contentModifier.padding(horizontal = 12.dp),
            content = content,
        )
    }
}

/** The title of a Tasks bottom sheet (sort, reorder sections), so every one reads the same. */
@Composable
internal fun TasksSheetHeader(
    title: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = title,
        style = MaterialTheme.typography.sheetTitle,
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = adaptiveHorizontalMargin())
                .padding(top = 12.dp, bottom = 8.dp),
    )
}
