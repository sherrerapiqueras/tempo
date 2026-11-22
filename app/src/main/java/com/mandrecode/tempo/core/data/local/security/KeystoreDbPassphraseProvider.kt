package com.mandrecode.tempo.core.data.local.security

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.security.GeneralSecurityException
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * Protects the local database's passphrase with an Android Keystore-backed AES key so the
 * database can be opened transparently on every launch, with no user-facing secret.
 *
 * The passphrase itself is random key material (not a human password) — but it is still passed
 * to SQLCipher as a bound byte[] parameter, so SQLCipher runs it through its own internal PBKDF2
 * derivation like any other passphrase (as opposed to the `x'<hex>'` raw-key literal syntax,
 * which would skip that derivation and produce a different final encryption key).
 */
@OptIn(ExperimentalEncodingApi::class)
class KeystoreDbPassphraseProvider
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) : DbPassphraseProvider {
        private val mutex = Mutex()

        override suspend fun getOrCreatePassphrase(): ByteArray =
            withContext(Dispatchers.IO) {
                mutex.withLock {
                    val prefs = securePrefs()
                    val storedBlob = prefs.getString(KEY_PASSPHRASE_BLOB, null)
                    if (storedBlob != null) {
                        decryptBlob(storedBlob)
                    } else {
                        generateAndStorePassphrase(prefs)
                    }
                }
            }

        private fun securePrefs(): SharedPreferences =
            context.getSharedPreferences(
                SECURE_PREFS_FILE_NAME,
                Context.MODE_PRIVATE,
            )

        private fun keyStore(): KeyStore = KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }

        /**
         * A missing or unusable Keystore key while a passphrase blob still exists means the
         * existing encrypted database can never be re-keyed safely — surface that distinctly
         * rather than silently minting a new passphrase that would orphan real user data.
         */
        private fun decryptBlob(blob: String): ByteArray {
            val (iv, ciphertext) = parseBlob(blob)
            val key = requireKeystoreKey()
            return decryptWithKey(key, iv, ciphertext)
        }

        private fun parseBlob(blob: String): Pair<ByteArray, ByteArray> {
            val parts = blob.split(BLOB_SEPARATOR, limit = 2)
            if (parts.size != 2) {
                throw UnrecoverableDatabaseKeyException("Stored database key blob is malformed")
            }
            return try {
                Base64.Default.decode(parts[0]) to Base64.Default.decode(parts[1])
            } catch (e: IllegalArgumentException) {
                throw UnrecoverableDatabaseKeyException("Stored database key blob is not valid base64", e)
            }
        }

        private fun requireKeystoreKey(): SecretKey =
            try {
                keyStore().getKey(KEY_ALIAS, null) as? SecretKey
                    ?: throw UnrecoverableDatabaseKeyException(
                        "Database key blob exists but its Keystore key is missing",
                    )
            } catch (e: GeneralSecurityException) {
                throw UnrecoverableDatabaseKeyException("Unable to access Android Keystore", e)
            }

        private fun decryptWithKey(
            key: SecretKey,
            iv: ByteArray,
            ciphertext: ByteArray,
        ): ByteArray =
            try {
                runCipher(key, iv, ciphertext)
            } catch (e: KeyPermanentlyInvalidatedException) {
                throw UnrecoverableDatabaseKeyException("Database Keystore key was invalidated", e)
            } catch (e: GeneralSecurityException) {
                throw UnrecoverableDatabaseKeyException("Unable to decrypt the stored database key", e)
            }

        /** Split out so both this and [decryptWithKey] stay within detekt's per-function ThrowsCount. */
        private fun runCipher(
            key: SecretKey,
            iv: ByteArray,
            ciphertext: ByteArray,
        ): ByteArray =
            try {
                val cipher = Cipher.getInstance(TRANSFORMATION)
                cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
                cipher.doFinal(ciphertext)
            } catch (e: IllegalArgumentException) {
                // e.g. a malformed IV length from a corrupted blob that still happened to be
                // valid base64 — GCMParameterSpec/Cipher.init can throw this instead of a
                // GeneralSecurityException, and it must map to the same domain exception.
                throw UnrecoverableDatabaseKeyException("Unable to decrypt the stored database key", e)
            }

        /**
         * Always mints a fresh Keystore key alongside a fresh passphrase, even if a stale alias
         * is somehow already present, so the two are never mismatched.
         */
        @SuppressLint("UseKtx")
        private fun generateAndStorePassphrase(prefs: SharedPreferences): ByteArray {
            val store = keyStore()
            if (store.containsAlias(KEY_ALIAS)) {
                store.deleteEntry(KEY_ALIAS)
            }
            val key = generateKeystoreKey()

            val passphrase = ByteArray(PASSPHRASE_LENGTH_BYTES)
            SecureRandom().nextBytes(passphrase)

            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, key)
            val ciphertext = cipher.doFinal(passphrase)

            val blob =
                Base64.Default.encode(cipher.iv) + BLOB_SEPARATOR +
                    Base64.Default.encode(ciphertext)
            // Must be a synchronous, verified write: this passphrase may be used to key/migrate
            // the database within this same call before returning. An async apply() that hasn't
            // flushed by the time the process dies would leave the blob missing on next launch,
            // silently minting a *different* passphrase and permanently orphaning that database.
            check(prefs.edit().putString(KEY_PASSPHRASE_BLOB, blob).commit()) {
                "Failed to persist the database passphrase blob"
            }

            return passphrase
        }

        private fun generateKeystoreKey(): SecretKey {
            val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEY_STORE)
            val spec =
                KeyGenParameterSpec
                    .Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(KEY_SIZE_BITS)
                    .setUserAuthenticationRequired(false)
                    .build()
            keyGenerator.init(spec)
            return keyGenerator.generateKey()
        }

        private companion object {
            const val ANDROID_KEY_STORE = "AndroidKeyStore"
            const val KEY_ALIAS = "tempo_db_passphrase_key"
            const val SECURE_PREFS_FILE_NAME = "tempo_secure_prefs"
            const val KEY_PASSPHRASE_BLOB = "db_passphrase_blob"
            const val BLOB_SEPARATOR = "."
            const val TRANSFORMATION = "AES/GCM/NoPadding"
            const val GCM_TAG_LENGTH_BITS = 128
            const val KEY_SIZE_BITS = 256
            const val PASSPHRASE_LENGTH_BYTES = 32
        }
    }
