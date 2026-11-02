package com.mandrecode.tempo.core.ui.theme

import android.app.ActivityManager
import android.content.Context
import android.content.ContextWrapper
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import com.mandrecode.tempo.util.dynamicColorScheme
import com.mandrecode.tempo.util.supportsDynamicColor

/**
 * Tempo brand colors from the logo.
 * All color values are defined in Color.kt.
 */

private val LightColorScheme = lightColorScheme()

private val DarkColorScheme = darkColorScheme()

private val TempoLightColorScheme =
    lightColorScheme(
        primary = TempoLightPrimary,
        onPrimary = TempoLightOnPrimary,
        primaryContainer = TempoLightPrimaryContainer,
        onPrimaryContainer = TempoLightOnPrimaryContainer,
        inversePrimary = TempoLightInversePrimary,
        secondary = TempoLightSecondary,
        onSecondary = TempoLightOnSecondary,
        secondaryContainer = TempoLightSecondaryContainer,
        onSecondaryContainer = TempoLightOnSecondaryContainer,
        tertiary = TempoLightTertiary,
        onTertiary = TempoLightOnTertiary,
        tertiaryContainer = TempoLightTertiaryContainer,
        onTertiaryContainer = TempoLightOnTertiaryContainer,
        background = TempoLightBackground,
        onBackground = TempoLightOnBackground,
        surface = TempoLightSurface,
        onSurface = TempoLightOnSurface,
        surfaceVariant = TempoLightSurfaceVariant,
        onSurfaceVariant = TempoLightOnSurfaceVariant,
        surfaceTint = TempoLightSurfaceTint,
        inverseSurface = TempoLightInverseSurface,
        inverseOnSurface = TempoLightInverseOnSurface,
        error = TempoLightError,
        onError = TempoLightOnError,
        errorContainer = TempoLightErrorContainer,
        onErrorContainer = TempoLightOnErrorContainer,
        outline = TempoLightOutline,
        outlineVariant = TempoLightOutlineVariant,
        scrim = TempoLightScrim,
        surfaceDim = TempoLightSurfaceDim,
        surfaceBright = TempoLightSurfaceBright,
        surfaceContainerLowest = TempoLightSurfaceContainerLowest,
        surfaceContainerLow = TempoLightSurfaceContainerLow,
        surfaceContainer = TempoLightSurfaceContainer,
        surfaceContainerHigh = TempoLightSurfaceContainerHigh,
        surfaceContainerHighest = TempoLightSurfaceContainerHighest,
    )

private val TempoDarkColorScheme =
    darkColorScheme(
        primary = TempoDarkPrimary,
        onPrimary = TempoDarkOnPrimary,
        primaryContainer = TempoDarkPrimaryContainer,
        onPrimaryContainer = TempoDarkOnPrimaryContainer,
        inversePrimary = TempoDarkInversePrimary,
        secondary = TempoDarkSecondary,
        onSecondary = TempoDarkOnSecondary,
        secondaryContainer = TempoDarkSecondaryContainer,
        onSecondaryContainer = TempoDarkOnSecondaryContainer,
        tertiary = TempoDarkTertiary,
        onTertiary = TempoDarkOnTertiary,
        tertiaryContainer = TempoDarkTertiaryContainer,
        onTertiaryContainer = TempoDarkOnTertiaryContainer,
        background = TempoDarkBackground,
        onBackground = TempoDarkOnBackground,
        surface = TempoDarkSurface,
        onSurface = TempoDarkOnSurface,
        surfaceVariant = TempoDarkSurfaceVariant,
        onSurfaceVariant = TempoDarkOnSurfaceVariant,
        surfaceTint = TempoDarkSurfaceTint,
        inverseSurface = TempoDarkInverseSurface,
        inverseOnSurface = TempoDarkInverseOnSurface,
        error = TempoDarkError,
        onError = TempoDarkOnError,
        errorContainer = TempoDarkErrorContainer,
        onErrorContainer = TempoDarkOnErrorContainer,
        outline = TempoDarkOutline,
        outlineVariant = TempoDarkOutlineVariant,
        scrim = TempoDarkScrim,
        surfaceDim = TempoDarkSurfaceDim,
        surfaceBright = TempoDarkSurfaceBright,
        surfaceContainerLowest = TempoDarkSurfaceContainerLowest,
        surfaceContainerLow = TempoDarkSurfaceContainerLow,
        surfaceContainer = TempoDarkSurfaceContainer,
        surfaceContainerHigh = TempoDarkSurfaceContainerHigh,
        surfaceContainerHighest = TempoDarkSurfaceContainerHighest,
    )

