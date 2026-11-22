package com.mandrecode.tempo.core.ui.components

import android.os.Build
import androidx.activity.ExperimentalActivityApi
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.dismiss
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.graphics.drawable.toDrawable
import androidx.core.view.WindowCompat
import com.mandrecode.tempo.R
import com.mandrecode.tempo.core.ui.glass.glassPanel
import com.mandrecode.tempo.core.ui.glass.rememberBackdropBlur
import com.mandrecode.tempo.core.ui.theme.LocalIsDarkTheme
import com.mandrecode.tempo.core.ui.theme.LocalTempoGlass
import com.mandrecode.tempo.core.ui.theme.TempoMotionTokens
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import androidx.compose.foundation.layout.Box as LayoutBox

internal const val DISMISS_THRESHOLD_FRACTION = 0.3f
private const val SHEET_SCRIM_ALPHA = 0.32f
private val SHEET_SHADOW_ELEVATION = 1.dp

/**
 * Rounded at the top only, with the same 28 dp radius as the screens' content block beside it,
 * which also runs to the bottom edge.
 */
private val DockedSheetShape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
private val SHEET_HANDLE_TOUCH_TARGET_SIZE = 48.dp
private val SHEET_HANDLE_CONTENT_INSET = 24.dp
private val SHEET_HANDLE_EDGE_PADDING = 8.dp
private val SHEET_HANDLE_LENGTH = 32.dp
private val SHEET_HANDLE_THICKNESS = 4.dp
private val SHEET_MAX_WIDTH = 640.dp

internal enum class TempoModalSheetDirection {
    Top,
    Bottom,
    End,
}

@Composable
internal fun TempoModalSheet(
    direction: TempoModalSheetDirection,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    hasUnsavedChanges: Boolean = false,
    content: @Composable ColumnScope.(onRequestDismiss: () -> Unit) -> Unit,
) {
    val state = rememberTempoModalSheetState(direction, onDismissRequest, hasUnsavedChanges)

    if (state.showDiscardDialog.value) {
        DiscardChangesConfirmDialog(
            onCancelDiscard = { state.showDiscardDialog.value = false },
            onConfirmDiscard = {
                state.showDiscardDialog.value = false
                state.forceDismiss.value = true
                state.animateDismiss()
            },
        )
    }

    LaunchedEffect(state.offset) {
        state.offset.animateTo(0f, animationSpec = tween(TempoMotionTokens.DURATION_SHEET_MILLIS))
    }

    Dialog(
        onDismissRequest = state.onRequestDismiss,
        properties =
            DialogProperties(
                usePlatformDefaultWidth = false,
                decorFitsSystemWindows = false,
                dismissOnBackPress = false,
                dismissOnClickOutside = false,
            ),
    ) {
        TempoModalSheetDialogContent(
            state = state,
            modifier = modifier,
            content = content,
        )
    }
}

/**
 * Non-modal counterpart used by very large windows. It deliberately keeps dismissal on the same
 * guarded predictive-back path as modal sheets while leaving the adjacent content interactive.
 */
