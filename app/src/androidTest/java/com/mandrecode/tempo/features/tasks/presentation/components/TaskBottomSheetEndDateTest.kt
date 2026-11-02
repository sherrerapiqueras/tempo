package com.mandrecode.tempo.features.tasks.presentation.components

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import com.google.common.truth.Truth.assertThat
import com.mandrecode.tempo.R
import com.mandrecode.tempo.core.domain.model.Periodicity
import com.mandrecode.tempo.core.ui.theme.TempoTheme
import com.mandrecode.tempo.features.tasks.domain.model.Category
import com.mandrecode.tempo.features.tasks.domain.model.Task
import com.mandrecode.tempo.features.tasks.presentation.TasksContract
import com.mandrecode.tempo.util.DateTimeFormatter
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import org.junit.Rule
import org.junit.Test

/** The periodicity section's end date (#509). */
class TaskBottomSheetEndDateTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private val endDate = LocalDate(2099, 3, 31)
    private val inbox = Category(id = 3, name = "Inbox", isDefault = true)
    private var setFormPeriodicity: (Periodicity) -> Unit = {}

    // Resolved inside the composition, in the same locale the sheet renders in.
    private lateinit var addEndDate: String
    private lateinit var clearEndDate: String
    private lateinit var untilLabel: String
    private lateinit var monday: String
    private lateinit var ok: String
    private lateinit var cancel: String

    private fun setSheet(
        periodicity: Periodicity?,
        initialEndDate: LocalDate? = null,
        parentTaskId: Long? = null,
    ) {
        val task =
            Task(
                id = 1,
                title = "Water plants",
                description = "",
                categoryId = inbox.id,
                reminderDate = LocalDateTime(2099, 1, 1, 10, 0),
                periodicity = periodicity,
                periodicityEndDate = initialEndDate,
                parentTaskId = parentTaskId,
            )
        composeTestRule.setContent {
            // The ViewModel's role: the end date lives in the form state.
            var formState by remember {
                mutableStateOf(
                    TasksContract.TaskFormState(
                        editingTask = task,
                        reminderDate = task.reminderDate,
                        periodicity = periodicity,
                        periodicityEndDate = initialEndDate,
                    ),
                )
            }
            setFormPeriodicity = { formState = formState.copy(periodicity = it) }
            addEndDate = stringResource(R.string.add_end_date)
            clearEndDate = stringResource(R.string.clear_end_date)
            untilLabel = stringResource(R.string.periodicity_until, DateTimeFormatter.formatDate(initialEndDate ?: endDate))
            monday = stringResource(R.string.monday)
            ok = stringResource(R.string.ok)
            cancel = stringResource(R.string.cancel)
            TempoTheme {
                TaskBottomSheet(
                    task = task,
                    categories = listOf(inbox),
                    selectedCategoryIdFromFilter = null,
                    formState = formState,
                    onSetPeriodicityEndDate = { formState = formState.copy(periodicityEndDate = it) },
                    onSetPriority = {},
                    onClearPriority = {},
                    onSetReminder = { _, _, _, _, _ -> },
                    onClearReminder = {},
                    onSetPeriodicity = { formState = formState.copy(periodicity = it) },
                    onClearPeriodicity = { formState = formState.copy(periodicity = null, periodicityEndDate = null) },
                    onSetPeriodicityInterval = {},
                    onSetRepeatDays = {},
                    onSetMonthDayOption = {},
                    onDismiss = {},
                    onClearErrors = {},
                    onConfirm = { _, _, _ -> },
                )
            }
        }
    }

    @Test
    fun everyPeriodicity_showsTheOption() {
        setSheet(periodicity = null)

        Periodicity.periods.forEach { period ->
            composeTestRule.runOnIdle { setFormPeriodicity(period) }

            composeTestRule.endDateRow().performScrollTo().assertIsDisplayed()
            composeTestRule.onNodeWithText(addEndDate).assertIsDisplayed()
        }
    }

    @Test
    fun noPeriodicity_hidesTheOption() {
        setSheet(periodicity = null)

        composeTestRule.endDateRow().assertDoesNotExist()
    }

    @Test
    fun subtask_hidesTheOption() {
        setSheet(periodicity = Periodicity.DAILY, parentTaskId = 9L)

        composeTestRule.endDateRow().assertDoesNotExist()
    }

    @Test
    fun clearingThePeriodicity_hidesTheOption() {
        setSheet(periodicity = Periodicity.DAILY)

        composeTestRule.onNodeWithTag("taskPeriodicityChip_DAILY").performScrollTo().performClick()
        composeTestRule.waitForIdle()

        composeTestRule.endDateRow().assertDoesNotExist()
    }

    @Test
    fun weekly_placesTheOptionBelowTheWeekdays() {
        setSheet(periodicity = Periodicity.WEEKLY)

        val row = composeTestRule.endDateRow().performScrollTo()
        val weekday = composeTestRule.onNodeWithContentDescription(monday)

        assertThat(row.getUnclippedBoundsInRoot().top).isAtLeast(weekday.getUnclippedBoundsInRoot().bottom)
        row.assertHeightIsAtLeast(48.dp)
    }

    @Test
    fun endDateSet_showsUntilAndClears() {
        setSheet(periodicity = Periodicity.DAILY, initialEndDate = endDate)

        composeTestRule.onNodeWithText(untilLabel).performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription(clearEndDate).performClick()

        composeTestRule.onNodeWithText(addEndDate).assertIsDisplayed()
        composeTestRule.onNodeWithText(untilLabel).assertDoesNotExist()
    }

    @Test
    fun addEndDate_opensThePicker_andCancelChangesNothing() {
        setSheet(periodicity = Periodicity.DAILY)

        composeTestRule.onNodeWithText(addEndDate).performScrollTo().performClick()
        composeTestRule.onNodeWithText(ok).assertIsDisplayed()
        // The sheet's footer has a Cancel of its own; the dialog's comes last.
        composeTestRule.onAllNodesWithText(cancel).onLast().performClick()

        composeTestRule.onNodeWithText(ok).assertDoesNotExist()
        composeTestRule.onNodeWithText(addEndDate).assertIsDisplayed()
    }

    @Test
    fun tappingUntil_reopensThePicker() {
        setSheet(periodicity = Periodicity.DAILY, initialEndDate = endDate)

        composeTestRule.onNodeWithText(untilLabel).performScrollTo().performClick()

        composeTestRule.onNodeWithText(ok).assertIsDisplayed()
    }

    @Test
    fun endDateBeforeTheReminder_isNotSelectedInThePicker() {
        setSheet(periodicity = Periodicity.DAILY, initialEndDate = LocalDate(2098, 12, 31))

        composeTestRule.onNodeWithText(untilLabel).performScrollTo().performClick()

        composeTestRule.onNodeWithText(ok).assertIsNotEnabled()
    }

    // The sheet merges its plain rows' semantics, so the row's own tag is only in the unmerged tree.
    private fun ComposeContentTestRule.endDateRow() = onNodeWithTag(TASK_PERIODICITY_END_DATE_ROW_TEST_TAG, useUnmergedTree = true)
}
