# Design

## Context

See proposal.md for motivation. Today the only "session" is `FocusSession`: the one running timer, kept in SharedPreferences so a receiver can read it on a cold start without opening the encrypted database, and identified everywhere by a non-null `taskId`. Focus's state, its session sheet, the notification actions and the per-task "today" record all key on that id.

## Goals / Non-Goals

**Goals:**
- A persisted session entity with an optional task, without disturbing the task-timer paths that already work (task card, reminder action).
- Rounds that advance with the app dead, on the alarm the timer already uses.
- UI built from the Focus surfaces that exist — Up next card, running card, session sheet, `ValueStepper`, `TempoModalBottomSheet`, `TempoConfirmDialog` — so nothing new is introduced visually.

**Non-Goals:**
- Renaming the running timer type, or moving it into Room.
- A run history table.
- Docking the session editor on large windows.

## Decisions

### Two types: `SavedSession` (Room) and the running `FocusSession` (prefs)
The saved session is `SavedSession(id, title, focusMinutes, breakMinutes, rounds, taskId?)`, listed by id (creation order), in a new `focus_sessions` table. The running timer stays where it is and gains what it needs to run one: `taskId` becomes nullable, `taskTitle` becomes `title`, and an optional `cycle: SessionCycle(savedSessionId, round, totalRounds, focusLength, breakLength)`.

*Alternative:* one Room row per run, with the active one flagged. Rejected: the end receiver would have to open SQLCipher on a cold start, the reason the timer is in preferences in the first place.

The timer carries a copy of the lengths rather than re-reading the saved session at each round, so editing or deleting a session never changes a run already under way and the receiver needs no database.

### `taskId` is a plain indexed column, resolved on read
No foreign key, like every other reference in this database. The link is resolved when sessions are read (`GetSavedSessionsUseCase` joins against all tasks), so a deleted task simply stops resolving and the session reads as unlinked. Task ids are `AUTOINCREMENT` and never reused, so a stale id cannot come to point at another task.

*Alternative:* FK with `ON DELETE SET NULL`. Rejected: it would be the database's only enforced constraint, and backup restore deletes and re-inserts tasks in an order that would then matter.

### Round advance lives in the use cases, driven by the end alarm
`FocusSession.nextPhase(now)` is pure: focus round *n* of *N* (n < N) → rest; rest → focus round *n+1*; otherwise none. `FocusSessionUseCases.timeUp()` settles the expired phase (banks minutes, records the run) and, when there is a next phase, replaces the active session with it directly — never passing through "no session" — then reschedules the alarm and re-posts the notification. With no next phase it is `end()`.

- **Idempotency:** `timeUp()` acts only on a session that `hasExpired(now)`. A duplicate or late alarm finds either the next phase (not expired → no-op) or nothing.
- **Never through null:** Focus shows the finished sheet when the active session disappears after expiring. Swapping phases in one write keeps that sheet for the real end only.
- **Transaction boundary:** banking minutes is the Room write (`daily_focus_activity`); the alarm, the notification and the preferences write are side effects outside it, in the order settle → store next phase → schedule → notify. A crash between settle and store leaves no session, which reads as stopped — minutes are never banked twice.
- **Notification:** the ongoing notification is cleared before the next phase's is posted, so the phase change alerts instead of being swallowed by `setOnlyAlertOnce`.
- `reconcile()` (app start, reboot) keeps ending an expired session outright: starting a rest for a round that ran out while the phone was off would be a timer nobody asked for.
- `skipBreak()` starts the next phase immediately, for "Back to it" during a rest between rounds.

### Linked sessions run on the task
A linked session's timer has both `cycle` and `taskId`. Minutes bank against the task as for a task timer, and Mark done completes it. The task's agenda row is hidden only for a direct task timer (no `cycle`): the running card there *is* the task, whereas a saved session's card carries the session's own name.

Focus resolves the linked task from the saved-session list (`SavedSessionEntry(session, linkedTask)`), not only from the day's agenda, because a session may be linked to a task that is not on today.

### The session sheet's subject becomes a key
The sheet latched a task id. It now latches `sessionSubjectKey: String?` — `task:<id>` for a task preview or task timer, `session:<id>` for a saved session's timer — so both kinds close correctly when their subject goes away. Saved sessions have no preview state: their card opens the editor, and their sheet exists only while they run.

### Focus add opens the session editor; the task-creation path is removed from Focus
`FocusFloatingBarState.onAddTask` becomes `onAddSession`; `UiEvent.AddTask` and `TaskEditorTarget.NewTask` are deleted rather than left unreachable. Editing an existing task and adding a subtask from Focus are untouched.

### Session editor is its own small MVI slice inside `FocusViewModel`
State `sessionEditor: SessionEditorState?` (draft fields + the open tasks for the picker), handled in a new `FocusViewModelSessionEditor.kt` extension file, the way the plan sheet is split out. A separate ViewModel was rejected: saving-and-starting needs the replace-session confirmation that already lives in `FocusViewModel`.

### Backup: additive list, schema version stays 1
`focusSessions` is added to the DTO with a default, which the format's own rules allow without a version bump. Replace restores ids as-is, dropping links to tasks the payload does not carry. Merge skips a session identical (title, lengths, rounds) to a local one and otherwise inserts it with its link remapped through the task id map, or unlinked.

## Risks / Trade-offs

- [`taskId` nullable ripples through Focus state and tests] → compile-driven; the direct task-timer behaviour is pinned by the existing unit tests, which must stay green unchanged in intent.
- [Inexact alarms when exact-alarm permission is denied delay a round change] → same degradation the timer already has; the countdown itself stays correct.
- [Removing add-task from Focus reverses #434] → the user's explicit choice; recorded as a spec change.
- [A linked task completed elsewhere leaves a session linked to finished work] → the link stays visible and Mark done is simply not offered; the user can relink.

## Migration Plan

Room 13 → 14 creates `focus_sessions` and its index; purely additive, no backfill. Stored running timers written by the previous version read back unchanged (same keys; a missing cycle means none). Rollback is not supported by Room downgrade, as for every prior migration.
