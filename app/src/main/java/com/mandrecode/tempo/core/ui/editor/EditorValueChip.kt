package com.mandrecode.tempo.core.ui.editor

import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
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
) {
    InputChip(
        selected = true,
        onClick = onClick,
        shape = RoundedCornerShape(EDITOR_SET_ITEM_CORNER_RADIUS),
        label = {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(vertical = 12.dp),
            )
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
                .height(EDITOR_CONTROL_HEIGHT),
    )
}
