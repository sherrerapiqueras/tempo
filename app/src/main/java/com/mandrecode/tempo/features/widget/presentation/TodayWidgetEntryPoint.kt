package com.mandrecode.tempo.features.widget.presentation

import android.content.Context
import com.mandrecode.tempo.features.focus.domain.usecase.GetFocusAgendaUseCase
import com.mandrecode.tempo.features.tasks.domain.repository.TaskRepository
import com.mandrecode.tempo.features.tasks.domain.usecase.ToggleTaskCompletionUseCase
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlin.time.Clock

/**
 * What the widget needs from the app graph. Glance creates the widget and its action callbacks
 * itself, so they can't take constructor injection; they reach the graph through this instead.
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface TodayWidgetEntryPoint {
    fun getFocusAgenda(): GetFocusAgendaUseCase

    fun taskRepository(): TaskRepository

    fun toggleTaskCompletion(): ToggleTaskCompletionUseCase

    fun clock(): Clock

    companion object {
        fun from(context: Context): TodayWidgetEntryPoint =
            EntryPointAccessors.fromApplication(context.applicationContext, TodayWidgetEntryPoint::class.java)
    }
}
