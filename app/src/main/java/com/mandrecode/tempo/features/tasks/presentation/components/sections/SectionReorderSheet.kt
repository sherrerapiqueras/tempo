package com.mandrecode.tempo.features.tasks.presentation.components.sections

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.mandrecode.tempo.R
import com.mandrecode.tempo.core.ui.adaptive.adaptiveHorizontalMargin
import com.mandrecode.tempo.core.ui.components.TempoModalBottomSheet
import com.mandrecode.tempo.core.ui.theme.subtaskTitle
import com.mandrecode.tempo.features.tasks.domain.model.TaskSection
import kotlinx.collections.immutable.ImmutableList
import kotlin.math.roundToInt

private const val DRAGGED_ROW_TINT_ALPHA = 0.14f

/**
 * Reorders a category's sections (#61) on a surface of their own, so moving a whole section never
 * means dragging it through a long list. Each row drags by its handle; dropping it reports the move.
 */
@Composable
internal fun SectionReorderSheet(
    sections: ImmutableList<TaskSection>,
    onReorder: (fromIndex: Int, toIndex: Int, sections: List<TaskSection>) -> Unit,
    onDismiss: () -> Unit,
) {
    // Laid out like the sort sheet: same header and padding, and no Done — each drop is saved
    // as it happens, so the sheet just closes like the others (swipe, scrim or back).
    TempoModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(bottom = 32.dp),
        ) {
            TasksSheetHeader(title = stringResource(R.string.section_reorder))
            ReorderableSectionRows(
                sections = sections,
                onReorder = onReorder,
                modifier = Modifier.padding(horizontal = adaptiveHorizontalMargin()),
            )
        }
    }
}

@Composable
private fun ReorderableSectionRows(
    sections: ImmutableList<TaskSection>,
    onReorder: (fromIndex: Int, toIndex: Int, sections: List<TaskSection>) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptic = LocalHapticFeedback.current
    val stepPx = with(LocalDensity.current) { (TasksSheetRowHeight + TasksSheetRowSpacing).toPx() }
    var draggedIndex by remember { mutableIntStateOf(-1) }
    var dragOffset by remember { mutableFloatStateOf(0f) }

    fun targetOf(from: Int) = (from + (dragOffset / stepPx).roundToInt()).coerceIn(0, sections.lastIndex)
    val targetIndex = if (draggedIndex < 0) -1 else targetOf(draggedIndex)

    Column(verticalArrangement = Arrangement.spacedBy(TasksSheetRowSpacing), modifier = modifier.padding(top = 8.dp)) {
        sections.forEachIndexed { index, section ->
            val isDragged = index == draggedIndex
            SectionReorderRow(
                name = section.name,
                isDragged = isDragged,
                isTarget = index == targetIndex && !isDragged,
                dragOffset = if (isDragged) dragOffset else 0f,
                dragKey = sections,
                handleDragGestures = {
                    detectDragGestures(
                        onDragStart = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            draggedIndex = index
                            dragOffset = 0f
                        },
                        onDrag = { change, amount ->
                            change.consume()
                            dragOffset += amount.y
                        },
                        onDragEnd = {
                            val from = draggedIndex
                            if (from >= 0 && targetOf(from) != from) onReorder(from, targetOf(from), sections)
                            draggedIndex = -1
                            dragOffset = 0f
                        },
                        onDragCancel = {
                            draggedIndex = -1
                            dragOffset = 0f
                        },
                    )
                },
            )
        }
    }
}

@Composable
private fun SectionReorderRow(
    name: String,
    isDragged: Boolean,
    isTarget: Boolean,
    dragOffset: Float,
    dragKey: Any,
    handleDragGestures: suspend PointerInputScope.() -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    TasksSheetRow(
        // The editor's solid selector fill: in the dark theme the plain container tones sit too close
        // to the glass sheet for each card to read as a card. The lifted row is the same fill under
        // a wash of the primary, so it still stands out from its neighbours.
        color =
            if (isDragged) {
                colors.primary.copy(alpha = DRAGGED_ROW_TINT_ALPHA).compositeOver(colors.surfaceContainerHighest)
            } else {
                colors.surfaceContainerHighest
            },
        shadowElevation = if (isDragged) 6.dp else 0.dp,
        modifier =
            Modifier
                // Fixed, unlike the sort sheet's rows: a drag counts rows by this exact step.
                .height(TasksSheetRowHeight)
                .zIndex(if (isDragged) 1f else 0f)
                // Opaque while lifted: a translucent row lets its own shadow show through its
                // rounded edges as artifacts. Elevation and the stronger tint mark it instead.
                .graphicsLayer { translationY = dragOffset },
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_drag_handle),
            contentDescription = stringResource(R.string.section_reorder),
            tint = colors.onSurfaceVariant,
            modifier = Modifier.pointerInput(dragKey, block = handleDragGestures).size(48.dp).padding(12.dp),
        )
        Text(
            text = name,
            style = MaterialTheme.typography.subtaskTitle,
            color = if (isTarget) colors.primary else colors.onSurface,
        )
    }
}
