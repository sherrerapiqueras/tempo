# Tasks

## 1. Model and state

- [x] 1.1 Let a round cycle exist without a saved session and store it; verify in `FocusSessionTest` and `FocusSessionRepositoryImplTest`
- [x] 1.2 Hold the session being set up in `FocusSessionRepository` and derive the sheet's subject from it; verify in `FocusUiStateTest`
- [x] 1.3 Route add/edit/save/start/delete through the session sheet in the view model, with task runs taking rest and rounds; verify in `FocusSavedSessionViewModelTest` and `FocusSessionViewModelTest`

## 2. Session sheet

- [x] 2.1 Fold the session form into the session sheet: editable name, shared setup rows, one Start, animated change into the running controls; verify with `FocusSessionSheetTest` (instrumented)
- [x] 2.2 Show round markers on the sheet and the running card; verify in `FocusSessionSheetTest` and `FocusContentTest`

- [x] 2.3 Start-align the heading, size the sheet to its content and replace the ring with the expressive dial on sheet and card; verify on the emulator and in `FocusSessionSheetTest`
- [x] 2.4 One linked-task row for every kind of session, always removable before starting, opening the task's own sheet; remove Open in Tasks and its navigation callback; verify in `FocusSessionSheetTest`, `FocusUiStateTest` and `FocusPaneHostTest`

## 3. Focus row

- [x] 3.1 Put saved sessions in the Up next row and drop the Sessions label; verify in `FocusContentTest` and screenshot tests

## 4. Integration checks

- [x] 4.1 `openspec validate feat-517-unified-session-sheet` passes
- [x] 4.2 `./gradlew ktlintCheck :app:detekt :app:lintDebug testDebugUnitTest koverVerifyDebug` passes with the detekt baseline not grown
- [x] 4.3 Instrumented tests for the touched classes pass on an emulator
- [x] 4.4 Smoke test on the Pixel 10 emulator: new session, saved session and task all start in the same sheet

## Workflow follow-up

- Archive the change in the same PR once verification is complete.
