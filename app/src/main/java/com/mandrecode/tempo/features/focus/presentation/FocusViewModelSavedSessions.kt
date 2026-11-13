package com.mandrecode.tempo.features.focus.presentation

import android.util.Log
import androidx.lifecycle.viewModelScope
import com.mandrecode.tempo.features.focus.domain.model.FocusSession
import com.mandrecode.tempo.features.focus.domain.model.SavedSession
import com.mandrecode.tempo.features.focus.domain.model.SavedSessionEntry
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toPersistentList
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Saved sessions: making one, changing one, deleting one and starting one (#517).
 *
 * Its own file for the reason the plan sheet has one — a subject of its own, sharing a screen with
 * the day rather than belonging to it.
 */
internal fun FocusViewModel.onSessionEditorEvent(event: FocusContract.UiEvent) {
    when (event) {
        FocusContract.UiEvent.AddSession -> openSessionEditor(FocusContract.SessionEditorTarget())

        is FocusContract.UiEvent.EditSession -> {
            val entry =
                mutableUiState.value.savedSessions
                    .firstOrNull { it.session.id == event.sessionId } ?: return
            openSessionEditor(
                FocusContract.SessionEditorTarget(session = entry.session, linkedTask = entry.linkedTask),
            )
        }

        FocusContract.UiEvent.DismissSessionEditor -> closeSessionEditor()

        is FocusContract.UiEvent.SaveSession -> saveSession(event.draft, event.startNow)

        is FocusContract.UiEvent.DeleteSession ->
            viewModelScope.launch {
                // Its timer goes with it, keeping what it earned: a countdown for a session that
                // no longer exists would have nothing to go back to.
                if (mutableUiState.value.session?.savedSessionId == event.sessionId) finishSession()
                deleteSavedSession(event.sessionId)
                closeSessionEditor()
            }

        is FocusContract.UiEvent.StartSavedSession -> startSavedSession(event.sessionId)

        else -> onSessionEvent(event)
    }
}

/**
 * Saves the sheet's draft and, with [startNow], runs it.
 *
 * The sheet is closed before the write, and only an open sheet can save: a second tap landing while
 * the first is still on its way to the database would otherwise save the session twice.
 */
private fun FocusViewModel.saveSession(
    draft: FocusContract.SessionDraft,
    startNow: Boolean,
) {
    if (mutableUiState.value.sessionEditor == null) return
    closeSessionEditor()
    viewModelScope.launch {
        val saved = saveSavedSession(draft.toSavedSession())
        if (saved != null && startNow) {
            // The row follows the database a beat later, and starting reads the session from the
            // row — so what was just saved is put there now rather than waited for.
            mutableUiState.update { state ->
                val others = state.savedSessions.filterNot { it.session.id == saved.id }
                val entry =
                    state.savedSessions.firstOrNull { it.session.id == saved.id }?.copy(session = saved)
                        ?: SavedSessionEntry(session = saved)
                state.copy(savedSessions = (others + entry).sortedBy { it.session.id }.toPersistentList())
            }
            startSavedSession(saved.id)
        }
    }
}

private fun FocusViewModel.openSessionEditor(target: FocusContract.SessionEditorTarget) {
    mutableUiState.update { it.copy(sessionEditor = target) }
    linkableTasksJob?.cancel()
    linkableTasksJob =
        viewModelScope.launch {
            getLinkableTasks()
                .catch { exception ->
                    if (exception is CancellationException) throw exception
                    // The sheet still works without the picker's list; the link is optional.
                    Log.w(SAVED_SESSIONS_TAG, "Unable to load tasks to link", exception)
                }.collect { tasks ->
                    mutableUiState.update { it.copy(linkableTasks = tasks.toPersistentList()) }
                }
        }
}

private fun FocusViewModel.closeSessionEditor() {
    linkableTasksJob?.cancel()
    linkableTasksJob = null
    mutableUiState.update { it.copy(sessionEditor = null, linkableTasks = persistentListOf()) }
}

/**
 * Saved sessions belong to no day, so they are collected once rather than with the agenda.
 * A failure leaves the row empty; like the day's own query, it must not take the app down.
 */
internal fun FocusViewModel.observeSavedSessions() {
    viewModelScope.launch {
        getSavedSessions()
            .catch { exception ->
                if (exception is CancellationException) throw exception
                Log.w(SAVED_SESSIONS_TAG, "Unable to load saved sessions", exception)
            }.collect { sessions ->
                mutableUiState.update { it.copy(savedSessions = sessions.toPersistentList()) }
            }
    }
}

/**
 * Starts the work [last] was on again: the saved session from its first round if it still
 * exists, otherwise a plain session under the same name.
 */
internal suspend fun FocusViewModel.startAgain(last: FocusSession) {
    val saved = savedSession(last.savedSessionId)
    if (saved != null) {
        focusSessionUseCases.startSaved(saved)
    } else {
        focusSessionUseCases.start(last.taskId, last.title)
    }
}

/**
 * The way out of a break. Between a saved session's rounds it is the next round, started
 * now; after its last one it is the session again; on a task it is a fresh session there.
 * A break banks no minutes, so cutting one short costs nothing and nothing is asked.
 */
internal fun FocusViewModel.backToWork() {
    val session = mutableUiState.value.session?.takeIf { it.isBreak } ?: return
    if (focusSessionUseCases.skipBreak()) return
    val saved = savedSession(session.savedSessionId)
    when {
        saved != null -> startSavedSession(saved.id, force = true)
        session.taskId != null -> startSessionOn(taskId = session.taskId, lengthMinutes = null)
        else ->
            viewModelScope.launch {
                focusSessionUseCases.start(taskId = null, title = session.title)
                sendEffect(FocusContract.UiEffect.OpenSessionScreen)
            }
    }
}

internal fun FocusViewModel.savedSession(id: Long?): SavedSession? =
    mutableUiState.value.savedSessions
        .firstOrNull { it.session.id == id }
        ?.session

/**
 * Starts a saved session from its first round, asking first when that would throw away a
 * focus session running on something else — the same question [startSessionOn] asks.
 */
internal fun FocusViewModel.startSavedSession(
    sessionId: Long,
    force: Boolean = false,
) {
    val saved = savedSession(sessionId) ?: return
    val key = FocusSession.subjectKeyOf(taskId = saved.taskId, savedSessionId = saved.id)
    val replaced = mutableUiState.value.session?.takeIf { !it.isBreak && it.subjectKey != key }
    if (!force && replaced != null) {
        mutableUiState.update {
            it.copy(
                pendingStart =
                    FocusContract.PendingStart(
                        taskId = saved.taskId,
                        title = saved.title,
                        lengthMinutes = null,
                        replacingTaskTitle = replaced.title,
                        savedSessionId = saved.id,
                    ),
            )
        }
        return
    }

    viewModelScope.launch {
        focusSessionUseCases.startSaved(saved)
        focusSessionRepository.setPreviewTaskId(null)
        sendEffect(FocusContract.UiEffect.OpenSessionScreen)
    }
}

private const val SAVED_SESSIONS_TAG = "FocusSavedSessions"
