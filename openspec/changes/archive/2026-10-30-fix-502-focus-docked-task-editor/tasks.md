# Tasks

## 1. Focus screen docking

- [x] 1.1 Let `FocusTaskEditor` take a `placement` and render `TaskEditor` with it; keep `BottomSheet` while the Plan tasks sheet is open; update the `FocusEditors.kt` KDoc. Verify Focus unit and UI tests still pass with `./gradlew testDebugUnitTest`.
- [x] 1.2 Restructure `FocusScreen` into the `Row` + `DockedEditorPane` layout with rail clearance, content bias, content-block top and frozen state as in `TasksScreen`, moving the editor between pane and sheet with movable content. Don't report the docked state to navigation (since #501, docking never touches the back stack). Verify on the Pixel Tablet (1280 dp) that opening a task docks it beside the agenda with no scrim and no blink.
- [x] 1.3 Show the edited task as selected in the Focus agenda and close the editor when its own card is tapped again; verify both on the tablet and with a Compose UI test on `FocusContent` for the selected state. The docking decisions are pure functions in `FocusEditorHost.kt`, unit-tested in `FocusEditorHostTest`.
- [x] 1.4 Add `@PreviewTest` screenshot references for Focus with a docked editor at 1280 dp and 1600 dp, light and dark, run `./gradlew updateDebugScreenshotTest`, review the diff report, and verify `./gradlew validateDebugScreenshotTest` passes.

## 2. Device validation

- [x] 2.1 On the Pixel Tablet: open a task from the agenda, open another, re-tap the open one, add a task, add a subtask, and open a task from the Plan tasks sheet; verify each matches the spec scenarios.
- [x] 2.2 On the Large Desktop emulator: with a Focus task docked, resize the window below and above 1200 dp; verify the draft survives the move between pane and sheet.
- [x] 2.3 On a phone emulator: open a task from Focus; verify it's a bottom sheet as before. Note how long the first open takes on a single running emulator. (Observed: the first open after a cold start can take a few seconds while Focus creates its `TasksViewModel`; later opens are immediate. Pre-existing with the sheet, not changed here.)

## 3. Quality gates

- [x] 3.1 Run `./gradlew ktlintFormat ktlintCheck :app:detekt testDebugUnitTest koverVerifyDebug validateDebugScreenshotTest` and verify all pass without growing the detekt baseline.
- [x] 3.2 Run `openspec validate fix-502-focus-docked-task-editor --strict` and verify it passes.

## Workflow follow-up

- Implement after `fix-501-docked-editor-blink` is merged, on branch `fix/502-focus-docked-task-editor`.
- Archive this change with `openspec-archive-change` in the same PR before the final CI run.
- PR title `fix(#502): dock the Focus task editor on large windows`, body with `Closes #502`.
