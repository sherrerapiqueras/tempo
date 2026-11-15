package com.mandrecode.tempo.features.focus.domain.repository

import com.mandrecode.tempo.features.focus.domain.model.SavedSession
import kotlinx.coroutines.flow.Flow

/** The topic sessions most recently started, most recent first, never more than a handful. */
interface SavedSessionRepository {
    fun getAll(): Flow<List<SavedSession>>

    suspend fun getById(id: Long): SavedSession?

    /**
     * Notes that [session] was just used. One of the same name takes its lengths, rounds and time;
     * otherwise it is added, and the least recently used drops off past the limit.
     *
     * @return the session as remembered, under its id.
     */
    suspend fun remember(session: SavedSession): SavedSession
}
