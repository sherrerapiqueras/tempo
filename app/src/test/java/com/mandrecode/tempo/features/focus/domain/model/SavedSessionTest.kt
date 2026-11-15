package com.mandrecode.tempo.features.focus.domain.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SavedSessionTest {
    private val study = SavedSession(id = 1, title = "Study", focusMinutes = 25, breakMinutes = 5, rounds = 4)

    @Test
    fun `normalizing trims the name and brings every number into range`() {
        val wild = study.copy(title = "  Study  ", focusMinutes = 0, breakMinutes = 99_999, rounds = 0)

        val normalized = wild.normalized()

        assertThat(normalized.title).isEqualTo("Study")
        assertThat(normalized.focusMinutes).isEqualTo(FocusSession.SESSION_LENGTH_RANGE.first)
        assertThat(normalized.breakMinutes).isEqualTo(FocusSession.BREAK_LENGTH_RANGE.last)
        assertThat(normalized.rounds).isEqualTo(1)
    }

    @Test
    fun `a session already in range is left as it is`() {
        assertThat(study.normalized()).isEqualTo(study)
    }

    @Test
    fun `the same topic is the same name, whatever its case, spacing or numbers`() {
        assertThat(study.hasSameNameAs(study.copy(id = 9, title = " study ", focusMinutes = 50))).isTrue()
        assertThat(study.hasSameNameAs(study.copy(title = "Read"))).isFalse()
    }
}
