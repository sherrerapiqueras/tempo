package com.mandrecode.tempo.features.widget.presentation

import android.content.Context
import android.content.Intent
import androidx.annotation.DrawableRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.Action
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.itemsIndexed
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.semantics.contentDescription
import androidx.glance.semantics.semantics
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.mandrecode.tempo.MainActivity
import com.mandrecode.tempo.R
import com.mandrecode.tempo.core.ui.components.CardGroupPosition
import com.mandrecode.tempo.infrastructure.reminders.receivers.TaskReminderReceiver
import com.mandrecode.tempo.util.DateTimeFormatter

/**
 * The Today widget in Tempo's visual language, within what RemoteViews can draw:
 *
 * - the two-block screen: a tinted strip (the header, on the scheme's widget background) above the
 *   content, here the day's tasks;
 * - the tasks as one connected group of cards, round at the group's ends and tight at the joins
 *   ([CardGroupPosition]), on `surface`;
 * - Tempo's task check: a quiet circle, a filled rounded square in `primary` once done;
 * - adding a task as the widget's one main action, in `primary` (on the tinted strip, the
 *   `secondaryContainer` the app uses for "add" would barely stand out from a background derived
 *   from it).
 *
 * The layout follows the size the launcher gives it (spec: "Widget adapts to its size").
 */
@Composable
internal fun TodayWidgetContent(day: WidgetDay) {
    val size = LocalSize.current
    when {
        size.width < TinyThreshold -> QuickAddTile(day)
        size.height < TinyThreshold -> TodayStrip(day)
        else ->
            Column(modifier = widgetContainer().padding(WidgetPadding)) {
                if (size.height < CompactHeightThreshold) CompactHeader(day) else FullHeader(day)
                Spacer(GlanceModifier.height(HeaderGap))
                if (day.isEmpty) EmptyDay() else TaskGroup(day)
            }
    }
}

@Composable
private fun TaskGroup(day: WidgetDay) {
    LazyColumn(modifier = GlanceModifier.fillMaxSize()) {
        itemsIndexed(day.rows, itemId = { _, row -> row.taskId }) { index, row ->
            Column(modifier = GlanceModifier.fillMaxWidth()) {
                if (index > 0) Spacer(GlanceModifier.height(ConnectedGap))
                TaskCard(row, CardGroupPosition.of(index, day.rows.size))
            }
        }
    }
}

@Composable
private fun TaskCard(
    row: WidgetTaskRow,
    position: CardGroupPosition,
) {
    Row(
        modifier =
            GlanceModifier
                .fillMaxWidth()
                .background(
                    imageProvider = ImageProvider(position.cardBackground()),
                    colorFilter = ColorFilter.tint(GlanceTheme.colors.surface),
                ).padding(end = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TaskCheck(row)
        Column(
            modifier =
                GlanceModifier
                    .defaultWeight()
                    .padding(vertical = 10.dp)
                    .clickable(openTaskAction(LocalContext.current, row.taskId)),
        ) {
            Text(
                text = row.title,
                style =
                    TextStyle(
                        color =
                            if (row.isCompleted) {
                                GlanceTheme.colors.onSurfaceVariant
                            } else {
                                GlanceTheme.colors.onSurface
                            },
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                    ),
                maxLines = 2,
            )
            Metadata(row)
        }
    }
}

@Composable
private fun TaskCheck(row: WidgetTaskRow) {
    val context = LocalContext.current
    val description =
        context.getString(
            if (row.isCompleted) R.string.widget_reopen_task else R.string.widget_complete_task,
            row.title,
        )
    Box(
        modifier =
            GlanceModifier
                .size(48.dp)
                .clickable(
                    actionRunCallback<ToggleTaskFromWidgetAction>(
                        actionParametersOf(
                            ToggleTaskFromWidgetAction.TaskIdKey to row.taskId,
                            ToggleTaskFromWidgetAction.WasCompletedKey to row.isCompleted,
                        ),
                    ),
                ).semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Image(
            provider =
                ImageProvider(if (row.isCompleted) R.drawable.widget_check_done else R.drawable.widget_check_idle),
            contentDescription = null,
            colorFilter =
                ColorFilter.tint(if (row.isCompleted) GlanceTheme.colors.primary else GlanceTheme.colors.onSurface),
            modifier = GlanceModifier.size(26.dp),
        )
    }
}

/** "09:00 · Work", or "Overdue · Work" for work carried over; nothing when there is neither. */
@Composable
private fun Metadata(row: WidgetTaskRow) {
    val context = LocalContext.current
    val lead: Pair<String, ColorProvider>? =
        when {
            row.isOverdue -> context.getString(R.string.active_overdue) to GlanceTheme.colors.error
            row.dueTime != null ->
                DateTimeFormatter.formatTimeOfDay(row.dueTime, context) to GlanceTheme.colors.onSurfaceVariant
            else -> null
        }
    if (lead == null && row.categoryName == null) return
    val style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 12.sp)
    Row {
        lead?.let { (text, color) ->
            Text(text = text, style = style.copy(color = color), maxLines = 1)
        }
        if (lead != null && row.categoryName != null) Text(text = " · ", style = style, maxLines = 1)
        row.categoryName?.let { Text(text = it, style = style, maxLines = 1) }
    }
}

@Composable
private fun EmptyDay() {
    Box(
        modifier =
            GlanceModifier
                .fillMaxSize()
                .background(
                    imageProvider = ImageProvider(R.drawable.widget_card_single),
                    colorFilter = ColorFilter.tint(GlanceTheme.colors.surface),
                ).padding(16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = LocalContext.current.getString(R.string.widget_today_empty),
            style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 14.sp),
        )
    }
}

/** The widget's own surface: the tinted strip, with the launcher's widget corner radius. */
@Composable
internal fun widgetContainer(): GlanceModifier =
    GlanceModifier
        .fillMaxSize()
        .appWidgetBackground()
        .background(GlanceTheme.colors.widgetBackground)
        .cornerRadius(android.R.dimen.system_app_widget_background_radius)

@DrawableRes
private fun CardGroupPosition.cardBackground(): Int =
    when (this) {
        CardGroupPosition.SINGLE -> R.drawable.widget_card_single
        CardGroupPosition.FIRST -> R.drawable.widget_card_first
        CardGroupPosition.MIDDLE -> R.drawable.widget_card_middle
        CardGroupPosition.LAST -> R.drawable.widget_card_last
    }

internal fun openNewTaskAction(): Action =
    actionStartActivity<MainActivity>(
        actionParametersOf(TodayWidget.openNewTaskDialogKey to true),
    )

/**
 * Opens the task's editor, through the same extra a reminder notification uses.
 *
 * A whole [Intent] rather than action parameters: inside a lazy list Glance relays activity starts
 * through its own trampoline, and parameters given that way reached MainActivity without their
 * extras. An explicit intent keeps them.
 */
private fun openTaskAction(
    context: Context,
    taskId: Long,
): Action =
    actionStartActivity(
        Intent(context, MainActivity::class.java)
            .putExtra(TaskReminderReceiver.EXTRA_TASK_ID, taskId)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
    )

private val TinyThreshold = 110.dp
private val CompactHeightThreshold = 180.dp
private val WidgetPadding = 10.dp
private val HeaderGap = 8.dp

/** The same seam as the app's connected cards (ConnectedCardGap). */
private val ConnectedGap = 3.dp
