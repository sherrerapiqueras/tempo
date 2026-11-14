package com.mandrecode.tempo.features.focus.domain.usecase

import com.google.common.truth.Truth.assertThat
import com.mandrecode.tempo.core.domain.repository.DailyFocusActivityRepository
import com.mandrecode.tempo.features.focus.domain.model.FocusSession
import com.mandrecode.tempo.features.focus.domain.model.SavedSession
import com.mandrecode.tempo.features.focus.domain.repository.FocusSessionRepository
import com.mandrecode.tempo.features.focus.domain.scheduler.FocusSessionScheduler
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import org.junit.Test
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

class FocusSessionUseCasesTest {
    private val start = Instant.fromEpochMilliseconds(1_800_000_000_000)
    private var now = start

    private val sessionFlow = MutableStateFlow<FocusSession?>(null)
    private val lengthFlow = MutableStateFlow(25)
    private val breakLengthFlow = MutableStateFlow(5)

    private val sessionRepository =
        mockk<FocusSessionRepository>(relaxed = true) {
            every { activeSession } returns sessionFlow
            every { defaultLengthMinutes } returns lengthFlow
            every { breakLengthMinutes } returns breakLengthFlow
            every { setActiveSession(any()) } answers { sessionFlow.value = firstArg() }
        }
    private val activityRepository = mockk<DailyFocusActivityRepository>(relaxed = true)
    private val scheduler = mockk<FocusSessionScheduler>(relaxed = true)

    private val useCases =
        FocusSessionUseCases(
            sessionRepository = sessionRepository,
            activityRepository = activityRepository,
            scheduler = scheduler,
            clock =
                object : Clock {
                    override fun now(): Instant = now
                },
        )

    @Test
    fun `starting schedules the alarm and posts the notification`() =
        runTest {
            useCases.start(taskId = 1, title = "Report")

            assertThat(sessionFlow.value?.taskId).isEqualTo(1)
            verify { scheduler.scheduleSessionEnd(any()) }
            verify { scheduler.showOngoingNotification(any()) }
        }

    @Test
    fun `starting uses the configured default length`() =
        runTest {
            lengthFlow.value = 45

            useCases.start(taskId = 1, title = "Report")

            assertThat(sessionFlow.value?.plannedLength).isEqualTo(45.minutes)
        }

    @Test
    fun `starting on a second task replaces the first and banks its minutes`() =
        runTest {
            useCases.start(taskId = 1, title = "Report")
            now = start + 10.minutes

            useCases.start(taskId = 2, title = "Emails")

            assertThat(sessionFlow.value?.taskId).isEqualTo(2)
            coVerify { activityRepository.addFocusMinutes(any<LocalDate>(), 10) }
        }

    @Test
    fun `there is only ever one session`() =
        runTest {
            useCases.start(taskId = 1, title = "Report")
            useCases.start(taskId = 2, title = "Emails")

            assertThat(sessionFlow.value?.taskId).isEqualTo(2)
        }

    @Test
    fun `pausing cancels the alarm, since it is tied to a wall-clock end`() =
        runTest {
            useCases.start(taskId = 1, title = "Report")
            now = start + 5.minutes

            useCases.pause()

            assertThat(sessionFlow.value?.isPaused).isTrue()
            verify { scheduler.cancelSessionEnd() }
        }

    @Test
    fun `resuming rearms the alarm`() =
        runTest {
            useCases.start(taskId = 1, title = "Report")
            now = start + 5.minutes
            useCases.pause()
            now = start + 30.minutes

            useCases.resume()

            assertThat(sessionFlow.value?.isPaused).isFalse()
            verify(atLeast = 2) { scheduler.scheduleSessionEnd(any()) }
        }

    @Test
    fun `ending banks whole elapsed minutes and clears everything`() =
        runTest {
            useCases.start(taskId = 1, title = "Report")
            now = start + 12.minutes

            val ended = useCases.end()

            assertThat(ended?.taskId).isEqualTo(1)
            assertThat(sessionFlow.value).isNull()
            coVerify { activityRepository.addFocusMinutes(any<LocalDate>(), 12) }
            verify { scheduler.cancelSessionEnd() }
            verify { scheduler.clearOngoingNotification() }
        }

    @Test
    fun `ending inside the first minute banks nothing`() =
        runTest {
            useCases.start(taskId = 1, title = "Report")

            useCases.end()

            coVerify(exactly = 0) { activityRepository.addFocusMinutes(any<LocalDate>(), any()) }
        }

    @Test
    fun `ending when nothing runs is a no-op`() =
        runTest {
            assertThat(useCases.end()).isNull()
        }

    @Test
    fun `a session that ran out counts as a run at the task`() =
        runTest {
            useCases.start(taskId = 1, title = "Report")
            now = start + 25.minutes

            useCases.end()

            coVerify { sessionRepository.recordSessionFor(taskId = 1, today = any()) }
        }

