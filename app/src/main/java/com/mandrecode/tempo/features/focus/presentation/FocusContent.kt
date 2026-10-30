package com.mandrecode.tempo.features.focus.presentation

import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mandrecode.tempo.R
import com.mandrecode.tempo.core.ui.components.FirstLoadCrossfade
import com.mandrecode.tempo.core.ui.components.TempoLinkButton
import com.mandrecode.tempo.core.ui.components.TempoLoadingIndicator
import com.mandrecode.tempo.core.ui.components.WavyDivider
import com.mandrecode.tempo.core.ui.glass.glassPanel
import com.mandrecode.tempo.core.ui.navigation.floatingNavigationBottomClearancePadding
import com.mandrecode.tempo.core.ui.theme.TempoSpacing
import com.mandrecode.tempo.core.ui.theme.emptyStateTitle
import com.mandrecode.tempo.core.ui.theme.groupLabel
import com.mandrecode.tempo.core.ui.theme.sectionHeader
import com.mandrecode.tempo.features.focus.domain.model.FocusAgendaItem
import com.mandrecode.tempo.features.focus.domain.model.TaskFocusToday
import com.mandrecode.tempo.features.focus.presentation.components.FocusSummaryHero
import com.mandrecode.tempo.features.focus.presentation.components.RunningSessionCard
import com.mandrecode.tempo.features.focus.presentation.components.SessionFinishedSheet
import com.mandrecode.tempo.features.focus.presentation.components.StartSessionButton
import com.mandrecode.tempo.features.focus.presentation.components.UpNextCard
import com.mandrecode.tempo.features.focus.presentation.components.WorkedOnActions
import com.mandrecode.tempo.features.focus.presentation.components.upNextMetadata
import com.mandrecode.tempo.features.tasks.presentation.components.cards.TaskItem
import kotlinx.collections.immutable.ImmutableList

private val ContentBlockTopCornerRadius = 28.dp
private val ContentBlockShape =
    RoundedCornerShape(topStart = ContentBlockTopCornerRadius, topEnd = ContentBlockTopCornerRadius)

/** The two levels of muting Tasks and Routines use for their own empty states. */
private const val EMPTY_STATE_TITLE_ALPHA = 0.6f
private const val EMPTY_STATE_MESSAGE_ALPHA = 0.4f

