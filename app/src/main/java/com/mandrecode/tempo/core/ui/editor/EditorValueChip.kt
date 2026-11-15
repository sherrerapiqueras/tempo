package com.mandrecode.tempo.core.ui.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mandrecode.tempo.R

/**
 * An editor row's value once it has been set — a reminder, an end date. It replaces the row's
 * [EditorAddButton]: tapping it changes the value and [onClear], when given, removes it.
 */
@Composable
internal fun EditorValueChip(
    label: String,
    onClick: () -> Unit,
    onClear: (() -> Unit)?,
    clearContentDescription: String,
    modifier: Modifier = Modifier,
    /**
     * One quiet line under the label, for a value that carries something of its own — the task a
     * session is linked to, with where and when it is due. The chip grows to hold it.
     */
    supporting: String? = null,
    /**
     * A small glyph closing the second line, with what it means for a screen reader: something the
     * value has that is worth knowing about but is not spelled out here.
     */
    supportingIcon: Painter? = null,
    supportingIconDescription: String? = null,
) {
    InputChip(
        selected = true,
        onClick = onClick,
        shape = RoundedCornerShape(EDITOR_SET_ITEM_CORNER_RADIUS),
        label = {
            ChipLabel(label, supporting, supportingIcon, supportingIconDescription)
        },
        trailingIcon =
            onClear?.let { clear ->
                {
                    IconButton(onClick = clear) {
                        Icon(
                            painterResource(R.drawable.ic_close),
                            contentDescription = clearContentDescription,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            },
        colors =
            InputChipDefaults.inputChipColors(
                selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer,
                selectedLeadingIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
                selectedTrailingIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
            ),
        border = null,
        modifier =
            modifier
                .padding(start = 4.dp)
                // One line is exactly a control's height, as every other row's control is; two
                // lines take what they need above that.
                .then(
                    if (supporting == null) {
                        Modifier.height(EDITOR_CONTROL_HEIGHT)
                    } else {
                        Modifier.heightIn(min = EDITOR_CONTROL_HEIGHT)
                    },
                ),
    )
}

/** The chip's words: its label alone, or the label over one quieter line and its glyph. */
@Composable
private fun ChipLabel(
    label: String,
    supporting: String?,
    supportingIcon: Painter?,
    supportingIconDescription: String?,
) {
    if (supporting == null && supportingIcon == null) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(vertical = 12.dp),
        )
        return
    }
    val quiet = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = SUPPORTING_ALPHA)
    Column(modifier = Modifier.padding(vertical = 8.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            supporting?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.labelMedium,
                    color = quiet,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
            }
            supportingIcon?.let {
                Icon(
                    painter = it,
                    contentDescription = supportingIconDescription,
                    tint = quiet,
                    modifier = Modifier.size(SupportingIconSize),
                )
            }
        }
    }
}

private val SupportingIconSize = 14.dp
private const val SUPPORTING_ALPHA = 0.72f
