package com.mandrecode.tempo.core.data.local

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.mandrecode.tempo.R
import com.mandrecode.tempo.core.data.entity.CategoryEntity
import com.mandrecode.tempo.core.data.entity.TaskEntity
import com.mandrecode.tempo.core.data.local.security.DbKdfIterMarker
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

/**
 * The default category is ensured every time the database opens, so a database that lost it, or was
 * reset by a schema downgrade, is never left without one. Every database here is built through
 * [TempoDatabase.configure], the same configuration the app uses, apart from the SQLCipher factory.
 */
@RunWith(AndroidJUnit4::class)
class DefaultCategorySeedingTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val dbName = "default_category_seeding_test_${UUID.randomUUID()}"
    private val open = mutableListOf<TempoDatabase>()

    @Before
    fun setUp() {
        context.deleteDatabase(dbName)
    }

    @After
    fun tearDown() {
        open.forEach { runCatching { it.close() } }
        context.deleteDatabase(dbName)
        // Not scoped per database; creating files here writes it, and an absent one is always safe.
        DbKdfIterMarker.clear(context)
    }

    @Test
    fun freshDatabase_seedsTheInboxAsTheDefault() =
        runTest {
            val database = build()

            val categories = database.categoryDao().getAllCategoriesSync()

            assertThat(categories).hasSize(1)
            val inbox = categories.single()
            assertThat(inbox.id).isEqualTo(INBOX_ID)
            assertThat(inbox.name).isEqualTo(context.getString(R.string.category_inbox))
            assertThat(inbox.icon).isEqualTo("inbox")
            assertThat(inbox.isDefault).isTrue()
            assertThat(inbox.sortOrder).isEqualTo(-1)
        }

    @Test
    fun reopeningADatabaseWithoutCategories_restoresTheInbox() =
        runTest {
            val first = build()
            first.categoryDao().deleteAllCategories()
            assertThat(first.categoryDao().getAllCategoriesSync()).isEmpty()
            first.close()

            val reopened = build()

            val inbox = reopened.categoryDao().getCategoryById(INBOX_ID)
            assertThat(inbox).isNotNull()
            assertThat(inbox!!.isDefault).isTrue()
            assertThat(reopened.categoryDao().getAllCategoriesSync()).hasSize(1)
        }

    @Test
    fun aRenamedInbox_isUntouchedOnReopen() =
        runTest {
            val first = build()
            val edited =
                first.categoryDao().getCategoryById(INBOX_ID)!!.copy(
                    name = "My things",
                    color = "#336699",
                    icon = "star",
                    sortOrder = 7,
                    sectionsEnabled = true,
                )
            first.categoryDao().updateCategory(edited)
            first.close()

            val reopened = build()

            assertThat(reopened.categoryDao().getAllCategoriesSync()).containsExactly(edited)
        }

    @Test
    fun anInboxThatIsNotTheDefault_isNotRewrittenWhenNoCategoryIsDefault() =
        runTest {
            val first = build()
            val notDefault = first.categoryDao().getCategoryById(INBOX_ID)!!.copy(isDefault = false)
            first.categoryDao().updateCategory(notDefault)
            first.close()

            val reopened = build()

            assertThat(reopened.categoryDao().getAllCategoriesSync()).containsExactly(notDefault)
        }

    @Test
    fun aDeletedInbox_isNotBroughtBackWhileAnotherCategoryIsTheDefault() =
        runTest {
            val first = build()
            val workId = first.categoryDao().insertCategory(CategoryEntity(name = "Work", sortOrder = 1))
            first.categoryDao().setDefaultCategory(workId)
            first.categoryDao().deleteCategoryById(INBOX_ID)
            first.close()

            val reopened = build()

            val categories = reopened.categoryDao().getAllCategoriesSync()
            assertThat(categories.map { it.id }).containsExactly(workId)
            assertThat(categories.count { it.isDefault }).isEqualTo(1)
        }

    @Test
    fun aDowngrade_inADebugBuild_resetsTheDatabaseAndStillHasTheInbox() =
        runTest {
            val first = build()
            first.categoryDao().insertCategory(CategoryEntity(name = "Work", sortOrder = 1))
            first.taskDao().insertTask(TaskEntity(title = "Lost in the reset", description = ""))
            first.close()
            markAsWrittenByANewerBuild()

            val reopened = build(allowDestructiveDowngrade = true)

            assertThat(rowCount(reopened, "tasks")).isEqualTo(0)
            val categories = reopened.categoryDao().getAllCategoriesSync()
            assertThat(categories.map { it.id }).containsExactly(INBOX_ID)
            assertThat(categories.single().isDefault).isTrue()
        }

    @Test
    fun aDowngrade_inAReleaseBuild_failsToOpenAndLeavesTheFileUntouched() =
        runTest {
            val first = build()
            first.categoryDao().insertCategory(CategoryEntity(name = "Work", sortOrder = 1))
            first.taskDao().insertTask(TaskEntity(title = "Must survive", description = ""))
            first.close()
            markAsWrittenByANewerBuild()

            val refused = build(allowDestructiveDowngrade = false)
            assertThrows(IllegalStateException::class.java) { refused.openHelper.writableDatabase }
            runCatching { refused.close() }

            SQLiteDatabase
                .openDatabase(context.getDatabasePath(dbName).path, null, SQLiteDatabase.OPEN_READONLY)
                .use { raw ->
                    assertThat(raw.version).isEqualTo(NEWER_VERSION)
                    assertThat(scalar(raw, "SELECT COUNT(*) FROM tasks")).isEqualTo(1)
                    assertThat(scalar(raw, "SELECT COUNT(*) FROM categories WHERE name = 'Work'")).isEqualTo(1)
                }
        }

    private fun build(allowDestructiveDowngrade: Boolean = false): TempoDatabase =
        TempoDatabase
            .configure(
                Room.databaseBuilder(context, TempoDatabase::class.java, dbName),
                context,
                allowDestructiveDowngrade,
            ).build()
            .also { open += it }

    /** What a newer build leaves behind: the same file, at a schema version this build has never seen. */
    private fun markAsWrittenByANewerBuild() {
        SQLiteDatabase
            .openDatabase(context.getDatabasePath(dbName).path, null, SQLiteDatabase.OPEN_READWRITE)
            .use { it.version = NEWER_VERSION }
    }

    private fun rowCount(
        database: TempoDatabase,
        table: String,
    ): Int =
        database.openHelper.readableDatabase.query("SELECT COUNT(*) FROM $table").use {
            it.moveToFirst()
            it.getInt(0)
        }

    private fun scalar(
        raw: SQLiteDatabase,
        sql: String,
    ): Int =
        raw.rawQuery(sql, null).use {
            it.moveToFirst()
            it.getInt(0)
        }

    private companion object {
        const val INBOX_ID = -1L
        const val NEWER_VERSION = 99
    }
}
