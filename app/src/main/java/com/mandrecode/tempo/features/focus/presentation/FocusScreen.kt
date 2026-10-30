package com.mandrecode.tempo.features.focus.presentation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mandrecode.tempo.R
import com.mandrecode.tempo.core.ui.adaptive.DockedEditorPane
import com.mandrecode.tempo.core.ui.adaptive.SheetPlacement
import com.mandrecode.tempo.core.ui.components.ExpressiveSnackbarHost
import com.mandrecode.tempo.core.ui.components.HoldSplashWhileFirstLoad
import com.mandrecode.tempo.core.ui.glass.ambientBackground
import com.mandrecode.tempo.core.ui.navigation.FocusFloatingBarState
import com.mandrecode.tempo.core.ui.navigation.adaptiveScreenContentLayout
import com.mandrecode.tempo.core.ui.navigation.animatedDockedContentBias
import com.mandrecode.tempo.core.ui.navigation.floatingNavigationSnackbarBottomPadding
import com.mandrecode.tempo.core.ui.navigation.floatingRailContentClearance
import com.mandrecode.tempo.features.focus.presentation.components.PlanTasksSheet
import com.mandrecode.tempo.features.focus.presentation.components.ReplaceSessionDialog
import com.mandrecode.tempo.features.tasks.presentation.TasksViewModel
import com.mandrecode.tempo.features.tasks.presentation.showTasksEffect
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FocusScreen(
    onOpenSession: () -> Unit,
    modifier: Modifier = Modifier,
    /**
     * Told whether today is paused, so the title can carry the same palm Routines does. Vacation is
     * one toggle across the app, and a surface with its own streak has to say when that streak is
     * being counted on holiday terms.
     */
    topBar: @Composable (isVacationModeActive: Boolean) -> Unit = {},
    /** Hands the floating bar its add action, which opens the task form over Focus (#434). */
    onFloatingBarStateChange: (FocusFloatingBarState) -> Unit = {},
    viewModel: FocusViewModel = viewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    HoldSplashWhileFirstLoad(isLoading = uiState.isLoading)
    // On entering the tab and on every return to the app: the view model is created at startup,
    // so this is where Focus catches up with a new day and recounts (#438).
    LifecycleResumeEffect(viewModel) {
        viewModel.onTabShown()
        onPauseOrDispose { }
    }
    val onAddTask = remember(viewModel) { { viewModel.onEvent(FocusContract.UiEvent.AddTask) } }
    SideEffect { onFloatingBarStateChange(FocusFloatingBarState(onAddTask = onAddTask)) }
    val railContentClearance = floatingRailContentClearance()
    val snackbarHostState = remember { SnackbarHostState() }

    val editor = focusEditorHost(uiState = uiState, onEvent = viewModel::onEvent)
    val contentBias = animatedDockedContentBias(editor.isDocked)
    var contentBlockTop by remember { mutableStateOf(0.dp) }

    FocusEffects(
        uiEffect = viewModel.uiEffect,
        snackbarHostState = snackbarHostState,
        onOpenSession = onOpenSession,
        onEvent = viewModel::onEvent,
    )
    TaskEditorEffects(isEditorOpen = uiState.taskEditor != null, snackbarHostState = snackbarHostState)

    Row(
        modifier =
            modifier
                .fillMaxSize()
                // Explicit fill rather than relying on the root Surface: on rail layouts
                // adaptiveScreenContentLayout insets the Scaffold to clear the floating rail, and
                // the docked editor leaves a gutter and margins around its card, exposing this
                // Row's background in those gaps. It must match the Scaffold's own colour, matching
                // what Routines and Tasks already do. The page's one ambient backdrop; the Scaffold
                // and content inside it stay transparent.
                .ambientBackground(),
    ) {
        Scaffold(
            modifier =
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .adaptiveScreenContentLayout(railClearance = railContentClearance, horizontalBias = contentBias),
            containerColor = Color.Transparent,
            contentWindowInsets = WindowInsets(0),
            topBar = { topBar(uiState.isVacationModeActive) },
        ) { innerPadding ->
            Box(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
                FocusContent(
                    uiState = uiState,
                    onEvent = editor.onContentEvent,
                    selectedTaskId = editor.selectedTaskId,
                    onContentBlockTopChange = { contentBlockTop = it },
                )

                FocusSnackbarHost(snackbarHostState)
            }
        }
        DockedEditorPane(visible = editor.isDocked, contentBlockTop = contentBlockTop) {
            // Only while docking is possible: past the breakpoint the editor moves to the sheet in
            // the same frame the pane starts its exit, and must not be in both places.
            if (editor.canDock) {
                editor.shownTarget?.let { editor.content(it, SheetPlacement.DockedPane) }
            }
        }
    }

    FocusOverlays(uiState = uiState, editor = editor, onEvent = viewModel::onEvent)
}

