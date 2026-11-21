package com.mandrecode.tempo.infrastructure.reminders.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.mandrecode.tempo.features.routines.domain.repository.HabitRepository
import com.mandrecode.tempo.infrastructure.notifications.NotificationSyncManager
import com.mandrecode.tempo.infrastructure.receivers.launchReceiverWork
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.datetime.LocalDate
import javax.inject.Inject

@AndroidEntryPoint
class StartHabitChainReceiver : BroadcastReceiver() {
    @Inject
    lateinit var habitRepository: HabitRepository

    @Inject
    lateinit var notificationSyncManager: NotificationSyncManager

    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        val chainId = intent.getLongExtra(EXTRA_HABIT_CHAIN_ID, -1L)
        if (chainId != -1L) {
            val scheduledDate =
                intent
                    .getStringExtra(EXTRA_SCHEDULED_DATE)
                    ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

            launchReceiverWork(Dispatchers.IO, "StartHabitChainReceiver") {
                habitRepository.refreshHabitChainLiveActivity(
                    chainId,
                    scheduledDate,
                    fromNotification = true,
                )
            }

            notificationSyncManager.dismissHabitChainNotification(chainId)
        }
    }

    companion object {
        const val EXTRA_HABIT_CHAIN_ID = "HABIT_CHAIN_ID"
        const val EXTRA_SCHEDULED_DATE = "SCHEDULED_DATE"
    }
}
