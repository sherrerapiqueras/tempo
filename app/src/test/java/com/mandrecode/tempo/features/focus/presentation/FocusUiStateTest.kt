package com.mandrecode.tempo.features.focus.presentation

import com.google.common.truth.Truth.assertThat
import com.mandrecode.tempo.features.focus.domain.model.FocusAgendaItem
import com.mandrecode.tempo.features.focus.domain.model.FocusSession
import com.mandrecode.tempo.features.focus.domain.model.SavedSession
import com.mandrecode.tempo.features.focus.domain.model.TaskFocusToday
import com.mandrecode.tempo.features.tasks.domain.model.Task
import kotlinx.collections.immutable.persistentListOf
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import org.junit.Test
import kotlin.time.Instant

class FocusUiStateTest {
    private val today = LocalDate(2026, 7, 29)
    private val now = Instant.fromEpochMilliseconds(1_800_000_000_000)

    private fun task(id: Long) =
        Task(
            id = id,
            title = "Task $id",
            description = "",
            reminderDate = LocalDateTime(today, LocalTime(9, 0)),
        )

    @Test
    fun `progress is zero when nothing is scheduled, rather than dividing by zero`() {
        val state = FocusContract.UiState(scheduledCount = 0, completedCount = 0)

        assertThat(state.progress).isEqualTo(0f)
    }

    @Test
    fun `progress is the completed ratio`() {
        val state = FocusContract.UiState(scheduledCount = 4, completedCount = 1)

        assertThat(state.progress).isEqualTo(0.25f)
    }

    @Test
    fun `progress is clamped when more was completed than scheduled`() {
        val state = FocusContract.UiState(scheduledCount = 2, completedCount = 5)

        assertThat(state.progress).isEqualTo(1f)
    }

    @Test
    fun `the day is empty only when both sections are`() {
        assertThat(FocusContract.UiState().isDayEmpty).isTrue()
        assertThat(
            FocusContract
                .UiState(
                    todayItems = persistentListOf(FocusAgendaItem.TaskEntry(task(1))),
                ).isDayEmpty,
        ).isFalse()
    }

    @Test
    fun `session subtasks are empty when nothing is running`() {
        val state =
            FocusContract.UiState(
                todayItems =
                    persistentListOf(
                        FocusAgendaItem.TaskEntry(task(1), subtasks = listOf(task(2))),
                    ),
            )

        assertThat(state.sessionSubtasks).isEmpty()
    }

    @Test
    fun `session subtasks come from the task the session runs on`() {
        val state =
            FocusContract.UiState(
                session = FocusSession.start(taskId = 1, title = "Task 1", now = now),
                todayItems =
                    persistentListOf(
                        FocusAgendaItem.TaskEntry(task(1), subtasks = listOf(task(2), task(3))),
                        FocusAgendaItem.TaskEntry(task(4), subtasks = listOf(task(5))),
                    ),
            )

        assertThat(state.sessionSubtasks.map { it.id }).containsExactly(2L, 3L)
    }

    @Test
    fun `session subtasks are found in the overdue section too`() {
        val state =
            FocusContract.UiState(
                session = FocusSession.start(taskId = 9, title = "Overdue", now = now),
                overdue =
                    persistentListOf(FocusAgendaItem.TaskEntry(task(9), subtasks = listOf(task(10)))),
            )

        assertThat(state.sessionSubtasks.map { it.id }).containsExactly(10L)
    }

    @Test
    fun `a session on a nested subtask resolves to that subtask`() {
        val state =
            FocusContract.UiState(
                session = FocusSession.start(taskId = 2, title = "Task 2", now = now),
                todayItems =
                    persistentListOf(
                        FocusAgendaItem.TaskEntry(
                            task(1),
                            subtasks = listOf(task(2)),
                            categoryName = "Work",
                            subtaskFocusToday = mapOf(2L to TaskFocusToday(sessions = 1, minutes = 25)),
                        ),
                    ),
            )

        assertThat(state.sessionEntry?.task?.id).isEqualTo(2L)
        // The subtask's own time, banked by earlier sessions on it, stays on its card.
        assertThat(state.sessionEntry?.focusToday).isEqualTo(TaskFocusToday(sessions = 1, minutes = 25))
        assertThat(state.sessionEntry?.categoryName).isEqualTo("Work")
        assertThat(state.runningEntry?.task?.id).isEqualTo(2L)
    }

