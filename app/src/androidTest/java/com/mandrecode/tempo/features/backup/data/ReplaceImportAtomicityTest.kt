package com.mandrecode.tempo.features.backup.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.mandrecode.tempo.core.data.entity.TaskEntity
import com.mandrecode.tempo.core.data.local.TempoDatabase
import com.mandrecode.tempo.core.domain.model.ThemeMode
import com.mandrecode.tempo.core.domain.repository.DailyFocusActivityRepository
import com.mandrecode.tempo.features.backup.data.repository.BackupRepositoryImpl
import com.mandrecode.tempo.features.backup.domain.model.BackupSettings
import com.mandrecode.tempo.features.backup.domain.model.ImportMode
import com.mandrecode.tempo.features.backup.domain.util.BackupPayloadValidator
import com.mandrecode.tempo.features.backup.domain.util.MergePlanner
import com.mandrecode.tempo.infrastructure.security.BackupEncryptionService
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith

/** A replace import is all or nothing, daily focus history included, on a real Room database. */
@RunWith(AndroidJUnit4::class)
class ReplaceImportAtomicityTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val database =
        TempoDatabase.configure(Room.inMemoryDatabaseBuilder(context, TempoDatabase::class.java), context, false).build()

    // A real snapshot: a relaxed mock's settings don't serialise, and the export would be unreadable.
    private val settings =
        mockk<BackupSettingsDataSource>(relaxed = true) {
            coEvery { snapshot() } returns
                BackupSettings(
                    themeMode = ThemeMode.SYSTEM,
                    useTempoColors = true,
                    autoRemoveCompletedTasks = false,
                    completedTaskRetentionDays = 30,
                )
        }

    private fun repository(history: DailyFocusActivityRepository = mockk(relaxed = true)) =
        BackupRepositoryImpl(
            database,
            BackupPayloadValidator(),
            MergePlanner(),
            settings,
            history,
            BackupEncryptionService(),
            context,
        )

    @After
    fun close() = database.close()

    @Test
    fun aFailedHistoryWriteRollsBackTheWholeReplace() =
        runTest {
            val inbox = database.categoryDao().getAllCategoriesSync().single()
            database.taskDao().insertTask(TaskEntity(title = "Backed up", description = "", categoryId = inbox.id))
            val backup = repository().exportEncrypted(PASSPHRASE.toCharArray())
            database.taskDao().getAllTasksSync().forEach { database.taskDao().deleteTask(it) }
            database.taskDao().insertTask(TaskEntity(title = "Since then", description = "", categoryId = inbox.id))
            val failingHistory =
                mockk<DailyFocusActivityRepository>(relaxed = true) {
                    coEvery { replaceAll(any()) } throws IllegalStateException("disk full")
                }

            val result =
                runCatching { repository(failingHistory).importFromJson(backup, ImportMode.REPLACE, PASSPHRASE.toCharArray()) }

            assertThat(result.exceptionOrNull()).hasMessageThat().isEqualTo("disk full")
            assertThat(database.taskDao().getAllTasksSync().map { it.title }).containsExactly("Since then")
        }

    private companion object {
        const val PASSPHRASE = "correct horse battery staple"
    }
}
