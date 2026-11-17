package com.mandrecode.tempo.core.di

import com.mandrecode.tempo.features.backup.domain.repository.BackupPassphraseStore
import com.mandrecode.tempo.features.backup.domain.repository.CloudAuthorization
import com.mandrecode.tempo.features.backup.domain.repository.CloudBackupStore
import com.mandrecode.tempo.features.backup.domain.repository.DriveBackupPreferences
import com.mandrecode.tempo.features.backup.domain.scheduler.DriveBackupScheduler
import com.mandrecode.tempo.infrastructure.backup.drive.DriveBackupSchedulerImpl
import com.mandrecode.tempo.infrastructure.backup.drive.DriveRestCloudStore
import com.mandrecode.tempo.infrastructure.backup.drive.GoogleDriveAuthorization
import com.mandrecode.tempo.infrastructure.backup.drive.HttpTransport
import com.mandrecode.tempo.infrastructure.backup.drive.KeystoreBackupPassphraseStore
import com.mandrecode.tempo.infrastructure.backup.drive.SharedPrefsDriveBackupPreferences
import com.mandrecode.tempo.infrastructure.backup.drive.UrlConnectionTransport
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** Google Drive backup (#306): domain interfaces to their Android and Drive implementations. */
@Module
@InstallIn(SingletonComponent::class)
abstract class DriveBackupModule {
    @Binds
    abstract fun bindPassphraseStore(impl: KeystoreBackupPassphraseStore): BackupPassphraseStore

    @Binds
    abstract fun bindPreferences(impl: SharedPrefsDriveBackupPreferences): DriveBackupPreferences

    @Binds
    abstract fun bindCloudStore(impl: DriveRestCloudStore): CloudBackupStore

    @Binds
    abstract fun bindAuthorization(impl: GoogleDriveAuthorization): CloudAuthorization

    @Binds
    abstract fun bindScheduler(impl: DriveBackupSchedulerImpl): DriveBackupScheduler

    @Binds
    abstract fun bindTransport(impl: UrlConnectionTransport): HttpTransport
}