    @Test
    fun `stopping early does not count as a run at the task`() =
        runTest {
            useCases.start(taskId = 1, title = "Report")
            now = start + 12.minutes

            useCases.end()

            // The minutes are still banked — they were worked. What the card must not do is report
            // a session done for one the user cut short.
            coVerify { activityRepository.addFocusMinutes(any<LocalDate>(), 12) }
            coVerify { sessionRepository.addFocusMinutesFor(taskId = 1, minutes = 12, today = any()) }
            coVerify(exactly = 0) { sessionRepository.recordSessionFor(any(), any()) }
        }

    @Test
    fun `minutes are banked against the task as well as the day`() =
        runTest {
            useCases.start(taskId = 1, title = "Report")
            now = start + 25.minutes

            useCases.end()

            coVerify { activityRepository.addFocusMinutes(any<LocalDate>(), 25) }
            coVerify { sessionRepository.addFocusMinutesFor(taskId = 1, minutes = 25, today = any()) }
        }

    @Test
    fun `a break banks no minutes against the task either`() =
        runTest {
            useCases.start(taskId = 1, title = "Report", isBreak = true)
            now = start + 5.minutes

            useCases.end()

            coVerify(exactly = 0) { sessionRepository.addFocusMinutesFor(any(), any(), any()) }
        }

    @Test
    fun `a break never counts as a run at the task, however long it goes`() =
        runTest {
            useCases.start(taskId = 1, title = "Report", isBreak = true)
            now = start + 30.minutes

            useCases.end()

            coVerify(exactly = 0) { sessionRepository.recordSessionFor(any(), any()) }
        }

    @Test
    fun `reconcile completes a session whose time passed while the app was gone`() =
        runTest {
            useCases.start(taskId = 1, title = "Report")
            now = start + 40.minutes

            val settled = useCases.reconcile()

            assertThat(settled?.taskId).isEqualTo(1)
            assertThat(sessionFlow.value).isNull()
            coVerify { activityRepository.addFocusMinutes(any<LocalDate>(), 25) }
        }

    @Test
    fun `reconcile restores a session that still has time left`() =
        runTest {
            useCases.start(taskId = 1, title = "Report")
            now = start + 5.minutes

            val settled = useCases.reconcile()

            assertThat(settled).isNull()
            assertThat(sessionFlow.value?.taskId).isEqualTo(1)
            verify(atLeast = 2) { scheduler.scheduleSessionEnd(any()) }
        }

    @Test
    fun `reconcile leaves a paused session paused rather than rearming it`() =
        runTest {
            useCases.start(taskId = 1, title = "Report")
            now = start + 5.minutes
            useCases.pause()
            now = start + 300.minutes

            val settled = useCases.reconcile()

            assertThat(settled).isNull()
            assertThat(sessionFlow.value?.isPaused).isTrue()
        }

    @Test
    fun `reconcile with no session does nothing`() =
        runTest {
            assertThat(useCases.reconcile()).isNull()
        }

    private fun saved(
        rounds: Int = 2,
        taskId: Long? = null,
    ) = SavedSession(id = 4, title = "Study", focusMinutes = 25, breakMinutes = 5, rounds = rounds, taskId = taskId)

    @Test
    fun `starting a saved session runs its first round at its own length`() =
        runTest {
            lengthFlow.value = 45

            useCases.startSaved(saved())

            assertThat(sessionFlow.value?.title).isEqualTo("Study")
            assertThat(sessionFlow.value?.plannedLength).isEqualTo(25.minutes)
            assertThat(sessionFlow.value?.cycle?.round).isEqualTo(1)
            verify { scheduler.scheduleSessionEnd(any()) }
        }

    @Test
    fun `starting a saved session replaces what was running and banks its minutes`() =
        runTest {
            useCases.start(taskId = 1, title = "Report")
            now = start + 10.minutes

            useCases.startSaved(saved())

            assertThat(sessionFlow.value?.savedSessionId).isEqualTo(4)
            coVerify { activityRepository.addFocusMinutes(any<LocalDate>(), 10) }
        }

    @Test
    fun `time up on a round with more to go starts the rest without ever being without a session`() =
        runTest {
            val seen = mutableListOf<FocusSession?>()
            every { sessionRepository.setActiveSession(any()) } answers {
                seen += firstArg<FocusSession?>()
                sessionFlow.value = firstArg()
            }
            useCases.startSaved(saved())
            now = start + 25.minutes

            val timeUp = useCases.timeUp()

            assertThat(timeUp?.next?.isBreak).isTrue()
            assertThat(sessionFlow.value?.isBreak).isTrue()
            assertThat(sessionFlow.value?.plannedLength).isEqualTo(5.minutes)
            assertThat(seen).doesNotContain(null)
            coVerify { activityRepository.addFocusMinutes(any<LocalDate>(), 25) }
            // The new phase is announced, not folded silently into the old notification.
            verify { scheduler.clearOngoingNotification() }
            verify(atLeast = 2) { scheduler.scheduleSessionEnd(any()) }
        }

