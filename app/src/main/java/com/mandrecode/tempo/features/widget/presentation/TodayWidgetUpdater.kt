package com.mandrecode.tempo.features.widget.presentation

import android.content.Context
import android.os.Build
import android.util.Log
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.updateAll
import com.mandrecode.tempo.BuildConfig
import com.mandrecode.tempo.features.focus.domain.usecase.GetFocusAgendaUseCase
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Clock

/**
 * Keeps placed Today widgets current while the app's process is alive (spec: "Widget stays
 * current").
 *
 * It watches the day's agenda, re-keyed at each local midnight, and redraws the widgets when what
 * they show changes. Whatever starts the process — the app, a notification action, an alarm, the
 * widget's own periodic update — starts this too, and its first emission redraws the widgets, so a
 * change made while the process was dead shows as soon as anything wakes it. While it stays dead,
 * the widget's `updatePeriodMillis` is the backstop for the date rolling over.
 */
@Singleton
class TodayWidgetUpdater
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val getFocusAgenda: GetFocusAgendaUseCase,
        private val clock: Clock,
    ) {
        @OptIn(FlowPreview::class)
        fun start(scope: CoroutineScope) {
            scope.launch {
                widgetDays(getFocusAgenda, clock)
                    // A bulk edit (clearing completed tasks, an import) settles before one redraw.
                    .debounce(RENDER_DEBOUNCE_MS)
                    // A database that can't be read leaves the widget as it was; it must not take
                    // down whatever process this is running in.
                    .catch { Log.w(TAG, "Unable to watch the day for the widget", it) }
                    .collect { redrawPlacedWidgets() }
            }
            scope.launch { publishGeneratedPreview() }
        }

        @Suppress("TooGenericExceptionCaught") // AppWidgetManager failures are best-effort here.
        private suspend fun redrawPlacedWidgets() {
            try {
                if (GlanceAppWidgetManager(context).getGlanceIds(TodayWidget::class.java).isNotEmpty()) {
                    TodayWidget().updateAll(context)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Unable to redraw the Today widget", e)
            }
        }

        /**
         * Hands the launcher's widget picker a preview generated from the real layout (Android 15+).
         * Once per app version: the system rate-limits these, and the layout only changes with one.
         */
        @Suppress("TooGenericExceptionCaught")
        private suspend fun publishGeneratedPreview() {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) return
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            if (prefs.getInt(KEY_PREVIEW_VERSION, -1) == BuildConfig.VERSION_CODE) return
            try {
                val result = GlanceAppWidgetManager(context).setWidgetPreviews(QuickAddTaskWidgetReceiver::class)
                if (result == GlanceAppWidgetManager.SET_WIDGET_PREVIEWS_RESULT_SUCCESS) {
                    prefs.edit().putInt(KEY_PREVIEW_VERSION, BuildConfig.VERSION_CODE).apply()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Unable to publish the widget preview", e)
            }
        }

        private companion object {
            const val TAG = "TodayWidgetUpdater"
            const val RENDER_DEBOUNCE_MS = 500L
            const val PREFS = "today_widget"
            const val KEY_PREVIEW_VERSION = "generated_preview_version"
        }
    }

/**
 * The day the widget shows, as it changes: the Focus agenda for today, then for each following day as
 * its local midnight passes. Shared by the widget's content, which watches it while its session is
 * open, and by [TodayWidgetUpdater], which redraws the widget when it changes after the session has
 * closed.
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal fun widgetDays(
    getFocusAgenda: GetFocusAgendaUseCase,
    clock: Clock,
): Flow<WidgetDay> =
    flow {
        while (true) {
            val zone = TimeZone.currentSystemDefault()
            val now = clock.now()
            val today = now.toLocalDateTime(zone).date
            emit(today)
            delay(today.plus(DatePeriod(days = 1)).atStartOfDayIn(zone) - now)
        }
    }.flatMapLatest { date -> getFocusAgenda(date).map { WidgetDay.from(date, it) } }
        .distinctUntilChanged()
