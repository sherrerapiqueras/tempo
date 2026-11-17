package com.mandrecode.tempo.infrastructure.backup.drive

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
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

        @Synchronized
        override fun save(passphrase: CharArray) {
            val key = freshKey()
            val plain = encode(passphrase)
            try {
                val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, key) }
                val blob =
                    Base64.Default.encode(cipher.iv) + SEPARATOR + Base64.Default.encode(cipher.doFinal(plain))
                check(prefs.edit().putString(KEY_BLOB, blob).commit()) { "Failed to persist the backup passphrase" }
            } finally {
                plain.fill(0)
            }
        }

        @Synchronized
        override fun read(): CharArray? {
            val blob = prefs.getString(KEY_BLOB, null)
            val key = keyStore().getKey(KEY_ALIAS, null) as? SecretKey
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
            prefs.edit().remove(KEY_BLOB).commit()
            runCatching { keyStore().deleteEntry(KEY_ALIAS) }
        }

        private fun keyStore(): KeyStore = KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }

        /** A new key per passphrase, so a replaced passphrase cannot be read back by any old blob. */
        private fun freshKey(): SecretKey {
            keyStore().takeIf { it.containsAlias(KEY_ALIAS) }?.deleteEntry(KEY_ALIAS)
            val spec =
                KeyGenParameterSpec
                    .Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
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
            private const val KEY_ALIAS = "tempo_backup_passphrase_key"
            private const val ANDROID_KEY_STORE = "AndroidKeyStore"
            private const val TRANSFORMATION = "AES/GCM/NoPadding"
            private const val SEPARATOR = "."
            private const val GCM_TAG_LENGTH_BITS = 128
            private const val KEY_SIZE_BITS = 256
        }
    }
