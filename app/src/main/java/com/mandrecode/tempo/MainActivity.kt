package com.mandrecode.tempo

import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.navigation3.runtime.NavKey
import com.mandrecode.tempo.core.data.local.security.DatabaseWarmupSignal
import com.mandrecode.tempo.core.data.preferences.NavigationPreferencesRepository
import com.mandrecode.tempo.core.data.preferences.ThemePreferencesRepository
import com.mandrecode.tempo.core.domain.model.TempoTab
import com.mandrecode.tempo.core.domain.model.ThemeMode
import com.mandrecode.tempo.core.ui.MainViewModel
import com.mandrecode.tempo.core.ui.StartupContentGate
import com.mandrecode.tempo.core.ui.components.StartupContentGateHost
import com.mandrecode.tempo.core.ui.glass.BackdropBlurHost
import com.mandrecode.tempo.core.ui.model.MainUiState
import com.mandrecode.tempo.core.ui.navigation.OnboardingRoute
import com.mandrecode.tempo.core.ui.navigation.PendingNotificationAction
import com.mandrecode.tempo.core.ui.navigation.TempoNavHost
import com.mandrecode.tempo.core.ui.navigation.route
import com.mandrecode.tempo.core.ui.navigation.tabViewModel
import com.mandrecode.tempo.core.ui.navigation.tabViewModelKey
import com.mandrecode.tempo.core.ui.theme.ApplyApplicationNightMode
import com.mandrecode.tempo.core.ui.theme.TempoTheme
import com.mandrecode.tempo.features.focus.domain.repository.FocusSessionRepository
import com.mandrecode.tempo.features.focus.presentation.FocusViewModel
import com.mandrecode.tempo.features.routines.presentation.RoutinesViewModel
import com.mandrecode.tempo.features.tasks.presentation.TasksViewModel
import com.mandrecode.tempo.features.widget.presentation.QuickAddTaskWidget
import com.mandrecode.tempo.infrastructure.reminders.ReminderRefreshScheduler
import com.mandrecode.tempo.infrastructure.reminders.receivers.HabitReminderReceiver
import com.mandrecode.tempo.infrastructure.reminders.receivers.MarkAsCompletedReceiver
import com.mandrecode.tempo.infrastructure.reminders.receivers.TaskReminderReceiver
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity :
    ComponentActivity(),
    StartupContentGateHost {
    @Inject
    lateinit var navigationPreferencesRepository: NavigationPreferencesRepository

    @Inject
    lateinit var focusSessionRepository: FocusSessionRepository

    @Inject
    lateinit var themePreferencesRepository: ThemePreferencesRepository

    @Inject
    lateinit var databaseWarmupSignal: DatabaseWarmupSignal

    private val mainViewModel: MainViewModel by viewModels()

    override val startupContentGate = StartupContentGate()

    // Triggers for navigation
    private val routinesNavigationTrigger = mutableLongStateOf(0L)
    private val tasksNavigationTrigger = mutableLongStateOf(0L)

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)

        // Hold the system splash screen — which users already expect to sit on briefly — until
        // the encrypted database has finished its startup warm-up (see TempoApp.onCreate), or
        // MAX_SPLASH_HOLD_MS elapses, whichever comes first. Without this, the splash dismisses
        // on first frame (near-instant, since MainUiState.Loading only depends on DataStore
        // preferences, not the database) and the SQLCipher key-derivation delay instead shows up
        // moments later as the in-app loading indicator on whichever screen needs DAO data first
        // — a much more jarring place for a startup cost to become visible. The bound keeps a
        // slow device or a failed warm-up (see DatabaseWarmupSignal) from holding the splash
        // indefinitely.
        //
        // It also holds while the app's own settings are loading and while the first screen is
        // still fetching its first content (StartupContentGate), so the splash hands over to a
        // finished screen rather than to its bare background with the content arriving on top a
        // moment later (#438). The same bound applies to all three.
        val splashStartElapsedMs = SystemClock.elapsedRealtime()
        splashScreen.setKeepOnScreenCondition {
            val stillStarting =
                !databaseWarmupSignal.isReady.value ||
                    mainViewModel.uiState.value is MainUiState.Loading ||
                    startupContentGate.isHolding
            val keep = stillStarting && SystemClock.elapsedRealtime() - splashStartElapsedMs < MAX_SPLASH_HOLD_MS
            if (!keep) {
                // Also when the cap ran out first: the splash is gone, so a load still running
                // is on screen, and its content should fade in over it rather than snap.
                startupContentGate.release()
            }
            keep
        }

        warmTabsWhenDatabaseIsReady()

        // Establish edge-to-edge before first composition so the app draws behind the system bars
        // from the first frame. TempoTheme's SideEffect re-applies the theme-aware transparent
        // dark/light style on every recomposition.
        enableEdgeToEdge()

        setContent {
            val uiState by mainViewModel.uiState.collectAsStateWithLifecycle()
            val pendingNotificationAction by mainViewModel.pendingNotificationAction.collectAsStateWithLifecycle()

            when (val state = uiState) {
                is MainUiState.Loading -> {
                    // Must be wrapped in TempoTheme, not just MaterialTheme.colorScheme.surface
                    // directly — without it this resolves to Compose's unthemed default M3
                    // baseline scheme (a plain lavender-tinted background), not this app's actual
                    // colors, producing a visible flash to the correct theme once
                    // MainUiState.Success arrives. The splash-hold above makes this frame visible
                    // more often: it releases as soon as the database is ready, independently of
                    // whether this Loading state has resolved, so this is no longer guaranteed to
                    // be as short-lived as it was before that change.
                    //
                    // useTempoColors = true matches ThemePreferencesRepositoryImpl's actual
                    // stored default (getCurrentUseTempoColors() falls back to true, not
                    // TempoTheme's own false default) — keep these in sync if that default ever
                    // changes. darkTheme is left at TempoTheme's own isSystemInDarkTheme()
                    // default, matching ThemeMode.SYSTEM, the stored theme mode default.
                    TempoTheme(useTempoColors = true) {
                        Surface(
                            modifier = Modifier.fillMaxSize(),
                            color = MaterialTheme.colorScheme.surface,
                        ) {}
                    }
                }

                is MainUiState.Success -> {
                    ApplyApplicationNightMode(state.themeMode)

                    // Read once: the start destination is fixed for the session, so there is no need to
                    // hit SharedPreferences on every recomposition.
                    val lastRoute = remember { navigationPreferencesRepository.getLastRoute() }
                    val startDestination = rememberStartDestination(state, lastRoute)

                    TempoTheme(
                        darkTheme =
                            when (state.themeMode) {
                                ThemeMode.LIGHT -> false
                                ThemeMode.DARK -> true
                                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                            },
                        useTempoColors = state.useTempoColors,
                    ) {
                        // colorScheme.surface, not .background: Tasks/Routines' NavDisplay
                        // crossfade layers both scenes' alpha over this Surface, so whatever
                        // shows through briefly during the fade should match the larger of the
                        // two blocks in their two-block layout (the white/near-black content
                        // area) rather than the tinted top strip, or that content area flashes
                        // tinted for a frame on every tab switch.
                        Surface(
                            modifier = Modifier.fillMaxSize(),
                            color = MaterialTheme.colorScheme.surface,
                        ) {
                            // Blurs everything here while a sheet or dialog is open over it.
                            BackdropBlurHost(modifier = Modifier.fillMaxSize()) {
                                TempoNavHost(
                                    focusSessionRepository = focusSessionRepository,
                                    routinesNavigationTrigger = routinesNavigationTrigger.longValue,
                                    tasksNavigationTrigger = tasksNavigationTrigger.longValue,
                                    pendingNotificationAction = pendingNotificationAction,
                                    onConsumePendingNotificationAction = {
                                        mainViewModel.consumePendingNotificationAction()
                                        intent.clearNotificationExtras()
                                    },
                                    startDestination = startDestination,
                                    onRouteChange = { routeName ->
                                        navigationPreferencesRepository.saveLastRoute(routeName)
                                    },
                                )
                            }
                        }
                    }
                }
            }

            LaunchedEffect(Unit) {
                withFrameNanos { }
                ReminderRefreshScheduler.enqueuePeriodicRefresh(applicationContext)
                ReminderRefreshScheduler.enqueueImmediateRefresh(applicationContext)
            }
        }

        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent == null) return

        var handledTaskNotificationOpen = false
        var handledRoutineNotificationOpen = false

        if (intent.hasExtra(TaskReminderReceiver.EXTRA_TASK_ID)) {
            val taskId = intent.getLongExtra(TaskReminderReceiver.EXTRA_TASK_ID, -1L)
            // Periodic tasks have their reminderDate pre-advanced by TaskReminderReceiver
            // before the notification fires. The notification embeds the original
            // reminderDate so the bottom-sheet completion toggle can compute the next
            // occurrence from the correct anchor (mirrors MarkAsCompletedReceiver).
            val originalReminderDate =
                intent
                    .getStringExtra(MarkAsCompletedReceiver.EXTRA_ORIGINAL_REMINDER_DATE)
                    ?.let { runCatching { LocalDateTime.parse(it) }.getOrNull() }
            if (taskId != -1L) {
                mainViewModel.setPendingNotificationAction(
                    PendingNotificationAction.OpenTask(
                        taskId = taskId,
                        originalReminderDate = originalReminderDate,
                    ),
                )
                tasksNavigationTrigger.longValue++
                handledTaskNotificationOpen = true
            }
        }

        if (intent.hasExtra(HabitReminderReceiver.EXTRA_HABIT_ID)) {
            val habitId = intent.getLongExtra(HabitReminderReceiver.EXTRA_HABIT_ID, -1L)
            if (habitId != -1L) {
                mainViewModel.setPendingNotificationAction(PendingNotificationAction.OpenHabit(habitId))
                routinesNavigationTrigger.longValue++
                handledRoutineNotificationOpen = true
            }
        }

        if (intent.hasExtra(HabitReminderReceiver.EXTRA_HABIT_CHAIN_ID)) {
            val chainId = intent.getLongExtra(HabitReminderReceiver.EXTRA_HABIT_CHAIN_ID, -1L)
            val scheduledDate = intent.getHabitChainScheduledDate()
            if (chainId != -1L) {
                mainViewModel.setPendingNotificationAction(
                    PendingNotificationAction.OpenHabitChain(
                        chainId = chainId,
                        scheduledDate = scheduledDate,
                    ),
                )
                routinesNavigationTrigger.longValue++
                handledRoutineNotificationOpen = true
            }
        }

        handleExplicitNavigationRequests(intent, handledRoutineNotificationOpen, handledTaskNotificationOpen)
    }

    private fun handleExplicitNavigationRequests(
        intent: Intent,
        handledRoutineNotificationOpen: Boolean,
        handledTaskNotificationOpen: Boolean,
    ) {
        if (
            !handledRoutineNotificationOpen &&
            intent.getBooleanExtra(HabitReminderReceiver.EXTRA_OPEN_ROUTINES, false)
        ) {
            routinesNavigationTrigger.longValue++
            intent.removeExtra(HabitReminderReceiver.EXTRA_OPEN_ROUTINES)
        }
        if (
            !handledTaskNotificationOpen &&
            intent.getBooleanExtra(TaskReminderReceiver.EXTRA_OPEN_TASKS, false)
        ) {
            tasksNavigationTrigger.longValue++
            intent.removeExtra(TaskReminderReceiver.EXTRA_OPEN_TASKS)
        }

        // Launched from the home-screen quick-add-task widget. Guarded by the notification
        // flags so a widget extra can never override an already-handled notification deep link
        // if an Intent somehow carried both.
        if (
            !handledRoutineNotificationOpen &&
            !handledTaskNotificationOpen &&
            intent.getBooleanExtra(QuickAddTaskWidget.EXTRA_OPEN_NEW_TASK_DIALOG, false)
        ) {
            mainViewModel.setPendingNotificationAction(PendingNotificationAction.OpenNewTaskDialog)
            tasksNavigationTrigger.longValue++
            intent.removeExtra(QuickAddTaskWidget.EXTRA_OPEN_NEW_TASK_DIALOG)
        }
    }

    private fun Intent.getHabitChainScheduledDate(): LocalDate? =
        getStringExtra(HabitReminderReceiver.EXTRA_SCHEDULED_DATE)
            ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

    private fun Intent.clearNotificationExtras() {
        removeExtra(TaskReminderReceiver.EXTRA_TASK_ID)
        removeExtra(TaskReminderReceiver.EXTRA_OPEN_TASKS)
        removeExtra(MarkAsCompletedReceiver.EXTRA_ORIGINAL_REMINDER_DATE)
        removeExtra(HabitReminderReceiver.EXTRA_HABIT_ID)
        removeExtra(HabitReminderReceiver.EXTRA_HABIT_CHAIN_ID)
        removeExtra(HabitReminderReceiver.EXTRA_OPEN_ROUTINES)
        removeExtra(HabitReminderReceiver.EXTRA_SCHEDULED_DATE)
        removeExtra(QuickAddTaskWidget.EXTRA_OPEN_NEW_TASK_DIALOG)
    }

    /**
     * Creates the three tabs' view models as soon as the database is ready, so every tab has its
     * first data loaded before it is opened and appears finished on its first frame (#438). They
     * are the same instances the tabs use (see [tabViewModel]); the one on screen is created here
     * or by its screen, whichever comes first. Created on the main thread, as view models are,
     * but only once the database exists: before that, creating one would block on its warm-up.
     */
    private fun warmTabsWhenDatabaseIsReady() {
        lifecycleScope.launch {
            databaseWarmupSignal.isReady.first { it }
            // "Ready" means the warm-up attempt is over, not that it worked: if the database could
            // not be opened, creating these fails the same way. The warm-up is only an
            // optimisation, so it gives up quietly; the screen that needs the database reports the
            // failure through the usual path when it is opened.
            try {
                val provider = ViewModelProvider(this@MainActivity)
                listOf(FocusViewModel::class.java, RoutinesViewModel::class.java, TasksViewModel::class.java)
                    .forEach { provider[tabViewModelKey(it), it] }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Intentionally ignored — see above.
            }
        }
    }

    private companion object {
        // A safety cap, not a target: the splash lets go the moment the first screen has content.
        // 2.5 s rather than 1.5 s since the splash also waits for that content (#438) — measured
        // cold starts on a Pixel 10 AVD reach content in 1.3–1.7 s, and a cap below that lifts the
        // splash onto a still-empty screen.
        const val MAX_SPLASH_HOLD_MS = 2_500L
    }
}

@Composable
internal fun rememberStartDestination(
    state: MainUiState.Success,
    lastRoute: String?,
): NavKey =
    remember {
        if (!state.isOnboardingCompleted) {
            OnboardingRoute()
        } else {
            // Reopens on the tab the user left, Focus (the home surface) on a first launch.
            (TempoTab.fromPreferenceValue(lastRoute) ?: TempoTab.DEFAULT).route
        }
    }
