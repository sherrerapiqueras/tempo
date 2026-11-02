package com.mandrecode.tempo.core.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mandrecode.tempo.R
import com.mandrecode.tempo.core.ui.glass.DialogWindowGlass
import kotlinx.coroutines.flow.drop
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TempoDatePickerDialog(
    initialDate: LocalDate?,
    onConfirm: (LocalDate) -> Unit,
    onDismiss: () -> Unit,
    selectableDates: SelectableDates = DatePickerDefaults.AllDates,
    initialDisplayedDate: LocalDate? = null,
) {
    val haptic = LocalHapticFeedback.current

    // M3 DatePicker works in UTC millis. We need to convert our local date to UTC millis at start of day.
    val initialMillis = initialDate?.atStartOfDayIn(TimeZone.UTC)?.toEpochMilliseconds()

    val datePickerState =
        rememberDatePickerState(
            initialSelectedDateMillis = initialMillis,
            initialDisplayedMonthMillis =
                initialMillis ?: initialDisplayedDate?.atStartOfDayIn(TimeZone.UTC)?.toEpochMilliseconds(),
            selectableDates = selectableDates,
        )

    // Add haptic feedback when date picker values change
    LaunchedEffect(datePickerState, haptic) {
        snapshotFlow {
            datePickerState.selectedDateMillis to datePickerState.displayedMonthMillis
        }.drop(1)
            .collect { (selectedDate, _) ->
                if (selectedDate != null) {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                }
            }
    }

    val colorScheme = MaterialTheme.colorScheme
    // The selected day is the primary container, like every other selected state in the app; the
    // stock picker fills it with the bright primary.
    val pickerColors =
        DatePickerDefaults.colors(
            selectedDayContainerColor = colorScheme.primaryContainer,
            selectedDayContentColor = colorScheme.onPrimaryContainer,
            selectedYearContainerColor = colorScheme.primaryContainer,
            selectedYearContentColor = colorScheme.onPrimaryContainer,
        )

    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            DialogConfirmButton(
                label = stringResource(R.string.ok),
                // Nothing to confirm until a date is picked, so OK says so instead of doing nothing.
                enabled = datePickerState.selectedDateMillis != null,
                modifier = Modifier.padding(end = DatePickerButtonsEndInset, bottom = DatePickerButtonsBottomInset),
                onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        // Convert UTC millis back to LocalDate
                        val selectedDate =
                            Instant
                                .fromEpochMilliseconds(millis)
                                .toLocalDateTime(TimeZone.UTC)
                                .date

                        onConfirm(selectedDate)
                    }
                },
            )
        },
        dismissButton = {
            DialogCancelButton(
                label = stringResource(R.string.cancel),
                onClick = onDismiss,
                modifier = Modifier.padding(bottom = DatePickerButtonsBottomInset),
            )
        },
        colors = pickerColors,
    ) {
        // Blurs the app behind the picker and lightens the dim, like the app's alert dialogs.
        DialogWindowGlass()
        DatePicker(state = datePickerState, colors = pickerColors)
    }
}

// Material's date picker holds its buttons about 6dp from the dialog's right edge and 12dp from its
// bottom. These make up the difference to the 24dp on both edges that Material gives a dialog's
// actions, which is also where the time picker's end up.
private val DatePickerButtonsEndInset = 18.dp
private val DatePickerButtonsBottomInset = 12.dp
