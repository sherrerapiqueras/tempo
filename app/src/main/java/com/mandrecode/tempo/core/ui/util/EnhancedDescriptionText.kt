package com.mandrecode.tempo.core.ui.util

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.core.net.toUri

private const val URL_ANNOTATION_TAG = "tempo_url"

private val LINK_PATTERN =
    Regex(
        pattern =
            """(?i)\b((?:https?://)[^\s<>()]+|""" +
                """www\.[^\s<>()]+|""" +
                """(?<![@/:])(?:[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?\.)+[a-z]{2,}(?:/[^\s<>()]*)?)""",
    )

@Composable
fun EnhancedDescriptionText(
    text: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
    color: Color = LocalContentColor.current,
    linkColor: Color = MaterialTheme.colorScheme.primary,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
    onTextLayout: (TextLayoutResult) -> Unit = {},
) {
    val context = LocalContext.current
    val annotatedText =
        remember(text, linkColor) {
            buildEnhancedDescriptionText(text = text, linkColor = linkColor)
        }
    var textLayoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }

    Text(
        text = annotatedText,
        style = style,
        color = color,
        maxLines = maxLines,
        overflow = overflow,
        onTextLayout = { layoutResult ->
            textLayoutResult = layoutResult
            onTextLayout(layoutResult)
        },
        modifier =
            modifier.pointerInput(annotatedText) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    val up = waitForUpOrCancellation() ?: return@awaitEachGesture
                    val layoutResult = textLayoutResult ?: return@awaitEachGesture
                    val offset = layoutResult.getOffsetForPosition(up.position)
                    val uri =
                        annotatedText
                            .getStringAnnotations(
                                tag = URL_ANNOTATION_TAG,
                                start = offset,
                                end = offset,
                            ).firstOrNull()
                            ?.item

                    if (uri != null) {
                        up.consume()
                        openExternalUri(context = context, uri = uri)
                    }
                }
            },
    )
}

internal fun buildEnhancedDescriptionText(
    text: String,
    linkColor: Color,
): AnnotatedString =
    buildAnnotatedString {
        var currentIndex = 0
        findDescriptionLinks(text).forEach { link ->
            val range = link.range
            append(text.substring(currentIndex, range.first))
            val displayText = text.substring(range)
            pushStringAnnotation(tag = URL_ANNOTATION_TAG, annotation = link.uri)
            withStyle(
                SpanStyle(
                    color = linkColor,
                    textDecoration = TextDecoration.Underline,
                ),
            ) {
                append(displayText)
            }
            pop()
            currentIndex = range.last + 1
        }

        if (currentIndex < text.length) {
            append(text.substring(currentIndex))
        }
    }

internal fun normalizeDescriptionUri(rawValue: String): String =
    if (rawValue.startsWith("www.", ignoreCase = true) || rawValue.none { it == ':' }) {
        "https://$rawValue"
    } else {
        rawValue
    }

internal fun openExternalUri(
    context: Context,
    uri: String,
) {
    val intent =
        Intent(Intent.ACTION_VIEW, uri.toUri())
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    try {
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        // The system has no app for this URI. Leave the card in place without interrupting the user.
    } catch (_: SecurityException) {
        // External targets may deny access.
    }
}

private fun trimTrailingPunctuation(
    text: String,
    match: MatchResult,
): IntRange {
    var lastIndex = match.range.last
    while (lastIndex >= match.range.first && textNeedsTrimming(text[lastIndex])) {
        lastIndex--
    }
    return match.range.first..lastIndex
}

private fun textNeedsTrimming(character: Char): Boolean = character in ".,;:!?"

internal fun findDescriptionLinks(
    text: String,
    rangeOffset: Int = 0,
): Sequence<DescriptionLink> =
    LINK_PATTERN.findAll(text).mapNotNull { match ->
        val range = trimTrailingPunctuation(text = text, match = match)
        if (range.isEmpty()) {
            null
        } else {
            val displayText = text.substring(range)
            DescriptionLink(
                range = (range.first + rangeOffset)..(range.last + rangeOffset),
                uri = normalizeDescriptionUri(displayText),
            )
        }
    }

internal data class DescriptionLink(
    val range: IntRange,
    val uri: String,
)
