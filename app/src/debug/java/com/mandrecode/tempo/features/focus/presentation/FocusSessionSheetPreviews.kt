package com.mandrecode.tempo.features.focus.presentation

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.mandrecode.tempo.core.ui.theme.TempoTheme
import com.mandrecode.tempo.features.focus.domain.model.FocusAgendaItem
import com.mandrecode.tempo.features.focus.domain.model.FocusSession
import com.mandrecode.tempo.features.focus.domain.model.SavedSession
import com.mandrecode.tempo.features.focus.domain.model.SavedSessionEntry
import com.mandrecode.tempo.features.tasks.domain.model.Task
import kotlinx.collections.immutable.persistentListOf
import kotlin.time.Clock

private val chapter = Task(id = 7, title = "Read chapter 3", description = "Pages 41 to 68")
private val study =
    SavedSession(id = 1, title = "Study", focusMinutes = 50, breakMinutes = 10, rounds = 4, taskId = chapter.id)
private val base =
    FocusContract.UiState(
        isLoading = false,
        savedSessions = persistentListOf(SavedSessionEntry(study, linkedTask = chapter)),
        linkableTasks = persistentListOf(chapter),
    )

@Composable
private fun Sheet(uiState: FocusContract.UiState) {
    TempoTheme { FocusSessionSheet(uiState = uiState, onEvent = {}, onDismiss = {}) }
}

@Preview(name = "Session sheet – new session", showBackground = true)
@Composable
private fun SessionSheetNewPreview() {
    Sheet(base.copy(setupSessionId = SavedSession.NEW_ID))
}

@Preview(name = "Session sheet – saved session, before starting", showBackground = true)
@Preview(
    name = "Session sheet – saved session, before starting, dark",
    showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun SessionSheetSavedPreview() {
    Sheet(base.copy(setupSessionId = study.id))
}

@Preview(name = "Session sheet – task, before starting", showBackground = true)
@Composable
private fun SessionSheetTaskPreview() {
    Sheet(base.copy(previewTaskId = chapter.id, todayItems = persistentListOf(FocusAgendaItem.TaskEntry(chapter))))
}

@Preview(name = "Session sheet – round 2 of 4", showBackground = true)
@Composable
private fun SessionSheetRunningPreview() {
    val running = FocusSession.start(study, Clock.System.now())
    Sheet(base.copy(session = running.copy(cycle = running.cycle?.copy(round = 2))))
}
