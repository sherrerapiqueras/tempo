package com.mandrecode.tempo.core.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.mandrecode.tempo.core.data.entity.SavedSessionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SavedSessionDao {
    /** In the order they were made. */
    @Query("SELECT * FROM focus_sessions ORDER BY id ASC")
    fun getAll(): Flow<List<SavedSessionEntity>>

    @Query("SELECT * FROM focus_sessions ORDER BY id ASC")
    suspend fun getAllSync(): List<SavedSessionEntity>

    @Query("SELECT * FROM focus_sessions WHERE id = :id")
    suspend fun getById(id: Long): SavedSessionEntity?

    @Insert
    suspend fun insert(session: SavedSessionEntity): Long

    @Update
    suspend fun update(session: SavedSessionEntity)

    @Query("DELETE FROM focus_sessions WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM focus_sessions")
    suspend fun deleteAll()
}
