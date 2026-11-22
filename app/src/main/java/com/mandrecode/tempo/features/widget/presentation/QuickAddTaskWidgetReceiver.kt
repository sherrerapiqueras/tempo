package com.mandrecode.tempo.features.widget.presentation

import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver

/**
 * The Today widget's receiver. It keeps the class name it had when the widget was "Quick Add
 * Task": the launcher stores placed widgets by the receiver's component name, so renaming it would
 * remove every widget users have already placed instead of upgrading it.
 */
class QuickAddTaskWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TodayWidget()
}
