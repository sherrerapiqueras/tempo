package com.mandrecode.tempo.features.focus.presentation.components

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import com.google.common.truth.Truth.assertThat
import com.mandrecode.tempo.core.ui.theme.TempoTheme
import com.mandrecode.tempo.features.focus.domain.model.SavedSession
import com.mandrecode.tempo.features.focus.presentation.FocusContract
import com.mandrecode.tempo.features.tasks.domain.model.Task
import kotlinx.collections.immutable.persistentListOf
import org.junit.Rule
import org.junit.Test

/** The session sheet (#517): what it asks for, what it hands back, and what it refuses. */
class SessionEditorSheetTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private val chapter = Task(id = 7, title = "Read chapter 3", description = "")
    private val study =
        SavedSession(id = 3, title = "Study", focusMinutes = 50, breakMinutes = 10, rounds = 4, taskId = chapter.id)
    private val events = mutableListOf<FocusContract.UiEvent>()

    private fun setSheet(target: FocusContract.SessionEditorTarget = FocusContract.SessionEditorTarget()) {
        composeTestRule.setContent {
            TempoTheme {
                SessionEditorSheet(
                    target = target,
                    linkableTasks = persistentListOf(chapter),
                    defaultFocusMinutes = 25,
                    defaultBreakMinutes = 5,
                    onEvent = { events += it },
                )
            }
        }
    }

    /**
     * Types the name and puts the keyboard away. A new sheet opens with the name focused, and on a
     * small screen the keyboard sits over the buttons the test goes on to press.
     */
    private fun nameIt(name: String) {
        composeTestRule.onNodeWithTag(SESSION_EDITOR_TITLE_FIELD_TEST_TAG).performTextInput(name)
        composeTestRule.onNodeWithTag(SESSION_EDITOR_TITLE_FIELD_TEST_TAG).performImeAction()
        composeTestRule.waitForIdle()
    }

    @Test
    fun newSession_startsFromTheConfiguredLengthsAndCannotBeAddedWithoutAName() {
        setSheet()

        composeTestRule.onNodeWithText("25 min").assertIsDisplayed()
        composeTestRule.onNodeWithText("5 min").assertIsDisplayed()
        composeTestRule.onNodeWithText("Add session").assertIsNotEnabled()
    }

    @Test
    fun startingWithoutAName_asksForOneInsteadOfSaving() {
        setSheet()

        composeTestRule.onNodeWithText("Start 25 min").performClick()

        composeTestRule.onNodeWithText("Title is required").assertIsDisplayed()
        assertThat(events).isEmpty()
    }

    @Test
    fun namedSession_isHandedBackWithWhatWasSet() {
        setSheet()

        nameIt("Study")
        composeTestRule.onNodeWithContentDescription("More rounds").performClick()
        composeTestRule.onNodeWithContentDescription("Longer session").performClick()
        composeTestRule.onNodeWithContentDescription("Shorter rest").performClick()
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
                        title = "Study",
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
        setSheet()

        nameIt("Study")
        composeTestRule.onNodeWithText("Link a task").performClick()
        composeTestRule.onNodeWithText("Read chapter 3").performClick()
        composeTestRule.onNodeWithText("Start 25 min").performScrollTo().performClick()

        val save = events.single() as FocusContract.UiEvent.SaveSession
        assertThat(save.startNow).isTrue()
        assertThat(save.draft.taskId).isEqualTo(chapter.id)
    }

    @Test
    fun existingSession_opensOnItsValuesAndOffersDeletingAfterAsking() {
        setSheet(FocusContract.SessionEditorTarget(session = study, linkedTask = chapter))

        composeTestRule.onNodeWithTag(SESSION_EDITOR_TITLE_FIELD_TEST_TAG).assertTextContains("Study")
        composeTestRule.onNodeWithText("50 min").assertIsDisplayed()
        composeTestRule.onNodeWithText("Read chapter 3").assertIsDisplayed()
        composeTestRule.onNodeWithText("Add session").assertDoesNotExist()

        composeTestRule.onNodeWithText("Delete session").performClick()
        assertThat(events).isEmpty()
        composeTestRule.onNodeWithText("Delete this session?").assertIsDisplayed()
        composeTestRule.onNodeWithText("Delete").performClick()

        assertThat(events).containsExactly(FocusContract.UiEvent.DeleteSession(3))
    }

    @Test
    fun unlinkingTheTask_goesBackToOfferingALink() {
        setSheet(FocusContract.SessionEditorTarget(session = study, linkedTask = chapter))

        composeTestRule.onNodeWithContentDescription("Remove the linked task").performClick()

        composeTestRule.onNodeWithText("Link a task").assertIsDisplayed()
    }
}
