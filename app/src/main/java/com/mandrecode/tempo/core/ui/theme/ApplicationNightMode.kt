package com.mandrecode.tempo.core.ui.theme

import android.app.UiModeManager
import android.content.Context
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import com.mandrecode.tempo.core.domain.model.ThemeMode

/**
 * Tells the system which theme mode the app uses, so what it draws before the app can, the splash
 * above all, stops following the system's mode instead (#513). The system keeps the value across
 * launches, so it takes effect from the next cold start. Android 12 and up only; below that the
 * splash has no such API and follows the system.
 *
 * This also changes what `isSystemInDarkTheme()` reports to this app, which is what makes
 * [ThemeMode.SYSTEM] need its own value here rather than leaving the last one in place.
 */
@Composable
internal fun ApplyApplicationNightMode(themeMode: ThemeMode) {
    val context = LocalContext.current
    LaunchedEffect(themeMode) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context
                .getSystemService(Context.UI_MODE_SERVICE)
                .let { it as UiModeManager }
                .setApplicationNightMode(themeMode.toUiModeManagerNightMode())
        }
    }
}

internal fun ThemeMode.toUiModeManagerNightMode(): Int =
    when (this) {
        ThemeMode.LIGHT -> UiModeManager.MODE_NIGHT_NO
        ThemeMode.DARK -> UiModeManager.MODE_NIGHT_YES
        ThemeMode.SYSTEM -> UiModeManager.MODE_NIGHT_AUTO
    }
