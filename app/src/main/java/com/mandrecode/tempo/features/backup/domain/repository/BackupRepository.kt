package com.mandrecode.tempo.features.backup.domain.repository

import com.mandrecode.tempo.features.backup.domain.model.ImportMode
import com.mandrecode.tempo.features.backup.domain.model.ImportOutcome

interface BackupRepository {
    /** Serializes the full local dataset and encrypts it with a key derived from [passphrase]. */
    suspend fun exportEncrypted(passphrase: CharArray): String

    /**
     * Whether the device holds any of the user's own data: a task, habit or chain, or any category
     * but the seeded Inbox.
     */
    suspend fun hasUserData(): Boolean

    /** True when [content] is this app's encrypted backup envelope — every valid export is one. */
    fun isEncryptedBackup(content: String): Boolean

    /**
     * Applies a backup document to the local database. [passphrase] decrypts [content], which
     * must be an encrypted envelope (see [isEncryptedBackup]) — there is no unencrypted backup
     * format to fall back to, so a missing envelope or a missing/wrong passphrase both surface
     * as a failure ([ImportOutcome.CorruptFile] or [ImportOutcome.WrongPassphrase] respectively).
     * Decoding, decryption, version check and payload validation all happen before any write;
     * database mutations run in a single transaction, so any non-[ImportOutcome.Success] result
     * (or thrown error) leaves local data untouched.
     */
    suspend fun importFromJson(
        content: String,
        mode: ImportMode,
        passphrase: CharArray? = null,
    ): ImportOutcome
}
