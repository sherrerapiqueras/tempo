# Tasks

- [x] 1.1 Bound the expanded rail title to the rail width (single line, auto-size).
- [x] 1.2 Add `DockedEditorPane` with a 16 dp gutter and end margin, top aligned to the content block; use it in `TasksScreen` and `RoutinesScreen`, and paint the screen `Row` (not just the list box) with `colorScheme.background` so the gutter and margins are tinted.
- [x] 1.3 Report the content block's top offset from `TasksContent` and `RoutinesContent`.
- [x] 1.4 Docked sheet surface: top-rounded, flush bottom, no shadow.
- [x] 1.5 Animate the readable column toward the pane while docked (`adaptiveScreenContentLayout` bias).
- [x] 1.6 Screenshot previews for the docked Tasks screen (list + editor) at 1280 dp and 1600 dp, light and dark; regenerate and review references.
- [x] 1.7 Instrumented `DockedEditorPaneTest` for the top alignment and gutter (the header is measured a frame late, which the preview screenshots cannot capture).
- [x] 1.8 Run `./gradlew ktlintCheck :app:detekt testDebugUnitTest validateDebugScreenshotTest lintDebug` and check the tablet emulator at 1280 dp and 1600 dp, light and dark.
