package com.mandrecode.tempo.core.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.mandrecode.tempo.core.data.entity.CategoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CategoryDao {
    @Query("SELECT * FROM categories ORDER BY isDefault DESC, sortOrder ASC, id ASC")
    fun getAllCategories(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories WHERE id = :id")
    suspend fun getCategoryById(id: Long): CategoryEntity?

    @Query("SELECT * FROM categories WHERE LOWER(name) = LOWER(:name)")
    suspend fun getCategoryByName(name: String): CategoryEntity?

    @Insert
    suspend fun insertCategory(category: CategoryEntity): Long

    @Update
    suspend fun updateCategory(category: CategoryEntity)

    @Update
    suspend fun updateCategories(categories: List<CategoryEntity>)

    @Query("SELECT COUNT(*) FROM categories")
    suspend fun getCategoryCount(): Int

    @Delete
    suspend fun deleteCategory(category: CategoryEntity)

    @Query("DELETE FROM categories WHERE id = :id")
    suspend fun deleteCategoryById(id: Long)

    @Query("UPDATE categories SET isDefault = 0")
    suspend fun clearAllDefaults()

    @Query("UPDATE categories SET isDefault = 1 WHERE id = :id")
    suspend fun setDefault(id: Long)

    @Transaction
    suspend fun setDefaultCategory(id: Long) {
        clearAllDefaults()
        setDefault(id)
    }

    @Query("SELECT MAX(sortOrder) FROM categories")
    suspend fun getMaxSortOrder(): Int?

    @Query("SELECT * FROM categories ORDER BY id ASC")
    suspend fun getAllCategoriesSync(): List<CategoryEntity>

    @Query("DELETE FROM categories")
    suspend fun deleteAllCategories()

    /**
     * Whether the user has any data of their own (#307): a task, habit or chain, or a category
     * other than the seeded Inbox ([inboxUid]). Existence checks only; nothing is loaded.
     */
    @Query(
        "SELECT EXISTS(SELECT 1 FROM tasks) OR EXISTS(SELECT 1 FROM habits) " +
            "OR EXISTS(SELECT 1 FROM habit_chains) OR EXISTS(SELECT 1 FROM categories WHERE uid != :inboxUid)",
    )
    suspend fun hasUserData(inboxUid: String): Boolean
}
