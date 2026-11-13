package com.mandrecode.tempo.features.focus.presentation.components

import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import com.google.common.truth.Truth.assertThat
import com.mandrecode.tempo.features.focus.domain.model.SavedSession
import org.junit.Test

/** What the session sheet makes of what is typed into it, and what it saves as it closes. */
class SessionEditorFormTest {
    private val study = SavedSession(3, "Study", focusMinutes = 50, breakMinutes = 10, rounds = 4, taskId = 7)

    private fun formOf(original: SavedSession?) =
        SessionEditorForm(
            original = original,
            title = mutableStateOf(original?.title.orEmpty()),
            focusMinutes = mutableIntStateOf(original?.focusMinutes ?: 25),
            breakMinutes = mutableIntStateOf(original?.breakMinutes ?: 5),
            rounds = mutableIntStateOf(original?.rounds ?: 1),
            linkedTaskId = mutableStateOf(original?.taskId),
            showsTitleError = mutableStateOf(false),
        )

    @Test
    fun `a new session cannot be saved until it has a name, and never saves on close`() {
        val form = formOf(null)

        assertThat(form.isNew).isTrue()
        assertThat(form.canSave).isFalse()
        form.title = "Read"

        assertThat(form.canSave).isTrue()
        assertThat(form.savesOnClose).isFalse()
        assertThat(form.draft.id).isEqualTo(0)
    }

    @Test
    fun `an existing session left as it was does not save on close`() {
        assertThat(formOf(study).savesOnClose).isFalse()
    }

    @Test
    fun `an existing session that was changed saves what it was changed to`() {
        val form = formOf(study)
        form.rounds = 2
        form.linkedTaskId = null

        assertThat(form.savesOnClose).isTrue()
        assertThat(form.closingDraft.toSavedSession()).isEqualTo(study.copy(rounds = 2, taskId = null))
    }

    @Test
    fun `a name rubbed out goes back to what it was and keeps the other changes`() {
        val form = formOf(study)
        form.title = "  "
        form.focusMinutes = 25

        assertThat(form.canSave).isFalse()
        assertThat(form.savesOnClose).isTrue()
        assertThat(form.closingDraft.toSavedSession()).isEqualTo(study.copy(focusMinutes = 25))
    }

    @Test
    fun `only surrounding spaces added to the name is no change`() {
        val form = formOf(study)
        form.title = " Study "

        assertThat(form.savesOnClose).isFalse()
    }
}