    @Test
    fun `a session on a task that left the agenda yields no subtasks`() {
        val state =
            FocusContract.UiState(
                session = FocusSession.start(taskId = 99, title = "Gone", now = now),
                todayItems = persistentListOf(FocusAgendaItem.TaskEntry(task(1))),
            )

        assertThat(state.sessionSubtasks).isEmpty()
    }

    @Test
    fun `the card you tapped is the one the screen is about, not the one counting down`() {
        val state =
            FocusContract.UiState(
                session = FocusSession.start(taskId = 1, title = "Task 1", now = now),
                previewTaskId = 4,
                todayItems =
                    persistentListOf(
                        FocusAgendaItem.TaskEntry(task(1)),
                        FocusAgendaItem.TaskEntry(task(4)),
                    ),
            )

        assertThat(state.sessionTaskId).isEqualTo(4)
        assertThat(state.sessionEntry?.task?.id).isEqualTo(4)
        // And no countdown: the ring belongs to the task it is running on, not to the one being
        // looked at.
        assertThat(state.screenSession).isNull()
    }

    @Test
    fun `the running session is the subject once nothing else is being previewed`() {
        val session = FocusSession.start(taskId = 1, title = "Task 1", now = now)
        val state =
            FocusContract.UiState(
                session = session,
                todayItems = persistentListOf(FocusAgendaItem.TaskEntry(task(1))),
            )

        assertThat(state.sessionTaskId).isEqualTo(1)
        assertThat(state.screenSession).isEqualTo(session)
    }

    @Test
    fun `previewing the running session's own task still counts down`() {
        val session = FocusSession.start(taskId = 1, title = "Task 1", now = now)
        val state =
            FocusContract.UiState(
                session = session,
                previewTaskId = 1,
                todayItems = persistentListOf(FocusAgendaItem.TaskEntry(task(1))),
            )

        assertThat(state.screenSession).isEqualTo(session)
    }

    private fun saved(id: Long) = SavedSession(id, "Study $id", focusMinutes = 25, breakMinutes = 5, rounds = 2)

    @Test
    fun `an empty day with a running session is not shown as empty, and recents alone do not count`() {
        assertThat(FocusContract.UiState().showsEmptyState).isTrue()
        assertThat(FocusContract.UiState(recentSessions = persistentListOf(saved(1))).showsEmptyState).isTrue()
        assertThat(FocusContract.UiState(session = FocusSession.start(saved(1), now)).showsEmptyState).isFalse()
    }

    @Test
    fun `the screen's subject tells a task from a topic session with the same id`() {
        val onTask = FocusContract.UiState(session = FocusSession.start(taskId = 1, title = "Report", now = now))
        val onTopic = FocusContract.UiState(session = FocusSession.start(saved(1), now))

        assertThat(onTask.sessionSubjectKey).isEqualTo("task:1")
        assertThat(onTopic.sessionSubjectKey).isEqualTo("session:1")
        assertThat(FocusContract.UiState().sessionSubjectKey).isNull()
    }

    @Test
    fun `previewing a task makes it the subject and stills the topic session's countdown`() {
        val running = FocusSession.start(saved(4), now)
        val state = FocusContract.UiState(session = running, previewTaskId = 2)

        assertThat(state.sessionSubjectKey).isEqualTo("task:2")
        assertThat(state.screenSession).isNull()
        assertThat(state.copy(previewTaskId = null).screenSession).isEqualTo(running)
    }

    @Test
    fun `a topic session has no entry`() {
        val state = FocusContract.UiState(session = FocusSession.start(saved(4), now))

        assertThat(state.sessionEntry).isNull()
        assertThat(state.sessionSubtasks).isEmpty()
    }

