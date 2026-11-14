package com.mandrecode.tempo.core.ui.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation3.runtime.NavKey
import com.mandrecode.tempo.R
import com.mandrecode.tempo.core.ui.components.SettingsButton
import com.mandrecode.tempo.core.ui.glass.glass
import com.mandrecode.tempo.core.ui.theme.topBarTitle
import com.mandrecode.tempo.features.focus.domain.repository.FocusSessionRepository

private val RailTitleMinFontSize = 16.sp

internal fun <T> floatingControlsMotionSpec() =
    spring<T>(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMediumLow,
    )

@Composable
internal fun PersistentFloatingBar(
    currentRoute: NavKey,
    topLevelRoute: NavKey,
    focusSessionRepository: FocusSessionRepository,
    onOpenSession: () -> Unit,
    focusState: FocusFloatingBarState,
    routinesState: RoutinesFloatingBarState,
    tasksState: TasksFloatingBarState,
    onNavigateToTopLevel: (NavKey) -> Unit,
    onOpenSettings: () -> Unit,
    onRouteChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val isRailLayout = isFloatingNavigationRailLayout()
    val isTasksRoute = topLevelRoute == TasksRoute
    val isRailSettingsDestination = currentRoute == SettingsRoute && isRailLayout
    // The session sheet sits over whichever tab you opened it from, the same way the task and habit
    // sheets do. Those never change the route, so the bar stays; this one does, and the bar was
    // vanishing under it for no reason the user could see.
    val barRoute = if (currentRoute == FocusSessionRoute) topLevelRoute else currentRoute
    val visible =
        when {
            barRoute == FocusRoute -> true
            barRoute == RoutinesRoute -> routinesState.visible
            barRoute == TasksRoute -> tasksState.visible
            isRailSettingsDestination -> true
            else -> false
        }

    if (!visible) return

    val navigationContent: @Composable () -> Unit = {
        TempoBottomNavigation(
            currentRoute = barRoute,
            focusSessionRepository = focusSessionRepository,
            onNavigateToTopLevel = onNavigateToTopLevel,
            onReselect = { route -> if (route == TasksRoute) tasksState.onReselect() },
            onRouteChange = onRouteChange,
            onOpenSession = onOpenSession,
            // Tasks carries sort beside the pill and the add button; on a 360dp phone with every tab
            // enabled that leaves no room for the countdown, so the chip stays compact there.
            hasContextualActions = isTasksRoute,
        )
    }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment =
            if (isRailLayout) {
                Alignment.TopStart
            } else {
                Alignment.BottomCenter
            },
    ) {
        if (isRailLayout) {
            PersistentLandscapeFloatingBar(
                isTasksRoute = isTasksRoute,
                topLevelRoute = topLevelRoute,
                navigationContent = navigationContent,
                focusState = focusState,
                routinesState = routinesState,
                tasksState = tasksState,
                onOpenSettings = onOpenSettings,
                settingsSelected = currentRoute == SettingsRoute,
            )
        } else {
            PersistentPortraitFloatingBar(
                isTasksRoute = isTasksRoute,
                topLevelRoute = topLevelRoute,
                navigationContent = navigationContent,
                focusState = focusState,
                routinesState = routinesState,
                tasksState = tasksState,
            )
        }
    }
}

@Composable
private fun PersistentLandscapeFloatingBar(
    isTasksRoute: Boolean,
    topLevelRoute: NavKey,
    navigationContent: @Composable () -> Unit,
    focusState: FocusFloatingBarState,
    routinesState: RoutinesFloatingBarState,
    tasksState: TasksFloatingBarState,
    onOpenSettings: () -> Unit,
    settingsSelected: Boolean,
) {
    val addAction = rememberAddAction(topLevelRoute, focusState, routinesState, tasksState)
    val isExpandedRail = isExpandedFloatingRailLayout()

    // Rail hierarchy: app identity first on expanded rails, then the primary add action,
    // navigation tabs, the contextual sort action, and finally settings pinned to the bottom.
    // The scrollable/weighted content above settings is split into its own Column so settings
    // never gets pushed out of bounds when the rail runs out of vertical space.
    Column(
        modifier =
            Modifier
                .fillMaxHeight()
                // systemBars rather than statusBars: in a freeform desktop window the top inset is the
                // caption bar, whose back arrow otherwise draws over the expanded rail's app title.
                .windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Vertical))
                .padding(start = FloatingRailStartPadding, top = 16.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(FloatingToolbarItemSpacing),
        horizontalAlignment = if (isExpandedRail) Alignment.Start else Alignment.CenterHorizontally,
    ) {
        RailScrollableContent(
            isTasksRoute = isTasksRoute,
            navigationContent = navigationContent,
            tasksState = tasksState,
            addAction = addAction,
            isExpandedRail = isExpandedRail,
            settingsSelected = settingsSelected,
            modifier = Modifier.weight(1f),
        )

        SettingsRailButton(
            selected = settingsSelected,
            expanded = isExpandedRail,
            onClick = onOpenSettings,
        )
    }
}

