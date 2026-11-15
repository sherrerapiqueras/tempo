package com.mandrecode.tempo.core.ui.editor

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Dp
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
    val density = LocalDensity.current
    val imeVisible = WindowInsets.ime.getBottom(density) > 0
    // In a short window (landscape phone with the keyboard up) a pinned footer would take what
    // is left and hide the form, including the title being typed, so it scrolls with the form.
    val pinned = pinFooter && rememberVisibleWindowHeight(imeVisible) >= MIN_HEIGHT_TO_PIN_FOOTER
    val scrollState = rememberScrollState()
    val fade by animateFloatAsState(
        targetValue = if (pinned && scrollState.canScrollForward) 1f else 0f,
        label = "editor_form_bottom_fade",
    )
    Column(
        modifier =
            Modifier
                .weight(1f, fill = fillsHeight)
                .fillMaxWidth()
                // The offscreen layer the fade needs is only paid for while something is faded.
                .then(if (fade > 0f) Modifier.fadeBottomEdge { fade } else Modifier)
                .verticalScroll(scrollState)
                .padding(horizontal = margin)
                .padding(top = 8.dp, bottom = if (pinned) 0.dp else 32.dp),
    ) {
        body()
        if (!pinned) footer()
    }
    if (pinned) {
        // Hugs the keyboard when it is up, and gets the roomier resting spacing when it is not,
        // so the buttons read as part of the sheet's bottom edge in both states.
        val footerTopPadding by animateDpAsState(
            targetValue = if (imeVisible) PINNED_FOOTER_PADDING_KEYBOARD else PINNED_FOOTER_TOP_PADDING,
            label = "editor_footer_top_padding",
        )
        val footerBottomPadding by animateDpAsState(
            targetValue = if (imeVisible) PINNED_FOOTER_PADDING_KEYBOARD else PINNED_FOOTER_BOTTOM_PADDING,
            label = "editor_footer_bottom_padding",
        )
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = margin)
                    .padding(top = footerTopPadding, bottom = footerBottomPadding),
        ) {
            footer()
        }
    }
}

/**
 * Fades the last [FORM_FADE_HEIGHT] of the content to transparent by [fraction] (0..1), so a form
 * that continues behind the pinned footer ends in a soft edge instead of a cut-off row. Alpha, not
 * a painted colour, because the sheet surface is translucent.
 */
private fun Modifier.fadeBottomEdge(fraction: () -> Float): Modifier =
    graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        .drawWithContent {
            drawContent()
            val amount = fraction()
            if (amount > 0f) {
                val height = FORM_FADE_HEIGHT.toPx().coerceAtMost(size.height)
                drawRect(
                    brush =
                        Brush.verticalGradient(
                            colors = listOf(Color.Black, Color.Black.copy(alpha = 1f - amount)),
                            startY = size.height - height,
                            endY = size.height,
                        ),
                    topLeft = Offset(0f, size.height - height),
                    size = Size(size.width, height),
                    blendMode = BlendMode.DstIn,
                )
            }
        }

/** The window's height minus the keyboard, i.e. the room the sheet actually has. */
@Composable
private fun rememberVisibleWindowHeight(imeVisible: Boolean): Dp {
    val density = LocalDensity.current
    val windowHeight =
        with(density) {
            LocalWindowInfo.current.containerSize.height
                .toDp()
        }
    val imeHeight = if (imeVisible) with(density) { WindowInsets.ime.getBottom(this).toDp() } else 0.dp
    return windowHeight - imeHeight
}

/** Below this visible window height a pinned footer would leave too little room for the form. */
private val MIN_HEIGHT_TO_PIN_FOOTER = 320.dp
private val FORM_FADE_HEIGHT = 24.dp
private val PINNED_FOOTER_TOP_PADDING = 16.dp
private val PINNED_FOOTER_BOTTOM_PADDING = 16.dp
private val PINNED_FOOTER_PADDING_KEYBOARD = 8.dp
