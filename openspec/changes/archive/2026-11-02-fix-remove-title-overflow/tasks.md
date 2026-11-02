# Tasks

## 1. Remove the move

- [x] 1.1 Add `resolveEditorTitleEdit` and remove `EditorTitleSplit`, `EditorTitleFieldState` and `EDITOR_MAX_TITLE_LENGTH`
- [x] 1.2 Use it in the task and habit sheets, and remove their focus jump
- [x] 1.3 Return `EditorTitleField` to a `String` value, handling Enter as a key press
- [x] 1.4 Warn before discarding when an autosaving editor's title is over the validation limit
- [x] 1.5 Remove the unused description focus requester

## 2. Tests

- [x] 2.1 Replace the split unit tests with tests for `resolveEditorTitleEdit`
- [x] 2.2 Replace the overflow instrumented tests in both sheets with long-title and paste tests

## 3. Verification

- [x] 3.1 `openspec validate fix-remove-title-overflow --strict`
- [x] 3.2 `./gradlew ktlintCheck :app:detekt testDebugUnitTest koverVerifyDebug`
- [x] 3.3 Sheet instrumented tests on an emulator
- [x] 3.4 Real on-screen keyboard check past 65 characters, compared with the control
- [x] 3.5 Long title check on cards, Focus and the chain selector