    @Test
    fun `time up on the rest starts the next round and banks nothing for resting`() =
        runTest {
            useCases.startSaved(saved())
            now = start + 25.minutes
            useCases.timeUp()
            now = start + 30.minutes

            val timeUp = useCases.timeUp()

            assertThat(timeUp?.next?.cycle?.round).isEqualTo(2)
            assertThat(sessionFlow.value?.isBreak).isFalse()
            coVerify(exactly = 1) { activityRepository.addFocusMinutes(any<LocalDate>(), any()) }
        }

    @Test
    fun `time up on the last round ends the session`() =
        runTest {
            useCases.startSaved(saved(rounds = 1))
            now = start + 25.minutes

            val timeUp = useCases.timeUp()

            assertThat(timeUp?.next).isNull()
            assertThat(timeUp?.finished?.title).isEqualTo("Study")
            assertThat(sessionFlow.value).isNull()
        }

    @Test
    fun `a late or repeated alarm finds a phase that has not run out and leaves it alone`() =
        runTest {
            useCases.startSaved(saved())
            now = start + 25.minutes
            useCases.timeUp()
            val rest = sessionFlow.value

            assertThat(useCases.timeUp()).isNull()
            assertThat(sessionFlow.value).isEqualTo(rest)
            coVerify(exactly = 1) { activityRepository.addFocusMinutes(any<LocalDate>(), any()) }
        }

    @Test
    fun `time up with nothing running, or on a paused session, does nothing`() =
        runTest {
            assertThat(useCases.timeUp()).isNull()

            useCases.startSaved(saved())
            useCases.pause()
            now = start + 60.minutes

            assertThat(useCases.timeUp()).isNull()
            assertThat(sessionFlow.value?.isPaused).isTrue()
        }

    @Test
    fun `skipping a rest between rounds starts the next round now`() =
        runTest {
            useCases.startSaved(saved())
            now = start + 25.minutes
            useCases.timeUp()
            now = start + 26.minutes

            assertThat(useCases.skipBreak()).isTrue()

            assertThat(sessionFlow.value?.isBreak).isFalse()
            assertThat(sessionFlow.value?.cycle?.round).isEqualTo(2)
            assertThat(sessionFlow.value?.runningSince).isEqualTo(start + 26.minutes)
        }

    @Test
    fun `there is no rest to skip during a round, after the last one, or with nothing running`() =
        runTest {
            assertThat(useCases.skipBreak()).isFalse()

            useCases.startSaved(saved())
            assertThat(useCases.skipBreak()).isFalse()

            val lastRound = sessionFlow.value?.cycle?.copy(round = 2)
            useCases.start(taskId = null, title = "Study", isBreak = true, cycle = lastRound)
            assertThat(useCases.skipBreak()).isFalse()
        }

    @Test
    fun `a session linked to no task banks the day's minutes and nothing against a task`() =
        runTest {
            useCases.startSaved(saved(rounds = 1))
            now = start + 25.minutes

            useCases.end()

            coVerify { activityRepository.addFocusMinutes(any<LocalDate>(), 25) }
            verify(exactly = 0) { sessionRepository.addFocusMinutesFor(any(), any(), any()) }
            verify(exactly = 0) { sessionRepository.recordSessionFor(any(), any()) }
        }

    @Test
    fun `a session linked to a task banks its minutes and its run against that task`() =
        runTest {
            useCases.startSaved(saved(rounds = 1, taskId = 9))
            now = start + 25.minutes

            useCases.end()

            verify { sessionRepository.addFocusMinutesFor(9, 25, any()) }
            verify { sessionRepository.recordSessionFor(9, any()) }
        }

    @Test
    fun `a session restored after its time ran out is ended, not moved on to its rest`() =
        runTest {
            useCases.startSaved(saved())
            now = start + 40.minutes

            val ended = useCases.reconcile()

            assertThat(ended?.title).isEqualTo("Study")
            assertThat(sessionFlow.value).isNull()
        }

    @Test
    fun `an alarm that arrives before the session has run out is armed again, not dropped`() =
        runTest {
            useCases.startSaved(saved())
            now = start + 24.minutes

            assertThat(useCases.timeUp()).isNull()

            assertThat(sessionFlow.value?.cycle?.round).isEqualTo(1)
            verify(exactly = 2) { scheduler.scheduleSessionEnd(any()) }
            coVerify(exactly = 0) { activityRepository.addFocusMinutes(any<LocalDate>(), any()) }
        }

    @Test
    fun `the next phase is stored before the finished round is banked`() =
        runTest {
            useCases.startSaved(saved())
            now = start + 25.minutes

            useCases.timeUp()

            io.mockk.coVerifyOrder {
                sessionRepository.setActiveSession(match { it?.isBreak == true })
                activityRepository.addFocusMinutes(any<LocalDate>(), 25)
            }
        }
}
