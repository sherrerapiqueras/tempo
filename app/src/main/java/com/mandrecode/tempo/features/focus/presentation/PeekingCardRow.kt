package com.mandrecode.tempo.features.focus.presentation

import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * A row of cards you swipe through: Up next, with the running session first.
 *
 * Cards stop short of the full width when there is more than one, so the next shows at the edge —
 * the only honest way to say there is more. They start on the same line and end wherever their
 * content does: a running session is a deeper card than a queued one, and one height for all of
 * them meant padding the short ones out to match the tallest thing on screen.
 *
 * [startKey] names what must be seen at the start of the row. When it changes to something, the row
 * goes back there: a lazy row holds on to the card it was showing, and a session started from
 * anywhere but the first card arrived off-screen to the left, under a header already saying it was
 * under way.
 */
@Composable
internal fun PeekingCardRow(
    cardCount: Int,
    modifier: Modifier = Modifier,
    startKey: String? = null,
    content: LazyListScope.(cardWidth: Dp) -> Unit,
) {
    val listState = rememberLazyListState()
    LaunchedEffect(startKey) {
        if (startKey != null) listState.scrollToItem(0)
    }
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val cardWidth = if (cardCount > 1) maxWidth * SINGLE_PEEK_FRACTION else maxWidth
        LazyRow(
            state = listState,
            flingBehavior = rememberSnapFlingBehavior(listState),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.Top,
        ) {
            content(cardWidth)
        }
    }
}

/** Leaves the next card showing at the edge, so the row reads as a row. */
private const val SINGLE_PEEK_FRACTION = 0.88f
