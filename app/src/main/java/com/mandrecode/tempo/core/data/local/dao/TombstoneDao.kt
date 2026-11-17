package com.mandrecode.tempo.core.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import com.mandrecode.tempo.core.data.entity.TombstoneEntity

/** Read and purge access to tombstones. Triggers write them; nothing in the app inserts one (#281). */
@Dao
interface TombstoneDao {
    @Query("SELECT * FROM tombstones ORDER BY deletedAt ASC")
    suspend fun getAll(): List<TombstoneEntity>

    @Query("SELECT * FROM tombstones WHERE uid = :uid AND entityType = :entityType")
    suspend fun get(
        uid: String,
        entityType: String,
    ): TombstoneEntity?

    @Query("DELETE FROM tombstones WHERE deletedAt < :cutoffMillis")
    suspend fun purgeOlderThan(cutoffMillis: Long): Int
}
