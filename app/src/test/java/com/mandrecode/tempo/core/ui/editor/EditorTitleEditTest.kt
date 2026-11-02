package com.mandrecode.tempo.core.ui.editor

import com.google.common.truth.Truth.assertThat
import com.mandrecode.tempo.core.domain.util.ValidationUtils
import org.junit.Test

class EditorTitleEditTest {
    @Test
    fun givenPlainText_whenResolved_thenKeptAsIs() {
        assertThat(resolveEditorTitleEdit("Buy milk", "Buy mil")).isEqualTo("Buy milk")
    }

    @Test
    fun givenVeryLongText_whenResolved_thenKeptWhole() {
        val long = "word ".repeat(200)

        assertThat(resolveEditorTitleEdit(long, "")).isEqualTo(long)
    }

    @Test
    fun givenEnterKey_whenResolved_thenNull() {
        assertThat(resolveEditorTitleEdit("Buy\n milk", "Buy milk")).isNull()
    }

    @Test
    fun givenPastedLines_whenResolved_thenJoinedWithSingleSpaces() {
        assertThat(resolveEditorTitleEdit("First line\nSecond line", "")).isEqualTo("First line Second line")
    }

    @Test
    fun givenPastedWindowsLineBreaksAndSpaces_whenResolved_thenJoinedWithSingleSpaces() {
        assertThat(resolveEditorTitleEdit("First  \r\n\r\n  Second", "")).isEqualTo("First Second")
    }

    @Test
    fun givenPastedEdgeLineBreaks_whenResolved_thenTrimmed() {
        assertThat(resolveEditorTitleEdit("\nFirst\n", "")).isEqualTo("First")
    }

    @Test
    fun givenOnlyLineBreaksPasted_whenResolved_thenEmpty() {
        assertThat(resolveEditorTitleEdit(" \n \n ", "")).isEmpty()
    }

    @Test
    fun givenOtherUnicodeLineBreak_whenResolved_thenJoinedWithSingleSpace() {
        assertThat(resolveEditorTitleEdit("First\u2028Second", "")).isEqualTo("First Second")
    }

    @Test
    fun givenTitleAtValidationLimit_whenChecked_thenCanAutosave() {
        assertThat(canAutosaveEditorTitle("a".repeat(ValidationUtils.MAX_TITLE_LENGTH))).isTrue()
    }

    @Test
    fun givenTitleOverValidationLimit_whenChecked_thenCannotAutosave() {
        assertThat(canAutosaveEditorTitle("a".repeat(ValidationUtils.MAX_TITLE_LENGTH + 1))).isFalse()
    }

    @Test
    fun givenBlankTitle_whenChecked_thenCannotAutosave() {
        assertThat(canAutosaveEditorTitle("  ")).isFalse()
    }
}
