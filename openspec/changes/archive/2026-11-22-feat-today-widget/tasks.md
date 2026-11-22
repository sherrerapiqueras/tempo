# Tasks

## 1. Widget model and data
- [x] 1.1 `TodayWidgetState` (+ rows) and the pure `from(agenda)` mapping; unit tests for order, overdue labels, counts and the empty day.
- [x] 1.2 Hilt `@EntryPoint` exposing the agenda use case, the toggle use case, the task repository and the clock.

## 2. Glance UI
- [x] 2.1 `TodayWidget` (`SizeMode.Exact`) with the tiny, compact and full layouts in Tempo's style; new drawables and en/es strings.
- [x] 2.2 Toggle `ActionCallback`; row and add actions through `MainActivity`'s existing extras.
- [x] 2.3 Full Tempo light/dark `TempoGlanceColorScheme`.
- [x] 2.4 Widget info XML: resizable, 3×2 target, 30 min update period; receiver keeps its class name.
- [x] 2.5 Generated preview on Android 15+.

## 3. Keeping it current
- [x] 3.1 `TodayWidgetUpdater` started from `TempoApp`; midnight re-keying, debounce, placed-instance check.
- [x] 3.2 Settings' widget refresh points at the new widget.

## 4. Verification
- [x] 4.1 `openspec validate feat-today-widget`.
- [x] 4.2 `./gradlew ktlintFormat ktlintCheck :app:detekt testDebugUnitTest koverVerifyDebug lintDebug assembleRelease`.
- [x] 4.3 Placed on a Pixel 10 AVD (API 37): 1×1, 4×1, 3×2 and 4×4, light and dark with dynamic colours; completed tasks and opened them from it (reopening a completed row was not exercised: the sample day had no completed task due today); app language ≠ system language; picker preview. Tempo static colours not exercised: the AVD always has dynamic colour.
