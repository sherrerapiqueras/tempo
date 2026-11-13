package com.mandrecode.tempo.features.focus.domain.usecase

import com.google.common.truth.Truth.assertThat
import com.mandrecode.tempo.features.focus.domain.model.FocusSession
import com.mandrecode.tempo.features.focus.domain.model.TaskFocusToday
import com.mandrecode.tempo.features.focus.domain.repository.FocusSessionRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.minus
import org.junit.Test
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

class HasFocusTimeTodayUseCaseTest {
    private val today = LocalDate(2026, 7, 30)
    private val clock =
        object : Clock {
            override fun now(): Instant = today.atStartOfDayIn(TimeZone.currentSystemDefault()) + 9.hours
        }

    private val activeSession = MutableStateFlow<FocusSession?>(null)
    private val repository =
        mockk<FocusSessionRepository> {
            every { this@mockk.activeSession } returns this@HasFocusTimeTodayUseCaseTest.activeSession
            every { focusOn(any(), any()) } returns TaskFocusToday()
        }

    private val useCase = HasFocusTimeTodayUseCase(repository, clock)

    @Test
    fun `a task with a finished session today has had focus time`() {
        every { repository.focusOn(TASK_ID, today) } returns TaskFocusToday(sessions = 1, minutes = 25)

        assertThat(useCase(TASK_ID)).isTrue()
    }

    @Test
    fun `minutes alone are enough, because a session stopped early is still work done`() {
        every { repository.focusOn(TASK_ID, today) } returns TaskFocusToday(sessions = 0, minutes = 4)

        assertThat(useCase(TASK_ID)).isTrue()
    }

    @Test
    fun `a session running right now counts before it has banked anything`() {
        activeSession.value = session(taskId = TASK_ID)

        assertThat(useCase(TASK_ID)).isTrue()
    }

    @Test
    fun `a break taken from the task counts as the task's own time`() {
        activeSession.value = session(taskId = TASK_ID, isBreak = true)

        assertThat(useCase(TASK_ID)).isTrue()
    }

    @Test
    fun `a session on some other task says nothing about this one`() {
        activeSession.value = session(taskId = 99L)

        assertThat(useCase(TASK_ID)).isFalse()
    }

    @Test
    fun `yesterday's work is not today's`() {
        every { repository.focusOn(TASK_ID, today.minus(1, DateTimeUnit.DAY)) } returns
            TaskFocusToday(sessions = 2, minutes = 50)

        // The use case asks about the clock's day, and the day it asks about has nothing on it.
        assertThat(useCase(TASK_ID)).isFalse()
    }

    @Test
    fun `an untouched task has had no focus time`() {
        assertThat(useCase(TASK_ID)).isFalse()
    }

    @Test
    fun `a running session answers without reaching for the stored day`() {
        activeSession.value = session(taskId = TASK_ID)

        assertThat(useCase(TASK_ID)).isTrue()

        // The sweep asks this once per overdue task; the field read is the cheaper of the two.
        verify(exactly = 0) { repository.focusOn(any(), any()) }
    }

    private fun session(
        taskId: Long,
        isBreak: Boolean = false,
    ) = FocusSession(
        taskId = taskId,
        title = "Task $taskId",
        plannedLength = FocusSession.DEFAULT_LENGTH,
        isBreak = isBreak,
        runningSince = clock.now(),
    )

    private companion object {
        const val TASK_ID = 7L
    }
}
