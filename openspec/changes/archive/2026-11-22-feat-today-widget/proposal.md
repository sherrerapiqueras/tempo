# Proposal

No originating issue: raised in the pre-release review (the widget was a half-finished design with a broken rendering).

## Why

The only home-screen widget is a 1×1 "add task" icon. It cannot be resized, it ignores the system's widget corner radius, its picker preview is a bare vector, and its fallback colour scheme maps every Material role onto four colours, so anything beyond the icon would draw in the wrong colours. It shows nothing of the user's day, which is what a task app's widget is for.

## What Changes

- The widget becomes **Today**: the same day Focus shows (due today, then overdue and still open), in Tempo's own visual language: a tinted header strip over a surface block, tasks as one connected group of cards, Tempo's circular check that turns into a filled rounded square when done, and the "add" action as the widget's one `primary` action.
- Each task can be completed (or un-completed) from the widget. Tapping a task opens it in the app; the add button opens the new-task sheet, as the old widget did.
- The widget is resizable and lays itself out by size: a quick-add tile at 1×1 (what an already placed widget keeps showing until it is enlarged), a one-line summary when one row high, a compact header over the list when short, and the full header with date and progress otherwise.
- It updates when tasks change in the app, from a notification action, or when the day rolls over, its picker preview is generated from the real layout on Android 15+ (a static layout in system colours on 12-14), and it is drawn in the app's own language.
- The Glance fallback colour scheme (used where dynamic colour is unavailable) maps every role from Tempo's light and dark palettes.

**Non-goals**

- Habits and habit chains (Focus excludes them too, #404).
- Starting a focus session from the widget.
- Spaces (#331): the widget shows every category, as Focus does.
- Tempo's bundled Google Sans Flex: RemoteViews can only use system font families.
- A configuration activity: there is nothing to configure.

## Capabilities

### New Capabilities

- `home-screen-widget`: what the Today widget shows at each size, its actions, how it stays current, and its theming.

### Modified Capabilities

None.

## Impact

- **UI (Glance):** `features/widget/presentation`. The provider class is renamed (`QuickAddTaskWidget` → `TodayWidget`) but the receiver keeps its class name `QuickAddTaskWidgetReceiver`, so widgets users have already placed upgrade in place instead of disappearing.
- **Infrastructure:** a process-scoped updater started from `TempoApp`, and a Glance action callback that toggles completion through `ToggleTaskCompletionUseCase`.
- **Resources:** widget info XML (resizable, 3×2 default), new drawables, en/es strings, `updatePeriodMillis` of 30 min as the day-rollover backstop when the process is dead.
- **Settings:** the existing widget refresh on the Tempo-colours toggle now targets the new widget.
- No data, schema or backup changes.
