package com.mandrecode.tempo.infrastructure.focus

import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.mandrecode.tempo.MainActivity
import com.mandrecode.tempo.R
import com.mandrecode.tempo.core.data.preferences.ThemePreferencesRepository
import com.mandrecode.tempo.features.focus.domain.model.FocusSession
import com.mandrecode.tempo.features.focus.domain.scheduler.FocusSessionScheduler
import com.mandrecode.tempo.infrastructure.notifications.NotificationChannelManager
import com.mandrecode.tempo.infrastructure.notifications.RequestCodeGenerator
import com.mandrecode.tempo.util.supportsDynamicColor
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Drives a focus session with an exact alarm and an ongoing notification — no foreground service.
 *
 * The countdown is rendered by the platform's own chronometer against a fixed end timestamp, so the
 * app posts the notification once per state change rather than once per second, and the displayed
 * time stays correct even while the process is dead.
 */
@Singleton
class AndroidFocusSessionScheduler
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val themePreferences: ThemePreferencesRepository,
    ) : FocusSessionScheduler {
        private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        private val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        override fun scheduleSessionEnd(session: FocusSession) {
            val endsAt = session.endsAt ?: return
            val pendingIntent = sessionEndPendingIntent()
            if (canScheduleExactAlarms()) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    endsAt.toEpochMilliseconds(),
                    pendingIntent,
                )
            } else {
                // Exact alarms can be denied or revoked. The session still runs and the
                // notification's countdown stays accurate, since both are anchored to the same end
                // timestamp — only the moment the completion sheet appears becomes approximate.
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    endsAt.toEpochMilliseconds(),
                    pendingIntent,
                )
            }
        }

        override fun cancelSessionEnd() {
            alarmManager.cancel(sessionEndPendingIntent())
        }

        override fun showOngoingNotification(session: FocusSession) {
            if (!NotificationChannelManager.canPostNotifications(context)) return
            NotificationChannelManager.ensureFocusSessionChannel(context, notificationManager)

            val builder =
                NotificationCompat
                    .Builder(context, NotificationChannelManager.FOCUS_SESSION_CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_focus)
                    .setContentTitle(session.title)
                    // A second line, and the app's own accent. Title-plus-chronometer alone left
                    // the row looking like a system message with three buttons bolted under it.
                    // Not colorized: the platform only honours that for foreground-service
                    // notifications, and this feature deliberately has no service.
                    .setContentText(statusText(session))
                    .setColor(accentColor())
                    .setOngoing(true)
                    // Alerts when it arrives and then stays quiet: the pause and resume updates
                    // that follow are the same notification changing, not news.
                    .setOnlyAlertOnce(true)
                    .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                    .setCategory(NotificationCompat.CATEGORY_STOPWATCH)
                    // Promoted the way a habit chain's live activity is, so a running session can be
                    // glanced at from the status bar rather than only found in the shade.
                    .setRequestPromotedOngoing(true)
                    .setContentIntent(openFocusPendingIntent())

            val endsAt = session.endsAt
            if (endsAt != null) {
                // The platform renders the countdown itself from this timestamp; nothing here ticks.
                builder
                    .setUsesChronometer(true)
                    .setChronometerCountDown(true)
                    .setWhen(endsAt.toEpochMilliseconds())
                    .setShowWhen(true)
            }

            addSessionActions(builder, session)

            notificationManager.notify(RequestCodeGenerator.forFocusSession(), builder.build())
        }

        /** `Focusing`, or `Focusing · Round 2 of 4` for a saved session that repeats. */
        private fun statusText(session: FocusSession): String {
            val status =
                context.getString(
                    when {
                        session.isBreak -> R.string.focus_session_break
                        session.isPaused -> R.string.focus_session_paused
                        else -> R.string.focus_session_focusing
                    },
                )
            val cycle = session.cycle?.takeIf { it.hasRounds } ?: return status
            val round = context.getString(R.string.focus_session_round, cycle.round, cycle.totalRounds)
            return "$status · $round"
        }

        /**
         * Done, pause-or-resume, stop — the same three the card and the session screen offer, in the
         * same order, so the notification is the app's controls rather than a cut-down version of
         * them. A break has no work to finish, so it gets two.
         */
        private fun addSessionActions(
            builder: NotificationCompat.Builder,
            session: FocusSession,
        ) {
            // Done is about the task. A session linked to none has nothing to tick.
            if (!session.isBreak && session.taskId != null) {
                builder.addAction(
                    R.drawable.ic_check_circle,
                    context.getString(R.string.focus_session_mark_done),
                    FocusSessionActionReceiver.pendingIntent(
                        context,
                        FocusSessionActionReceiver.ACTION_COMPLETE,
                    ),
                )
            }
            builder.addAction(
                if (session.isPaused) R.drawable.ic_play_arrow else R.drawable.ic_pause,
                context.getString(
                    if (session.isPaused) {
                        R.string.focus_session_resume
                    } else {
                        R.string.focus_session_pause
                    },
                ),
                FocusSessionActionReceiver.pendingIntent(
                    context,
                    FocusSessionActionReceiver.ACTION_PAUSE,
                ),
            )
            builder.addAction(
                R.drawable.ic_stop,
                context.getString(R.string.focus_session_stop),
                FocusSessionActionReceiver.pendingIntent(
                    context,
                    FocusSessionActionReceiver.ACTION_STOP,
                ),
            )
        }

        /**
         * Tempo's own teal when the app is on its own palette, and the wallpaper's accent when it
         * is on dynamic colour — so the notification matches the app the user is actually looking
         * at rather than always advertising the brand.
         */
        private fun accentColor(): Int {
            val dynamic = !themePreferences.currentUseTempoColors() && supportsDynamicColor
            val colorRes =
                if (dynamic) android.R.color.system_accent1_600 else R.color.notification_accent
            return ContextCompat.getColor(context, colorRes)
        }

        override fun clearOngoingNotification() {
            notificationManager.cancel(RequestCodeGenerator.forFocusSession())
        }

        private fun canScheduleExactAlarms(): Boolean =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                alarmManager.canScheduleExactAlarms()
            } else {
                true
            }

        private fun sessionEndPendingIntent(): PendingIntent =
            PendingIntent.getBroadcast(
                context,
                RequestCodeGenerator.forFocusSession(),
                Intent(context, FocusSessionEndReceiver::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

        private fun openFocusPendingIntent(): PendingIntent =
            PendingIntent.getActivity(
                context,
                RequestCodeGenerator.forFocusSession(),
                Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    putExtra(EXTRA_OPEN_FOCUS, true)
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

        companion object {
            const val EXTRA_OPEN_FOCUS = "extra_open_focus"
        }
    }
