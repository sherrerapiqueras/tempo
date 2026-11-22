package com.mandrecode.tempo.features.settings.presentation

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.core.net.toUri
import com.mandrecode.tempo.BuildConfig
import com.mandrecode.tempo.R

private const val LOG_TAG = "SettingsScreen"

internal fun openNotificationSettings(context: Context) {
    val intent =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            }
        } else {
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", context.packageName, null)
            }
        }
    try {
        context.startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        Log.e(LOG_TAG, "Unable to open notification settings", e)
    }
}

internal fun openLanguageSettings(context: Context) {
    val intent =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Intent(Settings.ACTION_APP_LOCALE_SETTINGS).apply {
                data = Uri.fromParts("package", context.packageName, null)
            }
        } else {
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", context.packageName, null)
            }
        }
    try {
        context.startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        Log.e(LOG_TAG, "Unable to open language settings", e)
    }
}

internal fun openReview(context: Context) {
    val intent =
        Intent(
            Intent.ACTION_VIEW,
            "market://details?id=${context.packageName}".toUri(),
        )
    try {
        context.startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        Log.e(LOG_TAG, "Play Store app unavailable, opening browser", e)
        openReviewInBrowser(context)
    }
}

private fun openReviewInBrowser(context: Context) {
    val webIntent =
        Intent(
            Intent.ACTION_VIEW,
            "https://play.google.com/store/apps/details?id=${context.packageName}".toUri(),
        )
    try {
        context.startActivity(webIntent)
    } catch (e: ActivityNotFoundException) {
        Log.e(LOG_TAG, "Unable to open Play Store", e)
    }
}

internal fun openFeedback(
    context: Context,
    version: String,
) {
    val url =
        "${BuildConfig.FEEDBACK_FORM_URL}&${BuildConfig.FEEDBACK_VERSION_ENTRY}=${Uri.encode(version)}"
    openExternalLink(context, url, failureLogMessage = "Unable to open feedback form")
}

internal fun openSourceCode(context: Context) {
    openExternalLink(
        context,
        BuildConfig.SOURCE_CODE_URL,
        failureLogMessage = "Unable to open source code repository",
    )
}

internal fun openPrivacyPolicy(context: Context) {
    openExternalLink(
        context,
        BuildConfig.PRIVACY_POLICY_URL,
        failureLogMessage = "Unable to open privacy policy",
    )
}

/**
 * Opens [url] in whichever app handles it, falling back to the shared "no browser" toast when the
 * device has no handler for it.
 */
private fun openExternalLink(
    context: Context,
    url: String,
    failureLogMessage: String,
) {
    val intent = Intent(Intent.ACTION_VIEW, url.toUri())
    try {
        context.startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        Log.e(LOG_TAG, failureLogMessage, e)
        Toast
            .makeText(
                context,
                context.getString(R.string.no_browser_app),
                Toast.LENGTH_SHORT,
            ).show()
    }
}