@Composable
internal fun TempoDockedSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    hasUnsavedChanges: Boolean = false,
    dismissRequestKey: Int = 0,
    content: @Composable ColumnScope.(onRequestDismiss: () -> Unit) -> Unit,
) {
    val currentOnDismissRequest by rememberUpdatedState(onDismissRequest)
    val currentHasUnsavedChanges by rememberUpdatedState(hasUnsavedChanges)
    val initialDismissRequestKey = remember { dismissRequestKey }
    val showDiscardDialog = remember { androidx.compose.runtime.mutableStateOf(false) }
    val requestDismiss: () -> Unit = {
        if (currentHasUnsavedChanges) {
            showDiscardDialog.value = true
        } else {
            currentOnDismissRequest()
        }
    }

    LaunchedEffect(dismissRequestKey) {
        if (dismissRequestKey != initialDismissRequestKey) {
            requestDismiss()
        }
    }

    if (showDiscardDialog.value) {
        DiscardChangesConfirmDialog(
            onCancelDiscard = { showDiscardDialog.value = false },
            onConfirmDiscard = {
                showDiscardDialog.value = false
                currentOnDismissRequest()
            },
        )
    }

    TempoModalSheetPredictiveBackHandler(
        onProgress = {},
        onRestore = {},
        onDismiss = requestDismiss,
    )

    // statusBarsPadding sits on this outer Box, not the Surface below, so the card's rounded
    // shape and color never paint behind the status bar; DockedEditorPane adds the header height
    // on top of that so the card starts exactly where the main list's content block does. The
    // surface also mirrors that block in every other way — colorScheme.surface (not
    // surfaceContainerHigh, unlike the modal bottom sheet), only the top corners rounded, running
    // to the bottom edge and casting no shadow — so the docked pane reads as a continuation of the
    // same content plane rather than an elevated overlay floating beside it.
    Box(modifier = modifier.fillMaxSize().statusBarsPadding()) {
        Surface(
            modifier =
                Modifier
                    .fillMaxSize()
                    .glassPanel(DockedSheetShape, MaterialTheme.colorScheme.surface)
                    .semantics {
                        dismiss {
                            requestDismiss()
                            true
                        }
                    },
            shape = DockedSheetShape,
            color = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ) {
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .navigationBarsPadding()
                        .imePadding(),
            ) {
                content(requestDismiss)
            }
        }
    }
}

@Composable
private fun TempoModalSheetDialogContent(
    state: TempoModalSheetState,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.(onRequestDismiss: () -> Unit) -> Unit,
) {
    TempoModalSheetPredictiveBackHandler(
        onProgress = state::handlePredictiveBackProgress,
        onRestore = state::restore,
        onDismiss = state.onRequestDismiss,
    )
    TempoModalSheetWindowEffects()
    // The blur and the scrim both follow how far the sheet is open, so they grow as it slides in,
    // ease off under a drag and are gone once it has slid away, never switching on or off at once.
    val backdropBlurred = rememberBackdropBlur { state.openFraction() }
    val glass = LocalTempoGlass.current
    // With the app blurred behind the window, depth does most of the separating, so the scrim only
    // needs to dim; without it, the scrim is the whole separation and keeps its full strength.
    val scrimAlpha = if (backdropBlurred) SHEET_SCRIM_ALPHA * glass.blurredScrimFraction else SHEET_SCRIM_ALPHA
    val scrimColor = MaterialTheme.colorScheme.scrim

    val focusManager = LocalFocusManager.current
    LaunchedEffect(state.showDiscardDialog.value) {
        if (state.showDiscardDialog.value) {
            focusManager.clearFocus()
        }
    }
    LaunchedEffect(state.dismissing.value) {
        if (state.dismissing.value) {
            focusManager.clearFocus()
        }
    }

    Box(
        modifier =
            modifier
                .fillMaxSize()
                .drawBehind { drawRect(scrimColor, alpha = scrimAlpha * state.openFraction()) }
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = state.onRequestDismiss,
                ),
    ) {
        TempoModalSheetSurface(
            state = state,
            glassy = backdropBlurred,
            content = content,
        )
    }
}

