# Proposal

Originating issue: [#509 — feat: add reminders final date (until x date)](https://github.com/sherrerapiqueras/tempo/issues/509)

## Why

A repeating task can only stop when the user remembers to stop it. A course that ends in March or a medication taken for two weeks keeps producing occurrences and reminders until someone edits it by hand. Task apps commonly offer "repeat until", and Tempo has no equivalent.

## What Changes

- The task editor's periodicity section gains an end-date sub-option. It is the last row of the section, below whichever sub-selectors the chosen periodicity shows, and it appears for all five periodicities.
- With no end date, the row offers "Add end date". With one, it shows "Until 31 Mar", which can be tapped to change the date or cleared.
- The end date is inclusive and has no time component: any occurrence on that date still happens, and none is created after it.
- When the next occurrence would fall after the end date, completing the task or rolling it over ends the series instead of creating a new occurrence.
- End dates are stored with the task, and included in backups.

**Non-goals**

- Habits and habit chains. Habits are permanent by definition, so an end date doesn't apply to them.
- "After N times" end conditions.
- Showing the end date on the task card. The card keeps its existing repeat icon.
- A message announcing that a series has ended.

## Capabilities

### New Capabilities

- `task-periodicity-end-date`: setting, changing and clearing a repeating task's end date, and how that date limits its occurrences.

### Modified Capabilities

- `task-reminder-rollover`: rollover of an overdue occurrence stops creating occurrences past the end date.

## Impact

- **Data:** new nullable `tasks.periodicityEndDate` column, Room version 12 → 13 with an additive migration, and a new exported schema. Existing tasks read as having no end date.
- **Backup:** new optional field in the task DTO, so files written earlier still import.
- **Domain:** `TaskReminderDateUtil`, `ToggleTaskCompletionUseCase`, `RollOverduePeriodicTaskUseCase` and the `Task` model.
- **Infrastructure:** `RescheduleRemindersWorker` handles a new rollover result.
- **UI:** the task editor, its form state and events, `TempoDatePickerDialog`, and new strings in `values` and `values-es`.
- **Adaptive:** the row behaves the same at every window size, because it lives inside the editor's existing content column.
