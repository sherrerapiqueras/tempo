package com.mandrecode.tempo.features.tasks.presentation.components

import android.view.KeyEvent
import androidx.activity.BackEventCompat
import androidx.activity.ComponentActivity
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.TextField
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

private const val TITLE_TAG = "title"
private const val DESCRIPTION_TAG = "description"

/**
 * Mirrors the task sheet's handler order: the sheet's predictive-back handler is composed first and
 * the task editor's handler inside the content, so the editor's handler wins while a field is focused.
 */
class TaskEditorBackHandlerTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private var sheetProgressEvents = 0
    private var sheetDismissals = 0

    // Driven explicitly: whether a test device shows a soft keyboard for a focused field varies.
    private var isKeyboardVisible by mutableStateOf(true)

    private fun renderEditor(interceptsLegacyImeBack: Boolean = false) {
        composeTestRule.setContent {
            PredictiveBackHandler { progress ->
                progress.collect { sheetProgressEvents++ }
                sheetDismissals++
            }
            val focusState = remember { TaskEditorFocusState() }
            TaskEditorBackHandler(focusState = focusState, isKeyboardVisible = isKeyboardVisible)
            val focusManager = LocalFocusManager.current
            val focusConfig =
                remember {
                    TaskBottomSheetFocusConfig(
                        titleFocusRequester = FocusRequester(),
                        focusState = focusState,
                        clearEditorFocus = { focusManager.clearFocus() },
                        interceptsLegacyImeBack = interceptsLegacyImeBack,
                    )
                }
            Column {
                var title by remember { mutableStateOf("") }
                var description by remember { mutableStateOf("") }
                TextField(
                    value = title,
                    onValueChange = { title = it },
                    modifier = Modifier.testTag(TITLE_TAG).taskTitleFocus(focusConfig),
                )
                TextField(
                    value = description,
                    onValueChange = { description = it },
                    modifier = Modifier.testTag(DESCRIPTION_TAG).taskDescriptionFocus(focusConfig),
                )
            }
        }
    }

    private fun pressBack() {
        // Let the focus change recompose so the editor handler is enabled before back arrives.
        composeTestRule.waitForIdle()
        composeTestRule.runOnUiThread {
            composeTestRule.activity.onBackPressedDispatcher.onBackPressed()
        }
        composeTestRule.waitForIdle()
    }

    @Test
    fun givenFieldsSwitchedRepeatedly_thenFocusTransfersEachTime() {
        renderEditor()
        val title = composeTestRule.onNodeWithTag(TITLE_TAG)
        val description = composeTestRule.onNodeWithTag(DESCRIPTION_TAG)

        repeat(2) {
            title.performClick()
            title.assertIsFocused()
            description.performClick()
            description.assertIsFocused()
            title.assertIsNotFocused()
        }
        assertEquals(0, sheetDismissals)
    }

    @Test
    fun givenFocusedTitle_whenBackCompletes_thenClearsFocusWithoutDismissingSheet() {
        renderEditor()
        val title = composeTestRule.onNodeWithTag(TITLE_TAG)
        title.performClick()

        pressBack()

        title.assertIsNotFocused()
        assertEquals(0, sheetDismissals)
    }

    @Test
    fun givenFocusedDescription_whenBackCompletesTwice_thenSecondBackReachesSheet() {
        renderEditor()
        val description = composeTestRule.onNodeWithTag(DESCRIPTION_TAG)
        description.performClick()

        pressBack()
        description.assertIsNotFocused()
        assertEquals(0, sheetDismissals)

        pressBack()
        assertEquals(1, sheetDismissals)
    }

    @Test
    fun givenFocusedField_whenPredictiveBackIsCancelled_thenFocusIsKeptAndSheetDoesNotMove() {
        renderEditor()
        val description = composeTestRule.onNodeWithTag(DESCRIPTION_TAG)
        description.performClick()
        composeTestRule.waitForIdle()

        val dispatcher = composeTestRule.activity.onBackPressedDispatcher
        composeTestRule.runOnUiThread {
            dispatcher.dispatchOnBackStarted(backEvent(0f))
            dispatcher.dispatchOnBackProgressed(backEvent(0.5f))
        }
        // Let the gesture collectors drain queued progress before cancelling, so a sheet that
        // wrongly owned the gesture would have counted it.
        composeTestRule.waitForIdle()
        description.assertIsFocused()
        assertEquals(0, sheetProgressEvents)

        composeTestRule.runOnUiThread { dispatcher.dispatchOnBackCancelled() }
        composeTestRule.waitForIdle()

        description.assertIsFocused()
        assertEquals(0, sheetProgressEvents)
        assertEquals(0, sheetDismissals)
    }

    @Test
    fun givenLegacyImeBack_whenSystemBackKeyPressedTwice_thenFirstClearsFocusAndSecondReachesSheet() {
        renderEditor(interceptsLegacyImeBack = true)
        val title = composeTestRule.onNodeWithTag(TITLE_TAG)
        title.performClick()
        composeTestRule.waitForIdle()

        sendLegacyImeBackKey()
        title.assertIsNotFocused()
        assertEquals(0, sheetDismissals)

        pressBack()
        assertEquals(1, sheetDismissals)
    }

    @Test
    fun givenFocusedField_whenKeyboardAlreadyClosed_thenFirstBackReachesSheet() {
        renderEditor()
        val description = composeTestRule.onNodeWithTag(DESCRIPTION_TAG)
        description.performClick()

        isKeyboardVisible = false
        pressBack()

        description.assertIsFocused()
        assertEquals(1, sheetDismissals)
    }

    @Test
    fun givenRoutedGesture_whenKeyboardClosesMidGesture_thenGestureStillClearsFocusOnly() {
        renderEditor()
        val description = composeTestRule.onNodeWithTag(DESCRIPTION_TAG)
        description.performClick()
        composeTestRule.waitForIdle()

        val dispatcher = composeTestRule.activity.onBackPressedDispatcher
        composeTestRule.runOnUiThread {
            dispatcher.dispatchOnBackStarted(backEvent(0f))
            dispatcher.dispatchOnBackProgressed(backEvent(0.5f))
        }
        composeTestRule.waitForIdle()
        isKeyboardVisible = false
        composeTestRule.waitForIdle()
        composeTestRule.runOnUiThread { dispatcher.onBackPressed() }
        composeTestRule.waitForIdle()

        description.assertIsNotFocused()
        assertEquals(0, sheetProgressEvents)
        assertEquals(0, sheetDismissals)
    }

    @Test
    fun givenLegacyImeBack_whenKeyboardAlreadyClosed_thenBackKeyIsNotIntercepted() {
        renderEditor(interceptsLegacyImeBack = true)
        val title = composeTestRule.onNodeWithTag(TITLE_TAG)
        title.performClick()
        isKeyboardVisible = false
        composeTestRule.waitForIdle()

        val view = composeTestRule.activity.window.decorView
        composeTestRule.runOnUiThread {
            val consumed = view.dispatchKeyEventPreIme(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BACK))
            assertEquals(false, consumed)
        }
        title.assertIsFocused()
    }

    /**
     * Before Android 13 the window delivers the back key to the view hierarchy's pre-IME stage and
     * only then to the keyboard. Newer releases route the key to predictive back first, so the key is
     * injected at that same pre-IME stage to exercise the legacy path on any test device.
     */
    private fun sendLegacyImeBackKey() {
        val view = composeTestRule.activity.window.decorView
        composeTestRule.runOnUiThread {
            listOf(KeyEvent.ACTION_DOWN, KeyEvent.ACTION_UP).forEach { action ->
                val consumed = view.dispatchKeyEventPreIme(KeyEvent(action, KeyEvent.KEYCODE_BACK))
                assertEquals(true, consumed)
            }
        }
        composeTestRule.waitForIdle()
    }

    private fun backEvent(progress: Float) =
        BackEventCompat(
            touchX = 0f,
            touchY = 0f,
            progress = progress,
            swipeEdge = BackEventCompat.EDGE_LEFT,
        )
}
