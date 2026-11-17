package com.mandrecode.tempo.core.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import com.mandrecode.tempo.core.data.entity.PendingChangeEntity

/** Read and acknowledge pending changes (#546). Triggers write them; nothing in the app inserts one. */
@Dao
interface PendingChangeDao {
    /** Pending changes in the order the records last changed. */
    @Query("SELECT * FROM pending_changes ORDER BY seq ASC")
    suspend fun getAll(): List<PendingChangeEntity>

    /**
     * Marks a change as delivered. Removes it only if the record has not changed again since it was
     * read — a newer change has a new seq — so an edit made during an upload stays pending.
     * Returns whether it was removed.
     */
    @Query("DELETE FROM pending_changes WHERE uid = :uid AND seq = :seq")
    suspend fun acknowledge(
        uid: String,
        seq: Long,
    ): Int
}
