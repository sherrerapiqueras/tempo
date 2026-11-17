package com.mandrecode.tempo.features.tasks.domain.model

import com.mandrecode.tempo.core.domain.model.UNSET_TIMESTAMP
import com.mandrecode.tempo.core.domain.model.UNSET_UID

/**
 * A file attached to a task: a picture, a document, anything the user picked.
 *
 * The file itself is a private copy the app keeps under [storedName], so it stays readable after
 * the original is moved or deleted and needs no lasting permission on someone else's storage.
 * An attachment that is still only in the editor of a task not yet saved has [id] and [taskId]
 * of `0`; saving the task is what gives it a row.
 */
data class TaskAttachment(
    val id: Long = 0,
    val taskId: Long = 0,
    val displayName: String,
    val mimeType: String?,
    val sizeBytes: Long,
    val storedName: String,
    /** Stable global identity (#30), assigned by the database on insert; [UNSET_UID] until stored. */
    val uid: String = UNSET_UID,
    /** Epoch millis UTC, stamped by the database; [UNSET_TIMESTAMP] until stored. */
    val createdAt: Long = UNSET_TIMESTAMP,
    /** Epoch millis UTC of the last stored change, stamped by the database. */
    val updatedAt: Long = UNSET_TIMESTAMP,
) {
    /**
     * This record saved as a *new* one: no local id and no identity, so the database assigns both.
     * Any copy that becomes another row must go through here — reusing the source's uid fails (#30).
     */
    fun asNew(): TaskAttachment =
        copy(
            id = 0,
            uid = UNSET_UID,
            createdAt = UNSET_TIMESTAMP,
            updatedAt = UNSET_TIMESTAMP,
        )

    val kind: Kind
        get() =
            when {
                mimeType == null -> Kind.FILE
                mimeType.startsWith("image/") -> Kind.IMAGE
                mimeType == "application/pdf" -> Kind.PDF
                else -> Kind.FILE
            }

    val isSaved: Boolean get() = id != 0L

    enum class Kind { IMAGE, PDF, FILE }

    companion object {
        /** Files above this are refused rather than copied: the copy lives on the user's device. */
        const val MAX_SIZE_BYTES: Long = 25L * 1024 * 1024
    }
}

/** What happened to one picked file. */
sealed interface AttachmentImportResult {
    data class Imported(
        val attachment: TaskAttachment,
    ) : AttachmentImportResult

    data object TooLarge : AttachmentImportResult

    data object Unreadable : AttachmentImportResult
}
