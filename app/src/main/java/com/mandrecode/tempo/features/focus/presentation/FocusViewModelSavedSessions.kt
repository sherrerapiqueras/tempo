package com.mandrecode.tempo.features.focus.presentation

import android.util.Log
import androidx.lifecycle.viewModelScope
import com.mandrecode.tempo.features.focus.domain.model.FocusSession
import com.mandrecode.tempo.features.focus.domain.model.SavedSession
import com.mandrecode.tempo.features.focus.domain.model.SavedSessionEntry
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toPersistentList
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Saved sessions: making one, changing one, deleting one and starting one (#517).
 *
 * Its own file for the reason the plan sheet has one — a subject of its own, sharing a screen with
 * the day rather than belonging to it.
 */
internal fun FocusViewModel.onSessionEditorEvent(event: FocusContract.UiEvent) {
    when (event) {
        FocusContract.UiEvent.AddSession -> openSessionSetup(SavedSession.NEW_ID)

        is FocusContract.UiEvent.EditSession ->
            if (savedSession(event.sessionId) != null) openSessionSetup(event.sessionId)

        // The screen stays where it is; what it is about becomes a session of its own.
        FocusContract.UiEvent.UnlinkPreviewTask -> focusSessionRepository.setSetupSessionId(SavedSession.NEW_ID)

        is FocusContract.UiEvent.SaveSession -> saveSession(event.draft, event.startNow)

        is FocusContract.UiEvent.DeleteSession ->
            viewModelScope.launch {
                // Its timer goes with it, keeping what it earned: a countdown for a session that
                // no longer exists would have nothing to go back to.
                if (mutableUiState.value.session?.savedSessionId == event.sessionId) finishSession()
                deleteSavedSession(event.sessionId)
                focusSessionRepository.setSetupSessionId(null)
            }

        is FocusContract.UiEvent.StartSavedSession -> startSavedSession(event.sessionId)

        else -> onSessionEvent(event)
    }
}

/**
 * Opens the session screen on a saved session that is not running: a new one, or one to change or
 * start. The same screen a task opens, and the one its timer will run in.
 */
private fun FocusViewModel.openSessionSetup(sessionId: Long) {
    focusSessionRepository.setSetupSessionId(sessionId)
    sendEffect(FocusContract.UiEffect.OpenSessionScreen)
}

/**
 * Saves the screen's draft and, with [startNow], runs it.
 *
 * Only what is being set up can be saved, and under the id it is being set up as. A new session
 * takes its real id the moment it is first written, a beat before the screen holding the draft
 * catches up — so a second tap in that beat still carries "new", and is answered by updating the
 * session just made rather than making another. One write at a time besides, for the tap that
 * lands while the first is still on its way to the database.
 *
 * The write itself is not cancellable: closing the screen is one of the things that saves, and it
 * clears this view model in the same breath.
 */
private fun FocusViewModel.saveSession(
    draft: FocusContract.SessionDraft,
    startNow: Boolean,
) {
    val settingUp = focusSessionRepository.setupSessionId.value ?: return
    if (isSavingSession) return
    isSavingSession = true
    val session = draft.toSavedSession().let { if (it.id == SavedSession.NEW_ID) it.copy(id = settingUp) else it }
    viewModelScope.launch {
        try {
            val saved = withContext(NonCancellable) { saveSavedSession(session) }
            if (saved != null && startNow) {
                showSaved(saved)
                if (settingUp == SavedSession.NEW_ID) {
                    mutableUiState.update { it.copy(startedNewSessionId = saved.id) }
                }
                // Under its real id from here on: a new session was being set up under a stand-in,
                // and the screen follows it across to the one its timer will carry.
                focusSessionRepository.setSetupSessionId(saved.id)
                startSavedSession(saved.id)
            } else {
                focusSessionRepository.setSetupSessionId(null)
            }
        } finally {
            isSavingSession = false
        }
    }
}

/**
 * Puts a session just saved in the row now. The row follows the database a beat later, and
 * starting reads the session from the row.
 */
private fun FocusViewModel.showSaved(saved: SavedSession) {
    mutableUiState.update { state ->
        val others = state.savedSessions.filterNot { it.session.id == saved.id }
        val entry =
            state.savedSessions.firstOrNull { it.session.id == saved.id }?.copy(session = saved)
                ?: SavedSessionEntry(session = saved)
        state.copy(savedSessions = (others + entry).sortedBy { it.session.id }.toPersistentList())
    }
}

/**
 * The session being set up has changed. While there is one, the tasks it could be linked to are
 * kept loaded; with none, they are let go.
 */
internal fun FocusViewModel.onSetupSessionChanged(sessionId: Long?) {
    mutableUiState.update { it.copy(setupSessionId = sessionId) }
    if (sessionId == null) {
        linkableTasksJob?.cancel()
        linkableTasksJob = null
        mutableUiState.update { it.copy(linkableTasks = persistentListOf()) }
    } else if (linkableTasksJob == null) {
        linkableTasksJob =
            viewModelScope.launch {
                getLinkableTasks()
                    .catch { exception ->
                        if (exception is CancellationException) throw exception
                        // The screen still works without the picker's list; the link is optional.
                        Log.w(SAVED_SESSIONS_TAG, "Unable to load tasks to link", exception)
                    }.collect { tasks ->
                        mutableUiState.update { it.copy(linkableTasks = tasks.toPersistentList()) }
                    }
            }
    }
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
        session.taskId != null ->
            startSessionOn(FocusContract.UiEvent.StartSession(taskId = session.taskId))
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
