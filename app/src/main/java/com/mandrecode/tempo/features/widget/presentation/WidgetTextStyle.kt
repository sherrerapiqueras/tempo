package com.mandrecode.tempo.features.widget.presentation

import android.graphics.Typeface
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.text.FontFamily
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider

/** The widget's type scale. */
internal enum class WidgetTextStyle(
    val size: TextUnit,
    val weight: FontWeight,
    val maxLines: Int,
) {
    /** The full header's "Today". */
    Header(22.sp, FontWeight.Medium, 1),

    /** "Today" where there is less room. */
    Title(16.sp, FontWeight.Medium, 1),

    /** A task's title, up to two lines. */
    Task(15.sp, FontWeight.Medium, 2),

    /** Supporting text: the date, the progress, the empty day. */
    Body(13.sp, FontWeight.Normal, 1),

    /** A task's time and category. */
    Meta(12.sp, FontWeight.Normal, 1),
}

/**
 * The system's Google Sans, the family Tempo's own Google Sans Flex belongs to, where the device has
 * it (Pixel devices ship it as `google-sans`, with a separate `google-sans-medium`).
 *
 * A widget cannot use the app's bundled font: Glance's text only names system font families, and
 * a launcher inflating RemoteViews does not load font resources from the app (tried: the text fell
 * back to Roboto). Where the family is missing, naming it would also lose the medium weight, so the
 * text keeps the system default family and its own weight instead.
 */
private val systemHasGoogleSans: Boolean by lazy {
    Typeface.create(GOOGLE_SANS, Typeface.NORMAL) != Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
}

private const val GOOGLE_SANS = "google-sans"
private const val GOOGLE_SANS_MEDIUM = "google-sans-medium"

private fun WidgetTextStyle.textStyle(color: ColorProvider): TextStyle =
    when {
        !systemHasGoogleSans -> TextStyle(color = color, fontSize = size, fontWeight = weight)
        // The medium cut is its own family; asking for Medium weight on top would do nothing.
        weight == FontWeight.Medium ->
            TextStyle(color = color, fontSize = size, fontFamily = FontFamily(GOOGLE_SANS_MEDIUM))
        else -> TextStyle(color = color, fontSize = size, fontWeight = weight, fontFamily = FontFamily(GOOGLE_SANS))
    }

@Composable
internal fun WidgetText(
    text: String,
    style: WidgetTextStyle,
    modifier: GlanceModifier = GlanceModifier,
    color: ColorProvider = GlanceTheme.colors.onSurface,
) {
    Text(
        text = text,
        style = style.textStyle(color),
        maxLines = style.maxLines,
        modifier = modifier,
    )
}
