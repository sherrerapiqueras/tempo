package com.mandrecode.tempo.features.tasks.data.repository

import com.mandrecode.tempo.core.data.local.dao.TaskAttachmentDao
import com.mandrecode.tempo.core.domain.model.UNSET_TIMESTAMP
import com.mandrecode.tempo.core.domain.model.UNSET_UID
import com.mandrecode.tempo.features.tasks.data.attachment.AttachmentFileStore
import com.mandrecode.tempo.features.tasks.data.attachment.AttachmentTooLargeException
import com.mandrecode.tempo.features.tasks.data.mapper.toDomain
import com.mandrecode.tempo.features.tasks.data.mapper.toEntity
import com.mandrecode.tempo.features.tasks.domain.model.AttachmentImportResult
import com.mandrecode.tempo.features.tasks.domain.model.TaskAttachment
import com.mandrecode.tempo.features.tasks.domain.repository.TaskAttachmentRepository
import jakarta.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.io.IOException

class TaskAttachmentRepositoryImpl
    @Inject
    constructor(
        private val dao: TaskAttachmentDao,
        private val fileStore: AttachmentFileStore,
    ) : TaskAttachmentRepository {
        override fun observeAttachments(taskId: Long): Flow<List<TaskAttachment>> {
            val rows = dao.observeForTask(taskId)
            return rows.map { it.toDomain() }
        }

        override suspend fun importFile(sourceUri: String): AttachmentImportResult =
            try {
                val stored = fileStore.copyIn(sourceUri, TaskAttachment.MAX_SIZE_BYTES)
                AttachmentImportResult.Imported(
                    TaskAttachment(
                        displayName = stored.displayName,
                        mimeType = stored.mimeType,
                        sizeBytes = stored.sizeBytes,
                        storedName = stored.storedName,
                    ),
                )
            } catch (_: AttachmentTooLargeException) {
                AttachmentImportResult.TooLarge
            } catch (_: IOException) {
                AttachmentImportResult.Unreadable
            } catch (_: IllegalArgumentException) {
                AttachmentImportResult.Unreadable
            }

        override suspend fun attach(
            attachments: List<TaskAttachment>,
            taskId: Long,
        ) {
            if (attachments.isEmpty()) return
            dao.insertAll(attachments.map { it.asNew().copy(taskId = taskId).toEntity() })
        }

        override suspend fun remove(attachment: TaskAttachment) {
            dao.deleteById(attachment.id)
            if (dao.countByStoredName(attachment.storedName) == 0) {
                fileStore.deleteQuietly(attachment.storedName)
            }
        }

        override suspend fun discard(attachment: TaskAttachment) {
            if (dao.countByStoredName(attachment.storedName) == 0) {
                fileStore.deleteQuietly(attachment.storedName)
            }
        }

        override suspend fun copyAttachments(
            fromTaskId: Long,
            toTaskId: Long,
        ) {
            val source = dao.getForTask(fromTaskId)
            if (source.isEmpty()) return
            // Stored rows: a copy is a new record, so it must not keep the source's identity (#30).
            dao.insertAll(
                source.map {
                    it.copy(
                        id = 0,
                        taskId = toTaskId,
                        uid = UNSET_UID,
                        createdAt = UNSET_TIMESTAMP,
                        updatedAt = UNSET_TIMESTAMP,
                    )
                },
            )
        }

        override suspend fun pruneOrphans(keepFilesFromEpochMillis: Long) {
            // Rows are not touched here: orphaned rows are dropped when the database opens
            // (TempoDatabase.OrphanedAttachmentRowsCallback), before anything in this process could
            // delete a task. Doing it now could take the rows of a deletion still waiting on Undo.
            // Files first, rows second: a file copied in after the listing is never a candidate,
            // and one attached after it is still seen as referenced by the row query that follows.
            val candidates = fileStore.listStoredNames(modifiedBeforeEpochMillis = keepFilesFromEpochMillis)
            val referenced = dao.getAllStoredNames().toSet()
            candidates
                .filterNot { it in referenced }
                .forEach { fileStore.deleteQuietly(it) }
        }

        private suspend fun AttachmentFileStore.deleteQuietly(storedName: String) {
            try {
                delete(storedName)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // A file that will not delete now is an orphan, and pruneOrphans gets another go.
            }
        }
    }