@Composable
private fun RailScrollableContent(
    isTasksRoute: Boolean,
    navigationContent: @Composable () -> Unit,
    tasksState: TasksFloatingBarState,
    addAction: AddAction?,
    isExpandedRail: Boolean,
    settingsSelected: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(FloatingToolbarItemSpacing),
        horizontalAlignment = if (isExpandedRail) Alignment.Start else Alignment.CenterHorizontally,
    ) {
        if (isExpandedRail) {
            RailAppTitle()
            AnimatedVisibility(
                visible = !settingsSelected && addAction != null,
                enter = expandVertically(expandFrom = Alignment.Top) + fadeIn(),
                exit = shrinkVertically(shrinkTowards = Alignment.Top) + fadeOut(),
            ) {
                if (addAction != null) {
                    TempoSoloActionButton(
                        iconRes = R.drawable.ic_add,
                        label = addAction.label,
                        expanded = true,
                        onClick = addAction.onClick,
                    )
                }
            }
        } else {
            AnimatedVisibility(
                visible = !settingsSelected && addAction != null,
                enter = expandVertically(expandFrom = Alignment.Top) + fadeIn(),
                exit = shrinkVertically(shrinkTowards = Alignment.Top) + fadeOut(),
            ) {
                if (addAction != null) {
                    AddActionButton(addAction)
                }
            }
        }

        navigationContent()

        VerticalTaskActionButtons(
            tasksState = tasksState,
            showActions = isTasksRoute && !settingsSelected,
            modifier = Modifier.padding(start = if (isExpandedRail) FloatingToolbarRailSurfacePadding else 0.dp),
            expanded = isExpandedRail,
        )
    }
}

@Composable
private fun RailAppTitle() {
    // Bounded to the rail and shrunk to fit: a longer app name (a translation, say) would
    // otherwise run past the rail into the screen content, which starts only
    // FloatingRailContentGap after it.
    val titleStyle = MaterialTheme.typography.topBarTitle
    Text(
        text = stringResource(R.string.app_name),
        style = titleStyle,
        color = MaterialTheme.colorScheme.primary,
        maxLines = 1,
        autoSize =
            TextAutoSize.StepBased(
                minFontSize = RailTitleMinFontSize,
                maxFontSize = titleStyle.fontSize,
            ),
        modifier =
            Modifier
                .width(FloatingRailExpandedSurfaceWidth)
                .padding(
                    start = FloatingToolbarRailSurfacePadding,
                    end = FloatingToolbarRailSurfacePadding,
                    bottom = FloatingToolbarItemSpacing,
                ),
    )
}

@Composable
private fun SettingsRailButton(
    selected: Boolean,
    expanded: Boolean,
    onClick: () -> Unit,
) {
    if (!expanded) {
        SettingsButton(
            onClick = onClick,
            selected = selected,
        )
        return
    }

    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = Color.Transparent,
        contentColor =
            if (selected) {
                MaterialTheme.colorScheme.onSecondaryContainer
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        modifier =
            Modifier
                .width(FloatingRailExpandedSurfaceWidth)
                .height(FloatingToolbarItemSize)
                .glass(
                    shape = CircleShape,
                    tint = if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Unspecified,
                ).semantics { this.selected = selected },
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_settings),
                contentDescription = null,
            )
            Text(
                text = stringResource(R.string.settings),
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}

@Composable
internal fun AddActionButton(addAction: AddAction) {
    TempoBottomRailActionButton(
        iconRes = R.drawable.ic_add,
        contentDescription = addAction.label,
        onClick = addAction.onClick,
    )
}

/**
 * Holds on to the last non-null [value] so content on its way out of an [AnimatedVisibility] still
 * has something to draw while it shrinks.
 */
@Composable
internal fun <T : Any> rememberLastNonNull(value: T?): T? {
    var last by remember { mutableStateOf(value) }
    if (value != null) last = value
    return last
}

/**
 * The primary add action for the active tab, or `null` for a tab that has none. Focus adds a
 * session — a timer with a name, which is the one thing made on Focus rather than in Tasks (#517).
 */
@Composable
internal fun rememberAddAction(
    topLevelRoute: NavKey,
    focusState: FocusFloatingBarState,
    routinesState: RoutinesFloatingBarState,
    tasksState: TasksFloatingBarState,
): AddAction? =
    when (topLevelRoute) {
        FocusRoute ->
            AddAction(
                label = stringResource(R.string.add_session),
                onClick = focusState.onAddSession,
            )

        TasksRoute ->
            AddAction(
                label = stringResource(R.string.add_task),
                onClick = tasksState.onAddTask,
            )

        RoutinesRoute ->
            AddAction(
                label = stringResource(R.string.add_habit),
                onClick = routinesState.onAddHabit,
            )

        else -> null
    }

internal data class AddAction(
    val label: String,
    val onClick: () -> Unit,
)
