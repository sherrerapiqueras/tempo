package com.mandrecode.tempo.infrastructure.backup.drive

import android.content.Context
import com.mandrecode.tempo.features.backup.domain.model.BackupFrequency
import com.mandrecode.tempo.features.backup.domain.model.DriveBackupFailure
import com.mandrecode.tempo.features.backup.domain.model.DriveBackupSettings
import com.mandrecode.tempo.features.backup.domain.model.DriveBackupStatus
import com.mandrecode.tempo.features.backup.domain.repository.DriveBackupPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** Drive backup settings and attempt history (#306), in [PREFS_NAME]. Device-scoped: never synced. */
@Singleton
class SharedPrefsDriveBackupPreferences
    @Inject
    constructor(
        @ApplicationContext context: Context,
    ) : DriveBackupPreferences {
        private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        private val _settings = MutableStateFlow(readSettings())
        private val _status = MutableStateFlow(readStatus())
        override val settings: StateFlow<DriveBackupSettings> = _settings.asStateFlow()
        override val status: StateFlow<DriveBackupStatus> = _status.asStateFlow()

        @Synchronized
        override fun updateSettings(settings: DriveBackupSettings) {
            prefs
                .edit()
                .putBoolean(KEY_ENABLED, settings.enabled)
                .putString(KEY_FREQUENCY, settings.frequency.name)
                .putBoolean(KEY_UNMETERED_ONLY, settings.unmeteredOnly)
                .apply()
            _settings.value = settings
        }

        @Synchronized
        override fun recordSuccess(atMillis: Long) {
            prefs.edit().putLong(KEY_LAST_SUCCESS, atMillis).apply()
            _status.value = _status.value.copy(lastSuccessAt = atMillis)
        }

        @Synchronized
        override fun replaceLastSuccess(atMillis: Long?) {
            val edit = prefs.edit()
            if (atMillis == null) edit.remove(KEY_LAST_SUCCESS) else edit.putLong(KEY_LAST_SUCCESS, atMillis)
            edit.apply()
            _status.value = _status.value.copy(lastSuccessAt = atMillis)
        }

        @Synchronized
        override fun recordFailure(
            failure: DriveBackupFailure,
            atMillis: Long,
        ) {
            prefs
                .edit()
                .putLong(KEY_LAST_FAILURE_AT, atMillis)
                .putString(KEY_LAST_FAILURE, failure.name)
                .apply()
            _status.value = _status.value.copy(lastFailure = failure, lastFailureAt = atMillis)
        }

        private fun readSettings() =
            DriveBackupSettings(
                enabled = prefs.getBoolean(KEY_ENABLED, false),
                frequency =
                    BackupFrequency.entries.firstOrNull { it.name == prefs.getString(KEY_FREQUENCY, null) }
                        ?: BackupFrequency.DAILY,
                unmeteredOnly = prefs.getBoolean(KEY_UNMETERED_ONLY, true),
            )

        private fun readStatus() =
            DriveBackupStatus(
                lastSuccessAt = prefs.getLong(KEY_LAST_SUCCESS, 0L).takeIf { it > 0 },
                lastFailureAt = prefs.getLong(KEY_LAST_FAILURE_AT, 0L).takeIf { it > 0 },
                lastFailure =
                    prefs.getString(KEY_LAST_FAILURE, null)?.let { stored ->
                        DriveBackupFailure.entries.firstOrNull { it.name == stored }
                    },
            )

        companion object {
            const val PREFS_NAME = "drive_backup_prefs"
            private const val KEY_ENABLED = "enabled"
            private const val KEY_FREQUENCY = "frequency"
            private const val KEY_UNMETERED_ONLY = "unmetered_only"
            private const val KEY_LAST_SUCCESS = "last_success_at"
            private const val KEY_LAST_FAILURE_AT = "last_failure_at"
            private const val KEY_LAST_FAILURE = "last_failure"
        }
    }
