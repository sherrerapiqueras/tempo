package com.mandrecode.tempo.core.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.mandrecode.tempo.core.data.entity.TaskSectionEntity
import com.mandrecode.tempo.features.tasks.data.repository.CategoryRepositoryImpl
import com.mandrecode.tempo.features.tasks.data.repository.TaskRepositoryImpl
import com.mandrecode.tempo.features.tasks.domain.model.Category
import com.mandrecode.tempo.features.tasks.domain.model.Task
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Deletion tombstones (#281), through the real repositories and the identity triggers. */
@RunWith(AndroidJUnit4::class)
class TombstoneTest {
    @get:Rule
    val databaseRule = InMemoryTempoDatabaseRule()

    private val database get() = databaseRule.database
    private lateinit var tasks: TaskRepositoryImpl
    private lateinit var categories: CategoryRepositoryImpl

    @Before
    fun setUp() {
        tasks = TaskRepositoryImpl(database.taskDao(), database)
        categories =
            CategoryRepositoryImpl(database.categoryDao(), database.taskDao(), database.taskSectionDao(), database)
    }

    @Test
    fun deletingATaskTreeTombstonesEveryNode_andUndoClearsThem() =
        runTest {
            val categoryId = categories.insertCategory(Category(name = "Work"))
            val parent = tasks.insertTask(Task(title = "Parent", description = "", categoryId = categoryId))
            val child =
                tasks.insertTask(Task(title = "Child", description = "", categoryId = categoryId, parentTaskId = parent))
            val grandchild =
                tasks.insertTask(Task(title = "Grandchild", description = "", categoryId = categoryId, parentTaskId = child))
            val uids = listOf(parent, child, grandchild).map { tasks.getTaskById(it)!!.uid }

            val snapshot = tasks.deleteTaskWithSnapshot(parent)

            val tombstones = database.tombstoneDao().getAll()
            assertThat(tombstones.map { it.uid }).containsExactlyElementsIn(uids)
            assertThat(tombstones.map { it.entityType }.toSet()).containsExactly("tasks")
            assertThat(tasks.getTaskById(child)).isNull()

            tasks.restoreDeletedTasks(snapshot)

            assertThat(database.tombstoneDao().getAll()).isEmpty()
            assertThat(tasks.getTaskById(grandchild)!!.uid).isEqualTo(uids[2])
        }

    @Test
    fun clearingCompletedTasksTombstonesEachOne() =
        runTest {
            val categoryId = categories.insertCategory(Category(name = "Work"))
            val doneUids =
                List(3) {
                    val id =
                        tasks.insertTask(
                            Task(title = "Done $it", description = "", categoryId = categoryId, isCompleted = true),
                        )
                    tasks.getTaskById(id)!!.uid
                }
            val open = tasks.insertTask(Task(title = "Open", description = "", categoryId = categoryId))

            tasks.deleteCompletedTasksByCategoryId(categoryId)

            assertThat(database.tombstoneDao().getAll().map { it.uid }).containsExactlyElementsIn(doneUids)
            assertThat(tasks.getTaskById(open)).isNotNull()
        }

    @Test
    fun deletingACategoryTombstonesItsTasksAndSections_andUndoClearsThem() =
        runTest {
            val categoryId = categories.insertCategory(Category(name = "Work", sectionsEnabled = true))
            database.taskSectionDao().insertSection(TaskSectionEntity(categoryId = categoryId, name = "Soon"))
            tasks.insertTask(Task(title = "A", description = "", categoryId = categoryId))
            tasks.insertTask(Task(title = "B", description = "", categoryId = categoryId))
            val category = categories.getCategoryById(categoryId)!!

            val snapshot = categories.deleteCategoryWithSnapshot(category)

            val byType = database.tombstoneDao().getAll().groupBy { it.entityType }
            assertThat(byType.getValue("categories").map { it.uid }).containsExactly(category.uid)
            assertThat(byType.getValue("tasks")).hasSize(2)
            assertThat(byType.getValue("task_sections")).hasSize(1)
            assertThat(categories.getCategoryById(categoryId)).isNull()

            categories.restoreDeletedCategory(snapshot)

            assertThat(database.tombstoneDao().getAll()).isEmpty()
        }

    @Test
    fun aNewRecordLeavesOtherTombstonesAlone() =
        runTest {
            val categoryId = categories.insertCategory(Category(name = "Work"))
            val gone = tasks.insertTask(Task(title = "Gone", description = "", categoryId = categoryId))
            tasks.deleteTaskWithSnapshot(gone)

            tasks.insertTask(Task(title = "New", description = "", categoryId = categoryId))

            assertThat(database.tombstoneDao().getAll()).hasSize(1)
        }

    @Test
    fun openingPurgesTombstonesOlderThanTheWindowOnly() =
        runTest {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val name = "tombstone_purge_test"
            context.deleteDatabase(name)
            val day = 86_400_000L
            val now = System.currentTimeMillis()
            val first = TempoDatabase.configure(Room.databaseBuilder(context, TempoDatabase::class.java, name), context, false).build()
            first.openHelper.writableDatabase.execSQL(
                "INSERT INTO tombstones (uid, entityType, deletedAt) VALUES " +
                    "('old', 'tasks', ${now - (TombstoneSchema.TOMBSTONE_RETENTION_DAYS + 1) * day}), " +
                    "('young', 'tasks', ${now - 10 * day})",
            )
            first.close()

            val reopened = TempoDatabase.configure(Room.databaseBuilder(context, TempoDatabase::class.java, name), context, false).build()
            try {
                assertThat(reopened.tombstoneDao().getAll().map { it.uid }).containsExactly("young")
            } finally {
                reopened.close()
                context.deleteDatabase(name)
            }
        }
}
