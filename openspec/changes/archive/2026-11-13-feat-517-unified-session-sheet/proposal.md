# Proposal

Originating issue: [#517 — feat: create a focus session when in session screen](https://github.com/sherrerapiqueras/tempo/issues/517). Follow-up to `feat-517-focus-sessions`, from the owner's review of the first build.

## Why

The first build left Focus with three places to start from (Up next, Sessions, the day's list) and two different sheets: a form for a saved session and the ring sheet for a timer. Starting a saved session closed one and opened the other, and a session with rounds never showed how many were left.

## What Changes

- **One session sheet.** The ring sheet is the only session surface. A new session, a saved session and a task all open it. Before starting it shows the name (editable for a session), the focus, rest and rounds controls and Start; pressing Start keeps the same sheet open and turns it into the running timer, with the change animated. The separate session form is removed.
- **One row of cards.** Saved sessions join the Up next row, after the day's tasks, instead of having a row and a label of their own.
- **Rounds are shown.** A session with more than one round shows a marker per round under the ring and on the running card: done, under way, still to come.
- **Task timers get the same controls.** A timer started on a task can be given a rest and rounds for that run, without saving a session. The separate "Start with" length row is replaced by the shared controls.

- **The sheet matches the app's other sheets.** Its heading is start-aligned, with the task's description under it, and it is as tall as its content rather than the whole screen. The thin ring is replaced by a filled, scalloped shape from the expressive set with the time traced round its edge: a cookie while focusing, a flower while resting.
- **One linked-task row.** The task a session is for stands in the same place whether the sheet was opened from the task or the task was linked to a session: a chip naming it, with what the task carries beneath. Before a session starts the link can always be taken off; taken off a sheet opened from a task, the sheet becomes a new session with none. Choosing a task loads its details in place.
- **The link opens the task.** Tapping the chip opens the task's own sheet over the session sheet. The "Open in Tasks" button, which left Focus for the Tasks tab, is removed.

### Non-goals

- No change to what a saved session stores, to backups, or to the database.
- No change to the day's task list or to the task editor it opens.
- An ad-hoc task run with rounds is not saved as a session.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `focus-saved-sessions`: where sessions are listed, how they are created and edited, and how rounds are shown.

## Impact

- **Domain**: a round cycle no longer requires a saved session, so a task timer can carry one.
- **UI**: `FocusSessionScreen`, the session body, the running card and the Focus card row; `SessionEditorSheet` is folded into the session sheet.
- **State**: the session being set up is held beside the task preview, so the sheet's own view model can see it.
- **Navigation**: the open-in-Tasks callback is removed from the Focus and session destinations.
- **Strings**: `focus_sessions_label`, `focus_session_start_custom` and `focus_session_open_in_tasks` removed in both locales.
