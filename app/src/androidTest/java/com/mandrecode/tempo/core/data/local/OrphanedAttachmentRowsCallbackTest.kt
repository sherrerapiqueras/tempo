package com.mandrecode.tempo.core.data.local

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import com.mandrecode.tempo.core.data.entity.TaskAttachmentEntity
import com.mandrecode.tempo.core.data.entity.TaskEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Orphaned attachment rows go when the database opens, before this run of the app can have
 * deleted anything — never later, when a deletion could still be waiting on Undo (#380).
 */
@RunWith(AndroidJUnit4::class)
class OrphanedAttachmentRowsCallbackTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @After
    fun tearDown() {
        context.deleteDatabase(DB_NAME)
    }

    @Test
    fun rowsOfTasksGoneBeforeTheAppStarted_areDroppedOnOpen_andTheRestStay() {
        context.deleteDatabase(DB_NAME)
        val before =
            Room
                .databaseBuilder(context, TempoDatabase::class.java, DB_NAME)
                .addCallback(EntityIdentitySchema.TriggersCallback)
                .build()
        runBlocking {
            before.taskDao().insertTask(TaskEntity(id = 1L, title = "Kept", description = ""))
            before.taskAttachmentDao().insertAll(
                listOf(attachment(taskId = 1L, storedName = "kept"), attachment(taskId = 99L, storedName = "gone")),
            )
        }
        before.close()

        val reopened =
            Room
                .databaseBuilder(context, TempoDatabase::class.java, DB_NAME)
                .addCallback(EntityIdentitySchema.TriggersCallback)
                .addCallback(TempoDatabase.OrphanedAttachmentRowsCallback)
                .build()
        try {
            val storedNames = runBlocking { reopened.taskAttachmentDao().getAllStoredNames() }
            assertThat(storedNames).containsExactly("kept")
        } finally {
            reopened.close()
        }
    }

    private fun attachment(
        taskId: Long,
        storedName: String,
    ) = TaskAttachmentEntity(
        taskId = taskId,
        displayName = storedName,
        mimeType = null,
        sizeBytes = 1,
        storedName = storedName,
    )

    private companion object {
        const val DB_NAME = "orphaned_attachment_rows_test"
    }
}
