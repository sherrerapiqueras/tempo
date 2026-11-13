package com.mandrecode.tempo.features.focus.domain.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class FocusSessionTest {
    private val start = Instant.fromEpochMilliseconds(1_800_000_000_000)

    private fun session(length: Int = 25): FocusSession {
        val title = "Report"
        return FocusSession.start(taskId = 1, title = title, now = start, length = length.minutes)
    }

    @Test
    fun `a fresh session has its full length remaining`() {
        assertThat(session().remaining(start)).isEqualTo(25.minutes)
    }

    @Test
    fun `remaining time is derived from the clock, not counted down`() {
        assertThat(session().remaining(start + 10.minutes)).isEqualTo(15.minutes)
    }

    @Test
    fun `remaining time never goes negative`() {
        assertThat(session().remaining(start + 40.minutes)).isEqualTo(kotlin.time.Duration.ZERO)
    }

    @Test
    fun `a session expires once its time is up`() {
        val session = session()

        assertThat(session.hasExpired(start + 24.minutes)).isFalse()
        assertThat(session.hasExpired(start + 25.minutes)).isTrue()
    }

    @Test
    fun `pausing banks the elapsed time and stops the clock`() {
        val paused = session().pause(start + 10.minutes)

        assertThat(paused.isPaused).isTrue()
        assertThat(paused.elapsed(start + 30.minutes)).isEqualTo(10.minutes)
        assertThat(paused.remaining(start + 30.minutes)).isEqualTo(15.minutes)
    }

    @Test
    fun `a paused session never expires`() {
        val paused = session().pause(start + 10.minutes)

        assertThat(paused.hasExpired(start + 90.minutes)).isFalse()
    }

    @Test
    fun `resuming continues from where it paused`() {
        val paused = session().pause(start + 10.minutes)
        val resumed = paused.resume(start + 60.minutes)

        assertThat(resumed.isPaused).isFalse()
        assertThat(resumed.remaining(start + 60.minutes)).isEqualTo(15.minutes)
        assertThat(resumed.remaining(start + 65.minutes)).isEqualTo(10.minutes)
    }

    @Test
    fun `pausing twice is a no-op rather than double-banking`() {
        val paused = session().pause(start + 10.minutes)

        assertThat(paused.pause(start + 20.minutes)).isEqualTo(paused)
    }

    @Test
    fun `resuming a running session is a no-op`() {
        val running = session()

        assertThat(running.resume(start + 5.minutes)).isEqualTo(running)
    }

    @Test
    fun `elapsed time is capped at the planned length`() {
        assertThat(session().elapsed(start + 90.minutes)).isEqualTo(25.minutes)
    }

    @Test
    fun `a session stopped inside its first minute banks nothing`() {
        assertThat(session().bankableMinutes(start + 45.seconds)).isEqualTo(0)
    }

    @Test
    fun `only whole minutes are banked`() {
        assertThat(session().bankableMinutes(start + 10.minutes + 59.seconds)).isEqualTo(10)
    }

    @Test
    fun `endsAt is the wall-clock time the alarm and chronometer share`() {
        assertThat(session().endsAt).isEqualTo(start + 25.minutes)
    }

    @Test
    fun `a paused session has no end time to schedule against`() {
        assertThat(session().pause(start + 5.minutes).endsAt).isNull()
    }

    @Test
    fun `resuming pushes the end time out by the time spent paused`() {
        val resumed = session().pause(start + 10.minutes).resume(start + 60.minutes)

        assertThat(resumed.endsAt).isEqualTo(start + 75.minutes)
    }

    private fun saved(rounds: Int) = SavedSession(4, "Study", 25, 5, rounds = rounds, taskId = 9)

    @Test
    fun `a saved session starts on its first round with its own lengths and task`() {
        val session = FocusSession.start(saved(rounds = 3), start)

        assertThat(session.title).isEqualTo("Study")
        assertThat(session.taskId).isEqualTo(9)
        assertThat(session.plannedLength).isEqualTo(25.minutes)
        assertThat(session.cycle)
            .isEqualTo(SessionCycle(4, round = 1, totalRounds = 3, focusLength = 25.minutes, breakLength = 5.minutes))
        assertThat(session.savedSessionId).isEqualTo(4)
    }

    @Test
    fun `a focus round with rounds to go is followed by its rest`() {
        val next = FocusSession.start(saved(rounds = 2), start).nextPhase(start + 25.minutes)

        assertThat(next?.isBreak).isTrue()
        assertThat(next?.plannedLength).isEqualTo(5.minutes)
        assertThat(next?.runningSince).isEqualTo(start + 25.minutes)
        assertThat(next?.cycle?.round).isEqualTo(1)
    }

    @Test
    fun `a rest between rounds is followed by the next round, starting clean`() {
        val rest = FocusSession.start(saved(rounds = 2), start).nextPhase(start + 25.minutes)!!

        val next = rest.pause(start + 27.minutes).nextPhase(start + 30.minutes)

        assertThat(next?.isBreak).isFalse()
        assertThat(next?.plannedLength).isEqualTo(25.minutes)
        assertThat(next?.cycle?.round).isEqualTo(2)
        assertThat(next?.completedBeforeNow).isEqualTo(kotlin.time.Duration.ZERO)
        assertThat(next?.isPaused).isFalse()
    }

    @Test
    fun `nothing follows the last round, a single round, or a timer with no cycle`() {
        val lastRound =
            FocusSession
                .start(saved(rounds = 2), start)
                .nextPhase(start)!!
                .nextPhase(start)!!

        assertThat(lastRound.nextPhase(start)).isNull()
        assertThat(FocusSession.start(saved(rounds = 1), start).nextPhase(start)).isNull()
        assertThat(session().nextPhase(start)).isNull()
    }

    @Test
    fun `a rest taken after the last round leads nowhere by itself`() {
        val lastRound = FocusSession.start(saved(rounds = 1), start)
        val rest = lastRound.copy(isBreak = true)

        assertThat(rest.nextPhase(start)).isNull()
    }

    @Test
    fun `rounds beyond what a session may repeat are capped when it starts`() {
        assertThat(FocusSession.start(saved(rounds = 99), start).cycle?.totalRounds)
            .isEqualTo(FocusSession.ROUNDS_RANGE.last)
    }

    @Test
    fun `the subject is the saved session when there is one, else the task`() {
        assertThat(session().subjectKey).isEqualTo("task:1")
        assertThat(FocusSession.start(saved(rounds = 1), start).subjectKey).isEqualTo("session:4")
        assertThat(FocusSession.subjectKeyOf(taskId = 1))
            .isNotEqualTo(FocusSession.subjectKeyOf(taskId = 1, savedSessionId = 1))
    }

    @Test
    fun `only a session of more than one round has rounds to speak of`() {
        assertThat(FocusSession.start(saved(rounds = 1), start).cycle?.hasRounds).isFalse()
        assertThat(FocusSession.start(saved(rounds = 2), start).cycle?.hasRounds).isTrue()
    }

    @Test
    fun `a finished cycle has nothing left to follow it`() {
        val midWay = FocusSession.start(saved(rounds = 4), start)
        val rest = midWay.copy(isBreak = true, cycle = midWay.cycle?.finished())

        assertThat(rest.cycle?.round).isEqualTo(4)
        assertThat(rest.nextPhase(start)).isNull()
        assertThat(rest.savedSessionId).isEqualTo(4)
    }

    @Test
    fun `a task can be given rounds of its own without a saved session behind them`() {
        val rounds = FocusSession.adHocCycle(focusMinutes = 25, breakMinutes = 5, rounds = 2)
        val running = FocusSession.start(taskId = 1, title = "Report", now = start, cycle = rounds)

        assertThat(rounds?.savedSessionId).isNull()
        assertThat(running.savedSessionId).isNull()
        assertThat(running.subjectKey).isEqualTo("task:1")
        assertThat(running.nextPhase(start)?.isBreak).isTrue()
    }

    @Test
    fun `one round is no cycle at all, and too many are capped`() {
        assertThat(FocusSession.adHocCycle(focusMinutes = 25, breakMinutes = 5, rounds = 1)).isNull()
        assertThat(FocusSession.adHocCycle(focusMinutes = 25, breakMinutes = 5, rounds = 0)).isNull()
        assertThat(FocusSession.adHocCycle(focusMinutes = 25, breakMinutes = 5, rounds = 99)?.totalRounds)
            .isEqualTo(FocusSession.ROUNDS_RANGE.last)
    }
}
