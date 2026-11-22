package com.mandrecode.tempo.features.widget.presentation

import android.content.Context
import androidx.glance.appwidget.updateAll
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/**
 * Redraws every placed Quick Add Task widget, which has no periodic refresh of its own.
 *
 * Kept out of the view models that need it so they never touch Glance: its internals reach
 * Android framework classes as soon as they load, which a JVM unit test cannot do.
 */
class QuickAddTaskWidgetRefresher
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        suspend fun refresh() {
            QuickAddTaskWidget().updateAll(context)
        }
    }