@Composable
private fun BoxScope.TempoModalSheetSurface(
    state: TempoModalSheetState,
    glassy: Boolean,
    content: @Composable ColumnScope.(onRequestDismiss: () -> Unit) -> Unit,
) {
    val layoutFactor = currentLayoutFactor()
    // Fixed for the session: [glassy] only says whether this device blurs at all.
    val sheetColor =
        MaterialTheme.colorScheme.surfaceContainerHigh.let {
            if (glassy) it.copy(alpha = LocalTempoGlass.current.sheetAlpha) else it
        }
    Surface(
        modifier =
            Modifier
                .then(state.sizeModifier())
                .onSizeChanged { size ->
                    state.isExpandedToStatusBar.value =
                        !state.direction.isHorizontal &&
                        size.height >= state.dismissAxisSizePx.roundToInt()
                }.align(state.direction.alignment)
                .offset { state.direction.translationOffset(state.offset.value.roundToInt(), layoutFactor) }
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {},
                ).then(if (state.direction == TempoModalSheetDirection.Bottom) Modifier.imePadding() else Modifier),
        shape = state.direction.shape,
        color = sheetColor,
        contentColor = MaterialTheme.colorScheme.onSurface,
        // A shadow under a translucent surface shows through it as a grey cast; the rim and the
        // blurred backdrop carry the edge instead.
        shadowElevation = if (glassy) 0.dp else SHEET_SHADOW_ELEVATION,
        border = null,
    ) {
        TempoModalSheetColumn(
            state = state,
            content = content,
        )
    }
}

@Composable
private fun TempoModalSheetState.sizeModifier(): Modifier =
    if (direction.isHorizontal) {
        Modifier
            .width(with(LocalDensity.current) { dismissAxisSizePx.toDp() })
            .fillMaxHeight()
    } else {
        Modifier
            .widthIn(max = SHEET_MAX_WIDTH)
            .fillMaxWidth()
            .heightIn(max = maxSheetHeight)
    }

@Composable
private fun TempoModalSheetColumn(
    state: TempoModalSheetState,
    content: @Composable ColumnScope.(onRequestDismiss: () -> Unit) -> Unit,
) {
    LayoutBox(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .then(state.statusBarPaddingModifier)
                    .then(state.navigationBarPaddingModifier)
                    .then(state.horizontalContentPaddingModifier),
        ) {
            if (state.direction == TempoModalSheetDirection.Bottom) {
                Spacer(modifier = Modifier.height(SHEET_HANDLE_CONTENT_INSET))
            }
            content(state.onRequestDismiss)
            if (state.direction == TempoModalSheetDirection.Top) {
                Spacer(modifier = Modifier.height(SHEET_HANDLE_CONTENT_INSET))
            }
        }
        TempoModalSheetDragHandle(
            state = state,
            modifier =
                Modifier.align(
                    when (state.direction) {
                        TempoModalSheetDirection.Bottom -> Alignment.TopCenter
                        TempoModalSheetDirection.Top -> Alignment.BottomCenter
                        TempoModalSheetDirection.End -> Alignment.CenterStart
                    },
                ),
        )
    }
}

private val TempoModalSheetState.statusBarPaddingModifier: Modifier
    @Composable
    get() =
        if (direction != TempoModalSheetDirection.Bottom || isExpandedToStatusBar.value) {
            Modifier.windowInsetsPadding(WindowInsets.statusBars)
        } else {
            Modifier
        }

private val TempoModalSheetState.navigationBarPaddingModifier: Modifier
    @Composable
    get() =
        when (direction) {
            TempoModalSheetDirection.Bottom -> Modifier.navigationBarsPadding()
            TempoModalSheetDirection.End ->
                Modifier.windowInsetsPadding(
                    WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.End),
                )

            TempoModalSheetDirection.Top -> Modifier
        }

/**
 * Side sheets are full-height, so the keyboard insets their content instead of moving the sheet,
 * and the drag handle occupies the start edge instead of the top/bottom.
 */
private val TempoModalSheetState.horizontalContentPaddingModifier: Modifier
    get() =
        if (direction.isHorizontal) {
            Modifier
                .padding(start = SHEET_HANDLE_CONTENT_INSET)
                .imePadding()
        } else {
            Modifier
        }

@Composable
private fun currentLayoutFactor(): Int = if (LocalLayoutDirection.current == LayoutDirection.Rtl) -1 else 1

