package com.mandrecode.tempo.core.ui.editor

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mandrecode.tempo.core.ui.adaptive.SheetPlacement
import com.mandrecode.tempo.core.ui.adaptive.adaptiveHorizontalMargin
import com.mandrecode.tempo.core.ui.adaptive.rememberSheetPlacement

/**
 * Shared layout for the task and habit editor sheets: a scrolling form with the footer either
 * pinned below it or, when [pinFooter] is false, scrolling as the last item of the form.
 *
 * The create footer (Cancel/Add) is pinned so it stays above the keyboard and at the bottom of
 * the surface. The edit footer only holds Delete — items autosave, so nothing there needs to
 * stay reachable — and keeps scrolling with the form.
 *
 * The footer is a different slot in each branch, but only one is ever composed and the mode
 * (create vs edit) does not change while a sheet is open, so there is no state to preserve.
 */
@Suppress("ktlint:compose:content-slot-reused")
@Composable
internal fun ColumnScope.EditorSheetLayout(
    pinFooter: Boolean,
    placement: SheetPlacement?,
    footer: @Composable () -> Unit,
    body: @Composable () -> Unit,
) {
    val margin = adaptiveHorizontalMargin()
    // A docked pane has a fixed height, so the form takes all of it and the footer lands on the
    // bottom edge. A bottom sheet wraps its content: the form only takes what it needs, up to the
    // sheet's maximum height, and the footer follows it.
    val fillsHeight = (placement ?: rememberSheetPlacement()) == SheetPlacement.DockedPane
    Column(
        modifier =
            Modifier
                .weight(1f, fill = fillsHeight)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = margin)
                .padding(top = 8.dp, bottom = if (pinFooter) PINNED_FORM_BOTTOM_PADDING else 32.dp),
    ) {
        body()
        if (!pinFooter) footer()
    }
    if (pinFooter) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = margin)
                    .padding(bottom = PINNED_FOOTER_BOTTOM_PADDING),
        ) {
            footer()
        }
    }
}

// Space between the end of the form and the pinned footer; it scrolls away instead of taking
// permanent height, which matters on short windows with the keyboard open.
private val PINNED_FORM_BOTTOM_PADDING = 24.dp
private val PINNED_FOOTER_BOTTOM_PADDING = 16.dp
