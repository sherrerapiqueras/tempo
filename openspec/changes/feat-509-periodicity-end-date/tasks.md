# Tasks

## 1. Data model and persistence

- [x] 1.1 Add `periodicityEndDate: LocalDate?` to `Task` and `TaskEntity`, a `LocalDate` converter in `Converters`, and the `TaskMapper` fields; verify with a mapper and converter unit test.
- [x] 1.2 Add `MIGRATION_12_13`, bump the database to version 13 and generate the schema with `./gradlew kspDebugKotlin`; verify `13.json` is produced and a 12→13 case in `MigrationTest` passes on an AVD.
- [x] 1.3 Clear the column in `TaskDao.clearAllReminders`; verify with a DAO test.
- [x] 1.4 Add the optional field to `TaskBackupDto` and `BackupMapper`; verify with a round-trip test and a test that restores JSON lacking the field.

## 2. Series rule in the domain layer

- [x] 2.1 Add `Task.withoutRecurrence()` and use it at the archive, detach and rolled-over-original sites; verify existing `ToggleTaskCompletionUseCase` and `RollOverduePeriodicTaskUseCase` tests stay green.
- [x] 2.2 Add `TaskReminderDateUtil.nextOccurrenceWithinEnd`; verify unit tests cover every periodicity, inclusive end date, an interval stepping over the end date, hourly on the end date, and a fast-forward past the end date.
- [x] 2.3 Guard `advanceReminderIfNeeded` so it never passes the end date; verify with unit tests, including create and update with a past reminder and a same-day end date.
- [x] 2.4 Use the rule in `ToggleTaskCompletionUseCase`, inside the transaction, against the re-read task; verify tests for last occurrence (no insert, no schedule), earlier occurrence (end date copied) and unchecking a last occurrence.
- [x] 2.5 Add `Result.SeriesEnded` to `RollOverduePeriodicTaskUseCase`, with no scheduler call so the last notification survives; verify tests for last occurrence, device off past the end date (no stale occurrence), fast-forward within the end date, reuse of a linked occurrence, and a repeat run returning `NotApplicable`.
- [x] 2.6 Handle `SeriesEnded` in `RescheduleRemindersWorker`; verify a worker test shows the reminder date doesn't move and no occurrence is created.

## 3. Editor state

- [x] 3.1 Add `periodicityEndDate` to `TaskFormState`, both form builders, `editFieldsFrom`, the save path and `sanitizePeriodicityFields`, plus a `SetPeriodicityEndDate` event; verify with `TasksViewModel` tests that it saves on create and edit.
- [x] 3.2 Clear the end date in `onClearPeriodicity` and `clearReminder`, and in `setReminder` when the new date is after it, while keeping it on periodicity switches; verify with `TasksViewModel` tests for each case.

## 4. Editor UI

- [x] 4.1 Add `initialDisplayedDate` to `TempoDatePickerDialog`; verify existing callers compile unchanged and the picker opens on the given month in a preview.
- [x] 4.2 Extract `EditorValueChip` to `core/ui/editor` and adopt it in the task reminder row; verify `TaskBottomSheetTest` stays green and the row looks unchanged on a device.
- [x] 4.3 Add the end-date row as the last row of the periodicity section, the picker with its date bounds, and the dirty-check and snapshot fields in `TaskBottomSheetContent`; add `@Preview`s in `src/debug` for both states in light and dark.
- [x] 4.4 Add `add_end_date`, `periodicity_until` and `clear_end_date` to `values` and `values-es`; verify with `./gradlew lintDebug`.
- [x] 4.5 Extend `TaskBottomSheetSectionTest` for row visibility per periodicity, add, change and clear, and the row's position below the sub-selectors; verify on an AVD, including a 360 dp width with the longest Spanish label.

## 5. Integration checks

- [x] 5.1 Run `openspec validate feat-509-periodicity-end-date --strict`, `./gradlew testDebugUnitTest koverVerifyDebug`, `./gradlew ktlintFormat ktlintCheck :app:detekt`, `./gradlew lintDebug` and `./gradlew validateDebugScreenshotTest`; verify all pass and the detekt baseline is still at most 155.
- [ ] 5.2 Run `./gradlew connectedDebugAndroidTest` on the Pixel 10 AVD; verify it passes.
- [ ] 5.3 Smoke-test on the Pixel 7, or the AVD if it isn't connected: a daily task ending tomorrow, completed twice, stops; a task left overdue past its end stays overdue with no new occurrence; both editor states look right in light and dark under the Tempo palette and a dynamic theme.

## Workflow follow-up

- Implement on `feat/509-periodicity-end-date`, with commits as `feat(#509): ...` and a PR containing `Closes #509`.
- Update the checklist and archive this change in the same PR, before its final CI run.
