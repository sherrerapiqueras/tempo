package com.mandrecode.tempo.core.ui.editor

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.mandrecode.tempo.R

private const val DESCRIPTION_MAX_LINES = 5

/**
 * The description field of an editor sheet. Its text starts where the controls of the rows below
 * start, which a Material `TextField` can't do: its value overload keeps 16dp inside each edge and
 * has no parameter to change that. The error sits outside the decoration for the same reason.
 *
 * @param fieldModifier applied to the editable field itself, for its test tag and focus handling.
 */
@Composable
internal fun EditorDescriptionField(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    error: String?,
    modifier: Modifier = Modifier,
    fieldModifier: Modifier = Modifier,
    visualTransformation: VisualTransformation = VisualTransformation.None,
) {
    val colors = editorTransparentTextFieldColors()
    val isError = error != null
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val textStyle =
        MaterialTheme.typography.bodyLarge.merge(
            TextStyle(color = colors.textColor(enabled = true, isError = isError, focused = isFocused)),
        )

    Column(modifier = modifier) {
        CompositionLocalProvider(LocalTextSelectionColors provides colors.textSelectionColors) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier =
                    fieldModifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = TextFieldDefaults.MinHeight)
                        .semantics { if (error != null) error(error) },
                textStyle = textStyle,
                cursorBrush = SolidColor(colors.cursorColor(isError)),
                visualTransformation = visualTransformation,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                interactionSource = interactionSource,
                maxLines = DESCRIPTION_MAX_LINES,
                decorationBox = { innerTextField ->
                    TextFieldDefaults.DecorationBox(
                        value = value.text,
                        innerTextField = innerTextField,
                        enabled = true,
                        singleLine = false,
                        visualTransformation = visualTransformation,
                        interactionSource = interactionSource,
                        isError = isError,
                        placeholder = {
                            Text(
                                stringResource(R.string.add_details),
                                style = MaterialTheme.typography.bodyLarge,
                            )
                        },
                        colors = colors,
                        contentPadding = TextFieldDefaults.contentPaddingWithoutLabel(start = 0.dp, end = 0.dp),
                    )
                },
            )
        }
        if (error != null) {
            Text(
                text = error,
                style = MaterialTheme.typography.bodySmall,
                color = colors.errorSupportingTextColor,
                modifier = Modifier.padding(bottom = EDITOR_TEXT_FIELD_BOTTOM_INSET),
            )
        }
    }
}
