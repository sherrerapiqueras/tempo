package com.mandrecode.tempo.features.focus.presentation

import com.google.common.truth.Truth.assertThat
import com.mandrecode.tempo.core.ui.adaptive.SheetPlacement
import com.mandrecode.tempo.features.tasks.domain.model.Task
import org.junit.Test

/** What Focus's docked pane holds on a large window, and what closes it there (#502). */
class FocusEditorHostTest {
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
    fun givenLargeWindow_whenNothingElseIsOpen_thenTheEditorDocks() {
        assertThat(canDockFocusEditor(SheetPlacement.DockedPane, FocusContract.UiState())).isTrue()
    }

    @Test
    fun givenLargeWindow_whenPlanningIsOpen_thenTheEditorStaysASheetOverIt() {
        val planning = FocusContract.UiState(planSheet = FocusContract.PlanSheetState())

        assertThat(canDockFocusEditor(SheetPlacement.DockedPane, planning)).isFalse()
    }

    @Test
    fun givenSmallerWindow_whenChoosing_thenTheEditorIsASheet() {
        assertThat(canDockFocusEditor(SheetPlacement.BottomSheet, FocusContract.UiState())).isFalse()
    }

    @Test
    fun givenAnExistingTaskOpen_whenAskingWhichTask_thenItIsThatTask() {
        assertThat(editing.editingTaskId()).isEqualTo(7L)
    }

    @Test
    fun givenANewTaskOpen_whenAskingWhichTask_thenThereIsNone() {
        val adding = FocusContract.UiState(taskEditor = FocusContract.TaskEditorTarget.NewTask)

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
        assertThat(closesDockedEditor(FocusContract.UiEvent.AddTask, isDocked = true, editingTaskId = 7)).isFalse()
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
}
