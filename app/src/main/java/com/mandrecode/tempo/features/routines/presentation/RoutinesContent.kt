package com.mandrecode.tempo.features.routines.presentation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mandrecode.tempo.R
import com.mandrecode.tempo.core.domain.model.VacationPeriod
import com.mandrecode.tempo.core.ui.adaptive.adaptiveHorizontalMargin
import com.mandrecode.tempo.core.ui.components.FirstLoadCrossfade
import com.mandrecode.tempo.core.ui.components.TempoLoadingIndicator
import com.mandrecode.tempo.core.ui.components.WavyDivider
import com.mandrecode.tempo.core.ui.glass.glassPanel
import com.mandrecode.tempo.core.ui.navigation.floatingNavigationBottomClearancePadding
import com.mandrecode.tempo.core.ui.theme.TempoSpacing
import com.mandrecode.tempo.core.ui.theme.emptyStateTitle
import com.mandrecode.tempo.core.ui.theme.sectionHeader
import com.mandrecode.tempo.core.ui.util.rememberPressableButtonAnimation
import com.mandrecode.tempo.features.routines.domain.model.Habit
import com.mandrecode.tempo.features.routines.domain.model.HabitType
import com.mandrecode.tempo.features.routines.presentation.components.cards.HabitCard
import com.mandrecode.tempo.features.routines.presentation.components.cards.HabitChainCard
import com.mandrecode.tempo.features.routines.presentation.components.cards.QuitHabitCard
import com.mandrecode.tempo.features.routines.presentation.components.sections.DayFilterRow
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import kotlin.time.Clock

private val ContentBlockTopCornerRadius = 28.dp
private val ContentBlockShape =
    RoundedCornerShape(topStart = ContentBlockTopCornerRadius, topEnd = ContentBlockTopCornerRadius)

/**
 * [showAddHabitButton] makes this composable self-sufficient in isolation (used directly by
 * `RoutinesContentTest.showsAddHabitFab` under `src/androidTest`, and by previews). In the real
 * app, [RoutinesScreen] always passes `false` here because the shared `PersistentFloatingBar`
 * (`core/ui/navigation/Navigation.kt`) already renders a single add action for both tasks and
 * routines at every window tier — the two tabs' add affordance is
 * already unified there, not a live inconsistency.
 */
@Composable
fun RoutinesContent(
    uiState: RoutinesContract.UiState,
    onEvent: (RoutinesContract.UiEvent) -> Unit,
    modifier: Modifier = Modifier,
    showAddHabitButton: Boolean = true,
    selectedHabitId: Long? = null,
    selectedHabitChainId: Long? = null,
    onContentBlockTopChange: (Dp) -> Unit = {},
) {
    val density = LocalDensity.current
    val listState = rememberLazyListState()
    val listBottomPadding = floatingNavigationBottomClearancePadding(defaultPadding = 16.dp)
    val horizontalMargin = adaptiveHorizontalMargin()

    Box(
        // Transparent: the page's ambient backdrop is painted once by the screen around this
        // (and by the preview hosts), so the panel's translucency picks up the same glows.
        modifier = modifier.fillMaxSize(),
    ) {
        FirstLoadCrossfade(
            isLoading = uiState.isLoading,
            loading = { TempoLoadingIndicator(message = stringResource(R.string.loading_habits)) },
        ) {
            val scheduledItems = uiState.scheduledTimelineItems
            val unscheduledItems = uiState.unscheduledTimelineItems
            val quitHabits =
                remember(uiState.habits) {
                    uiState.habits.filter { it.habitType == HabitType.QUIT }
                }

            Column(
                modifier = Modifier.fillMaxSize(),
            ) {
                // The header's height is where the rounded content block starts, which a docked
                // editor pane beside this content lines its own surface up with (DockedEditorPane).
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .onSizeChanged { onContentBlockTopChange(with(density) { it.height.toDp() }) },
                ) {
                    DayFilterRow(
                        selectedDate = uiState.selectedDate,
                        onSelectDate = { onEvent(RoutinesContract.UiEvent.SelectDate(it)) },
                    )
                }

                Box(
                    modifier =
                        Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            // clip before background: background() alone doesn't clip children,
                            // so list overscroll/ripple effects would otherwise draw past the
                            // rounded top corners instead of respecting the seam.
                            .clip(ContentBlockShape)
                            .glassPanel(ContentBlockShape, MaterialTheme.colorScheme.surface),
                ) {
                    val allBuildItems = scheduledItems + unscheduledItems
                    if (allBuildItems.isEmpty() && quitHabits.isEmpty()) {
                        EmptyDayMessage(
                            selectedDate = uiState.selectedDate,
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else {
                        val showTimeline = scheduledItems.isNotEmpty()
                        val habitsById =
                            remember(uiState.habits) {
                                uiState.habits.associateBy { it.id }
                            }

                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding =
                                PaddingValues(
                                    start = horizontalMargin,
                                    end = horizontalMargin,
                                    top = 16.dp,
                                    bottom = listBottomPadding,
                                ),
                            verticalArrangement = Arrangement.spacedBy(TimelineItemGap),
                        ) {
                            items(
                                items = scheduledItems,
                                key = {
                                    it.chainId?.let { id -> "c$id" }
                                        ?: it.habitId?.let { id -> "h$id" } ?: 0L
                                },
                            ) { item ->
                                TimelineItemCard(
                                    item = item,
                                    uiState = uiState,
                                    habitsById = habitsById,
                                    onEvent = onEvent,
                                    showTimeline = showTimeline,
                                    // Carries the line over the list's gap to the next scheduled item.
                                    timelineExtension = timelineExtensionAfter(item, scheduledItems),
                                    selectedHabitId = selectedHabitId,
                                    selectedHabitChainId = selectedHabitChainId,
                                    modifier = Modifier.animateItem(),
                                )
                            }

                            items(
                                items = unscheduledItems,
                                key = {
                                    it.chainId?.let { id -> "uc$id" }
                                        ?: it.habitId?.let { id -> "uh$id" } ?: 0L
                                },
                            ) { item ->
                                TimelineItemCard(
                                    item = item,
                                    uiState = uiState,
                                    habitsById = habitsById,
                                    onEvent = onEvent,
                                    showTimeline = false,
                                    selectedHabitId = selectedHabitId,
                                    selectedHabitChainId = selectedHabitChainId,
                                    modifier = Modifier.animateItem(),
                                )
                            }

                            quitHabitsSection(
                                quitHabits = quitHabits,
                                hasItemsAbove = allBuildItems.isNotEmpty(),
                                selectedDate = uiState.selectedDate,
                                onEvent = onEvent,
                                vacationPeriods = uiState.vacationPeriods,
                                selectedHabitId = selectedHabitId,
                            )
                        }
                    }
                }
            }
        }

        // Add Habit Button (Bottom-End)
        if (showAddHabitButton) {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .navigationBarsPadding()
                        .padding(end = 20.dp, bottom = 12.dp),
                contentAlignment = Alignment.BottomEnd,
            ) {
                AnimatedVisibility(
                    visible = !uiState.isLoading,
                    enter =
                        scaleIn(
                            animationSpec =
                                spring(
                                    dampingRatio = Spring.DampingRatioLowBouncy,
                                    stiffness = Spring.StiffnessLow,
                                ),
                        ),
                    exit = scaleOut(),
                ) {
                    AddHabitFab(
                        onClick = { onEvent(RoutinesContract.UiEvent.ShowHabitBottomSheet()) },
                    )
                }
            }
        }
    }
}

