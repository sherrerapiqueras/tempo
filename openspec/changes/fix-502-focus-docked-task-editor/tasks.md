# Tasks

## 1. Navigation

- [ ] 1.1 Add `FocusEditorRoute : EditorRoute` supporting `FocusRoute`, register its empty entry with `EDITOR_ROUTE_METADATA`, and report visibility from `FocusDestination` via `navigator.setEditorVisible`; verify with a scene strategy unit test that `[Focus, FocusEditor]` produces the same scene key as `[Focus]` (requires `fix-501-docked-editor-blink`).

## 2. Focus screen docking

- [ ] 2.1 Let `FocusTaskEditor` take a `placement` and render `TaskEditor` with it; keep `BottomSheet` while the Plan tasks sheet is open; update the `FocusEditors.kt` KDoc. Verify Focus unit and UI tests still pass with `./gradlew testDebugUnitTest`.
- [ ] 2.2 Restructure `FocusScreen` into the `Row` + `DockedEditorPane` layout with rail clearance, content bias, content-block top and frozen state as in `TasksScreen`, moving the editor between pane and sheet with movable content. Verify on the Pixel Tablet (1280 dp) that opening a task docks it beside the agenda with no scrim and no blink.
- [ ] 2.3 Show the edited task as selected in the Focus agenda and close the editor when its own card is tapped again; verify both on the tablet and with a Compose UI test on `FocusContent` for the selected state.
- [ ] 2.4 Add `@PreviewTest` screenshot references for Focus with a docked editor at 1280 dp and 1600 dp, light and dark, run `./gradlew updateDebugScreenshotTest`, review the diff report, and verify `./gradlew validateDebugScreenshotTest` passes.

## 3. Device validation

- [ ] 3.1 On the Pixel Tablet: open a task from the agenda, open another, re-tap the open one, add a task, add a subtask, and open a task from the Plan tasks sheet; verify each matches the spec scenarios.
- [ ] 3.2 On the Large Desktop emulator: with a Focus task docked, resize the window below and above 1200 dp; verify the draft survives the move between pane and sheet.
- [ ] 3.3 On a phone emulator: open a task from Focus; verify it's a bottom sheet as before. Note how long the first open takes on a single running emulator.

## 4. Quality gates

- [ ] 4.1 Run `./gradlew ktlintFormat ktlintCheck :app:detekt testDebugUnitTest koverVerifyDebug validateDebugScreenshotTest` and verify all pass without growing the detekt baseline.
- [ ] 4.2 Run `openspec validate fix-502-focus-docked-task-editor --strict` and verify it passes.

## Workflow follow-up

- Implement after `fix-501-docked-editor-blink` is merged, on branch `fix/502-focus-docked-task-editor`.
- Archive this change with `openspec-archive-change` in the same PR before the final CI run.
- PR title `fix(#502): dock the Focus task editor on large windows`, body with `Closes #502`.
