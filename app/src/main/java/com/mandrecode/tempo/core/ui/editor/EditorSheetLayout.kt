package com.mandrecode.tempo.core.ui.editor

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mandrecode.tempo.core.ui.adaptive.adaptiveHorizontalMargin

/**
 * Shared layout for the task and habit editor sheets: a scrolling form with the footer either
 * pinned below it or, when [pinFooter] is false, scrolling as the last item of the form.
 *
 * The create footer (Cancel/Add) is pinned so it stays above the keyboard and at the bottom of
 * the surface. The edit footer only holds Delete — items autosave, so nothing there needs to
 * stay reachable — and keeps scrolling with the form.
 */
@Composable
internal fun ColumnScope.EditorSheetLayout(
    pinFooter: Boolean,
    footer: @Composable () -> Unit,
    body: @Composable () -> Unit,
) {
    val margin = adaptiveHorizontalMargin()
    // Switching between the pinned and scrolling slot (e.g. the habit/chain tab) must not reset
    // the footer's internal state.
    val currentFooter by rememberUpdatedState(footer)
    val movableFooter = remember { movableContentOf { currentFooter() } }
    Column(
        modifier =
            Modifier
                .weight(1f, fill = false)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = margin)
                .padding(top = 8.dp, bottom = if (pinFooter) 0.dp else 32.dp),
    ) {
        body()
        if (!pinFooter) movableFooter()
    }
    if (pinFooter) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = margin)
                    .padding(bottom = PINNED_FOOTER_BOTTOM_PADDING),
        ) {
            movableFooter()
        }
    }
}

private val PINNED_FOOTER_BOTTOM_PADDING = 24.dp
