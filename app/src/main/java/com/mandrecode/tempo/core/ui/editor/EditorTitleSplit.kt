package com.mandrecode.tempo.core.ui.editor

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import java.text.BreakIterator

/** The text that stays in an editor's title, and the text that moves on to its description. */
internal data class EditorTitleSplit(
    val title: String,
    val overflow: String,
)

/**
 * Splits [text] at the last word boundary within [EDITOR_MAX_TITLE_LENGTH], so a word is never
 * shared between the title and the description. Text with no usable boundary is cut between
 * characters instead. The whitespace at the split belongs to neither side.
 */
internal fun splitEditorTitle(text: String): EditorTitleSplit {
    if (text.length <= EDITOR_MAX_TITLE_LENGTH) return EditorTitleSplit(title = text, overflow = "")
    val end = lastWordBoundary(text) ?: lastCharacterBoundary(text)
    return EditorTitleSplit(
        title = text.substring(0, end).trimEnd(),
        overflow = text.substring(end).trimStart(),
    )
}

/**
 * The description once [overflow] has moved to its start, with the cursor right after the moved
 * text so the user can carry on typing the word.
 */
internal fun descriptionWithTitleOverflow(
    overflow: String,
    description: String,
): TextFieldValue {
    val separator = if (overflow.isEmpty() || description.isEmpty()) "" else " "
    return TextFieldValue(
        text = overflow + separator + description,
        selection = TextRange(overflow.length),
    )
}

/** The last whitespace within the limit, unless taking it would leave the title blank. */
private fun lastWordBoundary(text: String): Int? =
    (EDITOR_MAX_TITLE_LENGTH downTo 1)
        .firstOrNull { text[it].isWhitespace() }
        ?.takeIf { text.substring(0, it).isNotBlank() }

/**
 * The last boundary within the limit that keeps a surrogate pair or emoji sequence whole. A single
 * character longer than the limit has no such boundary, so it is cut between code points instead.
 */
private fun lastCharacterBoundary(text: String): Int {
    val characters = BreakIterator.getCharacterInstance().apply { setText(text) }
    val boundary = characters.preceding(EDITOR_MAX_TITLE_LENGTH + 1)
    return when {
        boundary > 0 -> boundary
        text[EDITOR_MAX_TITLE_LENGTH - 1].isHighSurrogate() -> EDITOR_MAX_TITLE_LENGTH - 1
        else -> EDITOR_MAX_TITLE_LENGTH
    }
}
