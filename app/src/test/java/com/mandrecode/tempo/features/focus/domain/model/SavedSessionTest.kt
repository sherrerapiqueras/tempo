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
    fun `the same setup is the same name and numbers, whatever the id or the link`() {
        assertThat(study.hasSameSetupAs(study.copy(id = 9, taskId = 3))).isTrue()
        assertThat(study.hasSameSetupAs(study.copy(title = "Read"))).isFalse()
        assertThat(study.hasSameSetupAs(study.copy(focusMinutes = 50))).isFalse()
        assertThat(study.hasSameSetupAs(study.copy(breakMinutes = 10))).isFalse()
        assertThat(study.hasSameSetupAs(study.copy(rounds = 1))).isFalse()
    }
}