@Composable
fun FocusContent(
    uiState: FocusContract.UiState,
    onEvent: (FocusContract.UiEvent) -> Unit,
    modifier: Modifier = Modifier,
    /** The task open in a docked editor beside the agenda, shown as selected (#502). */
    selectedTaskId: Long? = null,
    /** Reports how far below the top the content block starts, so a docked editor can line up. */
    onContentBlockTopChange: (Dp) -> Unit = {},
) {
    val density = LocalDensity.current
    val listBottomPadding = floatingNavigationBottomClearancePadding(defaultPadding = 16.dp)

    Box(
        // Transparent: the page's ambient backdrop is painted once by the screen around this
        // (and by the preview hosts), so the panel's translucency picks up the same glows.
        modifier = modifier.fillMaxSize(),
    ) {
        FirstLoadCrossfade(
            isLoading = uiState.isLoading,
            loading = { TempoLoadingIndicator(message = stringResource(R.string.focus)) },
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                FocusSummaryHero(
                    today = uiState.today,
                    streakDays = uiState.streakDays,
                    history = uiState.history,
                    scheduledCount = uiState.scheduledCount,
                    completedCount = uiState.completedCount,
                    progress = uiState.progress,
                    band = uiState.headlineBand,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .onSizeChanged { onContentBlockTopChange(with(density) { it.height.toDp() }) }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                )

                Box(
                    modifier =
                        Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            // clip before background so list overscroll cannot draw past the seam.
                            .clip(ContentBlockShape)
                            .glassPanel(ContentBlockShape, MaterialTheme.colorScheme.surface),
                ) {
                    uiState.finishedSession?.let { finished ->
                        SessionFinishedSheet(
                            finished = finished,
                            nextSessionMinutes = uiState.defaultSessionLengthMinutes,
                            onEvent = onEvent,
                        )
                    }

                    if (uiState.isDayEmpty) {
                        FocusEmptyState(
                            undatedTaskCount = uiState.undatedTaskCount,
                            onUndatedClick = { onEvent(FocusContract.UiEvent.UndatedTasksClicked) },
                        )
                    } else {
                        // Render the agenda once its current day is known.
                        uiState.today?.let {
                            FocusAgendaList(
                                uiState = uiState,
                                onEvent = onEvent,
                                bottomPadding = listBottomPadding,
                                selectedTaskId = selectedTaskId,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FocusAgendaList(
    uiState: FocusContract.UiState,
    onEvent: (FocusContract.UiEvent) -> Unit,
    bottomPadding: Dp,
    selectedTaskId: Long?,
) {
    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = bottomPadding),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        upNextSection(uiState = uiState, onEvent = onEvent)

        // Today above Overdue. Both are the day, but only one of them is the day you are in, and
        // opening Focus onto last week's leftovers put the work you came for below the fold.
        agendaSection(
            key = "today",
            label = { stringResource(R.string.focus_section_today, uiState.visibleToday.size) },
            entries = uiState.visibleToday,
            uiState = uiState,
            onEvent = onEvent,
            selectedTaskId = selectedTaskId,
        )

        agendaSection(
            key = "overdue",
            label = { stringResource(R.string.focus_section_overdue, uiState.visibleOverdue.size) },
            entries = uiState.visibleOverdue,
            uiState = uiState,
            onEvent = onEvent,
            selectedTaskId = selectedTaskId,
        )

        if (uiState.undatedTaskCount > 0) {
            item(key = "undated_footer") {
                UndatedTasksFooter(
                    count = uiState.undatedTaskCount,
                    onClick = { onEvent(FocusContract.UiEvent.UndatedTasksClicked) },
                    modifier = Modifier.animateItem(),
                )
            }
        }
    }
}

/**
 * One headed block of the day. Both sections are the same thing rendered twice, so they are written
 * once — the only difference between them is which list they read and the order they are called in.
 */
private fun LazyListScope.agendaSection(
    key: String,
    label: @Composable () -> String,
    entries: List<FocusAgendaItem>,
    uiState: FocusContract.UiState,
    onEvent: (FocusContract.UiEvent) -> Unit,
    selectedTaskId: Long?,
) {
    if (entries.isEmpty()) return

    item(key = "${key}_header") {
        SectionHeader(label = label(), modifier = Modifier.animateItem())
    }
    items(entries, key = { it.id }) { entry ->
        AgendaRow(
            entry = entry,
            expandedTaskIds = uiState.expandedTaskIds,
            selectedTaskId = selectedTaskId,
            onEvent = onEvent,
            // Rows slide out of each other's way when one grows, the way they do in Routines and
            // Tasks. Without this an expanding card teleported everything below it, which read as
            // the list jumping rather than the card opening.
            modifier = Modifier.animateItem(),
        )
    }
}

/**
 * The spotlight slot: the Up next card, or the same card transformed into the running session.
 * Lifted out of the list builder so neither grows past what fits on a screen.
 */
private fun LazyListScope.upNextSection(
    uiState: FocusContract.UiState,
    onEvent: (FocusContract.UiEvent) -> Unit,
) {
    val session = uiState.session
    val upNext = uiState.upNext
    if (session == null && upNext.isEmpty()) return

    item(key = "up_next_label") {
        Text(
            // The header names what the slot is holding. It said FOCUSING through a break too,
            // which is the same thing the card was doing one level down.
            text =
                stringResource(
                    when {
                        session == null -> R.string.focus_up_next
                        session.isBreak -> R.string.focus_session_break
                        else -> R.string.focus_session_focusing
                    },
                ).uppercase(),
            style = MaterialTheme.typography.groupLabel,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    // One row whatever is happening: the running card is pinned first and the rest of the
    // shortlist stays beside it, so you can still see what is queued without ending what you are
    // doing to look.
    upNextRow(uiState, onEvent)
}

/**
 * The shortlist you swipe through.
 *
 * A row rather than a single card, because the best next thing is not always the one you are ready
 * to do, and the alternative was leaving the screen to find the one you are. Cards stop short of
 * the full width so the next one shows at the edge — the only honest way to say there is more.
 *
 * A running session takes the first place and keeps it: it is the one card you must be able to
 * reach without hunting, and the queue reads as what comes after it.
 */
private fun LazyListScope.upNextRow(
    uiState: FocusContract.UiState,
    onEvent: (FocusContract.UiEvent) -> Unit,
) {
    val session = uiState.session
    val queued = uiState.upNext.filterNot { it.task.id == session?.taskId }

    item(key = "up_next_row") {
        val listState = rememberLazyListState()
        BoxWithConstraints(modifier = Modifier.fillMaxWidth().animateItem()) {
            val cardCount = queued.size + if (session != null) 1 else 0
            val cardWidth = if (cardCount > 1) maxWidth * SINGLE_PEEK_FRACTION else maxWidth
            LazyRow(
                state = listState,
                flingBehavior = rememberSnapFlingBehavior(listState),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                // Cards start on the same line and end wherever their content does. A running
                // session is a deeper card than a queued one, and a task already worked on carries
                // two buttons where an untouched one carries a single pill — one height for all of
                // them meant padding the short ones out to match the tallest thing on screen.
                verticalAlignment = Alignment.Top,
            ) {
                if (session != null) {
                    item(key = "running_session") {
                        RunningSessionCard(
                            session = session,
                            // The running task's own row, not the screen's subject: a sheet open on
                            // some other card must not relabel the one still counting down.
                            subtasks = uiState.runningEntry?.subtasks.orEmpty(),
                            focusToday = uiState.runningEntry?.focusToday ?: TaskFocusToday(),
                            onExpand = { onEvent(FocusContract.UiEvent.OpenSessionScreen) },
                            onPauseResume = {
                                onEvent(
                                    if (session.isPaused) {
                                        FocusContract.UiEvent.ResumeSession
                                    } else {
                                        FocusContract.UiEvent.PauseSession
                                    },
                                )
                            },
                            onStop = { onEvent(FocusContract.UiEvent.StopSession) },
                            onComplete = { onEvent(FocusContract.UiEvent.CompleteSessionTask) },
                            onBackToWork = { onEvent(FocusContract.UiEvent.BackToWork) },
                            modifier = Modifier.width(cardWidth),
                        )
                    }
                }
                items(queued, key = { it.id }) { entry ->
                    UpNextCard(
                        title = entry.displayTitle(),
                        metadata = entry.upNextMetadata(uiState.today),
                        metadataIconRes = R.drawable.ic_flag.takeIf { entry.priority != null },
                        onClick = {
                            onEvent(FocusContract.UiEvent.PreviewUpNext(entry.task.id))
                        },
                        modifier = Modifier.width(cardWidth),
                        trailingContent = {
                            UpNextAction(
                                entry = entry,
                                defaultSessionLengthMinutes = uiState.defaultSessionLengthMinutes,
                                onEvent = onEvent,
                            )
                        },
                    )
                }
            }
        }
    }
}

/**
 * What a queued card offers: starting, or picking back up work that already had a run at it.
 *
 * A task with sessions behind it and no tick is neither untouched nor finished, and offering
 * "Start 25 min" as though nothing had happened lost that — which is exactly what dismissing the
 * break sheet without choosing used to do.
 */
@Composable
private fun UpNextAction(
    entry: FocusAgendaItem.TaskEntry,
    defaultSessionLengthMinutes: Int,
    onEvent: (FocusContract.UiEvent) -> Unit,
) {
    if (entry.sessionsToday > 0) {
        WorkedOnActions(
            onBackToWork = { onEvent(FocusContract.UiEvent.StartSession(taskId = entry.task.id)) },
            onComplete = { onEvent(FocusContract.UiEvent.ToggleTaskCompletion(entry.task)) },
        )
    } else {
        StartSessionButton(
            minutes = defaultSessionLengthMinutes,
            onClick = { onEvent(FocusContract.UiEvent.StartSession(taskId = entry.task.id)) },
        )
    }
}

/** Leaves the next card showing at the edge, so the row reads as a row. */
private const val SINGLE_PEEK_FRACTION = 0.88f

@Composable
private fun AgendaRow(
    entry: FocusAgendaItem,
    expandedTaskIds: ImmutableList<Long>,
    selectedTaskId: Long?,
    onEvent: (FocusContract.UiEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    when (entry) {
        is FocusAgendaItem.TaskEntry ->
            TaskItem(
                modifier = modifier,
                task = entry.task,
                subtasks = entry.subtasks,
                isSelected = entry.task.id == selectedTaskId,
                onToggleCompletion = { onEvent(FocusContract.UiEvent.ToggleTaskCompletion(it)) },
                onEdit = { onEvent(FocusContract.UiEvent.EditTask(it)) },
                // Without both of these the card is pinned open: its default is expanded and its
                // toggle goes nowhere, so the chevron did nothing at all here.
                isSubtasksExpanded = entry.task.id in expandedTaskIds,
                onToggleSubtasksExpansion = {
                    onEvent(FocusContract.UiEvent.ToggleSubtasksExpanded(entry.task.id))
                },
                onAddSubtask = { onEvent(FocusContract.UiEvent.AddSubtask(it)) },
            )
    }
}

@Composable
private fun SectionHeader(
    label: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(top = 12.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.sectionHeader,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(end = 12.dp),
        )
        WavyDivider(modifier = Modifier.weight(1f))
    }
}

/**
 * What is left over from the day: work carrying no date at all.
 *
 * It opens the planning sheet rather than the Tasks tab. Tapping it is someone volunteering to
 * plan, and answering that by moving them to another tab — where the same tasks are scattered
 * through a list sorted for something else — spent their willingness on finding them again.
 *
 * The same control the session screen uses, and a calendar being added to rather than an
 * open-in-new arrow: the icon has to promise what actually happens, and nothing leaves Focus any
 * more — these tasks are about to be put on a day.
 *
 * The label is a verb, not a headcount. "3 tasks without a date" stated a fact and left the reader
 * to work out that the fact was tappable; naming the offer instead means the row asks for the one
 * thing it can actually do something about.
 */
@Composable
private fun UndatedTasksFooter(
    count: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.fillMaxWidth().padding(vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        TempoLinkButton(
            label = pluralStringResource(R.plurals.focus_undated_tasks, count, count),
            iconRes = R.drawable.ic_calendar_add_on,
            onClick = onClick,
            // Decorative now. The label is a verb, so it already says what happens; describing the
            // icon as well would have a screen reader make the same offer twice.
            iconContentDescription = null,
        )
    }
}

/**
 * The empty day, in the same words and the same shape Tasks and Routines use for theirs: the
 * headline style they share, the same two levels of muting, and sitting above the middle rather
 * than dead centre, so an empty Focus reads as the same app as an empty anything else.
 */
@Composable
private fun FocusEmptyState(
    undatedTaskCount: Int,
    onUndatedClick: () -> Unit,
) {
    Box(
        modifier = Modifier.fillMaxSize(),
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
                text = stringResource(R.string.focus_empty_title),
                style = MaterialTheme.typography.emptyStateTitle,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = EMPTY_STATE_TITLE_ALPHA),
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.focus_empty_message),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = EMPTY_STATE_MESSAGE_ALPHA),
                textAlign = TextAlign.Center,
            )
            if (undatedTaskCount > 0) {
                UndatedTasksFooter(count = undatedTaskCount, onClick = onUndatedClick)
            }
        }
    }
}

@Composable
private fun FocusAgendaItem.displayTitle(): String =
    when (this) {
        is FocusAgendaItem.TaskEntry -> task.title
    }
