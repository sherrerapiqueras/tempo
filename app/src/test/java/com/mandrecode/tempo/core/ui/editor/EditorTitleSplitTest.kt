package com.mandrecode.tempo.core.ui.editor

import androidx.compose.ui.text.TextRange
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class EditorTitleSplitTest {
    private val grinningFace = "😀"
    private val familyEmoji = "👨‍👩‍👧"
    private val spainFlag = "🇪🇸"
    private val redHeart = "❤️"

    private fun padding(length: Int) = "a".repeat(length)

    @Test
    fun givenTextAtLimit_whenSplit_thenAllStaysInTitle() {
        val text = padding(EDITOR_MAX_TITLE_LENGTH)

        assertThat(splitEditorTitle(text)).isEqualTo(EditorTitleSplit(text, ""))
    }

    @Test
    fun givenWordCrossingLimit_whenSplit_thenWholeWordMoves() {
        val fits = padding(60)

        val split = splitEditorTitle("$fits cuando")

        assertThat(split).isEqualTo(EditorTitleSplit(fits, "cuando"))
    }

    @Test
    fun givenPastedSentence_whenSplit_thenWordsStayInOrderAndWhole() {
        val words = List(30) { "word$it" }

        val split = splitEditorTitle(words.joinToString(" "))

        assertThat(split.title.length).isAtMost(EDITOR_MAX_TITLE_LENGTH)
        assertThat("${split.title} ${split.overflow}").isEqualTo(words.joinToString(" "))
        assertThat(words).containsAtLeastElementsIn(split.title.split(" "))
        assertThat(words).containsAtLeastElementsIn(split.overflow.split(" "))
    }

    @Test
    fun givenWordEndingAtLimit_whenSplit_thenWordStaysInTitle() {
        val fits = padding(EDITOR_MAX_TITLE_LENGTH)

        assertThat(splitEditorTitle("$fits next")).isEqualTo(EditorTitleSplit(fits, "next"))
    }

    @Test
    fun givenSpaceTypedAtLimit_whenSplit_thenNothingOverflows() {
        val fits = padding(EDITOR_MAX_TITLE_LENGTH)

        assertThat(splitEditorTitle("$fits ")).isEqualTo(EditorTitleSplit(fits, ""))
    }

    @Test
    fun givenRunOfSpacesAtSplit_whenSplit_thenSpacesAreDropped() {
        val fits = padding(50)

        val split = splitEditorTitle("$fits${" ".repeat(10)}${padding(10)}")

        assertThat(split).isEqualTo(EditorTitleSplit(fits, padding(10)))
    }

    @Test
    fun givenOneLongWord_whenSplit_thenHardSplitsAtLimit() {
        val split = splitEditorTitle(padding(EDITOR_MAX_TITLE_LENGTH) + "OVERFLOW")

        assertThat(split).isEqualTo(EditorTitleSplit(padding(EDITOR_MAX_TITLE_LENGTH), "OVERFLOW"))
    }

    @Test
    fun givenLeadingSpacesBeforeLongWord_whenSplit_thenTitleIsNotBlank() {
        val word = padding(70)

        val split = splitEditorTitle("  $word")

        assertThat(split.title).isEqualTo("  " + padding(EDITOR_MAX_TITLE_LENGTH - 2))
        assertThat(split.overflow).isEqualTo(padding(7))
    }

    @Test
    fun givenOnlySpaces_whenSplit_thenBothSidesAreEmpty() {
        val split = splitEditorTitle(" ".repeat(EDITOR_MAX_TITLE_LENGTH + 5))

        assertThat(split).isEqualTo(EditorTitleSplit("", ""))
    }

    @Test
    fun givenSurrogatePairCrossingLimit_whenSplit_thenPairMovesWhole() {
        val fits = padding(EDITOR_MAX_TITLE_LENGTH - 1)

        assertThat(splitEditorTitle(fits + grinningFace))
            .isEqualTo(EditorTitleSplit(fits, grinningFace))
    }

    @Test
    fun givenJoinedEmojiCrossingLimit_whenSplit_thenSequenceMovesWhole() {
        val fits = padding(EDITOR_MAX_TITLE_LENGTH - 3)

        assertThat(splitEditorTitle(fits + familyEmoji))
            .isEqualTo(EditorTitleSplit(fits, familyEmoji))
    }

    @Test
    fun givenFlagCrossingLimit_whenSplit_thenFlagMovesWhole() {
        val fits = padding(EDITOR_MAX_TITLE_LENGTH - 2)

        assertThat(splitEditorTitle(fits + spainFlag))
            .isEqualTo(EditorTitleSplit(fits, spainFlag))
    }

    @Test
    fun givenVariationSequenceCrossingLimit_whenSplit_thenSequenceMovesWhole() {
        val fits = padding(EDITOR_MAX_TITLE_LENGTH - 1)

        assertThat(splitEditorTitle(fits + redHeart))
            .isEqualTo(EditorTitleSplit(fits, redHeart))
    }

    @Test
    fun givenCharacterLongerThanLimit_whenSplit_thenCutsInsideItRatherThanEmptyingTitle() {
        val text = "e" + "́".repeat(EDITOR_MAX_TITLE_LENGTH + 5)

        val split = splitEditorTitle(text)

        assertThat(split.title).isEqualTo(text.take(EDITOR_MAX_TITLE_LENGTH))
        assertThat(split.overflow).isEqualTo(text.drop(EDITOR_MAX_TITLE_LENGTH))
    }

    @Test
    fun givenSurrogateInLongCharacterAtLimit_whenSplit_thenPairIsNotCut() {
        // U+1D165 is a combining mark outside the BMP, so its pairs stay in one long character.
        val combiningStem = "𝅥"
        val text = "e" + combiningStem.repeat(EDITOR_MAX_TITLE_LENGTH)

        val split = splitEditorTitle(text)

        assertThat(split.title.length).isEqualTo(EDITOR_MAX_TITLE_LENGTH)
        assertThat(split.title.last().isHighSurrogate()).isFalse()
        assertThat(split.title + split.overflow).isEqualTo(text)
    }

    @Test
    fun givenEmptyDescription_whenJoined_thenCursorIsAtEnd() {
        val value = descriptionWithTitleOverflow(overflow = "cuan", description = "")

        assertThat(value.text).isEqualTo("cuan")
        assertThat(value.selection).isEqualTo(TextRange(4))
    }

    @Test
    fun givenExistingDescription_whenJoined_thenSeparatedWithCursorAfterMovedText() {
        val value = descriptionWithTitleOverflow(overflow = "cuan", description = "notes")

        assertThat(value.text).isEqualTo("cuan notes")
        assertThat(value.selection).isEqualTo(TextRange(4))
    }

    @Test
    fun givenNoOverflow_whenJoined_thenDescriptionIsUntouchedWithCursorAtStart() {
        val value = descriptionWithTitleOverflow(overflow = "", description = "notes")

        assertThat(value.text).isEqualTo("notes")
        assertThat(value.selection).isEqualTo(TextRange.Zero)
    }
}