@Suppress("ktlint:compose:compositionlocal-allowlist")
val LocalIsDarkTheme = compositionLocalOf { false }

@Composable
fun TempoTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color is available on Android 12+
    dynamicColor: Boolean = true,
    useTempoColors: Boolean = false,
    content: @Composable () -> Unit,
) {
    val baseColorScheme =
        when {
            useTempoColors -> {
                if (darkTheme) TempoDarkColorScheme else TempoLightColorScheme
            }

            dynamicColor && supportsDynamicColor -> {
                val context = LocalContext.current
                dynamicColorScheme(context, isDark = darkTheme)
            }

            darkTheme -> DarkColorScheme
            else -> LightColorScheme
        }
    val colorScheme = baseColorScheme.withPageSurfaceContrast(darkTheme)

    val windowBackground = colorScheme.surface.toArgb()
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val activity = view.context.findComponentActivity() ?: return@SideEffect
            // Use explicit dark/light styles (never auto): `enableEdgeToEdge` only sets
            // isNavigationBarContrastEnforced = false when nightMode is NIGHT_YES/NIGHT_NO. With
            // SystemBarStyle.auto (nightMode = NIGHT_AUTO) it forces contrast enforcement back on,
            // which paints a scrim behind the gesture nav bar and breaks transparency.
            val style =
                if (darkTheme) {
                    SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                } else {
                    SystemBarStyle.light(
                        android.graphics.Color.TRANSPARENT,
                        android.graphics.Color.TRANSPARENT,
                    )
                }
            activity.enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                activity.window.isNavigationBarContrastEnforced = false
            }
            // Otherwise the window keeps the manifest theme's background, chosen by the system's
            // night mode: area the app hasn't drawn yet, such as a desktop window growing, would
            // flash that colour when the app's theme differs (#513).
            activity.window.setBackgroundDrawable(ColorDrawable(windowBackground))
        }
    }

    val glass =
        remember(colorScheme, darkTheme) {
            tempoGlass(
                colorScheme = colorScheme,
                darkTheme = darkTheme,
                blurEnabled = supportsGlassBlur(view),
            )
        }

    CompositionLocalProvider(
        LocalIsDarkTheme provides darkTheme,
        LocalTempoGlass provides glass,
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content,
        )
    }
}

/**
 * Backdrop blur runs a RenderEffect over a recorded layer, which only exists from API 31. Previews
 * (layoutlib) and low-RAM devices get the denser no-blur tint instead: the first cannot render it,
 * the second should not spend the memory on it.
 */
private fun supportsGlassBlur(view: View): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || view.isInEditMode) return false
    val activityManager = view.context.getSystemService(ActivityManager::class.java)
    return activityManager?.isLowRamDevice != true
}

private tailrec fun Context.findComponentActivity(): ComponentActivity? =
    when (this) {
        is ComponentActivity -> this
        is ContextWrapper -> baseContext.findComponentActivity()
        else -> null
    }

internal fun ColorScheme.withPageSurfaceContrast(darkTheme: Boolean): ColorScheme =
    if (darkTheme) {
        copy(
            background = surfaceContainer,
            surface = surfaceContainerLowest,
            surfaceContainerLow = surfaceContainerLowest,
            surfaceContainer = surfaceContainerLow,
            // Down one, as the light branch does. Left at `surfaceContainer` this landed on the
            // same value `background` already had, so a card — which is drawn on `background` —
            // was the exact colour of the sheet underneath it and disappeared into it. Nothing
            // showed it in a flat card; a card with a two-tone split showed it immediately.
            surfaceContainerHigh = surfaceContainerLow,
            surfaceContainerHighest = surfaceContainerHigh,
        )
    } else {
        copy(
            background = surfaceContainer,
            surface = surfaceContainerLowest,
            surfaceContainerLow = surfaceContainerLowest,
            surfaceContainer = surface,
            surfaceContainerHigh = surfaceContainerLow,
            surfaceContainerHighest = surfaceContainerHigh,
        )
    }
