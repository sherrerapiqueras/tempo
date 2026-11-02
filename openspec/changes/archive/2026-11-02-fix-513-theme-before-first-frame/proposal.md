# Proposal

## Why

When the theme chosen in the app differs from the system's, Android still draws the splash and the window background from the system configuration. A dark app on a light system shows a white splash on cold start, and white in the newly exposed area when a desktop window grows ([#513](https://github.com/sherrerapiqueras/tempo/issues/513), part of [#468](https://github.com/sherrerapiqueras/tempo/issues/468)). Both were reproduced by screen recording on the Pixel Tablet and Large Desktop emulators.

## What Changes

- On Android 12 and up, the app tells the system its chosen theme mode, so the splash and the system-configured resources follow it from the next launch on.
- The window background follows the app's current theme surface at every API level, so a growing window never exposes the other theme's colour.
- Changing the theme in Settings, or the system theme, is handled in place rather than by recreating the activity, as the app already handles size changes.

### Non-goals

- The splash on Android 11 and below, which has no system API for this: it keeps following the system theme.
- The very first launch after installing or updating, before the app has told the system its mode.
- Any change to colours or to the theme settings themselves.

## Capabilities

### New Capabilities

- `theme-mode-window-surfaces`: what the system draws around the app's content follows the theme chosen in the app.

### Modified Capabilities

_None._

## Impact

- `core/ui/theme/Theme.kt` (window background), a new `core/ui/theme/ApplicationNightMode.kt`, `MainActivity.kt`, `AndroidManifest.xml` (`uiMode` in `configChanges`).
- Unit test for the mode mapping; manual validation by screen recording on the tablet and desktop emulators.
