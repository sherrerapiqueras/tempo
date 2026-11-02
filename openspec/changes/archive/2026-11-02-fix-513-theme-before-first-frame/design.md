# Design

## Context

See proposal.md. The splash and the window background come from the manifest theme, resolved against the system's night mode, before Compose runs. `MainActivity` applies the app's `ThemeMode` only inside Compose.

## Goals / Non-Goals

**Goals:**
- No white frame around a dark app, and no dark frame around a light one, wherever the platform allows it.

**Non-Goals:**
- A custom splash for Android 11 and below.

## Decisions

**Tell the system the mode with `UiModeManager.setApplicationNightMode` (API 31+).** It is the platform's way to make the splash follow an app-level choice, and the system persists it across launches. It also makes `values-night` resources and `isSystemInDarkTheme()` follow the app, which fixes the one-frame mismatch of the `MainUiState.Loading` surface too. `ThemeMode.SYSTEM` maps to `MODE_NIGHT_AUTO`, so it really does follow the system again. AppCompat's delegate isn't used because the app has no AppCompat dependency, and none may be added without asking.

**Set the window background from `TempoTheme`.** It already holds the activity lookup and the resolved scheme in its `SideEffect`, and this covers every API level and both dynamic and Tempo colours, which a resource could not. Surface rather than background, matching the root `Surface` in `MainActivity`.

**Handle `uiMode` in `configChanges`.** The new call changes the activity's configuration, which would otherwise recreate it on every change in Settings. Compose already reacts to configuration changes, and the app has no other night-qualified resources it reads at runtime.

**Alternative considered: a neutral splash colour.** It would avoid the mismatch without any API, but would be wrong for both themes and wouldn't help the window background.

## Risks / Trade-offs

- [The first launch after updating still shows the old splash] → Accepted: the system only knows the mode once the app has told it.
- [Android 11 and below keep the system-themed splash] → Accepted: no API exists; the window background fix still applies there.
