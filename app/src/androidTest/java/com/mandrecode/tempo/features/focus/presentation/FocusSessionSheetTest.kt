package com.mandrecode.tempo.features.focus.presentation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
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
import com.mandrecode.tempo.features.focus.domain.model.SavedSessionEntry
import com.mandrecode.tempo.features.focus.presentation.components.SESSION_EDITOR_TITLE_FIELD_TEST_TAG
import com.mandrecode.tempo.features.focus.presentation.components.SESSION_ROUNDS_TEST_TAG
import com.mandrecode.tempo.features.tasks.domain.model.Task
import kotlinx.collections.immutable.persistentListOf
import org.junit.Rule
import org.junit.Test
import kotlin.time.Clock

/**
 * The one session sheet (#517): a new session, a saved session and a task are all set up and run
 * in it, and starting changes it in place rather than swapping it for another.
 */
class FocusSessionSheetTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private val chapter = Task(id = 7, title = "Read chapter 3", description = "")
    private val study =
        SavedSession(id = 3, title = "Study", focusMinutes = 50, breakMinutes = 10, rounds = 4, taskId = chapter.id)
    private val base =
        FocusContract.UiState(
            isLoading = false,
            savedSessions = persistentListOf(SavedSessionEntry(study, linkedTask = chapter)),
            linkableTasks = persistentListOf(chapter),
        )
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
    fun newSession_showsTheRingAtTheConfiguredLengthAndCannotBeAddedWithoutAName() {
        setSheet(base.copy(setupSessionId = SavedSession.NEW_ID))

        composeTestRule.onNodeWithText("25:00").assertIsDisplayed()
        composeTestRule.onNodeWithText("5 min").assertIsDisplayed()
        composeTestRule.onNodeWithText("Add session").assertIsNotEnabled()
    }

    @Test
    fun startingWithoutAName_asksForOneInsteadOfSaving() {
        setSheet(base.copy(setupSessionId = SavedSession.NEW_ID))

        composeTestRule.onNodeWithText("Start 25 min").performScrollTo().performClick()

        composeTestRule.onNodeWithText("Title is required").assertExists()
        assertThat(events).isEmpty()
    }

    @Test
    fun namedSession_isHandedBackWithWhatWasSet_andTheRingFollowsTheLength() {
        setSheet(base.copy(setupSessionId = SavedSession.NEW_ID))

        nameIt("Deep work")
        composeTestRule.onNodeWithContentDescription("More rounds").performScrollTo().performClick()
        composeTestRule.onNodeWithContentDescription("Longer session").performClick()
        composeTestRule.onNodeWithContentDescription("Shorter rest").performClick()
        composeTestRule.onNodeWithText("30:00").assertExists()
        composeTestRule.onNodeWithTag(SESSION_ROUNDS_TEST_TAG, useUnmergedTree = true).assertExists()
        composeTestRule
            .onNodeWithText("Add session")
            .assertIsEnabled()
            .performScrollTo()
            .performClick()

        assertThat(events)
            .containsExactly(
                FocusContract.UiEvent.SaveSession(
                    FocusContract.SessionDraft(
                        id = 0,
                        title = "Deep work",
                        focusMinutes = 30,
                        breakMinutes = 4,
                        rounds = 2,
                        taskId = null,
                    ),
                ),
            )
    }

    @Test
    fun linkingATask_putsItOnTheSessionAndStartSavesAndStarts() {
        setSheet(base.copy(setupSessionId = SavedSession.NEW_ID))

        nameIt("Deep work")
        composeTestRule.onNodeWithText("Link a task").performScrollTo().performClick()
        // The picker is a sheet of its own and takes a moment to arrive.
        composeTestRule.waitUntil(PICKER_TIMEOUT_MS) {
            composeTestRule.onAllNodesWithText("Read chapter 3").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText("Read chapter 3").performClick()
        composeTestRule.onNodeWithText("Start 25 min").performScrollTo().performClick()

        val save = events.single() as FocusContract.UiEvent.SaveSession
        assertThat(save.startNow).isTrue()
        assertThat(save.draft.taskId).isEqualTo(chapter.id)
    }

    @Test
    fun savedSession_opensOnItsValuesAndOffersDeletingAfterAsking() {
        setSheet(base.copy(setupSessionId = study.id))

        composeTestRule.onNodeWithTag(SESSION_EDITOR_TITLE_FIELD_TEST_TAG).assertTextContains("Study")
        composeTestRule.onNodeWithText("50:00").assertIsDisplayed()
        composeTestRule.onNodeWithText("Read chapter 3").assertExists()
        composeTestRule.onNodeWithText("Add session").assertDoesNotExist()

        composeTestRule.onNodeWithText("Delete session").performScrollTo().performClick()
        assertThat(events).isEmpty()
        composeTestRule.onNodeWithText("Delete this session?").assertIsDisplayed()
        composeTestRule.onNodeWithText("Delete").performClick()

        assertThat(events).containsExactly(FocusContract.UiEvent.DeleteSession(3))
    }

    @Test
    fun unlinkingTheTask_goesBackToOfferingALink() {
        setSheet(base.copy(setupSessionId = study.id))

        composeTestRule.onNodeWithContentDescription("Remove the linked task").performScrollTo().performClick()

        composeTestRule.onNodeWithText("Link a task").assertExists()
    }

    private val onTask =
        base.copy(previewTaskId = chapter.id, todayItems = persistentListOf(FocusAgendaItem.TaskEntry(chapter)))

    @Test
    fun task_getsTheSameControlsAndStartsWithWhatWasSet() {
        setSheet(onTask)

        // Its title heads the sheet and names the link, in the place a saved session's link stands.
        composeTestRule.onAllNodesWithText("Read chapter 3").assertCountEquals(2)
        // A task already is the task: nothing to pick, nothing to save or delete.
        composeTestRule.onNodeWithText("Link a task").assertDoesNotExist()
        composeTestRule.onNodeWithText("Add session").assertDoesNotExist()
        composeTestRule.onNodeWithText("Open in Tasks").assertDoesNotExist()
        composeTestRule.onNodeWithContentDescription("More rounds").performScrollTo().performClick()
        composeTestRule.onNodeWithText("Start 25 min").performScrollTo().performClick()

        assertThat(events)
            .containsExactly(FocusContract.UiEvent.StartSession(lengthMinutes = 25, breakMinutes = 5, rounds = 2))
    }

    @Test
    fun linkedTask_opensTheTaskItself_whereverTheLinkCameFrom() {
        setSheet(onTask)
        composeTestRule.onAllNodesWithText("Read chapter 3")[1].performClick()

        assertThat(events).containsExactly(FocusContract.UiEvent.EditTask(chapter))
    }

    @Test
    fun savedSessionsLinkedTask_opensTheTaskToo() {
        setSheet(base.copy(setupSessionId = study.id))
        composeTestRule.onNodeWithText("Read chapter 3").performClick()

        assertThat(events).containsExactly(FocusContract.UiEvent.EditTask(chapter))
    }

    @Test
    fun takingTheLinkOffATask_asksForTheSheetToBecomeANewSession() {
        setSheet(onTask)

        composeTestRule.onNodeWithContentDescription("Remove the linked task").performClick()

        assertThat(events).containsExactly(FocusContract.UiEvent.UnlinkPreviewTask)
    }

    @Test
    fun linkingATaskToANewSession_showsWhatThatTaskCarries() {
        val filed =
            base.copy(
                setupSessionId = SavedSession.NEW_ID,
                todayItems = persistentListOf(FocusAgendaItem.TaskEntry(chapter, categoryName = "Reading")),
            )
        setSheet(filed)
        composeTestRule.onNodeWithText("Reading").assertDoesNotExist()

        composeTestRule.onNodeWithText("Link a task").performScrollTo().performClick()
        // The picker is a sheet of its own and takes a moment to arrive.
        composeTestRule.waitUntil(PICKER_TIMEOUT_MS) {
            composeTestRule.onAllNodesWithText("Read chapter 3").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText("Read chapter 3").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Reading").assertExists()
        composeTestRule.onNodeWithContentDescription("Remove the linked task").assertExists()
    }

    @Test
    fun linkedTasksDescription_isSignalledOnTheChipAndNeverWrittenOut() {
        val noted = chapter.copy(description = "Pages 41 to 68")
        val state =
            base.copy(
                savedSessions = persistentListOf(SavedSessionEntry(study, linkedTask = noted)),
                linkableTasks = persistentListOf(noted),
            )
        setSheet(state.copy(setupSessionId = study.id))

        composeTestRule.onNodeWithContentDescription("Has a description", useUnmergedTree = true).assertExists()
        composeTestRule.onNodeWithText("Pages 41 to 68").assertDoesNotExist()

        // The same before and after starting: nothing arrives with the timer.
        uiState = state.copy(session = FocusSession.start(study, Clock.System.now()))
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithContentDescription("Has a description", useUnmergedTree = true).assertExists()
        composeTestRule.onNodeWithText("Pages 41 to 68").assertDoesNotExist()
    }

    @Test
    fun taskWithNoDescription_showsNoSuchSign() {
        setSheet(base.copy(setupSessionId = study.id))

        composeTestRule.onNodeWithContentDescription("Has a description", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun runningSession_keepsItsTaskAndOffersNoWayToTakeItOff() {
        setSheet(base.copy(session = FocusSession.start(study, Clock.System.now())))

        composeTestRule.onNodeWithText("Read chapter 3").assertExists()
        composeTestRule.onNodeWithContentDescription("Remove the linked task").assertDoesNotExist()
    }

    @Test
    fun starting_changesTheSameSheetIntoTheRunningTimer() {
        setSheet(base.copy(setupSessionId = study.id))
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
    }
}

private const val PICKER_TIMEOUT_MS = 5_000L
