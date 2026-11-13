package com.mandrecode.tempo.features.focus.presentation.components

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.mandrecode.tempo.core.ui.theme.TempoTheme
import com.mandrecode.tempo.features.focus.domain.model.SavedSession
import com.mandrecode.tempo.features.focus.presentation.FocusContract
import com.mandrecode.tempo.features.tasks.domain.model.Task
import kotlinx.collections.immutable.persistentListOf

private val chapter = Task(id = 7, title = "Read chapter 3", description = "")
private val tasks = persistentListOf(chapter, Task(id = 8, title = "Write up the lab notes", description = ""))

@Preview(name = "Session sheet – new", showBackground = true)
@Composable
private fun SessionEditorSheetNewPreview() {
    TempoTheme {
        SessionEditorSheet(
            target = FocusContract.SessionEditorTarget(),
            linkableTasks = tasks,
            defaultFocusMinutes = 25,
            defaultBreakMinutes = 5,
            onEvent = {},
        )
    }
}

@Preview(name = "Session sheet – existing, linked", showBackground = true)
@Preview(
    name = "Session sheet – existing, linked, dark",
    showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun SessionEditorSheetExistingPreview() {
    TempoTheme {
        SessionEditorSheet(
            target =
                FocusContract.SessionEditorTarget(
                    session =
                        SavedSession(
                            id = 1,
                            title = "Study",
                            focusMinutes = 50,
                            breakMinutes = 10,
                            rounds = 4,
                            taskId = chapter.id,
                        ),
                    linkedTask = chapter,
                ),
            linkableTasks = tasks,
            defaultFocusMinutes = 25,
            defaultBreakMinutes = 5,
            onEvent = {},
        )
    }
}
