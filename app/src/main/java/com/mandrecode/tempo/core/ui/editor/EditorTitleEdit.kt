package com.mandrecode.tempo.core.ui.editor

import com.mandrecode.tempo.core.domain.util.ValidationUtils

private val LINE_BREAK_WITH_SPACES = Regex("""\s*\R\s*""")

/**
 * Resolves an edit of the title field, or returns null if it was the Enter key, which adds a
 * newline to the [title] and nothing else. Any other line break is pasted text, and the title has
 * only one line to keep it on.
 */
internal fun resolveEditorTitleEdit(
    newValue: String,
    title: String,
): String? =
    when {
        !LINE_BREAK_WITH_SPACES.containsMatchIn(newValue) -> newValue
        newValue.replaceFirst("\n", "") == title -> null
        else -> newValue.replace(LINE_BREAK_WITH_SPACES, " ").trim()
    }

/** Whether autosave can store [title]. If not, closing the editor has to warn about losing the edit. */
internal fun canAutosaveEditorTitle(title: String): Boolean {
    val fitsValidation = title.length <= ValidationUtils.MAX_TITLE_LENGTH
    return title.isNotBlank() && fitsValidation
}
