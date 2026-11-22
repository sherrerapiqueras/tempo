package com.mandrecode.tempo.features.widget.presentation

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.mandrecode.tempo.R
import com.mandrecode.tempo.core.domain.model.Priority

// The task priority as the widget shows it: a flag in the app's priority colour, and its name for
// screen readers.

@DrawableRes
internal fun Priority.flagIcon(): Int =
    when (this) {
        Priority.HIGH -> R.drawable.widget_flag_high
        Priority.MEDIUM -> R.drawable.widget_flag_medium
        Priority.LOW -> R.drawable.widget_flag_low
    }

@StringRes
internal fun Priority.label(): Int =
    when (this) {
        Priority.HIGH -> R.string.priority_high
        Priority.MEDIUM -> R.string.priority_medium
        Priority.LOW -> R.string.priority_low
    }
