package com.mandrecode.tempo.core.ui.editor

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldColors
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.mandrecode.tempo.R
import com.mandrecode.tempo.core.ui.theme.inputTitle

private const val TITLE_MAX_LINES = 3
private const val DESCRIPTION_MAX_LINES = 5

/**
 * The title field of an editor sheet. Beside a completion checkbox its text starts where the
 * description below it starts, which a Material `TextField` can't do: see [EditorDescriptionField].
 *
 * @param besideCheckbox whether a completion checkbox sits to its left. Without one, the title
 *   starts level with the icons of the rows below.
 * @param fieldModifier applied to the editable field itself, for its test tag and focus handling.
 */
@Composable
internal fun EditorTitleField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    error: String?,
    besideCheckbox: Boolean,
    modifier: Modifier = Modifier,
    fieldModifier: Modifier = Modifier,
) {
    val colors = editorTransparentTextFieldColors()
    val focusManager = LocalFocusManager.current
    val interactionSource = remember { MutableInteractionSource() }
    val startInset = if (besideCheckbox) 0.dp else EDITOR_ICON_START_INSET
    val textStyle = MaterialTheme.typography.inputTitle

    EditorFieldFrame(
        error = error,
        errorPadding = PaddingValues(start = startInset),
        colors = colors,
        modifier = modifier,
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier =
                fieldModifier
                    .fillMaxWidth()
                    .semantics { if (error != null) error(error) },
            textStyle = textStyle.withFieldColor(colors, error != null, interactionSource),
            cursorBrush = SolidColor(colors.cursorColor(error != null)),
            keyboardOptions =
                KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = ImeAction.Done,
                ),
            keyboardActions =
                KeyboardActions(
                    onDone = {
                        defaultKeyboardAction(ImeAction.Done)
                        focusManager.clearFocus()
                    },
                ),
            interactionSource = interactionSource,
            maxLines = TITLE_MAX_LINES,
            decorationBox = { innerTextField ->
                EditorFieldDecoration(
                    value = value,
                    innerTextField = innerTextField,
                    placeholder = placeholder,
                    placeholderStyle = textStyle,
                    isError = error != null,
                    colors = colors,
                    interactionSource = interactionSource,
                    contentPadding =
                        TextFieldDefaults.contentPaddingWithoutLabel(
                            start = startInset,
                            top = EDITOR_TEXT_FIELD_TOP_INSET,
                            end = 0.dp,
                            bottom = EDITOR_TEXT_FIELD_TOP_INSET,
                        ),
                )
            },
        )
    }
}

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
    val interactionSource = remember { MutableInteractionSource() }
    val textStyle = MaterialTheme.typography.bodyLarge

    EditorFieldFrame(
        error = error,
        errorPadding = PaddingValues(bottom = EDITOR_TEXT_FIELD_BOTTOM_INSET),
        colors = colors,
        modifier = modifier,
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier =
                fieldModifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = EDITOR_CONTROL_HEIGHT)
                    .semantics { if (error != null) error(error) },
            textStyle = textStyle.withFieldColor(colors, error != null, interactionSource),
            cursorBrush = SolidColor(colors.cursorColor(error != null)),
            visualTransformation = visualTransformation,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            interactionSource = interactionSource,
            maxLines = DESCRIPTION_MAX_LINES,
            decorationBox = { innerTextField ->
                EditorFieldDecoration(
                    value = value.text,
                    innerTextField = innerTextField,
                    placeholder = stringResource(R.string.add_details),
                    placeholderStyle = textStyle,
                    isError = error != null,
                    colors = colors,
                    interactionSource = interactionSource,
                    visualTransformation = visualTransformation,
                    contentPadding =
                        TextFieldDefaults.contentPaddingWithoutLabel(
                            start = 0.dp,
                            top = EDITOR_TEXT_FIELD_TOP_INSET,
                            end = 0.dp,
                            bottom = EDITOR_TEXT_FIELD_BOTTOM_INSET,
                        ),
                )
            },
        )
    }
}

@Composable
private fun EditorFieldFrame(
    error: String?,
    errorPadding: PaddingValues,
    colors: TextFieldColors,
    modifier: Modifier = Modifier,
    field: @Composable () -> Unit,
) {
    Column(modifier = modifier) {
        CompositionLocalProvider(LocalTextSelectionColors provides colors.textSelectionColors, content = field)
        if (error != null) {
            Text(
                text = error,
                style = MaterialTheme.typography.bodySmall,
                color = colors.errorSupportingTextColor,
                modifier = Modifier.padding(errorPadding),
            )
        }
    }
}

@Composable
private fun EditorFieldDecoration(
    value: String,
    innerTextField: @Composable () -> Unit,
    placeholder: String,
    placeholderStyle: TextStyle,
    isError: Boolean,
    colors: TextFieldColors,
    interactionSource: MutableInteractionSource,
    contentPadding: PaddingValues,
    visualTransformation: VisualTransformation = VisualTransformation.None,
) {
    TextFieldDefaults.DecorationBox(
        value = value,
        innerTextField = innerTextField,
        enabled = true,
        singleLine = false,
        visualTransformation = visualTransformation,
        interactionSource = interactionSource,
        isError = isError,
        placeholder = { Text(placeholder, style = placeholderStyle) },
        colors = colors,
        contentPadding = contentPadding,
    )
}

@Composable
private fun TextStyle.withFieldColor(
    colors: TextFieldColors,
    isError: Boolean,
    interactionSource: MutableInteractionSource,
): TextStyle {
    val isFocused by interactionSource.collectIsFocusedAsState()
    return merge(TextStyle(color = colors.textColor(enabled = true, isError = isError, focused = isFocused)))
}
