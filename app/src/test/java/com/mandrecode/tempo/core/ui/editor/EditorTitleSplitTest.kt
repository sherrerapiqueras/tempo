package com.mandrecode.tempo.core.ui.editor

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
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
    fun givenNoOverflow_whenJoined_thenNextWordCannotGlueToDescription() {
        val value = descriptionWithTitleOverflow(overflow = "", description = "notes")

        assertThat(value.text).isEqualTo(" notes")
        assertThat(value.selection).isEqualTo(TextRange.Zero)
    }

    @Test
    fun givenDescriptionStartingWithWhitespace_whenJoined_thenNoSeparatorIsAdded() {
        assertThat(descriptionWithTitleOverflow(overflow = "cuan", description = "\nnotes").text)
            .isEqualTo("cuan\nnotes")
        assertThat(descriptionWithTitleOverflow(overflow = "", description = " notes").text)
            .isEqualTo(" notes")
    }

    @Test
    fun givenDescriptionStartingWithList_whenJoined_thenListKeepsItsOwnLine() {
        assertThat(descriptionWithTitleOverflow(overflow = "cuan", description = "- milk").text)
            .isEqualTo("cuan\n- milk")
        assertThat(descriptionWithTitleOverflow(overflow = "cuan", description = "  - milk").text)
            .isEqualTo("cuan\n  - milk")
    }

    @Test
    fun givenNonBreakingSpaceAtLimit_whenSplit_thenItIsNotABoundary() {
        val fits = padding(40)
        val kept = padding(EDITOR_MAX_TITLE_LENGTH - 45)

        val split = splitEditorTitle("$fits 100\u00A0km$kept")

        assertThat(split.title).isEqualTo(fits)
        assertThat(split.overflow).isEqualTo("100\u00A0km$kept")
    }

    @Test
    fun givenOnlyNonBreakingSpaces_whenSplit_thenHardSplits() {
        val text = padding(30) + "\u00A0" + padding(40)

        assertThat(splitEditorTitle(text).title).isEqualTo(text.take(EDITOR_MAX_TITLE_LENGTH))
    }

    @Test
    fun givenBoundaryLeavingShortTitle_whenSplit_thenHardSplitsInstead() {
        val text = "a " + padding(64)

        val split = splitEditorTitle(text)

        assertThat(split.title).isEqualTo(text.take(EDITOR_MAX_TITLE_LENGTH))
        assertThat(split.overflow).isEqualTo("a")
    }

    @Test
    fun givenBoundaryLeavingMinimumTitle_whenSplit_thenWordMovesWhole() {
        val fits = padding(16)
        val word = padding(60)

        assertThat(splitEditorTitle("$fits $word")).isEqualTo(EditorTitleSplit(fits, word))
    }

    @Test
    fun givenPastedLines_whenSplit_thenFirstLineIsTitleAndLineBreaksSurvive() {
        val split = splitEditorTitle("Buy milk\nand eggs\nand bread")

        assertThat(split).isEqualTo(EditorTitleSplit("Buy milk", "and eggs\nand bread"))
    }

    @Test
    fun givenLeadingBlankLines_whenSplit_thenTheyAreSkipped() {
        assertThat(splitEditorTitle("\n\nBuy milk\nand eggs"))
            .isEqualTo(EditorTitleSplit("Buy milk", "and eggs"))
    }

    @Test
    fun givenFirstLineOverLimit_whenSplit_thenSplitsAtWordAndKeepsLaterLines() {
        val fits = padding(60)

        assertThat(splitEditorTitle("$fits cuando\nnotes"))
            .isEqualTo(EditorTitleSplit(fits, "cuando\nnotes"))
    }

    @Test
    fun givenEdit_whenWithinLimit_thenOnlyTitleChanges() {
        assertThat(resolveEditorTitleChange("Buy", "Bu", "notes"))
            .isEqualTo(EditorTitleChange("Buy", description = null))
    }

    @Test
    fun givenEnterKey_whenResolved_thenNoChangeIsReturned() {
        assertThat(resolveEditorTitleChange("Buy\n", "Buy", "notes")).isNull()
        assertThat(resolveEditorTitleChange("Bu\ny", "Buy", "notes")).isNull()
        assertThat(resolveEditorTitleChange("\n", "", "notes")).isNull()
    }

    @Test
    fun givenPastedLines_whenResolved_thenLaterLinesMoveToDescription() {
        val change = resolveEditorTitleChange("Buy milk\nand eggs", "", "")

        assertThat(change?.title).isEqualTo("Buy milk")
        assertThat(change?.description?.text).isEqualTo("and eggs")
        assertThat(change?.description?.selection).isEqualTo(TextRange(8))
    }

    @Test
    fun givenTextOverLimit_whenResolved_thenOverflowMovesToDescription() {
        val fits = padding(60)

        val change = resolveEditorTitleChange("$fits cuando", fits, "notes")

        assertThat(change?.title).isEqualTo(fits)
        assertThat(change?.description?.text).isEqualTo("cuando notes")
    }

    @Test
    fun givenOnlySpacesOverLimit_whenResolved_thenTitleEmptiesAndDescriptionKeepsItsFocusState() {
        val change = resolveEditorTitleChange(" ".repeat(EDITOR_MAX_TITLE_LENGTH + 1), "", "notes")

        assertThat(change).isEqualTo(EditorTitleChange("", description = null))
    }

    @Test
    fun givenFieldState_whenSameOverflowRepeats_thenEveryEditIsReported() {
        val state = EditorTitleFieldState()
        val title = padding(EDITOR_MAX_TITLE_LENGTH)
        val reported = mutableListOf<String>()

        repeat(2) {
            state.onValueChange(TextFieldValue(title + "b", TextRange(66)), title) { reported += it }
        }

        assertThat(reported).containsExactly(title + "b", title + "b")
        assertThat(state.displayed(title).text).isEqualTo(title)
        assertThat(state.displayed(title).selection).isEqualTo(TextRange(EDITOR_MAX_TITLE_LENGTH))
    }

    @Test
    fun givenFieldState_whenOnlyCursorMoves_thenNothingIsReported() {
        val state = EditorTitleFieldState()
        var reports = 0

        state.onValueChange(TextFieldValue("Buy", TextRange(1)), "Buy") { reports++ }

        assertThat(reports).isEqualTo(0)
        assertThat(state.displayed("Buy").selection).isEqualTo(TextRange(1))
    }
}
