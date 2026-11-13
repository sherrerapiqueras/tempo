package com.mandrecode.tempo.features.focus.presentation

import com.google.common.truth.Truth.assertThat
import com.mandrecode.tempo.core.ui.adaptive.SheetPlacement
import com.mandrecode.tempo.features.tasks.domain.model.Task
import org.junit.Test

/** What Focus's docked pane holds on a large window, and what closes it there (#502). */
class FocusPaneHostTest {
    private val openTask = Task(id = 7, title = "Reply to client emails", description = "")
    private val otherTask = Task(id = 8, title = "Book the dentist", description = "")

    private fun yields(
        dock: Boolean,
        was: Boolean,
        open: Boolean,
        session: Boolean,
    ) = sessionYieldsToTask(canDock = dock, wasEditorOpen = was, isEditorOpen = open, isSessionOpen = session)

    private fun pane(
        dock: Boolean,
        editor: Boolean,
        session: Boolean,
    ) = focusPane(canDock = dock, isEditorOpen = editor, isSessionOpen = session)

    private val editing = FocusContract.UiState(taskEditor = FocusContract.TaskEditorTarget.Existing(openTask))

    @Test
    fun givenLargeWindow_whenNothingElseIsOpen_thenThePaneIsAvailable() {
        assertThat(canDockFocusPane(SheetPlacement.DockedPane, isEditorOverPlanSheet = false)).isTrue()
    }

    @Test
    fun givenLargeWindow_whenATaskOpensFromPlanning_thenItStaysASheetOverIt() {
        assertThat(canDockFocusPane(SheetPlacement.DockedPane, isEditorOverPlanSheet = true)).isFalse()
    }

    @Test
    fun givenSmallerWindow_whenChoosing_thenThereIsNoPane() {
        assertThat(canDockFocusPane(SheetPlacement.BottomSheet, isEditorOverPlanSheet = false)).isFalse()
    }

    @Test
    fun givenASheet_whenAnotherTaskBecomesTheSubject_thenItStaysOnTheFirst() {
        assertThat(nextSessionSubject(isDocked = false, subject = 1L, current = 2L)).isEqualTo(1L)
        assertThat(nextSessionSubject(isDocked = false, subject = null, current = 2L)).isEqualTo(2L)
    }

    @Test
    fun givenDockedSession_whenAnotherTaskIsChosen_thenThePaneFollowsIt() {
        assertThat(nextSessionSubject(isDocked = true, subject = 1L, current = 2L)).isEqualTo(2L)
    }

    @Test
    fun givenASavedSessionAsSubject_whenATaskIsChosen_thenTheKeysAreToldApart() {
        assertThat(nextSessionSubject(isDocked = true, subject = "session:1", current = "task:1"))
            .isEqualTo("task:1")
        assertThat(nextSessionSubject(isDocked = false, subject = "session:1", current = "task:1"))
            .isEqualTo("session:1")
    }

    @Test
    fun givenDockedSession_whenTheSessionEnds_thenItKeepsTheSubjectSoItCanClose() {
        assertThat(nextSessionSubject(isDocked = true, subject = 1L, current = null)).isEqualTo(1L)
    }

    @Test
    fun givenAnExistingTaskOpen_whenAskingWhichTask_thenItIsThatTask() {
        assertThat(editing.editingTaskId()).isEqualTo(7L)
    }

    @Test
    fun givenANewSubtaskOpen_whenAskingWhichTask_thenThereIsNone() {
        val adding = FocusContract.UiState(taskEditor = FocusContract.TaskEditorTarget.NewSubtask(parentTaskId = 7))

        assertThat(adding.editingTaskId()).isNull()
        assertThat(FocusContract.UiState().editingTaskId()).isNull()
    }

    @Test
    fun givenDockedTask_whenItsCardIsTappedAgain_thenTheEditorCloses() {
        assertThat(closesDockedEditor(FocusContract.UiEvent.EditTask(openTask), isDocked = true, editingTaskId = 7))
            .isTrue()
    }

    @Test
    fun givenDockedTask_whenAnotherCardIsTapped_thenItOpensThatTaskInstead() {
        assertThat(closesDockedEditor(FocusContract.UiEvent.EditTask(otherTask), isDocked = true, editingTaskId = 7))
            .isFalse()
    }

