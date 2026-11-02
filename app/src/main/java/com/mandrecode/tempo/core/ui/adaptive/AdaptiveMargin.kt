package com.mandrecode.tempo.core.ui.adaptive

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The side margin of a screen's or a sheet's content, from Material 3's window size classes: 16dp on
 * a compact window, otherwise 24dp.
 */
internal fun adaptiveHorizontalMargin(widthClass: WindowWidthClass): Dp =
    when (widthClass) {
        WindowWidthClass.COMPACT -> 16.dp
        else -> 24.dp
    }

/** [adaptiveHorizontalMargin] for the current window. */
@Composable
internal fun adaptiveHorizontalMargin(): Dp = adaptiveHorizontalMargin(currentWindowWidthClass())
