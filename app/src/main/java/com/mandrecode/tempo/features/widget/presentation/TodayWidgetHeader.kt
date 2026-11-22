package com.mandrecode.tempo.features.widget.presentation

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.cornerRadius
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.semantics.contentDescription
import androidx.glance.semantics.semantics
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.mandrecode.tempo.MainActivity
import com.mandrecode.tempo.R

// The Today widget's header pieces and its add action (see TodayWidgetContent).

/** 1×1, including widgets placed before the redesign: the quick-add the widget used to be. */
@Composable
internal fun QuickAddTile(day: WidgetDay) {
    Box(
        modifier = widgetContainer().clickable(openNewTaskAction()),
        contentAlignment = Alignment.Center,
    ) {
        AddButton(size = 48.dp, clickable = false)
        if (day.remainingCount > 0) {
            Box(modifier = GlanceModifier.fillMaxSize().padding(6.dp), contentAlignment = Alignment.TopEnd) {
                Text(
                    text = day.remainingCount.toString(),
                    style =
                        TextStyle(
                            color = GlanceTheme.colors.onSurfaceVariant,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                        ),
                )
            }
        }
    }
}

/**
 * One row high and wider than a tile: the day in a line — its progress and the next open task —
 * with the add action beside it.
 */
@Composable
internal fun TodayStrip(day: WidgetDay) {
    val context = LocalContext.current
    val next = day.rows.firstOrNull { !it.isCompleted }?.title ?: context.getString(R.string.widget_today_empty)
    Row(
        modifier = widgetContainer().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = GlanceModifier.defaultWeight().clickable(actionStartActivity<MainActivity>())) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = context.getString(R.string.widget_today_label),
                    style =
                        TextStyle(
                            color = GlanceTheme.colors.onSurface,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium,
                        ),
                    maxLines = 1,
                )
                if (day.scheduledCount > 0) {
                    Spacer(GlanceModifier.width(8.dp))
                    Text(
                        text = progress(day),
                        style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 13.sp),
                        maxLines = 1,
                    )
                }
            }
            Text(
                text = next,
                style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 14.sp),
                maxLines = 1,
            )
        }
        AddButton(size = 40.dp)
    }
}

@Composable
internal fun FullHeader(day: WidgetDay) {
    val context = LocalContext.current
    Row(
        modifier = GlanceModifier.fillMaxWidth().padding(start = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = GlanceModifier.defaultWeight()) {
            Text(
                text = context.getString(R.string.widget_today_label),
                style =
                    TextStyle(
                        color = GlanceTheme.colors.onSurface,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Medium,
                    ),
                maxLines = 1,
            )
            Text(
                text = subtitle(day),
                style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 13.sp),
                maxLines = 1,
            )
        }
        AddButton(size = 48.dp)
    }
}

@Composable
internal fun CompactHeader(day: WidgetDay) {
    val context = LocalContext.current
    Row(
        modifier = GlanceModifier.fillMaxWidth().padding(start = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = context.getString(R.string.widget_today_label),
            style =
                TextStyle(
                    color = GlanceTheme.colors.onSurface,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                ),
            maxLines = 1,
        )
        Spacer(GlanceModifier.width(8.dp))
        Text(
            text = progress(day),
            style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 13.sp),
            maxLines = 1,
            modifier = GlanceModifier.defaultWeight(),
        )
        AddButton(size = 36.dp)
    }
}

@Composable
internal fun subtitle(day: WidgetDay): String {
    val date = widgetDateLabel(day.date, LocalContext.current)
    return if (day.scheduledCount == 0) date else "$date · ${progress(day)}"
}

@Composable
internal fun progress(day: WidgetDay): String =
    LocalContext.current.resources.getQuantityString(
        R.plurals.widget_today_progress,
        day.scheduledCount,
        day.completedCount,
        day.scheduledCount,
    )

@Composable
internal fun AddButton(
    size: Dp,
    clickable: Boolean = true,
) {
    val description = LocalContext.current.getString(R.string.add_task)
    val base =
        GlanceModifier
            .size(size)
            .background(GlanceTheme.colors.primary)
            .cornerRadius(size / 2)
            .semantics { contentDescription = description }
    Box(
        modifier = if (clickable) base.clickable(openNewTaskAction()) else base,
        contentAlignment = Alignment.Center,
    ) {
        Image(
            provider = ImageProvider(R.drawable.ic_add),
            contentDescription = null,
            colorFilter = ColorFilter.tint(GlanceTheme.colors.onPrimary),
            modifier = GlanceModifier.size(24.dp),
        )
    }
}