    @Test
    fun `another session after a recent topic is offered at that topic's own length`() {
        val study = saved(1).copy(focusMinutes = 50)
        val state =
            FocusContract.UiState(
                defaultSessionLengthMinutes = 25,
                recentSessions = persistentListOf(study),
                lastSession = FocusSession.start(study, now),
            )

        assertThat(state.nextSessionMinutes).isEqualTo(50)
        // Dropped off the recents, or a plain task timer: what starts again is at the set length.
        assertThat(state.copy(recentSessions = persistentListOf()).nextSessionMinutes).isEqualTo(25)
        assertThat(FocusContract.UiState(defaultSessionLengthMinutes = 30).nextSessionMinutes).isEqualTo(30)
    }

    @Test
    fun `a timer on a task always offers finishing it`() {
        val state = FocusContract.UiState(session = FocusSession.start(taskId = 1, title = "Report", now = now))

        assertThat(state.canCompleteRunningTask).isTrue()
        assertThat(state.canCompleteScreenTask).isTrue()
    }

    @Test
    fun `a topic session has nothing to finish`() {
        val state = FocusContract.UiState(session = FocusSession.start(saved(4), now))

        assertThat(state.canCompleteRunningTask).isFalse()
        assertThat(state.canCompleteScreenTask).isFalse()
    }

    @Test
    fun `a task being looked at before it starts can be finished from the screen`() {
        val state = FocusContract.UiState(session = FocusSession.start(saved(4), now), previewTaskId = 2)

        assertThat(state.canCompleteScreenTask).isTrue()
        assertThat(FocusContract.UiState().canCompleteRunningTask).isFalse()
    }

    @Test
    fun `the pause toggle asks for whichever the session is not doing`() {
        val running = FocusSession.start(taskId = 1, title = "Report", now = now)

        assertThat(running.pauseResumeEvent()).isEqualTo(FocusContract.UiEvent.PauseSession)
        assertThat(running.pause(now).pauseResumeEvent()).isEqualTo(FocusContract.UiEvent.ResumeSession)
    }

    @Test
    fun `a new session is set up under a stand-in key and shows nothing of what else is running`() {
        val elsewhere = FocusSession.start(taskId = 1, title = "Report", now = now)
        val state =
            FocusContract.UiState(
                todayItems = persistentListOf(FocusAgendaItem.TaskEntry(task(1))),
                session = elsewhere,
                setupSessionId = SavedSession.NEW_ID,
            )

        assertThat(state.isSettingUpSession).isTrue()
        assertThat(state.sessionSubjectKey).isEqualTo("session:0")
        assertThat(state.screenSession).isNull()
        // Not the running task's details under a session that has nothing to do with it.
        assertThat(state.sessionEntry).isNull()
    }

    @Test
    fun `a task run with rounds of its own is still the task's timer`() {
        val entry = FocusAgendaItem.TaskEntry(task(1))
        val rounds = FocusSession.adHocCycle(focusMinutes = 25, breakMinutes = 5, rounds = 3)
        val state =
            FocusContract.UiState(
                todayItems = persistentListOf(entry),
                upNext = persistentListOf(entry),
                session = FocusSession.start(taskId = 1, title = "Task 1", now = now, cycle = rounds),
            )

        assertThat(state.sessionSubjectKey).isEqualTo("task:1")
        assertThat(state.visibleToday).isEmpty()
        assertThat(state.queuedUpNext).isEmpty()
        assertThat(state.canCompleteRunningTask).isTrue()
    }

    @Test
    fun `a start that asked first is asked for again as it was`() {
        val request = FocusContract.UiEvent.StartSession(lengthMinutes = 50, taskId = 1, breakMinutes = 10, rounds = 3)

        val pending = request.toPendingStart(task(1), replacing = "Emails")

        assertThat(pending.replacingTaskTitle).isEqualTo("Emails")
        assertThat(pending.toStartSession()).isEqualTo(request)
        assertThat(request.cycle(defaultFocusMinutes = 25, defaultBreakMinutes = 5)?.totalRounds).isEqualTo(3)
        assertThat(FocusContract.UiEvent.StartSession().cycle(25, 5)).isNull()
    }
}