private fun Modifier.pointerInputForSheetDrag(
    state: TempoModalSheetState,
    scope: CoroutineScope,
    layoutFactor: Int,
): Modifier =
    pointerInput(state.direction, state.dismissAxisSizePx, layoutFactor) {
        var snapJob: Job? = null
        detectSheetDragGestures(
            direction = state.direction,
            layoutFactor = layoutFactor,
            onDragEnd = {
                snapJob?.cancel()
                if (
                    state.direction.shouldDismiss(
                        offset = state.offset.value,
                        axisSizePx = state.dismissAxisSizePx,
                    )
                ) {
                    state.onRequestDismiss()
                } else {
                    snapJob = scope.launch { state.restore() }
                }
            },
            onDragCancel = {
                snapJob?.cancel()
                snapJob = scope.launch { state.restore() }
            },
            onDrag = { dragAmount ->
                val newOffset =
                    state.direction.coerceDragOffset(
                        offset = state.offset.value + dragAmount,
                        axisSizePx = state.dismissAxisSizePx,
                    )
                snapJob?.cancel()
                snapJob = scope.launch { state.offset.snapTo(newOffset) }
            },
        )
    }

@Composable
private fun TempoModalSheetWindowEffects() {
    val isDarkTheme = LocalIsDarkTheme.current
    val view = LocalView.current
    val dialogWindow = (view.parent as? DialogWindowProvider)?.window
    SideEffect {
        dialogWindow?.let { window ->
            window.setBackgroundDrawable(0.toDrawable())
            window.setDimAmount(0f)
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !isDarkTheme
                isAppearanceLightNavigationBars = !isDarkTheme
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                window.isNavigationBarContrastEnforced = false
            }
        }
    }
}

@Composable
private fun TempoModalSheetDragHandle(
    state: TempoModalSheetState,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val layoutFactor = currentLayoutFactor()
    val dismissSheetLabel = stringResource(R.string.dismiss_sheet)
    val horizontal = state.direction.isHorizontal
    LayoutBox(
        modifier =
            modifier
                .then(
                    if (horizontal) {
                        Modifier
                            .fillMaxHeight()
                            .width(SHEET_HANDLE_TOUCH_TARGET_SIZE)
                    } else {
                        Modifier
                            .fillMaxWidth()
                            .height(SHEET_HANDLE_TOUCH_TARGET_SIZE)
                    },
                ).semantics {
                    contentDescription = dismissSheetLabel
                    dismiss {
                        state.onRequestDismiss()
                        true
                    }
                }.pointerInputForSheetDrag(state, scope, layoutFactor),
        contentAlignment =
            when (state.direction) {
                TempoModalSheetDirection.Bottom -> Alignment.TopCenter
                TempoModalSheetDirection.Top -> Alignment.BottomCenter
                TempoModalSheetDirection.End -> Alignment.CenterStart
            },
    ) {
        Surface(
            modifier =
                Modifier
                    .then(
                        when (state.direction) {
                            TempoModalSheetDirection.Bottom -> Modifier.padding(top = SHEET_HANDLE_EDGE_PADDING)
                            TempoModalSheetDirection.Top -> Modifier.padding(bottom = SHEET_HANDLE_EDGE_PADDING)
                            TempoModalSheetDirection.End -> Modifier.padding(start = SHEET_HANDLE_EDGE_PADDING)
                        },
                    ).size(
                        width = if (horizontal) SHEET_HANDLE_THICKNESS else SHEET_HANDLE_LENGTH,
                        height = if (horizontal) SHEET_HANDLE_LENGTH else SHEET_HANDLE_THICKNESS,
                    ),
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
            shape = CircleShape,
        ) {}
    }
}

@OptIn(ExperimentalActivityApi::class)
@Composable
private fun TempoModalSheetPredictiveBackHandler(
    onProgress: suspend (Float) -> Unit,
    onRestore: suspend () -> Unit,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()

    PredictiveBackHandler {
        try {
            it.collect { backEvent ->
                onProgress(backEvent.progress)
            }
            onDismiss()
        } catch (exception: CancellationException) {
            scope.launch { onRestore() }
            throw exception
        }
    }
}
