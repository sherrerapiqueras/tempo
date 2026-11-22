# Design

## Context

Glance 1.2 renders the widget as RemoteViews, so Compose UI, custom fonts, blur and arbitrary shapes are unavailable; corner radius works on API 31+ only. Tempo's app UI has three patterns that survive that translation: the two-block screen (tinted strip, surface content), connected card groups (outer corners round, 8 dp joins, 3 dp seams; `CardGroupPosition`), and the task check (a circle outlined at low emphasis that becomes a 16 dp rounded square with a primary tint when done). Colour roles follow the agreed convention: `secondaryContainer` for adding things, `primary` for completion, neutral containers for idle.

## Decisions

1. **Data is Focus's agenda.** The widget reads `GetFocusAgendaUseCase(today)`, so "today" has one definition (`FocusDayMembership`). Rows read Today then Overdue, uncompleted first, as in Focus. Mapping from the agenda to widget rows is a pure function (`TodayWidgetState.from`), unit-tested.
2. **Watched content, plus an updater.** While Glance keeps a widget's session open, an update only recomposes it; `provideGlance` does not run again. So the content collects the day as a flow (`widgetDays`, with the first value loaded before composing) rather than rendering a snapshot, which went stale the moment a task changed (found on the emulator). A process-scoped `TodayWidgetUpdater`, started from `TempoApp`, observes the agenda (re-keyed at each local midnight), maps it to `TodayWidgetState`, drops duplicates, debounces, and calls `updateAll` only if a widget is placed. Any process start (app, notification action, alarm, `APPWIDGET_UPDATE`) therefore refreshes it. `updatePeriodMillis = 1800000` is the backstop for a day rollover while the process is dead. That covers the time after Glance has closed the session.
3. **Toggle through the use case.** The check is an `ActionCallback` that resolves `ToggleTaskCompletionUseCase` and `TaskRepository` from a Hilt `@EntryPoint`, re-reads the task, toggles it, then updates the widget. Rollover, reminders and periodicity behave exactly as a tap in the app.
4. **Size buckets (`SizeMode.Exact`).** Narrow (width < 110 dp): quick-add tile with the open count. Low (height < 110 dp): one line with progress and the next open task. Compact (height < 180 dp): small header over the list. Full: header strip with "Today", date, "n of m done" and the add button, over the list block. Exact (rather than Responsive) keeps one RemoteViews in the host's memory.
5. **Keep the receiver's component name.** Renaming the receiver class would change its `ComponentName`, and the launcher would drop every placed instance. Only the `GlanceAppWidget` is renamed.
6. **Previews.** Android 15+ gets a generated preview (`providePreview` at a declared 3×2 `previewSizeMode`, published with `GlanceAppWidgetManager.setWidgetPreviews` once per app version, retried on later starts when rate-limited). Android 12-14 get a static `previewLayout` in the system's dynamic colours; older versions keep the `previewImage` icon.
8. **App language.** A process started only to update the widget resolves resources in the system language, not the per-app one (seen on the emulator: a Spanish Tempo drew an English widget). The widget composes with a context configured with `LocaleManager.applicationLocales`, and formats its date with that locale.
9. **Row taps open the task through an explicit `Intent`.** Inside a lazy list, `actionStartActivity` with action parameters reached `MainActivity` without the extras on a cold start; an explicit intent carrying `EXTRA_TASK_ID` arrives intact.
7. **Fallback colours.** `TempoGlanceColorScheme` is built from Tempo's full light and dark `ColorScheme`s through `ColorProvider(day, night)`, so every role the layout uses exists. Dynamic colour still wins where supported (#231).

## Risks

- `updateAll` on every agenda change could be chatty during bulk edits; the debounce (500 ms) and duplicate filter bound it to one render per settled change.
- On API < 31 the widget has square corners (no `cornerRadius` support). Accepted.
- An already placed 1×1 widget shows the quick-add tile until the user enlarges it; the picker shows the new default size for new placements.
