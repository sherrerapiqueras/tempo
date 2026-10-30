package com.mandrecode.tempo.core.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberDecoratedNavEntries
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.mandrecode.tempo.core.domain.model.TempoTab
import com.mandrecode.tempo.core.ui.adaptive.SheetPlacement
import com.mandrecode.tempo.core.ui.adaptive.rememberSheetPlacement
import com.mandrecode.tempo.core.ui.glass.GlassBackdropSource
import com.mandrecode.tempo.core.ui.glass.ProvideGlassBackdrop
import com.mandrecode.tempo.core.ui.glass.rememberGlassBackdrop
import com.mandrecode.tempo.features.focus.domain.repository.FocusSessionRepository
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.Serializable

@Serializable
object FocusRoute : NavKey

@Serializable
object RoutinesRoute : NavKey

@Serializable
object TasksRoute : NavKey

@Serializable
object SettingsRoute : NavKey

/**
 * The running focus session, presented as a slide-in overlay like Settings rather than a tab
 * destination — it is somewhere the user goes and comes back from.
 */
@Serializable
object FocusSessionRoute : NavKey

@Serializable
data class OnboardingRoute(
    val isReplay: Boolean = false,
) : NavKey

sealed interface PendingNotificationAction {
    data class OpenTask(
        val taskId: Long,
        val originalReminderDate: LocalDateTime?,
    ) : PendingNotificationAction

    data class OpenHabit(
        val habitId: Long,
    ) : PendingNotificationAction

    data class OpenHabitChain(
        val chainId: Long,
        val scheduledDate: LocalDate? = null,
    ) : PendingNotificationAction

    // Not notification-triggered — set when MainActivity is launched from the home-screen
    // quick-add-task widget, to open the same blank task creation sheet as the in-app "+" button.
    data object OpenNewTaskDialog : PendingNotificationAction
}

@OptIn(ExperimentalMaterial3Api::class)
/**
 * The two ways a task's editor is opened from outside the Tasks tab: a notification extra the
 * activity received, and Focus asking in-app. Both travel the same channel rather than Focus
 * getting a second way in, and the in-app request wins while set — a stale notification extra must
 * not shadow the tap the user just made.
 */
@Stable
private class TaskEditorRequests {
    var inApp by mutableStateOf<PendingNotificationAction?>(null)
        private set

    fun request(taskId: Long) {
        inApp = PendingNotificationAction.OpenTask(taskId, null)
    }

    fun consume(onConsumeExternal: () -> Unit) {
        if (inApp != null) inApp = null else onConsumeExternal()
    }
}

/**
 * windowInsetsPadding, not a plain padding(startInset, endInset): it also marks this horizontal
 * inset as consumed for descendants, so NavDisplay/Scaffold don't apply the same safe-drawing inset
 * a second time internally (a plain padding() modifier doesn't consume anything, which doubled this
 * offset and visibly displaced the app-bar title).
 */
@Composable
private fun rememberHorizontalInsetPadding(): Modifier =
    Modifier.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))

/** The session screen always sits on the Focus tab's stack, wherever the user opened it from. */
private fun TempoNavigator.openSession() {
    navigateToTopLevel(FocusRoute)
    navigate(FocusSessionRoute)
}

