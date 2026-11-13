package com.mandrecode.tempo.features.focus.domain.repository

import com.mandrecode.tempo.features.focus.domain.model.SavedSession
import kotlinx.coroutines.flow.Flow

/** The sessions the user has saved, in the order they were made. */
interface SavedSessionRepository {
    fun getAll(): Flow<List<SavedSession>>

    suspend fun getById(id: Long): SavedSession?

    /** @return the new session's id. */
    suspend fun insert(session: SavedSession): Long

    suspend fun update(session: SavedSession)

    suspend fun delete(id: Long)
}