private val TimelineItemGap = 8.dp

private fun timelineExtensionAfter(
    item: RoutinesContract.TimelineItem,
    scheduledItems: List<RoutinesContract.TimelineItem>,
): Dp = if (item == scheduledItems.lastOrNull()) 0.dp else TimelineItemGap

@Composable
@Suppress("LongMethod")
internal fun TimelineItemCard(
    item: RoutinesContract.TimelineItem,
    uiState: RoutinesContract.UiState,
    habitsById: Map<Long, Habit>,
    onEvent: (RoutinesContract.UiEvent) -> Unit,
    showTimeline: Boolean,
    modifier: Modifier = Modifier,
    timelineExtension: Dp = 0.dp,
    selectedHabitId: Long? = null,
    selectedHabitChainId: Long? = null,
) {
    Box(modifier = modifier) {
        if (item.isChain && item.chainId != null) {
            // Resolve latest chain data from uiState — decoupled from the stable timelineItems list
            val habitChain =
                remember(uiState.habitChains, item.chainId) {
                    uiState.habitChains.find { it.id == item.chainId }
                }
            if (habitChain != null) {
                val chainHabits =
                    remember(habitsById, habitChain.habitIds) {
                        habitChain.habitIds.mapNotNull { habitsById[it] }
                    }

                val timeLabel =
                    habitChain.periodicReminder?.let { reminder ->
                        formatTime(reminder.hour, reminder.minute)
                    }

                HabitChainCard(
                    habitChain = habitChain,
                    chainHabits = chainHabits,
                    selectedDate = uiState.selectedDate,
                    isSelected = habitChain.id == selectedHabitChainId,
                    selectedHabitId = selectedHabitId,
                    onEdit = { onEvent(RoutinesContract.UiEvent.ShowHabitChainBottomSheet(habitChain)) },
                    onAddHabit = {
                        onEvent(
                            RoutinesContract.UiEvent.ShowHabitBottomSheet(
                                chainId = habitChain.id,
                            ),
                        )
                    },
                    onHabitToggle = { habitId, isCompleted ->
                        onEvent(RoutinesContract.UiEvent.ToggleHabitCompletion(habitId, isCompleted))
                    },
                    onHabitClick = { habitId ->
                        val habit = chainHabits.find { it.id == habitId }
                        if (habit != null) {
                            onEvent(RoutinesContract.UiEvent.ShowHabitBottomSheet(habit))
                        }
                    },
                    timeLabel = timeLabel,
                    showTimeline = showTimeline,
                    timelineExtension = timelineExtension,
                )
            }
        } else if (!item.isChain && item.habitId != null) {
            val habit =
                remember(habitsById, item.habitId) {
                    habitsById[item.habitId]
                }
            if (habit != null) {
                val timeLabel =
                    habit.reminderDate?.let { reminder ->
                        formatTime(reminder.hour, reminder.minute)
                    }

                HabitCard(
                    habit = habit,
                    selectedDate = uiState.selectedDate,
                    onEdit = { onEvent(RoutinesContract.UiEvent.ShowHabitBottomSheet(habit)) },
                    onDelete = { onEvent(RoutinesContract.UiEvent.ShowDeleteHabitConfirmation(habit)) },
                    onToggle = { habitId, isCompleted ->
                        onEvent(RoutinesContract.UiEvent.ToggleHabitCompletion(habitId, isCompleted))
                    },
                    timeLabel = timeLabel,
                    showTimeline = showTimeline,
                    timelineExtension = timelineExtension,
                    isSelected = habit.id == selectedHabitId,
                )
            }
        }
    }
}

