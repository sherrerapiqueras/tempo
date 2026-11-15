package com.mandrecode.tempo.core.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.mandrecode.tempo.core.data.entity.CategoryEntity
import com.mandrecode.tempo.core.data.entity.TaskEntity
import com.mandrecode.tempo.core.data.local.security.DbKdfIterMarker
import com.mandrecode.tempo.core.data.local.security.SqlCipherKdfIter
import kotlinx.coroutines.test.runTest
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import org.junit.After
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import java.security.SecureRandom
import net.zetetic.database.sqlcipher.SQLiteDatabase as CipherSQLiteDatabase

/**
 * Exercises the same Room + SQLCipher wiring as [TempoDatabase.getDatabase] end-to-end, using an
 * isolated database name so it never touches the shared app database other instrumented tests
 * may rely on.
 */
@RunWith(AndroidJUnit4::class)
class TempoDatabaseSqlCipherSmokeTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val dbName = "sqlcipher_smoke_test_db"
    private var database: TempoDatabase? = null

    @After
    fun tearDown() {
        database?.close()
        context.deleteDatabase(dbName)
        // Creating a file here writes this marker, which is not scoped per database.
        DbKdfIterMarker.clear(context)
    }

    @Test
    fun sqlCipherBackedRoomDatabase_opensWritesReadsAndCloses() =
        runTest {
            val passphrase = ByteArray(32).also { SecureRandom().nextBytes(it) }
            val db =
                Room
                    .databaseBuilder(context, TempoDatabase::class.java, dbName)
                    .openHelperFactory(
                        SupportOpenHelperFactory(
                            passphrase,
                            SqlCipherKdfIter.hookFor(SqlCipherKdfIter.CURRENT),
                            false,
                        ),
                    ).addMigrations(*TempoDatabase.MIGRATIONS)
                    .build()
            database = db

            val categoryId = db.categoryDao().insertCategory(CategoryEntity(name = "Smoke Test", sortOrder = 0))
            val categories = db.categoryDao().getAllCategoriesSync()

            assertThat(categories.any { it.id == categoryId }).isTrue()
            assertThat(db.isOpen).isTrue()

            db.close()
            database = null
            assertThat(db.isOpen).isFalse()
        }

    @Test
    fun aDowngradedEncryptedDatabase_inADebugBuild_isResetAndStillHasTheInbox() =
        runTest {
            val passphrase = ByteArray(32).also { SecureRandom().nextBytes(it) }
            val first = open(passphrase, allowDestructiveDowngrade = false)
            first.taskDao().insertTask(TaskEntity(title = "Lost in the reset", description = ""))
            first.openHelper.writableDatabase.version = NEWER_VERSION
            first.close()

            val reopened = open(passphrase, allowDestructiveDowngrade = true)

            val categories = reopened.categoryDao().getAllCategoriesSync()
            assertThat(categories.map { it.id }).containsExactly(-1L)
            assertThat(categories.single().isDefault).isTrue()
            assertThat(reopened.taskDao().getAllTasksSync()).isEmpty()
        }

    @Test
    fun aDowngradedEncryptedDatabase_inAReleaseBuild_failsToOpenAndKeepsItsData() =
        runTest {
            val passphrase = ByteArray(32).also { SecureRandom().nextBytes(it) }
            val first = open(passphrase, allowDestructiveDowngrade = false)
            first.taskDao().insertTask(TaskEntity(title = "Must survive", description = ""))
            first.openHelper.writableDatabase.version = NEWER_VERSION
            first.close()

            val refused = open(passphrase, allowDestructiveDowngrade = false)
            assertThrows(IllegalStateException::class.java) { refused.openHelper.writableDatabase }
            runCatching { refused.close() }
            database = null

            val raw =
                CipherSQLiteDatabase.openDatabase(
                    context.getDatabasePath(dbName).absolutePath,
                    passphrase,
                    null,
                    CipherSQLiteDatabase.OPEN_READONLY,
                    SqlCipherKdfIter.hookFor(SqlCipherKdfIter.CURRENT),
                )
            try {
                assertThat(raw.version).isEqualTo(NEWER_VERSION)
                raw.rawQuery("SELECT COUNT(*) FROM tasks", null).use {
                    it.moveToFirst()
                    assertThat(it.getInt(0)).isEqualTo(1)
                }
            } finally {
                raw.close()
            }
        }

    /** The same wiring as [TempoDatabase.getDatabase]: SQLCipher open helper plus the shared configuration. */
    private fun open(
        passphrase: ByteArray,
        allowDestructiveDowngrade: Boolean,
    ): TempoDatabase =
        TempoDatabase
            .configure(
                Room
                    .databaseBuilder(context, TempoDatabase::class.java, dbName)
                    .openHelperFactory(
                        SupportOpenHelperFactory(
                            passphrase,
                            SqlCipherKdfIter.hookFor(SqlCipherKdfIter.CURRENT),
                            false,
                        ),
                    ),
                context,
                allowDestructiveDowngrade,
            ).build()
            .also { database = it }

    private companion object {
        const val NEWER_VERSION = 99
    }
}
