# Tasks

## 1. Follow the app's theme

- [x] 1.1 Set the window background from the resolved scheme in `TempoTheme`, and verify by recording a desktop window growing with a dark app on a light system: peak frame brightness fell from 91 to 34 with no white block.
- [x] 1.2 Add `ApplyApplicationNightMode` and call it from `MainActivity`, unit-test the mode mapping, and verify by recording a cold start with a dark app on a light system on the tablet and desktop emulators: peak brightness fell from 253 to 53, with a dark splash.
- [x] 1.3 Add `uiMode` to `configChanges` and verify on the tablet that switching Light, Dark and System, and switching the system theme while on System, recolours in place with no activity destroyed.

## 2. Quality gates

- [x] 2.1 Run `ktlintCheck`, `:app:detekt`, `lintDebug`, `testDebugUnitTest`, `koverVerifyDebug`: all pass with the detekt baseline at 155. `validateDebugScreenshotTest` shows only known macOS differences.
- [x] 2.2 Run `openspec validate fix-513-theme-before-first-frame --strict`.

## Not verified on a device

- The "Back to following the system" splash scenario: the host was too loaded to capture the cold start in System mode. System mode was verified in the running app.
- Android 11 and below, where only the window background applies.

## Workflow follow-up

- Archive in the same PR; PR body with `Closes #513` and `Part of #468`, base `fix/510-sections-list-layout`.
