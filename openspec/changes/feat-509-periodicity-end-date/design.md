# Design

## Context

See proposal.md for motivation and specs for the required behaviour.

Each task occurrence is its own row. Only the latest occurrence holds recurrence fields: completion (`ToggleTaskCompletionUseCase`) and overdue rollover (`RollOverduePeriodicTaskUseCase`) both insert a copy for the next occurrence and strip recurrence from the old row. Editing therefore never has to propagate along a series.

`TaskReminderDateUtil.advanceReminderIfNeeded` fast-forwards past reminders to the next future occurrence. It is called from create, update, the rollover and completion flows, and `RescheduleRemindersWorker`.

## Goals / Non-Goals

**Goals:**

- One rule decides when a series is over, shared by completion and rollover.
- The editor control looks like the existing "add, then set" pattern rather than a new one.

**Non-Goals:**

- Habits and habit chains, "after N times", and card display, as listed in proposal.md.
- Reconciling the `task-reminder-rollover` main spec with the code. Its first requirement says an overdue original keeps its recurrence metadata, while `asRolledOverOriginal` strips it. This change builds on what the code does and leaves that older wording alone.

## Decisions

### Model: a nullable `LocalDate` on the task

`Task.periodicityEndDate: LocalDate?`, stored as an ISO text column through a new `LocalDate` converter in `Converters`. The name follows the existing `periodicity*` fields.

- *Instead of `LocalDateTime`:* "until 31 March" is a calendar date, and a time would add a choice nobody asked for.
- *Instead of a separate series table:* the single-owner model already gives one place to hold it, and `copy()` carries it to each new occurrence.

### Data invariants

- `periodicityEndDate` is non-null only when `periodicity` is non-null.
- The check always compares `nextOccurrence.date > periodicityEndDate`, using the local date of the occurrence.
- A reminder after the end date is tolerated in the domain layer. The Focus plan sheet can move a reminder without opening the editor, and the result is safe: that occurrence still fires and the series then ends. Silently dropping the end date there would turn a limited series into an unlimited one.

To keep the first invariant, the three existing strip sites (archive, detach, rolled-over original) move to one `Task.withoutRecurrence()` helper, as does `sanitizePeriodicityFields`. `TaskDao.clearAllReminders` also clears the column.

### The series rule: one function that returns null

Add `TaskReminderDateUtil.nextOccurrenceWithinEnd(task, now): LocalDateTime?`. It computes the next occurrence, fast-forwards it past `now`, and returns null if the result falls after the end date. Completion and rollover both call it.

**The trap this avoids:** guarding only `advanceReminderIfNeeded` would break rollover. With a daily task ending 10 March, a 5 March reminder and a first run on 15 March, the calculated next occurrence is 6 March, which is within the end date. Fast-forwarding would pass the end date and leave it unchanged, so a stale 6 March occurrence would be inserted, and each later run would spawn another up to 10 March. The check must run on the fast-forwarded date, before any insert.

`advanceReminderIfNeeded` also gets a guard, for create, update and the worker: if advancing would pass the end date, it returns the task unchanged. Rollover then ends the series on its next pass. Create and update tell the user such a reminder won't fire, through the same message as a past reminder on a one-off task, which is why `isPastReminderThatWontFire` replaces the periodicity-only check.

Completion anchors the rule on the `reminderDate` its caller passes, not the stored one. `MarkAsCompletedReceiver` and the editor restore the occurrence that actually fired, and the next date must follow from that one.

### Ending a series on rollover

Add a `Result.SeriesEnded` object to the rollover use case. The overdue task is written with `withoutRecurrence()`, as other rolled-over originals are, so it remains as an ordinary overdue task. `RescheduleRemindersWorker` handles the new result with `return@forEach`, so it never reaches `advanceReminderIfNeeded`.

- *Instead of keeping recurrence on the final overdue row:* `shouldRollOver` would stay true forever, and every path through the worker and receiver would need a special case.
- *Trade-off:* extending the end date on an already-ended overdue task isn't possible. The user sets a periodicity on it again, as with any one-off task.

### Idempotency

- **Rollover:** after `SeriesEnded` the task has no periodicity, so `canRollOver` is false and repeat runs return `NotApplicable`. The worker's `shouldRollOver` is false as well, so the reminder date doesn't move.
- **Existing next instance:** `findReusableCommit` runs before the end-date check, so a valid linked occurrence is reused and no decision to end the series is taken.
- **Completion:** the archived row is stripped, so it can't be completed as periodic twice. Unchecking a last occurrence takes the non-periodic branch because `nextInstanceId` is null.

