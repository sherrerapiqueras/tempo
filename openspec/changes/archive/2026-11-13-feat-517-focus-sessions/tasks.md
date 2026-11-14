# Tasks

## 1. Running timer model

- [x] 1.1 Make `FocusSession.taskId` nullable, rename `taskTitle` to `title`, add `SessionCycle` and `nextPhase`; verify with new cases in `FocusSessionTest`
- [x] 1.2 Persist the cycle and the optional task in `FocusSessionRepositoryImpl`, reading old records unchanged; verify in `FocusSessionRepositoryImplTest`
- [x] 1.3 Add `startSaved`, `timeUp` and `skipBreak` to `FocusSessionUseCases`, banking against a task only when there is one; verify in `FocusSessionUseCasesTest`

## 2. Saved session data layer

- [x] 2.1 Add `SavedSession` model, repository interface and CRUD use cases with validation; verify with use case unit tests
- [x] 2.2 Add `SavedSessionEntity`, DAO, mapper, repository implementation, Hilt bindings and migration 13 → 14; verify `./gradlew kspDebugKotlin` exports `app/schemas/**/14.json` and mapper tests pass
- [x] 2.3 Carry saved sessions in backups (DTO, mapper, replace, merge) and document the field in `docs/BACKUP_FORMAT.md`; verify with backup mapper/repository tests

## 3. Infrastructure

- [x] 3.1 End receiver advances rounds through `timeUp` and only announces a real end; notification actions omit Mark done without a task; verify `./gradlew assembleDebug` and the receiver-facing use case tests

## 4. Focus presentation

- [x] 4.1 Extend the contract and view model: saved sessions in state, subject key, session editor events, start/replace/delete, finished-sheet choices for a session; verify in `FocusViewModelTest`, `FocusSessionViewModelTest` and `FocusUiStateTest`
- [x] 4.2 Build the session editor sheet and task picker from existing components, with previews under `src/debug/`; verify previews render in `assembleDebug`
- [x] 4.3 Add the Sessions row, round status and task-less controls to the Focus content, running card, session sheet and finished sheet; verify `FocusContentTest` compiles and screenshot tests pass or are regenerated after review
- [x] 4.4 Point the Focus add button at the session editor and remove task creation from Focus; verify floating-bar and pane-host tests

## 5. Strings

- [x] 5.1 Add session strings and rename the undated plural in `values/` and `values-es/`; verify `./gradlew lintDebug` reports no `MissingTranslation`/`ExtraTranslation`

## 6. Integration checks

- [x] 6.1 `openspec validate feat-517-focus-sessions` passes
- [x] 6.2 `./gradlew ktlintFormat ktlintCheck :app:detekt` passes with the baseline not grown
- [x] 6.3 `./gradlew testDebugUnitTest koverVerifyDebug` passes
- [x] 6.4 `./gradlew validateDebugScreenshotTest` passes
- [x] 6.5 Smoke test on a device or emulator: create, start, cycle rounds, link to a task, delete

## Workflow follow-up

- Archive the change in the same PR once verification is complete.