    @Test
    fun givenASheet_whenTheSameCardIsTapped_thenItIsAnOrdinaryEdit() {
        assertThat(closesDockedEditor(FocusContract.UiEvent.EditTask(openTask), isDocked = false, editingTaskId = 7))
            .isFalse()
    }

    @Test
    fun givenDockedTask_whenSomethingElseHappens_thenTheEditorStaysOpen() {
        assertThat(closesDockedEditor(FocusContract.UiEvent.AddSession, isDocked = true, editingTaskId = 7)).isFalse()
    }

    @Test
    fun givenLargeWindow_whenOnlyTheSessionIsOpen_thenThePaneShowsIt() {
        assertThat(pane(dock = true, editor = false, session = true)).isEqualTo(FocusPane.SESSION)
    }

    @Test
    fun givenLargeWindow_whenATaskAndTheSessionAreBothOpen_thenThePaneShowsTheTask() {
        assertThat(pane(dock = true, editor = true, session = true)).isEqualTo(FocusPane.EDITOR)
    }

    @Test
    fun givenLargeWindow_whenNothingIsOpen_thenThereIsNoPane() {
        assertThat(pane(dock = true, editor = false, session = false)).isEqualTo(FocusPane.NONE)
    }

    @Test
    fun givenSmallerWindow_whenEitherIsOpen_thenThereIsNoPane() {
        assertThat(pane(dock = false, editor = true, session = true)).isEqualTo(FocusPane.NONE)
    }

    @Test
    fun givenSessionDocked_whenATaskOpens_thenTheSessionGivesWay() {
        assertThat(yields(dock = true, was = false, open = true, session = true))
            .isTrue()
    }

    @Test
    fun givenBothFoundOpenOnReturn_whenFocusIsComposed_thenTheSessionStays() {
        assertThat(yields(dock = true, was = true, open = true, session = true))
            .isFalse()
    }

    @Test
    fun givenNoSession_whenATaskOpens_thenThereIsNothingToClose() {
        assertThat(yields(dock = true, was = false, open = true, session = false))
            .isFalse()
    }

    @Test
    fun givenSmallerWindow_whenATaskOpensOverTheSession_thenTheSessionSheetStays() {
        assertThat(yields(dock = false, was = false, open = true, session = true))
            .isFalse()
    }

    @Test
    fun givenTaskDocked_whenTheSessionOpens_thenTheTaskGivesWay() {
        assertThat(taskYieldsToSession(canDock = true, isEditorOpen = true, isSessionOpen = true)).isTrue()
        assertThat(taskYieldsToSession(canDock = true, isEditorOpen = false, isSessionOpen = true)).isFalse()
        assertThat(taskYieldsToSession(canDock = true, isEditorOpen = true, isSessionOpen = false)).isFalse()
        assertThat(taskYieldsToSession(canDock = false, isEditorOpen = true, isSessionOpen = true)).isFalse()
    }

    @Test
    fun givenANewSessionJustSaved_whenItIsStillBeingSetUp_thenTheLatchIsLetGoForItsRealKey() {
        assertThat(latchedSessionSubject("session:0", setupSessionId = 12)).isNull()
    }

    @Test
    fun givenANewSession_whenNothingIsBeingSetUpAnyMore_thenTheLatchIsKeptSoTheSurfaceCloses() {
        assertThat(latchedSessionSubject("session:0", setupSessionId = null)).isEqualTo("session:0")
        assertThat(latchedSessionSubject("session:0", setupSessionId = 0)).isEqualTo("session:0")
    }

    @Test
    fun givenAnythingElseLatched_whenASessionIsBeingSetUp_thenTheLatchIsUntouched() {
        assertThat(latchedSessionSubject("session:4", setupSessionId = 12)).isEqualTo("session:4")
        assertThat(latchedSessionSubject("task:1", setupSessionId = 12)).isEqualTo("task:1")
        assertThat(latchedSessionSubject(null, setupSessionId = 12)).isNull()
    }

    @Test
    fun givenASurfaceOpenedOnATask_whenItsLinkIsTakenOff_thenItBecomesTheNewSessionInPlace() {
        assertThat(latchedSessionSubject("task:1", setupSessionId = 0)).isNull()
        assertThat(latchedSessionSubject("session:4", setupSessionId = 0)).isNull()
    }
}
