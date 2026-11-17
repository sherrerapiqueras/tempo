package com.mandrecode.tempo.core.data.local

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.mandrecode.tempo.core.data.entity.PendingChangeEntity.Companion.OPERATION_DELETE
import com.mandrecode.tempo.core.data.entity.PendingChangeEntity.Companion.OPERATION_UPSERT
import com.mandrecode.tempo.features.tasks.data.repository.CategoryRepositoryImpl
import com.mandrecode.tempo.features.tasks.data.repository.TaskRepositoryImpl
import com.mandrecode.tempo.features.tasks.domain.model.Category
import com.mandrecode.tempo.features.tasks.domain.model.Task
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The pending-changes journal (#546), through the real repositories and triggers. */
@RunWith(AndroidJUnit4::class)
class MutationJournalTest {
    @get:Rule
    val databaseRule = InMemoryTempoDatabaseRule()

    private val database get() = databaseRule.database
    private val journal get() = database.pendingChangeDao()
    private lateinit var tasks: TaskRepositoryImpl
    private lateinit var categories: CategoryRepositoryImpl

    @Before
    fun setUp() {
        tasks = TaskRepositoryImpl(database.taskDao(), database)
        categories =
            CategoryRepositoryImpl(database.categoryDao(), database.taskDao(), database.taskSectionDao(), database)
    }

    private suspend fun pendingFor(uid: String) = journal.getAll().filter { it.uid == uid }

    @Test
    fun creatingAndEditingARecordLeavesOneCoalescedUpsert() =
        runTest {
            val categoryId = categories.insertCategory(Category(name = "Work"))
            val id = tasks.insertTask(Task(title = "Draft", description = "", categoryId = categoryId))
            repeat(5) { n -> tasks.updateTask(tasks.getTaskById(id)!!.copy(title = "Draft $n")) }
            val uid = tasks.getTaskById(id)!!.uid

            val pending = pendingFor(uid)

            assertThat(pending).hasSize(1)
            assertThat(pending.single().operation).isEqualTo(OPERATION_UPSERT)
            assertThat(pending.single().entityType).isEqualTo("tasks")
        }

    @Test
    fun deletingTurnsTheChangeIntoADelete_andUndoBackIntoAnUpsert() =
        runTest {
            val categoryId = categories.insertCategory(Category(name = "Work"))
            val id = tasks.insertTask(Task(title = "Gone", description = "", categoryId = categoryId))
            val uid = tasks.getTaskById(id)!!.uid

            val snapshot = tasks.deleteTaskWithSnapshot(id)
            assertThat(pendingFor(uid).single().operation).isEqualTo(OPERATION_DELETE)

            tasks.restoreDeletedTasks(snapshot)
            assertThat(pendingFor(uid).single().operation).isEqualTo(OPERATION_UPSERT)
        }

    @Test
    fun deletingACategoryJournalsADeleteForItAndEachOfItsTasks() =
        runTest {
            val categoryId = categories.insertCategory(Category(name = "Work"))
            val taskUids =
                List(2) {
                    val id = tasks.insertTask(Task(title = "T$it", description = "", categoryId = categoryId))
                    tasks.getTaskById(id)!!.uid
                }
            val category = categories.getCategoryById(categoryId)!!

            categories.deleteCategoryWithSnapshot(category)

            val deletes = journal.getAll().filter { it.operation == OPERATION_DELETE }.map { it.uid }
            assertThat(deletes).containsExactlyElementsIn(taskUids + category.uid)
        }

    @Test
    fun changesAreReadInTheOrderTheRecordsLastChanged() =
        runTest {
            val categoryId = categories.insertCategory(Category(name = "Work"))
            val a = tasks.insertTask(Task(title = "A", description = "", categoryId = categoryId))
            val b = tasks.insertTask(Task(title = "B", description = "", categoryId = categoryId))
            tasks.updateTask(tasks.getTaskById(a)!!.copy(title = "A2"))

            val order = journal.getAll().filter { it.entityType == "tasks" }.map { it.uid }

            assertThat(order).containsExactly(tasks.getTaskById(b)!!.uid, tasks.getTaskById(a)!!.uid).inOrder()
        }

    @Test
    fun acknowledgingRemovesTheChange_unlessTheRecordChangedAgainSinceItWasRead() =
        runTest {
            val categoryId = categories.insertCategory(Category(name = "Work"))
            val quiet = tasks.insertTask(Task(title = "Quiet", description = "", categoryId = categoryId))
            val busy = tasks.insertTask(Task(title = "Busy", description = "", categoryId = categoryId))
            val quietUid = tasks.getTaskById(quiet)!!.uid
            val busyUid = tasks.getTaskById(busy)!!.uid
            val read = journal.getAll().associateBy { it.uid }

            // Edited while the read changes were "being uploaded".
            tasks.updateTask(tasks.getTaskById(busy)!!.copy(title = "Busy again"))

            assertThat(journal.acknowledge(quietUid, read.getValue(quietUid).seq)).isEqualTo(1)
            assertThat(journal.acknowledge(busyUid, read.getValue(busyUid).seq)).isEqualTo(0)
            assertThat(pendingFor(quietUid)).isEmpty()
            assertThat(pendingFor(busyUid)).hasSize(1)
        }

    @Test
    fun aUidSharedByTwoKindsKeepsOnePendingChangePerKind() =
        runTest {
            val db = database.openHelper.writableDatabase
            val uid = "0195a1b2-c3d4-7e5f-8a6b-0000000000bb"
            db.execSQL("INSERT INTO categories (id, name, isDefault, sortOrder, uid) VALUES (50, 'C', 0, 0, '$uid')")
            db.execSQL("INSERT INTO task_sections (id, categoryId, name, sortOrder, uid) VALUES (60, 50, 'S', 0, '$uid')")
            db.execSQL("DELETE FROM task_sections WHERE id = 60")

            val pending = pendingFor(uid).associate { it.entityType to it.operation }

            assertThat(pending).containsExactly("categories", OPERATION_UPSERT, "task_sections", OPERATION_DELETE)
        }

    @Test
    fun oneEditTakesOneSequenceNumber() =
        runTest {
            val categoryId = categories.insertCategory(Category(name = "Work"))
            val id = tasks.insertTask(Task(title = "Draft", description = "", categoryId = categoryId))
            val uid = tasks.getTaskById(id)!!.uid
            val before = pendingFor(uid).single().seq

            tasks.updateTask(tasks.getTaskById(id)!!.copy(title = "Edited"))

            // Without the guard the stamp trigger's nested update journals the edit a second time.
            assertThat(pendingFor(uid).single().seq).isEqualTo(before + 1)
        }
}
