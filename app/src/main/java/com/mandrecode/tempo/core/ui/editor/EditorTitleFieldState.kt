package com.mandrecode.tempo.core.ui.editor

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.TextFieldValue

/**
 * Holds a title field's cursor while the sheet owns its text. A `TextField` taking a `String` skips
 * an edit that matches the last text it reported, even if the sheet moved that text to the
 * description and left the title unchanged, so the same keystroke would be lost the second time.
 */
internal class EditorTitleFieldState {
    private var value by mutableStateOf(TextFieldValue())

    fun displayed(title: String): TextFieldValue = value.copy(text = title)

    fun onValueChange(
        newValue: TextFieldValue,
        title: String,
        onTitleChange: (String) -> Unit,
    ) {
        value = newValue
        if (newValue.text != title) onTitleChange(newValue.text)
    }
}
