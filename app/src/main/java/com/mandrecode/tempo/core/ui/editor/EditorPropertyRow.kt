package com.mandrecode.tempo.core.ui.editor

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.dp

/**
 * Shared "leading icon box + trailing content" row used throughout the task and habit bottom
 * sheets for property fields (description, category, priority, reminder, periodicity/repeat
 * days, icon, color, etc.).
 */
@Composable
internal fun EditorPropertyRow(
    iconPainter: Painter,
    iconContentDescription: String?,
    modifier: Modifier = Modifier,
    iconBoxModifier: Modifier = Modifier.width(48.dp),
    verticalAlignment: Alignment.Vertical = Alignment.CenterVertically,
    iconTint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = verticalAlignment,
    ) {
        Box(
            modifier = iconBoxModifier,
            contentAlignment = Alignment.CenterStart,
        ) {
            Icon(
                painter = iconPainter,
                contentDescription = iconContentDescription,
                tint = iconTint,
                modifier = Modifier.padding(start = EDITOR_ICON_START_INSET),
            )
        }
        content()
    }
}