/**
 * Hosted inside the content rather than as the Scaffold's own snackbarHost, the way Tasks and
 * Routines do it: the floating bar owns the bottom of a compact window, and a snackbar left where
 * Material puts it comes up underneath it.
 */
@Composable
private fun FocusSnackbarHost(snackbarHostState: SnackbarHostState) {
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .navigationBarsPadding()
                .padding(bottom = floatingNavigationSnackbarBottomPadding()),
        contentAlignment = Alignment.BottomCenter,
    ) {
        ExpressiveSnackbarHost(snackbarHostState)
    }
}

/** What opens over the screen: the replace-session prompt, the Plan tasks sheet and a sheet editor. */
@Composable
private fun FocusOverlays(
    uiState: FocusContract.UiState,
    editor: FocusEditorHost,
    onEvent: (FocusContract.UiEvent) -> Unit,
) {
    uiState.pendingStart?.let { pending ->
        ReplaceSessionDialog(pending = pending, onEvent = onEvent)
    }

    // Under the editors, so opening a task from the sheet stacks the form over it rather than
    // replacing it — closing the form puts you back where you were planning from.
    uiState.planSheet?.let { planSheet ->
        PlanTasksSheet(state = planSheet, onEvent = onEvent)
    }

    if (!editor.canDock) {
        uiState.taskEditor?.let { editor.content(it, SheetPlacement.BottomSheet) }
    }
}

/**
 * The screen's one-shot events: opening the session, and the offer to take a batch of planning
 * back.
 *
 * The undo lives out here rather than in the sheet because it is about what the sheet did, and by
 * the time there is anything to say the sheet has closed.
 */
@Composable
private fun FocusEffects(
    uiEffect: Flow<FocusContract.UiEffect>,
    snackbarHostState: SnackbarHostState,
    onOpenSession: () -> Unit,
    onEvent: (FocusContract.UiEvent) -> Unit,
) {
    val undoLabel = stringResource(R.string.undo)
    val currentOnOpenSession by rememberUpdatedState(onOpenSession)
    val currentOnEvent by rememberUpdatedState(onEvent)
    // The count is only known when the effect arrives, so the message has to be formatted inside
    // the collector rather than read as a resource up here. Kept current rather than captured: the
    // effect does not restart on a configuration change, and a closed-over Resources would go on
    // answering in the locale the screen opened in.
    val currentResources by rememberUpdatedState(LocalContext.current.resources)

    LaunchedEffect(uiEffect, snackbarHostState) {
        uiEffect.collect { effect ->
            when (effect) {
                FocusContract.UiEffect.OpenSessionScreen -> currentOnOpenSession()

                // Launched rather than awaited. A snackbar stands for seconds, and this collector
                // is also where navigation arrives — awaiting it would leave a session started
                // from Up next running with no screen opening until the notice about the planning
                // had gone. Concurrent shows still queue on the host's own mutex, so nothing is
                // lost by not waiting here.
                is FocusContract.UiEffect.PlanBatchConfirmed ->
                    launch {
                        val result =
                            snackbarHostState.showSnackbar(
                                message =
                                    currentResources.getQuantityString(
                                        R.plurals.plan_tasks_undo_message,
                                        effect.count,
                                        effect.count,
                                    ),
                                actionLabel = undoLabel,
                                withDismissAction = true,
                            )
                        if (result == SnackbarResult.ActionPerformed) {
                            // The batch came with the offer, so it is still the one this snackbar
                            // was raised for even if another sheet has been and gone since.
                            currentOnEvent(FocusContract.UiEvent.UndoPlanBatch(effect.batch))
                        }
                    }
            }
        }
    }
}

/**
 * Focus hosts its own Tasks view model for the task editor, created only once an editor has opened
 * (see FocusTaskEditor). From then on its messages come out here, on Focus's snackbar: a save or a
 * reminder that fails after the form has closed still has to be reported.
 */
@Composable
private fun TaskEditorEffects(
    isEditorOpen: Boolean,
    snackbarHostState: SnackbarHostState,
) {
    var hasOpenedTaskEditor by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(isEditorOpen) {
        if (isEditorOpen) hasOpenedTaskEditor = true
    }
    if (hasOpenedTaskEditor) {
        CollectTaskEditorEffects(snackbarHostState)
    }
}

/** The view model here is the same instance FocusTaskEditor uses: same owner, same class. */
@Composable
private fun CollectTaskEditorEffects(
    snackbarHostState: SnackbarHostState,
    viewModel: TasksViewModel = viewModel(),
) {
    val context = LocalContext.current
    LaunchedEffect(viewModel) {
        viewModel.uiEffect.collect { effect ->
            showTasksEffect(effect, context, snackbarHostState, viewModel::onEvent)
        }
    }
}
