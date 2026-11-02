package com.mandrecode.tempo.core.ui.editor

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** Production uses Android's own `BreakIterator`, so the emoji rules are checked against it too. */
class EditorTitleSplitDeviceTest {
    private fun assertMovesWhole(
        emoji: String,
        kept: Int,
    ) {
        val fits = "a".repeat(kept)

        assertThat(splitEditorTitle(fits + emoji)).isEqualTo(EditorTitleSplit(fits, emoji))
    }

    @Test
    fun surrogatePair_crossingLimit_movesWhole() {
        assertMovesWhole("😀", kept = EDITOR_MAX_TITLE_LENGTH - 1)
    }

    @Test
    fun joinedEmoji_crossingLimit_movesWhole() {
        assertMovesWhole("👨‍👩‍👧", kept = EDITOR_MAX_TITLE_LENGTH - 3)
    }

    @Test
    fun flag_crossingLimit_movesWhole() {
        assertMovesWhole("🇪🇸", kept = EDITOR_MAX_TITLE_LENGTH - 2)
    }

    @Test
    fun variationSequence_crossingLimit_movesWhole() {
        assertMovesWhole("❤️", kept = EDITOR_MAX_TITLE_LENGTH - 1)
    }

    @Test
    fun skinToneEmoji_crossingLimit_movesWhole() {
        assertMovesWhole("👍🏽", kept = EDITOR_MAX_TITLE_LENGTH - 3)
    }
}