@Composable
internal fun EmptyDayMessage(
    selectedDate: LocalDate,
    modifier: Modifier = Modifier,
) {
    val today = remember { Clock.System.todayIn(TimeZone.currentSystemDefault()) }
    val dayLabel =
        when (selectedDate) {
            today -> {
                stringResource(R.string.today)
            }

            else -> {
                val resId =
                    when (selectedDate.dayOfWeek) {
                        kotlinx.datetime.DayOfWeek.MONDAY -> R.string.monday
                        kotlinx.datetime.DayOfWeek.TUESDAY -> R.string.tuesday
                        kotlinx.datetime.DayOfWeek.WEDNESDAY -> R.string.wednesday
                        kotlinx.datetime.DayOfWeek.THURSDAY -> R.string.thursday
                        kotlinx.datetime.DayOfWeek.FRIDAY -> R.string.friday
                        kotlinx.datetime.DayOfWeek.SATURDAY -> R.string.saturday
                        kotlinx.datetime.DayOfWeek.SUNDAY -> R.string.sunday
                    }
                stringResource(resId)
            }
        }

    Box(
        modifier = modifier,
        contentAlignment =
            BiasAlignment(
                horizontalBias = 0f,
                verticalBias = TempoSpacing.CENTERED_CONTENT_VERTICAL_BIAS,
            ),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp),
        ) {
            Text(
                text = stringResource(R.string.no_habits_for_day, dayLabel),
                style = MaterialTheme.typography.emptyStateTitle,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.tap_to_create_first_habit),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                textAlign = TextAlign.Center,
            )
        }
    }
}

private fun LazyListScope.quitHabitsSection(
    quitHabits: List<Habit>,
    hasItemsAbove: Boolean,
    selectedDate: LocalDate,
    onEvent: (RoutinesContract.UiEvent) -> Unit,
    vacationPeriods: List<VacationPeriod>,
    selectedHabitId: Long? = null,
) {
    if (quitHabits.isEmpty()) return
    if (hasItemsAbove) {
        item(key = "quit_top_spacer") {
            Spacer(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(16.dp)
                        .animateItem(),
            )
        }
    }
    item(key = "quit_separator") {
        QuitHabitsSeparator(modifier = Modifier.animateItem())
    }
    items(items = quitHabits, key = { "q${it.id}" }) { habit ->
        QuitHabitCard(
            habit = habit,
            selectedDate = selectedDate,
            vacationPeriods = vacationPeriods,
            onEdit = { onEvent(RoutinesContract.UiEvent.ShowHabitBottomSheet(habit)) },
            isSelected = habit.id == selectedHabitId,
            onToggle = { habitId, isCompleted ->
                onEvent(RoutinesContract.UiEvent.ToggleHabitCompletion(habitId, isCompleted))
            },
            modifier = Modifier.animateItem(),
        )
    }
}

@Composable
internal fun QuitHabitsSeparator(modifier: Modifier = Modifier) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            color = MaterialTheme.colorScheme.secondaryContainer,
            shape = RoundedCornerShape(16.dp),
            tonalElevation = 3.dp,
            modifier = Modifier.padding(end = 12.dp),
        ) {
            Text(
                text = stringResource(R.string.quit_habits_section_title),
                style =
                    MaterialTheme.typography.sectionHeader,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )
        }
        WavyDivider(modifier = Modifier.weight(1f))
    }
}

internal fun formatTime(
    hour: Int,
    minute: Int,
): String = "${hour.toString().padStart(2, '0')}:${minute.toString().padStart(2, '0')}"

@Composable
private fun AddHabitFab(onClick: () -> Unit) {
    val (buttonInteractionSource, buttonCornerRadius) = rememberPressableButtonAnimation()

    FloatingActionButton(
        onClick = onClick,
        modifier = Modifier.size(76.dp),
        shape = RoundedCornerShape(buttonCornerRadius.value),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        contentColor = MaterialTheme.colorScheme.onSurface,
        elevation =
            FloatingActionButtonDefaults.elevation(
                defaultElevation = 4.dp,
                pressedElevation = 8.dp,
            ),
        interactionSource = buttonInteractionSource,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_routine),
            contentDescription = stringResource(R.string.add_habit),
            modifier = Modifier.size(30.dp),
        )
    }
}