@Composable
fun TempoNavHost(
    focusSessionRepository: FocusSessionRepository,
    modifier: Modifier = Modifier,
    routinesNavigationTrigger: Long = 0L,
    tasksNavigationTrigger: Long = 0L,
    pendingNotificationAction: PendingNotificationAction? = null,
    onConsumePendingNotificationAction: () -> Unit = {},
    startDestination: NavKey = RoutinesRoute,
    onRouteChange: (String) -> Unit = {},
) {
    val navigator = rememberTempoNavigator(startDestination)
    val taskEditorRequests = remember { TaskEditorRequests() }
    val openTaskInTasks: (Long) -> Unit = { taskId ->
        taskEditorRequests.request(taskId)
        navigator.navigateToTopLevel(TasksRoute)
    }
    var focusFloatingBarState by remember { mutableStateOf(FocusFloatingBarState()) }
    var routinesFloatingBarState by remember { mutableStateOf(RoutinesFloatingBarState()) }
    var tasksFloatingBarState by remember { mutableStateOf(TasksFloatingBarState()) }

    NotificationNavigationEffects(
        navigator = navigator,
        routinesNavigationTrigger = routinesNavigationTrigger,
        tasksNavigationTrigger = tasksNavigationTrigger,
        onRouteChange = onRouteChange,
    )

    val activeEntries =
        rememberActiveEntries(
            navigator = navigator,
            pendingNotificationAction = taskEditorRequests.inApp ?: pendingNotificationAction,
            onConsumePendingNotificationAction = {
                taskEditorRequests.consume(onConsumePendingNotificationAction)
            },
            onFocusFloatingBarStateChange = { focusFloatingBarState = it },
            onRoutinesFloatingBarStateChange = { routinesFloatingBarState = it },
            onTasksFloatingBarStateChange = { tasksFloatingBarState = it },
            onOpenTaskInTasks = openTaskInTasks,
        )
    val insetPaddingModifier = rememberHorizontalInsetPadding()

    GlassChromeLayers(
        entries = activeEntries,
        navigator = navigator,
        onOpenTaskInTasks = openTaskInTasks,
        modifier = modifier,
    ) {
        PersistentFloatingBar(
            modifier = insetPaddingModifier,
            currentRoute = navigator.currentRoute,
            topLevelRoute = navigator.topLevelRoute,
            focusSessionRepository = focusSessionRepository,
            onOpenSession = navigator::openSession,
            focusState = focusFloatingBarState,
            routinesState = routinesFloatingBarState,
            tasksState = tasksFloatingBarState,
            onNavigateToTopLevel = navigator::navigateToTopLevel,
            onOpenSettings = { navigator.navigate(SettingsRoute) },
            onRouteChange = onRouteChange,
        )
    }
}

/**
 * The app's two planes. Everything the floating [chrome] can sit over is recorded first, so its
 * glass blurs what is really behind it — the Settings overlay included, which the rail stays beside
 * on large windows — and the chrome is drawn above it, sampling that recording.
 */
@Composable
private fun GlassChromeLayers(
    entries: List<NavEntry<NavKey>>,
    navigator: TempoNavigator,
    onOpenTaskInTasks: (Long) -> Unit,
    modifier: Modifier = Modifier,
    chrome: @Composable () -> Unit,
) {
    val glassBackdrop = rememberGlassBackdrop()
    val insetPaddingModifier = rememberHorizontalInsetPadding()
    Box(modifier = modifier.fillMaxSize()) {
        GlassBackdropSource(backdrop = glassBackdrop, modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.fillMaxSize()) {
                HorizontalInsetMarginStrips()

                TempoNavDisplay(
                    entries = entries,
                    navigator = navigator,
                    modifier = insetPaddingModifier,
                )

                SlideOverlays(navigator = navigator, onOpenTaskInTasks = onOpenTaskInTasks)
            }
        }
        ProvideGlassBackdrop(glassBackdrop, chrome)
    }
}

/**
 * Tinted fill for the safe-drawing horizontal inset (e.g. a landscape display cutout), painted
 * separately from TempoNavDisplay rather than shared with it as a background: TempoNavDisplay
 * crossfades Tasks/Routines by alpha-blending each scene, and MainActivity's root Surface is
 * deliberately colorScheme.surface so that blend doesn't flash a tinted backdrop. A
 * colorScheme.background fill behind TempoNavDisplay itself would become the crossfade's blend
 * target instead and reintroduce that flash — so only these margin strips get it.
 */
@Composable
private fun BoxScope.HorizontalInsetMarginStrips() {
    val layoutDirection = LocalLayoutDirection.current
    val horizontalInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal).asPaddingValues()
    val startInset = horizontalInsets.calculateStartPadding(layoutDirection)
    val endInset = horizontalInsets.calculateEndPadding(layoutDirection)

    if (startInset > 0.dp) {
        Box(
            modifier =
                Modifier
                    .fillMaxHeight()
                    .width(startInset)
                    .align(Alignment.CenterStart)
                    .background(MaterialTheme.colorScheme.background),
        )
    }
    if (endInset > 0.dp) {
        Box(
            modifier =
                Modifier
                    .fillMaxHeight()
                    .width(endInset)
                    .align(Alignment.CenterEnd)
                    .background(MaterialTheme.colorScheme.background),
        )
    }
}

