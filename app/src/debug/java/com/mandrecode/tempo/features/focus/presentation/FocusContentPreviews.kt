package com.mandrecode.tempo.features.focus.presentation

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.mandrecode.tempo.core.domain.model.DailyFocusActivity
import com.mandrecode.tempo.core.domain.model.Priority
import com.mandrecode.tempo.core.ui.theme.TempoTheme
import com.mandrecode.tempo.features.focus.domain.model.FocusAgendaItem
import com.mandrecode.tempo.features.focus.domain.model.FocusSession
import com.mandrecode.tempo.features.focus.domain.model.SavedSession
import com.mandrecode.tempo.features.focus.domain.model.SavedSessionEntry
import com.mandrecode.tempo.features.tasks.domain.model.Task
import kotlinx.collections.immutable.persistentListOf
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.minus
import kotlin.time.Clock

private val today = LocalDate(2026, 7, 29)

private fun task(
    id: Long,
    title: String,
    hour: Int?,
    priority: Priority? = null,
    isCompleted: Boolean = false,
    date: LocalDate = today,
) = Task(
    id = id,
    title = title,
    description = "",
    isCompleted = isCompleted,
    priority = priority,
    reminderDate = hour?.let { LocalDateTime(date, LocalTime(it, 0)) },
)

private fun history(vararg pairs: Pair<Int, Int>) =
    pairs
        .mapIndexed { index, (scheduled, completed) ->
            DailyFocusActivity(
                date = today.minus(pairs.size - 1 - index, DateTimeUnit.DAY),
                scheduledCount = scheduled,
                completedCount = completed,
            )
        }.let { persistentListOf(*it.toTypedArray()) }

private val midDayState =
    FocusContract.UiState(
        isLoading = false,
        today = today,
        streakDays = 14,
        history = history(3 to 3, 2 to 1, 0 to 0, 4 to 4, 2 to 2, 3 to 1, 5 to 1),
        scheduledCount = 5,
        completedCount = 1,
        upNext =
            persistentListOf(
                FocusAgendaItem.TaskEntry(
                    task(1, "Finish Q3 budget report", hour = 9, priority = Priority.HIGH),
                ),
                FocusAgendaItem.TaskEntry(
                    task(2, "Reply to the design review", hour = 11, priority = Priority.MEDIUM),
                ),
            ),
        overdue =
            persistentListOf(
                // id 4, not 2: id 2 is already the "Reply to the design review" entry in upNext,
                // and `UiState.entryFor()` resolves a task id against upNext + overdue + todayItems
                // with firstOrNull, so a duplicate makes this row unreachable by id.
                FocusAgendaItem.TaskEntry(
                    task(4, "Renew gym membership", hour = 10, date = today.minus(2, DateTimeUnit.DAY)),
                ),
            ),
        todayItems =
            persistentListOf(
                FocusAgendaItem.TaskEntry(
                    task(1, "Finish Q3 budget report", hour = 9, priority = Priority.HIGH),
                ),
                FocusAgendaItem.TaskEntry(task(2, "Reply to the design review", hour = 11, priority = Priority.MEDIUM)),
                FocusAgendaItem.TaskEntry(task(3, "Reply to client emails", hour = 12)),
                FocusAgendaItem.TaskEntry(task(5, "Send the invoice", hour = 14, isCompleted = true)),
            ),
        undatedTaskCount = 12,
    )

private val emptyDayState =
    FocusContract.UiState(
        isLoading = false,
        today = today,
        streakDays = 0,
        history = history(0 to 0, 0 to 0, 0 to 0, 0 to 0, 0 to 0, 0 to 0, 0 to 0),
        undatedTaskCount = 3,
    )

private val allDoneState =
    midDayState.copy(
        scheduledCount = 2,
        completedCount = 2,
        upNext = persistentListOf(),
        overdue = persistentListOf(),
        todayItems =
            persistentListOf(
                FocusAgendaItem.TaskEntry(task(5, "Send the invoice", hour = 14, isCompleted = true)),
                FocusAgendaItem.TaskEntry(
                    task(3, "Reply to client emails", hour = 12, isCompleted = true),
                ),
            ),
    )

@Preview(name = "Focus - mid day", showBackground = true, device = "id:pixel_9")
@Composable
private fun FocusContentMidDayPreview() {
    TempoTheme { FocusContent(uiState = midDayState, onEvent = {}) }
}

@Preview(name = "Focus - mid day dark", showBackground = true, device = "id:pixel_9")
@Composable
private fun FocusContentMidDayDarkPreview() {
    TempoTheme(darkTheme = true) { FocusContent(uiState = midDayState, onEvent = {}) }
}

@Preview(name = "Focus - nothing due", showBackground = true, device = "id:pixel_9")
@Composable
private fun FocusContentEmptyPreview() {
    TempoTheme { FocusContent(uiState = emptyDayState, onEvent = {}) }
}

@Preview(name = "Focus - all done", showBackground = true, device = "id:pixel_9")
@Composable
private fun FocusContentAllDonePreview() {
    TempoTheme { FocusContent(uiState = allDoneState, onEvent = {}) }
}

private val savedSessions =
    persistentListOf(
        SavedSessionEntry(
            session = SavedSession(id = 1, title = "Study", focusMinutes = 50, breakMinutes = 10, rounds = 4),
        ),
        SavedSessionEntry(
            session = SavedSession(id = 2, title = "Deep work", focusMinutes = 25, breakMinutes = 5, taskId = 3),
            linkedTask = task(3, "Reply to client emails", hour = 12),
        ),
    )

@Preview(name = "Focus - saved sessions", showBackground = true, device = "id:pixel_9")
@Composable
private fun FocusContentSavedSessionsPreview() {
    TempoTheme { FocusContent(uiState = midDayState.copy(savedSessions = savedSessions), onEvent = {}) }
}

@Preview(name = "Focus - nothing due, saved sessions", showBackground = true, device = "id:pixel_9")
@Composable
private fun FocusContentEmptyDaySavedSessionsPreview() {
    TempoTheme(darkTheme = true) {
        FocusContent(uiState = emptyDayState.copy(savedSessions = savedSessions), onEvent = {})
    }
}

@Preview(name = "Focus - saved session on round 2 of 4", showBackground = true, device = "id:pixel_9")
@Composable
private fun FocusContentRunningSavedSessionPreview() {
    val running = FocusSession.start(savedSessions.first().session, Clock.System.now())
    TempoTheme {
        FocusContent(
            uiState =
                midDayState.copy(
                    savedSessions = savedSessions,
                    session = running.copy(cycle = running.cycle?.copy(round = 2)),
                ),
            onEvent = {},
        )
    }
}

@Preview(name = "Focus - loading", showBackground = true, device = "id:pixel_9")
@Composable
private fun FocusContentLoadingPreview() {
    TempoTheme { FocusContent(uiState = FocusContract.UiState(), onEvent = {}) }
}
