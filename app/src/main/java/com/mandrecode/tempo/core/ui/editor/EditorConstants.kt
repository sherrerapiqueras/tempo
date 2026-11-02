package com.mandrecode.tempo.core.ui.editor

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mandrecode.tempo.core.ui.adaptive.WindowWidthClass
import com.mandrecode.tempo.core.ui.adaptive.currentWindowWidthClass

internal const val EDITOR_MAX_TITLE_LENGTH = 65
internal const val EDITOR_AUTO_SAVE_DEBOUNCE_MS = 350L
internal val EDITOR_PROPERTY_ROW_GAP = 20.dp

/** The space between a row's icon and the edge of the sheet's content, centring it on a checkbox. */
internal val EDITOR_ICON_START_INSET = 8.dp

/**
 * A completion checkbox beside a title. Taps just outside it still land, because Compose extends
 * touch targets smaller than the 48dp minimum.
 */
internal val EDITOR_CHECKBOX_SIZE = 40.dp

/** The space after that checkbox, so a title starts where the rows' content does. */
internal val EDITOR_CHECKBOX_TITLE_GAP = 12.dp

/** The height of a row's control (chips, selectors, pills), so every row reads the same size. */
internal val EDITOR_CONTROL_HEIGHT = 48.dp

/**
 * The corner radius of an item that has been set — a reminder chip, an attached file. Squarer than
 * the 24dp pill of the Add button it replaces: in this app a smaller radius says "it's marked", as
 * on a completed card or a selected chip.
 */
internal val EDITOR_SET_ITEM_CORNER_RADIUS = 16.dp

/**
 * The empty space a borderless text field keeps below its text. A gap after one is shortened by
 * this, so the space between its text and the next row's control matches the gap between controls.
 */
internal val EDITOR_TEXT_FIELD_BOTTOM_INSET = 16.dp

/**
 * The empty space a title keeps on both sides of its text and a description keeps above its text.
 * Together they make the gap from title to description the same as the gap between controls.
 */
internal val EDITOR_TEXT_FIELD_TOP_INSET = 10.dp
internal val EDITOR_DELETE_BUTTON_CORNER_RADIUS = 24.dp

/** The side margin of an editor sheet's content: 16dp on a compact window, otherwise 24dp. */
@Composable
internal fun editorSheetHorizontalPadding(): Dp {
    val isCompact = currentWindowWidthClass() == WindowWidthClass.COMPACT
    return if (isCompact) 16.dp else 24.dp
}
