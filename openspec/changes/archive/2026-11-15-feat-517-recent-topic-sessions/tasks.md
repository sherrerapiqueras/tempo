# Tasks

## 1. Data

- [x] 1.1 Reshape `focus_sessions` (drop the task link, add last-used), order and trim in the DAO, and add `remember`; verify schema `14.json` regenerates and repository tests pass
- [x] 1.2 Carry last-used in backups and drop the task link; verify backup mapper, validator and repository tests

## 2. Presentation

- [x] 2.1 Replace save/edit/delete/link with starting a new session and remembering it; verify view-model and state tests
- [x] 2.2 Show recents in the sheet, remove the footer, picker and unlink, keep the task chip as a link; verify `FocusSessionSheetTest`
- [x] 2.3 Remove session cards from Up next; verify `FocusContentTest`

## 3. Integration checks

- [x] 3.1 `openspec validate feat-517-recent-topic-sessions` passes
- [x] 3.2 `./gradlew ktlintCheck :app:detekt :app:lintDebug testDebugUnitTest koverVerifyDebug` passes with the detekt baseline not grown
- [x] 3.3 Instrumented tests for the touched classes pass on an emulator
- [x] 3.4 Smoke test on the Pixel 10 emulator after clearing debug data: start a topic, see it in recents, reuse it

## Workflow follow-up

- Archive the change in the same PR once verification is complete.
