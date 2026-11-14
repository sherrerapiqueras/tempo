package com.mandrecode.tempo.infrastructure.focus

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.mandrecode.tempo.MainActivity
import com.mandrecode.tempo.R
import com.mandrecode.tempo.core.di.IoDispatcher
import com.mandrecode.tempo.features.focus.domain.usecase.FocusSessionUseCases
import com.mandrecode.tempo.infrastructure.notifications.NotificationChannelManager
import com.mandrecode.tempo.infrastructure.notifications.RequestCodeGenerator
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Fires when a session's time is up: banks the elapsed minutes and either starts the next phase of
 * a saved session's rounds, or replaces the ongoing countdown with a one-shot "time's up"
 * notification, so the user is told even if the app is not foregrounded.
 *
 * In-app, the completion sheet is what the user actually sees; this notification is the fallback
 * for a session that ran out while they were elsewhere.
 */
@AndroidEntryPoint
class FocusSessionEndReceiver : BroadcastReceiver() {
    @Inject
    lateinit var focusSessionUseCases: FocusSessionUseCases

    @Inject
    @IoDispatcher
    lateinit var ioDispatcher: CoroutineDispatcher

    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        val pendingResult = goAsync()
        CoroutineScope(ioDispatcher).launch {
            try {
                // Not `end()`: a saved session with rounds to go moves on to its rest or its next
                // round here, and only the last one is over. A late or repeated alarm finds a phase
                // that has not run out and does nothing.
                val timeUp = focusSessionUseCases.timeUp() ?: return@launch
                // A new phase announces itself through its own ongoing notification; "time's up"
                // is for the run that has actually ended.
                if (timeUp.next != null) return@launch
                val finished = timeUp.finished
                notifyTimeIsUp(
                    context = context,
                    title = finished.title,
                    minutes = finished.plannedLength.inWholeMinutes.toInt(),
                    wasBreak = finished.isBreak,
                )
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun notifyTimeIsUp(
        context: Context,
        title: String,
        minutes: Int,
        wasBreak: Boolean,
    ) {
        if (!NotificationChannelManager.canPostNotifications(context)) return
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        NotificationChannelManager.ensureFocusSessionChannel(context, notificationManager)

        val openFocus =
            android.app.PendingIntent.getActivity(
                context,
                RequestCodeGenerator.forFocusSession(),
                Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    putExtra(AndroidFocusSessionScheduler.EXTRA_OPEN_FOCUS, true)
                },
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE,
            )

        val notification =
            NotificationCompat
                .Builder(context, NotificationChannelManager.FOCUS_SESSION_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_focus)
                // A break did not complete anything, so counting its minutes as work done was
                // both wrong and a strange thing to be told after resting.
                .setContentTitle(
                    if (wasBreak) {
                        context.getString(R.string.focus_break_finished_title)
                    } else {
                        context.getString(R.string.focus_session_finished_title, minutes)
                    },
                ).setContentText(title)
                .setAutoCancel(true)
                .setContentIntent(openFocus)
                .build()

        notificationManager.notify(RequestCodeGenerator.forFocusSession(), notification)
    }
}
