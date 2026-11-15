package com.mandrecode.tempo.features.focus.presentation.components

import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import com.google.common.truth.Truth.assertThat
import com.mandrecode.tempo.features.focus.domain.model.SavedSession
import com.mandrecode.tempo.features.focus.presentation.FocusContract
import org.junit.Test

/** What the session sheet makes of what is typed into it. */
class SessionEditorFormTest {
    private fun newForm() =
        SessionEditorForm(
            isNew = true,
            title = mutableStateOf(""),
            focusMinutes = mutableIntStateOf(25),
            breakMinutes = mutableIntStateOf(5),
            rounds = mutableIntStateOf(1),
            showsTitleError = mutableStateOf(false),
        )

    @Test
    fun `a new session cannot start until it has a name, and starts with no id`() {
        val form = newForm()

        assertThat(form.canStart).isFalse()
        form.title = "Read"

        assertThat(form.canStart).isTrue()
        assertThat(form.newSession)
            .isEqualTo(SavedSession(id = SavedSession.NEW_ID, title = "Read", focusMinutes = 25, breakMinutes = 5))
    }

    @Test
    fun `picking a recent session fills the form with it and clears a name error`() {
        val form = newForm()
        form.showsTitleError = true

        form.fillFrom(SavedSession(id = 3, title = "Study", focusMinutes = 50, breakMinutes = 10, rounds = 4))

        assertThat(form.newSession)
            .isEqualTo(SavedSession(id = 0, title = "Study", focusMinutes = 50, breakMinutes = 10, rounds = 4))
        assertThat(form.showsTitleError).isFalse()
    }

    @Test
    fun `a run on a task takes the lengths and rounds set`() {
        val form = newForm()
        form.focusMinutes = 50
        form.rounds = 3

        assertThat(form.startOnTask())
            .isEqualTo(FocusContract.UiEvent.StartSession(lengthMinutes = 50, breakMinutes = 5, rounds = 3))
    }
}
