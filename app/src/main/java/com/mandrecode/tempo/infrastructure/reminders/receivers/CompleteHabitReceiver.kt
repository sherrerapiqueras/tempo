package com.mandrecode.tempo.infrastructure.reminders.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.mandrecode.tempo.features.routines.domain.usecase.ToggleHabitCompletionUseCase
import com.mandrecode.tempo.infrastructure.receivers.launchReceiverWork
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import javax.inject.Inject
import kotlin.time.Clock

@AndroidEntryPoint
class CompleteHabitReceiver : BroadcastReceiver() {
    @Inject
    lateinit var toggleHabitCompletionUseCase: ToggleHabitCompletionUseCase

    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        val habitId = intent.getLongExtra(EXTRA_HABIT_ID, -1L)
        if (habitId != -1L) {
            launchReceiverWork(Dispatchers.IO, "CompleteHabitReceiver") {
                val scheduledDate =
                    intent
                        .getStringExtra(EXTRA_SCHEDULED_DATE)
                        ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
                        ?: Clock.System.todayIn(TimeZone.currentSystemDefault())
                toggleHabitCompletionUseCase(habitId, true, scheduledDate, fromNotification = true)
            }
        }
    }

    companion object {
        const val EXTRA_HABIT_ID = "HABIT_ID"
        const val EXTRA_HABIT_CHAIN_ID = "HABIT_CHAIN_ID"
        const val EXTRA_SCHEDULED_DATE = "SCHEDULED_DATE"
    }
}
