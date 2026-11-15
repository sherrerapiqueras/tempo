package com.mandrecode.tempo.core.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.mandrecode.tempo.core.data.entity.SavedSessionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SavedSessionDao {
    /** Most recently used first. */
    @Query("SELECT * FROM focus_sessions ORDER BY lastUsedAt DESC, id DESC")
    fun getAll(): Flow<List<SavedSessionEntity>>

    @Query("SELECT * FROM focus_sessions ORDER BY lastUsedAt DESC, id DESC")
    suspend fun getAllSync(): List<SavedSessionEntity>

    @Query("SELECT * FROM focus_sessions WHERE id = :id")
    suspend fun getById(id: Long): SavedSessionEntity?

    /** The session called [title], whatever its capitals: "study" is "Study". */
    @Query("SELECT * FROM focus_sessions WHERE title = :title COLLATE NOCASE LIMIT 1")
    suspend fun findByTitle(title: String): SavedSessionEntity?

    @Insert
    suspend fun insert(session: SavedSessionEntity): Long

    @Update
    suspend fun update(session: SavedSessionEntity)

    /** Forgets every session past the [keep] most recently used. */
    @Query(
        "DELETE FROM focus_sessions WHERE id NOT IN " +
            "(SELECT id FROM focus_sessions ORDER BY lastUsedAt DESC, id DESC LIMIT :keep)",
    )
    suspend fun trimTo(keep: Int)

    @Query("DELETE FROM focus_sessions")
    suspend fun deleteAll()

    /**
     * Notes that [session] was just used: the one of the same name takes its lengths, rounds and
     * time, or it is added — and then only the [keep] most recent are kept.
     *
     * One transaction, so the list is never seen a session too long, and two starts at once cannot
     * both find the name missing.
     *
     * @return the id the session is remembered under.
     */
    @Transaction
    suspend fun remember(
        session: SavedSessionEntity,
        keep: Int,
    ): Long {
        val existing = findByTitle(session.title)
        val id =
            if (existing != null) {
                update(session.copy(id = existing.id))
                existing.id
            } else {
                insert(session.copy(id = 0))
            }
        trimTo(keep)
        return id
    }
}
