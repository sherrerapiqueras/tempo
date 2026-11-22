package com.mandrecode.tempo.core.data.local.security

import android.annotation.SuppressLint
import android.content.Context
import androidx.annotation.VisibleForTesting

/**
 * Tracks which [SqlCipherKdfIter] value the *on-disk* database is currently keyed with, so
 * [KdfIterRekeyer] doesn't have to re-verify that on every single launch. An absent marker is
 * always safe: [KdfIterRekeyer.rekeyIfNeeded] falls back to actually opening the file to confirm
 * before trusting or updating it. A marker that says [SqlCipherKdfIter.CURRENT], however, *is*
 * trusted outright — that's the whole point, since re-verifying on every read would reintroduce
 * the per-launch open this marker exists to avoid. This is safe under normal operation because
 * every writer of this marker ([TempoDatabase]'s `inboxCallback`, [DatabaseEncryptionMigrator],
 * and [KdfIterRekeyer] itself) only ever writes it immediately after actually verifying the file
 * at that exact moment — the only way it could go stale in the "says CURRENT but isn't" direction
 * is out-of-band tampering with the database file *after* that write, bypassing every one of this
 * app's own write paths (Android's own backup mechanisms already exclude this file — see
 * docs/DB_ENCRYPTION.md). If that ever happened, the subsequent Room open at `CURRENT` would fail
 * loudly there rather than silently using a wrong key.
 *
 * Not secret, so a plain (unencrypted) `SharedPreferences` file is fine — unlike
 * [KeystoreDbPassphraseProvider]'s blob, kdf_iter isn't sensitive on its own.
 *
 * Not scoped per database name/path — fine in production, where Tempo only ever has the one
 * `tempo_database` file, but instrumented tests exercising multiple differently-named database
 * files in the same process must call [clear] between cases to avoid one test's marker leaking
 * into another's.
 */
internal object DbKdfIterMarker {
    fun readOrNull(context: Context): Int? {
        val stored = prefs(context).getInt(KEY_KDF_ITER, NOT_SET)
        return stored.takeIf { it != NOT_SET }
    }

    // commit(), not the KTX edit {}: its result is checked, and the KTX form returns nothing.
    @SuppressLint("UseKtx")
    fun write(
        context: Context,
        kdfIter: Int,
    ) {
        check(prefs(context).edit().putInt(KEY_KDF_ITER, kdfIter).commit()) {
            "Failed to persist the database kdf_iter marker"
        }
    }

    @VisibleForTesting
    @SuppressLint("UseKtx")
    fun clear(context: Context) {
        check(prefs(context).edit().clear().commit()) {
            "Failed to clear the database kdf_iter marker"
        }
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS_FILE_NAME, Context.MODE_PRIVATE)

    private const val PREFS_FILE_NAME = "tempo_db_kdf_prefs"
    private const val KEY_KDF_ITER = "db_kdf_iter"
    private const val NOT_SET = -1
}
