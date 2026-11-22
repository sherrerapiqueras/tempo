package com.mandrecode.tempo

import android.app.Application
import androidx.compose.ui.ComposeUiFlags
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.mandrecode.tempo.core.data.local.TempoDatabase
import com.mandrecode.tempo.core.data.local.security.DatabaseWarmupSignal
import com.mandrecode.tempo.features.tasks.domain.usecase.PruneOrphanedTaskAttachmentsUseCase
import com.mandrecode.tempo.features.widget.presentation.TodayWidgetUpdater
import dagger.Lazy
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
@OptIn(ExperimentalComposeUiApi::class)
class TempoApp :
    Application(),
    Configuration.Provider {
    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var tempoDatabaseLazy: Lazy<TempoDatabase>

    @Inject
    lateinit var databaseWarmupSignal: DatabaseWarmupSignal

    @Inject
    lateinit var pruneOrphanedTaskAttachments: Lazy<PruneOrphanedTaskAttachmentsUseCase>

    @Inject
    lateinit var todayWidgetUpdater: Lazy<TodayWidgetUpdater>

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        val processStartedAtEpochMillis = System.currentTimeMillis()
        ComposeUiFlags.isMediaQueryIntegrationEnabled = true
        // SQLCipher's native core must be loaded explicitly before any database access —
        // unlike the deprecated android-database-sqlcipher library, this one does not do it
        // automatically as part of opening a database.
        System.loadLibrary("sqlcipher")
        super.onCreate()
        // Warm up the encrypted database off the main thread as early as possible.
        // DatabaseModule.provideTempoDatabase() blocks its caller on Keystore I/O and a
        // potential one-time migration; Hilt's Lazy/Provider memoize on first resolution
        // (DoubleCheck), so triggering that resolution here means the first ViewModel that
        // needs the database moments later — on the main thread — finds it already built (or
        // waits only for whatever's left) instead of paying the full cost itself.
        //
        // A failure here (e.g. UnrecoverableDatabaseKeyException) must not crash the process:
        // an uncaught exception on a coroutine without a CoroutineExceptionHandler propagates
        // to this thread's default handler and takes the app down before any UI can show an
        // error. Swallowing it here is safe — Dagger's DoubleCheck doesn't cache a failed
        // resolution, so the real caller (e.g. the first ViewModel) retries and surfaces the
        // same failure properly through the normal call path. CancellationException must still
        // propagate, though — swallowing it would break structured concurrency (e.g. a
        // cancelled applicationScope wouldn't actually stop this coroutine).
        applicationScope.launch {
            try {
                tempoDatabaseLazy.get()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Intentionally ignored — see comment above.
            } finally {
                // Runs on success, failure, and cancellation alike: MainActivity's splash-screen
                // hold only cares that the warm-up attempt has concluded, not how.
                databaseWarmupSignal.markReady()
            }
            // After the warm-up, so it never competes with the first screen for the database.
            // Same failure contract: nothing here may take the app down.
            try {
                pruneOrphanedTaskAttachments.get()(processStartedAtEpochMillis)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Orphans left now are pruned on the next start.
            }
            // Also after the warm-up: it reads the day from the database the moment it starts.
            todayWidgetUpdater.get().start(applicationScope)
        }
    }

    override val workManagerConfiguration: Configuration
        get() =
            Configuration
                .Builder()
                .setWorkerFactory(workerFactory)
                .build()
}
