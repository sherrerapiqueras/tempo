package com.mandrecode.tempo.features.focus.presentation.components

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.mandrecode.tempo.R
import com.mandrecode.tempo.core.ui.components.TempoConfirmDialog
import com.mandrecode.tempo.features.focus.presentation.FocusContract

/**
 * Asks before a start ends the session already running.
 *
 * Starting replaces whatever is going and banks only the minutes it earned, so swapping tasks
 * mid-session throws the rest away. That is worth a question rather than something to notice
 * afterwards — and it only appears for a different task, since restarting the same one is just
 * carrying on.
 *
 * On [TempoConfirmDialog], like every other question this app asks, so the title, the buttons and
 * their pressed corners are the ones already met when deleting a task or discarding an edit. Not
 * destructive, though: nothing is deleted here, and painting "Start anyway" in the red kept for
 * deletions would overstate what confirming costs.
 */
@Composable
internal fun ReplaceSessionDialog(
    pending: FocusContract.PendingStart,
    onEvent: (FocusContract.UiEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    TempoConfirmDialog(
        title = stringResource(R.string.focus_replace_session_title),
        confirmLabel = stringResource(R.string.focus_replace_session_confirm),
        onConfirm = { onEvent(FocusContract.UiEvent.ConfirmPendingStart) },
        onCancel = { onEvent(FocusContract.UiEvent.DismissPendingStart) },
        modifier = modifier,
        isDestructive = false,
        text = {
            Text(
                stringResource(
                    R.string.focus_replace_session_message,
                    pending.replacingTaskTitle,
                    pending.title,
                ),
            )
        },
    )
}
