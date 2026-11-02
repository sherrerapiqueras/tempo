package com.mandrecode.tempo.features.routines.presentation.components

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.test.platform.app.InstrumentationRegistry
import com.mandrecode.tempo.R
import com.mandrecode.tempo.core.ui.components.HABIT_COMPLETION_CHECKBOX_TEST_TAG
import com.mandrecode.tempo.core.ui.editor.EDITOR_AUTO_SAVE_DEBOUNCE_MS
import com.mandrecode.tempo.core.ui.theme.TempoTheme
import com.mandrecode.tempo.features.routines.domain.model.Habit
import com.mandrecode.tempo.features.routines.domain.model.HabitChain
import com.mandrecode.tempo.features.routines.domain.model.HabitType
import com.mandrecode.tempo.features.routines.domain.util.HabitReminderDateUtil
import com.mandrecode.tempo.features.routines.presentation.RoutinesContract
import com.mandrecode.tempo.features.routines.presentation.RoutinesContract.HabitSheetTab
import kotlinx.collections.immutable.toPersistentSet
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.todayIn
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import kotlin.math.abs
import kotlin.time.Clock

private val markHabitNotCompleted: String
    get() = InstrumentationRegistry.getInstrumentation().targetContext.getString(R.string.mark_as_not_completed)

private val selectHabits: String
    get() = InstrumentationRegistry.getInstrumentation().targetContext.getString(R.string.select_habits)

class HabitBottomSheetTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private fun defaultFormState() = RoutinesContract.HabitFormState()

    private fun today() = Clock.System.todayIn(TimeZone.currentSystemDefault())

    private val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())

    private fun habitInChain() =
        Habit(
            id = 1L,
            title = "Push-ups",
            description = "Do 20 push-ups",
            createdDate = now,
        )

    private fun secondHabitInChain() =
        habitInChain().copy(
            id = 2L,
            title = "Read",
        )

    private fun chainContainingHabit() =
        HabitChain(
            id = 10L,
            title = "Morning routine",
            habitIds = listOf(1L),
            createdDate = now,
        )

    private fun renderHabitInChain(
        formState: RoutinesContract.HabitFormState =
            defaultFormState().copy(
                editingHabit = habitInChain(),
            ),
    ) {
        composeTestRule.setContent {
            TempoTheme {
                HabitBottomSheet(
                    formState = formState,
                    selectedDate = today(),
                    habits = listOf(habitInChain()),
                    habitChains = listOf(chainContainingHabit()),
                    onSelectTab = {},
                    onSetReminder = { _, _, _, _, _ -> },
                    onClearReminder = {},
                    onSetColorKey = {},
                    onClearColor = {},
                    onSetIcon = {},
                    onClearIcon = {},
                    onDismiss = {},
                    onClearErrors = {},
                    onConfirmHabit = { _, _ -> },
                    onConfirmHabitChain = { _, _, _ -> },
                    onSetHabitType = {},
                )
            }
        }
    }

    @Test
    fun showsVacationNotice_whenVacationModeIsActive() {
        composeTestRule.setContent {
            TempoTheme {
                HabitBottomSheet(
                    formState = defaultFormState().copy(editingHabit = habitInChain()),
                    selectedDate = today(),
                    habits = listOf(habitInChain()),
                    habitChains = emptyList(),
                    onSelectTab = {},
                    onSetReminder = { _, _, _, _, _ -> },
                    onClearReminder = {},
                    onSetColorKey = {},
                    onClearColor = {},
                    onSetIcon = {},
                    onClearIcon = {},
                    onDismiss = {},
                    onClearErrors = {},
                    onConfirmHabit = { _, _ -> },
                    onConfirmHabitChain = { _, _, _ -> },
                    onSetHabitType = {},
                    isVacationModeActive = true,
                )
            }
        }

        composeTestRule.onNodeWithTag(VACATION_MODE_NOTICE_TEST_TAG, useUnmergedTree = true).assertExists()
    }

    @Test
    fun hidesVacationNotice_whenVacationModeIsOff() {
        composeTestRule.setContent {
            TempoTheme {
                HabitBottomSheet(
                    formState = defaultFormState().copy(editingHabit = habitInChain()),
                    selectedDate = today(),
                    habits = listOf(habitInChain()),
                    habitChains = emptyList(),
                    onSelectTab = {},
                    onSetReminder = { _, _, _, _, _ -> },
                    onClearReminder = {},
                    onSetColorKey = {},
                    onClearColor = {},
                    onSetIcon = {},
                    onClearIcon = {},
                    onDismiss = {},
                    onClearErrors = {},
                    onConfirmHabit = { _, _ -> },
                    onConfirmHabitChain = { _, _, _ -> },
                    onSetHabitType = {},
                )
            }
        }

        composeTestRule.onNodeWithTag(VACATION_MODE_NOTICE_TEST_TAG, useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun displaysHabitTab() {
        composeTestRule.setContent {
            TempoTheme {
                HabitBottomSheet(
                    formState = defaultFormState(),
                    selectedDate = today(),
                    habits = emptyList(),
                    habitChains = emptyList(),
                    onSelectTab = {},
                    onSetReminder = { _, _, _, _, _ -> },
                    onClearReminder = {},
                    onSetColorKey = {},
                    onClearColor = {},
                    onSetIcon = {},
                    onClearIcon = {},
                    onDismiss = {},
                    onClearErrors = {},
                    onConfirmHabit = { _, _ -> },
                    onConfirmHabitChain = { _, _, _ -> },
                    onSetHabitType = {},
                )
            }
        }

        composeTestRule.onNodeWithText("Habit").assertIsDisplayed()
    }

    @Test
    fun displaysDiscardDialog_whenUnsavedChangesExist_andCancelIsClicked() {
        var dismissed = false
        composeTestRule.setContent {
            TempoTheme {
                HabitBottomSheet(
                    formState = defaultFormState(),
                    selectedDate = today(),
                    habits = emptyList(),
                    habitChains = emptyList(),
                    onSelectTab = {},
                    onSetReminder = { _, _, _, _, _ -> },
                    onClearReminder = {},
                    onSetColorKey = {},
                    onClearColor = {},
                    onSetIcon = {},
                    onClearIcon = {},
                    onDismiss = { dismissed = true },
                    onClearErrors = {},
                    onConfirmHabit = { _, _ -> },
                    onConfirmHabitChain = { _, _, _ -> },
                    onSetHabitType = {},
                )
            }
        }

        // Enter some text
        composeTestRule.onNodeWithText("Go for a walk").performTextInput("My Habit")

        // Click Cancel
        composeTestRule.onNodeWithText("Cancel").performScrollTo().performClick()

        // Verify dialog is shown
        composeTestRule.onNodeWithText("Discard changes?").assertIsDisplayed()

        // Ensure not dismissed yet
        assertFalse(dismissed)

        // Click Discard
        composeTestRule.onNodeWithText("Discard").performClick()

        // Wait for the sheet hide animation to complete and trigger onDismiss
        composeTestRule.waitUntil(timeoutMillis = 5000) { dismissed }

        // Verify dismissed
        assertTrue(dismissed)
    }

    @Test
    fun displaysHabitChainTab() {
        composeTestRule.setContent {
            TempoTheme {
                HabitBottomSheet(
                    formState = defaultFormState(),
                    selectedDate = today(),
                    habits = emptyList(),
                    habitChains = emptyList(),
                    onSelectTab = {},
                    onSetReminder = { _, _, _, _, _ -> },
                    onClearReminder = {},
                    onSetColorKey = {},
                    onClearColor = {},
                    onSetIcon = {},
                    onClearIcon = {},
                    onDismiss = {},
                    onClearErrors = {},
                    onConfirmHabit = { _, _ -> },
                    onConfirmHabitChain = { _, _, _ -> },
                    onSetHabitType = {},
                )
            }
        }

        composeTestRule.onNodeWithText("Habit chain").assertIsDisplayed()
    }

    @Test
    fun displaysTitlePlaceholder() {
        composeTestRule.setContent {
            TempoTheme {
                HabitBottomSheet(
                    formState = defaultFormState(),
                    selectedDate = today(),
                    habits = emptyList(),
                    habitChains = emptyList(),
                    onSelectTab = {},
                    onSetReminder = { _, _, _, _, _ -> },
                    onClearReminder = {},
                    onSetColorKey = {},
                    onClearColor = {},
                    onSetIcon = {},
                    onClearIcon = {},
                    onDismiss = {},
                    onClearErrors = {},
                    onConfirmHabit = { _, _ -> },
                    onConfirmHabitChain = { _, _, _ -> },
                    onSetHabitType = {},
                )
            }
        }

        composeTestRule.onNodeWithText("Go for a walk").assertIsDisplayed()
    }

    @Test
    fun givenFocusedHabitTitle_whenDoneIsPerformed_thenFocusIsCleared() {
        composeTestRule.setContent {
            TempoTheme {
                HabitBottomSheet(
                    formState = defaultFormState(),
                    selectedDate = today(),
                    habits = emptyList(),
                    habitChains = emptyList(),
                    onSelectTab = {},
                    onSetReminder = { _, _, _, _, _ -> },
                    onClearReminder = {},
                    onSetColorKey = {},
                    onClearColor = {},
                    onSetIcon = {},
                    onClearIcon = {},
                    onDismiss = {},
                    onClearErrors = {},
                    onConfirmHabit = { _, _ -> },
                    onConfirmHabitChain = { _, _, _ -> },
                    onSetHabitType = {},
                )
            }
        }

        val titleField = composeTestRule.onNodeWithTag(HABIT_BOTTOM_SHEET_TITLE_FIELD_TEST_TAG)
        titleField.performClick()
        titleField.assertIsFocused()
        titleField.performImeAction()
        titleField.assertIsNotFocused()
    }

    @Test
    fun displaysSaveButton() {
        composeTestRule.setContent {
            TempoTheme {
                HabitBottomSheet(
                    formState = defaultFormState(),
                    selectedDate = today(),
                    habits = emptyList(),
                    habitChains = emptyList(),
                    onSelectTab = {},
                    onSetReminder = { _, _, _, _, _ -> },
                    onClearReminder = {},
                    onSetColorKey = {},
                    onClearColor = {},
                    onSetIcon = {},
                    onClearIcon = {},
                    onDismiss = {},
                    onClearErrors = {},
                    onConfirmHabit = { _, _ -> },
                    onConfirmHabitChain = { _, _, _ -> },
                    onSetHabitType = {},
                )
            }
        }

        composeTestRule.onNodeWithText("Add habit").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun habitInChain_hidesInvertedToggle() {
        renderHabitInChain()

        composeTestRule.onNodeWithText("Inverted habit").assertDoesNotExist()
    }

    @Test
    fun habitInChain_hidesColorPicker() {
        renderHabitInChain()

        composeTestRule.onNodeWithText("Material You").assertDoesNotExist()
    }

    @Test
    fun habitInChain_hidesReminder() {
        renderHabitInChain()

        composeTestRule.onNodeWithText("Add Reminder").assertDoesNotExist()
    }

    @Test
    fun habitInChain_hidesHabitTypeSelector() {
        renderHabitInChain()

        composeTestRule.onNodeWithText("Quit").assertDoesNotExist()
    }

    @Test
    fun habitInChain_displaysTitleAndDescription() {
        renderHabitInChain()

        composeTestRule.onNodeWithText("Push-ups").assertIsDisplayed()
        composeTestRule.onNodeWithText("Do 20 push-ups").assertIsDisplayed()
    }

    @Test
    fun givenRapidDescriptionEdits_whenAutoSavingHabit_thenDispatchesLatestValueOnce() {
        val savedDescriptions = mutableListOf<String>()
        composeTestRule.mainClock.autoAdvance = false
        composeTestRule.setContent {
            TempoTheme {
                HabitBottomSheet(
                    formState = defaultFormState().copy(editingHabit = habitInChain()),
                    selectedDate = today(),
                    habits = listOf(habitInChain()),
                    habitChains = emptyList(),
                    onSelectTab = {},
                    onSetHabitType = {},
                    onSetReminder = { _, _, _, _, _ -> },
                    onClearReminder = {},
                    onSetColorKey = {},
                    onClearColor = {},
                    onSetIcon = {},
                    onClearIcon = {},
                    onDismiss = {},
                    onClearErrors = {},
                    onConfirmHabit = { _, _ -> },
                    onConfirmHabitChain = { _, _, _ -> },
                    onAutoSaveHabit = { _, description -> savedDescriptions += description },
                )
            }
        }
        composeTestRule.mainClock.advanceTimeBy(1_000)

        val descriptionField =
            composeTestRule.onNodeWithTag(HABIT_BOTTOM_SHEET_DESCRIPTION_FIELD_TEST_TAG)
        descriptionField.performTextReplacement("a")
        descriptionField.performTextReplacement("ab")
        descriptionField.performTextReplacement("abc")

        composeTestRule.mainClock.advanceTimeBy(EDITOR_AUTO_SAVE_DEBOUNCE_MS + 1)
        composeTestRule.runOnIdle { assertEquals(listOf("abc"), savedDescriptions) }
        composeTestRule.mainClock.autoAdvance = true
    }

    @Test
    fun givenHabitAndChainEditors_whenSwitchingTabs_thenRebindsWithoutAutosavingUnchangedChain() {
        val habit = habitInChain()
        val chain = chainContainingHabit().copy(description = "Complete in order")
        val savedHabits = mutableListOf<String>()
        val savedChains = mutableListOf<String>()
        var dismissed = false
        lateinit var switchToChainTab: () -> Unit
        composeTestRule.setContent {
            TempoTheme {
                var selectedTab by remember { mutableStateOf(HabitSheetTab.HABIT) }
                switchToChainTab = { selectedTab = HabitSheetTab.HABIT_CHAIN }
                HabitBottomSheet(
                    formState =
                        defaultFormState().copy(
                            selectedTab = selectedTab,
                            editingHabit = habit,
                            editingHabitChain = chain,
                        ),
                    selectedDate = today(),
                    habits = listOf(habit),
                    habitChains = listOf(chain),
                    onSelectTab = { selectedTab = it },
                    onSetHabitType = {},
                    onSetReminder = { _, _, _, _, _ -> },
                    onClearReminder = {},
                    onSetColorKey = {},
                    onClearColor = {},
                    onSetIcon = {},
                    onClearIcon = {},
                    onDismiss = { dismissed = true },
                    onClearErrors = {},
                    onConfirmHabit = { _, _ -> },
                    onConfirmHabitChain = { _, _, _ -> },
                    onAutoSaveHabit = { title, _ -> savedHabits += title },
                    onAutoSaveHabitChain = { title, _, _ -> savedChains += title },
                )
            }
        }
        composeTestRule.mainClock.advanceTimeBy(EDITOR_AUTO_SAVE_DEBOUNCE_MS + 1)
        composeTestRule.runOnIdle {
            assertTrue(savedHabits.isEmpty())
            assertTrue(savedChains.isEmpty())
        }

        composeTestRule.runOnIdle { switchToChainTab() }
        composeTestRule.mainClock.advanceTimeBy(EDITOR_AUTO_SAVE_DEBOUNCE_MS + 1)

        composeTestRule.onNodeWithText(chain.title).assertIsDisplayed()
        composeTestRule.onNodeWithText(chain.description).assertIsDisplayed()
        composeTestRule.runOnIdle {
            assertTrue(savedHabits.isEmpty())
            assertTrue(savedChains.isEmpty())
        }

        composeTestRule.onNodeWithText("Cancel").performScrollTo().performClick()
        composeTestRule.waitUntil(timeoutMillis = 5_000) { dismissed }
        composeTestRule.runOnIdle {
            assertTrue(dismissed)
            assertTrue(savedHabits.isEmpty())
            assertTrue(savedChains.isEmpty())
        }
    }

    // --- Regression tests for #398, #424: title overflow and long text ---

    private fun renderNewHabitSheet() {
        composeTestRule.setContent {
            TempoTheme {
                HabitBottomSheet(
                    formState = defaultFormState(),
                    selectedDate = today(),
                    habits = emptyList(),
                    habitChains = emptyList(),
                    onSelectTab = {},
                    onSetReminder = { _, _, _, _, _ -> },
                    onClearReminder = {},
                    onSetColorKey = {},
                    onClearColor = {},
                    onSetIcon = {},
                    onClearIcon = {},
                    onDismiss = {},
                    onClearErrors = {},
                    onConfirmHabit = { _, _ -> },
                    onConfirmHabitChain = { _, _, _ -> },
                    onSetHabitType = {},
                )
            }
        }
    }

    @Test
    fun titleOverflow_at65Chars_movesExcessTextToDescription() {
        renderNewHabitSheet()

        val titlePart = "A".repeat(65)
        val overflowPart = "OVERFLOW"
        composeTestRule
            .onNodeWithText("Go for a walk")
            .performTextInput(titlePart + overflowPart)

        // The overflow text should now appear in the description field
        composeTestRule
            .onNodeWithText(overflowPart, substring = true)
            .assertIsDisplayed()
    }

    @Test
    fun titleOverflow_midWord_movesWholeWordToDescription() {
        renderNewHabitSheet()

        val titlePart = "A".repeat(60)
        val overflowWord = "OVERFLOWING"
        composeTestRule
            .onNodeWithText("Go for a walk")
            .performTextInput("$titlePart $overflowWord")

        // Exact matches: a mid-word split would leave part of the word in each field
        composeTestRule.onNodeWithText(titlePart).assertIsDisplayed()
        composeTestRule.onNodeWithText(overflowWord).assertIsDisplayed()
    }

    @Test
    fun longTitle_nearLimit_displaysCorrectly() {
        renderNewHabitSheet()

        val longTitle = "B".repeat(60)
        composeTestRule
            .onNodeWithText("Go for a walk")
            .performTextInput(longTitle)

        composeTestRule
            .onNodeWithText(longTitle, substring = true)
            .assertIsDisplayed()
    }

    @Test
    fun titleAtExactLimit_doesNotOverflowToDescription() {
        renderNewHabitSheet()

        val exactTitle = "C".repeat(65)
        composeTestRule
            .onNodeWithText("Go for a walk")
            .performTextInput(exactTitle)

        // Title should contain all 65 chars
        composeTestRule
            .onNodeWithText(exactTitle, substring = true)
            .assertIsDisplayed()

        // Description placeholder should still be visible (no overflow text)
        composeTestRule
            .onNodeWithText("Add details", substring = true)
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun descriptionIconRow_displaysDescriptionIcon() {
        renderNewHabitSheet()

        // Verify the description icon is present (top-aligned row from #374 fix)
        composeTestRule
            .onNodeWithContentDescription("Description")
            .assertIsDisplayed()
    }

    @Test
    fun iconPickerRow_displaysIconLabel() {
        renderNewHabitSheet()

        // Verify the icon picker section is rendered (top-aligned row from #374 fix)
        composeTestRule
            .onNodeWithContentDescription("Choose Icon")
            .assertIsDisplayed()
    }

    // --- Tests for update button enabled/disabled based on changes ---

    private fun standaloneHabit() =
        Habit(
            id = 2L,
            title = "Meditate",
            description = "10 minutes",
            createdDate = now,
        )

    private fun renderEditHabitSheet(habit: Habit = standaloneHabit()) {
        composeTestRule.setContent {
            TempoTheme {
                HabitBottomSheet(
                    formState =
                        defaultFormState().copy(
                            editingHabit = habit,
                        ),
                    selectedDate = today(),
                    habits = listOf(habit),
                    habitChains = emptyList(),
                    onSelectTab = {},
                    onSetReminder = { _, _, _, _, _ -> },
                    onClearReminder = {},
                    onSetColorKey = {},
                    onClearColor = {},
                    onSetIcon = {},
                    onClearIcon = {},
                    onDismiss = {},
                    onClearErrors = {},
                    onConfirmHabit = { _, _ -> },
                    onConfirmHabitChain = { _, _, _ -> },
                    onSetHabitType = {},
                )
            }
        }
    }

    private fun renderEditHabitChainSheet(
        chain: HabitChain = chainContainingHabit(),
        habits: List<Habit> = listOf(habitInChain()),
        onToggleHabitCompletion: ((Long, Boolean) -> Unit)? = null,
        selectedDate: kotlinx.datetime.LocalDate = today(),
    ) {
        composeTestRule.setContent {
            TempoTheme {
                HabitBottomSheet(
                    formState =
                        defaultFormState().copy(
                            editingHabitChain = chain,
                            selectedTab = HabitSheetTab.HABIT_CHAIN,
                        ),
                    selectedDate = selectedDate,
                    habits = habits,
                    habitChains = listOf(chain),
                    onSelectTab = {},
                    onSetReminder = { _, _, _, _, _ -> },
                    onClearReminder = {},
                    onSetColorKey = {},
                    onClearColor = {},
                    onSetIcon = {},
                    onClearIcon = {},
                    onDismiss = {},
                    onClearErrors = {},
                    onConfirmHabit = { _, _ -> },
                    onConfirmHabitChain = { _, _, _ -> },
                    onSetHabitType = {},
                    onToggleHabitCompletion = onToggleHabitCompletion,
                )
            }
        }
    }

    private fun renderNewHabitChainSheet(habits: List<Habit> = listOf(habitInChain())) {
        composeTestRule.setContent {
            TempoTheme {
                HabitBottomSheet(
                    formState = defaultFormState().copy(selectedTab = HabitSheetTab.HABIT_CHAIN),
                    selectedDate = today(),
                    habits = habits,
                    habitChains = emptyList(),
                    onSelectTab = {},
                    onSetReminder = { _, _, _, _, _ -> },
                    onClearReminder = {},
                    onSetColorKey = {},
                    onClearColor = {},
                    onSetIcon = {},
                    onClearIcon = {},
                    onDismiss = {},
                    onClearErrors = {},
                    onConfirmHabit = { _, _ -> },
                    onConfirmHabitChain = { _, _, _ -> },
                    onSetHabitType = {},
                )
            }
        }
    }

    private fun assertHabitSelectorVerticalAlignment(
        selectorTestTag: String,
        maxDifferenceDp: Float,
    ) {
        val iconNode =
            composeTestRule
                .onNodeWithContentDescription(selectHabits, useUnmergedTree = true)
        val selectorNode =
            composeTestRule
                .onAllNodesWithTag(selectorTestTag, useUnmergedTree = true)[0]

        iconNode.assertIsDisplayed()
        selectorNode.assertIsDisplayed()

        val iconBounds = iconNode.fetchSemanticsNode().boundsInRoot
        val selectorBounds = selectorNode.fetchSemanticsNode().boundsInRoot
        val iconCenterY = (iconBounds.top + iconBounds.bottom) / 2f
        val selectorCenterY = (selectorBounds.top + selectorBounds.bottom) / 2f
        val density =
            InstrumentationRegistry
                .getInstrumentation()
                .targetContext.resources.displayMetrics.density

        assertTrue(
            "Expected habits icon and first selector row to be vertically aligned; " +
                "icon=$iconBounds selector=$selectorBounds",
            abs(iconCenterY - selectorCenterY) <= maxDifferenceDp * density,
        )
    }

    @Test
    fun habitSelectorIcon_alignedWithAvailableHabit_whenNoHabitsSelected() {
        renderNewHabitChainSheet(habits = listOf(habitInChain(), secondHabitInChain()))

        assertHabitSelectorVerticalAlignment(
            selectorTestTag = AVAILABLE_HABIT_CHIP_TEST_TAG,
            maxDifferenceDp = 1f,
        )
    }

    @Test
    fun habitSelectorIcon_remainsAlignedWithFirstSelectedHabit() {
        renderEditHabitChainSheet(
            chain = chainContainingHabit().copy(habitIds = listOf(1L, 2L)),
            habits = listOf(habitInChain(), secondHabitInChain()),
            onToggleHabitCompletion = { _, _ -> },
        )

        assertHabitSelectorVerticalAlignment(
            selectorTestTag = SELECTED_HABIT_ROW_TEST_TAG,
            maxDifferenceDp = 4f,
        )
    }

    @Test
    fun editHabit_updateButtonDisabled_whenNoChanges() {
        renderEditHabitSheet()

        composeTestRule
            .onNodeWithText("Update")
            .performScrollTo()
            .assertIsNotEnabled()
    }

    @Test
    fun editHabit_updateButtonEnabled_afterTitleChange() {
        renderEditHabitSheet()

        // Modify the title
        composeTestRule
            .onNodeWithText("Meditate")
            .performTextReplacement("Meditate longer")

        composeTestRule
            .onNodeWithText("Update")
            .performScrollTo()
            .assertIsEnabled()
    }

    @Test
    fun editHabit_updateButtonEnabled_afterHabitTypeToggle() {
        val habit = standaloneHabit()
        composeTestRule.setContent {
            TempoTheme {
                var formState by remember {
                    mutableStateOf(
                        defaultFormState().copy(
                            editingHabit = habit,
                            selectedHabitType = HabitType.BUILD,
                        ),
                    )
                }
                HabitBottomSheet(
                    formState = formState,
                    selectedDate = today(),
                    habits = listOf(habit),
                    habitChains = emptyList(),
                    onSelectTab = {},
                    onSetReminder = { _, _, _, _, _ -> },
                    onClearReminder = {},
                    onSetColorKey = {},
                    onClearColor = {},
                    onSetIcon = {},
                    onClearIcon = {},
                    onDismiss = {},
                    onClearErrors = {},
                    onConfirmHabit = { _, _ -> },
                    onConfirmHabitChain = { _, _, _ -> },
                    onSetHabitType = { formState = formState.copy(selectedHabitType = it) },
                )
            }
        }

        composeTestRule
            .onNodeWithText("Quit")
            .performScrollTo()
            .performClick()

        composeTestRule
            .onNodeWithText("Update")
            .performScrollTo()
            .assertIsEnabled()
    }

    @Test
    fun editHabitChain_updateButtonDisabled_whenNoChanges() {
        renderEditHabitChainSheet()

        composeTestRule
            .onNodeWithText("Update")
            .performScrollTo()
            .assertIsNotEnabled()
    }

    @Test
    fun editHabitChain_updateButtonEnabled_afterTitleChange() {
        renderEditHabitChainSheet()

        // Modify the title
        composeTestRule
            .onNodeWithText("Morning routine")
            .performTextReplacement("Evening routine")

        composeTestRule
            .onNodeWithText("Update")
            .performScrollTo()
            .assertIsEnabled()
    }

    // --- Tests for create button disabled until user makes a change ---

    @Test
    fun createHabit_addButtonDisabled_whenNoChanges() {
        renderNewHabitSheet()

        composeTestRule
            .onNodeWithText("Add habit")
            .performScrollTo()
            .assertIsNotEnabled()
    }

    @Test
    fun createHabit_addButtonEnabled_afterTitleInput() {
        renderNewHabitSheet()

        composeTestRule
            .onNodeWithText("Go for a walk")
            .performTextInput("My new habit")

        composeTestRule
            .onNodeWithText("Add habit")
            .performScrollTo()
            .assertIsEnabled()
    }

    @Test
    fun createHabitChain_addButtonDisabled_whenNoChanges() {
        composeTestRule.setContent {
            TempoTheme {
                HabitBottomSheet(
                    formState =
                        defaultFormState().copy(
                            selectedTab = HabitSheetTab.HABIT_CHAIN,
                        ),
                    selectedDate = today(),
                    habits = emptyList(),
                    habitChains = emptyList(),
                    onSelectTab = {},
                    onSetReminder = { _, _, _, _, _ -> },
                    onClearReminder = {},
                    onSetColorKey = {},
                    onClearColor = {},
                    onSetIcon = {},
                    onClearIcon = {},
                    onDismiss = {},
                    onClearErrors = {},
                    onConfirmHabit = { _, _ -> },
                    onConfirmHabitChain = { _, _, _ -> },
                    onSetHabitType = {},
                )
            }
        }

        composeTestRule
            .onNodeWithText("Add habit")
            .performScrollTo()
            .assertIsNotEnabled()
    }

    // --- Regression tests for #673: save/edit-state correctness for build & quit habits ---

    /**
     * Simulates the form state immediately after the user taps the "Quit" type
     * card on a fresh new-habit sheet: type is QUIT, the auto-applied 21:00
     * reminder is set, and any prior repeat-day selection has been snapshotted
     * and cleared. Title is still blank.
     */
    private fun newQuitHabitFormStateWithBlankTitle() =
        defaultFormState().copy(
            selectedHabitType = HabitType.QUIT,
            reminderDate =
                HabitReminderDateUtil.nextUpcomingTime(
                    hour = HabitReminderDateUtil.QUIT_DEFAULT_REMINDER_HOUR,
                ),
            quitDefaultReminderApplied = true,
            quitRepeatDaysCleared = true,
        )

    @Test
    fun createQuitHabit_addButtonDisabled_whenTitleBlank_evenWithAutoReminder() {
        composeTestRule.setContent {
            TempoTheme {
                HabitBottomSheet(
                    formState = newQuitHabitFormStateWithBlankTitle(),
                    selectedDate = today(),
                    habits = emptyList(),
                    habitChains = emptyList(),
                    onSelectTab = {},
                    onSetReminder = { _, _, _, _, _ -> },
                    onClearReminder = {},
                    onSetColorKey = {},
                    onClearColor = {},
                    onSetIcon = {},
                    onClearIcon = {},
                    onDismiss = {},
                    onClearErrors = {},
                    onConfirmHabit = { _, _ -> },
                    onConfirmHabitChain = { _, _, _ -> },
                    onSetHabitType = {},
                )
            }
        }

        // Auto-applied reminder + QUIT type would otherwise mark the form dirty;
        // the title gate must keep Save disabled.
        composeTestRule
            .onNodeWithText("Add habit")
            .performScrollTo()
            .assertIsNotEnabled()
    }

    @Test
    fun createQuitHabit_addButtonEnabled_afterTitleInput() {
        composeTestRule.setContent {
            TempoTheme {
                HabitBottomSheet(
                    formState = newQuitHabitFormStateWithBlankTitle(),
                    selectedDate = today(),
                    habits = emptyList(),
                    habitChains = emptyList(),
                    onSelectTab = {},
                    onSetReminder = { _, _, _, _, _ -> },
                    onClearReminder = {},
                    onSetColorKey = {},
                    onClearColor = {},
                    onSetIcon = {},
                    onClearIcon = {},
                    onDismiss = {},
                    onClearErrors = {},
                    onConfirmHabit = { _, _ -> },
                    onConfirmHabitChain = { _, _, _ -> },
                    onSetHabitType = {},
                )
            }
        }

        composeTestRule
            .onNodeWithText("Quit smoking")
            .performTextInput("Stop scrolling")

        composeTestRule
            .onNodeWithText("Add habit")
            .performScrollTo()
            .assertIsEnabled()
    }

    @Test
    fun createHabitChain_addButtonDisabled_whenTitleBlankButHabitsSelected() {
        composeTestRule.setContent {
            TempoTheme {
                HabitBottomSheet(
                    formState =
                        defaultFormState().copy(
                            selectedTab = HabitSheetTab.HABIT_CHAIN,
                        ),
                    selectedDate = today(),
                    habits = listOf(habitInChain()),
                    habitChains = emptyList(),
                    onSelectTab = {},
                    onSetReminder = { _, _, _, _, _ -> },
                    onClearReminder = {},
                    onSetColorKey = {},
                    onClearColor = {},
                    onSetIcon = {},
                    onClearIcon = {},
                    onDismiss = {},
                    onClearErrors = {},
                    onConfirmHabit = { _, _ -> },
                    onConfirmHabitChain = { _, _, _ -> },
                    onSetHabitType = {},
                )
            }
        }

        // Select an available habit so hasUnsavedChanges goes true via selectedHabitIds.
        composeTestRule
            .onNodeWithText("Push-ups")
            .performScrollTo()
            .performClick()

        // Title is still blank → Save must remain disabled.
        composeTestRule
            .onNodeWithText("Add habit")
            .performScrollTo()
            .assertIsNotEnabled()

        // Type a title → Save becomes enabled.
        composeTestRule
            .onNodeWithText("Workout routine")
            .performTextInput("Morning chain")

        composeTestRule
            .onNodeWithText("Add habit")
            .performScrollTo()
            .assertIsEnabled()
    }

    @Test
    fun editHabit_updateButtonDisabled_whenTitleCleared() {
        renderEditHabitSheet()

        // Make some other change so hasUnsavedChanges is true; the title gate
        // must still keep Save disabled when the title is empty.
        composeTestRule
            .onNodeWithText("10 minutes")
            .performTextInput("Updated description")

        composeTestRule
            .onNodeWithText("Update")
            .performScrollTo()
            .assertIsEnabled()

        composeTestRule
            .onNodeWithText("Meditate")
            .performTextReplacement("")

        composeTestRule
            .onNodeWithText("Update")
            .performScrollTo()
            .assertIsNotEnabled()
    }

    @Test
    fun editBuildHabit_buildQuitBuildRoundTrip_doesNotEnableSave() {
        // Mirror the ViewModel-level round-trip behavior at the UI layer:
        // hand the sheet a formState equivalent to "user toggled Quit then
        // Build again on an existing build habit". Snapshot/restore in the
        // ViewModel means the form must look identical to its initial state,
        // and the title gate is incidentally satisfied because the original
        // habit has a title — but no field has actually changed, so
        // hasUnsavedChanges is false and Save stays disabled.
        val existing = standaloneHabit()
        composeTestRule.setContent {
            TempoTheme {
                HabitBottomSheet(
                    formState =
                        defaultFormState().copy(
                            editingHabit = existing,
                            selectedHabitType = HabitType.BUILD,
                            reminderDate = existing.reminderDate,
                            selectedRepeatDays = existing.repeatDays?.toPersistentSet(),
                            // After the round-trip the snapshot fields are reset.
                            quitDefaultReminderApplied = false,
                            quitClearedRepeatDays = null,
                            quitRepeatDaysCleared = false,
                        ),
                    selectedDate = today(),
                    habits = listOf(existing),
                    habitChains = emptyList(),
                    onSelectTab = {},
                    onSetReminder = { _, _, _, _, _ -> },
                    onClearReminder = {},
                    onSetColorKey = {},
                    onClearColor = {},
                    onSetIcon = {},
                    onClearIcon = {},
                    onDismiss = {},
                    onClearErrors = {},
                    onConfirmHabit = { _, _ -> },
                    onConfirmHabitChain = { _, _, _ -> },
                    onSetHabitType = {},
                )
            }
        }

        composeTestRule
            .onNodeWithText("Update")
            .performScrollTo()
            .assertIsNotEnabled()
    }

    // --- Tests for #657 / #658: completion checkbox in the habit bottom sheet ---

    private fun completedHabit() =
        habitInChain().copy(
            completionHistory = today().toString(),
        )

    @Test
    fun completionCheckbox_notDisplayed_whenCreatingNewHabit() {
        renderNewHabitSheet()

        composeTestRule.onNodeWithTag(HABIT_COMPLETION_CHECKBOX_TEST_TAG).assertDoesNotExist()
    }

    @Test
    fun completionCheckbox_displayed_whenEditingHabit() {
        composeTestRule.setContent {
            TempoTheme {
                HabitBottomSheet(
                    formState = defaultFormState().copy(editingHabit = standaloneHabit()),
                    selectedDate = today(),
                    habits = listOf(standaloneHabit()),
                    habitChains = emptyList(),
                    onSelectTab = {},
                    onSetReminder = { _, _, _, _, _ -> },
                    onClearReminder = {},
                    onSetColorKey = {},
                    onClearColor = {},
                    onSetIcon = {},
                    onClearIcon = {},
                    onDismiss = {},
                    onClearErrors = {},
                    onConfirmHabit = { _, _ -> },
                    onConfirmHabitChain = { _, _, _ -> },
                    onSetHabitType = {},
                    onToggleHabitCompletion = { _, _ -> },
                )
            }
        }

        composeTestRule.onNodeWithTag(HABIT_COMPLETION_CHECKBOX_TEST_TAG).assertIsDisplayed()
    }

    @Test
    fun completionCheckbox_clicking_invokesOnToggleHabitCompletion_withCorrectId() {
        var toggledHabitId: Long? = null
        var toggledIsCompleted: Boolean? = null
        val habit = standaloneHabit()
        composeTestRule.setContent {
            TempoTheme {
                HabitBottomSheet(
                    formState = defaultFormState().copy(editingHabit = habit),
                    selectedDate = today(),
                    habits = listOf(habit),
                    habitChains = emptyList(),
                    onSelectTab = {},
                    onSetReminder = { _, _, _, _, _ -> },
                    onClearReminder = {},
                    onSetColorKey = {},
                    onClearColor = {},
                    onSetIcon = {},
                    onClearIcon = {},
                    onDismiss = {},
                    onClearErrors = {},
                    onConfirmHabit = { _, _ -> },
                    onConfirmHabitChain = { _, _, _ -> },
                    onSetHabitType = {},
                    onToggleHabitCompletion = { id, completed ->
                        toggledHabitId = id
                        toggledIsCompleted = completed
                    },
                )
            }
        }

        composeTestRule.onNodeWithTag(HABIT_COMPLETION_CHECKBOX_TEST_TAG).performClick()

        assertTrue(toggledHabitId == habit.id)
        // Habit is uncompleted, so click should toggle to true.
        assertTrue(toggledIsCompleted == true)
    }

    @Test
    fun completionCheckbox_completedHabit_showsCompletedContentDescription() {
        composeTestRule.setContent {
            TempoTheme {
                HabitBottomSheet(
                    formState = defaultFormState().copy(editingHabit = completedHabit()),
                    selectedDate = today(),
                    habits = listOf(completedHabit()),
                    habitChains = emptyList(),
                    onSelectTab = {},
                    onSetReminder = { _, _, _, _, _ -> },
                    onClearReminder = {},
                    onSetColorKey = {},
                    onClearColor = {},
                    onSetIcon = {},
                    onClearIcon = {},
                    onDismiss = {},
                    onClearErrors = {},
                    onConfirmHabit = { _, _ -> },
                    onConfirmHabitChain = { _, _, _ -> },
                    onSetHabitType = {},
                    onToggleHabitCompletion = { _, _ -> },
                )
            }
        }

        composeTestRule.onNodeWithContentDescription(markHabitNotCompleted).assertIsDisplayed()
    }

    @Test
    fun completionCheckbox_displayedInChainRows_whenEditingHabitChain() {
        renderEditHabitChainSheet(onToggleHabitCompletion = { _, _ -> })

        // Chain has one habit → one checkbox should appear in the selected list.
        composeTestRule.onNodeWithTag(CHAIN_HABIT_COMPLETION_CHECKBOX_TEST_TAG).assertIsDisplayed()
    }

    @Test
    fun completionCheckbox_notDisplayedInChainRows_whenCreatingNewChain() {
        composeTestRule.setContent {
            TempoTheme {
                HabitBottomSheet(
                    formState = defaultFormState().copy(selectedTab = HabitSheetTab.HABIT_CHAIN),
                    selectedDate = today(),
                    habits = listOf(habitInChain()),
                    habitChains = emptyList(),
                    onSelectTab = {},
                    onSetReminder = { _, _, _, _, _ -> },
                    onClearReminder = {},
                    onSetColorKey = {},
                    onClearColor = {},
                    onSetIcon = {},
                    onClearIcon = {},
                    onDismiss = {},
                    onClearErrors = {},
                    onConfirmHabit = { _, _ -> },
                    onConfirmHabitChain = { _, _, _ -> },
                    onSetHabitType = {},
                    onToggleHabitCompletion = { _, _ -> },
                )
            }
        }

        // Select the habit so it shows up in the chain list.
        composeTestRule.onNodeWithText("Push-ups").performScrollTo().performClick()

        // No editing chain → no checkbox in the row.
        composeTestRule.onNodeWithTag(CHAIN_HABIT_COMPLETION_CHECKBOX_TEST_TAG).assertDoesNotExist()
    }

    // --- Tests for #45: chain completion checkbox next to the chain title ---

    @Test
    fun chainTitleCheckbox_displayed_whenEditingHabitChain() {
        renderEditHabitChainSheet(onToggleHabitCompletion = { _, _ -> })

        composeTestRule.onNodeWithTag(HABIT_COMPLETION_CHECKBOX_TEST_TAG).assertIsDisplayed()
    }

    @Test
    fun chainTitleCheckbox_notDisplayed_whenCreatingNewChain() {
        composeTestRule.setContent {
            TempoTheme {
                HabitBottomSheet(
                    formState = defaultFormState().copy(selectedTab = HabitSheetTab.HABIT_CHAIN),
                    selectedDate = today(),
                    habits = listOf(habitInChain()),
                    habitChains = emptyList(),
                    onSelectTab = {},
                    onSetReminder = { _, _, _, _, _ -> },
                    onClearReminder = {},
                    onSetColorKey = {},
                    onClearColor = {},
                    onSetIcon = {},
                    onClearIcon = {},
                    onDismiss = {},
                    onClearErrors = {},
                    onConfirmHabit = { _, _ -> },
                    onConfirmHabitChain = { _, _, _ -> },
                    onSetHabitType = {},
                    onToggleHabitCompletion = { _, _ -> },
                )
            }
        }

        // No editing chain → no checkbox next to the title.
        composeTestRule.onNodeWithTag(HABIT_COMPLETION_CHECKBOX_TEST_TAG).assertDoesNotExist()
    }

    @Test
    fun chainTitleCheckbox_tapWhenIncomplete_completesAllMemberHabits() {
        val chain = chainContainingHabit().copy(habitIds = listOf(1L, 2L))
        val toggled = mutableListOf<Pair<Long, Boolean>>()
        renderEditHabitChainSheet(
            chain = chain,
            habits = listOf(habitInChain(), secondHabitInChain()),
            onToggleHabitCompletion = { id, completed -> toggled.add(id to completed) },
        )

        composeTestRule.onNodeWithTag(HABIT_COMPLETION_CHECKBOX_TEST_TAG).performClick()

        assertEquals(setOf(1L to true, 2L to true), toggled.toSet())
    }

    @Test
    fun chainTitleCheckbox_tapWhenAllCompleted_uncompletesAllMemberHabits() {
        val chain = chainContainingHabit().copy(habitIds = listOf(1L, 2L))
        val completedHabit1 = habitInChain().copy(completionHistory = today().toString())
        val completedHabit2 = secondHabitInChain().copy(completionHistory = today().toString())
        val toggled = mutableListOf<Pair<Long, Boolean>>()
        renderEditHabitChainSheet(
            chain = chain,
            habits = listOf(completedHabit1, completedHabit2),
            onToggleHabitCompletion = { id, completed -> toggled.add(id to completed) },
        )

        composeTestRule.onNodeWithTag(HABIT_COMPLETION_CHECKBOX_TEST_TAG).performClick()

        assertEquals(setOf(1L to false, 2L to false), toggled.toSet())
    }

    @Test
    fun chainTitleCheckbox_tapWhenPartiallyCompleted_onlyTogglesIncompleteHabits() {
        val chain = chainContainingHabit().copy(habitIds = listOf(1L, 2L))
        val completedHabit1 = habitInChain().copy(completionHistory = today().toString())
        val incompleteHabit2 = secondHabitInChain()
        val toggled = mutableListOf<Pair<Long, Boolean>>()
        renderEditHabitChainSheet(
            chain = chain,
            habits = listOf(completedHabit1, incompleteHabit2),
            onToggleHabitCompletion = { id, completed -> toggled.add(id to completed) },
        )

        composeTestRule.onNodeWithTag(HABIT_COMPLETION_CHECKBOX_TEST_TAG).performClick()

        // Habit 1 is already completed → must not be re-toggled; only habit 2 flips.
        assertEquals(listOf(2L to true), toggled)
    }

    @Test
    fun chainTitleCheckbox_disabled_whenChainHasNoHabits() {
        val emptyChain = chainContainingHabit().copy(habitIds = emptyList())
        renderEditHabitChainSheet(
            chain = emptyChain,
            habits = emptyList(),
            onToggleHabitCompletion = { _, _ -> },
        )

        composeTestRule.onNodeWithTag(HABIT_COMPLETION_CHECKBOX_TEST_TAG).assertIsNotEnabled()
    }

    @Test
    fun chainTitleCheckbox_disabled_whenSelectedDateOutsideToggleWindow() {
        val chain = chainContainingHabit().copy(habitIds = listOf(1L, 2L))
        var toggleInvoked = false
        renderEditHabitChainSheet(
            chain = chain,
            habits = listOf(habitInChain(), secondHabitInChain()),
            onToggleHabitCompletion = { _, _ -> toggleInvoked = true },
            selectedDate = today().minus(DatePeriod(days = 5)),
        )

        composeTestRule.onNodeWithTag(HABIT_COMPLETION_CHECKBOX_TEST_TAG).assertIsNotEnabled()

        composeTestRule.onNodeWithTag(HABIT_COMPLETION_CHECKBOX_TEST_TAG).performClick()
        assertFalse(toggleInvoked)
    }

    // Regression test for #655: when the upstream habit updates after a toggle (i.e. the
    // ViewModel refreshes the editing snapshot from the live repo), the sheet's checkbox
    // must reflect the new completion state — not stay stuck on the original snapshot.
    @Test
    fun completionCheckbox_reflectsUpstreamStateChange_afterToggle() {
        composeTestRule.setContent {
            TempoTheme {
                var habit by remember { mutableStateOf(standaloneHabit()) }
                HabitBottomSheet(
                    formState = defaultFormState().copy(editingHabit = habit),
                    selectedDate = today(),
                    habits = listOf(habit),
                    habitChains = emptyList(),
                    onSelectTab = {},
                    onSetReminder = { _, _, _, _, _ -> },
                    onClearReminder = {},
                    onSetColorKey = {},
                    onClearColor = {},
                    onSetIcon = {},
                    onClearIcon = {},
                    onDismiss = {},
                    onClearErrors = {},
                    onConfirmHabit = { _, _ -> },
                    onConfirmHabitChain = { _, _, _ -> },
                    onSetHabitType = {},
                    onToggleHabitCompletion = { _, _ ->
                        habit = habit.copy(completionHistory = today().toString())
                    },
                )
            }
        }

        composeTestRule.onNodeWithContentDescription(markHabitNotCompleted).assertDoesNotExist()

        composeTestRule.onNodeWithTag(HABIT_COMPLETION_CHECKBOX_TEST_TAG).performClick()

        composeTestRule.waitUntil(timeoutMillis = 5000) {
            composeTestRule
                .onAllNodesWithContentDescription(markHabitNotCompleted)
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        composeTestRule.onNodeWithContentDescription(markHabitNotCompleted).assertIsDisplayed()
    }

    // Regression test: chain habit rows must not show the leading "1.", "2.", … number prefix.
    @Test
    fun chainHabitRow_doesNotShowLeadingNumberPrefix() {
        renderEditHabitChainSheet()

        composeTestRule.onNodeWithText("1.").assertDoesNotExist()
    }
}
