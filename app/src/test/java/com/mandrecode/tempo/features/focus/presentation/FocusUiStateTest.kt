package com.mandrecode.tempo.features.focus.presentation

import com.google.common.truth.Truth.assertThat
import com.mandrecode.tempo.features.focus.domain.model.FocusAgendaItem
import com.mandrecode.tempo.features.focus.domain.model.FocusSession
import com.mandrecode.tempo.features.focus.domain.model.SavedSession
import com.mandrecode.tempo.features.focus.domain.model.SavedSessionEntry
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

    private fun saved(
        id: Long,
        taskId: Long? = null,
    ) = SavedSession(id = id, title = "Study $id", focusMinutes = 25, breakMinutes = 5, rounds = 2, taskId = taskId)

    @Test
    fun `an empty day with saved sessions or a running session is not shown as empty`() {
        val study = SavedSessionEntry(saved(1))

        assertThat(FocusContract.UiState().showsEmptyState).isTrue()
        assertThat(FocusContract.UiState(savedSessions = persistentListOf(study)).showsEmptyState).isFalse()
        assertThat(FocusContract.UiState(session = FocusSession.start(saved(1), now)).showsEmptyState).isFalse()
    }

    @Test
    fun `the running saved session is not offered for starting again`() {
        val state =
            FocusContract.UiState(
                savedSessions = persistentListOf(SavedSessionEntry(saved(1)), SavedSessionEntry(saved(2))),
                session = FocusSession.start(saved(1), now),
            )

        assertThat(state.startableSessions.map { it.session.id }).containsExactly(2L)
    }

    @Test
    fun `a saved session linked to a task leaves that task's row in the day`() {
        val entry = FocusAgendaItem.TaskEntry(task(1))
        val state =
            FocusContract.UiState(
                todayItems = persistentListOf(entry),
                session = FocusSession.start(saved(4, taskId = 1), now),
            )

        assertThat(state.visibleToday).containsExactly(entry)
    }

    @Test
    fun `the screen's subject tells a task from a saved session with the same id`() {
        val onTask = FocusContract.UiState(session = FocusSession.start(taskId = 1, title = "Report", now = now))
        val onSaved = FocusContract.UiState(session = FocusSession.start(saved(1), now))

        assertThat(onTask.sessionSubjectKey).isEqualTo("task:1")
        assertThat(onSaved.sessionSubjectKey).isEqualTo("session:1")
        assertThat(FocusContract.UiState().sessionSubjectKey).isNull()
    }

    @Test
    fun `previewing a task makes it the subject and stills the saved session's countdown`() {
        val running = FocusSession.start(saved(4), now)
        val state = FocusContract.UiState(session = running, previewTaskId = 2)

        assertThat(state.sessionSubjectKey).isEqualTo("task:2")
        assertThat(state.screenSession).isNull()
        assertThat(state.copy(previewTaskId = null).screenSession).isEqualTo(running)
    }

    @Test
    fun `a linked task that is not on today is still found for the session's controls`() {
        val elsewhere = task(8)
        val state =
            FocusContract.UiState(
                savedSessions = persistentListOf(SavedSessionEntry(saved(4, taskId = 8), linkedTask = elsewhere)),
                session = FocusSession.start(saved(4, taskId = 8), now),
            )

        assertThat(state.sessionEntry?.task).isEqualTo(elsewhere)
        assertThat(state.runningEntry?.task).isEqualTo(elsewhere)
    }

    @Test
    fun `a session linked to no task has no entry`() {
        val state = FocusContract.UiState(session = FocusSession.start(saved(4), now))

        assertThat(state.sessionEntry).isNull()
        assertThat(state.sessionSubtasks).isEmpty()
    }

    @Test
    fun `a draft becomes the session it describes`() {
        val draft =
            FocusContract.SessionDraft(3, "Study", focusMinutes = 50, breakMinutes = 10, rounds = 4, taskId = 7)

        assertThat(draft.toSavedSession())
            .isEqualTo(SavedSession(3, "Study", focusMinutes = 50, breakMinutes = 10, rounds = 4, taskId = 7))
        assertThat(FocusContract.SessionEditorTarget().isNew).isTrue()
        assertThat(FocusContract.SessionEditorTarget(session = saved(1)).isNew).isFalse()
    }
}
