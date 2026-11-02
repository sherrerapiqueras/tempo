package com.mandrecode.tempo.core.ui.editor

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import java.text.BreakIterator

/** A word boundary is only worth taking if it leaves a title of at least this length. */
private const val MIN_TITLE_LENGTH_AT_WORD_BOUNDARY = 16
private const val NON_BREAKING_SPACES = "   "

/** The text that stays in an editor's title, and the text that moves on to its description. */
internal data class EditorTitleSplit(
    val title: String,
    val overflow: String,
)

/** What a title edit leaves in the editor. A non-null [description] takes focus from the title. */
internal data class EditorTitleChange(
    val title: String,
    val description: TextFieldValue?,
)

/**
 * Resolves an edit of the title field, or returns null if it was the Enter key, which adds a
 * newline to the [title] and nothing else. Any other newline is pasted text.
 */
internal fun resolveEditorTitleChange(
    newValue: String,
    title: String,
    description: String,
): EditorTitleChange? {
    val hasNewline = '\n' in newValue
    return when {
        hasNewline && newValue.replaceFirst("\n", "") == title -> null
        !hasNewline && newValue.length <= EDITOR_MAX_TITLE_LENGTH -> EditorTitleChange(newValue, description = null)
        else -> {
            val split = splitEditorTitle(newValue)
            val movedDescription =
                descriptionWithTitleOverflow(split.overflow, description).takeIf { split.title.isNotEmpty() }
            EditorTitleChange(split.title, movedDescription)
        }
    }
}

/**
 * Splits [text] so that a word is never shared between the title and the description: at the end
 * of its first line, or else at the last word boundary within [EDITOR_MAX_TITLE_LENGTH]. Text with
 * no usable boundary is cut between characters instead. The whitespace at the split belongs to
 * neither side.
 */
internal fun splitEditorTitle(text: String): EditorTitleSplit {
    val source = if ('\n' in text) text.trimStart() else text
    if ('\n' !in source && source.length <= EDITOR_MAX_TITLE_LENGTH) return EditorTitleSplit(source, overflow = "")
    val end = firstLineEnd(source) ?: lastWordBoundary(source) ?: lastCharacterBoundary(source)
    return EditorTitleSplit(
        title = source.substring(0, end).trimEnd(),
        overflow = source.substring(end).trimStart(),
    )
}

/**
 * The description once [overflow] has moved to its start, with the cursor right after the moved
 * text so the user can carry on typing the word. The text after the cursor is kept apart from it:
 * a list stays on its own line, and anything else is separated by a space.
 */
internal fun descriptionWithTitleOverflow(
    overflow: String,
    description: String,
): TextFieldValue {
    val separator =
        when {
            description.isEmpty() -> ""
            description.trimStart(' ', '\t').startsWith("- ") -> "\n"
            description.first().isWhitespace() -> ""
            else -> " "
        }
    return TextFieldValue(
        text = overflow + separator + description,
        selection = TextRange(overflow.length),
    )
}

private fun firstLineEnd(text: String): Int? = text.indexOf('\n').takeIf { it in 1..EDITOR_MAX_TITLE_LENGTH }

/** The last breakable whitespace within the limit, unless taking it would leave too short a title. */
private fun lastWordBoundary(text: String): Int? =
    (EDITOR_MAX_TITLE_LENGTH downTo 1)
        .firstOrNull { text[it].isWhitespace() && text[it] !in NON_BREAKING_SPACES }
        ?.takeIf { text.substring(0, it).trim().length >= MIN_TITLE_LENGTH_AT_WORD_BOUNDARY }

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
