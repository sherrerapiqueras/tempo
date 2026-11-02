package com.mandrecode.tempo.core.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Generic gap scale for shared, cross-cutting UI chrome (navigation, snackbars, dialogs) — not a
 * mandate to route every screen-specific padding literal through it. Feature screens routinely
 * need content-driven dimensions (a particular card's corner radius, a chip's own height) that
 * don't map onto an abstract spacing step; those stay as local literals by design.
 */
data class Spacing(
    val extraSmall: Dp = 2.dp,
    val small: Dp = 4.dp,
    val medium: Dp = 8.dp,
    val default: Dp = 16.dp,
    val large: Dp = 24.dp,
    val extraLarge: Dp = 32.dp,
    val huge: Dp = 64.dp,
)

// CompositionLocal to provide spacing throughout the app
@Suppress("ktlint:compose:compositionlocal-allowlist")
val LocalSpacing = compositionLocalOf { Spacing() }

// Extension property to easily access spacing from MaterialTheme
val MaterialTheme.spacing: Spacing
    @Composable
    @ReadOnlyComposable
    get() = LocalSpacing.current

/**
 * Named measurements for specific UI elements (a particular card's content padding, the bottom
 * navigation bar's height) that don't map onto [Spacing]'s generic gap scale — as opposed to a
 * step in that abstract scale. Not restricted to shared components; a value belongs here when
 * it names one specific measurement, wherever it's used.
 */
object TempoSpacing {
    // Card content padding
    val cardContentPadding = 16.dp

    /** Between a card row's leading checkbox and its title: Material's `ListTokens.ItemBetweenSpace`. */
    val cardLeadingContentGap = 12.dp

    // Bottom navigation height
    val bottomNavHeight = 80.dp

    /**
     * Vertical bias for full-size centered content (loading indicators, empty states) so it sits
     * above dead-center rather than directly under the visual midpoint of typical nav chrome.
     */
    const val CENTERED_CONTENT_VERTICAL_BIAS = -0.4f
}
