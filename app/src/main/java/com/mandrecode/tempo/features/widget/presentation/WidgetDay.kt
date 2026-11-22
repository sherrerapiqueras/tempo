package com.mandrecode.tempo.features.widget.presentation

import com.mandrecode.tempo.core.domain.model.Priority
import com.mandrecode.tempo.features.focus.domain.model.FocusAgenda
import com.mandrecode.tempo.features.focus.domain.model.FocusAgendaItem
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime

/**
 * What the Today widget draws: the Focus day, flattened into the rows a home-screen list can show.
 *
 * A plain value, so the widget's updater can tell when anything visible has changed (and skip the
 * render when nothing has), and so the mapping from the agenda can be tested on the JVM.
 */
data class WidgetDay(
    val date: LocalDate,
    val rows: ImmutableList<WidgetTaskRow> = persistentListOf(),
    val completedCount: Int = 0,
    val scheduledCount: Int = 0,
) {
    val isEmpty: Boolean get() = rows.isEmpty()

    /** Open work still on the day, for the 1×1 tile. */
    val remainingCount: Int get() = scheduledCount - completedCount

    companion object {
        /**
         * Today's rows before the overdue ones, the order Focus reads its sections in, and each
         * section's own order (open work first, timed before untimed) kept as the agenda gives it.
         * Within the flattened list, completed rows sink below every open one, so what is left to
         * do is always at the top of a widget too short to show it all.
         */
        fun from(
            date: LocalDate,
            agenda: FocusAgenda,
        ): WidgetDay {
            val today = agenda.today.map { it.toRow(isOverdue = false) }
            val overdue = agenda.overdue.map { it.toRow(isOverdue = true) }
            val (open, done) = (today + overdue).partition { !it.isCompleted }
            return WidgetDay(
                date = date,
                rows = (open + done).toImmutableList(),
                completedCount = agenda.completedCount,
                scheduledCount = agenda.scheduledCount,
            )
        }

        private fun FocusAgendaItem.toRow(isOverdue: Boolean): WidgetTaskRow =
            when (this) {
                is FocusAgendaItem.TaskEntry ->
                    WidgetTaskRow(
                        taskId = task.id,
                        title = task.title,
                        // An overdue row says so instead of a time: a bare "08:00" on last week's
                        // task reads as something due this morning (as on Focus's own cards).
                        dueTime = dueTime.takeUnless { isOverdue },
                        isOverdue = isOverdue && !isCompleted,
                        categoryName = categoryName,
                        isCompleted = isCompleted,
                        priority = priority,
                    )
            }
    }
}

data class WidgetTaskRow(
    val taskId: Long,
    val title: String,
    val dueTime: LocalTime?,
    val isOverdue: Boolean,
    val categoryName: String?,
    val isCompleted: Boolean,
    val priority: Priority? = null,
)
