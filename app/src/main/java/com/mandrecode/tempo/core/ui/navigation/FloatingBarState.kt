package com.mandrecode.tempo.core.ui.navigation

import com.mandrecode.tempo.features.tasks.presentation.model.SortOption

data class FocusFloatingBarState(
    /** Focus adds a session, not a task (#517). */
    val onAddSession: () -> Unit = {},
)

data class RoutinesFloatingBarState(
    val visible: Boolean = true,
    val onAddHabit: () -> Unit = {},
)

data class TasksFloatingBarState(
    val visible: Boolean = true,
    val sortOption: SortOption = SortOption.MANUAL,
    val sortMenuExpanded: Boolean = false,
    val onAddTask: () -> Unit = {},
    val onSort: () -> Unit = {},
    val onSelectSortOption: (SortOption) -> Unit = {},
    val onDismissSort: () -> Unit = {},
    /** The Tasks tab was tapped while already open. */
    val onReselect: () -> Unit = {},
)