### Transaction boundaries

**Inside the transaction:**

- Both flows evaluate `nextOccurrenceWithinEnd` against the task re-read inside the transaction, so a concurrent edit to the end date is respected.
- The archive or strip write and any insert of a next occurrence commit together.

**After commit (Android side effects):**

- Completion: the existing code cancels the archived task's alarm. `commit.nextInstance?.let` already returns `Skipped` when there is no next occurrence.
- Rollover `SeriesEnded`: no scheduler call. The reminder has already passed, so no alarm is pending, and `TaskReminderScheduler.cancel` also dismisses the notification, which would erase the one the last occurrence just posted.

Scheduler calls stay outside the transaction, as they are now.

### UI: last row of the periodicity section

```
[repeat]  Hourly | Daily | [Every week, Mon] | Monthly | Yearly
          - 1 +
          M T W T F S S
          [+ Add end date]     or     [Until 31 Mar  x]
```

- **Placement:** after the `AnimatedContent` sub-options, inside `AnimatedVisibility(periodicity != null)`, with the same 48 dp leading spacer as the other sub-rows. It lives outside the `AnimatedContent`, so it doesn't re-animate when switching periodicity.
- **Empty state:** the standard tonal `EditorAddButton`. "Something that can be added" is `secondaryContainer` under the agreed colour roles, and the unused `quiet` variant would break that. It can't compete with "Add Reminder", which is always replaced by a chip before this row appears.
- **Set state:** the same `InputChip` as the reminder: `secondaryContainer`, 16 dp radius, 48 dp tall, trailing close button. Extract it to `core/ui/editor/EditorValueChip` and use it for the task and habit editors, replacing the existing copies. Habits get no end date; they share only the chip.
- **Copy:** "Add end date" for the action, "Until 31 Mar" for the value, because the button names the thing and the chip reads as a sentence. `DateTimeFormatter.formatDate` drops the year when it's the current one and shows it otherwise. Strings: `add_end_date`, `periodicity_until`, `clear_end_date`, in `values` and `values-es` ("Añadir fecha de fin", "Hasta el %1$s", "Quitar fecha de fin").
- **Picker:** the existing `TempoDatePickerDialog`, owned by the row composable so `TaskBottomSheetContent` (already in the detekt baseline) doesn't grow. It gains an optional `initialDisplayedDate`, because with no selection the stock picker opens on the current month, which may contain no selectable day when the reminder is months away. No date is pre-selected, so OK stays disabled until the user picks one. Pre-selecting the reminder date would make it too easy to create a series of one.
- **Moving the reminder past the end date in the editor:** `setReminder` clears the end date, and the chip visibly becomes the Add button right below the reminder. *Alternative rejected:* moving the end date forward would invent a date the user never chose.
- **Read-only:** on a completed task the row follows `isPeriodicityReadOnly`, like its siblings. Completed tasks normally have recurrence stripped, so this is a safety net.
- **Adaptive:** identical at compact, medium and expanded widths. The row sits in the editor's content column in the bottom sheet, the side sheet (≥840 dp) and the docked pane (≥1200 dp), and both states are as wide as their label. The longest label, Spanish "Hasta el 31 mar 2027" with the close button, must be checked at 360 dp.

State follows the existing form pattern: `TaskFormState.periodicityEndDate`, a `SetPeriodicityEndDate(LocalDate?)` event, the dirty check and snapshot in `TaskBottomSheetContent`, and both places that build `TaskFormState` from a task.

## Risks / Trade-offs

- [A recurrence copy site is missed] → The `monthDayOption` usages were the checklist for this design (entity, mappers, backup, form state, strip sites). The new helper reduces the strip sites to one, and tests cover the rest.
- [Silent series end: the task just doesn't come back] → Accepted for now, since the user chose the date. A message can be added later without changing the model.
- [Clearing the end date when the reminder moves is easy to miss] → The change happens directly below the control being edited. Revisit if it proves confusing on a device.
- [Parameter count in the editor composables] → Follows the existing per-event callback style. `TaskPeriodicitySection` is already in the detekt baseline, so the new row is its own composable and the frozen baseline doesn't grow.

## Migration Plan

`MIGRATION_12_13` runs `ALTER TABLE tasks ADD COLUMN periodicityEndDate TEXT DEFAULT NULL`, with no backfill. The change is additive, like `MIGRATION_11_12`. The backup field is optional with a null default, so the backup schema version doesn't change.

An app downgrade isn't supported by Room without a destructive path, which is the same as for earlier migrations.
