package com.mandrecode.tempo.features.focus.presentation

import com.google.common.truth.Truth.assertThat
import com.mandrecode.tempo.core.ui.adaptive.SheetPlacement
import com.mandrecode.tempo.features.tasks.domain.model.Task
import org.junit.Test

/** Where Focus's task editor goes on a large window, and what closes it there (#502). */
class FocusEditorHostTest {
    private val openTask = Task(id = 7, title = "Reply to client emails", description = "")
    private val otherTask = Task(id = 8, title = "Book the dentist", description = "")
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
}
