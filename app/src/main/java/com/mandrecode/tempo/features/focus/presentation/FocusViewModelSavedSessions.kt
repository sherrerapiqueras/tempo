package com.mandrecode.tempo.features.focus.presentation

import android.util.Log
import androidx.lifecycle.viewModelScope
import com.mandrecode.tempo.features.focus.domain.model.FocusSession
import com.mandrecode.tempo.features.focus.domain.model.SavedSession
import kotlinx.collections.immutable.toPersistentList
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Topic sessions: setting a new one up, starting it, and keeping the last few to run again (#517).
 *
 * Its own file for the reason the plan sheet has one — a subject of its own, sharing a screen with
 * the day rather than belonging to it.
 */
internal fun FocusViewModel.onSessionEditorEvent(event: FocusContract.UiEvent) {
    when (event) {
        FocusContract.UiEvent.AddSession -> {
            // The same screen a task opens, and the one the timer will run in.
            focusSessionRepository.setSetupSessionId(SavedSession.NEW_ID)
            sendEffect(FocusContract.UiEffect.OpenSessionScreen)
        }

        is FocusContract.UiEvent.StartNewSession -> startNewSession(event.session)

        else -> onSessionEvent(event)
    }
}

/**
 * Starts the session the screen has set up, asking first when that would throw away a focus
 * session already running — the same question [FocusViewModel.startSessionOn] asks.
 *
 * It is remembered only here, as it starts: one the user backed out of replacing leaves no trace.
 * Only while the screen is still setting one up, and one at a time, for the second tap that lands
 * while the first is on its way to the database.
 *
 * The write is not cancellable: the timer it is followed by must not be left pointing at a session
 * that was never stored.
 */
internal fun FocusViewModel.startNewSession(
    draft: SavedSession,
    force: Boolean = false,
) {
    if (focusSessionRepository.setupSessionId.value == null || isStartingSession) return
    val replaced = mutableUiState.value.session?.takeIf { !it.isBreak }
    if (!force && replaced != null) {
        mutableUiState.update {
            it.copy(
                pendingStart =
                    FocusContract.PendingStart(
                        taskId = null,
                        title = draft.title,
                        lengthMinutes = draft.focusMinutes,
                        replacingTaskTitle = replaced.title,
                        newSession = draft,
                    ),
            )
        }
        return
    }

    isStartingSession = true
    viewModelScope.launch {
        try {
            val saved = withContext(NonCancellable) { rememberSession(draft) } ?: return@launch
            focusSessionUseCases.startSaved(saved)
            val started = focusSessionRepository.activeSession.value
            // Both at once, and before the setup is let go: the screen follows the session it
            // started only if it can see, in one look, that this timer is the one it asked for.
            mutableUiState.update { it.copy(session = started, startedNewSessionId = saved.id) }
            focusSessionRepository.setSetupSessionId(null)
            sendEffect(FocusContract.UiEffect.OpenSessionScreen)
        } finally {
            isStartingSession = false
        }
    }
}

/**
 * Recent sessions belong to no day, so they are collected once rather than with the agenda.
 * A failure leaves the list empty; like the day's own query, it must not take the app down.
 */
internal fun FocusViewModel.observeRecentSessions() {
    viewModelScope.launch {
        getSavedSessions()
            .catch { exception ->
                if (exception is CancellationException) throw exception
                Log.w(RECENT_SESSIONS_TAG, "Unable to load recent sessions", exception)
            }.collect { sessions ->
                mutableUiState.update { it.copy(recentSessions = sessions.toPersistentList()) }
            }
    }
}

/**
 * Starts the work [last] was on again: the topic from its first round if it is still among the
 * recent ones, otherwise a plain session under the same name.
 */
internal suspend fun FocusViewModel.startAgain(last: FocusSession) {
    val saved = recentSession(last.savedSessionId)
    if (saved != null) {
        runAgain(saved)
    } else {
        focusSessionUseCases.start(last.taskId, last.title)
    }
}

/**
 * The way out of a break. Between a session's rounds it is the next round, started now; after
 * a topic's last one it is the topic again; on a task it is a fresh session there.
 * A break banks no minutes, so cutting one short costs nothing and nothing is asked.
 */
internal fun FocusViewModel.backToWork() {
    val session = mutableUiState.value.session?.takeIf { it.isBreak } ?: return
    if (focusSessionUseCases.skipBreak()) return
    val saved = recentSession(session.savedSessionId)
    if (saved == null && session.taskId != null) {
        startSessionOn(FocusContract.UiEvent.StartSession(taskId = session.taskId))
    } else {
        viewModelScope.launch {
            if (saved != null) runAgain(saved) else focusSessionUseCases.start(taskId = null, title = session.title)
            sendEffect(FocusContract.UiEffect.OpenSessionScreen)
        }
    }
}

internal fun FocusViewModel.recentSession(id: Long?): SavedSession? =
    mutableUiState.value.recentSessions
        .firstOrNull { it.id == id }

/** Runs a recent topic from its first round. Used again, it moves back to the front. */
private suspend fun FocusViewModel.runAgain(saved: SavedSession) {
    focusSessionUseCases.startSaved(rememberSession(saved) ?: saved)
}

private const val RECENT_SESSIONS_TAG = "FocusRecentSessions"
