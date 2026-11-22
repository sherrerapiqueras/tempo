package com.mandrecode.tempo.features.widget.presentation

import android.content.Context
import android.util.Log
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.GlanceTheme
import androidx.glance.LocalContext
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.provideContent
import com.mandrecode.tempo.R
import com.mandrecode.tempo.core.domain.model.Priority
import com.mandrecode.tempo.util.supportsDynamicColor
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * The Today home-screen widget: the Focus day — due today, then overdue — with each task's check,
 * and a way to add one. See the `home-screen-widget` spec.
 *
 * While Glance keeps the widget's session open the content watches the day itself; once Glance has
 * closed it, [TodayWidgetUpdater] asks for a new one whenever the day changes.
 */
class TodayWidget : GlanceAppWidget() {
    // Exact rather than Responsive: one layout held in the launcher's memory, laid out for the size
    // the widget really has.
    override val sizeMode: SizeMode = SizeMode.Exact

    // The picker draws the preview at its default 3×2 cell size; without a size here the layout
    // sees none and falls back to its 1×1 tile.
    override val previewSizeMode = SizeMode.Responsive(setOf(PreviewSize))

    override suspend fun provideGlance(
        context: Context,
        id: GlanceId,
    ) {
        val graph = TodayWidgetEntryPoint.from(context)
        val initial = loadDay(graph)
        val localized = context.inAppLanguage()
        provideContent {
            // Watched, not just read once: while Glance keeps this session open, an update only
            // recomposes it (provideGlance does not run again), so a snapshot would go stale the
            // moment a task changed. When the session has closed, the next update starts here anew.
            val day by remember { days(graph) }.collectAsState(initial = initial)
            CompositionLocalProvider(LocalContext provides localized) {
                TodayWidgetTheme {
                    TodayWidgetContent(day)
                }
            }
        }
    }

    /** The picker's generated preview (Android 15+): the real layout, filled with an example day. */
    override suspend fun providePreview(
        context: Context,
        widgetCategory: Int,
    ) {
        val localized = context.inAppLanguage()
        val day = previewDay(localized)
        provideContent {
            CompositionLocalProvider(LocalContext provides localized) {
                TodayWidgetTheme {
                    TodayWidgetContent(day)
                }
            }
        }
    }

    @Suppress("TooGenericExceptionCaught") // A widget that can't read the day still offers "add".
    private suspend fun loadDay(graph: TodayWidgetEntryPoint): WidgetDay {
        val today =
            graph
                .clock()
                .now()
                .toLocalDateTime(TimeZone.currentSystemDefault())
                .date
        return try {
            WidgetDay.from(today, graph.getFocusAgenda()(today).first())
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            WidgetDay(date = today)
        }
    }

    /** The day as it changes; a database that can't be read leaves the widget on what it shows. */
    private fun days(graph: TodayWidgetEntryPoint): Flow<WidgetDay> =
        widgetDays(graph.getFocusAgenda(), graph.clock())
            .catch { Log.w(TAG, "Unable to watch the day", it) }

    private fun previewDay(context: Context): WidgetDay {
        val today =
            kotlin.time.Clock.System
                .now()
                .toLocalDateTime(TimeZone.currentSystemDefault())
                .date
        val category = context.getString(R.string.widget_preview_category)
        return WidgetDay(
            date = today,
            rows =
                persistentListOf(
                    WidgetTaskRow(
                        taskId = 1,
                        title = context.getString(R.string.widget_preview_task_plan),
                        dueTime = PreviewMorning,
                        isOverdue = false,
                        categoryName = category,
                        isCompleted = false,
                        priority = Priority.HIGH,
                    ),
                    WidgetTaskRow(
                        taskId = 2,
                        title = context.getString(R.string.widget_preview_task_call),
                        dueTime = PreviewAfternoon,
                        isOverdue = false,
                        categoryName = category,
                        isCompleted = false,
                    ),
                    WidgetTaskRow(
                        taskId = 3,
                        title = context.getString(R.string.widget_preview_task_run),
                        dueTime = null,
                        isOverdue = false,
                        categoryName = category,
                        isCompleted = true,
                    ),
                ),
            completedCount = 1,
            scheduledCount = 3,
        )
    }

    companion object {
        // Read by MainActivity.handleIntent() to open the existing task-creation sheet
        // instead of the app's default start destination.
        const val EXTRA_OPEN_NEW_TASK_DIALOG = "OPEN_NEW_TASK_DIALOG"
        val openNewTaskDialogKey = ActionParameters.Key<Boolean>(EXTRA_OPEN_NEW_TASK_DIALOG)

        private const val TAG = "TodayWidget"
    }
}

private val PreviewSize = DpSize(width = 270.dp, height = 190.dp)
private val PreviewMorning = LocalTime(hour = 9, minute = 0)
private val PreviewAfternoon = LocalTime(hour = 13, minute = 30)

/**
 * The system's dynamic colours where the device has them, so the widget sits with the launcher and
 * wallpaper (#231); Tempo's own palette otherwise.
 */
@androidx.compose.runtime.Composable
private fun TodayWidgetTheme(content: @androidx.compose.runtime.Composable () -> Unit) {
    val colors =
        if (shouldUseTempoStaticColors(supportsDynamicColor)) TempoGlanceColorScheme else GlanceTheme.colors
    GlanceTheme(colors = colors, content = content)
}