/**
 * Settings presents as a slide-in overlay rather than a NavDisplay scene, for the reason documented
 * on [SettingsSlideOverlay]. The session is a bottom sheet, which brings its own scrim and window,
 * so it needs no overlay of its own — only the route to decide when it is showing.
 */
@Composable
private fun BoxScope.SlideOverlays(
    navigator: TempoNavigator,
    onOpenTaskInTasks: (Long) -> Unit,
) {
    // Computed here rather than passed in: two sibling overlays sharing one caller-supplied
    // modifier trips the "modifiers are used once, by the root layout" rule.
    val insetPadding =
        Modifier.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))

    SettingsSlideOverlay(
        visible = navigator.currentRoute == SettingsRoute,
        onDismiss = { navigator.pop() },
        modifier = insetPadding,
    ) {
        SettingsDestination(navigator = navigator)
    }

    // On a large window Focus docks the session beside its agenda instead (#502).
    if (navigator.currentRoute == FocusSessionRoute && rememberSheetPlacement() != SheetPlacement.DockedPane) {
        FocusSessionDestination(navigator = navigator, onOpenTaskInTasks = onOpenTaskInTasks)
    }
}

@Composable
private fun TempoNavDisplay(
    entries: List<NavEntry<NavKey>>,
    navigator: TempoNavigator,
    modifier: Modifier = Modifier,
) {
    // The default single-pane scene throughout: a docked editor is drawn by its screen and never
    // reaches navigation, so docking can't be mistaken for a change of screen (#501).
    NavDisplay(
        entries = entries,
        modifier = modifier.fillMaxSize(),
        onBack = { navigator.pop() },
        transitionSpec = { navigationTransition(initialScene = initialState, targetScene = targetState) },
        popTransitionSpec = { navigationPopTransition(initialScene = initialState, targetScene = targetState) },
    )
}

@Composable
private fun rememberActiveEntries(
    navigator: TempoNavigator,
    pendingNotificationAction: PendingNotificationAction?,
    onConsumePendingNotificationAction: () -> Unit,
    onFocusFloatingBarStateChange: (FocusFloatingBarState) -> Unit,
    onRoutinesFloatingBarStateChange: (RoutinesFloatingBarState) -> Unit,
    onTasksFloatingBarStateChange: (TasksFloatingBarState) -> Unit,
    onOpenTaskInTasks: (Long) -> Unit,
): List<NavEntry<NavKey>> {
    val decorators =
        listOf<NavEntryDecorator<NavKey>>(
            rememberSaveableStateHolderNavEntryDecorator(),
            rememberViewModelStoreNavEntryDecorator(),
        )
    val entries =
        rememberNavEntryProvider(
            navigator,
            pendingNotificationAction,
            onConsumePendingNotificationAction,
            onFocusFloatingBarStateChange,
            onRoutinesFloatingBarStateChange,
            onTasksFloatingBarStateChange,
            onOpenTaskInTasks,
        )

    val focusEntries = rememberDecoratedNavEntries(navigator.focusBackStack, decorators, entries)
    val routinesEntries = rememberDecoratedNavEntries(navigator.routinesBackStack, decorators, entries)
    val tasksEntries = rememberDecoratedNavEntries(navigator.tasksBackStack, decorators, entries)
    val onboardingEntries = rememberDecoratedNavEntries(navigator.onboardingBackStack, decorators, entries)
    val settingsEntries = rememberDecoratedNavEntries(navigator.settingsBackStack, decorators, entries)
    val sectionEntries =
        when (navigator.section) {
            TempoNavigator.Section.FOCUS -> focusEntries
            TempoNavigator.Section.ROUTINES -> routinesEntries
            TempoNavigator.Section.TASKS -> tasksEntries
            TempoNavigator.Section.SETTINGS -> settingsEntries
            TempoNavigator.Section.ONBOARDING -> onboardingEntries
        }
    // Settings slides in as its own overlay (SettingsSlideOverlay) rather than as a NavDisplay
    // scene, so its entry never reaches NavDisplay here regardless of which section is active.
    return sectionEntries.filterNot { it.metadata.containsKey(SETTINGS_ROUTE_METADATA) }
}

