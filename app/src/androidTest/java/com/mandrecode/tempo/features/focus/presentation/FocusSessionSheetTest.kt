package com.mandrecode.tempo.features.focus.presentation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import com.google.common.truth.Truth.assertThat
import com.mandrecode.tempo.core.ui.theme.TempoTheme
import com.mandrecode.tempo.features.focus.domain.model.FocusAgendaItem
import com.mandrecode.tempo.features.focus.domain.model.FocusSession
import com.mandrecode.tempo.features.focus.domain.model.SavedSession
import com.mandrecode.tempo.features.focus.presentation.components.RECENT_SESSIONS_ROW_TEST_TAG
import com.mandrecode.tempo.features.focus.presentation.components.SESSION_EDITOR_TITLE_FIELD_TEST_TAG
import com.mandrecode.tempo.features.focus.presentation.components.SESSION_ROUNDS_TEST_TAG
import com.mandrecode.tempo.features.tasks.domain.model.Task
import kotlinx.collections.immutable.persistentListOf
import org.junit.Rule
import org.junit.Test
import kotlin.time.Clock

/**
 * The one session sheet (#517): a new topic session and a task are both set up and run in it, and
 * starting changes it in place rather than swapping it for another.
 */
class FocusSessionSheetTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private val chapter = Task(id = 7, title = "Read chapter 3", description = "")
    private val study = SavedSession(id = 3, title = "Study", focusMinutes = 50, breakMinutes = 10, rounds = 4)
    private val base = FocusContract.UiState(isLoading = false)
    private val settingUp = base.copy(setupSessionId = SavedSession.NEW_ID)
    private val events = mutableListOf<FocusContract.UiEvent>()
    private var uiState by mutableStateOf(base)

    private fun setSheet(state: FocusContract.UiState) {
        uiState = state
        composeTestRule.setContent {
            TempoTheme {
                FocusSessionSheet(uiState = uiState, onEvent = { events += it }, onDismiss = {})
            }
        }
    }

    /**
     * Types the name and puts the keyboard away. A new session opens with the name focused, and on
     * a small screen the keyboard sits over the buttons the test goes on to press.
     */
    private fun nameIt(name: String) {
        composeTestRule.onNodeWithTag(SESSION_EDITOR_TITLE_FIELD_TEST_TAG).performTextInput(name)
        composeTestRule.onNodeWithTag(SESSION_EDITOR_TITLE_FIELD_TEST_TAG).performImeAction()
        composeTestRule.waitForIdle()
    }

    @Test
    fun newSession_showsTheRingAtTheConfiguredLengthWithNothingToManage() {
        setSheet(settingUp)

        composeTestRule.onNodeWithText("25:00").assertIsDisplayed()
        composeTestRule.onNodeWithText("5 min").assertIsDisplayed()
        composeTestRule.onNodeWithText("Add session").assertDoesNotExist()
        composeTestRule.onNodeWithText("Link a task").assertDoesNotExist()
        // No recents yet, so no row for them.
        composeTestRule.onNodeWithTag(RECENT_SESSIONS_ROW_TEST_TAG).assertDoesNotExist()
    }

    @Test
    fun startingWithoutAName_asksForOneInsteadOfStarting() {
        setSheet(settingUp)

        composeTestRule.onNodeWithText("Start 25 min").performScrollTo().performClick()

        composeTestRule.onNodeWithText("Title is required").assertExists()
        assertThat(events).isEmpty()
    }

    @Test
    fun namedSession_startsWithWhatWasSet_andTheRingFollowsTheLength() {
        setSheet(settingUp)

        nameIt("Deep work")
        composeTestRule.onNodeWithContentDescription("More rounds").performScrollTo().performClick()
        composeTestRule.onNodeWithContentDescription("Longer session").performClick()
        composeTestRule.onNodeWithContentDescription("Shorter rest").performClick()
        composeTestRule.onNodeWithText("30:00").assertExists()
        composeTestRule.onNodeWithTag(SESSION_ROUNDS_TEST_TAG, useUnmergedTree = true).assertExists()
        composeTestRule.onNodeWithText("Start 30 min").performScrollTo().performClick()

        assertThat(events)
            .containsExactly(
                FocusContract.UiEvent.StartNewSession(
                    SavedSession(id = 0, title = "Deep work", focusMinutes = 30, breakMinutes = 4, rounds = 2),
                ),
            )
    }

    @Test
    fun aRecentSession_fillsTheFormAndStartsAsItWas() {
        setSheet(settingUp.copy(recentSessions = persistentListOf(study)))

        composeTestRule.onNodeWithText("Study").performScrollTo().performClick()

        composeTestRule.onNodeWithTag(SESSION_EDITOR_TITLE_FIELD_TEST_TAG).assertTextContains("Study")
        composeTestRule.onNodeWithText("50:00").assertIsDisplayed()
        // The chip of the topic now in the form shows as the one chosen.
        composeTestRule.onNode(hasText("Study") and isSelected()).assertExists()
        composeTestRule.onNodeWithText("Start 50 min").performScrollTo().performClick()

        assertThat(events).containsExactly(FocusContract.UiEvent.StartNewSession(study.copy(id = 0)))
    }

    private val onTask =
        base.copy(previewTaskId = chapter.id, todayItems = persistentListOf(FocusAgendaItem.TaskEntry(chapter)))

    @Test
    fun task_getsTheSameControlsAndStartsWithWhatWasSet() {
        setSheet(onTask.copy(recentSessions = persistentListOf(study)))

        // Its title heads the sheet and names the link.
        composeTestRule.onAllNodesWithText("Read chapter 3").assertCountEquals(2)
        // A task is the task: no recents, nothing to remove, nothing to save.
        composeTestRule.onNodeWithText("Study").assertDoesNotExist()
        composeTestRule.onNodeWithContentDescription("Remove the linked task").assertDoesNotExist()
        composeTestRule.onNodeWithText("Add session").assertDoesNotExist()
        composeTestRule.onNodeWithContentDescription("More rounds").performScrollTo().performClick()
        composeTestRule.onNodeWithText("Start 25 min").performScrollTo().performClick()

        assertThat(events)
            .containsExactly(FocusContract.UiEvent.StartSession(lengthMinutes = 25, breakMinutes = 5, rounds = 2))
    }

    @Test
    fun theTasksChip_opensTheTaskItself() {
        setSheet(onTask)
        composeTestRule.onAllNodesWithText("Read chapter 3")[1].performClick()

        assertThat(events).containsExactly(FocusContract.UiEvent.EditTask(chapter))
    }

    @Test
    fun aTopicSession_hasNoTaskRow() {
        setSheet(base.copy(session = FocusSession.start(study, Clock.System.now())))

        composeTestRule.onNodeWithContentDescription("Linked task").assertDoesNotExist()
        composeTestRule.onNodeWithText("Mark done").assertDoesNotExist()
    }

    @Test
    fun theTasksDescription_isSignalledOnTheChipAndNeverWrittenOut() {
        val noted = chapter.copy(description = "Pages 41 to 68")
        val state = base.copy(previewTaskId = noted.id, todayItems = persistentListOf(FocusAgendaItem.TaskEntry(noted)))
        setSheet(state)

        composeTestRule.onNodeWithContentDescription("Has a description", useUnmergedTree = true).assertExists()
        composeTestRule.onNodeWithText("Pages 41 to 68").assertDoesNotExist()

        // The same once the timer runs on it: nothing arrives with the timer.
        uiState =
            state.copy(
                previewTaskId = null,
                session = FocusSession.start(taskId = noted.id, title = noted.title, now = Clock.System.now()),
            )
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithContentDescription("Has a description", useUnmergedTree = true).assertExists()
        composeTestRule.onNodeWithText("Pages 41 to 68").assertDoesNotExist()
    }

    @Test
    fun aTaskWithNoDescription_showsNoSuchSign() {
        setSheet(onTask)

        composeTestRule.onNodeWithContentDescription("Has a description", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun starting_changesTheSameSheetIntoTheRunningTimer() {
        setSheet(settingUp.copy(recentSessions = persistentListOf(study)))
        composeTestRule.onNodeWithText("Study").performScrollTo().performClick()
        composeTestRule.onNodeWithText("Start 50 min").assertExists()

        // What the view model does on Start: the session runs, and is no longer being set up.
        uiState = base.copy(session = FocusSession.start(study, Clock.System.now()))
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Study").assertIsDisplayed()
        composeTestRule.onNodeWithText("Focusing · Round 1 of 4").assertIsDisplayed()
        composeTestRule.onNodeWithTag(SESSION_ROUNDS_TEST_TAG, useUnmergedTree = true).assertIsDisplayed()
        composeTestRule.onNodeWithText("Pause").assertExists()
        composeTestRule.onNodeWithText("Start 50 min").assertDoesNotExist()
        composeTestRule.onNodeWithContentDescription("More rounds").assertDoesNotExist()
        composeTestRule.onNodeWithTag(RECENT_SESSIONS_ROW_TEST_TAG).assertDoesNotExist()
    }
}
