package com.mandrecode.tempo.features.widget.presentation

import com.google.common.truth.Truth.assertThat
import com.mandrecode.tempo.core.domain.model.Priority
import com.mandrecode.tempo.features.focus.domain.model.FocusAgenda
import com.mandrecode.tempo.features.focus.domain.model.FocusAgendaItem
import com.mandrecode.tempo.features.tasks.domain.model.Task
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.atTime
import org.junit.Test

class WidgetDayTest {
    private val today = LocalDate(2026, 11, 22)

    private fun entry(
        id: Long,
        at: LocalDateTime?,
        completed: Boolean = false,
        category: String? = "Work",
    ) = FocusAgendaItem.TaskEntry(
        task = Task(id = id, title = "Task $id", description = "", isCompleted = completed, reminderDate = at),
        categoryName = category,
    )

    @Test
    fun `today's rows come before overdue ones and keep the agenda's order`() {
        val agenda =
            FocusAgenda(
                today = listOf(entry(1, today.atTime(9, 0)), entry(2, today.atTime(15, 0))),
                overdue = listOf(entry(3, LocalDate(2026, 11, 21).atTime(8, 0))),
            )

        val day = WidgetDay.from(today, agenda)

        assertThat(day.rows.map { it.taskId }).containsExactly(1L, 2L, 3L).inOrder()
        assertThat(day.rows.first().dueTime).isEqualTo(LocalTime(9, 0))
    }

    @Test
    fun `an overdue row is labelled instead of showing a time`() {
        val agenda = FocusAgenda(overdue = listOf(entry(3, LocalDate(2026, 11, 21).atTime(8, 0))))

        val row = WidgetDay.from(today, agenda).rows.single()

        assertThat(row.isOverdue).isTrue()
        assertThat(row.dueTime).isNull()
        assertThat(row.categoryName).isEqualTo("Work")
    }

    @Test
    fun `completed rows sink below every open row, overdue ones included`() {
        val agenda =
            FocusAgenda(
                today = listOf(entry(1, today.atTime(9, 0)), entry(2, today.atTime(10, 0), completed = true)),
                overdue = listOf(entry(3, LocalDate(2026, 11, 20).atTime(8, 0))),
            )

        val day = WidgetDay.from(today, agenda)

        assertThat(day.rows.map { it.taskId }).containsExactly(1L, 3L, 2L).inOrder()
        assertThat(day.completedCount).isEqualTo(1)
        assertThat(day.scheduledCount).isEqualTo(3)
        assertThat(day.remainingCount).isEqualTo(2)
    }

    @Test
    fun `a completed overdue row is not labelled overdue`() {
        val agenda = FocusAgenda(overdue = listOf(entry(3, LocalDate(2026, 11, 20).atTime(8, 0), completed = true)))

        assertThat(
            WidgetDay
                .from(today, agenda)
                .rows
                .single()
                .isOverdue,
        ).isFalse()
    }

    @Test
    fun `a row carries its task's priority`() {
        val task =
            Task(
                id = 7,
                title = "Urgent",
                description = "",
                priority = Priority.HIGH,
                reminderDate = today.atTime(9, 0),
            )
        val agenda = FocusAgenda(today = listOf(FocusAgendaItem.TaskEntry(task = task)))

        assertThat(
            WidgetDay
                .from(today, agenda)
                .rows
                .single()
                .priority,
        ).isEqualTo(Priority.HIGH)
    }

    @Test
    fun `an empty day has no rows`() {
        val day = WidgetDay.from(today, FocusAgenda())

        assertThat(day.isEmpty).isTrue()
        assertThat(day.remainingCount).isEqualTo(0)
        assertThat(day.date).isEqualTo(today)
    }
}
