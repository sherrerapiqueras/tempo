package com.mandrecode.tempo.features.widget.presentation

import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import kotlinx.datetime.LocalDate
import kotlinx.datetime.number
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * [this] context in the language the user picked for Tempo (Android 13+ per-app language).
 *
 * The widget is often drawn by a process the system started only to update it, and that process's
 * resources follow the system language, not the app's: a Spanish Tempo on an English phone drew an
 * English widget. The app's own language is read back from [LocaleManager] and applied here.
 */
internal fun Context.inAppLanguage(): Context {
    val locales =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getSystemService(LocaleManager::class.java)?.applicationLocales?.takeUnless { it.isEmpty }
        } else {
            null
        }
    return locales?.let { createConfigurationContext(Configuration(resources.configuration).apply { setLocales(it) }) }
        ?: this
}

/** "Sunday, 22 Nov" in [context]'s language, which [Locale.getDefault] may not be (see above). */
internal fun widgetDateLabel(
    date: LocalDate,
    context: Context,
): String {
    val locale = context.resources.configuration.locales[0] ?: Locale.getDefault()
    val calendar = Calendar.getInstance().apply { set(date.year, date.month.number - 1, date.day) }
    val weekday =
        SimpleDateFormat("EEEE", locale).format(calendar.time).replaceFirstChar { it.titlecase(locale) }
    return "$weekday, ${SimpleDateFormat("d MMM", locale).format(calendar.time)}"
}
