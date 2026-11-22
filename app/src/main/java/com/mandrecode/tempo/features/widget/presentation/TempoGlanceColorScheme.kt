package com.mandrecode.tempo.features.widget.presentation

import androidx.glance.color.ColorProviders
import androidx.glance.material3.ColorProviders
import com.mandrecode.tempo.core.ui.theme.TempoDarkColorScheme
import com.mandrecode.tempo.core.ui.theme.TempoLightColorScheme

/**
 * Tempo's palette for the widget, used where the system has no dynamic colour to offer (#231).
 *
 * Built from the app's own light and dark schemes, so every role the widget draws with is the
 * matching Tempo colour. Glance resolves the day/night pair against the live configuration on each
 * draw, so a system light/dark switch shows at once without a re-render. (The previous hand-built
 * scheme mapped every role onto four colours, which only worked while the widget drew one icon.)
 *
 * The app's page/surface contrast adjustment is deliberately not applied: on a home screen the
 * widget's tinted background comes from the scheme's own widget-background role.
 */
val TempoGlanceColorScheme: ColorProviders =
    ColorProviders(light = TempoLightColorScheme, dark = TempoDarkColorScheme)
