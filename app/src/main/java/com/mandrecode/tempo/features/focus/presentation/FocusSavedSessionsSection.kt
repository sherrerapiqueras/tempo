package com.mandrecode.tempo.features.focus.presentation

import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mandrecode.tempo.R
import com.mandrecode.tempo.core.ui.theme.groupLabel
import com.mandrecode.tempo.features.focus.presentation.components.StartSessionButton
import com.mandrecode.tempo.features.focus.presentation.components.UpNextCard
import com.mandrecode.tempo.features.focus.presentation.components.savedSessionMetadata

/**
 * The sessions the user has saved, as a second row of the same cards (#517).
 *
 * The Up next card and not a new one: a saved session is offered for exactly the same thing — one
 * tap to start — and a second kind of card for it would have said it was a different kind of act.
 * Its own row, though, under its own label, because it is not part of the day: these are here
 * every day, whatever is due.
 */
internal fun LazyListScope.savedSessionsSection(
    uiState: FocusContract.UiState,
    onEvent: (FocusContract.UiEvent) -> Unit,
) {
    val sessions = uiState.startableSessions
    if (sessions.isEmpty()) return

    item(key = "saved_sessions_label") {
        Text(
            text = stringResource(R.string.focus_sessions_label).uppercase(),
            style = MaterialTheme.typography.groupLabel,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp).animateItem(),
        )
    }
    item(key = "saved_sessions_row") {
        val listState = rememberLazyListState()
        BoxWithConstraints(modifier = Modifier.fillMaxWidth().animateItem()) {
            val cardWidth = if (sessions.size > 1) maxWidth * SINGLE_PEEK_FRACTION else maxWidth
            LazyRow(
                state = listState,
                flingBehavior = rememberSnapFlingBehavior(listState),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.Top,
            ) {
                items(sessions, key = { "saved_session_${it.session.id}" }) { entry ->
                    UpNextCard(
                        title = entry.session.title,
                        metadata = entry.savedSessionMetadata(),
                        onClick = { onEvent(FocusContract.UiEvent.EditSession(entry.session.id)) },
                        modifier = Modifier.width(cardWidth),
                        trailingContent = {
                            StartSessionButton(
                                minutes = entry.session.focusMinutes,
                                onClick = {
                                    onEvent(FocusContract.UiEvent.StartSavedSession(entry.session.id))
                                },
                            )
                        },
                    )
                }
            }
        }
    }
}

/**
 * Brings the Up next row back to its start whenever something new starts running.
 *
 * The running card is put first, but a lazy row holds on to the card it was showing: a session
 * started from anywhere but the first card — a saved session always is — arrived off-screen to the
 * left, under a header already saying it was under way.
 */
@Composable
internal fun KeepRunningCardInView(
    subjectKey: String?,
    listState: LazyListState,
) {
    LaunchedEffect(subjectKey) {
        if (subjectKey != null) listState.scrollToItem(0)
    }
}
