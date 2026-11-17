package com.mandrecode.tempo.features.backup.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.mandrecode.tempo.core.data.entity.CategoryEntity
import com.mandrecode.tempo.core.data.entity.TaskEntity
import com.mandrecode.tempo.core.data.local.TempoDatabase
import com.mandrecode.tempo.features.backup.data.repository.BackupRepositoryImpl
import com.mandrecode.tempo.features.backup.domain.util.BackupPayloadValidator
import com.mandrecode.tempo.features.backup.domain.util.MergePlanner
import com.mandrecode.tempo.infrastructure.security.BackupEncryptionService
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith

/** Whether a device already has the user's data, which decides merge-or-replace on restore (#307). */
@RunWith(AndroidJUnit4::class)
class HasUserDataTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val database =
        TempoDatabase.configure(Room.inMemoryDatabaseBuilder(context, TempoDatabase::class.java), context, false).build()
    private val repository =
        BackupRepositoryImpl(
            database,
            BackupPayloadValidator(),
            MergePlanner(),
            mockk(relaxed = true),
            mockk(relaxed = true),
            BackupEncryptionService(),
            context,
        )

    @After
    fun close() = database.close()

    @Test
    fun aFreshInstallWithOnlyTheSeededInboxHasNoData() =
        runTest {
            assertThat(database.categoryDao().getAllCategoriesSync()).isNotEmpty()

            assertThat(repository.hasUserData()).isFalse()
        }

    @Test
    fun aTaskIsData() =
        runTest {
            val inbox = database.categoryDao().getAllCategoriesSync().single()
            database.taskDao().insertTask(TaskEntity(title = "Mine", description = "", categoryId = inbox.id))

            assertThat(repository.hasUserData()).isTrue()
        }

    @Test
    fun aCategoryOfTheUsersOwnIsData() =
        runTest {
            database.categoryDao().insertCategory(CategoryEntity(name = "Work"))

            assertThat(repository.hasUserData()).isTrue()
        }
}
