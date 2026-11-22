package com.mandrecode.tempo.core.di

import android.app.NotificationManager
import android.content.Context
import androidx.work.WorkManager
import com.mandrecode.tempo.features.backup.domain.scheduler.BackupReminderScheduler
import com.mandrecode.tempo.features.tasks.domain.scheduler.CompletedTaskCleanupScheduler
import com.mandrecode.tempo.infrastructure.backup.BackupReminderSchedulerImpl
import com.mandrecode.tempo.infrastructure.notifications.NotificationSyncManager
import com.mandrecode.tempo.infrastructure.notifications.NotificationSyncManagerImpl
import com.mandrecode.tempo.infrastructure.permissions.PermissionChecker
import com.mandrecode.tempo.infrastructure.permissions.PermissionCheckerImpl
import com.mandrecode.tempo.infrastructure.tasks.CompletedTaskCleanupSchedulerImpl
import com.mandrecode.tempo.util.AppVersionProvider
import com.mandrecode.tempo.util.AppVersionProviderImpl
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlin.time.Clock

@Module
@InstallIn(SingletonComponent::class)
object InfrastructureModule {
    @Provides
    @Singleton
    fun provideNotificationSyncManager(
        @ApplicationContext context: Context,
    ): NotificationSyncManager {
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        return NotificationSyncManagerImpl(notificationManager)
    }

    @Provides
    @Singleton
    fun providePermissionChecker(impl: PermissionCheckerImpl): PermissionChecker = impl

    @Provides
    @Singleton
    fun provideAppVersionProvider(): AppVersionProvider = AppVersionProviderImpl()

    @Provides
    @Singleton
    fun provideClock(): Clock = Clock.System

    @Provides
    @Singleton
    fun provideWorkManager(
        @ApplicationContext context: Context,
    ): WorkManager = WorkManager.getInstance(context)

    @Provides
    @Singleton
    fun provideCleanupScheduler(impl: CompletedTaskCleanupSchedulerImpl): CompletedTaskCleanupScheduler = impl

    @Provides
    @Singleton
    fun provideBackupReminderScheduler(impl: BackupReminderSchedulerImpl): BackupReminderScheduler = impl
}
