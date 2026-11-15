package com.mandrecode.tempo.features.focus.presentation.components

import com.google.common.truth.Truth.assertThat
import com.mandrecode.tempo.features.focus.domain.model.FocusSession
import org.junit.Test

/** Which rounds read as done, under way and still to come. */
class SessionRoundsTest {
    private val fourRounds = FocusSession.adHocCycle(focusMinutes = 25, breakMinutes = 5, rounds = 4)

    @Test
    fun `during a round the ones before it are done and it is under way`() {
        val rounds = SessionRounds.of(fourRounds?.copy(round = 2), isBreak = false)

        assertThat(rounds?.done).isEqualTo(1)
        assertThat(rounds?.underWay).isEqualTo(2)
        assertThat(rounds?.pending).isEqualTo(2)
    }

    @Test
    fun `during a rest the round it follows is done and none is under way`() {
        val rounds = SessionRounds.of(fourRounds?.copy(round = 2), isBreak = true)

        assertThat(rounds?.done).isEqualTo(2)
        assertThat(rounds?.underWay).isNull()
        assertThat(rounds?.pending).isEqualTo(2)
    }

    @Test
    fun `before starting every round is still to come`() {
        val rounds = SessionRounds.planned(3)

        assertThat(rounds?.done).isEqualTo(0)
        assertThat(rounds?.underWay).isNull()
        assertThat(rounds?.pending).isEqualTo(3)
    }

    @Test
    fun `a single round has no rounds to show`() {
        assertThat(SessionRounds.planned(1)).isNull()
        assertThat(SessionRounds.of(null, isBreak = false)).isNull()
        assertThat(SessionRounds.of(fourRounds?.copy(totalRounds = 1), isBreak = false)).isNull()
    }
}
