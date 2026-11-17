package com.mandrecode.tempo.core.data.local

import android.content.Context
import android.database.sqlite.SQLiteException
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.collect.Range
import com.google.common.truth.Truth.assertThat
import com.mandrecode.tempo.core.data.entity.CategoryEntity
import com.mandrecode.tempo.core.data.entity.DEFAULT_INBOX_CATEGORY_ENTITY
import com.mandrecode.tempo.core.data.entity.TaskEntity
import com.mandrecode.tempo.core.domain.model.DEFAULT_INBOX_UID
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** The `uid` / `createdAt` / `updatedAt` triggers (#30), on the database configuration that ships. */
@RunWith(AndroidJUnit4::class)
class EntityIdentityTriggersTest {
    private lateinit var database: TempoDatabase
    private lateinit var db: SupportSQLiteDatabase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database =
            TempoDatabase
                .configure(
                    Room.inMemoryDatabaseBuilder(context, TempoDatabase::class.java),
                    context,
                    allowDestructiveDowngrade = false,
                ).build()
        db = database.openHelper.writableDatabase
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun everyIdentityTableHasItsTriggersAfterOpen() {
        EntityIdentitySchema.TABLES.forEach { table ->
            val names = mutableSetOf<String>()
            db.query("SELECT name FROM sqlite_master WHERE type = 'trigger' AND tbl_name = '$table'").use {
                while (it.moveToNext()) names += it.getString(0)
            }
            assertThat(names).isEqualTo(EntityIdentitySchema.triggerNames(table))
        }
    }

    @Test
    fun seededInboxHasWellKnownUidAndTimestamps() =
        runTest {
            val inbox = database.categoryDao().getCategoryById(DEFAULT_INBOX_CATEGORY_ENTITY.id)!!

            assertThat(inbox.uid).isEqualTo(DEFAULT_INBOX_UID)
            assertThat(inbox.createdAt).isGreaterThan(0L)
            assertThat(inbox.updatedAt).isEqualTo(inbox.createdAt)
        }

    @Test
    fun insertWithUnsetTimesIsStampedNow() =
        runTest {
            val before = System.currentTimeMillis()
            val id = database.categoryDao().insertCategory(CategoryEntity(name = "Work"))
            val after = System.currentTimeMillis()

            val stored = database.categoryDao().getCategoryById(id)!!
            assertThat(stored.createdAt).isIn(Range.closed(before - 1, after + 1))
            assertThat(stored.updatedAt).isEqualTo(stored.createdAt)
        }

    @Test
    fun insertWithExplicitTimesKeepsThem() =
        runTest {
            val id =
                database.categoryDao().insertCategory(
                    CategoryEntity(name = "Restored", uid = UID, createdAt = 1_000L, updatedAt = 2_000L),
                )

            val stored = database.categoryDao().getCategoryById(id)!!
            assertThat(stored.uid).isEqualTo(UID)
            assertThat(stored.createdAt).isEqualTo(1_000L)
            assertThat(stored.updatedAt).isEqualTo(2_000L)
        }

    @Test
    fun rawInsertWithoutUidGetsOne() {
        db.execSQL("INSERT INTO task_sections (categoryId, name, sortOrder) VALUES (-1, 'Raw', 0)")

        db.query("SELECT uid, createdAt FROM task_sections WHERE name = 'Raw'").use {
            it.moveToFirst()
            assertThat(it.getString(0)).matches("[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}")
            assertThat(it.getLong(1)).isGreaterThan(0L)
        }
    }

    @Test
    fun updateThatLeavesUpdatedAtAloneAdvancesItAndKeepsIdentity() =
        runTest {
            val dao = database.categoryDao()
            val id = dao.insertCategory(CategoryEntity(name = "Old", uid = UID, createdAt = 1_000L, updatedAt = 1_000L))
            val before = System.currentTimeMillis()

            dao.updateCategory(dao.getCategoryById(id)!!.copy(name = "New"))

            val stored = dao.getCategoryById(id)!!
            assertThat(stored.name).isEqualTo("New")
            assertThat(stored.uid).isEqualTo(UID)
            assertThat(stored.createdAt).isEqualTo(1_000L)
            assertThat(stored.updatedAt).isAtLeast(before - 1)
        }

    @Test
    fun updateWithExplicitUpdatedAtKeepsIt() =
        runTest {
            val dao = database.categoryDao()
            val id = dao.insertCategory(CategoryEntity(name = "Old", createdAt = 1_000L, updatedAt = 1_000L))

            dao.updateCategory(dao.getCategoryById(id)!!.copy(name = "Synced", updatedAt = 5_000L))

            assertThat(dao.getCategoryById(id)!!.updatedAt).isEqualTo(5_000L)
        }

    @Test
    fun updateFromARowWithoutMetadataKeepsIdentityAndStampsUpdatedAt() =
        runTest {
            // What a mapper that does not carry the metadata writes back: '' and 0.
            val dao = database.categoryDao()
            val id = dao.insertCategory(CategoryEntity(name = "Old", uid = UID, createdAt = 1_000L, updatedAt = 1_000L))
            val before = System.currentTimeMillis()

            dao.updateCategory(CategoryEntity(id = id, name = "Renamed"))

            val stored = dao.getCategoryById(id)!!
            assertThat(stored.name).isEqualTo("Renamed")
            assertThat(stored.uid).isEqualTo(UID)
            assertThat(stored.createdAt).isEqualTo(1_000L)
            assertThat(stored.updatedAt).isAtLeast(before - 1)
        }

    @Test
    fun overwritingAStoredUidWithAnotherFails() =
        runTest {
            val dao = database.categoryDao()
            val id = dao.insertCategory(CategoryEntity(name = "Mine", uid = UID))

            val failure =
                runCatching {
                    dao.updateCategory(dao.getCategoryById(id)!!.copy(uid = "11111111-2222-4333-8444-555555555555"))
                }.exceptionOrNull()

            assertThat(failure).isInstanceOf(SQLiteException::class.java)
            assertThat(dao.getCategoryById(id)!!.uid).isEqualTo(UID)
        }

    @Test
    fun rapidUpdatesStrictlyAdvanceUpdatedAtWithoutRecursing() =
        runTest {
            val dao = database.categoryDao()
            val id = dao.insertCategory(CategoryEntity(name = "0"))
            var last = dao.getCategoryById(id)!!.updatedAt

            repeat(20) { n ->
                dao.updateCategory(CategoryEntity(id = id, name = "$n"))
                val now = dao.getCategoryById(id)!!.updatedAt
                assertThat(now).isGreaterThan(last)
                last = now
            }
        }

    @Test
    fun bulkQueryUpdateAdvancesUpdatedAt() =
        runTest {
            val taskDao = database.taskDao()
            val id =
                taskDao.insertTask(
                    TaskEntity(title = "T", description = "", createdAt = 1_000L, updatedAt = 1_000L),
                )
            val before = System.currentTimeMillis()

            db.execSQL("UPDATE tasks SET sortOrder = sortOrder + 1")

            val stored = taskDao.getTaskById(id)!!
            assertThat(stored.createdAt).isEqualTo(1_000L)
            assertThat(stored.updatedAt).isAtLeast(before - 1)
        }

    @Test
    fun updateFromAStaleCopyStillAdvancesUpdatedAt() =
        runTest {
            // The editor read the row at 1_000; something else wrote it since (now 5_000).
            val dao = database.categoryDao()
            val id = dao.insertCategory(CategoryEntity(name = "Old", uid = UID, createdAt = 1_000L, updatedAt = 1_000L))
            val stale = dao.getCategoryById(id)!!
            dao.updateCategory(stale.copy(name = "Elsewhere", updatedAt = 5_000L))

            dao.updateCategory(stale.copy(name = "Mine"))

            val stored = dao.getCategoryById(id)!!
            assertThat(stored.name).isEqualTo("Mine")
            assertThat(stored.updatedAt).isGreaterThan(5_000L)
        }

    @Test
    fun insertWithCreatedAtButNoUpdatedAtTakesCreatedAt() =
        runTest {
            val id = database.categoryDao().insertCategory(CategoryEntity(name = "Restored", uid = UID, createdAt = 1_000L))

            assertThat(database.categoryDao().getCategoryById(id)!!.updatedAt).isEqualTo(1_000L)
        }

    @Test
    fun reopeningLeavesMatchingTriggersAlone() {
        // The open already reconciled them; doing it again has nothing to change.
        assertThat(EntityIdentitySchema.createTriggers(db)).isEqualTo(0)

        db.execSQL("DROP TRIGGER `${EntityIdentitySchema.updateTriggerName("tasks")}`")
        db.execSQL("CREATE TRIGGER `identity_obsolete_tasks` AFTER INSERT ON `tasks` BEGIN SELECT 1; END")

        assertThat(EntityIdentitySchema.createTriggers(db)).isEqualTo(2)
        assertThat(EntityIdentitySchema.createTriggers(db)).isEqualTo(0)
        db.query("SELECT COUNT(*) FROM sqlite_master WHERE name = 'identity_obsolete_tasks'").use {
            it.moveToFirst()
            assertThat(it.getInt(0)).isEqualTo(0)
        }
    }

    private companion object {
        const val UID = "01234567-89ab-7def-8123-456789abcdef"
    }
}
