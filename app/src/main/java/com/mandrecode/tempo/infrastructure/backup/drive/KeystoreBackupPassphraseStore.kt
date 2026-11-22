package com.mandrecode.tempo.infrastructure.backup.drive

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.core.content.edit
import com.mandrecode.tempo.features.backup.domain.repository.BackupPassphraseStore
import dagger.hilt.android.qualifiers.ApplicationContext
import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.security.GeneralSecurityException
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.io.encoding.Base64

/**
 * The backup passphrase (#305), encrypted with AES-256-GCM under a non-exportable Android Keystore
 * key, the same pattern as the database passphrase. [PREFS_FILE] and the key are excluded from
 * Auto Backup and device transfer (backup_rules.xml, data_extraction_rules.xml): the wrapped blob
 * is useless without its hardware-bound key, so a new phone gets nothing it cannot use.
 *
 * Reading never throws: a missing or invalidated key reads as "no passphrase", which a backup run
 * reports as PASSPHRASE_UNAVAILABLE rather than writing with any other key.
 */
@Singleton
class KeystoreBackupPassphraseStore
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) : BackupPassphraseStore {
        private val prefs get() = context.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE)

        /**
         * Installs the new passphrase under the alias not in use, and retires the old key only once the
         * new blob is committed. A change that fails part-way (key generation, encryption, the write)
         * leaves the old passphrase readable, so "backup wasn't changed" stays true.
         */
        @Synchronized
        override fun save(passphrase: CharArray) {
            val current = aliasInUse()
            val next = if (current == KEY_ALIAS) KEY_ALIAS_ALTERNATE else KEY_ALIAS
            val plain = encode(passphrase)
            try {
                val key = freshKey(next)
                val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, key) }
                val blob =
                    Base64.Default.encode(cipher.iv) + SEPARATOR + Base64.Default.encode(cipher.doFinal(plain))
                val saved =
                    prefs
                        .edit()
                        .putString(KEY_BLOB, blob)
                        .putString(KEY_ALIAS_IN_USE, next)
                        .commit()
                check(saved) { "Failed to persist the backup passphrase" }
            } catch (e: GeneralSecurityException) {
                discard(next)
                throw e
            } catch (e: IllegalStateException) {
                discard(next)
                throw e
            } finally {
                plain.fill(0)
            }
            discard(current)
        }

        private fun aliasInUse(): String = prefs.getString(KEY_ALIAS_IN_USE, null) ?: KEY_ALIAS

        private fun discard(alias: String) {
            runCatching { keyStore().deleteEntry(alias) }
        }

        @Synchronized
        override fun read(): CharArray? {
            val blob = prefs.getString(KEY_BLOB, null)
            // Keystore access itself can fail (an invalidated or unavailable key): that also reads
            // as "no passphrase", which a backup run reports as PASSPHRASE_UNAVAILABLE.
            val key =
                try {
                    keyStore().getKey(aliasInUse(), null) as? SecretKey
                } catch (_: GeneralSecurityException) {
                    null
                }
            return if (blob == null || key == null) null else unwrap(blob, key)
        }

        /** Null when the blob is malformed or no longer decrypts (an invalidated or replaced key). */
        private fun unwrap(
            blob: String,
            key: SecretKey,
        ): CharArray? =
            try {
                val parts = blob.split(SEPARATOR, limit = 2)
                val iv = Base64.Default.decode(parts[0])
                val cipher =
                    Cipher.getInstance(TRANSFORMATION).apply {
                        init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
                    }
                val plain = cipher.doFinal(Base64.Default.decode(parts[1]))
                try {
                    decode(plain)
                } finally {
                    plain.fill(0)
                }
            } catch (_: GeneralSecurityException) {
                null
            } catch (_: IllegalArgumentException) {
                null
            } catch (_: IndexOutOfBoundsException) {
                null
            }

        @Synchronized
        override fun clear() {
            // Synchronous: the Keystore keys are discarded next, and a blob left behind by an
            // unflushed apply() would point at a key that no longer exists.
            prefs.edit(commit = true) {
                remove(KEY_BLOB)
                remove(KEY_ALIAS_IN_USE)
            }
            discard(KEY_ALIAS)
            discard(KEY_ALIAS_ALTERNATE)
        }

        private fun keyStore(): KeyStore = KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }

        /** A new key per passphrase, so a replaced passphrase cannot be read back by any old blob. */
        private fun freshKey(alias: String): SecretKey {
            keyStore().takeIf { it.containsAlias(alias) }?.deleteEntry(alias)
            val spec =
                KeyGenParameterSpec
                    .Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(KEY_SIZE_BITS)
                    .build()
            return KeyGenerator
                .getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEY_STORE)
                .apply { init(spec) }
                .generateKey()
        }

        private fun encode(chars: CharArray): ByteArray {
            val buffer = Charsets.UTF_8.encode(CharBuffer.wrap(chars))
            return ByteArray(buffer.remaining()).also { buffer.get(it) }.also { buffer.array().fill(0) }
        }

        private fun decode(bytes: ByteArray): CharArray {
            val buffer = Charsets.UTF_8.decode(ByteBuffer.wrap(bytes))
            return CharArray(buffer.remaining()).also { buffer.get(it) }.also { buffer.array().fill('\u0000') }
        }

        companion object {
            const val PREFS_FILE = "tempo_backup_secret_prefs"
            private const val KEY_BLOB = "backup_passphrase_blob"
            private const val KEY_ALIAS_IN_USE = "backup_passphrase_key_alias"

            /** The two aliases a change alternates between; installs from before keep the first. */
            private const val KEY_ALIAS = "tempo_backup_passphrase_key"
            private const val KEY_ALIAS_ALTERNATE = "tempo_backup_passphrase_key_b"
            private const val ANDROID_KEY_STORE = "AndroidKeyStore"
            private const val TRANSFORMATION = "AES/GCM/NoPadding"
            private const val SEPARATOR = "."
            private const val GCM_TAG_LENGTH_BITS = 128
            private const val KEY_SIZE_BITS = 256
        }
    }