@Composable
private fun rememberNavEntryProvider(
    navigator: TempoNavigator,
    pendingNotificationAction: PendingNotificationAction?,
    onConsumePendingNotificationAction: () -> Unit,
    onFocusFloatingBarStateChange: (FocusFloatingBarState) -> Unit,
    onRoutinesFloatingBarStateChange: (RoutinesFloatingBarState) -> Unit,
    onTasksFloatingBarStateChange: (TasksFloatingBarState) -> Unit,
    onOpenTaskInTasks: (Long) -> Unit,
) = entryProvider<NavKey> {
    entry<FocusRoute>(metadata = mapOf(TOP_LEVEL_ROUTE_METADATA to FocusRoute)) {
        FocusDestination(
            navigator = navigator,
            onFloatingBarStateChange = onFocusFloatingBarStateChange,
            onOpenTaskInTasks = onOpenTaskInTasks,
        )
    }
    entry<RoutinesRoute>(metadata = mapOf(TOP_LEVEL_ROUTE_METADATA to RoutinesRoute)) {
        RoutinesDestination(
            navigator = navigator,
            pendingNotificationAction = pendingNotificationAction,
            onConsumePendingNotificationAction = onConsumePendingNotificationAction,
            onFloatingBarStateChange = onRoutinesFloatingBarStateChange,
        )
    }
    entry<TasksRoute>(metadata = mapOf(TOP_LEVEL_ROUTE_METADATA to TasksRoute)) {
        TasksDestination(
            navigator = navigator,
            pendingNotificationAction = pendingNotificationAction,
            onConsumePendingNotificationAction = onConsumePendingNotificationAction,
            onFloatingBarStateChange = onTasksFloatingBarStateChange,
        )
    }
    entry<OnboardingRoute>(metadata = mapOf(ONBOARDING_ROUTE_METADATA to true)) { route ->
        OnboardingDestination(navigator = navigator, isReplay = route.isReplay)
    }
    // navigator.navigate(SettingsRoute) pushes onto whichever section's back stack is
    // currently active, so this registration is needed for every section's entries, not
    // just settingsBackStack's. It supplies SettingsDestination for SettingsSlideOverlay;
    // the resulting entry is filtered out before reaching NavDisplay (see below) since
    // Settings is never rendered as a NavDisplay scene.
    entry<SettingsRoute>(metadata = mapOf(SETTINGS_ROUTE_METADATA to true)) {
        SettingsDestination(navigator = navigator)
    }
    entry<FocusSessionRoute>(metadata = mapOf(SETTINGS_ROUTE_METADATA to true)) {
        FocusSessionDestination(navigator = navigator, onOpenTaskInTasks = onOpenTaskInTasks)
    }
}

@Composable
private fun NotificationNavigationEffects(
    navigator: TempoNavigator,
    routinesNavigationTrigger: Long,
    tasksNavigationTrigger: Long,
    onRouteChange: (String) -> Unit,
) {
    val currentOnRouteChange by rememberUpdatedState(onRouteChange)

    LaunchedEffect(routinesNavigationTrigger) {
        if (routinesNavigationTrigger > 0) {
            navigator.navigateToTopLevel(RoutinesRoute)
            currentOnRouteChange(TempoTab.ROUTINES.preferenceValue)
        }
    }

    LaunchedEffect(tasksNavigationTrigger) {
        if (tasksNavigationTrigger > 0) {
            navigator.navigateToTopLevel(TasksRoute)
            currentOnRouteChange(TempoTab.TASKS.preferenceValue)
        }
    }
}
