package com.mandrecode.tempo.core.di

import com.mandrecode.tempo.core.data.repository.DailyFocusActivityRepositoryImpl
import com.mandrecode.tempo.core.domain.repository.DailyFocusActivityRepository
import com.mandrecode.tempo.core.domain.usecase.DailyActivityRecorder
import com.mandrecode.tempo.features.backup.data.repository.BackupRepositoryImpl
import com.mandrecode.tempo.features.backup.domain.repository.BackupRepository
import com.mandrecode.tempo.features.focus.data.FocusSessionRepositoryImpl
import com.mandrecode.tempo.features.focus.data.SavedSessionRepositoryImpl
import com.mandrecode.tempo.features.focus.domain.repository.FocusSessionRepository
import com.mandrecode.tempo.features.focus.domain.repository.SavedSessionRepository
import com.mandrecode.tempo.features.focus.domain.scheduler.FocusSessionScheduler
import com.mandrecode.tempo.features.focus.domain.usecase.RecordDailyActivityUseCase
import com.mandrecode.tempo.features.routines.data.repository.HabitChainRepositoryImpl
import com.mandrecode.tempo.features.routines.data.repository.HabitRepositoryImpl
import com.mandrecode.tempo.features.routines.domain.repository.HabitChainRepository
import com.mandrecode.tempo.features.routines.domain.repository.HabitRepository
import com.mandrecode.tempo.features.tasks.data.attachment.AttachmentFileStore
import com.mandrecode.tempo.features.tasks.data.repository.CategoryRepositoryImpl
import com.mandrecode.tempo.features.tasks.data.repository.TaskAttachmentRepositoryImpl
import com.mandrecode.tempo.features.tasks.data.repository.TaskRepositoryImpl
import com.mandrecode.tempo.features.tasks.data.repository.TaskSectionRepositoryImpl
import com.mandrecode.tempo.features.tasks.domain.repository.CategoryRepository
import com.mandrecode.tempo.features.tasks.domain.repository.TaskAttachmentRepository
import com.mandrecode.tempo.features.tasks.domain.repository.TaskRepository
import com.mandrecode.tempo.features.tasks.domain.repository.TaskSectionRepository
import com.mandrecode.tempo.infrastructure.attachments.AndroidAttachmentFileStore
import com.mandrecode.tempo.infrastructure.focus.AndroidFocusSessionScheduler
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
// One binding per repository; the function count grows with the app, not with complexity.
@Suppress("TooManyFunctions")
abstract class RepositoryModule {
    @Binds
    abstract fun bindTaskRepository(impl: TaskRepositoryImpl): TaskRepository

    @Binds
    abstract fun bindTaskAttachmentRepository(impl: TaskAttachmentRepositoryImpl): TaskAttachmentRepository

    @Binds
    abstract fun bindAttachmentFileStore(impl: AndroidAttachmentFileStore): AttachmentFileStore

    @Binds
    abstract fun bindHabitRepository(impl: HabitRepositoryImpl): HabitRepository

    @Binds
    abstract fun bindCategoryRepository(impl: CategoryRepositoryImpl): CategoryRepository

    @Binds
    abstract fun bindTaskSectionRepository(impl: TaskSectionRepositoryImpl): TaskSectionRepository

    @Binds
    abstract fun bindSavedSessionRepository(impl: SavedSessionRepositoryImpl): SavedSessionRepository

    @Binds
    abstract fun bindHabitChainRepository(impl: HabitChainRepositoryImpl): HabitChainRepository

    @Binds
    abstract fun bindBackupRepository(impl: BackupRepositoryImpl): BackupRepository

    @Binds
    abstract fun bindDailyFocusActivityRepository(impl: DailyFocusActivityRepositoryImpl): DailyFocusActivityRepository

    /**
     * Lets Tasks trigger a recount on completion without depending on the Focus
     * feature: it injects the [DailyActivityRecorder] interface declared in `core/domain`.
     */
    @Binds
    abstract fun bindDailyActivityRecorder(impl: RecordDailyActivityUseCase): DailyActivityRecorder

    @Binds
    abstract fun bindFocusSessionRepository(impl: FocusSessionRepositoryImpl): FocusSessionRepository

    @Binds
    abstract fun bindFocusSessionScheduler(impl: AndroidFocusSessionScheduler): FocusSessionScheduler
}
