package com.mandrecode.tempo.core.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.mandrecode.tempo.core.data.entity.TaskEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskDao {
    @Query("SELECT * FROM tasks ORDER BY id DESC")
    fun getAllTasks(): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE id = :id")
    suspend fun getTaskById(id: Long): TaskEntity?

    @Query("SELECT * FROM tasks WHERE id IN (:ids)")
    suspend fun getTasksByIds(ids: List<Long>): List<TaskEntity>

    @Query("SELECT * FROM tasks WHERE categoryId = :categoryId ORDER BY parentTaskId, sortOrder, id")
    suspend fun getTasksByCategoryId(categoryId: Long): List<TaskEntity>

    @Query("SELECT * FROM tasks WHERE parentTaskId = :parentId ORDER BY sortOrder ASC, id ASC")
    fun getSubtasks(parentId: Long): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE parentTaskId = :parentId ORDER BY sortOrder ASC, id ASC")
    suspend fun getSubtasksSync(parentId: Long): List<TaskEntity>

    @Query(
        """
        WITH RECURSIVE task_tree(id, depth) AS (
            SELECT id, 0 FROM tasks WHERE id IN (:rootIds)
            UNION ALL
            SELECT child.id, task_tree.depth + 1
            FROM tasks AS child
            JOIN task_tree ON child.parentTaskId = task_tree.id
        )
        SELECT tasks.*
        FROM tasks
        JOIN task_tree ON tasks.id = task_tree.id
        ORDER BY task_tree.depth, tasks.sortOrder, tasks.id
        """,
    )
    suspend fun getTaskTrees(rootIds: List<Long>): List<TaskEntity>

    @Query("SELECT MAX(sortOrder) FROM tasks WHERE parentTaskId = :parentId")
    suspend fun getMaxSubtaskSortOrder(parentId: Long): Int?

    @Query("SELECT * FROM tasks WHERE reminderDate IS NOT NULL")
    suspend fun getTasksWithReminders(): List<TaskEntity>

    @Query(
        "UPDATE tasks SET reminderDate = NULL, periodicity = NULL, " +
            "periodicityInterval = 1, repeatDays = NULL, monthDayOption = NULL, periodicityEndDate = NULL",
    )
    suspend fun clearAllReminders()

    @Query("SELECT MAX(sortOrder) FROM tasks WHERE categoryId = :categoryId AND parentTaskId IS NULL")
    suspend fun getMaxSortOrder(categoryId: Long): Int?

    @Query("UPDATE tasks SET reminderDate = :reminderDate WHERE id = :taskId")
    suspend fun updateTaskReminderDate(
        taskId: Long,
        reminderDate: String?,
    )

    @Query("UPDATE tasks SET sortOrder = :sortOrder WHERE id = :taskId")
    suspend fun updateTaskSortOrder(
        taskId: Long,
        sortOrder: Int,
    )

    @Query("UPDATE tasks SET nextInstanceId = :nextInstanceId WHERE id = :taskId")
    suspend fun updateTaskNextInstanceId(
        taskId: Long,
        nextInstanceId: Long?,
    )

    @Query("DELETE FROM tasks WHERE id = :taskId")
    suspend fun deleteTaskById(taskId: Long)

    @Insert
    suspend fun insertTask(task: TaskEntity): Long

    @Insert
    suspend fun insertTasks(tasks: List<TaskEntity>): List<Long>

    @Update
    suspend fun updateTask(task: TaskEntity)

    @Update
    suspend fun updateTasks(tasks: List<TaskEntity>)

    @Delete
    suspend fun deleteTask(task: TaskEntity)

    @Query("UPDATE tasks SET isCompleted = :isCompleted, completedAt = :completedAt WHERE id = :id")
    suspend fun updateTaskCompletion(
        id: Long,
        isCompleted: Boolean,
        completedAt: String?,
    )

    @Query("UPDATE tasks SET isCompleted = :isCompleted, completedAt = :completedAt WHERE parentTaskId = :parentId")
    suspend fun updateSubtasksCompletion(
        parentId: Long,
        isCompleted: Boolean,
        completedAt: String?,
    )

    @Query(
        "UPDATE tasks SET isCompleted = 1, completedAt = :completedAt " +
            "WHERE parentTaskId = :parentId AND isCompleted = 0",
    )
    suspend fun completeIncompleteSubtasks(
        parentId: Long,
        completedAt: String,
    )

    @Query("UPDATE tasks SET categoryId = :categoryId WHERE parentTaskId = :parentId")
    suspend fun updateSubtasksCategory(
        parentId: Long,
        categoryId: Long,
    )

    @Query("DELETE FROM tasks WHERE categoryId = :id")
    suspend fun deleteTasksByCategoryId(id: Long)

    @Query("DELETE FROM tasks WHERE parentTaskId = :parentId")
    suspend fun deleteSubtasks(parentId: Long)

    @Query("SELECT id FROM tasks WHERE isCompleted = 1 AND parentTaskId IS NULL AND categoryId = :categoryId")
    suspend fun getCompletedTopLevelTaskIds(categoryId: Long): List<Long>

    @Query(
        "SELECT id FROM tasks " +
            "WHERE isCompleted = 1 AND parentTaskId IS NULL " +
            "AND completedAt IS NOT NULL AND completedAt <= :cutoff",
    )
    suspend fun getCompletedTopLevelTaskIdsAtOrBefore(cutoff: String): List<Long>

    @Query("DELETE FROM tasks WHERE parentTaskId IN (:parentIds)")
    suspend fun deleteSubtasksByParentIds(parentIds: List<Long>)

    @Query("DELETE FROM tasks WHERE id IN (:ids)")
    suspend fun deleteTasksByIds(ids: List<Long>)

    @Query(
        """
        WITH RECURSIVE task_tree(id) AS (
            SELECT id FROM tasks WHERE id IN (:rootIds)
            UNION ALL
            SELECT child.id
            FROM tasks AS child
            JOIN task_tree ON child.parentTaskId = task_tree.id
        )
        DELETE FROM tasks WHERE id IN (SELECT id FROM task_tree)
        """,
    )
    suspend fun deleteTaskTrees(rootIds: List<Long>)

    @Transaction
    suspend fun deleteCompletedTaskTreesAtOrBefore(cutoff: String) {
        val completedParentIds = getCompletedTopLevelTaskIdsAtOrBefore(cutoff)
        if (completedParentIds.isNotEmpty()) {
            deleteTaskTrees(completedParentIds)
        }
    }

    @Query("SELECT * FROM tasks ORDER BY id ASC")
    suspend fun getAllTasksSync(): List<TaskEntity>

    @Query("DELETE FROM tasks")
    suspend fun deleteAllTasks()

    @Query("SELECT id FROM tasks WHERE sectionId = :sectionId AND parentTaskId IS NULL")
    suspend fun getTopLevelTaskIdsInSection(sectionId: Long): List<Long>

    @Query("UPDATE tasks SET sectionId = NULL WHERE sectionId = :sectionId")
    suspend fun clearSection(sectionId: Long)

    @Query(
        "UPDATE tasks SET sectionId = :sectionId " +
            "WHERE id IN (:taskIds) AND categoryId = :categoryId AND sectionId IS NULL",
    )
    suspend fun assignSection(
        taskIds: List<Long>,
        sectionId: Long,
        categoryId: Long,
    )

    @Query(
        """
        SELECT * FROM tasks
        WHERE categoryId = :categoryId AND parentTaskId IS NULL AND isCompleted = 0
            AND (
                (:sectionId IS NULL AND (
                    sectionId IS NULL
                    OR sectionId NOT IN (SELECT id FROM task_sections WHERE categoryId = tasks.categoryId)
                ))
                OR sectionId = :sectionId
            )
        ORDER BY sortOrder ASC, id ASC
        """,
    )
    suspend fun getActiveTopLevelTasksInSection(
        categoryId: Long,
        sectionId: Long?,
    ): List<TaskEntity>

    @Query("UPDATE tasks SET sectionId = :sectionId, sortOrder = :sortOrder WHERE id = :taskId")
    suspend fun updateTaskSectionAndSortOrder(
        taskId: Long,
        sectionId: Long?,
        sortOrder: Int,
    )
}
